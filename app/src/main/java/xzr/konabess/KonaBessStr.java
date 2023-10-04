package xzr.konabess;
import android.app.Activity;

public class KonaBessStr {
    public static String convert_bins(int which, Activity activity) throws Exception {
        if (ChipInfo.which == ChipInfo.type.exynos9820)
            return convert_bins_exynos9820(which, activity);
        else if (ChipInfo.which == ChipInfo.type.exynos9825)
            return convert_bins_exynos9825(which, activity);
        throw new Exception();
    }

    public static String convert_bins_exynos9820(int which, Activity activity) {
        switch (which) {
            case 0:
                return activity.getResources().getString(R.string.e9820);
        }
        return activity.getResources().getString(R.string.unknown_table) + which;
    }

    public static String convert_bins_exynos9825(int which, Activity activity) {
        switch (which) {
            case 0:
                return activity.getResources().getString(R.string.e9825);
        }
        return activity.getResources().getString(R.string.unknown_table) + which;
    }

    public static String convert_level_params(String input, Activity activity) {
        input = input.replace("gpu_dvfs_table,", "");
        if (input.equals("gpu-freq"))
            return activity.getResources().getString(R.string.freq);
        return input;
    }

    public static String help(String what, Activity activity) {
        if (what.equals("qcom,gpu-freq"))
            activity.getResources().getString(R.string.help_gpufreq);
        if (what.contains("bus"))
            return activity.getResources().getString(R.string.help_bus);
        if (what.contains("acd"))
            return activity.getResources().getString(R.string.help_acd);
        return "";
    }

    public static String generic_help(Activity activity) {
        activity.getResources().getString(R.string.help_msg_aio);
        return "";
    }
}