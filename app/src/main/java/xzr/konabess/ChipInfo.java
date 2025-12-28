package xzr.konabess;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Stores the detected Exynos model and maps model identifiers to localized labels.
 */
public class ChipInfo {
    /** Chip model selected for the current editing session. */
    public static type which;

    /**
     * Resolves a chip model to the label shown in the user interface.
     *
     * @param t chip model to describe
     * @param activity activity used to resolve string resources
     * @return localized chip label, or the unknown label for an unsupported value
     */
    public static String name2ChipDesc(type t, AppCompatActivity activity) {
        return switch (t) {
            case exynos9820 -> activity.getResources().getString(R.string.e9820);

            case exynos9825 -> activity.getResources().getString(R.string.e9825);

            case exynos990 -> activity.getResources().getString(R.string.e990);

            case exynos9810 -> activity.getResources().getString(R.string.e9810);

            default -> activity.getResources().getString(R.string.unknown);
        };
    }

    /** Chip identifiers recognized by device detection and the GPU-table parser. */
    public enum type {
        exynos9820,

        exynos9825,

        exynos990,

        exynos9810,

        unknown
    }
}
