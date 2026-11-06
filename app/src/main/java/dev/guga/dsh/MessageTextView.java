package dev.guga.dsh;

/** Routes touch-activated links through the standard accessibility click path. */
final class MessageTextView extends android.widget.TextView {
  Runnable linkClick;
  MessageTextView(android.content.Context context){super(context);}
  @Override public boolean performClick(){
    Runnable action=linkClick;linkClick=null;
    if(action!=null){sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_CLICKED);action.run();return true;}
    return super.performClick();
  }
}
