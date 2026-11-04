package dev.guga.dsh;

import android.widget.*;
import java.util.*;

/** Small, activity-owned LRU of detached transcripts. SVG renderers release on detach. */
final class TranscriptViews {
  final MainActivity host;
  final LinkedHashMap<String,Entry> cached=new LinkedHashMap<>();
  String active="";
  static final class Entry {
    final LinearLayout body;final Map<String,ChatTurnView> turns;final TextView error;
    Entry(MainActivity h){body=h.messages;turns=new LinkedHashMap<>(h.turnViews);error=h.turnError;}
    long bitmapBytes(android.view.View view){
      long size=0;if(view instanceof ImageView){var drawable=((ImageView)view).getDrawable();if(drawable instanceof android.graphics.drawable.BitmapDrawable)size=((android.graphics.drawable.BitmapDrawable)drawable).getBitmap().getAllocationByteCount();else if(drawable instanceof android.graphics.drawable.Animatable)size=4*1024*1024;}
      if(view instanceof android.view.ViewGroup){var group=(android.view.ViewGroup)view;for(int n=0;n<group.getChildCount();n++)size+=bitmapBytes(group.getChildAt(n));}return size;
    }
    long bytes(){return characters()*4L+turns.size()*12000L+bitmapBytes(body);}
    int characters(){int count=0;for(ChatTurnView v:turns.values())count+=v.payload==null?0:v.payload.length();return count;}
  }
  TranscriptViews(MainActivity host){this.host=host;}
  void activate(String id){
    host.messageActions.clear();
    if(active.equals(id))return;
    Entry next=cached.remove(id);
    if(!active.isEmpty()&&!host.turnViews.isEmpty())cached.put(active,new Entry(host));
    host.scroll.removeView(host.messages);
    host.turnViews.clear();
    if(next!=null){host.messages=next.body;host.turnViews.putAll(next.turns);host.turnError=next.error;}
    else{host.messages=new LinearLayout(host);host.messages.setOrientation(LinearLayout.VERTICAL);host.messages.setPadding(host.dp(4),host.dp(24),host.dp(4),host.dp(16));host.turnError=null;}
    host.scroll.addView(host.messages);active=id;trim();
  }
  void trim(){
    int turns=0,characters=0;long bytes=0;for(Entry e:cached.values()){turns+=e.turns.size();characters+=e.characters();bytes+=e.bytes();}
    Iterator<Entry> it=cached.values().iterator();
    while(it.hasNext()&&(cached.size()>2||turns>100||characters>500000||bytes>24*1024*1024L)){Entry e=it.next();turns-=e.turns.size();characters-=e.characters();bytes-=e.bytes();it.remove();}
  }
  void forget(String id){cached.remove(id);if(active.equals(id))active="";}
  void clear(){cached.clear();}
}
