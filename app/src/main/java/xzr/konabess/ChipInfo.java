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
/*
&mali {
        interactive_info = <260000 94 0>;
        gpu_dvfs_table_size = <9 8>; <row col>
        /*  8 columns      freq  down   up  stay  mif    little  middle   big
        gpu_dvfs_table = <  702000    78  100   9  2093000 1456000       0 1820000
        650000    78   98   5  2093000 1456000       0 2080000
        572000    78   98   5  1794000       0       0       0
        433000    78   95   1  1352000       0       0       0
        377000    78   90   1  1352000       0       0       0
        325000    78   85   1  1014000       0       0       0
        260000    78   85   1   676000       0       0       0
        200000    78   85   1   676000       0       0       0
        156000     0   85   1   676000       0       0       0 >;
        gpu_max_clock = <702000>;
        gpu_max_clock_limit = <702000>;
        gpu_min_clock = <156000>;
        gpu_dvfs_start_clock = <260000>;
        gpu_dvfs_bl_config_clock = <156000>;
        };*/