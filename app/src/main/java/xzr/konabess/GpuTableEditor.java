package xzr.konabess;

import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import xzr.konabess.adapters.ParamAdapter;
import xzr.konabess.utils.DialogUtil;
import xzr.konabess.utils.DtsHelper;

public class GpuTableEditor {
    private static int bin_position;
    private static int bin_positiondv;
    private static int bin_positionmax;
    private static int bin_positionmaxlim;
    private static int bin_positionmin;
    private static ArrayList<bin> bins;
    private static ArrayList<String> lines_in_dts;

    public static void init() throws IOException {
        lines_in_dts = new ArrayList<>();
        bins = new ArrayList<>();
        bin_position = -1;
        bin_positiondv = -1;
        bin_positionmax = -1;
        bin_positionmaxlim = -1;
        bin_positionmin = -1;
        BufferedReader bufferedReader = new BufferedReader(new FileReader(KonaBessCore.dts_path));
        String s;
        while ((s = bufferedReader.readLine()) != null) {
            lines_in_dts.add(s);
        }
    }

    public static void decode() {
        int i = -1;
        String this_line;
        int start;
        int end;
        bin bin = new bin();
        bin.dvfs_size = new ArrayList<>();
        bin.max = new ArrayList<>();
        bin.min = new ArrayList<>();
        bin.max_limit = new ArrayList<>();
        while (++i < lines_in_dts.size()) {
            this_line = lines_in_dts.get(i).trim();

            if ((ChipInfo.which == ChipInfo.type.exynos9820 || ChipInfo.which == ChipInfo.type.exynos9825) && this_line.contains("gpu_dvfs_table_size = <")) {
                start = end = i;
                if (bin_positiondv < 0)
                    bin_positiondv = i;
                decode_tablesz(lines_in_dts.subList(start, end + 1));
                lines_in_dts.subList(start, end + 1).clear();
                i = start - 1;
                continue;
            }

            if ((ChipInfo.which == ChipInfo.type.exynos9820 || ChipInfo.which == ChipInfo.type.exynos9825) && this_line.contains("gpu_dvfs_table = ")) {
                start = end = i;
                if (bin_position < 0)
                    bin_position = i;
                decode_bin(lines_in_dts.subList(start, end + 1));
                lines_in_dts.subList(start, end + 1).clear();
                i = start - 1;
                continue;
            }

            if ((ChipInfo.which == ChipInfo.type.exynos9820 || ChipInfo.which == ChipInfo.type.exynos9825) && this_line.contains("gpu_max_clock = <")) {
                start = end = i;
                if (bin_positionmax < 0)
                    bin_positionmax = i;
                decode_tablemax(lines_in_dts.subList(start, end + 1));
                lines_in_dts.subList(start, end + 1).clear();
                i = start - 1;
                continue;
            }

            if ((ChipInfo.which == ChipInfo.type.exynos9820 || ChipInfo.which == ChipInfo.type.exynos9825) && this_line.contains("gpu_max_clock_limit = <")) {
                start = end = i;
                if (bin_positionmaxlim < 0)
                    bin_positionmaxlim = i;
                decode_tablemaxl(lines_in_dts.subList(start, end + 1));
                lines_in_dts.subList(start, end + 1).clear();
                i = start - 1;
                continue;
            }

            if ((ChipInfo.which == ChipInfo.type.exynos9820 || ChipInfo.which == ChipInfo.type.exynos9825) && this_line.contains("gpu_min_clock = <")) {
                start = end = i;
                if (bin_positionmin < 0)
                    bin_positionmin = i;
                decode_tablemin(lines_in_dts.subList(start, end + 1));
                lines_in_dts.subList(start, end + 1).clear();
                i = start - 1;
            }
        }
        mergebins();
    }

    public static void mergebins() {
        bins.get(1).dvfs_size.add(bins.get(0).dvfs_size.get(0));
        bins.get(1).max.add(bins.get(2).max.get(0));
        bins.get(1).max_limit.add(bins.get(3).max_limit.get(0));
        bins.get(1).min.add(bins.get(4).min.get(0));

        // Remove bins 0, 2, 3, and 4
        bins.remove(4); // Remove bin 4 first to avoid index issues
        bins.remove(3);
        bins.remove(2);
        bins.remove(0);
    }

    public static void decode_tablesz(List<String> lines) {
        bin bin = new bin();
        bin.dvfs_size = new ArrayList<>();
        String nline = lines.get(0);
        nline = nline.trim().replace("gpu_dvfs_table_size = <", "").replace(">;", "");
        bin.dvfs_size.add(decode_tableszf(nline));
        bins.add(bin);
    }

    public static void decode_tablemax(List<String> lines) {
        bin bin = new bin();
        bin.max = new ArrayList<>();
        String nline = lines.get(0);
        nline = nline.trim().replace("gpu_max_clock = <", "").replace(">;", "");
        bin.max.add(decode_tablemaxf(nline));
        bins.add(bin);
    }

    public static void decode_tablemaxl(List<String> lines) {
        bin bin = new bin();
        bin.max_limit = new ArrayList<>();
        String nline = lines.get(0);
        nline = nline.trim().replace("gpu_max_clock_limit = <", "").replace(">;", "");
        bin.max_limit.add(decode_table_max_lm(nline));
        bins.add(bin);
    }

    public static void decode_tablemin(List<String> lines) {
        bin bin = new bin();
        bin.min = new ArrayList<>();
        String nline = lines.get(0);
        nline = nline.trim().replace("gpu_min_clock = <", "").replace(">;", "");
        bin.min.add(decode_tableminf(nline));
        bins.add(bin);
    }

    public static void decode_bin(List<String> lines) {
        bin bin = new bin();
        bin.header = new ArrayList<>();
        bin.levels = new ArrayList<>();
        bin.meta = new ArrayList<>();
        bin.max = new ArrayList<>();
        bin.min = new ArrayList<>();
        bin.max_limit = new ArrayList<>();
        bin.dvfs_size = new ArrayList<>();
        bin.id = 0;
        String nline = lines.get(0);
        nline = nline.trim().replace("gpu_dvfs_table = <", "").replace(">;", "");
        String[] hexArray = nline.split(" "); // Split the input string by spaces
        int groupSize = 8;
        int j = -1;
        String[][] result = new String[(hexArray.length + groupSize - 1) / groupSize][groupSize];
        for (int i = 0; i < hexArray.length; i++) {
            int row = i / groupSize;
            int col = i % groupSize;
            result[row][col] = hexArray[i];
        }
        while (++j < result.length) {
            bin.levels.add(decode_level(result[j][0]));
        }
        StringBuilder res = new StringBuilder();
        String[] meta = lines.toArray(new String[result.length]);

        for (int i = 0; i < result.length; i++) {
            for (int k = 0; k < 7; k++) {
                res.append(result[i][k + 1]).append(" ");
            }
            meta[i] = res.toString();
            res = new StringBuilder();
        }
        j = 0; // Reset j to 0
        while (j < meta.length) {
            bin.meta.add(decode_meta(meta[j]));
            j++;
        }
        bins.add(bin);
    }

    public static level decode_tableszf(String lines) {
        level level = new level();
        level.lines = new ArrayList<>();
        level.lines.add(lines);
        return level;
    }

    public static level decode_tablemaxf(String lines) {
        level level = new level();
        level.lines = new ArrayList<>();
        level.lines.add(lines);
        return level;
    }

    public static level decode_table_max_lm(String lines) {
        level level = new level();
        level.lines = new ArrayList<>();
        level.lines.add(lines);
        return level;
    }

    public static level decode_tableminf(String lines) {
        level level = new level();
        level.lines = new ArrayList<>();
        level.lines.add(lines);
        return level;
    }

    public static level decode_level(String lines) {
        level level = new level();
        level.lines = new ArrayList<>();
        lines = lines.trim();
        level.lines.add(lines);
        return level;
    }

    public static level decode_meta(String mline) {
        level level = new level();
        level.lines = new ArrayList<>();
        level.lines.add(mline);
        return level;
    }

    public static List<String> genTable(int type) {
        ArrayList<String> lines = new ArrayList<>();

        if ((ChipInfo.which == ChipInfo.type.exynos9820 || ChipInfo.which == ChipInfo.type.exynos9825) && type == 0) {
            lines.add("gpu_dvfs_table_size = <");
            lines.addAll(bins.get(0).dvfs_size.get(0).lines);
            lines.add(">;");
        }
        if ((ChipInfo.which == ChipInfo.type.exynos9820 || ChipInfo.which == ChipInfo.type.exynos9825) && type == 1) {
            lines.add("gpu_dvfs_table = <");
            int l = 0;
            for (int i = 0; i < bins.get(0).levels.size(); i++) {
                lines.addAll(bins.get(0).levels.get(i).lines);
                lines.add(" ");
                List<String> metaLines = bins.get(0).meta.get(l).lines;
                for (String metaLine : metaLines) {
                    lines.add(metaLine.trim());
                    lines.add(" ");
                }
                l++;
            }
            lines.remove(lines.size() - 1);
            lines.add(">;");
        }
        if ((ChipInfo.which == ChipInfo.type.exynos9820 || ChipInfo.which == ChipInfo.type.exynos9825) && type == 2) {
            lines.add("gpu_max_clock = <");
            lines.addAll(bins.get(0).max.get(0).lines);
            lines.add(">;");
        }
        if ((ChipInfo.which == ChipInfo.type.exynos9820 || ChipInfo.which == ChipInfo.type.exynos9825) && type == 3) {
            lines.add("gpu_max_clock_limit = <");
            lines.addAll(bins.get(0).max_limit.get(0).lines);
            lines.add(">;");
        }
        if ((ChipInfo.which == ChipInfo.type.exynos9820 || ChipInfo.which == ChipInfo.type.exynos9825) && type == 4) {
            lines.add("gpu_min_clock = <");
            lines.addAll(bins.get(0).min.get(0).lines);
            lines.add(">;");
        }

        // Concatenate all elements in the lines list into a single line
        String concatenatedLine = String.join("", lines);
        ArrayList<String> result = new ArrayList<>();
        result.add(concatenatedLine);
        return result;
    }

    public static List<String> genBack(List<String> table) {
        ArrayList<String> new_dts = new ArrayList<>(lines_in_dts);
        new_dts.addAll(bin_position, genTable(1));
        new_dts.addAll(bin_positiondv, genTable(0));
        new_dts.addAll(bin_positionmin, genTable(4));
        new_dts.addAll(bin_positionmaxlim, genTable(3));
        new_dts.addAll(bin_positionmax, genTable(2));
        return new_dts;
    }

    public static void writeOut(List<String> new_dts) throws IOException {
        File file = new File(KonaBessCore.dts_path);
        file.createNewFile();
        BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(file));
        for (String s : new_dts) {
            bufferedWriter.write(s);
            bufferedWriter.newLine();
        }
        bufferedWriter.close();
    }

    private static String generateSubtitle(String line) {
        return String.valueOf(DtsHelper.decode_int_line(line).value);
    }

    private static void generateALevel(AppCompatActivity activity, int last, int levelid, LinearLayout page) throws Exception {
        ((MainActivity) activity).onBackPressedListener = new MainActivity.onBackPressedListener() {
            @Override
            public void onBackPressed() {
                try {
                    generateLevels(activity, last, page);
                } catch (Exception ignored) {
                }
            }
        };

        ListView listView = new ListView(activity);
        ArrayList<ParamAdapter.item> items = new ArrayList<>();

        items.add(new ParamAdapter.item() {{
            title = activity.getResources().getString(R.string.back);
            subtitle = "";
        }});

        for (String line : bins.get(last).levels.get(levelid).lines) {
            items.add(new ParamAdapter.item() {{
                title = KonaBessStr.convert_level_params(DtsHelper.decode_hex_line(line).name, activity);
                subtitle = generateSubtitle(line);
            }});
        }

        listView.setOnItemClickListener((parent, view, position, id) -> {
            try {
                if (position == 0) {
                    generateLevels(activity, last, page);
                    return;
                }
                String raw_name = DtsHelper.decode_hex_line(bins.get(last).levels.get(levelid).lines.get(position - 1)).name;
                String raw_value = DtsHelper.decode_int_line(bins.get(last).levels.get(levelid).lines.get(position - 1)).value + "";
                EditText editText = new EditText(activity);
                editText.setInputType(InputType.TYPE_CLASS_NUMBER);
                editText.setText(raw_value);
                new AlertDialog.Builder(activity)
                        .setTitle(activity.getResources().getString(R.string.edit) + " \"" + items.get(position).title + "\"")
                        .setView(editText)
                        .setMessage(KonaBessStr.help(raw_name, activity))
                        .setPositiveButton(R.string.save, (dialog, which) -> {
                            try {
                                bins.get(last).levels.get(levelid).lines.set(position - 1, DtsHelper.inputToHex(editText.getText().toString()));
                                generateALevel(activity, last, levelid, page);
                                Toast.makeText(activity, R.string.save_success, Toast.LENGTH_SHORT).show();
                            } catch (Exception e) {
                                System.out.println(e.getMessage() + e.getCause());
                                DialogUtil.showError(activity, "Save new level failed");
                            }
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .create().show();

            } catch (Exception e) {
                System.out.println(e.getMessage() + e.getCause());
                DialogUtil.showError(activity, R.string.error_occur);
            }
        });
        listView.setAdapter(new ParamAdapter(items, activity));
        page.removeAllViews();
        page.addView(listView);
    }

    private static level level_clone(level from) {
        level next = new level();
        next.lines = new ArrayList<>(from.lines);
        return next;
    }

    private static void offset_initial_level_old(int offset) {
        boolean started = false;
        int bracket = 0;
        for (int i = 0; i < lines_in_dts.size(); i++) {
            String line = lines_in_dts.get(i);

            if (line.contains("qcom,kgsl-3d0") && line.contains("{")) {
                started = true;
                bracket++;
                continue;
            }

            if (line.contains("{")) {
                bracket++;
                continue;
            }

            if (line.contains("}")) {
                bracket--;
                if (bracket == 0)
                    break;
                continue;
            }

            if (!started)
                continue;

            if (line.contains("qcom,initial-pwrlevel")) {
                lines_in_dts.set(i,
                        DtsHelper.encodeIntOrHexLine(DtsHelper.decode_int_line(line).name,
                                DtsHelper.decode_int_line(line).value + offset + ""));
            }

        }
    }

    private static void offset_initial_level(int bin_id, int offset) {
        if (ChipInfo.which == ChipInfo.type.exynos9820 || ChipInfo.which == ChipInfo.type.exynos9825) {
            offset_initial_level_old(offset);
            return;
        }
        for (int i = 0; i < bins.get(bin_id).header.size(); i++) {
            String line = bins.get(bin_id).header.get(i);
            if (line.contains("qcom,initial-pwrlevel")) {
                bins.get(bin_id).header.set(i, DtsHelper.encodeIntOrHexLine(DtsHelper.decode_int_line(line).name, DtsHelper.decode_int_line(line).value + offset + ""));
                break;
            }
        }
    }

    private static void offset_ca_target_level(int bin_id, int offset) {
        for (int i = 0; i < bins.get(bin_id).header.size(); i++) {
            String line = bins.get(bin_id).header.get(i);
            if (line.contains("qcom,ca-target-pwrlevel")) {
                bins.get(bin_id).header.set(i,
                        DtsHelper.encodeIntOrHexLine(
                                DtsHelper.decode_int_line(line).name,
                                DtsHelper.decode_int_line(line).value + offset + ""));
                break;
            }
        }
    }

    public static boolean canAddNewLevel(int binID, Context context) throws Exception {
        int max_levels = 11 - min_level_chip_offset();
        if (bins.get(binID).levels.size() <= max_levels)
            return true;
        Toast.makeText(context, R.string.unable_add_more, Toast.LENGTH_SHORT).show();
        return false;
    }

    public static int min_level_chip_offset() throws Exception {
        if (ChipInfo.which == ChipInfo.type.exynos9820 || ChipInfo.which == ChipInfo.type.exynos9825)
            return 1;
        throw new Exception();
    }

    public static level inputToHex(int input) {
        level level = new level();
        level.lines = new ArrayList<>();
        String input_string = String.valueOf(input);
        int intValue = Integer.parseInt(input_string);
        String hexValue = Integer.toHexString(intValue);
        level.lines.add("0x" + hexValue + " 0x8");
        return level;
    }

    private static void generateLevels(AppCompatActivity activity, int id, LinearLayout page) throws Exception {
        bins.get(0).min.set(0, bins.get(0).levels.get(bins.get(0).levels.size() - 1));
        bins.get(0).max.set(0, bins.get(0).levels.get(0));
        bins.get(0).max_limit.set(0, bins.get(0).levels.get(0));
        bins.get(0).dvfs_size.set(0, inputToHex(bins.get(0).levels.size()));
        ((MainActivity) activity).onBackPressedListener = new MainActivity.onBackPressedListener() {
            @Override
            public void onBackPressed() {
                try {
                    generateBins(activity, page);
                } catch (Exception ignored) {
                }
            }
        };

        ListView listView = new ListView(activity);
        ArrayList<ParamAdapter.item> items = new ArrayList<>();

        items.add(new ParamAdapter.item() {{
            title = activity.getResources().getString(R.string.back);
            subtitle = "";
        }});

        items.add(new ParamAdapter.item() {{
            title = activity.getResources().getString(R.string.new_item);
            subtitle = activity.getResources().getString(R.string.new_desc);
        }});

        for (level level : bins.get(id).levels) {
            long freq = getFrequencyFromLevel(level);
            if (freq == 0)
                continue;

            ParamAdapter.item item = new ParamAdapter.item();
            item.title = freq / 1000 + "MHz";
            item.subtitle = "";
            items.add(item);
        }

        items.add(new ParamAdapter.item() {{
            title = activity.getResources().getString(R.string.new_item);
            subtitle = activity.getResources().getString(R.string.new_desc);
        }});

        listView.setOnItemClickListener((parent, view, position, id1) -> {
            if (position == items.size() - 1) {
                try {
                    if (!canAddNewLevel(id, activity))
                        return;
                    bins.get(id).levels.add(bins.get(id).levels.size() - min_level_chip_offset(), level_clone(bins.get(id).levels.get(bins.get(id).levels.size() - min_level_chip_offset())));
                    bins.get(0).meta.add(bins.get(0).meta.get(bins.get(0).meta.size() - 1));
                    generateLevels(activity, id, page);
                    offset_initial_level(id, 1);
                    offset_ca_target_level(id, 1);
                } catch (Exception e) {
                    System.out.println(e.getMessage() + e.getCause());
                    DialogUtil.showError(activity, "Can't add new level");
                }
                return;
            }
            if (position == 0) {
                try {
                    generateBins(activity, page);
                } catch (Exception ignored) {
                }
                return;
            }
            if (position == 1) {
                try {
                    if (!canAddNewLevel(id, activity))
                        return;
                    bins.get(id).levels.add(0, level_clone(bins.get(id).levels.get(0)));
                    bins.get(0).meta.add(0, bins.get(0).meta.get(0));
                    generateLevels(activity, id, page);
                    offset_initial_level(id, 1);
                    offset_ca_target_level(id, 1);
                } catch (Exception e) {
                    System.out.println(e.getMessage() + e.getCause());
                    DialogUtil.showError(activity, "Clone a level error");
                }
                return;
            }
            position -= 2;
            try {
                generateALevel(activity, id, position, page);
            } catch (Exception e) {
                System.out.println(e.getMessage() + e.getCause());
                DialogUtil.showError(activity, "Add a new level error");
            }
        });

        listView.setOnItemLongClickListener((parent, view, position, idd) -> {
            if (position == items.size() - 1)
                return true;
            if (bins.get(id).levels.size() == 1)
                return true;
            try {
                new AlertDialog.Builder(activity)
                        .setTitle(R.string.remove)
                        .setMessage(String.format(activity.getResources().getString(R.string.remove_msg), getFrequencyFromLevel(bins.get(id).levels.get(position - 2)) / 1000))
                        .setPositiveButton(R.string.yes, (dialog, which) -> {
                            bins.get(id).levels.remove(position - 2);
                            bins.get(id).meta.remove(position - 2);
                            try {
                                generateLevels(activity, id, page);
                                offset_initial_level(id, -1);
                                offset_ca_target_level(id, -1);
                            } catch (Exception e) {
                                System.out.println(e.getMessage() + e.getCause());
                                DialogUtil.showError(activity, "Remove a frequency error");
                            }
                        })
                        .setNegativeButton(R.string.no, null)
                        .create().show();
            } catch (Exception ignored) {
                ignored.printStackTrace();
            }
            return true;
        });

        listView.setAdapter(new ParamAdapter(items, activity));
        page.removeAllViews();
        page.addView(listView);
    }

    private static long getFrequencyFromLevel(level level) throws Exception {
        for (String line : level.lines) {
            if (line.contains("0x")) {
                return DtsHelper.decode_int_line(line).value;
            }
        }
        throw new Exception();
    }

    private static void generateBins(AppCompatActivity activity, LinearLayout page) throws Exception {
        ((MainActivity) activity).onBackPressedListener = new MainActivity.onBackPressedListener() {
            @Override
            public void onBackPressed() {
                ((MainActivity) activity).showMainView();
            }
        };

        ListView listView = new ListView(activity);
        ArrayList<ParamAdapter.item> items = new ArrayList<>();

        for (int i = 0; i < bins.size(); i++) {
            ParamAdapter.item item = new ParamAdapter.item();
            item.title = KonaBessStr.convert_bins(bins.get(i).id, activity);
            item.subtitle = "";
            items.add(item);
        }

        listView.setAdapter(new ParamAdapter(items, activity));
        listView.setOnItemClickListener((parent, view, position, id) -> {
            try {
                generateLevels(activity, position, page);
            } catch (Exception e) {
                System.out.println(e.getMessage() + e.getCause());
                DialogUtil.showError(activity, R.string.error_occur);
            }
        });

        page.removeAllViews();
        page.addView(listView);
    }

    private static View generateToolBar(AppCompatActivity activity) {
        LinearLayout toolbar = new LinearLayout(activity);
        HorizontalScrollView horizontalScrollView = new HorizontalScrollView(activity);
        horizontalScrollView.addView(toolbar);

        {
            Button button = new Button(activity);
            button.setText(R.string.save_freq_table);
            toolbar.addView(button);
            button.setOnClickListener(v -> {
                try {
                    writeOut(genBack(genTable(5)));
                    Toast.makeText(activity, R.string.save_success, Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    System.out.println(e.getMessage() + e.getCause());
                    DialogUtil.showError(activity, R.string.save_failed);
                }
            });
        }
        return horizontalScrollView;
    }

    private static class bin {
        int id;
        ArrayList<String> header;
        ArrayList<level> levels;
        ArrayList<level> meta;
        ArrayList<level> dvfs_size;
        ArrayList<level> max;
        ArrayList<level> max_limit;
        ArrayList<level> min;
    }

    private static class level {
        ArrayList<String> lines;
    }

    static class gpuTableLogic extends Thread {
        AppCompatActivity activity;
        AlertDialog waiting;
        LinearLayout showedView;
        LinearLayout page;

        public gpuTableLogic(AppCompatActivity activity, LinearLayout showedView) {
            this.activity = activity;
            this.showedView = showedView;
        }

        public void run() {
            activity.runOnUiThread(() -> {
                waiting = DialogUtil.getWaitDialog(activity, R.string.getting_freq_table);
                waiting.show();
            });

            try {
                init();
                decode();
            } catch (Exception e) {
                System.out.println(e.getMessage() + e.getCause());
                activity.runOnUiThread(() -> DialogUtil.showError(activity, R.string.getting_freq_table_failed));
            }

            activity.runOnUiThread(() -> {
                waiting.dismiss();
                showedView.removeAllViews();
                showedView.addView(generateToolBar(activity));
                page = new LinearLayout(activity);
                page.setOrientation(LinearLayout.VERTICAL);
                try {
                    generateBins(activity, page);
                } catch (Exception e) {
                    System.out.println(e.getMessage() + e.getCause());
                    DialogUtil.showError(activity, "Failed to generate bins");
                }
                showedView.addView(page);
            });
        }
    }
}