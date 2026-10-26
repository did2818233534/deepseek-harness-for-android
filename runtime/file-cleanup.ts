import { sameFileIdentity } from "./file-identity.ts";
import fs from 'node:fs';
import path from 'node:path';

// Only attachment copies and successfully generated artifacts are owned by a chat.
export function ownedFileIdentities(events: any[]): Map<string,any[]> {
  const files=new Map<string,any[]>(), calls=new Map<string,string>();
  const add=(p:any,identity?:any)=>{if(typeof p==='string' && p && !path.isAbsolute(p) && !p.includes('\\') && !p.split('/').includes('..') && !p.includes(':'))files.set(p,[...(files.get(p)||[]),identity]);};
  for(const e of events){
    if(e.type==='user/message')for(const a of e.data?.source?.attachments||[]){add(a.path,a.fileIdentity);add(a.storedPath,a.storedIdentity);}
    if(e.type==='tool/call')calls.set(e.data.callId,e.data.name);
    if(e.type==='tool/result' && !e.data.error && !e.data.message?.isError && ['write_file','create_document','edit_image'].includes(calls.get(e.data.message?.toolCallId)||'')){
      for(const b of e.data.message.content||[])if(b.type==='text')try{const output=JSON.parse(b.text);add(output.path,output.fileIdentity);}catch{}
    }
  }
  return files;
}
export function ownedFiles(events:any[]){return new Set(ownedFileIdentities(events).keys());}
export function cleanupSessionFiles(workspace:string,events:any[],otherEvents:any[][]){
  if(!fs.existsSync(workspace))return;
  const base=fs.realpathSync(workspace);
  // Include tool arguments, Markdown links, archived chats and inherited fork events.
  const protectedText=otherEvents.map(e=>JSON.stringify(e));
  const protectedPaths=new Set<string>();
  for(const es of otherEvents)for(const p of ownedFiles(es))try{protectedPaths.add(fs.realpathSync(path.resolve(base,p)));}catch{}
  const candidates:Array<{file:string,identities:any[]}>=[];
  for(const [name,identities] of ownedFileIdentities(events)){
    if(protectedText.some(s=>s.includes(JSON.stringify(name).slice(1,-1))||s.includes(encodeURI(name))||s.includes(encodeURIComponent(name))))continue;
    const file=path.resolve(base,name);
    if(!file.startsWith(base+path.sep))continue;
    try{
      const real=fs.realpathSync(file);
      // Never follow a link outside app-private workspace or remove a directory.
      if(real!==file || !real.startsWith(base+path.sep) || protectedPaths.has(real) || !fs.lstatSync(file).isFile())continue;
      if(identities.some(identity=>sameFileIdentity(file,identity)))candidates.push({file,identities});
    }catch(e){if(e.code!=='ENOENT')throw e;}
  }
  for(const {file,identities} of candidates)try{if(identities.some(identity=>sameFileIdentity(file,identity)))fs.unlinkSync(file);}catch(e){if(e.code!=='ENOENT')throw Error('关联文件清理失败，请重试或关闭文件清理后删除对话');}
}
