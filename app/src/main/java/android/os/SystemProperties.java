package android.os;

// Suppresses warnings for unused code and redundant suppression.
// Often used in stub or template classes.
@SuppressWarnings({"unused", "RedundantSuppression"})
public class SystemProperties {

    /**
     * Gets the system property for the given key.
     *
     * @param key Property name.
     * @param def Default value if the property is not set.
     * @return Value of the property, or def if not found.
     * <p>
     * Note: In this stub, the method always throws RuntimeException.
     * In real Android, this fetches system properties.
     */
    public static String get(String key, String def) {
        throw new RuntimeException("Stub!"); // Method not implemented (stub for compilation)
    }
}