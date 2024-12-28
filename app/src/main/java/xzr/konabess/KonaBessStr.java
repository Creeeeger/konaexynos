package xzr.konabess;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Map;

public class KonaBessStr {
    public static String convertBins(int which, AppCompatActivity activity) throws Exception {
        ChipInfo.type chipType = ChipInfo.which;

        // Map chip types to corresponding resource strings
        Map<ChipInfo.type, Integer> chipResourceMap = Map.of(
                ChipInfo.type.exynos9820, R.string.e9820,
                ChipInfo.type.exynos9825, R.string.e9825
        );

        // Check if chip type exists in the map
        if (chipResourceMap.containsKey(chipType)) {
            // Check if 'which' is 0 and fetch the resource safely
            if (which == 0) {
                Integer type = chipResourceMap.get(chipType); // Use Integer wrapper to avoid unboxing null
                if (type != null) {
                    return activity.getResources().getString(type);
                } else {
                    throw new Exception("Unsupported or null chip type: " + chipType);
                }
            }
            return activity.getResources().getString(R.string.unknown_table) + which;
        }

        // Throw exception for unsupported chip types
        throw new Exception("Unsupported chip type: " + chipType);
    }

    public static String convert_level_params(String input, AppCompatActivity activity) {
        input = input.replace("gpu_dvfs_table,", "");
        if (input.equals("gpu-freq"))
            return activity.getResources().getString(R.string.freq);
        return input;
    }
}