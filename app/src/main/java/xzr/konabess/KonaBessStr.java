package xzr.konabess;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Map;

/**
 * Utility class for converting various KonaBess data into localized strings
 * based on the device's chip type and input parameters.
 * <p>
 * Provides methods to map binary tables and level parameters
 * to human-readable descriptions.
 */
public class KonaBessStr {

    /**
     * Convert a numeric bin index to its corresponding description string.
     * <p>
     * Uses the static ChipInfo.which to determine the chipset,
     * then maps that type to resource IDs and fetches the appropriate string.
     * <p>
     * Throws an Exception if the chip type is unsupported or if the resource is null.
     *
     * @param which    Bin index (0 for base table, others appended to unknown_table)
     * @param activity Context used to access Android resources
     * @return Localized description for the given bin
     * @throws Exception for unsupported chip types or null mappings
     */
    public static String convertBins(int which, AppCompatActivity activity) throws Exception {
        // Determine current chip type from global setting
        ChipInfo.type chipType = ChipInfo.which;

        // Map supported chip types to their string resource IDs
        Map<ChipInfo.type, Integer> chipResourceMap = Map.of(
                ChipInfo.type.exynos9820, R.string.e9820,
                ChipInfo.type.exynos9825, R.string.e9825
        );

        // Verify that the current chip type is supported
        if (chipResourceMap.containsKey(chipType)) {
            // Only index 0 returns the base table description
            if (which == 0) {
                // Use wrapper Integer to avoid auto-unboxing null
                Integer resId = chipResourceMap.get(chipType);
                if (resId != null) {
                    return activity.getResources().getString(resId);
                } else {
                    // Defensive: should not happen if map is correct
                    throw new Exception("Unsupported or null chip type: " + chipType);
                }
            }
            // Non-zero bins: return default unknown table description plus index
            return activity.getResources().getString(R.string.unknown_table) + which;
        }

        // Chip type not found in map: error out
        throw new Exception("Unsupported chip type: " + chipType);
    }

    /**
     * Convert a level parameters string to a localized description.
     * <p>
     * Strips known prefixes and maps specific keywords to resources.
     * Unrecognized inputs are returned unchanged.
     *
     * @param input    Raw parameter string (e.g., "gpu_dvfs_table,")
     * @param activity Context used to access Android resources
     * @return Localized description or original input if no match
     */
    public static String convert_level_params(String input, AppCompatActivity activity) {
        // Remove CSV prefix for GPU DVFS tables
        input = input.replace("gpu_dvfs_table,", "");
        // Map the literal keyword "gpu-freq" to a localized string
        if (input.equals("gpu-freq"))
            return activity.getResources().getString(R.string.freq);
        // Fallback: return the raw input string
        return input;
    }
}
