import { fileIdentity } from "./file-identity.ts";
import { supportsImages } from "./vision.ts";
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
  if (!Array.isArray(input) || input.length > 8) throw Error('每条消息最多添加 8 个附件');
  let total = 0;
  return input.map(a => {
    const file = attachmentPath(workspace, a?.path);
    const bytes = fs.statSync(file).size;
    if (bytes > 16 * 1024 * 1024 || (total += bytes) > 32 * 1024 * 1024) throw Error('附件过大：单个最多 16 MB，总计最多 32 MB');
    const data = fs.readFileSync(file), mime = imageMime(data);
    if (String(a.mime || '').startsWith('image/') && a.mime !== 'image/svg+xml' && !mime) throw Error('图片格式不支持，请使用 PNG、JPEG、GIF 或 WebP');
    if (mime && !allowTemporarySwitch && !supportsImages(model)) throw Error('当前模型不支持图片，请切换到支持图片的 Flash 模型');
    const hash = crypto.createHash('sha256').update(data).digest('hex');
    // Immutable copies preserve prior turns when a working file is edited later.
    const dir = path.join(workspace, '.attachments');
    fs.mkdirSync(dir, {recursive:true});
    const stored = path.join(dir, hash);
    if (!fs.existsSync(stored)) fs.writeFileSync(stored, data, {flag:'wx'});
    const originalIdentity=fileIdentity(file);
    return {fileIdentity:originalIdentity?.sha256===hash?originalIdentity:undefined,storedIdentity:fileIdentity(stored),path:a.path, name:String(a.name || path.basename(a.path)).slice(0,255), mime:mime || String(a.mime || 'application/octet-stream'), bytes, attachmentId:'sha256:'+hash, storedPath:'.attachments/'+hash, mediaType:mime};
  });
}
export function attachmentImage(workspace: string, ref: any) {
  const file = attachmentPath(workspace, ref.storedPath);
  const data = fs.readFileSync(file);
  if ('sha256:'+crypto.createHash('sha256').update(data).digest('hex') !== ref.attachmentId) throw Error('图片附件已改变，请重新添加');
  return {type:'image_url', image_url:{url:`data:${ref.mediaType};base64,${data.toString('base64')}`}};
}
