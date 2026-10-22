package android.content;
public class Intent {
 public static final String ACTION_OPEN_DOCUMENT_TREE="android.intent.action.OPEN_DOCUMENT_TREE";
 public static final int FLAG_GRANT_READ_URI_PERMISSION=1,FLAG_GRANT_WRITE_URI_PERMISSION=2,FLAG_GRANT_PERSISTABLE_URI_PERMISSION=64,FLAG_GRANT_PREFIX_URI_PERMISSION=128;
 private final String action;private int flags;public Intent(String a){action=a;}public Intent(){action=null;}
 public Intent addFlags(int v){flags|=v;return this;}public int getFlags(){return flags;}public String getAction(){return action;}
}