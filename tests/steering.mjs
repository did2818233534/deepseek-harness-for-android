import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {createRequire} from 'node:module';
import {tool} from '../scripts/build-tools.mjs';
const {build}=await tool('esbuild/lib/main.js');
const dir=fs.mkdtempSync(path.resolve('.local/steering-test-'));
try {
  const out=path.join(dir,'steering.cjs');
  await build({entryPoints:['runtime/providers/steering.ts'],outfile:out,bundle:true,platform:'node',format:'cjs',target:'node18'});
  const {deliverGuidance}=createRequire(import.meta.url)(out);
  const queue=[],message={content:[{type:'text',text:'继续并缩短回答'}],source:{steerId:'stable-id'}};
  const local=m=>queue.push(m);
  assert.equal(await deliverGuidance(message,local),'local');assert.equal(queue.pop(),message);
  let nativeCalls=0;
  assert.equal(await deliverGuidance(message,local,{steer:async m=>{assert.equal(m,message);nativeCalls++;return 'accepted';}}),'native');
  assert.equal(nativeCalls,1);assert.equal(queue.length,0);
  for(const native of [
    {steer:async()=> 'unsupported'},
    {steer:async()=> {throw Error('relay HTTP 404');}},
    {steer:async()=> {throw Error('relay HTTP 503');}},
    {steer:async()=> {throw Error('request timeout');}},
    {steer:async()=> undefined}
  ]) {
    for(let repeat=0;repeat<2;repeat++) {
      assert.equal(await deliverGuidance(message,local,native),'local');
      assert.equal(queue.length,1);assert.equal(queue.pop().source.steerId,'stable-id');
    }
  }
  await assert.rejects(deliverGuidance(message,()=>{throw Error('queue unavailable');}),/queue unavailable/);
  console.log('PASS: native acceptance without duplicate queue, unsupported/relay error/timeout/malformed fallback, stable message identity, repeated delivery, local error propagation');
} finally {fs.rmSync(dir,{recursive:true,force:true});}
