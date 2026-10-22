package android.os;
import java.io.File;import java.io.IOException;import java.util.HashMap;import java.util.Map;
public final class Environment {
 public static final String MEDIA_MOUNTED="mounted",MEDIA_MOUNTED_READ_ONLY="mounted_ro",MEDIA_UNMOUNTED="unmounted",MEDIA_UNKNOWN="unknown";
 private static File external=new File(System.getProperty("java.io.tmpdir"),"srceng-primary");private static final Map<String,String> STATES=new HashMap<String,String>();private Environment(){}
 public static File getExternalStorageDirectory(){return external;}public static void setExternalStorageDirectory(File f){external=f;}public static void setExternalStorageState(File f,String s){STATES.put(normalize(f),s);}
 public static String getExternalStorageState(File f){String p=normalize(f),state=null;int longest=-1;for(Map.Entry<String,String> e:STATES.entrySet()){String r=e.getKey();if((p.equals(r)||p.startsWith(r+File.separator))&&r.length()>longest){longest=r.length();state=e.getValue();}}return state==null?MEDIA_MOUNTED:state;}
 private static String normalize(File f){try{return f.getCanonicalPath();}catch(IOException e){return f.getAbsolutePath();}}
}