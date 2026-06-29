package xzr.konabess;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.text.InputType;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
import java.util.List;

import xzr.konabess.adapters.ParamAdapter;
import xzr.konabess.utils.DialogUtil;
import xzr.konabess.utils.DtsHelper;

/**
 * Parses Samsung GPU DVFS properties from a decompiled DTS, exposes them to the editor UI, and
 * serializes edited values back into the source file.
 *
 * <p>The editor uses static session state. {@link #init()} must run before {@link #decode()}, and
 * decoding must complete before UI generation or serialization.
 */
public class GpuTableEditor {
    private static final List<bin> bins = new ArrayList<>();

    private static int binPosition;
    private static int bin_positiondv;
    private static int binPositionMax;
    private static int binPositionMaxLimit;
    private static int binPositionMin;

    private static List<String> linesInDtsCode = new ArrayList<>();

    /**
     * Resets parser state and loads {@link KonaBessCore#dts_path} into memory.
     *
     * @throws IOException if the selected DTS cannot be read
     */
    public static void init() throws IOException {
        binPosition = bin_positiondv = binPositionMax = binPositionMaxLimit = binPositionMin = -1;

        bins.clear();
        linesInDtsCode.clear();

        linesInDtsCode = Files.readAllLines(Paths.get(KonaBessCore.dts_path));
    }

    /**
     * Removes supported GPU properties from the loaded DTS and decodes them into one editable bin.
     *
     * <p>Removal indices are retained for {@link #writeOut(AppCompatActivity)}. Decode failures are
     * written to standard error and are not propagated.
     */
    public static void decode() {
        List<String> dvLines = new ArrayList<>();
        List<String> binLines = new ArrayList<>();
        List<String> maxLines = new ArrayList<>();
        List<String> maxLimitLines = new ArrayList<>();
        List<String> minLines = new ArrayList<>();

        for (int i = 0; i < linesInDtsCode.size(); i++) {
            String currentLine = linesInDtsCode.get(i).trim().replace(">;", "");

            if (isExynos()) {
                if (currentLine.contains("gpu_dvfs_table_size = <")) {
                    if (bin_positiondv < 0)
                        bin_positiondv = i;
                    dvLines.add(linesInDtsCode.remove(i));
                    i--;
                    continue;
                }

                if (currentLine.contains("gpu_dvfs_table = ")) {
                    if (binPosition < 0) binPosition = i;
                    binLines.add(linesInDtsCode.remove(i));
                    i--;
                    continue;
                }

                if (currentLine.contains("gpu_max_clock = <")) {
                    if (binPositionMax < 0) binPositionMax = i;
                    maxLines.add(linesInDtsCode.remove(i));
                    i--;
                    continue;
                }

                if (currentLine.contains("gpu_max_clock_limit = <")) {
                    if (binPositionMaxLimit < 0) binPositionMaxLimit = i;
                    maxLimitLines.add(linesInDtsCode.remove(i));
                    i--;
                    continue;
                }

                if (currentLine.contains("gpu_min_clock = <")) {
                    if (binPositionMin < 0) binPositionMin = i;
                    minLines.add(linesInDtsCode.remove(i));
                    i--;
                }
            }
        }

        try {
            if (!dvLines.isEmpty()) decodeTableSize(dvLines);
            if (!binLines.isEmpty()) decode_bin(binLines);
            if (!maxLines.isEmpty()) decodeTableMax(maxLines);
            if (!maxLimitLines.isEmpty()) decodeTableMaxLimit(maxLimitLines);
            if (!minLines.isEmpty()) decodeTableMin(minLines);

            mergeBins();
        } catch (Exception e) {
            System.err.println("Error during decoding process: " + e.getMessage());
        }
    }

    /**
     * Checks whether the active chip uses the table format handled by this editor.
     *
     * @return {@code true} for the four recognized Exynos models
     */
    private static boolean isExynos() {
        return ChipInfo.which == ChipInfo.type.exynos9820 || ChipInfo.which == ChipInfo.type.exynos9825 || ChipInfo.which == ChipInfo.type.exynos9810|| ChipInfo.which == ChipInfo.type.exynos990;
    }

    /**
     * Combines the five partial decode results into the table bin at index one.
     *
     * <p>The required append order is size, table, max clock, max-clock limit, and min clock. All
     * temporary bins are removed after their values are copied.
     *
     * @throws IndexOutOfBoundsException if a required property was not decoded
     */
    public static void mergeBins() {
        bins.get(1).dvfsSize.add(bins.get(0).dvfsSize.get(0));

        bins.get(1).max.add(bins.get(2).max.get(0));
        bins.get(1).maxLimit.add(bins.get(3).maxLimit.get(0));
        bins.get(1).min.add(bins.get(4).min.get(0));

        for (int i = 4; i >= 0; i--) {
            if (i != 1) bins.remove(i);
        }
    }

    /**
     * Decodes the first {@code gpu_dvfs_table_size} property into a partial bin.
     *
     * @param lines matching DTS property lines
     */
    public static void decodeTableSize(List<String> lines) {
        bin bin = new bin();
        bin.dvfsSize = new ArrayList<>();

        bin.dvfsSize.add(
                decodeTableFrequency(
                        lines.get(0)
                                .trim()
                                .replace("gpu_dvfs_table_size = <", "")
                                .replace(">;", "")
                )
        );
        bins.add(bin);
    }

    /**
     * Decodes the first {@code gpu_max_clock} property into a partial bin.
     *
     * @param lines matching DTS property lines
     */
    public static void decodeTableMax(List<String> lines) {
        bin bin = new bin();
        bin.max = new ArrayList<>();
        bin.max.add(
                decodeTableFrequency(
                        lines.get(0)
                                .trim()
                                .replace("gpu_max_clock = <", "")
                                .replace(">;", "")
                )
        );
        bins.add(bin);
    }

    /**
     * Decodes the first {@code gpu_max_clock_limit} property into a partial bin.
     *
     * @param lines matching DTS property lines
     */
    public static void decodeTableMaxLimit(List<String> lines) {
        bin bin = new bin();
        bin.maxLimit = new ArrayList<>();
        bin.maxLimit.add(
                decodeTableFrequency(
                        lines.get(0)
                                .trim()
                                .replace("gpu_max_clock_limit = <", "")
                                .replace(">;", "")
                )
        );
        bins.add(bin);
    }

    /**
     * Decodes the first {@code gpu_min_clock} property into a partial bin.
     *
     * @param lines matching DTS property lines
     */
    public static void decodeTableMin(List<String> lines) {
        bin bin = new bin();
        bin.min = new ArrayList<>();
        bin.min.add(
                decodeTableFrequency(
                        lines.get(0)
                                .trim()
                                .replace("gpu_min_clock = <", "")
                                .replace(">;", "")
                )
        );
        bins.add(bin);
    }

    /**
     * Decodes the first {@code gpu_dvfs_table} property into frequency and metadata rows.
     *
     * <p>Each row contains one frequency cell followed by seven metadata cells.
     *
     * @param lines matching DTS property lines
     */
    public static void decode_bin(List<String> lines) {
        bin bin = new bin();
        bin.header = new ArrayList<>();
        bin.levels = new ArrayList<>();
        bin.meta = new ArrayList<>();
        bin.max = new ArrayList<>();
        bin.min = new ArrayList<>();
        bin.maxLimit = new ArrayList<>();
        bin.dvfsSize = new ArrayList<>();
        bin.id = 0;

        String[] hexArray = lines.get(0)
                .trim()
                .replace("gpu_dvfs_table = <", "")
                .split(" ");

        List<List<String>> result = new ArrayList<>();
        for (int i = 0; i < hexArray.length; i += 8) {
            result.add(Arrays.asList(
                    Arrays.copyOfRange(hexArray, i, Math.min(i + 8, hexArray.length))
            ));
        }

        for (List<String> group : result) {
            bin.levels.add(decodeTableFrequency(group.get(0)));
        }

        List<String> meta = new ArrayList<>();
        for (List<String> group : result) {
            meta.add(String.join(" ", group.subList(1, group.size())));
        }
        for (String m : meta) {
            bin.meta.add(decodeTableFrequency(m));
        }

        bins.add(bin);
    }

    /**
     * Stores a trimmed table fragment in a level container.
     *
     * @param lines scalar or metadata cells from a DTS property
     * @return level containing the normalized fragment
     */
    private static level decodeTableFrequency(String lines) {
        level level = new level();
        level.lines = new ArrayList<>();

        level.lines.add(lines.trim().replace(">;", ""));
        return level;
    }

    /**
     * Serializes one supported GPU property from the current bin.
     *
     * @param type property selector: 0=size, 1=table, 2=max clock, 3=max-clock limit, 4=min clock
     * @param activity activity used to report invalid serialized data
     * @return a single-element list containing the complete property, or an empty list for an
     *     unsupported chip
     * @throws IllegalArgumentException if {@code type} is outside the supported range
     * @throws RuntimeException if the generated property contains no hexadecimal cell
     */
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

                lines.remove(lines.size() - 1);
                lines.add(">;");
            }
            case 2 ->
                    appendLines(lines, "gpu_max_clock = <", bins.get(0).max.get(0).lines);
            case 3 ->
                    appendLines(lines, "gpu_max_clock_limit = <", bins.get(0).maxLimit.get(0).lines);
            case 4 ->
                    appendLines(lines, "gpu_min_clock = <", bins.get(0).min.get(0).lines);
            default ->
                    throw new IllegalArgumentException("Invalid type: " + type);
        }

        if (!String.join("", lines).contains("0x")) {
            System.out.println("table: " + List.of(String.join("", lines)));

            DialogUtil.showError(activity, "Something is messed up with the data");
            throw new RuntimeException("Output does not contain '0x' so something is messed up");
        }

        return List.of(String.join("", lines));
    }

    /**
     * Appends a DTS property prefix, its cells, and the closing delimiter.
     *
     * @param lines serialization buffer
     * @param prefix property name and opening delimiter
     * @param content serialized property cells
     */
    private static void appendLines(List<String> lines, String prefix, List<String> content) {
        lines.add(prefix);
        lines.addAll(content);
        lines.add(">;");
    }

    /**
     * Reinserts all generated GPU properties and replaces the selected DTS file.
     *
     * <p>Property positions recorded by {@link #decode()} are used against the DTS with its original
     * GPU properties removed.
     *
     * @param activity activity used by serialization error dialogs
     * @throws IOException if the DTS cannot be created or replaced
     */
    public static void writeOut(AppCompatActivity activity) throws IOException {
        Path filePath = Paths.get(KonaBessCore.dts_path);

        ArrayList<String> newDts = new ArrayList<>(linesInDtsCode);

        newDts.addAll(binPosition, genTable(1, activity));

        newDts.addAll(bin_positiondv, genTable(0, activity));

        newDts.addAll(binPositionMin, genTable(4, activity));

        newDts.addAll(binPositionMaxLimit, genTable(3, activity));

        newDts.addAll(binPositionMax, genTable(2, activity));

        try (BufferedWriter writer = Files.newBufferedWriter(
                filePath,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            for (String line : newDts) {
                writer.write(line);
                writer.newLine();
            }
        }
    }

    /**
     * Copies a level and its mutable line list.
     *
     * @param from source level
     * @return independent level container
     */
    private static level level_clone(level from) {
        level next = new level();

        next.lines = new ArrayList<>(from.lines);
        return next;
    }

    /**
     * Extension point for device-specific limits on the number of frequency rows.
     *
     * <p>The current implementation permits every addition.
     *
     * @param binID target bin index
     * @param context context available for a future user-facing limit message
     * @return always {@code true}
     */
    public static boolean canAddNewLevel(int binID, Context context) {
        return true;
    }

    /**
     * Encodes a table-size value as a hexadecimal cell followed by {@code 0x8}.
     *
     * @param input number of frequency rows
     * @return encoded level container
     */
    private static level inputToHex(int input) {
        level level = new level();

        level.lines = List.of("0x" + Integer.toHexString(input) + " 0x8");
        return level;
    }

    /**
     * Parses the first hexadecimal fragment stored in a level.
     *
     * @param level frequency level
     * @return decoded numeric frequency
     * @throws IllegalArgumentException if the hexadecimal fragment is malformed
     * @throws Exception if the level contains no hexadecimal fragment
     */
    private static long getFrequencyFromLevel(level level) throws Exception {
        return level.lines.stream()
                .filter(line -> line.contains("0x"))
                .findFirst()
                .map(DtsHelper::decode_int_line)
                .map(decoded -> decoded.value)
                .orElseThrow(Exception::new);
    }

    /**
     * Displays the frequency rows for one bin and installs add, edit, remove, and back actions.
     *
     * <p>The list contains a back row, a prepend row, the frequency rows, and a final append row.
     *
     * @param activity activity hosting the editor
     * @param id index of the bin being edited
     * @param page container replaced with the level list
     * @throws Exception if a frequency or bin label cannot be decoded
     */
    private static void generateLevels(AppCompatActivity activity, int id, LinearLayout page) throws Exception {
        generateData();

        ((MainActivity) activity).onBackPressedListener = new MainActivity.onBackPressedListener() {
            /** Returns from the level list to the bin list. */
            @Override
            public void onBackPressed() {
                try {
                    generateBins(activity, page);
                } catch (Exception e) {
                    DialogUtil.showError(activity, "Generate Bins error");
                }
            }
        };

        RecyclerView recyclerView = new RecyclerView(activity);
        recyclerView.setLayoutManager(new LinearLayoutManager(activity));

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
            if (freq == 0) continue;

            ParamAdapter.item item = new ParamAdapter.item();
            item.title = freq / 1000 + "MHz";
            item.subtitle = "";
            items.add(item);
        }

        items.add(new ParamAdapter.item() {{
            title = activity.getResources().getString(R.string.new_item);
            subtitle = activity.getResources().getString(R.string.new_desc);
        }});

        MaterialLevelAdapter adapter = new MaterialLevelAdapter(items, activity, position -> {
            if (posIsSizeMinOne(activity, id, page, position, items)) return;
            if (posIs0(activity, page, position)) return;
            if (posIs1(activity, id, page, position)) return;
            position -= 2;
            try {
                generateALevel(activity, id, position, page);
            } catch (Exception e) {
                DialogUtil.showError(activity, "Add a new level error");
            }
        });
        recyclerView.setAdapter(adapter);

        GestureDetector gestureDetector = new GestureDetector(activity, new GestureDetector.SimpleOnGestureListener() {
            /**
             * Opens removal confirmation for the long-pressed row.
             *
             * @param e completed long-press event
             */
            @Override
            public void onLongPress(MotionEvent e) {
                View child = recyclerView.findChildViewUnder(e.getX(), e.getY());
                if (child != null) {
                    int position = recyclerView.getChildAdapterPosition(child);
                    removeFrequency(activity, id, page, position, items);
                }
            }

            /**
             * Marks single taps as recognized so the RecyclerView listener can process them.
             *
             * @param e completed tap event
             * @return always {@code true}
             */
            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                return true;
            }
        });

        recyclerView.addOnItemTouchListener(new RecyclerView.OnItemTouchListener() {
            /**
             * Routes completed taps to navigation, insertion, or level editing.
             *
             * @param rv RecyclerView receiving the event
             * @param e touch event
             * @return {@code true} when a completed tap is consumed
             */
            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                View child = rv.findChildViewUnder(e.getX(), e.getY());

                if (child != null && gestureDetector.onTouchEvent(e)) {
                    int position = rv.getChildAdapterPosition(child);

                    if (e.getAction() == MotionEvent.ACTION_UP) {
                        if (posIsSizeMinOne(activity, id, page, position, items)) return true;
                        if (posIs0(activity, page, position)) return true;
                        if (posIs1(activity, id, page, position)) return true;

                        position -= 2;
                        try {
                            generateALevel(activity, id, position, page);
                        } catch (Exception ex) {
                            DialogUtil.showError(activity, "Add a new level error");
                        }
                    }
                }

                return false;
            }

            /**
             * No-op because gesture handling occurs during interception.
             *
             * @param rv RecyclerView receiving the event
             * @param e touch event
             */
            @Override
            public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
            }

            /**
             * No-op because this listener does not change interception policy.
             *
             * @param disallowIntercept requested interception state
             */
            @Override
            public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
            }
        });

        MaterialCardView cardView = DialogUtil.createDynamicCard(activity, recyclerView);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.topMargin = dp(activity, 8);
        cardView.setLayoutParams(cardParams);

        LinearLayout section = createSectionLayout(activity);
        section.addView(createSectionTitle(activity, R.string.gpu_level_list_title));
        String binName = KonaBessStr.convertBins(bins.get(id).id, activity);
        section.addView(createSectionBody(activity,
                activity.getString(R.string.gpu_level_list_body, binName)
        ));
        section.addView(cardView);

        page.removeAllViews();
        page.addView(section);
    }

    /**
     * Confirms and removes the frequency represented by a long-pressed list row.
     *
     * <p>The final append row is ignored, and the final remaining frequency cannot be removed.
     *
     * @param activity activity hosting the confirmation dialog
     * @param id target bin index
     * @param page container refreshed after removal
     * @param position adapter position, including the two leading control rows
     * @param items rows currently displayed by the adapter
     */
    private static void removeFrequency(AppCompatActivity activity,
                                        int id,
                                        LinearLayout page,
                                        int position,
                                        ArrayList<ParamAdapter.item> items) {
        if (position == items.size() - 1) {
            return;
        }

        if (bins.get(id).levels.size() == 1) {
            return;
        }
        try {
            long freqMHz = getFrequencyFromLevel(bins.get(id).levels.get(position - 2)) / 1000;
            String message = String.format(
                    activity.getResources().getString(R.string.remove_msg),
                    freqMHz
            );

            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.remove)
                    .setMessage(message)
                    .setPositiveButton(R.string.yes,
                            (dialog, which) -> {
                                bins.get(id).levels.remove(position - 2);
                                bins.get(id).meta.remove(position - 2);
                                try {
                                    generateLevels(activity, id, page);
                                } catch (Exception e) {
                                    DialogUtil.showError(activity, "Remove a frequency error");
                                }
                            }
                    )
                    .setNegativeButton(R.string.no,
                            null
                    )
                    .create()
                    .show();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    /**
     * Handles the final append row by cloning the last frequency and metadata entry.
     *
     * @param activity activity hosting the editor
     * @param id target bin index
     * @param page container refreshed after insertion
     * @param position selected adapter position
     * @param items displayed adapter rows
     * @return {@code true} when the selected row was the append control
     */
    private static boolean posIsSizeMinOne(AppCompatActivity activity, int id, LinearLayout page, int position, ArrayList<ParamAdapter.item> items) {
        if (position == items.size() - 1) {
            try {
                if (!canAddNewLevel(id, activity))
                    return true;

                bins.get(id).levels.add(
                        bins.get(id).levels.size() - 1,
                        level_clone(bins.get(id).levels.get(bins.get(id).levels.size() - 1))
                );

                bins.get(0).meta.add(
                        bins.get(0).meta.get(bins.get(0).meta.size() - 1)
                );

                generateLevels(activity, id, page);
            } catch (Exception e) {
                DialogUtil.showError(activity, "Can't add new level");
            }
            return true;
        }
        return false;
    }

    /**
     * Synchronizes min, max, max-limit, and table-size properties with the current row list.
     *
     * <p>Rows are expected in descending frequency order.
     */
    private static void generateData() {
        bins.get(0).min.set(0, bins.get(0).levels.get(bins.get(0).levels.size() - 1));

        bins.get(0).max.set(0, bins.get(0).levels.get(0));
        bins.get(0).maxLimit.set(0, bins.get(0).levels.get(0));

        bins.get(0).dvfsSize.set(0, inputToHex(bins.get(0).levels.size()));
    }

    /**
     * Handles the prepend control by cloning the first frequency and metadata entry.
     *
     * @param activity activity hosting the editor
     * @param id target bin index
     * @param page container refreshed after insertion
     * @param position selected adapter position
     * @return {@code true} when the selected row was the prepend control
     */
    private static boolean posIs1(AppCompatActivity activity, int id, LinearLayout page, int position) {
        if (position == 1) {
            try {
                if (!canAddNewLevel(id, activity))
                    return true;

                bins.get(id).levels.add(
                        0,
                        level_clone(bins.get(id).levels.get(0))
                );

                bins.get(0).meta.add(0, bins.get(0).meta.get(0));

                generateLevels(activity, id, page);
            } catch (Exception e) {
                DialogUtil.showError(activity, "Clone a level error");
            }
            return true;
        }
        return false;
    }

    /**
     * Handles the first row as navigation back to the bin list.
     *
     * @param activity activity hosting the editor
     * @param page container replaced with the bin list
     * @param position selected adapter position
     * @return {@code true} when the selected row was the back control
     */
    private static boolean posIs0(AppCompatActivity activity, LinearLayout page, int position) {
        if (position == 0) {
            try {
                generateBins(activity, page);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            return true;
        }

        return false;
    }

    /**
     * Displays and edits the scalar fragments stored for one frequency level.
     *
     * <p>Saved decimal input is converted to hexadecimal before replacing the selected fragment.
     *
     * @param activity activity hosting the editor
     * @param last bin index
     * @param levelID frequency-row index
     * @param page container replaced with the parameter list
     * @throws Exception if an existing fragment cannot be decoded
     */
    private static void generateALevel(AppCompatActivity activity, int last, int levelID, LinearLayout page) throws Exception {
        ((MainActivity) activity).onBackPressedListener = new MainActivity.onBackPressedListener() {
            /** Returns from parameter editing to the containing level list. */
            @Override
            public void onBackPressed() {
                try {
                    generateLevels(activity, last, page);
                } catch (Exception ignored) {
                }
            }
        };

        RecyclerView recyclerView = new RecyclerView(activity);
        recyclerView.setLayoutManager(new LinearLayoutManager(activity));

        ArrayList<ParamAdapter.item> items = new ArrayList<>();
        items.add(new ParamAdapter.item() {{
            title = activity.getResources().getString(R.string.back);
            subtitle = "";
        }});
        for (String line : bins.get(last).levels.get(levelID).lines) {
            ParamAdapter.item item = new ParamAdapter.item();
            item.title = KonaBessStr.convert_level_params(
                    DtsHelper.decode_hex_line(line).name, activity
            );
            item.subtitle = String.valueOf(DtsHelper.decode_int_line(line).value);
            items.add(item);
        }

        recyclerView.setAdapter(new MaterialLevelAdapter(items, activity, (position) -> {
            try {
                if (position == 0) {
                    generateLevels(activity, last, page);
                    return;
                }

                String raw_value = String.valueOf(
                        DtsHelper.decode_int_line(
                                bins.get(last).levels.get(levelID).lines.get(position - 1)
                        ).value
                );
                EditText editText = new EditText(activity);
                editText.setInputType(InputType.TYPE_CLASS_NUMBER);
                editText.setText(raw_value);
                editText.setPadding(32, 32, 32, 32);

                new MaterialAlertDialogBuilder(activity)
                        .setTitle(activity.getResources().getString(R.string.edit)
                                + " \"" + items.get(position).title + "\"")
                        .setView(editText)
                        .setPositiveButton(R.string.save, (dialog, which) -> {
                            try {
                                bins.get(last).levels.get(levelID).lines.set(
                                        position - 1,
                                        DtsHelper.inputToHex(editText.getText().toString())
                                );

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

        MaterialCardView cardView = DialogUtil.createDynamicCard(activity, recyclerView);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.topMargin = dp(activity, 8);
        cardView.setLayoutParams(cardParams);

        page.removeAllViews();
        page.addView(cardView);
    }

    /**
     * Displays the parsed bin list and installs navigation back to the main screen.
     *
     * @param activity activity hosting the editor
     * @param page container replaced with the bin list
     * @throws Exception if a bin label cannot be resolved
     */
    private static void generateBins(AppCompatActivity activity, LinearLayout page) throws Exception {
        ((MainActivity) activity).onBackPressedListener = new MainActivity.onBackPressedListener() {
            /** Returns from the GPU editor to the main workflow screen. */
            @Override
            public void onBackPressed() {
                ((MainActivity) activity).showMainView();
            }
        };

        RecyclerView recyclerView = new RecyclerView(activity);
        recyclerView.setLayoutManager(new LinearLayoutManager(activity));

        ArrayList<ParamAdapter.item> items = new ArrayList<>();
        for (int i = 0; i < bins.size(); i++) {
            ParamAdapter.item item = new ParamAdapter.item();

            item.title = KonaBessStr.convertBins(bins.get(i).id, activity);
            item.subtitle = "";
            items.add(item);
        }

        recyclerView.setAdapter(new MaterialBinAdapter(items, activity, (position) -> {
            try {
                generateLevels(activity, position, page);
            } catch (Exception e) {
                e.printStackTrace();
                DialogUtil.showError(activity, R.string.error_occur);
            }
        }));

        MaterialCardView cardView = DialogUtil.createDynamicCard(activity, recyclerView);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.topMargin = dp(activity, 8);
        cardView.setLayoutParams(cardParams);

        LinearLayout section = createSectionLayout(activity);
        section.addView(createSectionTitle(activity, R.string.gpu_bin_list_title));
        section.addView(createSectionBody(activity, R.string.gpu_bin_list_body));
        section.addView(cardView);

        page.removeAllViews();
        page.addView(section);
    }

    /**
     * Creates the editor heading and save action.
     *
     * <p>Saving updates the DTS only; repacking and flashing remain separate main-screen actions.
     *
     * @param activity activity hosting the editor
     * @return editor header view
     */
    private static View generateToolBar(AppCompatActivity activity) {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        MaterialTextView title = new MaterialTextView(activity);
        title.setText(R.string.gpu_editor_header_title);
        title.setTextAppearance(R.style.TextAppearance_Material3_TitleLarge);
        title.setTextColor(DialogUtil.getDynamicColor(activity, com.google.android.material.R.attr.colorOnSurface));
        content.addView(title);

        MaterialTextView body = new MaterialTextView(activity);
        body.setText(R.string.gpu_editor_header_body);
        body.setTextAppearance(R.style.TextAppearance_Material3_BodyMedium);
        body.setTextColor(DialogUtil.getDynamicColor(activity, com.google.android.material.R.attr.colorOnSurfaceVariant));
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        bodyParams.topMargin = dp(activity, 8);
        body.setLayoutParams(bodyParams);
        content.addView(body);

        LinearLayout buttonRow = new LinearLayout(activity);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        rowParams.topMargin = dp(activity, 16);
        buttonRow.setLayoutParams(rowParams);
        content.addView(buttonRow);

        MaterialButton saveButton = new MaterialButton(activity);
        saveButton.setText(R.string.save_freq_table);
        saveButton.setAllCaps(false);
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        saveButton.setLayoutParams(saveParams);
        saveButton.setCornerRadius(dp(activity, 16));
        int primary = DialogUtil.getDynamicColor(activity, com.google.android.material.R.attr.colorPrimaryContainer);
        int onPrimary = DialogUtil.getDynamicColor(activity, com.google.android.material.R.attr.colorOnPrimaryContainer);
        saveButton.setBackgroundTintList(ColorStateList.valueOf(primary));
        saveButton.setTextColor(onPrimary);
        saveButton.setRippleColor(ColorStateList.valueOf(MaterialColors.layer(primary, Color.WHITE, 0.1f)));
        saveButton.setOnClickListener(v -> {
            try {
                writeOut(activity);
                Toast.makeText(activity, R.string.save_success, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                System.out.println(e.getMessage() + e.getCause());
                DialogUtil.showError(activity, R.string.save_failed);
            }
        });
        buttonRow.addView(saveButton);

        return content;
    }

    /** RecyclerView adapter for level rows and their optional subtitles. */
    public static class MaterialLevelAdapter extends RecyclerView.Adapter<MaterialLevelAdapter.ViewHolder> {
        private final ArrayList<ParamAdapter.item> items;
        private final Context context;
        private final OnItemClickListener listener;

        /**
         * Creates a level-row adapter.
         *
         * @param items rows displayed by the adapter
         * @param context context used to create and theme views
         * @param listener selection callback
         */
        public MaterialLevelAdapter(ArrayList<ParamAdapter.item> items, Context context, OnItemClickListener listener) {
            this.items = items;
            this.context = context;
            this.listener = listener;
        }

        /**
         * Creates a themed card row containing title and subtitle views.
         *
         * @param parent RecyclerView receiving the row
         * @param viewType adapter view type
         * @return new row holder
         */
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            MaterialCardView cardView = new MaterialCardView(context);
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            params.topMargin = dp(context, 6);
            params.bottomMargin = dp(context, 6);
            cardView.setLayoutParams(params);
            cardView.setRadius(dp(context, 18));
            cardView.setCardElevation(0f);
            cardView.setStrokeWidth(0);
            cardView.setCardBackgroundColor(DialogUtil.getDynamicColor(
                    context,
                    com.google.android.material.R.attr.colorSurfaceVariant
            ));

            LinearLayout content = new LinearLayout(context);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(dp(context, 20), dp(context, 16), dp(context, 20), dp(context, 16));
            cardView.addView(content);

            MaterialTextView titleView = new MaterialTextView(context);
            titleView.setTextAppearance(R.style.TextAppearance_Material3_TitleMedium);
            titleView.setTextColor(DialogUtil.getDynamicColor(context, com.google.android.material.R.attr.colorOnSurface));
            content.addView(titleView);

            MaterialTextView subtitleView = new MaterialTextView(context);
            subtitleView.setTextAppearance(R.style.TextAppearance_Material3_BodySmall);
            subtitleView.setTextColor(DialogUtil.getDynamicColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant));
            LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            subtitleParams.topMargin = dp(context, 2);
            subtitleView.setLayoutParams(subtitleParams);
            content.addView(subtitleView);

            return new ViewHolder(cardView, titleView, subtitleView);
        }

        /**
         * Binds row text, subtitle visibility, and the selection callback.
         *
         * @param holder row holder
         * @param position adapter position
         */
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ParamAdapter.item item = items.get(position);
            holder.titleView.setText(item.title);
            if (item.subtitle == null || item.subtitle.isEmpty()) {
                holder.subtitleView.setVisibility(View.GONE);
            } else {
                holder.subtitleView.setVisibility(View.VISIBLE);
                holder.subtitleView.setText(item.subtitle);
            }
            holder.itemView.setOnClickListener(v -> listener.onItemClick(position));
        }

        /** @return number of displayed rows */
        @Override
        public int getItemCount() {
            return items.size();
        }

        /** Receives adapter row selections. */
        public interface OnItemClickListener {
            /**
             * Handles a selected row.
             *
             * @param position adapter position
             */
            void onItemClick(int position);
        }

        /** Holds the title and subtitle views for a reusable card row. */
        static class ViewHolder extends RecyclerView.ViewHolder {
            final MaterialTextView titleView;
            final MaterialTextView subtitleView;

            /**
             * Stores the views created for one row.
             *
             * @param itemView card root
             * @param titleView row title
             * @param subtitleView optional row subtitle
             */
            public ViewHolder(@NonNull View itemView,
                              MaterialTextView titleView,
                              MaterialTextView subtitleView) {
                super(itemView);
                this.titleView = titleView;
                this.subtitleView = subtitleView;
            }
        }
    }

    /**
     * Creates the vertical layout used for titled editor sections.
     *
     * @param context context used for dimensions
     * @return empty section layout
     */
    private static LinearLayout createSectionLayout(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = dp(context, 12);
        layout.setLayoutParams(params);
        return layout;
    }

    /**
     * Creates a themed editor-section title.
     *
     * @param context themed context
     * @param textRes title resource
     * @return title view
     */
    private static MaterialTextView createSectionTitle(Context context, int textRes) {
        MaterialTextView title = new MaterialTextView(context);
        title.setText(textRes);
        title.setTextAppearance(R.style.TextAppearance_Material3_TitleMedium);
        title.setTextColor(DialogUtil.getDynamicColor(context, com.google.android.material.R.attr.colorOnSurface));
        return title;
    }

    /**
     * Creates editor-section body text from a resource.
     *
     * @param context themed context
     * @param textRes body resource
     * @return body view
     */
    private static MaterialTextView createSectionBody(Context context, int textRes) {
        return createSectionBody(context, context.getString(textRes));
    }

    /**
     * Creates themed editor-section body text.
     *
     * @param context themed context
     * @param text body content
     * @return body view
     */
    private static MaterialTextView createSectionBody(Context context, CharSequence text) {
        MaterialTextView body = new MaterialTextView(context);
        body.setText(text);
        body.setTextAppearance(R.style.TextAppearance_Material3_BodyMedium);
        body.setTextColor(DialogUtil.getDynamicColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = dp(context, 4);
        body.setLayoutParams(params);
        return body;
    }

    /**
     * Creates the outer surface containing the GPU editor.
     *
     * @param context themed context
     * @return empty editor card
     */
    private static MaterialCardView createEditorCard(Context context) {
        MaterialCardView card = new MaterialCardView(context);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = dp(context, 12);
        card.setLayoutParams(params);
        card.setCardBackgroundColor(DialogUtil.getDynamicColor(context, com.google.android.material.R.attr.colorSurface));
        card.setStrokeColor(DialogUtil.getDynamicColor(context, com.google.android.material.R.attr.colorOutline));
        card.setStrokeWidth(1);
        card.setRadius(dp(context, 24));
        card.setCardElevation(0f);
        card.setUseCompatPadding(false);
        card.setPreventCornerOverlap(false);
        return card;
    }

    /**
     * Converts a density-independent size to rounded physical pixels.
     *
     * @param context context that supplies display density
     * @param value size in dp
     * @return size in pixels
     */
    private static int dp(Context context, int value) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    /** RecyclerView adapter for selectable GPU-table bins. */
    public static class MaterialBinAdapter extends RecyclerView.Adapter<MaterialBinAdapter.ViewHolder> {
        private final ArrayList<ParamAdapter.item> items;

        private final Context context;

        private final OnItemClickListener listener;

        /**
         * Creates a bin-row adapter.
         *
         * @param items rows displayed by the adapter
         * @param context context used to create and theme views
         * @param listener selection callback
         */
        public MaterialBinAdapter(ArrayList<ParamAdapter.item> items, Context context, OnItemClickListener listener) {
            this.items = items;
            this.context = context;
            this.listener = listener;
        }

        /**
         * Creates a themed card row containing title and subtitle views.
         *
         * @param parent RecyclerView receiving the row
         * @param viewType adapter view type
         * @return new row holder
         */
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            MaterialCardView cardView = new MaterialCardView(context);
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            params.topMargin = dp(context, 6);
            params.bottomMargin = dp(context, 6);
            cardView.setLayoutParams(params);
            cardView.setRadius(dp(context, 18));
            cardView.setCardElevation(0f);
            cardView.setStrokeWidth(0);
            cardView.setCardBackgroundColor(DialogUtil.getDynamicColor(
                    context,
                    com.google.android.material.R.attr.colorSurfaceVariant
            ));

            LinearLayout content = new LinearLayout(context);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(dp(context, 20), dp(context, 16), dp(context, 20), dp(context, 16));
            cardView.addView(content);

            MaterialTextView titleView = new MaterialTextView(context);
            titleView.setTextAppearance(R.style.TextAppearance_Material3_TitleMedium);
            titleView.setTextColor(DialogUtil.getDynamicColor(context, com.google.android.material.R.attr.colorOnSurface));
            content.addView(titleView);

            MaterialTextView subtitleView = new MaterialTextView(context);
            subtitleView.setTextAppearance(R.style.TextAppearance_Material3_BodySmall);
            subtitleView.setTextColor(DialogUtil.getDynamicColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant));
            LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            subtitleParams.topMargin = dp(context, 2);
            subtitleView.setLayoutParams(subtitleParams);
            content.addView(subtitleView);

            return new ViewHolder(cardView, titleView, subtitleView);
        }

        /**
         * Binds row text, subtitle visibility, and the selection callback.
         *
         * @param holder row holder
         * @param position adapter position
         */
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ParamAdapter.item item = items.get(position);
            holder.titleView.setText(item.title);
            if (item.subtitle == null || item.subtitle.isEmpty()) {
                holder.subtitleView.setVisibility(View.GONE);
            } else {
                holder.subtitleView.setVisibility(View.VISIBLE);
                holder.subtitleView.setText(item.subtitle);
            }

            holder.itemView.setOnClickListener(v -> listener.onItemClick(position));
        }

        /** @return number of displayed rows */
        @Override
        public int getItemCount() {
            return items.size();
        }

        /** Receives adapter row selections. */
        public interface OnItemClickListener {
            /**
             * Handles a selected row.
             *
             * @param position adapter position
             */
            void onItemClick(int position);
        }

        /** Holds the title and subtitle views for a reusable card row. */
        static class ViewHolder extends RecyclerView.ViewHolder {
            final MaterialTextView titleView;
            final MaterialTextView subtitleView;

            /**
             * Stores the views created for one row.
             *
             * @param itemView card root
             * @param titleView row title
             * @param subtitleView optional row subtitle
             */
            public ViewHolder(@NonNull View itemView,
                              MaterialTextView titleView,
                              MaterialTextView subtitleView) {
                super(itemView);
                this.titleView = titleView;
                this.subtitleView = subtitleView;
            }
        }
    }

    /** Complete editable GPU table and its related limit properties. */
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

    /** One frequency cell or group of metadata cells. */
    private static class level {
        List<String> lines;
    }

    /** Background loader that parses the DTS before constructing the editor on the UI thread. */
    static class gpuTableLogic extends Thread {
        private final AppCompatActivity activity;
        private AlertDialog waiting;
        private final LinearLayout showedView;
        private LinearLayout page;

        /**
         * Creates a loader for an activity workspace.
         *
         * @param activity activity hosting the editor
         * @param showedView workspace that receives the editor surface
         */
        public gpuTableLogic(AppCompatActivity activity, LinearLayout showedView) {
            this.activity = activity;
            this.showedView = showedView;
        }

        /**
         * Shows progress, initializes and decodes parser state, then builds the editor UI.
         */
        @Override
        public void run() {
            activity.runOnUiThread(() -> {
                waiting = DialogUtil.getWaitDialog(activity, R.string.getting_freq_table);
                waiting.show();
            });

            try {
                init();
                decode();
            } catch (Exception e) {
                activity.runOnUiThread(() -> DialogUtil.showError(
                        activity,
                        R.string.getting_freq_table_failed + " " + e
                ));
            }

            activity.runOnUiThread(() -> {
                waiting.dismiss();
                showedView.removeAllViews();

                MaterialCardView editorSurface = createEditorCard(activity);
                LinearLayout editorContent = new LinearLayout(activity);
                editorContent.setOrientation(LinearLayout.VERTICAL);
                editorContent.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                ));
                editorContent.setPadding(
                        dp(activity, 24),
                        dp(activity, 20),
                        dp(activity, 24),
                        dp(activity, 24)
                );
                editorSurface.addView(editorContent);

                editorContent.addView(generateToolBar(activity));

                page = new LinearLayout(activity);
                page.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams pageParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                pageParams.topMargin = dp(activity, 20);
                page.setLayoutParams(pageParams);
                editorContent.addView(page);

                try {
                    generateBins(activity, page);
                } catch (Exception e) {
                    DialogUtil.showError(activity, "Failed to generate bins");
                }

                showedView.addView(editorSurface);
            });
        }
    }
}
