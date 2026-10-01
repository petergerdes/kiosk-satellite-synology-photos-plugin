// JVM substitute for the Android-only encoder. Never included in the plugin ZIP.
package android.util;

public final class Base64 {
    public static final int NO_WRAP = 2;
    public static String encodeToString(byte[] bytes, int flags) {
        if (flags != NO_WRAP) throw new AssertionError("Unexpected Base64 flags");
        return java.util.Base64.getEncoder().encodeToString(bytes);
    }
}
