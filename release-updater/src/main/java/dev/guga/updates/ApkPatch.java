package dev.guga.updates;

import java.io.*;
import java.security.*;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;

/** Bounded streaming copy/literal patch; preserves the signed APK byte for byte. */
public final class ApkPatch {
  private static final byte[] MAGIC={'D','S','H','P','A','T','0','1'};
  public static final long MAX_SIZE=512L*1024*1024;
  public static byte[] digest(File file) throws IOException {
    try {
      MessageDigest hash=MessageDigest.getInstance("SHA-256");
      try(InputStream in=new FileInputStream(file)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)hash.update(b,0,n);}
      return hash.digest();
    }catch(NoSuchAlgorithmException e){throw new IOException(e);}
  }
  public static void apply(File base,InputStream patch,File output) throws IOException {
    if(base.getCanonicalFile().equals(output.getCanonicalFile()))throw new IOException("不能覆盖原安装包");
    boolean complete=false;
    try {
      DataInputStream header=new DataInputStream(patch);
      byte[] magic=new byte[8],before=new byte[32],after=new byte[32];
      header.readFully(magic);if(!Arrays.equals(magic,MAGIC))throw new IOException("不是支持的更新补丁");
      header.readFully(before);header.readFully(after);long size=header.readLong();
      if(size<=0||size>MAX_SIZE)throw new IOException("补丁声明的安装包大小无效");
      if(!MessageDigest.isEqual(before,digest(base)))throw new IOException("补丁不适用于当前安装版本，请下载对应补丁或完整 APK");
      if(output.getParentFile().getUsableSpace()<size+16*1024*1024L)throw new IOException("空间不足，请预留至少一个完整安装包的空间");
      try(DataInputStream in=new DataInputStream(new GZIPInputStream(header));
          RandomAccessFile old=new RandomAccessFile(base,"r");FileOutputStream out=new FileOutputStream(output)) {
        long written=0;int operations=0;byte[] buffer=new byte[65536];
        while(true) {
          int kind=in.readUnsignedByte();if(kind==0)break;
          if(++operations>1000000)throw new IOException("补丁操作数量过多");
          long offset=kind==1?in.readLong():0;
          if(kind!=1&&kind!=2)throw new IOException("补丁操作无效");
          long length=in.readLong();
          if(length<=0||length>size-written)throw new IOException("补丁长度无效");
          if(kind==1){if(offset<0||offset>old.length()||length>old.length()-offset)throw new IOException("补丁引用越界");old.seek(offset);}
          long remaining=length;
          while(remaining>0) {
            int n=(int)Math.min(buffer.length,remaining);
            if(kind==1)old.readFully(buffer,0,n);else in.readFully(buffer,0,n);
            out.write(buffer,0,n);remaining-=n;
          }
          written+=length;
        }
        if(written!=size||in.read()!=-1)throw new IOException("补丁不完整或包含额外内容");
        out.getFD().sync();
      }
      if(!MessageDigest.isEqual(after,digest(output)))throw new IOException("合成文件校验失败，请重新下载补丁");
      complete=true;
    }catch(EOFException e){throw new IOException("补丁文件不完整，请重新下载",e);}
    finally{if(!complete)output.delete();}
  }
}
