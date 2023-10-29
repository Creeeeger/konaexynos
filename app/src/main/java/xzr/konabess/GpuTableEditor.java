package xzr.konabess;

import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
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
public class GpuTableEditor {
    private static int bin_position;
    private static ArrayList<bin> bins;

    private static class bin {
        int id;
        ArrayList<String> header;
        ArrayList<level> levels;
    }

    private static class level {
        ArrayList<String> lines;
    }

    private static ArrayList<String> lines_in_dts;

    public static void init() throws IOException {
        lines_in_dts = new ArrayList<>();
        bins = new ArrayList<>();
        bin_position = -1;
        BufferedReader bufferedReader = new BufferedReader(new FileReader(KonaBessCore.dts_path));
        String s;
        while ((s = bufferedReader.readLine()) != null) {
            lines_in_dts.add(s);
        }
    }

    public static void decode() throws Exception {
        int i = -1;
        String this_line;
        int start = -1;
        int end;
        int bracket = 0;
        while (++i < lines_in_dts.size()) {
            this_line = lines_in_dts.get(i).trim();
            if ((ChipInfo.which == ChipInfo.type.exynos9820 || ChipInfo.which == ChipInfo.type.exynos9825) && this_line.contains("gpu_dvfs_table = ")) {
                start = i;
                if (bin_position < 0)
                    bin_position = i;
                bracket++;
                continue;
            }

            if (bracket == 1 && start >= 0 && (ChipInfo.which == ChipInfo.type.exynos9820 || ChipInfo.which == ChipInfo.type.exynos9825)) {
                end = i;
                if (end >= start) {
                    decode_bin(lines_in_dts.subList(start, end));
                    lines_in_dts.subList(start, end).clear();
                } else {
                    throw new Exception();
                }
                i = start - 1;
                start = -1;
            }
        }
    }

    private static void decode_bin(List<String> lines) throws Exception {
        bin bin = new bin();
        bin.header = new ArrayList<>();
        bin.levels = new ArrayList<>();
        bin.id = bins.size();
        int j = -1;
        String nline = lines.get(0);
        nline = nline.trim().replace("gpu_dvfs_table = <", "").replace(">;", "");
        String[] hexArray = nline.split(" ");
        int groupSize = 8;
        String[][] result = new String[(hexArray.length + groupSize - 1) / groupSize][groupSize];
        for (int i = 0; i < hexArray.length; i++) {
            int row = i / groupSize;
            int col = i % groupSize;
            result[row][col] = hexArray[i];
        }
        while (++j < result.length) {
            bin.levels.add(decode_level(result[j][0]));
        }
        bins.add(bin);
    }

    public static level decode_level(String lines) {
        level level = new level();
        level.lines = new ArrayList<>();
        lines = lines.trim();
        level.lines.add(lines);
        System.out.println(lines);
        return level;
    }

    public static List<String> genTable() {
        ArrayList<String> lines = new ArrayList<>();
        if (ChipInfo.which == ChipInfo.type.exynos9820 || ChipInfo.which == ChipInfo.type.exynos9825) {
            lines.add("gpu_dvfs_table = <");
            lines.addAll(bins.get(0).header);
            for (int pwr_level_id = 0; pwr_level_id < bins.get(0).levels.size(); pwr_level_id++) {
                lines.add("qcom,gpu-pwrlevel@" + pwr_level_id + " {");
                lines.add("reg = <" + pwr_level_id + ">;");
                lines.addAll(bins.get(0).levels.get(pwr_level_id).lines);
                lines.add("};");
            }
            lines.add("};");
        }
        return lines;
    }

    public static List<String> genBack(List<String> table) {
        ArrayList<String> new_dts = new ArrayList<>(lines_in_dts);
        new_dts.addAll(bin_position, table);
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

    private static String generateSubtitle(String line) throws Exception {
        return DtsHelper.shouldUseHex(line) ? DtsHelper.decode_hex_line(line).value :
                DtsHelper.decode_int_line(line).value + "";
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
                title = KonaBessStr.convert_level_params(DtsHelper.decode_hex_line(line).name,
                        activity);
                subtitle = generateSubtitle(line);
            }});
        }

        listView.setOnItemClickListener((parent, view, position, id) -> {
            try {
                if (position == 0) {
                    generateLevels(activity, last, page);
                    return;
                }
                String raw_name =
                        DtsHelper.decode_hex_line(bins.get(last).levels.get(levelid).lines.get(position - 1)).name;
                String raw_value =
                        DtsHelper.shouldUseHex(bins.get(last).levels.get(levelid).lines.get(position - 1))
                                ?
                                DtsHelper.decode_hex_line(bins.get(last).levels.get(levelid).lines.get(position - 1)).value
                                :
                                DtsHelper.decode_int_line(bins.get(last).levels.get(levelid).lines.get(position - 1)).value + "";

                if (raw_name.equals("qcom,level") || raw_name.equals("qcom,cx-level")) {
                    try {
                        Spinner spinner = new Spinner(activity);
                        spinner.setAdapter(new ArrayAdapter(activity,
                                android.R.layout.simple_dropdown_item_1line,
                                ChipInfo.rpmh_levels.level_str()));
                        new AlertDialog.Builder(activity)
                                .setTitle(R.string.edit)
                                .setView(spinner)
                                .setMessage(R.string.editvolt_msg)
                                .setPositiveButton(R.string.save, (dialog, which) -> {
                                    try {
                                        bins.get(last).levels.get(levelid).lines.set(
                                                position - 1,
                                                DtsHelper.encodeIntOrHexLine(raw_name,
                                                        ChipInfo.rpmh_levels.levels()[spinner.getSelectedItemPosition()] + ""));
                                        generateALevel(activity, last, levelid, page);
                                        Toast.makeText(activity, R.string.save_success,
                                                Toast.LENGTH_SHORT).show();
                                    } catch (Exception exception) {
                                        DialogUtil.showError(activity, R.string.save_failed);
                                        exception.printStackTrace();
                                    }
                                })
                                .setNegativeButton(R.string.cancel, null)
                                .create().show();

                    } catch (Exception e) {
                        DialogUtil.showError(activity, R.string.error_occur);
                    }
                } else {
                    EditText editText = new EditText(activity);
                    editText.setInputType(DtsHelper.shouldUseHex(raw_name) ?
                            InputType.TYPE_CLASS_TEXT : InputType.TYPE_CLASS_NUMBER);
                    editText.setText(raw_value);
                    new AlertDialog.Builder(activity)
                            .setTitle(activity.getResources().getString(R.string.edit) + " \"" + items.get(position).title + "\"")
                            .setView(editText)
                            .setMessage(KonaBessStr.help(raw_name, activity))
                            .setPositiveButton(R.string.save, (dialog, which) -> {
                                try {
                                    bins.get(last).levels.get(levelid).lines.set(
                                            position - 1,
                                            DtsHelper.encodeIntOrHexLine(raw_name,
                                                    editText.getText().toString()));
                                    generateALevel(activity, last, levelid, page);
                                    Toast.makeText(activity, R.string.save_success,
                                            Toast.LENGTH_SHORT).show();
                                } catch (Exception e) {
                                    DialogUtil.showError(activity, R.string.save_failed);
                                }
                            })
                            .setNegativeButton(R.string.cancel, null)
                            .create().show();
                }
            } catch (Exception e) {
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

    private static void offset_initial_level_old(int offset) throws Exception {
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

    private static void offset_initial_level(int bin_id, int offset) throws Exception {
        if (ChipInfo.which == ChipInfo.type.exynos9820 || ChipInfo.which == ChipInfo.type.exynos9825) {
            offset_initial_level_old(offset);
            return;
        }
        for (int i = 0; i < bins.get(bin_id).header.size(); i++) {
            String line = bins.get(bin_id).header.get(i);
            if (line.contains("qcom,initial-pwrlevel")) {
                bins.get(bin_id).header.set(i,
                        DtsHelper.encodeIntOrHexLine(
                                DtsHelper.decode_int_line(line).name,
                                DtsHelper.decode_int_line(line).value + offset + ""));
                break;
            }
        }
    }

    private static void offset_ca_target_level(int bin_id, int offset) throws Exception {
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
        int max_levels = ChipInfo.getMaxTableLevels() - min_level_chip_offset();
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

    private static void generateLevels(AppCompatActivity activity, int id, LinearLayout page) throws Exception {
        System.out.println("level activity");
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
            System.out.println("freq from ll");
            long freq = getFrequencyFromLevel(level);
            if (freq == 0)
                continue;

            ParamAdapter.item item = new ParamAdapter.item();
            item.title = freq / 1000000 + "MHz";
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
                    bins.get(id).levels.add(bins.get(id).levels.size() - min_level_chip_offset(),
                            level_clone(bins.get(id).levels.get(bins.get(id).levels.size() - min_level_chip_offset())));
                    generateLevels(activity, id, page);
                    offset_initial_level(id, 1);
                    offset_ca_target_level(id, 1);
                } catch (Exception e) {
                    DialogUtil.showError(activity, R.string.error_occur);
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
                    generateLevels(activity, id, page);
                    offset_initial_level(id, 1);
                    offset_ca_target_level(id, 1);
                } catch (Exception e) {
                    DialogUtil.showError(activity, R.string.error_occur);
                }
                return;
            }
            position -= 2;
            try {
                generateALevel(activity, id, position, page);
            } catch (Exception e) {
                DialogUtil.showError(activity, R.string.error_occur);
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
                        .setMessage(String.format(activity.getResources().getString(R.string.remove_msg),
                                getFrequencyFromLevel(bins.get(id).levels.get(position - 2)) / 1000000))
                        .setPositiveButton(R.string.yes, (dialog, which) -> {
                            bins.get(id).levels.remove(position - 2);
                            try {
                                generateLevels(activity, id, page);
                                offset_initial_level(id, -1);
                                offset_ca_target_level(id, -1);
                            } catch (Exception e) {
                                DialogUtil.showError(activity, R.string.error_occur);
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
            if (line.contains("gpu_dvfs_table")) {
                return DtsHelper.decode_int_line(line).value;
            }
        }
        throw new Exception();
    }

    private static void generateBins(AppCompatActivity activity, LinearLayout page) throws Exception {
        System.out.println("bin generated");
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
            System.out.println("bin added "+ item.title);
        }

        listView.setAdapter(new ParamAdapter(items, activity));
        listView.setOnItemClickListener((parent, view, position, id) -> {
            try {
                System.out.println("levels genreieren ");
                generateLevels(activity, position, page);
            } catch (Exception e) {
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
                    writeOut(genBack(genTable()));
                    Toast.makeText(activity, R.string.save_success, Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    DialogUtil.showError(activity, R.string.save_failed);
                }
            });
        }
        return horizontalScrollView;
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
                    DialogUtil.showError(activity, R.string.app_name);
                }
                showedView.addView(page);
            });
        }
    }
}