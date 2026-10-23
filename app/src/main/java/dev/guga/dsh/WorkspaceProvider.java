package dev.guga.dsh;

import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;

/** Exact URI grants expose only the selected workspace file, read-only. */
public final class WorkspaceProvider extends ContentProvider {
  public boolean onCreate(){return true;}
  static Uri uri(Context c,String path){return new Uri.Builder().scheme("content").authority(c.getPackageName()+".workspace").appendPath(path).build();}
  static File resolve(Context c,Uri uri)throws FileNotFoundException {
    try {
      if(!"content".equals(uri.getScheme())||!(c.getPackageName()+".workspace").equals(uri.getAuthority())||uri.getPathSegments().size()!=1)throw new IOException("无效的文件链接");
      File file=new FileTools(c).resolve(uri.getLastPathSegment());if(!file.isFile())throw new IOException("文件不存在或已删除");return file;
    }catch(Exception e){throw new FileNotFoundException(e.getMessage());}
  }
  public String getType(Uri uri){try{return WorkspaceFiles.mime(resolve(getContext(),uri).getName());}catch(Exception e){return "application/octet-stream";}}
  public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException {
    if(!"r".equals(mode))throw new FileNotFoundException("Read only");return ParcelFileDescriptor.open(resolve(getContext(),uri),ParcelFileDescriptor.MODE_READ_ONLY);
  }
  public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order){
    try {File file=resolve(getContext(),uri);String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;
      MatrixCursor cursor=new MatrixCursor(columns);Object[] values=new Object[columns.length];
      for(int n=0;n<columns.length;n++)values[n]=OpenableColumns.DISPLAY_NAME.equals(columns[n])?file.getName():OpenableColumns.SIZE.equals(columns[n])?file.length():null;
      cursor.addRow(values);return cursor;
    }catch(Exception e){return null;}
  }
  public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
  public int update(Uri uri,ContentValues values,String where,String[] args){throw new UnsupportedOperationException();}
  public int delete(Uri uri,String where,String[] args){throw new UnsupportedOperationException();}
}
