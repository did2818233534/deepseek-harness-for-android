import {build} from '/opt/dsh-android/build-tools/node_modules/esbuild/lib/main.js';
import fs from 'node:fs';import os from 'node:os';import path from 'node:path';import assert from 'node:assert/strict';import {createRequire} from 'node:module';
const dir=fs.mkdtempSync(path.join(os.tmpdir(),'dsh-cleanup-'));
try{
 const out=path.join(dir,'cleanup.cjs');await build({entryPoints:['runtime/file-cleanup.ts'],outfile:out,bundle:true,platform:'node',format:'cjs',target:'node18'});
 const {cleanupSessionFiles}=createRequire(import.meta.url)(out);
 const identityOut=path.join(dir,'identity.cjs');await build({entryPoints:['runtime/file-identity.ts'],outfile:identityOut,bundle:true,platform:'node',format:'cjs'});const {fileIdentity}=createRequire(import.meta.url)(identityOut);
 const workspace=path.join(dir,'workspace');fs.mkdirSync(workspace);fs.mkdirSync(path.join(workspace,'.attachments'));
 const write=p=>fs.writeFileSync(path.join(workspace,p),'data');
 const attachments=names=>[{type:'user/message',data:{source:{attachments:names.map(name=>({path:name,fileIdentity:fs.existsSync(path.join(workspace,name))?fileIdentity(path.join(workspace,name)):undefined}))}}}];
 const generated=()=>[{type:'tool/call',data:{callId:'1',name:'write_file'}},{type:'tool/result',data:{message:{toolCallId:'1',content:[{type:'text',text:JSON.stringify({path:'研究报告.md',fileIdentity:fileIdentity(path.join(workspace,'研究报告.md'))})}]}}}];
 for(const p of ['photo.jpg','shared.jpg','.attachments/hash','研究报告.md','unrelated.txt'])write(p);
 const original=path.join(dir,'相册原图.jpg');fs.writeFileSync(original,'original');fs.symlinkSync(original,path.join(workspace,'outside.jpg'));
 const events=[...attachments(['photo.jpg','shared.jpg','.attachments/hash','outside.jpg','../相册原图.jpg',original]),...generated()];
 cleanupSessionFiles(workspace,events,[attachments(['shared.jpg','.attachments/hash'])]);
 assert(!fs.existsSync(path.join(workspace,'photo.jpg')));assert(!fs.existsSync(path.join(workspace,'研究报告.md')));
 for(const p of ['shared.jpg','.attachments/hash','unrelated.txt','outside.jpg'])assert(fs.existsSync(path.join(workspace,p)));
 assert.equal(fs.readFileSync(original,'utf8'),'original');
 cleanupSessionFiles(workspace,events,[]);cleanupSessionFiles(workspace,events,[]);
 assert(!fs.existsSync(path.join(workspace,'shared.jpg')));assert(fs.existsSync(original));
 write('研究报告.md');cleanupSessionFiles(workspace,generated(),[[{type:'assistant/message',data:{text:'[报告](研究报告.md)'}}]]);assert(fs.existsSync(path.join(workspace,'研究报告.md')));
 // A reused pathname must not confer ownership of a replacement file.
 write('报告.pdf');const oldChat=attachments(['报告.pdf']);fs.unlinkSync(path.join(workspace,'报告.pdf'));fs.writeFileSync(path.join(workspace,'报告.pdf'),'replacement');
 cleanupSessionFiles(workspace,oldChat,[]);assert.equal(fs.readFileSync(path.join(workspace,'报告.pdf'),'utf8'),'replacement');
 // Even identical contents at a freshly imported pathname belong to the new file.
 const sameContentChat=attachments(['报告.pdf']);const fresh=path.join(workspace,'fresh.tmp');fs.writeFileSync(fresh,'replacement');fs.renameSync(fresh,path.join(workspace,'报告.pdf'));
 cleanupSessionFiles(workspace,sameContentChat,[]);assert(fs.existsSync(path.join(workspace,'报告.pdf')));
 const unmodified=attachments(['报告.pdf']);cleanupSessionFiles(workspace,unmodified,[]);assert(!fs.existsSync(path.join(workspace,'报告.pdf')));
 write('研究报告.md');const oldGenerated=generated();fs.writeFileSync(path.join(workspace,'研究报告.md'),'new report');cleanupSessionFiles(workspace,oldGenerated,[]);assert(fs.existsSync(path.join(workspace,'研究报告.md')));
 // Legacy records without an identity fail closed rather than guessing ownership.
 cleanupSessionFiles(workspace,[{type:'user/message',data:{source:{attachments:[{path:'研究报告.md'}]}}}],[]);assert(fs.existsSync(path.join(workspace,'研究报告.md')));
 console.log('File cleanup tests passed: copies, shared/fork references, generated files, originals, symlinks, unrelated files, retry.');
}finally{fs.rmSync(dir,{recursive:true,force:true});}
