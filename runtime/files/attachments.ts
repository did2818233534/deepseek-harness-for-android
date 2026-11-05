import { fileIdentity } from "./file-identity.ts";
import { supportsImages } from "../providers/vision.ts";
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';

export function attachmentPath(workspace: string, name: string) {
  if (typeof name !== 'string' || !name || path.isAbsolute(name)) throw Error('附件路径无效');
  const base = fs.realpathSync(workspace);
  const file = fs.realpathSync(path.resolve(base, name));
  if (!file.startsWith(base + path.sep) || !fs.statSync(file).isFile()) throw Error('附件路径无效');
  return file;
}
function imageMime(b: Buffer) {
  if (b.subarray(0, 8).equals(Buffer.from([137,80,78,71,13,10,26,10]))) return 'image/png';
  if (b[0] === 255 && b[1] === 216 && b[2] === 255) return 'image/jpeg';
  if (/^GIF8[79]a$/.test(b.subarray(0,6).toString('ascii'))) return 'image/gif';
  if (b.subarray(0,4).toString() === 'RIFF' && b.subarray(8,12).toString() === 'WEBP') return 'image/webp';
  return '';
}
export function prepareAttachments(workspace: string, input: any, model: string, allowTemporarySwitch = false) {
  if (input === undefined) return [];
  if (!Array.isArray(input)) throw Error('附件列表格式无效');
  return input.map(a => {
    const file = attachmentPath(workspace, a?.path);
    const bytes = fs.statSync(file).size;
    const header=Buffer.alloc(12), fd=fs.openSync(file,'r');
    try { fs.readSync(fd,header,0,12,0); } finally { fs.closeSync(fd); }
    const mime=imageMime(header);
    if (String(a.mime || '').startsWith('image/') && a.mime !== 'image/svg+xml' && !mime) throw Error('图片格式不支持，请使用 PNG、JPEG、GIF 或 WebP');
    if (mime && !allowTemporarySwitch && !supportsImages(model)) throw Error('当前模型不支持图片，请切换到支持图片的 Flash 模型');
    const originalIdentity=fileIdentity(file);
    if(!originalIdentity)throw Error('附件正在修改，请重新添加');
    const hash=originalIdentity.sha256;
    // Immutable copies preserve prior turns when a working file is edited later.
    const dir = path.join(workspace, '.attachments');
    fs.mkdirSync(dir, {recursive:true});
    const stored = path.join(dir, hash);
    if (!fs.existsSync(stored)) {
      const temp=path.join(dir,'.tmp-'+crypto.randomUUID());
      try {
        fs.copyFileSync(file,temp);
        if(fileIdentity(temp)?.sha256!==hash)throw Error('附件正在修改，请重新添加');
        fs.renameSync(temp,stored);
      } finally { fs.rmSync(temp,{force:true}); }
    }
    return {fileIdentity:originalIdentity?.sha256===hash?originalIdentity:undefined,storedIdentity:fileIdentity(stored),path:a.path, name:String(a.name || path.basename(a.path)).slice(0,255), mime:mime || String(a.mime || 'application/octet-stream'), bytes, attachmentId:'sha256:'+hash, storedPath:'.attachments/'+hash, mediaType:mime};
  });
}
export function attachmentImage(workspace: string, ref: any) {
  const file = attachmentPath(workspace, ref.storedPath);
  const data = fs.readFileSync(file);
  if ('sha256:'+crypto.createHash('sha256').update(data).digest('hex') !== ref.attachmentId) throw Error('图片附件已改变，请重新添加');
  return {type:'image_url', image_url:{url:`data:${ref.mediaType};base64,${data.toString('base64')}`}};
}
