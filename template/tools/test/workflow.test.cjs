const {test} = require('node:test');
const assert = require('node:assert/strict');
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
test('blank listing refuses publication until artwork is supplied', () => assert.throws(() => contract.validateMarketplaceListing(blank())));
test('completed listing uses production shape', () => assert.equal(contract.validateMarketplaceListing(complete()).category, 'ui_extension'));
test('unknown fields and backend approval state are refused', () => {
  for (const field of ['website','privacyPolicy','approved','ownerUid']) assert.throws(() => contract.validateMarketplaceListing({...complete(), [field]: 'x'}));
});
test('duplicate capability and artwork IDs are refused', () => {
  const x = complete(); x.capabilities.push(x.capabilities[0]); assert.throws(() => contract.validateMarketplaceListing(x));
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
  const hash = require('node:crypto').createHash('sha256').update(m).digest('hex');
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
