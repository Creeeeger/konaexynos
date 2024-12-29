package xzr.konabess;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textview.MaterialTextView;

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
    private static final List<bin> bins = new ArrayList<>();
    private static int binPosition;
    private static int bin_positiondv;
    private static int binPositionMax;
    private static int binPositionMaxLimit;
    private static int binPositionMin;
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
        // Temporary storage for extracted lines
        List<String> dvLines = new ArrayList<>();
        List<String> binLines = new ArrayList<>();
        List<String> maxLines = new ArrayList<>();
        List<String> maxLimitLines = new ArrayList<>();
        List<String> minLines = new ArrayList<>();

        // Loop through the lines
        for (int i = 0; i < linesInDtsCode.size(); i++) {
            String currentLine = linesInDtsCode.get(i).trim().replace(">;", "");

            if (isExynos()) {
                // 1. gpu_dvfs_table_size
                if (currentLine.contains("gpu_dvfs_table_size = <")) {
                    if (bin_positiondv < 0) bin_positiondv = i; // Set position
                    dvLines.add(linesInDtsCode.remove(i)); // Collect and remove line
                    i--; // Adjust index
                    continue;
                }

                // 2. gpu_dvfs_table
                if (currentLine.contains("gpu_dvfs_table = ")) {
                    if (binPosition < 0) binPosition = i; // Set position
                    binLines.add(linesInDtsCode.remove(i)); // Collect and remove line
                    i--; // Adjust index
                    continue;
                }

                // 3. gpu_max_clock
                if (currentLine.contains("gpu_max_clock = <")) {
                    if (binPositionMax < 0) binPositionMax = i; // Set position
                    maxLines.add(linesInDtsCode.remove(i)); // Collect and remove line
                    i--; // Adjust index
                    continue;
                }

                // 4. gpu_max_clock_limit
                if (currentLine.contains("gpu_max_clock_limit = <")) {
                    if (binPositionMaxLimit < 0) binPositionMaxLimit = i; // Set position
                    maxLimitLines.add(linesInDtsCode.remove(i)); // Collect and remove line
                    i--; // Adjust index
                    continue;
                }

                // 5. gpu_min_clock
                if (currentLine.contains("gpu_min_clock = <")) {
                    if (binPositionMin < 0) binPositionMin = i; // Set position
                    minLines.add(linesInDtsCode.remove(i)); // Collect and remove line
                    i--; // Adjust index
                }
            }
        }

        // Decode collected data
        try {
            if (!dvLines.isEmpty()) decodeTableSize(dvLines);
            if (!binLines.isEmpty()) decode_bin(binLines);
            if (!maxLines.isEmpty()) decodeTableMax(maxLines);
            if (!maxLimitLines.isEmpty()) decodeTableMaxLimit(maxLimitLines);
            if (!minLines.isEmpty()) decodeTableMin(minLines);

            // Merge bins if all decoding steps succeed
            mergeBins();
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error during decoding process: " + e.getMessage());
        }
    }

    private static boolean isExynos() {
        return ChipInfo.which == ChipInfo.type.exynos9820 || ChipInfo.which == ChipInfo.type.exynos9825;
    }

    public static void mergeBins() {
        bins.get(1).dvfsSize.add(bins.get(0).dvfsSize.get(0));
        bins.get(1).max.add(bins.get(2).max.get(0));
        bins.get(1).maxLimit.add(bins.get(3).maxLimit.get(0));
        bins.get(1).min.add(bins.get(4).min.get(0));

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
        level.lines.add(lines.trim().replace(">;", ""));
        return level;
    }

    public static List<String> genTable(int type, AppCompatActivity activity) {
        if (!isExynos()) {
            return List.of();
        }

        List<String> lines = new ArrayList<>();

        switch (type) {
            case 0 ->
                    appendLines(lines, "gpu_dvfs_table_size = <", bins.get(0).dvfsSize.get(0).lines);
            case 1 -> {
                lines.add("gpu_dvfs_table = <");
                int l = 0;
                for (var level : bins.get(0).levels) {
                    lines.addAll(level.lines);
                    lines.add(" ");
                    for (String metaLine : bins.get(0).meta.get(l++).lines) {
                        lines.add(metaLine.trim());
                        lines.add(" ");
                    }
                }
                lines.remove(lines.size() - 1); // Remove trailing space
                lines.add(">;");
            }
            case 2 -> appendLines(lines, "gpu_max_clock = <", bins.get(0).max.get(0).lines);
            case 3 ->
                    appendLines(lines, "gpu_max_clock_limit = <", bins.get(0).maxLimit.get(0).lines);
            case 4 -> appendLines(lines, "gpu_min_clock = <", bins.get(0).min.get(0).lines);
            default -> throw new IllegalArgumentException("Invalid type: " + type);
        }

        if (!List.of(String.join("", lines)).toString().contains("0x")) {
            System.out.println("table: " + List.of(String.join("", lines)).toString());
            DialogUtil.showError(activity, "Something is messed up with the data");
            throw new RuntimeException("Output does not contain '0x' so something is messed up");
        }

        return List.of(String.join("", lines));
    }

    private static void appendLines(List<String> lines, String prefix, List<String> content) {
        lines.add(prefix);
        lines.addAll(content);
        lines.add(">;");
    }

    public static void writeOut(AppCompatActivity activity) throws IOException {
        Path filePath = Paths.get(KonaBessCore.dts_path);
        ArrayList<String> newDts = new ArrayList<>(linesInDtsCode);
        newDts.addAll(binPosition, genTable(1, activity));
        newDts.addAll(bin_positiondv, genTable(0, activity));
        newDts.addAll(binPositionMin, genTable(4, activity));
        newDts.addAll(binPositionMaxLimit, genTable(3, activity));
        newDts.addAll(binPositionMax, genTable(2, activity));

        // Use try-with-resources for automatic resource management
        try (BufferedWriter writer = Files.newBufferedWriter(filePath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            for (String line : newDts) {
                writer.write(line);
                writer.newLine();
            }
        }
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

    private static long getFrequencyFromLevel(level level) throws Exception {
        return level.lines.stream() // Stream through lines
                .filter(line -> line.contains("0x")) // Find the first line containing "0x"
                .findFirst() // Get the first match
                .map(DtsHelper::decode_int_line) // Decode the line
                .map(decoded -> decoded.value) // Extract the value
                .orElseThrow(Exception::new); // Throw an exception if no match is found
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
//                    DialogUtil.showError(activity, "Can't add new level");
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
                new MaterialAlertDialogBuilder(activity)
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

    private static void generateALevel(AppCompatActivity activity, int last, int levelID, LinearLayout page) throws Exception {
        // Handle back press to go back to the previous level
        ((MainActivity) activity).onBackPressedListener = new MainActivity.onBackPressedListener() {
            @Override
            public void onBackPressed() {
                try {
                    generateLevels(activity, last, page);
                } catch (Exception ignored) {
                }
            }
        };

        // RecyclerView instead of ListView
        RecyclerView recyclerView = new RecyclerView(activity);
        recyclerView.setLayoutManager(new LinearLayoutManager(activity));

        // Prepare the item list
        ArrayList<ParamAdapter.item> items = new ArrayList<>();

        // Back button item
        items.add(new ParamAdapter.item() {{
            title = activity.getResources().getString(R.string.back);
            subtitle = "";
        }});

        // Add level parameters
        for (String line : bins.get(last).levels.get(levelID).lines) {
            items.add(new ParamAdapter.item() {{
                title = KonaBessStr.convert_level_params(DtsHelper.decode_hex_line(line).name, activity);
                subtitle = String.valueOf(DtsHelper.decode_int_line(line).value);
            }});
        }

        // RecyclerView Adapter
        recyclerView.setAdapter(new MaterialLevelAdapter(items, activity, (position) -> {
            try {
                if (position == 0) {
                    // Back button functionality
                    generateLevels(activity, last, page);
                    return;
                }

                // Edit dialog for parameter values
                String raw_value = String.valueOf(DtsHelper.decode_int_line(
                        bins.get(last).levels.get(levelID).lines.get(position - 1)).value);

                // Material EditText with dynamic styles
                EditText editText = new EditText(activity);
                editText.setInputType(InputType.TYPE_CLASS_NUMBER);
                editText.setText(raw_value);
                editText.setPadding(32, 32, 32, 32);

                // Dynamic Material Dialog
                new MaterialAlertDialogBuilder(activity)
                        .setTitle(activity.getResources().getString(R.string.edit) + " \"" + items.get(position).title + "\"")
                        .setView(editText)
                        .setPositiveButton(R.string.save, (dialog, which) -> {
                            try {
                                bins.get(last).levels.get(levelID).lines.set(
                                        position - 1, DtsHelper.inputToHex(editText.getText().toString()));

                                generateALevel(activity, last, levelID, page);
                                Toast.makeText(activity, R.string.save_success, Toast.LENGTH_SHORT).show();
                            } catch (Exception e) {
                                e.printStackTrace();
                                DialogUtil.showError(activity, R.string.save_failed);
                            }
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .create().show();

            } catch (Exception e) {
                e.printStackTrace();
                DialogUtil.showError(activity, R.string.error_occur);
            }
        }));

        // Wrap RecyclerView in MaterialCardView
        MaterialCardView cardView = DialogUtil.createDynamicCard(activity, recyclerView);

        // Update the page view
        page.removeAllViews();
        page.addView(cardView);
    }

    private static void generateBins(AppCompatActivity activity, LinearLayout page) throws Exception {
        // Handle back press to return to the main view
        ((MainActivity) activity).onBackPressedListener = new MainActivity.onBackPressedListener() {
            @Override
            public void onBackPressed() {
                ((MainActivity) activity).showMainView();
            }
        };

        // Create RecyclerView instead of ListView
        RecyclerView recyclerView = new RecyclerView(activity);
        recyclerView.setLayoutManager(new LinearLayoutManager(activity)); // Vertical scrolling

        // Prepare items
        ArrayList<ParamAdapter.item> items = new ArrayList<>();
        for (int i = 0; i < bins.size(); i++) {
            ParamAdapter.item item = new ParamAdapter.item();
            item.title = KonaBessStr.convertBins(bins.get(i).id, activity);
            item.subtitle = ""; // Can be extended for additional info
            items.add(item);
        }

        // Adapter for RecyclerView
        recyclerView.setAdapter(new MaterialBinAdapter(items, activity, (position) -> {
            try {
                generateLevels(activity, position, page);
            } catch (Exception e) {
                e.printStackTrace();
                DialogUtil.showError(activity, R.string.error_occur);
            }
        }));

        // Dynamic Material Card for wrapping RecyclerView
        MaterialCardView cardView = DialogUtil.createDynamicCard(activity, recyclerView);

        // Update page UI
        page.removeAllViews();
        page.addView(cardView);
    }

    private static View generateToolBar(AppCompatActivity activity) {
        // Create a MaterialToolbar
        MaterialToolbar toolbar = new MaterialToolbar(activity);

        // Set toolbar title
        toolbar.setTitle(R.string.save_freq_table);
        toolbar.setTitleTextColor(MaterialColors.getColor(
                activity,
                com.google.android.material.R.attr.colorOnPrimary, // Dynamic color
                Color.WHITE // Fallback color
        ));

        // Dynamic background color for toolbar
        int toolbarColor = DialogUtil.getDynamicColor(activity, com.google.android.material.R.attr.colorPrimary);
        toolbar.setBackgroundColor(toolbarColor); // Matches notification primary color

        // Set toolbar layout parameters
        toolbar.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        toolbar.setPadding(16, 16, 16, 16); // Padding inside toolbar

        // Add a Horizontal ScrollView for buttons
        HorizontalScrollView scrollView = new HorizontalScrollView(activity);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        // LinearLayout inside ScrollView to hold buttons
        LinearLayout buttonContainer = new LinearLayout(activity);
        buttonContainer.setOrientation(LinearLayout.HORIZONTAL);
        buttonContainer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        scrollView.addView(buttonContainer); // Add button container to scroll view

        // Add a button inside the container
        MaterialButton button = new MaterialButton(activity, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);

        button.setText(R.string.save_freq_table);
        button.setPadding(16, 8, 16, 8); // Padding inside button
        button.setCornerRadius(16); // Rounded corners
        button.setStrokeWidth(2); // Border width

        // Dynamic button colors
        int buttonBackground = DialogUtil.getDynamicColor(activity, com.google.android.material.R.attr.colorPrimaryContainer);
        int buttonTextColor = DialogUtil.getDynamicColor(activity, com.google.android.material.R.attr.colorOnPrimaryContainer);
        int buttonStrokeColor = DialogUtil.getDynamicColor(activity, com.google.android.material.R.attr.colorPrimary);

        button.setBackgroundTintList(ColorStateList.valueOf(buttonBackground));
        button.setTextColor(buttonTextColor);
        button.setStrokeColor(ColorStateList.valueOf(buttonStrokeColor));

        // Set button click listener
        button.setOnClickListener(v -> {
            try {
                writeOut(activity);
                Toast.makeText(activity, R.string.save_success, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                System.out.println(e.getMessage() + e.getCause());
                DialogUtil.showError(activity, R.string.save_failed);
            }
        });

        // Add button to container
        buttonContainer.addView(button);

        // Create a wrapper layout for both toolbar and scroll view
        LinearLayout wrapperLayout = new LinearLayout(activity);
        wrapperLayout.setOrientation(LinearLayout.VERTICAL);
        wrapperLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        wrapperLayout.addView(toolbar);     // Add toolbar first
        wrapperLayout.addView(scrollView);  // Add scrollable buttons

        return wrapperLayout; // Return the wrapper layout
    }

    public static class MaterialLevelAdapter extends RecyclerView.Adapter<MaterialLevelAdapter.ViewHolder> {
        private final ArrayList<ParamAdapter.item> items;
        private final Context context;
        private final OnItemClickListener listener;

        public MaterialLevelAdapter(ArrayList<ParamAdapter.item> items, Context context, OnItemClickListener listener) {
            this.items = items;
            this.context = context;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            // MaterialCardView for each item
            MaterialCardView cardView = new MaterialCardView(context);
            cardView.setCardElevation(6f);
            cardView.setStrokeWidth(2);

            int colorPrimary = DialogUtil.getDynamicColor(context, com.google.android.material.R.attr.colorPrimaryContainer);
            int strokeColor = DialogUtil.getDynamicColor(context, com.google.android.material.R.attr.colorPrimary);

            cardView.setCardBackgroundColor(colorPrimary);
            cardView.setStrokeColor(strokeColor);

            // TextView inside the card
            MaterialTextView textView = new MaterialTextView(context);
            textView.setPadding(32, 24, 32, 24);
            textView.setTextSize(16);
            textView.setTextColor(DialogUtil.getDynamicColor(context, com.google.android.material.R.attr.colorOnSurface));

            cardView.addView(textView);

            return new ViewHolder(cardView, textView);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ParamAdapter.item item = items.get(position);
            holder.textView.setText(item.title + "\n" + item.subtitle);

            // Handle item clicks
            holder.itemView.setOnClickListener(v -> listener.onItemClick(position));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        public interface OnItemClickListener {
            void onItemClick(int position);
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            MaterialTextView textView;

            public ViewHolder(@NonNull View itemView, MaterialTextView textView) {
                super(itemView);
                this.textView = textView;
            }
        }
    }

    public static class MaterialBinAdapter extends RecyclerView.Adapter<MaterialBinAdapter.ViewHolder> {
        private final ArrayList<ParamAdapter.item> items;
        private final Context context;
        private final OnItemClickListener listener;

        public MaterialBinAdapter(ArrayList<ParamAdapter.item> items, Context context, OnItemClickListener listener) {
            this.items = items;
            this.context = context;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            // Inflate MaterialCardView
            MaterialCardView cardView = new MaterialCardView(context);
            cardView.setCardElevation(6f);
            cardView.setStrokeWidth(2);

            // Dynamic colors
            int colorPrimary = DialogUtil.getDynamicColor(context, com.google.android.material.R.attr.colorPrimaryContainer);
            int strokeColor = DialogUtil.getDynamicColor(context, com.google.android.material.R.attr.colorPrimary);

            cardView.setCardBackgroundColor(colorPrimary);
            cardView.setStrokeColor(strokeColor);

            // TextView inside the card
            MaterialTextView textView = new MaterialTextView(context);
            textView.setPadding(32, 24, 32, 24);
            textView.setTextSize(16);
            textView.setTextColor(DialogUtil.getDynamicColor(context, com.google.android.material.R.attr.colorOnSurface));
            cardView.addView(textView);

            return new ViewHolder(cardView, textView);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ParamAdapter.item item = items.get(position);
            holder.textView.setText(item.title);

            // Handle click events
            holder.itemView.setOnClickListener(v -> listener.onItemClick(position));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        // Interface for click handling
        public interface OnItemClickListener {
            void onItemClick(int position);
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            MaterialTextView textView;

            public ViewHolder(@NonNull View itemView, MaterialTextView textView) {
                super(itemView);
                this.textView = textView;
            }
        }
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