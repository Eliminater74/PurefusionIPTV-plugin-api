const {test} = require('node:test');
const assert = require('node:assert/strict');
const {createHash} = require('node:crypto');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const contract = require('../listing-contract.cjs');
const workflow = require('../marketplace.cjs');
const root = path.resolve(__dirname, '../..');
// Contract fixtures must stay independent of the developer's editable listing.
const blank = () => JSON.parse(fs.readFileSync(path.join(__dirname, 'listing.fixture.json')));
function complete() {
  const x = blank(); x.icon.assetId = 'a'.repeat(64); x.icon.byteSize = 100;
  x.screenshots[0].assetId = 'b'.repeat(64); x.screenshots[0].byteSize = 200; return x;
}
function subtitleListing() {
  return {
    ...complete(),
    pluginId: 'dev.pf_test.subtitles',
    displayName: 'Synthetic Subtitle Provider',
    developerId: 'pf_test',
    category: 'subtitle_provider',
    capabilities: ['NETWORK_EXTERNAL', 'PLUGIN_SCOPED_STORAGE'],
  };
}
function bind(listing, manifest, manifestSha256) {
  const bytes = JSON.stringify(manifest);
  return contract.bindMarketplaceListing(listing, listing, bytes,
    manifestSha256 ?? createHash('sha256').update(bytes).digest('hex'));
}
test('blank listing refuses publication until artwork is supplied', () => assert.throws(() => contract.validateMarketplaceListing(blank())));
test('completed listing uses production shape', () => assert.equal(contract.validateMarketplaceListing(complete()).category, 'ui_extension'));
test('API 3 subtitle provider listing is accepted', () => {
  const raw = subtitleListing();
  assert.equal(raw.apiLevel, 3);
  assert.deepEqual(contract.validateMarketplaceListing(raw), raw);
});
test('subtitle provider listing binds to package manifest identity and capabilities', () => {
  const listing = contract.validateMarketplaceListing(subtitleListing());
  const {pluginId, displayName, developerId, version, versionCode, category, apiLevel, minHostVersion, minSdk} = listing;
  const manifest = {
    schemaVersion: 1, pluginId, displayName, developerId, version, versionCode, category,
    apiLevel, minHostVersion, minSdk, capabilities: ['PLUGIN_SCOPED_STORAGE', 'NETWORK_EXTERNAL'],
  };
  assert.deepEqual(bind(listing, manifest), listing);
  assert.throws(() => bind(listing, {...manifest, category: 'ui_extension'}),
    {message: 'listing.category: differs from package manifest'});
  assert.throws(() => bind(listing, {...manifest, capabilities: ['NETWORK_EXTERNAL', 'NETWORK_EXTERNAL']}),
    {message: 'listing.capabilities: differs from package manifest'});
  assert.throws(() => bind(listing, manifest, '0'.repeat(64)),
    {message: 'manifestSha256: does not match the exact manifest bytes'});
});
test('subtitle provider publication evidence binds the release and artwork', () => {
  const listing = contract.validateMarketplaceListing(subtitleListing());
  const manifestSha256 = 'c'.repeat(64);
  const packagePath = 'synthetic/subtitles.pfp';
  const artwork = [listing.icon, ...listing.screenshots].map(({assetId, format, width, height, byteSize}) =>
    ({assetId, format, width, height, byteSize}));
  const verify = receipt => contract.requireMarketplacePublicationEvidence(listing, receipt, manifestSha256, packagePath);
  assert.doesNotThrow(() => verify({manifestSha256, packagePath, artwork}));
  assert.throws(() => verify(undefined), {message: 'verification: expected an object'});
  assert.throws(() => verify({manifestSha256, packagePath: 'synthetic/other.pfp', artwork}),
    {message: 'verification: package verification does not match this release'});
  assert.throws(() => verify({manifestSha256, packagePath, artwork: [{...artwork[0], width: 512}, artwork[1]]}),
    {message: 'verification: artwork verification does not match the listing'});
});
test('all other Marketplace categories remain accepted', () => {
  for (const category of ['epg_metadata', 'service_integration', 'ui_extension', 'media_server',
    'sync', 'theme_pack', 'runtime_extension']) {
    assert.equal(contract.validateMarketplaceListing({...complete(), category}).category, category);
  }
});
test('unknown categories and capabilities are refused', () => {
  assert.throws(() => contract.validateMarketplaceListing({...complete(), category: 'unknown_category'}),
    {message: 'listing.category: unrecognized identifier'});
  assert.throws(() => contract.validateMarketplaceListing({...complete(), capabilities: ['UNKNOWN_CAPABILITY']}),
    {message: 'listing.capabilities[0]: unrecognized identifier'});
});
test('required metadata, integer version codes and artwork stay strict', () => {
  const {publisher, icon, ...listing} = complete();
  assert.throws(() => contract.validateMarketplaceListing({...listing, icon}),
    {message: 'listing.publisher: expected 1–80 characters of plain text'});
  assert.throws(() => contract.validateMarketplaceListing({...listing, publisher}),
    {message: 'listing.icon: expected an object'});
  assert.throws(() => contract.validateMarketplaceListing({...complete(), screenshots: []}),
    {message: 'listing.screenshots: expected 1–8 entries'});
  for (const versionCode of [0, 1.5, '1', 2147483648]) {
    assert.throws(() => contract.validateMarketplaceListing({...complete(), versionCode}),
      {message: 'listing.versionCode: expected an integer from 1 to 2147483647'});
  }
});
test('unknown fields and backend approval state are refused', () => {
  for (const field of ['website','privacyPolicy','approved','ownerUid']) assert.throws(() => contract.validateMarketplaceListing({...complete(), [field]: 'x'}));
});
test('duplicate capability and artwork IDs are refused', () => {
  const x = complete(); x.capabilities.push(x.capabilities[0]);
  assert.throws(() => contract.validateMarketplaceListing(x), {message: 'listing.capabilities: duplicate capability'});
  const y = complete(); y.screenshots[0].assetId = y.icon.assetId; assert.throws(() => contract.validateMarketplaceListing(y));
});
test('unsafe URLs and descriptions are refused', () => {
  for (const url of ['http://example.com','https://user:secret@example.com','https://example.com:8443']) {
    const x = complete(); x.support.url = url; assert.throws(() => contract.validateMarketplaceListing(x));
  }
  assert.throws(() => contract.validateMarketplaceListing({...complete(), shortDescription:'<script>'}));
});
test('wrong screenshot geometry and oversize icon are refused', () => {
  const x = complete(); x.screenshots[0].width = 400; assert.throws(() => contract.validateMarketplaceListing(x));
  const y = complete(); y.icon.byteSize = 524289; assert.throws(() => contract.validateMarketplaceListing(y));
});
test('updates require increasing code and changelog', () => {
  const x = complete(); assert.throws(() => contract.requireMarketplaceVersion(x, 1));
  delete x.changelog; assert.throws(() => contract.requireMarketplaceVersion({...x,versionCode:2}, 1));
});
test('release manifest identity and exact hash are bound', () => {
  const x = complete(); const m = JSON.stringify({...x,pluginId:'dev.other.plugin'});
  const hash = createHash('sha256').update(m).digest('hex');
  assert.throws(() => contract.bindMarketplaceListing(x,x,m,hash));
});
test('preview renders actual listing and escapes all text', () => {
  const html = workflow.renderPreview(complete(), []);
  assert.ok(html.includes('Your Plugin Name')); assert.ok(html.includes('PLUGIN_SCOPED_STORAGE'));
  assert.ok(!workflow.renderPreview({...complete(),displayName:'<img src=x>'},[]).includes('<img src=x>'));
});
test('preparation calculates descriptors from real artwork', () => {
  const listing = workflow.prepareListing(root);
  assert.equal(contract.validateMarketplaceListing(listing).icon.width, 256);
  assert.equal(listing.screenshots[0].width, 1920);
  assert.match(listing.icon.assetId,/^[a-f0-9]{64}$/);
});
test('release completeness rejects example identity and support placeholders', () => assert.throws(() => workflow.requireComplete(complete())));
test('initialization changes manifest and listing together and refuses rerun', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(),'pf-init-'));
  try {
    fs.mkdirSync(path.join(dir,'marketplace'));
    fs.copyFileSync(path.join(root,'manifest.json'),path.join(dir,'manifest.json'));
    const baseline = JSON.parse(fs.readFileSync(path.join(dir,'manifest.json')));
    baseline.pluginId = 'dev.pf_example.starter';
    fs.writeFileSync(path.join(dir,'manifest.json'),JSON.stringify(baseline));
    fs.copyFileSync(path.join(root,'marketplace/listing-template.json'),path.join(dir,'marketplace/listing-template.json'));
    workflow.initialize(dir,{pluginId:'dev.pf_test.unique',displayName:'Fresh TV',publisher:'Independent',developerId:'pf_test',description:'Fresh example'});
    assert.equal(JSON.parse(fs.readFileSync(path.join(dir,'manifest.json'))).displayName,'Fresh TV');
    assert.equal(JSON.parse(fs.readFileSync(path.join(dir,'marketplace/listing-template.json'))).pluginId,'dev.pf_test.unique');
    assert.throws(() => workflow.initialize(dir,{pluginId:'dev.changed'}));
  } finally { fs.rmSync(dir,{recursive:true,force:true}); }
});
