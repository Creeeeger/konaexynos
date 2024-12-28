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

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import xzr.konabess.adapters.ParamAdapter;
import xzr.konabess.utils.DialogUtil;
import xzr.konabess.utils.DtsHelper;

public class GpuTableEditor {
    private static int binPosition;
    private static int bin_positiondv;
    private static int binPositionMax;
    private static int binPositionMaxLimit;
    private static int binPositionMin;
    private static List<bin> bins = new ArrayList<>();
    private static List<String> linesInDtsCode = new ArrayList<>();

    public static void init() throws IOException {
        // Initialize collections and variables
        binPosition = bin_positiondv = binPositionMax = binPositionMaxLimit = binPositionMin = -1;
        bins.clear();
        linesInDtsCode.clear();

        // Read all lines from the file in one go
        linesInDtsCode = Files.readAllLines(Paths.get(KonaBessCore.dts_path));
    }

    public static void decode() {
        int start, end;

        for (int i = 0; i < linesInDtsCode.size(); i++) {
            String currentLine = linesInDtsCode.get(i).trim();

            if (isExynos()) {
                if (currentLine.contains("gpu_dvfs_table_size = <")) {
                    if (bin_positiondv < 0) bin_positiondv = i; // Set position only if not set
                    decodeTableSize(Collections.singletonList(linesInDtsCode.remove(i))); // Process and remove line
                    i--; // Adjust index due to removal
                    continue;
                }

                if (currentLine.contains("gpu_dvfs_table = ")) {
                    start = end = i;
                    if (binPosition < 0) binPosition = i;
                    decode_bin(linesInDtsCode.subList(start, end + 1));
                    linesInDtsCode.subList(start, end + 1).clear();
                    i = start - 1; // Adjust index
                    continue;
                }

                if (currentLine.contains("gpu_max_clock = <")) {
                    start = end = i;
                    if (binPositionMax < 0) binPositionMax = i;
                    decodeTableMax(linesInDtsCode.subList(start, end + 1));
                    linesInDtsCode.subList(start, end + 1).clear();
                    i = start - 1;
                    continue;
                }

                if (currentLine.contains("gpu_max_clock_limit = <")) {
                    start = end = i;
                    if (binPositionMaxLimit < 0) binPositionMaxLimit = i;
                    decodeTableMaxLimit(linesInDtsCode.subList(start, end + 1));
                    linesInDtsCode.subList(start, end + 1).clear();
                    i = start - 1;
                    continue;
                }

                if (currentLine.contains("gpu_min_clock = <")) {
                    start = end = i;
                    if (binPositionMin < 0) binPositionMin = i;
                    decodeTableMin(linesInDtsCode.subList(start, end + 1));
                    linesInDtsCode.subList(start, end + 1).clear();
                    i = start - 1;
                }
            }
        }

        mergeBins();
    }

    private static boolean isExynos() {
        return ChipInfo.which == ChipInfo.type.exynos9820 || ChipInfo.which == ChipInfo.type.exynos9825;
    }

    public static void mergeBins() {

        try {
            bins.get(1).dvfsSize.add(bins.get(0).dvfsSize.get(0));
            bins.get(1).max.add(bins.get(2).max.get(0));
            bins.get(1).maxLimit.add(bins.get(3).maxLimit.get(0));
            bins.get(1).min.add(bins.get(4).min.get(0));
        } catch (Exception e) {
            System.out.println("error merge");
        }

        // Remove bins 0, 2, 3 and 4
        for (int i = 4; i >= 0; i--) {
            if (i != 1) bins.remove(i);
        }
    }

    public static void decodeTableSize(List<String> lines) {
        bin bin = new bin();
        bin.dvfsSize = new ArrayList<>();
        bin.dvfsSize.add(decodeTableFrequency(lines.get(0).trim().replace("gpu_dvfs_table_size = <", "").replace(">;", "")));
        bins.add(bin);
    }

    public static void decodeTableMax(List<String> lines) {
        bin bin = new bin();
        bin.max = new ArrayList<>();
        bin.max.add(decodeTableFrequency(lines.get(0).trim().replace("gpu_max_clock = <", "").replace(">;", "")));
        bins.add(bin);
    }

    public static void decodeTableMaxLimit(List<String> lines) {
        bin bin = new bin();
        bin.maxLimit = new ArrayList<>();
        bin.maxLimit.add(decodeTableFrequency(lines.get(0).trim().replace("gpu_max_clock_limit = <", "").replace(">;", "")));
        bins.add(bin);
    }

    public static void decodeTableMin(List<String> lines) {
        bin bin = new bin();
        bin.min = new ArrayList<>();
        bin.min.add(decodeTableFrequency(lines.get(0).trim().replace("gpu_min_clock = <", "").replace(">;", "")));
        bins.add(bin);
    }

    public static void decode_bin(List<String> lines) {
        // Initialize bin object with default values
        bin bin = new bin();
        bin.header = new ArrayList<>();
        bin.levels = new ArrayList<>();
        bin.meta = new ArrayList<>();
        bin.max = new ArrayList<>();
        bin.min = new ArrayList<>();
        bin.maxLimit = new ArrayList<>();
        bin.dvfsSize = new ArrayList<>();
        bin.id = 0;

        // Parse input string and split into groups
        String[] hexArray = lines.get(0)
                .trim()
                .replace("gpu_dvfs_table = <", "")
                .replace(">;", "")
                .split(" ");

        List<List<String>> result = new ArrayList<>();
        for (int i = 0; i < hexArray.length; i += 8) {
            result.add(Arrays.asList(Arrays.copyOfRange(hexArray, i, Math.min(i + 8, hexArray.length))));
        }

        // Process levels
        for (List<String> group : result) {
            bin.levels.add(decodeTableFrequency(group.get(0)));
        }

        // Process meta
        List<String> meta = new ArrayList<>();
        for (List<String> group : result) {
            meta.add(String.join(" ", group.subList(1, group.size())));
        }

        for (String m : meta) {
            bin.meta.add(decodeTableFrequency(m));
        }

        // Add bin to bins
        bins.add(bin);
    }

    private static level decodeTableFrequency(String lines) {
        level level = new level();
        level.lines = new ArrayList<>();
        level.lines.add(lines.trim());
        return level;
    }

    public static List<String> genTable(int type) {
        ArrayList<String> lines = new ArrayList<>();

        if (isExynos()) {
            if (type == 0) {
                lines.add("gpu_dvfs_table_size = <");
                lines.addAll(bins.get(0).dvfsSize.get(0).lines);
                lines.add(">;");
            }

            if (type == 1) {
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

            if (type == 2) {
                lines.add("gpu_max_clock = <");
                lines.addAll(bins.get(0).max.get(0).lines);
                lines.add(">;");
            }

            if (type == 3) {
                lines.add("gpu_max_clock_limit = <");
                lines.addAll(bins.get(0).maxLimit.get(0).lines);
                lines.add(">;");
            }

            if (type == 4) {
                lines.add("gpu_min_clock = <");
                lines.addAll(bins.get(0).min.get(0).lines);
                lines.add(">;");
            }
        }

        return List.of(String.join("", lines));
    }

    public static List<String> genBack(List<String> table) {
        ArrayList<String> new_dts = new ArrayList<>(linesInDtsCode);
        new_dts.addAll(binPosition, genTable(1));
        new_dts.addAll(bin_positiondv, genTable(0));
        new_dts.addAll(binPositionMin, genTable(4));
        new_dts.addAll(binPositionMaxLimit, genTable(3));
        new_dts.addAll(binPositionMax, genTable(2));
        return new_dts;
    }

    public static void writeOut(List<String> newDts) throws IOException {
        Path filePath = Paths.get(KonaBessCore.dts_path);

        // Use try-with-resources for automatic resource management
        try (BufferedWriter writer = Files.newBufferedWriter(filePath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            for (String line : newDts) {
                writer.write(line);
                writer.newLine();
            }
        }
    }

    private static void generateALevel(AppCompatActivity activity, int last, int levelID, LinearLayout page) throws Exception {
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

        for (String line : bins.get(last).levels.get(levelID).lines) {
            items.add(new ParamAdapter.item() {{
                title = KonaBessStr.convert_level_params(DtsHelper.decode_hex_line(line).name, activity);
                subtitle = String.valueOf(DtsHelper.decode_int_line(line).value);
            }});
        }

        listView.setOnItemClickListener((parent, view, position, id) -> {
            try {
                if (position == 0) {
                    generateLevels(activity, last, page);
                    return;
                }
                String raw_value = DtsHelper.decode_int_line(bins.get(last).levels.get(levelID).lines.get(position - 1)).value + "";
                EditText editText = new EditText(activity);
                editText.setInputType(InputType.TYPE_CLASS_NUMBER);
                editText.setText(raw_value);
                new AlertDialog.Builder(activity)
                        .setTitle(activity.getResources().getString(R.string.edit) + " \"" + items.get(position).title + "\"")
                        .setView(editText)
                        .setPositiveButton(R.string.save, (dialog, which) -> {
                            try {
                                bins.get(last).levels.get(levelID).lines.set(position - 1, DtsHelper.inputToHex(editText.getText().toString()));
                                generateALevel(activity, last, levelID, page);
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

    public static boolean canAddNewLevel(int binID, Context context) {
        if (bins.get(binID).levels.size() <= 10) {
            return true;
        } else {
            Toast.makeText(context, R.string.unable_add_more, Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    private static level inputToHex(int input) {
        level level = new level();
        level.lines = List.of("0x" + Integer.toHexString(input) + " 0x8");
        return level;
    }

    private static void generateLevels(AppCompatActivity activity, int id, LinearLayout page) throws Exception {
        bins.get(0).min.set(0, bins.get(0).levels.get(bins.get(0).levels.size() - 1));
        bins.get(0).max.set(0, bins.get(0).levels.get(0));
        bins.get(0).maxLimit.set(0, bins.get(0).levels.get(0));
        bins.get(0).dvfsSize.set(0, inputToHex(bins.get(0).levels.size()));

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
                    bins.get(id).levels.add(bins.get(id).levels.size() - 1, level_clone(bins.get(id).levels.get(bins.get(id).levels.size() - 1)));
                    bins.get(0).meta.add(bins.get(0).meta.get(bins.get(0).meta.size() - 1));
                    generateLevels(activity, id, page);
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
                            } catch (Exception e) {
                                System.out.println(e.getMessage() + e.getCause());
                                DialogUtil.showError(activity, "Remove a frequency error");
                            }
                        })
                        .setNegativeButton(R.string.no, null)
                        .create().show();
            } catch (Exception e) {
                System.out.println(e.getMessage());
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
        List<String> header;
        List<level> levels;
        List<level> meta;
        List<level> dvfsSize;
        List<level> max;
        List<level> maxLimit;
        List<level> min;
    }

    private static class level {
        List<String> lines;
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
                activity.runOnUiThread(() -> DialogUtil.showError(activity, R.string.getting_freq_table_failed + " " + e));
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