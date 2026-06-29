package android.os;

/**
 * Compile-time declaration of Android's hidden system-property API.
 *
 * <p>The framework implementation is resolved from the device boot class path. The local method
 * body exists only to make the hidden signature available to the compiler.
 */
@SuppressWarnings({"unused", "RedundantSuppression"})
public class SystemProperties {
    /**
     * Returns an Android system property from the framework implementation.
     *
     * @param key property name
     * @param def value returned by Android when the property is unset
     * @return property value, or {@code def} when unset
     * @throws RuntimeException if the compile-time stub is invoked directly
     */
    public static String get(String key, String def) {
        throw new RuntimeException("Stub!");
    }
}
