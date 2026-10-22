package android.content;
import android.net.Uri;import java.util.ArrayList;import java.util.List;
public class ContentResolver {
 private final List<UriPermission> permissions=new ArrayList<UriPermission>();private boolean failPersist,failQuery;
 public void takePersistableUriPermission(Uri u,int f){if(failPersist)throw new SecurityException("grant denied");revoke(u);permissions.add(new UriPermission(u,(f&Intent.FLAG_GRANT_READ_URI_PERMISSION)!=0,(f&Intent.FLAG_GRANT_WRITE_URI_PERMISSION)!=0));}
 public List<UriPermission> getPersistedUriPermissions(){if(failQuery)throw new SecurityException("query denied");return new ArrayList<UriPermission>(permissions);}
 public void releasePersistableUriPermission(Uri u,int f){revoke(u);}public void setFailPersist(boolean v){failPersist=v;}public void setFailQuery(boolean v){failQuery=v;}
 public void revoke(Uri u){for(int i=permissions.size()-1;i>=0;i--)if(u.equals(permissions.get(i).getUri()))permissions.remove(i);}
 public void addPersistedPermission(Uri u,boolean r,boolean w){revoke(u);permissions.add(new UriPermission(u,r,w));}
}