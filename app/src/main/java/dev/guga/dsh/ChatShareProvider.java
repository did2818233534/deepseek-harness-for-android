package dev.guga.dsh;

import android.content.*;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;

/** Only generated Markdown exports may be granted to another application, read-only. */
public final class ChatShareProvider extends ContentProvider {
  public boolean onCreate(){return true;}
  static File directory(Context c){return new File(c.getCacheDir(),"chat-exports");}
  static File resolve(Context c,Uri uri) throws FileNotFoundException {
    String name=uri.getLastPathSegment();
    if(!"content".equals(uri.getScheme()) || !(c.getPackageName()+".chat-share").equals(uri.getAuthority()) || uri.getPathSegments().size()!=1 || name==null || !name.matches("chat-[a-f0-9-]+\\.md")) throw new FileNotFoundException("Invalid export");
    File file=new File(directory(c),name);if(!file.isFile())throw new FileNotFoundException("Export unavailable");return file;
  }
  public String getType(Uri uri){return "text/markdown";}
  public ParcelFileDescriptor openFile(Uri uri,String mode) throws FileNotFoundException {
    if(!"r".equals(mode))throw new FileNotFoundException("Read only");
    return ParcelFileDescriptor.open(resolve(getContext(),uri),ParcelFileDescriptor.MODE_READ_ONLY);
  }
  public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order) {
    try {File file=resolve(getContext(),uri);String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;
      MatrixCursor cursor=new MatrixCursor(columns);Object[] row=new Object[columns.length];
      for(int n=0;n<columns.length;n++)row[n]=OpenableColumns.DISPLAY_NAME.equals(columns[n])?"对话记录.md":OpenableColumns.SIZE.equals(columns[n])?file.length():null;
      cursor.addRow(row);return cursor;
    }catch(FileNotFoundException e){return null;}
  }
  public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
  public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
  public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
}
