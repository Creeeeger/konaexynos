package xzr.konabess;
import android.app.Activity;

public class ChipInfo {
    public enum type {
        exynos9820,
        exynos9825,
        unknown
    }

    public static int getMaxTableLevels(type type) {
        return 11;
    }

    public static String name2chipdesc(String name, Activity activity) {
        type t = type.valueOf(name);
        return name2chipdesc(t, activity);
    }

    public static String name2chipdesc(type t, Activity activity) {
        switch (t) {
            case exynos9820:
                return activity.getResources().getString(R.string.e9820);
            case exynos9825:
                return activity.getResources().getString(R.string.e9825);
        }
        return activity.getResources().getString(R.string.unknown);
    }

    public static type which;

    public static class rpmh_levels {
        public static int[] levels() {
            if (ChipInfo.which == type.exynos9820)
                return rpmh_levels_exynos9820.levels;
            else if (ChipInfo.which == type.exynos9825)
                return rpmh_levels_exynos9825.levels;
            return new int[]{};
        }

        public static String[] level_str() {
            if (ChipInfo.which == type.exynos9820)
                return rpmh_levels_exynos9820.level_str;
            else if (ChipInfo.which == type.exynos9825)
                return rpmh_levels_exynos9825.level_str;
            return new String[]{};
        }
    }

    private static class rpmh_levels_exynos9820 {
        public static final int[] levels = {16, 48, 56, 64, 80, 96, 128, 144, 192, 224, 256, 320,
                336, 352, 384, 400, 416};
        public static final String[] level_str = {
                "RETENTION",
                "MIN_SVS",
                "TURBO_L1"
        };
    }

    private static class rpmh_levels_exynos9825 {
        public static final int[] levels = {16, 48, 56, 64, 80, 96, 128, 144, 192, 224, 256, 320,
                336, 352, 384, 400, 416};
        public static final String[] level_str = {
                "RETENTION",
                "MIN_SVS",
                "TURBO_L1"
        };
    }
}