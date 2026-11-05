import fs from 'node:fs';
import crypto from 'node:crypto';

// Bind an ownership record to both file contents and this filesystem incarnation.
export function fileIdentity(file:string){
  const before=fs.lstatSync(file);
  if(!before.isFile()||before.isSymbolicLink())return undefined;
  const hash=crypto.createHash('sha256'), buffer=Buffer.allocUnsafe(65536);
  const fd=fs.openSync(file,'r');
  try { let size; while((size=fs.readSync(fd,buffer,0,buffer.length,null))>0) hash.update(buffer.subarray(0,size)); }
  finally { fs.closeSync(fd); }
  const sha256=hash.digest('hex');
  const after=fs.lstatSync(file);
  const stamp=(s:any)=>[s.dev,s.ino,s.size,s.mtimeMs,s.ctimeMs,s.birthtimeMs].join(':');
  if(stamp(before)!==stamp(after))return undefined;
  return {stamp:stamp(after),sha256};
}
export function sameFileIdentity(file:string,expected:any){
  if(!expected||typeof expected.stamp!=='string'||typeof expected.sha256!=='string')return false;
  const actual=fileIdentity(file);
  return !!actual&&actual.stamp===expected.stamp&&actual.sha256===expected.sha256;
}
