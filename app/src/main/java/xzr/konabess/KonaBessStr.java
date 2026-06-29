package xzr.konabess;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Map;

/**
 * Converts GPU-table identifiers into text suitable for the editor interface.
 */
public class KonaBessStr {
    /**
     * Returns the display label for a parsed GPU-table bin.
     *
     * <p>Bin zero uses the detected chip label. Additional bins use the generic table label followed
     * by the bin index.
     *
     * @param which zero-based bin index
     * @param activity activity used to resolve string resources
     * @return localized bin label
     * @throws Exception if no supported chip has been selected
     */
    public static String convertBins(int which, AppCompatActivity activity) throws Exception {
        ChipInfo.type chipType = ChipInfo.which;

        Map<ChipInfo.type, Integer> chipResourceMap = Map.of(
                ChipInfo.type.exynos9820, R.string.e9820,
                ChipInfo.type.exynos9825, R.string.e9825,
                ChipInfo.type.exynos9810, R.string.e9810,
                ChipInfo.type.exynos990, R.string.e990
        );

        if (chipResourceMap.containsKey(chipType)) {
            if (which == 0) {
                Integer resId = chipResourceMap.get(chipType);
                if (resId != null) {
                    return activity.getResources().getString(resId);
                } else {
                    throw new Exception("Unsupported or null chip type: " + chipType);
                }
            }

            return activity.getResources().getString(R.string.unknown_table) + which;
        }

        throw new Exception("Unsupported chip type: " + chipType);
    }

    /**
     * Converts a raw level-parameter name to its localized editor label.
     *
     * @param input parameter name read from the table
     * @param activity activity used to resolve string resources
     * @return localized frequency label for {@code gpu-freq}; otherwise the normalized input
     */
    public static String convert_level_params(String input, AppCompatActivity activity) {
        input = input.replace("gpu_dvfs_table,", "");

        if (input.equals("gpu-freq"))
            return activity.getResources().getString(R.string.freq);

        return input;
    }
}
