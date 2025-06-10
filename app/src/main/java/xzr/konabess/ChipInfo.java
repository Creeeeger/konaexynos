package xzr.konabess;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Utility class for mapping a ChipInfo.type to its user-facing description string.
 * <p>
 * This class holds a static reference to the currently selected `type` and
 * provides a method to resolve resource-based descriptions for each chip.
 */
public class ChipInfo {

    /**
     * Currently selected chip type. Can be set by client code to control which
     * description will be returned by name2ChipDesc().
     */
    public static type which;

    /**
     * Returns the human-readable description for the given chip type.
     * <p>
     * Uses Android resources to fetch localized strings:
     * - R.string.e9820 for exynos9820
     * - R.string.e9825 for exynos9825
     * - R.string.unknown for all other values
     *
     * @param t        The chip type to describe
     * @param activity Context used to access resources
     * @return Localized description string for the chip
     */
    public static String name2ChipDesc(type t, AppCompatActivity activity) {
        return switch (t) {
            // Exynos 9820 (used in Galaxy S10/S10+) description
            case exynos9820 -> activity.getResources().getString(R.string.e9820);
            // Exynos 9825 (used in Galaxy Note10/S10 5G) description
            case exynos9825 -> activity.getResources().getString(R.string.e9825);
            // Fallback for unsupported or unknown types
            default -> activity.getResources().getString(R.string.unknown);
        };
    }

    /**
     * Enumeration of supported chip types.
     * <p>
     * - exynos9820: Samsung Exynos 9820 chipset
     * - exynos9825: Samsung Exynos 9825 chipset
     * - unknown:   Placeholder for unrecognized chips
     */
    public enum type {
        /**
         * Samsung Exynos 9820 processor
         */
        exynos9820,
        /**
         * Samsung Exynos 9825 processor
         */
        exynos9825,
        /**
         * Unrecognized or unspecified processor
         */
        unknown
    }
}