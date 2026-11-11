import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {createRequire} from 'node:module';
import {tool} from '../scripts/build-tools.mjs';
const {build}=await tool('esbuild/lib/main.js');
const dir=fs.mkdtempSync(path.resolve('.local/reasoning-test-'));
try {
  const out=path.join(dir,'reasoning.cjs');
  await build({entryPoints:['runtime/providers/reasoning.ts'],outfile:out,bundle:true,platform:'node',format:'cjs',target:'node18'});
  const {reasoningOptions:options,nativeThinking:config,thinkingRejected}=createRequire(import.meta.url)(out);
  for(const model of ['deepseek-flash','deepseek-v4-pro','deepseek-v4.1-flash'])assert.deepEqual(options('deepseek',model).levels,['none','low','high','max']);
  for(const model of ['claude-opus-5-5','claude-opus-5.5','claude-opus-5-5-latest']) {
    assert.deepEqual(options('anthropic',model).levels,['low','medium','high','xhigh','max']);
    assert.equal(options('anthropic',model).default,'medium');
    assert.equal(config('anthropic',model,'none').thinking.type,'adaptive');
    for(const effort of options('anthropic',model).levels)assert.equal(config('anthropic',model,effort).output_config.effort,effort);
  }
  assert(!options('anthropic','claude-sonnet-4-6').levels.includes('xhigh'));
  assert.equal(config('anthropic','claude-sonnet-5-5','none').thinking.type,'between_tools');
  for(const model of ['custom-alias','claude-opus-5-6','claude-opus-50'])assert.deepEqual(config('anthropic',model,'max'),{});
  assert.deepEqual(config('deepseek','unknown','high'),{});
  assert(thinkingRejected(Error('模型 API 400: output_config.effort not supported')));
  for(const message of ['401 invalid api key effort','503 overloaded','模型 API 503: thinking unsupported','模型 API 401: thinking unsupported','prompt is too long thinking 400','network timeout','canceled'])assert(!thinkingRejected(Error(message)));
  console.log('PASS: shared model capability table, five Claude levels, DeepSeek scale, aliases/dates, unknown models ignored, timeout lowest valid setting, optional-parameter rejection classification');
} finally {fs.rmSync(dir,{recursive:true,force:true});}
