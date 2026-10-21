package android.os;

public final class Build {
    public static String CPU_ABI = "armeabi-v7a";
    public static String[] SUPPORTED_ABIS = new String[] { "armeabi-v7a" };

    public static final class VERSION {
        public static int SDK_INT = 30;
        private VERSION() { }
    }

    private Build() { }
}
