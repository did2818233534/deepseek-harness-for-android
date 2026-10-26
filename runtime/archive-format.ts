import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {gunzipSync,gzipSync} from 'node:zlib';
export const digest=(data:any)=>crypto.createHash('sha256').update(data).digest('hex');
export function archiveDir(root:string,id:string){if(!/^[a-zA-Z0-9_-]{1,100}$/.test(id))throw Error('Invalid session id');return path.join(root,'..','archives',id);}
export function atomicJson(file:string,data:any){const tmp=file+'.tmp';const fd=fs.openSync(tmp,'w',0o600);try{fs.writeFileSync(fd,JSON.stringify(data));fs.fsyncSync(fd);}finally{fs.closeSync(fd);}fs.renameSync(tmp,file);syncDir(path.dirname(file));}
export function syncDir(dir:string){const fd=fs.openSync(dir,'r');try{fs.fsyncSync(fd);}finally{fs.closeSync(fd);}}
export function packedData(root:string,id:string,stub:any){
 if(!/^(?:[a-f0-9-]+-)?session-[a-f0-9-]+\.json\.gz$/.test(stub.archiveRef?.snapshot||''))throw Error('归档索引无效');
 const bytes=gunzipSync(fs.readFileSync(path.join(archiveDir(root,id),stub.archiveRef.snapshot)));
 if(digest(bytes)!==stub.archiveRef.sha256)throw Error('归档聊天校验失败，原归档已保留');
 return JSON.parse(bytes.toString('utf8'));
}
// Metadata-only changes to a packed chat must not accidentally discard its archive index.
export function updatePacked(root:string,id:string,data:any,summary:any){
 const dir=archiveDir(root,id),bytes=Buffer.from(JSON.stringify(data));
 const snapshot='session-'+crypto.randomUUID()+'.json.gz',file=path.join(dir,snapshot);
 const compressed=gzipSync(bytes,{level:3});if(!gunzipSync(compressed).equals(bytes))throw Error('归档校验失败');
 const fd=fs.openSync(file,'wx',0o600);try{fs.writeFileSync(fd,compressed);fs.fsyncSync(fd);}finally{fs.closeSync(fd);}syncDir(dir);
 return {header:data.header,archiveRef:{snapshot,sha256:digest(bytes)},summary};
}
