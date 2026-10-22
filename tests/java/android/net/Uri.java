package android.net;
import java.net.URI;import java.net.URLDecoder;import java.nio.charset.StandardCharsets;
public final class Uri {
 private final String value;private Uri(String v){value=v;}
 public static Uri parse(String v){if(v==null)throw new IllegalArgumentException("null URI");URI u=URI.create(v);if(u.getScheme()==null)throw new IllegalArgumentException("missing scheme");return new Uri(v);}
 public String getScheme(){int i=value.indexOf(':');return i<0?null:value.substring(0,i);}
 public String getAuthority(){try{return URI.create(value).getRawAuthority();}catch(RuntimeException e){return null;}}
 public String getPath(){try{String p=URI.create(value).getRawPath();return p==null?null:URLDecoder.decode(p.replace("+","%2B"),StandardCharsets.UTF_8.name());}catch(Exception e){throw new IllegalArgumentException("invalid path",e);}}
 public String toString(){return value;}public boolean equals(Object o){return o instanceof Uri&&value.equals(((Uri)o).value);}public int hashCode(){return value.hashCode();}
}