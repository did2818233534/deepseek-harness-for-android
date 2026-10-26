import {build} from '/opt/dsh-android/build-tools/node_modules/esbuild/lib/main.js';
import fs from 'node:fs';import path from 'node:path';import os from 'node:os';import assert from 'node:assert/strict';import {createRequire} from 'node:module';
const home=fs.mkdtempSync(path.join(os.tmpdir(),'dsh-archives-'));
try{
 const entry=path.join(home,'entry.ts');fs.writeFileSync(entry,`export * from ${JSON.stringify(path.resolve('runtime/archives.ts'))};export * from ${JSON.stringify(path.resolve('runtime/archive-format.ts'))};export * from ${JSON.stringify(path.resolve('runtime/file-identity.ts'))};`);
 const bundle=path.join(home,'archive.cjs');await build({entryPoints:[entry],outfile:bundle,bundle:true,platform:'node',target:'node18',format:'cjs'});
 const {ConversationArchives,archiveDir,atomicJson,packedData,updatePacked,fileIdentity}=createRequire(import.meta.url)(bundle);
 const root=path.join(home,'runtime','sessions'),workspace=path.join(home,'workspace');fs.mkdirSync(root,{recursive:true});fs.mkdirSync(workspace);
 const store={
  raw:id=>JSON.parse(fs.readFileSync(path.join(root,id+'.json'),'utf8')),
  isPacked(id){return !!this.raw(id).archiveRef;},
  load(id){const raw=this.raw(id);return raw.archiveRef?packedData(root,id,raw):raw;},
  summary(id){return this.load(id);},remember(id,data){return {header:data.header,archived:data.archived,title:data.title};},
  save(id,data,plain=false){if(!plain&&fs.existsSync(path.join(root,id+'.json'))&&this.isPacked(id))data=updatePacked(root,id,data,this.remember(id,data));atomicJson(path.join(root,id+'.json'),data);},
  commitPacked(id,stub){this.save(id,stub,true);},setArchived(id,value){const d=this.load(id);d.archived=value;this.save(id,d);},
  async list(){return fs.readdirSync(root).filter(n=>n.endsWith('.json')).map(n=>({header:{id:n.slice(0,-5)}}));}
 };
 let changes=0;const manager=new ConversationArchives(store,root,workspace,()=>changes++);
 const write=(name,content)=>{fs.writeFileSync(path.join(workspace,name),content);return {path:name,name,fileIdentity:fileIdentity(path.join(workspace,name))};};
 const report=write('报告.txt','可压缩内容。'.repeat(20000)),pdf=write('图纸.pdf',Buffer.alloc(8192,17)),shared=write('共享.csv','a,b\n1,2'),legacy=write('旧文件.txt','legacy'),changed=write('同名新文件.txt','old');delete legacy.fileIdentity;
 const data={header:{id:'first'},events:[{type:'user/message',data:{source:{attachments:[report,pdf,shared,legacy,changed]}}},{type:'assistant/message',data:{message:{content:[{type:'text',text:'[报告](报告.txt)'}]}}}],title:'归档测试',archived:false};store.save('first',data);store.save('second',{header:{id:'second'},events:[{type:'user/message',data:{source:{attachments:[shared]}}}],archived:false});
 fs.writeFileSync(path.join(workspace,changed.path),'new unrelated file');
 manager.archive('first');await manager.wait('first');assert.equal(manager.status('first').state,'packed');assert(store.isPacked('first'));assert(changes>=2);
 const dir=archiveDir(root,'first'),manifest=JSON.parse(fs.readFileSync(path.join(dir,'manifest.json'))),entries=manifest.entries;
 assert.equal(entries.find(e=>e.path==='报告.txt').codec,'gzip');assert.equal(entries.find(e=>e.path==='图纸.pdf').codec,'store');assert.equal(entries.find(e=>e.path==='共享.csv').retained,'shared');assert.equal(entries.find(e=>e.path==='旧文件.txt').retained,'changed-or-legacy');
 assert(!fs.existsSync(path.join(workspace,report.path)));assert(!fs.existsSync(path.join(workspace,pdf.path)));for(const n of ['共享.csv','旧文件.txt','同名新文件.txt'])assert(fs.existsSync(path.join(workspace,n)));
 const saved=store.load('first');assert.equal(saved.events[1].data.message.content[0].text,'[报告](报告.txt)');saved.title='改名';store.save('first',saved);assert(store.isPacked('first'));assert.equal(store.load('first').title,'改名');
 fs.writeFileSync(path.join(workspace,'报告.txt'),'new file occupying original name');
 // New manager simulates restart after all original owned files were removed.
 const resumed=new ConversationArchives(store,root,workspace,()=>{});resumed.restore('first');await resumed.wait('first');assert.notEqual(resumed.status('first').state,'error');assert(!store.isPacked('first'));assert.equal(store.load('first').archived,false);
 const restored=store.load('first');const restoredName=restored.events[0].data.source.attachments[0].path;assert.equal(restoredName,'报告(1).txt');assert.equal(fs.readFileSync(path.join(workspace,restoredName),'utf8'),'可压缩内容。'.repeat(20000));assert.equal(fs.readFileSync(path.join(workspace,'报告.txt'),'utf8'),'new file occupying original name');assert.equal(restored.events[1].data.message.content[0].text,'[报告](报告(1).txt)');assert(!fs.existsSync(dir));
 resumed.archive('first');await resumed.wait('first');assert(store.isPacked('first'));const again=JSON.parse(fs.readFileSync(path.join(dir,'manifest.json')));const payload=path.join(dir,again.entries.find(e=>e.path===restoredName).file);const bytes=fs.readFileSync(payload);fs.writeFileSync(payload,'corrupt');resumed.restore('first');await resumed.wait('first');assert.equal(resumed.status('first').state,'error');assert(store.isPacked('first'));assert(fs.existsSync(dir));fs.writeFileSync(payload,bytes);resumed.restore('first');await resumed.wait('first');assert(!store.isPacked('first'));assert(!fs.existsSync(dir));
 const owned=write('失败保护.md','data '.repeat(1000));store.save('failure',{header:{id:'failure'},events:[{type:'user/message',data:{source:{attachments:[owned]}}}]});const commit=store.commitPacked.bind(store);store.commitPacked=()=>{throw Error('simulated commit failure');};resumed.archive('failure');await resumed.wait('failure');assert(fs.existsSync(path.join(workspace,owned.path)));assert(!store.isPacked('failure'));store.commitPacked=commit;resumed.archive('failure');await resumed.wait('failure');assert(store.isPacked('failure'));await resumed.unpack('failure');assert(fs.existsSync(path.join(workspace,owned.path)));
 console.log('PASS: mixed codecs, verified commit, shared/legacy/replaced file preservation, restart, metadata updates, collision restore and links, repeat archive, corruption recovery, commit failure and retry.');
}finally{fs.rmSync(home,{recursive:true,force:true});}
