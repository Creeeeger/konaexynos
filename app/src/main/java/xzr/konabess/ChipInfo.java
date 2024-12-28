package xzr.konabess;

import androidx.appcompat.app.AppCompatActivity;

public class ChipInfo {
    public static type which;

    public static String name2ChipDesc(type t, AppCompatActivity activity) {
        switch (t) {
            case exynos9820:
                return activity.getResources().getString(R.string.e9820);
            case exynos9825:
                return activity.getResources().getString(R.string.e9825);
        }
        return activity.getResources().getString(R.string.unknown);
    }

    public enum type {
        exynos9820,
        exynos9825,
        unknown
    }
}