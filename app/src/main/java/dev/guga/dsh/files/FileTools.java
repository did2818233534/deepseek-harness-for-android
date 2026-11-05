package dev.guga.dsh.files;

import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import java.util.*;
import org.json.JSONArray;
import org.json.JSONObject;

public final class FileTools {

  public static final int MAX_BYTES = 16 * 1024 * 1024;
  public final File root;

  public FileTools(Context c) {
    root = new File(c.getFilesDir(), "workspace");
    root.mkdirs();
    com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(c);
  }

  public File resolve(String name) throws Exception {
    if (
      name.isEmpty() || name.startsWith("/") || name.contains("\\")
    ) throw new IOException("请使用工作区相对文件名");
    File f = new File(root, name).getCanonicalFile();
    if (
      !f.getPath().startsWith(root.getCanonicalPath() + File.separator)
    ) throw new IOException("不能访问工作区以外的文件");
    return f;
  }

  public void deleteLibraryFile(String name) throws Exception {
    File file=resolve(name);
    if(!file.exists())return;
    if(!file.isFile())throw new IOException("只能删除文件");
    Files.delete(file.toPath());
  }

  public JSONArray list() throws Exception {
    JSONArray out = new JSONArray();
    listDir(root, out, 0);
    return out;
  }

  private void listDir(File d, JSONArray out, int depth) throws Exception {
    if (depth > 8) return;
    File[] files = d.listFiles();
    if (files == null) return;
    Arrays.sort(files, Comparator.comparing(File::getName));
    for (File f : files) {
      if (f.getName().startsWith(".dsh-") || f.getName().equals(".attachments")) continue;
      if (f.isDirectory()) listDir(f, out, depth + 1);
      else out.put(
        new JSONObject()
          .put("path", root.toPath().relativize(f.toPath()).toString())
          .put("bytes", f.length())
      );
      if (out.length() >= 500) return;
    }
  }

  public synchronized String importFile(InputStream input, String name)
    throws Exception {
    if(name==null)name="导入文件.txt";
    name = name.replaceAll("[\\\\/\\p{Cntrl}]", "_");
    if (name.trim().isEmpty()) name = "导入文件.txt";
    File f = timestamped(resolve(name));
    File tmp = File.createTempFile(".dsh-import-", ".tmp", root);
    try (FileOutputStream out = new FileOutputStream(tmp)) {
      copy(input, out, Long.MAX_VALUE);
      out.getFD().sync();
    } catch (Exception e) {
      tmp.delete();
      throw e;
    }
    try{f=publishUnique(tmp,f);return f.getName();}finally{tmp.delete();}
  }

  public static byte[] readLimited(InputStream in, int limit) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    copy(in, out, limit);
    return out.toByteArray();
  }

  public static void copy(InputStream in, OutputStream out, long max)
    throws IOException {
    byte[] b = new byte[8192];
    int n;
    long total = 0;
    while ((n = in.read(b)) != -1) {
      total += n;
      if (total > max) throw new IOException(
        "文件超过大小限制（" + max / 1024 / 1024 + " MB）"
      );
      out.write(b, 0, n);
    }
  }

  public JSONObject read(JSONObject a) throws Exception {
    File f = resolve(a.getString("path"));
    if (!f.isFile()) throw new IOException("文件不存在");
    if (f.length() > MAX_BYTES) throw new IOException(
      "文件超过 16 MB，适合使用电脑处理"
    );
    String text = DocumentFormats.readText(f);
    int start = Math.max(0, Math.min(text.length(), a.optInt("offset", 0))),
      limit = Math.max(1, Math.min(40000, a.optInt("limit", 18000))),
      end = Math.min(text.length(), start + limit);
    return new JSONObject()
      .put("path", a.getString("path"))
      .put("text", text.substring(start, end))
      .put("totalChars", text.length())
      .put("nextOffset", end < text.length() ? end : JSONObject.NULL);
  }

  public synchronized JSONObject write(JSONObject a, boolean document)
    throws Exception {
    File f = timestamped(resolve(a.getString("path")));
    String content = a.getString("content");
    if (
      content.getBytes(StandardCharsets.UTF_8).length > 2 * 1024 * 1024
    ) throw new IOException("输出超过 2 MB");
    f.getParentFile().mkdirs();
    File tmp = File.createTempFile(".dsh-write-", ".tmp", f.getParentFile());
    try {
      String lower = f.getName().toLowerCase(Locale.ROOT);
      if (document) {
        if (lower.endsWith(".docx")) DocumentFormats.writeDocx(tmp, content);
        else if (lower.endsWith(".pdf")) DocumentFormats.writePdf(tmp, content);
        else throw new IOException("文档输出仅支持 DOCX/PDF");
      } else {
        if (
          !lower.matches(
            ".*\\.(txt|md|csv|tsv|json|html|htm|xml|log|yaml|yml|java|js|ts|py|css|svg)$"
          )
        ) throw new IOException(
          "请使用文本类扩展名；DOCX/PDF 请调用 create_document"
        );
        Files.write(tmp.toPath(), content.getBytes(StandardCharsets.UTF_8));
      }
      try (RandomAccessFile file = new RandomAccessFile(tmp, "rw")) {
        file.getFD().sync();
      }
      f=publishUnique(tmp,f);
    } finally {
      tmp.delete();
    }
    return new JSONObject()
      .put("path", root.getCanonicalFile().toPath().relativize(f.toPath()).toString())
      .put("bytes", f.length())
      .put("status", "已生成，可点击对话中的文件链接打开、分享或保存");
  }

  private File timestamped(File requested){
    String name=requested.getName();int dot=name.lastIndexOf('.');
    String base=dot>0?name.substring(0,dot):name,ext=dot>0?name.substring(dot):"";
    String time=new java.text.SimpleDateFormat("yyyyMMdd_HHmmss_SSS",Locale.ROOT).format(new Date());
    return new File(requested.getParentFile(),base+"_"+time+ext);
  }

  // CREATE_NEW atomically reserves an unused name; never truncate an existing file.
  private File publishUnique(File complete,File requested)throws Exception {
    String name=requested.getName();int dot=name.lastIndexOf('.');
    String base=dot>0?name.substring(0,dot):name,ext=dot>0?name.substring(dot):"";
    for(int suffix=0;;suffix++){
      File target=suffix==0?requested:new File(requested.getParentFile(),base+"("+suffix+")"+ext);
      java.nio.channels.FileChannel channel;
      try{channel=java.nio.channels.FileChannel.open(target.toPath(),java.nio.file.StandardOpenOption.CREATE_NEW,java.nio.file.StandardOpenOption.WRITE);}
      catch(java.nio.file.FileAlreadyExistsException occupied){continue;}
      Object identity=Files.readAttributes(target.toPath(),java.nio.file.attribute.BasicFileAttributes.class).fileKey();
      try(java.nio.channels.FileChannel output=channel;FileInputStream input=new FileInputStream(complete)){
        byte[] buffer=new byte[8192];int length;
        while((length=input.read(buffer))!=-1){java.nio.ByteBuffer bytes=java.nio.ByteBuffer.wrap(buffer,0,length);while(bytes.hasRemaining())output.write(bytes);}
        output.force(true);return target;
      }catch(Exception error){
        try{if(identity!=null&&identity.equals(Files.readAttributes(target.toPath(),java.nio.file.attribute.BasicFileAttributes.class).fileKey()))Files.delete(target.toPath());}catch(Exception ignored){}
        throw error;
      }
    }
  }

}
