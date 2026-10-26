import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {pipeline} from 'node:stream/promises';
import {createGzip,createGunzip} from 'node:zlib';
import {Writable} from 'node:stream';
import {ownedFileIdentities} from './file-cleanup.ts';
import {fileIdentity,sameFileIdentity} from './file-identity.ts';
import {archiveDir,atomicJson,digest,syncDir} from './archive-format.ts';

const textExtension=/\.(txt|md|markdown|csv|tsv|json|jsonl|html?|xml|log|ya?ml|java|js|ts|py|css|svg)$/i;
async function checksum(file:string,gzip=false){let bytes=0;const hash=crypto.createHash('sha256');const sink=new Writable({write(chunk,enc,done){bytes+=chunk.length;hash.update(chunk);done();}});if(gzip)await pipeline(fs.createReadStream(file),createGunzip(),sink);else await pipeline(fs.createReadStream(file),sink);return {sha256:hash.digest('hex'),bytes};}
async function durable(file:string){const fd=await fs.promises.open(file,'r');try{await fd.sync();}finally{await fd.close();}}
function local(base:string,name:string){if(typeof name!=='string'||!name||path.isAbsolute(name)||name.includes('\\')||name.includes(':')||name.split('/').includes('..'))throw Error('归档文件路径无效');const file=path.resolve(base,name);if(!file.startsWith(base+path.sep))throw Error('归档文件路径无效');return file;}
function mentions(events:any[],name:string){const text=JSON.stringify(events);return text.includes(JSON.stringify(name).slice(1,-1))||text.includes(encodeURI(name))||text.includes(encodeURIComponent(name));}
function remap(value:any,mapping:Map<string,any>):any{
 if(typeof value==='string'){
  if(mapping.has(value))return mapping.get(value).path;
  if(/^[\[{]/.test(value.trim()))try{return JSON.stringify(remap(JSON.parse(value),mapping));}catch{}
  for(const [old,ref] of mapping)if(old!==ref.path){value=value.split('('+old+')').join('('+ref.path+')').split('('+encodeURI(old)+')').join('('+encodeURI(ref.path)+')').split(JSON.stringify(old)).join(JSON.stringify(ref.path));}
  return value;
 }
 if(Array.isArray(value))return value.map(v=>remap(v,mapping));
 if(value&&typeof value==='object'){
  const out:any={};for(const [k,v] of Object.entries(value))out[k]=remap(v,mapping);
  if(mapping.has(value.path)){out.fileIdentity=mapping.get(value.path).identity;if('name' in out)out.name=path.basename(out.path);}
  if(mapping.has(value.storedPath))out.storedIdentity=mapping.get(value.storedPath).identity;
  return out;
 }
 return value;
}
export class ConversationArchives {
 readonly jobs=new Map<string,Promise<void>>();readonly states=new Map<string,{state:string,error?:string}>();
 private queue=Promise.resolve();
 constructor(private store:any,private sessionsRoot:string,private workspace:string,private changed:()=>void,private extraReferences:()=>any[][]=()=>[]){fs.mkdirSync(path.join(sessionsRoot,'..','archives'),{recursive:true});}
 get activeCount(){return this.jobs.size;}
 status(id:string){return this.states.get(id)||{state:this.store.isPacked(id)?'packed':''};}
 busy(id:string){return this.jobs.has(id);}
 wait(id:string){return this.jobs.get(id)||Promise.resolve();}
 private schedule(id:string,state:string,run:()=>Promise<void>){
  if(this.jobs.has(id))throw Error('该对话正在归档或恢复，请稍后再试');
  this.states.set(id,{state});
  const task=this.queue.then(run).then(()=>{this.states.delete(id);},error=>{this.states.set(id,{state:'error',error:String(error.message||error)});}).finally(()=>{this.jobs.delete(id);this.changed();});
  this.jobs.set(id,task);this.queue=task;this.changed();return task;
 }
 archive(id:string){this.store.setArchived(id,true);if(this.store.isPacked(id))return;this.schedule(id,'packing',()=>this.pack(id));}
 restore(id:string){this.schedule(id,'restoring',async()=>{await this.unpack(id);this.store.setArchived(id,false);});}
 async references(id:string){const sessions=await this.store.list();return sessions.filter(s=>s.header.id!==id).map(s=>this.store.load(s.header.id).events).concat(this.extraReferences());}
 private async pack(id:string){
  const data=this.store.load(id);if(!data.archived)return;
  const initial=JSON.stringify(data),dir=archiveDir(this.sessionsRoot,id);fs.rmSync(dir,{recursive:true,force:true});fs.mkdirSync(dir,{recursive:true});
  // An uncommitted folder is not authoritative. Originals remain untouched until commit.
  const token=crypto.randomUUID(),work=path.join(dir,'pending-'+token);fs.mkdirSync(work);
  const entries:any[]=[];let committed=false;
  try{
   const others=await this.references(id),base=fs.realpathSync(this.workspace);
   const textSnapshots=new Set<string>();
   for(const event of data.events)for(const a of event.data?.source?.attachments||[])if(textExtension.test(a.name||a.path||'')||String(a.mime).startsWith('text/'))textSnapshots.add(a.storedPath);
   for(const [name,identities] of ownedFileIdentities(data.events)){
    const entry:any={path:name};entries.push(entry);
    if(others.some(events=>mentions(events,name))){entry.retained='shared';continue;}
    const source=local(base,name);
    if(!fs.existsSync(source)){entry.retained='missing';continue;}
    if(fs.realpathSync(source)!==source){entry.retained='unverified';continue;}
    const identity=identities.find(i=>sameFileIdentity(source,i));
    if(!identity){entry.retained='changed-or-legacy';continue;}
    const compress=textExtension.test(name)||textSnapshots.has(name);entry.codec=compress?'gzip':'store';entry.identity=identity;entry.sha256=identity.sha256;
    entry.file='file-'+entries.length+(compress?'.gz':'.raw');let dest=path.join(work,entry.file);
    if(compress)await pipeline(fs.createReadStream(source),createGzip({level:3}),fs.createWriteStream(dest,{flags:'wx'}));else await fs.promises.copyFile(source,dest,fs.constants.COPYFILE_EXCL);
    let verified=await checksum(dest,compress);if(verified.sha256!==identity.sha256||!sameFileIdentity(source,identity))throw Error('文件在归档时发生变化，原文件已保留，请重试');
    // Tiny or incompressible text is stored verbatim instead of wasting space.
    if(compress&&fs.statSync(dest).size>=verified.bytes){await fs.promises.unlink(dest);entry.codec='store';entry.file='file-'+entries.length+'.raw';dest=path.join(work,entry.file);await fs.promises.copyFile(source,dest,fs.constants.COPYFILE_EXCL);verified=await checksum(dest);if(verified.sha256!==identity.sha256)throw Error('文件校验失败');}
    entry.bytes=verified.bytes;await durable(dest);
   }
   const snapshot='session-'+token+'.json.gz';await fs.promises.writeFile(path.join(work,'session-source.tmp'),initial,{flag:'wx'});
   await pipeline(fs.createReadStream(path.join(work,'session-source.tmp')),createGzip({level:3}),fs.createWriteStream(path.join(work,snapshot),{flags:'wx'}));
   const check=await checksum(path.join(work,snapshot),true);if(check.sha256!==digest(initial))throw Error('聊天归档校验失败');await durable(path.join(work,snapshot));await fs.promises.unlink(path.join(work,'session-source.tmp'));
   if(JSON.stringify(this.store.load(id))!==initial)throw Error('对话在归档时发生变化，请重试');
   const latest=await this.references(id);
   for(const entry of entries)if(entry.file&&latest.some(events=>mentions(events,entry.path)))entry.retained='shared';
   // All bytes are verified before publishing the index; crashes can only leave extra copies.
   for(const file of fs.readdirSync(work))fs.renameSync(path.join(work,file),path.join(dir,token+'-'+file));
   for(const entry of entries)if(entry.file)entry.file=token+'-'+entry.file;
   const manifest={version:1,entries};atomicJson(path.join(dir,'manifest.json'),manifest);syncDir(dir);
   const summary=this.store.remember(id,data);
   this.store.commitPacked(id,{header:data.header,summary,archiveRef:{snapshot:token+'-'+snapshot,sha256:check.sha256}});committed=true;
   for(const entry of entries)if(entry.file&&!entry.retained){const source=local(base,entry.path);try{if(sameFileIdentity(source,entry.identity))fs.unlinkSync(source);}catch(e){if(e.code!=='ENOENT')throw e;}}
   syncDir(base);
  }finally{fs.rmSync(work,{recursive:true,force:true});if(!committed){/* Originals are still authoritative; retry may replace the unused index. */}}
 }
 async unpack(id:string){
  if(!this.store.isPacked(id))return;
  const data=this.store.load(id),dir=archiveDir(this.sessionsRoot,id),manifest=JSON.parse(fs.readFileSync(path.join(dir,'manifest.json'),'utf8'));
  if(manifest.version!==1||!Array.isArray(manifest.entries))throw Error('归档目录索引无效');
  const base=fs.realpathSync(this.workspace),mapping=new Map<string,any>();
  for(const entry of manifest.entries){
   if(!entry.file)continue;
   if(!/^[a-f0-9-]+-file-\d+\.(gz|raw)$/.test(entry.file)||!['gzip','store'].includes(entry.codec))throw Error('归档文件索引无效');
   const source=path.join(dir,entry.file),verified=await checksum(source,entry.codec==='gzip');
   if(verified.sha256!==entry.sha256||verified.bytes!==entry.bytes)throw Error('归档文件校验失败，归档已保留');
  }
  for(const entry of manifest.entries){
   if(!entry.file)continue;
   const target=local(base,entry.path);let restored:string|undefined;
   if(entry.restored){const previous=local(base,entry.restored.path);if(fs.existsSync(previous)&&sameFileIdentity(previous,entry.restored.identity))restored=previous;}
   if(!restored&&fs.existsSync(target)&&sameFileIdentity(target,entry.identity))restored=target;
   if(!restored){
    fs.mkdirSync(path.dirname(target),{recursive:true});const parent=fs.realpathSync(path.dirname(target));if(parent!==base&&!parent.startsWith(base+path.sep))throw Error('恢复路径无效');
    const tmp=path.join(base,'.dsh-restore-'+crypto.randomUUID()+'.tmp');
    try{
     if(entry.codec==='gzip')await pipeline(fs.createReadStream(path.join(dir,entry.file)),createGunzip(),fs.createWriteStream(tmp,{flags:'wx'}));else await fs.promises.copyFile(path.join(dir,entry.file),tmp,fs.constants.COPYFILE_EXCL);
     if((await checksum(tmp)).sha256!==entry.sha256)throw Error('恢复文件校验失败');
     const ext=path.extname(target),stem=target.slice(0,target.length-ext.length);
     for(let n=0;;n++){const candidate=n?stem+'('+n+')'+ext:target;try{await fs.promises.copyFile(tmp,candidate,fs.constants.COPYFILE_EXCL);restored=candidate;break;}catch(e){if(e.code!=='EEXIST')throw e;}}
     await durable(restored!);if((await checksum(restored!)).sha256!==entry.sha256)throw Error('恢复文件校验失败');
    }finally{fs.rmSync(tmp,{force:true});}
   }
   syncDir(path.dirname(restored!));
   const ref={path:path.relative(base,restored!),identity:fileIdentity(restored!)};mapping.set(entry.path,ref);entry.restored=ref;atomicJson(path.join(dir,'manifest.json'),manifest);
  }
  const restored=remap(data,mapping);restored.archived=false;
  this.store.save(id,restored,true); // Durable plain session first; archive removal is last.
  fs.rmSync(dir,{recursive:true,force:true});
 }
 remove(id:string){if(this.busy(id))throw Error('请等待归档或恢复完成后再删除');fs.rmSync(archiveDir(this.sessionsRoot,id),{recursive:true,force:true});this.states.delete(id);}
}
