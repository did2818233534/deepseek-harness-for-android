"""Preload supported modules before denying file/network/process syscalls."""
import base64, csv, ctypes, datetime, decimal, hashlib, io, itertools, json, math, os, re, statistics, sys, textwrap
from PIL import Image, ImageOps, ImageDraw, ImageEnhance, ImageFilter, ImageChops
Image.init()
utf8=''.encode('utf-8')
guard=ctypes.CDLL(sys.argv[1])
request=json.loads(sys.stdin.buffer.read())
# Code only starts after kernel enforcement succeeds on all worker threads.
if guard.dsh_lock()!=0:os._exit(125)
env=dict(globals());env['data']=base64.b64decode(request['data']);env['result']=b''
exec(request['code'],env)
result=env['result']
if isinstance(result,str):result=result.encode('utf-8')
if not isinstance(result,bytes):raise TypeError('result must be bytes or str')
sys.stdout.buffer.write(result)
