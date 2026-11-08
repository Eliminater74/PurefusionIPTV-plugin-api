'use strict';
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const contract = require('./listing-contract.cjs');
const root = path.resolve(__dirname, '..');
const identityFields = ['pluginId','displayName','developerId','version','versionCode','category','apiLevel','minHostVersion','minSdk','capabilities'];
const read = f => JSON.parse(fs.readFileSync(f, 'utf8'));
const write = (f,data) => fs.writeFileSync(f,JSON.stringify(data,null,2)+'\n');
const hash = b => crypto.createHash('sha256').update(b).digest('hex');
function artwork(file,old) {
  const b=fs.readFileSync(file);
  if(b.length<33 || b.subarray(0,8).toString('hex')!=='89504e470d0a1a0a' || b.toString('ascii',12,16)!=='IHDR') throw Error('Local artwork helper accepts PNG only. Portal also accepts static JPEG and WebP.');
  const result={assetId:hash(b),format:'image/png',width:b.readUInt32BE(16),height:b.readUInt32BE(20),byteSize:b.length,altText:old.altText};
  if(old.caption!==undefined) result.caption=old.caption;
  return result;
}
function prepareListing(directory) {
  const listing=read(path.join(directory,'marketplace/listing-template.json'));
  const manifest=read(path.join(directory,'manifest.json'));
  for(const key of identityFields) listing[key]=manifest[key];
  listing.publisher=manifest.publisher;
  listing.icon=artwork(path.join(directory,'marketplace/artwork/plugin-icon-placeholder.png'),listing.icon);
  const screenshots=fs.readdirSync(path.join(directory,'marketplace/artwork')).filter(x=>/^screenshot.*\.png$/.test(x)).sort();
  listing.screenshots=screenshots.map((name,index)=>artwork(path.join(directory,'marketplace/artwork',name),listing.screenshots[index]||{altText:'Describe this screenshot'}));
  contract.validateMarketplaceListing(listing);
  return listing;
}
function requireComplete(listing) {
  const values=[listing.pluginId,listing.developerId,listing.displayName,listing.publisher,listing.shortDescription,listing.fullDescription,listing.support.url,listing.icon.altText,...listing.screenshots.flatMap(x=>[x.altText,x.caption||'']),...(listing.features||[]),listing.changelog||''];
  if(values.some(s=>/dev\.pf_example|your_developer_id|Your Plugin Name|Your Developer Name|example\.com|^Describe |^A short description/i.test(s))) throw Error('Replace example identity, descriptions, accessibility text and support URL before submission.');
}
function validateFiles(listing,directory) {
  const descriptors=fs.readdirSync(path.join(directory,'marketplace/artwork')).filter(x=>x.endsWith('.png')).map(name=>artwork(path.join(directory,'marketplace/artwork',name),{altText:'Local artwork'}));
  for(const image of [listing.icon,...listing.screenshots]) {
    const actual=descriptors.find(x=>x.assetId===image.assetId);
    if(!actual || ['format','width','height','byteSize'].some(k=>actual[k]!==image[k])) throw Error('Artwork bytes or dimensions differ: '+image.assetId);
  }
}
function escape(value) { return String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c])); }
function renderPreview(listing,urls) {
  const e=escape;
  return `<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src data:; style-src 'unsafe-inline'"><title>${e(listing.displayName)} - Marketplace draft</title>
<style>body{margin:0;background:#080e1a;color:#eaf0fa;font:16px/1.5 system-ui;padding:40px}main{max-width:1000px;margin:auto}.note{color:#98a9c0}.card{background:#121d30;border:1px solid #2c405b;border-radius:20px;padding:32px}header{display:flex;gap:24px;align-items:center}header img{width:110px;border-radius:20px}h1{margin:0;font-size:32px}h2{font-size:19px}.tag{display:inline-block;background:#20374b;border-radius:20px;padding:5px 14px;margin:12px 6px 0 0;color:#58dbc7}.gallery{display:flex;gap:16px;overflow:auto}.gallery img{width:100%;border-radius:12px}figure{margin:0;min-width:70%}figcaption{color:#aabbce}.full{white-space:pre-wrap}code{overflow-wrap:anywhere}footer{margin-top:28px;color:#aabbce}</style>
<main><p class="note">LOCAL DRAFT PREVIEW - No Marketplace approval or verified publisher status</p><article class="card"><header>${urls[0]?`<img src="${e(urls[0])}" alt="${e(listing.icon.altText)}">`:''}<div><h1>${e(listing.displayName)}</h1><p>by ${e(listing.publisher)}</p><span class="tag">${e(listing.category)}</span><span class="tag">Version ${e(listing.version)}</span></div></header>
<p>${e(listing.shortDescription)}</p><div class="gallery">${listing.screenshots.map((s,i)=>`<figure>${urls[i+1]?`<img src="${e(urls[i+1])}" alt="${e(s.altText)}">`:''}<figcaption>${e(s.caption)}</figcaption></figure>`).join('')}</div><h2>About this plugin</h2><p class="full">${e(listing.fullDescription)}</p><h2>Features</h2><ul>${(listing.features||[]).map(s=>`<li>${e(s)}</li>`).join('')}</ul><h2>Compatibility and permissions</h2><p>Plugin API ${e(listing.apiLevel)} - Android API ${e(listing.minSdk)}+ - Host ${e(listing.minHostVersion)}+</p><p>${listing.capabilities.map(e).join(' - ')||'No capabilities declared'}</p><h2>Release notes</h2><p class="full">${e(listing.changelog)}</p><footer>Developer: ${e(listing.publisher)}<br>Developer identity: ${e(listing.developerId)}<br>Support: ${e(listing.support.url)}<br>Plugin ID: <code>${e(listing.pluginId)}</code></footer></article></main></html>`;
}
function initialize(directory,values) {
  const mf=path.join(directory,'manifest.json'),lf=path.join(directory,'marketplace/listing-template.json');
  const m=read(mf),listing=read(lf);
  if(m.pluginId!=='dev.pf_example.starter') throw Error('Already customized. Edit manifest.json manually; init never overwrites a customized project.');
  if(!contract.isMarketplacePluginId(values.pluginId||'') || values.pluginId===m.pluginId) throw Error('Choose a new reverse-DNS plugin ID in your portal namespace.');
  for(const [key,max] of [['displayName',80],['publisher',80],['developerId',80],['description',400]]) {
    const v=values[key]; if(typeof v!=='string'||!v.trim()||v!==v.trim()||v.length>max||/[<>\x00-\x1f\x7f-\x9f\u202a-\u202e\u2066-\u2069]/u.test(v)) throw Error('Invalid '+key);
  }
  Object.assign(m,values);
  for(const key of identityFields) listing[key]=m[key];
  listing.publisher=m.publisher;listing.shortDescription=m.description;
  fs.copyFileSync(mf,mf+'.init-backup',fs.constants.COPYFILE_EXCL);
  try{fs.copyFileSync(lf,lf+'.init-backup',fs.constants.COPYFILE_EXCL);}catch(error){fs.unlinkSync(mf+'.init-backup');throw error;}
  try{write(mf,m);write(lf,listing);}catch(error){fs.copyFileSync(mf+'.init-backup',mf);fs.copyFileSync(lf+'.init-backup',lf);throw error;}
}
function main(args) {
  const [command,...rest]=args;
  if(command==='init') {
    if(rest.length!==5) throw Error('init PLUGIN_ID "Plugin name" "Publisher name" DEVELOPER_ID "Description"');
    const [pluginId,displayName,publisher,developerId,description]=rest;
    initialize(root,{pluginId,displayName,publisher,developerId,description});console.log('Customized manifest and listing; originals retained as .init-backup.');return;
  }
  if(command==='prepare') {write(path.join(root,'marketplace/listing.local.json'),prepareListing(root));console.log('Prepared marketplace/listing.local.json from metadata and local artwork.');return;}
  if(!['validate','preview'].includes(command)) throw Error('Commands: init, prepare, validate [listing.json] [generated-manifest.json] [highest-version-code], preview [listing.json]');
  const listing=contract.validateMarketplaceListing(read(rest[0]?path.resolve(rest[0]):path.join(root,'marketplace/listing.local.json')));
  validateFiles(listing,root);
  if(command==='validate') {
    requireComplete(listing);
    const manifest=fs.readFileSync(rest[1]?path.resolve(rest[1]):path.join(root,'packaging/build/signed/manifest.json'),'utf8');
    contract.bindMarketplaceListing(listing,listing,manifest,hash(manifest));
    if(rest[2]!==undefined){if(!/^\d+$/.test(rest[2])) throw Error('Highest version code must be a nonnegative integer');contract.requireMarketplaceVersion(listing,Number(rest[2]));}
    console.log('Listing shape, completeness, artwork and signed-manifest identity verified locally. Portal owns ID uniqueness, authorization and review.');
  } else {
    const files=fs.readdirSync(path.join(root,'marketplace/artwork')).filter(x=>x.endsWith('.png'));
    const urls=[listing.icon,...listing.screenshots].map(image=>{
      const f=files.find(f=>hash(fs.readFileSync(path.join(root,'marketplace/artwork',f)))===image.assetId);
      return 'data:image/png;base64,'+fs.readFileSync(path.join(root,'marketplace/artwork',f)).toString('base64');
    });
    const file=path.join(root,'marketplace/preview/card.html');fs.mkdirSync(path.dirname(file),{recursive:true});fs.writeFileSync(file,renderPreview(listing,urls));console.log('Open '+file+' in a browser.');
  }
}
module.exports={prepareListing,requireComplete,validateFiles,renderPreview,initialize};
if(require.main===module){try{main(process.argv.slice(2));}catch(error){console.error(error.message);process.exitCode=1;}}
