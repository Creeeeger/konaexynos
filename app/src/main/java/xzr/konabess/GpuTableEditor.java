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

public class GpuTableEditor {
    // List holding parsed bin data structures for GPU DVFS tables
    private static final List<bin> bins = new ArrayList<>();
    // Various positions in the DTS file where different GPU table entries were found
    private static int binPosition;
    private static int bin_positiondv;
    private static int binPositionMax;
    private static int binPositionMaxLimit;
    private static int binPositionMin;
    // Lines of the DTS source, loaded for editing
    private static List<String> linesInDtsCode = new ArrayList<>();

    /**
     * Initializes the editor state by clearing previous data and loading the DTS file into memory.
     *
     * @throws IOException If reading the DTS file fails.
     */
    public static void init() throws IOException {
        // Reset all position indices to -1 to indicate "not found"
        binPosition = bin_positiondv = binPositionMax = binPositionMaxLimit = binPositionMin = -1;
        // Clear any previously stored bin data and code lines
        bins.clear();
        linesInDtsCode.clear();

        // Read every line of the DTS file into a list for processing
        linesInDtsCode = Files.readAllLines(Paths.get(KonaBessCore.dts_path));
    }

    /**
     * Parses the loaded DTS lines to extract GPU DVFS table entries into temporary lists,
     * then decodes those entries into structured bin objects.
     */
    public static void decode() {
        // Temporary lists to hold raw lines for each table component
        List<String> dvLines = new ArrayList<>();
        List<String> binLines = new ArrayList<>();
        List<String> maxLines = new ArrayList<>();
        List<String> maxLimitLines = new ArrayList<>();
        List<String> minLines = new ArrayList<>();

        // Iterate through each line to find and remove matching GPU DVFS entries
        for (int i = 0; i < linesInDtsCode.size(); i++) {
            // Normalize the line by trimming whitespace and stripping trailing ">;"
            String currentLine = linesInDtsCode.get(i).trim().replace(">;", "");

            // Only proceed if the device uses an Exynos GPU
            if (isExynos()) {
                // 1. Find gpu_dvfs_table_size entries
                if (currentLine.contains("gpu_dvfs_table_size = <")) {
                    if (bin_positiondv < 0)
                        bin_positiondv = i;         // Record first occurrence index
                    dvLines.add(linesInDtsCode.remove(i));              // Extract and remove from code list
                    i--;                                                // Adjust index after removal
                    continue;
                }

                // 2. Find gpu_dvfs_table entries
                if (currentLine.contains("gpu_dvfs_table = ")) {
                    if (binPosition < 0) binPosition = i;
                    binLines.add(linesInDtsCode.remove(i));
                    i--;
                    continue;
                }

                // 3. Find gpu_max_clock entries
                if (currentLine.contains("gpu_max_clock = <")) {
                    if (binPositionMax < 0) binPositionMax = i;
                    maxLines.add(linesInDtsCode.remove(i));
                    i--;
                    continue;
                }

                // 4. Find gpu_max_clock_limit entries
                if (currentLine.contains("gpu_max_clock_limit = <")) {
                    if (binPositionMaxLimit < 0) binPositionMaxLimit = i;
                    maxLimitLines.add(linesInDtsCode.remove(i));
                    i--;
                    continue;
                }

                // 5. Find gpu_min_clock entries
                if (currentLine.contains("gpu_min_clock = <")) {
                    if (binPositionMin < 0) binPositionMin = i;
                    minLines.add(linesInDtsCode.remove(i));
                    i--;
                }
            }
        }

        // Attempt to decode each set of extracted lines into bin structures
        try {
            if (!dvLines.isEmpty()) decodeTableSize(dvLines);
            if (!binLines.isEmpty()) decode_bin(binLines);
            if (!maxLines.isEmpty()) decodeTableMax(maxLines);
            if (!maxLimitLines.isEmpty()) decodeTableMaxLimit(maxLimitLines);
            if (!minLines.isEmpty()) decodeTableMin(minLines);

            // After decoding individual parts, merge them into a single cohesive bin
            mergeBins();
        } catch (Exception e) {
            // Log any errors encountered during decoding without crashing
            System.err.println("Error during decoding process: " + e.getMessage());
        }
    }

    /**
     * Checks if the current device is one of the supported Exynos chipsets.
     *
     * @return true if the chip type matches exynos9820 or exynos9825.
     */
    private static boolean isExynos() {
        return ChipInfo.which == ChipInfo.type.exynos9820
                || ChipInfo.which == ChipInfo.type.exynos9825;
    }

    /**
     * Merges separate bins for size, levels, max, maxLimit, and min into a single main bin entry.
     * Removes the interim bins, leaving only the merged result at index 0 or 1.
     */
    public static void mergeBins() {
        // Add the table size value from bin[0] into bin[1]
        bins.get(1).dvfsSize.add(bins.get(0).dvfsSize.get(0));
        // Add the max and limits from the respective bins into bin[1]
        bins.get(1).max.add(bins.get(2).max.get(0));
        bins.get(1).maxLimit.add(bins.get(3).maxLimit.get(0));
        bins.get(1).min.add(bins.get(4).min.get(0));

        // Remove all intermediate bins except the merged one
        for (int i = 4; i >= 0; i--) {
            if (i != 1) bins.remove(i);
        }
    }

    /**
     * Decodes the gpu_dvfs_table_size line into a bin containing a single DVFS size value.
     *
     * @param lines List containing the raw size line.
     */
    public static void decodeTableSize(List<String> lines) {
        bin bin = new bin();
        bin.dvfsSize = new ArrayList<>();
        // Extract the numeric value within "<...>" and decode it to an integer
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
     * Decodes gpu_max_clock entries into a bin with a single maximum clock frequency.
     *
     * @param lines List containing the raw max clock line.
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
     * Decodes gpu_max_clock_limit entries into a bin with a single maximum clock limit.
     *
     * @param lines List containing the raw max limit line.
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
     * Decodes gpu_min_clock entries into a bin with a single minimum clock frequency.
     *
     * @param lines List containing the raw min clock line.
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
     * Decodes the gpu_dvfs_table array entries into a bin with multiple level and metadata values.
     *
     * @param lines List containing the raw table line with space-separated hex values.
     */
    public static void decode_bin(List<String> lines) {
        // Create a fresh bin object with all lists initialized
        bin bin = new bin();
        bin.header = new ArrayList<>();
        bin.levels = new ArrayList<>();
        bin.meta = new ArrayList<>();
        bin.max = new ArrayList<>();
        bin.min = new ArrayList<>();
        bin.maxLimit = new ArrayList<>();
        bin.dvfsSize = new ArrayList<>();
        bin.id = 0;

        // Split the hex payload into groups of 8 values each
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

        // Decode the first element of each group as a frequency level
        for (List<String> group : result) {
            bin.levels.add(decodeTableFrequency(group.get(0)));
        }

        // Collect the remaining bytes of each group as metadata and decode them
        List<String> meta = new ArrayList<>();
        for (List<String> group : result) {
            meta.add(String.join(" ", group.subList(1, group.size())));
        }
        for (String m : meta) {
            bin.meta.add(decodeTableFrequency(m));
        }

        // Add the fully populated bin to the list for later merging
        bins.add(bin);
    }

    /**
     * Wraps a single line of hex or numeric data into a level object.
     *
     * @param lines Raw string representing a frequency or metadata line.
     * @return A level object containing the trimmed line.
     */
    private static level decodeTableFrequency(String lines) {
        // Create a new level and initialize its lines list
        level level = new level();
        level.lines = new ArrayList<>();
        // Add the trimmed, cleaned line (removing any trailing ">;")
        level.lines.add(lines.trim().replace(">;", ""));
        return level;
    }

    /**
     * Generates DTS code lines for a specific GPU table section.
     *
     * @param type     Section type (0=size, 1=table, 2=max, 3=maxLimit, 4=min).
     * @param activity Activity context for error dialogs.
     * @return A list containing the assembled lines for insertion.
     */
    public static List<String> genTable(int type, AppCompatActivity activity) {
        // If not Exynos device, nothing to generate
        if (!isExynos()) {
            return List.of();
        }

        List<String> lines = new ArrayList<>();

        switch (type) {
            case 0 ->
                // Generate gpu_dvfs_table_size line
                    appendLines(lines, "gpu_dvfs_table_size = <", bins.get(0).dvfsSize.get(0).lines);
            case 1 -> {
                // Start the gpu_dvfs_table block
                lines.add("gpu_dvfs_table = <");
                int l = 0;
                // For each level, add level and its metadata
                for (var level : bins.get(0).levels) {
                    lines.addAll(level.lines);
                    lines.add(" ");
                    for (String metaLine : bins.get(0).meta.get(l++).lines) {
                        lines.add(metaLine.trim());
                        lines.add(" ");
                    }
                }
                // Remove trailing space and close the block
                lines.remove(lines.size() - 1);
                lines.add(">;");
            }
            case 2 ->
                // Generate gpu_max_clock line
                    appendLines(lines, "gpu_max_clock = <", bins.get(0).max.get(0).lines);
            case 3 ->
                // Generate gpu_max_clock_limit line
                    appendLines(lines, "gpu_max_clock_limit = <", bins.get(0).maxLimit.get(0).lines);
            case 4 ->
                // Generate gpu_min_clock line
                    appendLines(lines, "gpu_min_clock = <", bins.get(0).min.get(0).lines);
            default ->
                // Invalid type parameter
                    throw new IllegalArgumentException("Invalid type: " + type);
        }

        // Verify that output contains expected hex markers
        if (!String.join("", lines).contains("0x")) {
            System.out.println("table: " + List.of(String.join("", lines)));
            // Show error dialog before throwing
            DialogUtil.showError(activity, "Something is messed up with the data");
            throw new RuntimeException("Output does not contain '0x' so something is messed up");
        }

        // Return single concatenated string
        return List.of(String.join("", lines));
    }

    /**
     * Helper to assemble a prefix, content lines, and closing delimiter.
     *
     * @param lines   The list to append to.
     * @param prefix  The DTS property declaration (e.g., "gpu_min_clock = <").
     * @param content List of hex or numeric strings to include.
     */
    private static void appendLines(List<String> lines, String prefix, List<String> content) {
        lines.add(prefix);
        lines.addAll(content);
        lines.add(">;");
    }

    /**
     * Writes the modified DTS content back to the original DTS file.
     * Inserts the regenerated GPU tables at their recorded positions.
     *
     * @param activity Activity context used for generating table lines.
     * @throws IOException If writing to the DTS file fails.
     */
    public static void writeOut(AppCompatActivity activity) throws IOException {
        // Resolve the path to the DTS file stored in KonaBessCore.dts_path
        Path filePath = Paths.get(KonaBessCore.dts_path);

        // Start with the base DTS lines that had the original GPU table entries removed
        ArrayList<String> newDts = new ArrayList<>(linesInDtsCode);

        // Insert the reconstructed full gpu_dvfs_table block at its original index
        newDts.addAll(binPosition, genTable(1, activity));
        // Insert the gpu_dvfs_table_size line at its original index
        newDts.addAll(bin_positiondv, genTable(0, activity));
        // Insert the gpu_min_clock line at its original index
        newDts.addAll(binPositionMin, genTable(4, activity));
        // Insert the gpu_max_clock_limit line at its original index
        newDts.addAll(binPositionMaxLimit, genTable(3, activity));
        // Insert the gpu_max_clock line at its original index
        newDts.addAll(binPositionMax, genTable(2, activity));

        // Open the file for writing, creating or truncating as needed
        try (BufferedWriter writer = Files.newBufferedWriter(
                filePath,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            // Write each line followed by a newline to preserve DTS formatting
            for (String line : newDts) {
                writer.write(line);
                writer.newLine();
            }
        }
    }

    /**
     * Creates a deep clone of a level object.
     *
     * @param from The source level to duplicate.
     * @return A new level with copied lines.
     */
    private static level level_clone(level from) {
        level next = new level();
        // Copy the list of string lines
        next.lines = new ArrayList<>(from.lines);
        return next;
    }

    /**
     * Checks if another GPU frequency level can be added to the specified bin.
     *
     * @param binID   Index of the bin in question.
     * @param context Context for showing a toast if limit reached.
     * @return true if under the threshold; false otherwise.
     */
    public static boolean canAddNewLevel(int binID, Context context) {
        if (bins.get(binID).levels.size() <= 10) {
            return true;
        } else {
            // Notify user that no more levels can be added
            Toast.makeText(context, R.string.unable_add_more, Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    /**
     * Converts an integer input into a level object with hexadecimal representation.
     *
     * @param input Numeric frequency or metadata value.
     * @return A level containing a single "0x..." line.
     */
    private static level inputToHex(int input) {
        level level = new level();
        // Format integer as hex with metadata byte "0x8"
        level.lines = List.of("0x" + Integer.toHexString(input) + " 0x8");
        return level;
    }

    /**
     * Extracts a numeric frequency from a level by decoding the first hex line.
     *
     * @param level The level containing hex-encoded frequency.
     * @return The decoded frequency value.
     * @throws Exception If no valid hex line is found.
     */
    private static long getFrequencyFromLevel(level level) throws Exception {
        return level.lines.stream()
                // Filter only lines containing hex marker
                .filter(line -> line.contains("0x"))
                .findFirst()
                // Decode via helper, then extract its numeric value field
                .map(DtsHelper::decode_int_line)
                .map(decoded -> decoded.value)
                // Throw if absent
                .orElseThrow(Exception::new);
    }

    /**
     * Builds and displays a dynamic UI for editing GPU frequency levels.
     * Replaces ListView with Material RecyclerView for modern styling.
     *
     * @param activity Calling activity for context and back-press handling.
     * @param id       Index of the bin to edit.
     * @param page     Container layout where the card view will be placed.
     * @throws Exception If generating levels or bins fails.
     */
    private static void generateLevels(AppCompatActivity activity, int id, LinearLayout page) throws Exception {
        // Populate bins and related data structures before building UI
        generateData();

        // Override back button to regenerate the bins view
        ((MainActivity) activity).onBackPressedListener = new MainActivity.onBackPressedListener() {
            @Override
            public void onBackPressed() {
                try {
                    generateBins(activity, page);
                } catch (Exception e) {
                    DialogUtil.showError(activity, "Generate Bins error");
                }
            }
        };

        // Create a RecyclerView with vertical LinearLayoutManager
        RecyclerView recyclerView = new RecyclerView(activity);
        recyclerView.setLayoutManager(new LinearLayoutManager(activity));

        // Prepare items for the adapter: Back, New Item, existing levels, New Item
        ArrayList<ParamAdapter.item> items = new ArrayList<>();
        // Add the 'Back' button
        items.add(new ParamAdapter.item() {{
            title = activity.getResources().getString(R.string.back);
            subtitle = "";
        }});

        // Add the 'New Item' button
        items.add(new ParamAdapter.item() {{
            title = activity.getResources().getString(R.string.new_item);
            subtitle = activity.getResources().getString(R.string.new_desc);
        }});

        // Add frequency levels
        for (level level : bins.get(id).levels) {
            long freq = getFrequencyFromLevel(level);
            if (freq == 0) continue;  // Skip zero entries

            ParamAdapter.item item = new ParamAdapter.item();
            item.title = freq / 1000 + "MHz";
            item.subtitle = "";
            items.add(item);
        }

        // Add another 'New Item' at the end
        items.add(new ParamAdapter.item() {{
            title = activity.getResources().getString(R.string.new_item);
            subtitle = activity.getResources().getString(R.string.new_desc);
        }});

        // Instantiate MaterialLevelAdapter with click handling logic
        MaterialLevelAdapter adapter = new MaterialLevelAdapter(items, activity, position -> {
            // Prevent selection of reserved positions and handle new-level creation
            if (posIsSizeMinOne(activity, id, page, position, items)) return;
            if (posIs0(activity, page, position)) return;
            if (posIs1(activity, id, page, position)) return;
            position -= 2; // Adjust index after headers
            try {
                generateALevel(activity, id, position, page);
            } catch (Exception e) {
                DialogUtil.showError(activity, "Add a new level error");
            }
        });
        recyclerView.setAdapter(adapter);

        // Gesture detector to handle long-press deletion
        GestureDetector gestureDetector = new GestureDetector(activity, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public void onLongPress(MotionEvent e) {
                View child = recyclerView.findChildViewUnder(e.getX(), e.getY());
                if (child != null) {
                    int position = recyclerView.getChildAdapterPosition(child);
                    removeFrequency(activity, id, page, position, items);
                }
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                return true; // Ensure click is recognized
            }
        });

        // Attach touch listener to handle both taps and long-presses
        // Attach a touch listener to intercept both tap and long-press gestures on RecyclerView items
        recyclerView.addOnItemTouchListener(new RecyclerView.OnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                // Determine which child view (item) is under the touch coordinates
                View child = rv.findChildViewUnder(e.getX(), e.getY());
                // Only proceed if a child was touched and the gestureDetector recognizes the event
                if (child != null && gestureDetector.onTouchEvent(e)) {
                    // Get the adapter position of the touched item
                    int position = rv.getChildAdapterPosition(child);
                    // Only react when the user lifts their finger (ACTION_UP)
                    if (e.getAction() == MotionEvent.ACTION_UP) {
                        // Handle special positions before normal level selection:
                        //   - Last 'New Item' entry for adding levels
                        //   - Position 0 for 'Back' to previous screen
                        //   - Position 1 for cloning first level
                        if (posIsSizeMinOne(activity, id, page, position, items)) return true;
                        if (posIs0(activity, page, position)) return true;
                        if (posIs1(activity, id, page, position)) return true;
                        // Adjust index to account for header items (Back + New Item)
                        position -= 2;
                        try {
                            // Generate the UI for editing the selected level
                            generateALevel(activity, id, position, page);
                        } catch (Exception ex) {
                            // Show an error dialog if something goes wrong during level generation
                            DialogUtil.showError(activity, "Add a new level error");
                        }
                    }
                }
                // Return false to allow the touch event to continue to other listeners if not consumed
                return false;
            }

            @Override
            public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                // No action needed here; touch events are handled in onInterceptTouchEvent
            }

            @Override
            public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
                // No-op: we do not need to handle requests to disallow intercept
            }
        });

        // Wrap the RecyclerView in a dynamic MaterialCardView
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

        // Replace previous page content with the new card view
        page.removeAllViews();
        page.addView(section);
    }

    /**
     * Prompts the user with a confirmation dialog to remove a selected GPU frequency level.
     * <p>
     * This method safeguards against removing the placeholder "New Item" at the end of the list
     * and ensures at least one frequency level remains. On user confirmation, it updates the
     * underlying data model and refreshes the UI.
     * </p>
     *
     * @param activity Calling activity, used for dialog theming and context.
     * @param id       Index of the bin from which to remove a level.
     * @param page     Container layout in which the levels UI will be regenerated.
     * @param position Adapter position of the tapped item in the RecyclerView.
     * @param items    List of displayed items (including "Back" and "New Item" entries).
     */
    private static void removeFrequency(AppCompatActivity activity,
                                        int id,
                                        LinearLayout page,
                                        int position,
                                        ArrayList<ParamAdapter.item> items) {
        // If the tapped item is the last "New Item" placeholder, do nothing.
        if (position == items.size() - 1) {
            return;
        }
        // If there is only one level left, prevent its removal to avoid an empty table.
        if (bins.get(id).levels.size() == 1) {
            return;
        }
        try {
            // Build and display a confirmation dialog showing the frequency (in MHz) to remove.
            long freqMHz = getFrequencyFromLevel(bins.get(id).levels.get(position - 2)) / 1000;
            String message = String.format(
                    activity.getResources().getString(R.string.remove_msg),
                    freqMHz
            );

            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.remove)           // Dialog title (e.g., "Remove")
                    .setMessage(message)                // Dialog body with the frequency value
                    .setPositiveButton(R.string.yes,    // "Yes" button confirms removal
                            (dialog, which) -> {
                                // Remove the selected frequency level and its metadata entry
                                bins.get(id).levels.remove(position - 2);
                                bins.get(id).meta.remove(position - 2);
                                try {
                                    // Regenerate the levels UI to reflect the removal
                                    generateLevels(activity, id, page);
                                } catch (Exception e) {
                                    // Show an error if UI regeneration fails
                                    DialogUtil.showError(activity, "Remove a frequency error");
                                }
                            }
                    )
                    .setNegativeButton(R.string.no,     // "No" button cancels removal
                            null
                    )
                    .create()
                    .show();
        } catch (Exception e) {
            // If any unexpected error occurs (e.g., parsing frequency), log it to console.
            System.out.println(e.getMessage());
        }
    }

    /**
     * Handles adding a new level when the last list item is tapped.
     *
     * @param activity Calling activity for context and toast.
     * @param id       Bin index to modify.
     * @param page     Layout to refresh.
     * @param position Adapter position of the tapped item.
     * @param items    List of displayed items.
     * @return true if the tap was handled here; false otherwise.
     */
    private static boolean posIsSizeMinOne(AppCompatActivity activity, int id, LinearLayout page, int position, ArrayList<ParamAdapter.item> items) {
        // If tap was on the last 'New Item' entry
        if (position == items.size() - 1) {
            try {
                // Check if adding another level is allowed
                if (!canAddNewLevel(id, activity))
                    return true;
                // Clone the last level into the second-last position
                bins.get(id).levels.add(
                        bins.get(id).levels.size() - 1,
                        level_clone(bins.get(id).levels.get(bins.get(id).levels.size() - 1))
                );
                // Also duplicate the corresponding meta entry
                bins.get(0).meta.add(
                        bins.get(0).meta.get(bins.get(0).meta.size() - 1)
                );
                // Refresh level editing UI
                generateLevels(activity, id, page);
            } catch (Exception e) {
                DialogUtil.showError(activity, "Can't add new level");
            }
            return true;
        }
        return false;
    }

    /**
     * Updates bin[0] aggregate values based on current levels before regenerating UI.
     */
    private static void generateData() {
        // Set bin.min[0] to the lowest frequency (last level)
        bins.get(0).min.set(0, bins.get(0).levels.get(bins.get(0).levels.size() - 1));
        // Set bin.max[0] and maxLimit[0] to the highest frequency (first level)
        bins.get(0).max.set(0, bins.get(0).levels.get(0));
        bins.get(0).maxLimit.set(0, bins.get(0).levels.get(0));
        // Update dvfsSize[0] to the current number of levels
        bins.get(0).dvfsSize.set(0, inputToHex(bins.get(0).levels.size()));
    }

    /**
     * Handles cloning the first level when the second list item is tapped.
     *
     * @param activity Calling activity for context and toast.
     * @param id       Bin index to modify.
     * @param page     Layout to refresh.
     * @param position Adapter position of the tapped item.
     * @return true if the tap was handled here; false otherwise.
     */
    private static boolean posIs1(AppCompatActivity activity, int id, LinearLayout page, int position) {
        // If tap was on the second item (position 1)
        if (position == 1) {
            try {
                // Check add-level limit
                if (!canAddNewLevel(id, activity))
                    return true;
                // Clone the first level to the beginning of the list
                bins.get(id).levels.add(
                        0,
                        level_clone(bins.get(id).levels.get(0))
                );
                // Also clone the corresponding meta entry
                bins.get(0).meta.add(0, bins.get(0).meta.get(0));
                // Refresh UI
                generateLevels(activity, id, page);
            } catch (Exception e) {
                DialogUtil.showError(activity, "Clone a level error");
            }
            return true;
        }
        return false;
    }

    /**
     * Handles taps on the first list item (position 0), which serves as a "Back" button
     * to return from the level-edit screen back to the bin overview.
     *
     * @param activity Calling activity, used to invoke UI updates.
     * @param page     The layout container where the bins list will be regenerated.
     * @param position The adapter position of the tapped item.
     * @return true if this method consumed the tap (i.e., position was 0); false otherwise.
     */
    private static boolean posIs0(AppCompatActivity activity, LinearLayout page, int position) {
        // Only handle taps on the very first item (the "Back" entry)
        if (position == 0) {
            try {
                // Rebuild and display the top-level bin selection UI
                generateBins(activity, page);
            } catch (Exception e) {
                // Wrap any unexpected exception in a runtime exception to avoid silent failures
                throw new RuntimeException(e);
            }
            // Indicate that this tap was handled here and should not be processed further
            return true;
        }
        // For any other position, do nothing here and allow other handlers to process it
        return false;
    }

    /**
     * Builds and displays a detailed UI to edit individual parameters of a selected level.
     *
     * @param activity Calling activity for dialog and context.
     * @param last     Bin index containing the level.
     * @param levelID  Index of the level within the bin.
     * @param page     Container for inserting the level-edit UI.
     * @throws Exception If decoding or UI generation fails.
     */
    private static void generateALevel(AppCompatActivity activity, int last, int levelID, LinearLayout page) throws Exception {
        // Override back-button to return to generateLevels view
        ((MainActivity) activity).onBackPressedListener = new MainActivity.onBackPressedListener() {
            @Override
            public void onBackPressed() {
                try {
                    generateLevels(activity, last, page);
                } catch (Exception ignored) {
                }
            }
        };

        // Setup RecyclerView to list editable parameters
        RecyclerView recyclerView = new RecyclerView(activity);
        recyclerView.setLayoutManager(new LinearLayoutManager(activity));

        // Build list items: first a 'Back' button, then each parameter line
        ArrayList<ParamAdapter.item> items = new ArrayList<>();
        items.add(new ParamAdapter.item() {{
            title = activity.getResources().getString(R.string.back);
            subtitle = "";
        }});
        for (String line : bins.get(last).levels.get(levelID).lines) {
            // Decode each line to display name and numeric value
            ParamAdapter.item item = new ParamAdapter.item();
            item.title = KonaBessStr.convert_level_params(
                    DtsHelper.decode_hex_line(line).name, activity
            );
            item.subtitle = String.valueOf(DtsHelper.decode_int_line(line).value);
            items.add(item);
        }

        // Adapter to handle taps on parameters
        recyclerView.setAdapter(new MaterialLevelAdapter(items, activity, (position) -> {
            try {
                if (position == 0) {
                    // Handle 'Back' tap
                    generateLevels(activity, last, page);
                    return;
                }

                // Prepare edit dialog for the tapped parameter
                String raw_value = String.valueOf(
                        DtsHelper.decode_int_line(
                                bins.get(last).levels.get(levelID).lines.get(position - 1)
                        ).value
                );
                EditText editText = new EditText(activity);
                editText.setInputType(InputType.TYPE_CLASS_NUMBER);
                editText.setText(raw_value);
                editText.setPadding(32, 32, 32, 32);

                // Show Material dialog with EditText for saving new value
                new MaterialAlertDialogBuilder(activity)
                        .setTitle(activity.getResources().getString(R.string.edit)
                                + " \"" + items.get(position).title + "\"")
                        .setView(editText)
                        .setPositiveButton(R.string.save, (dialog, which) -> {
                            try {
                                // Update the line with the new hex-encoded value
                                bins.get(last).levels.get(levelID).lines.set(
                                        position - 1,
                                        DtsHelper.inputToHex(editText.getText().toString())
                                );
                                // Refresh this level's UI
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

        // Wrap RecyclerView in a MaterialCardView for styling
        MaterialCardView cardView = DialogUtil.createDynamicCard(activity, recyclerView);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.topMargin = dp(activity, 8);
        cardView.setLayoutParams(cardParams);

        // Replace any existing views and display the card
        page.removeAllViews();
        page.addView(cardView);
    }

    /**
     * Generates the list of bins for selection and displays them in a Material-styled RecyclerView.
     *
     * @param activity Calling activity for context and back-navigation handling.
     * @param page     Container layout where the bins list will be inserted.
     * @throws Exception If UI generation fails.
     */
    private static void generateBins(AppCompatActivity activity, LinearLayout page) throws Exception {
        // Override back button to return to the main activity view
        ((MainActivity) activity).onBackPressedListener = new MainActivity.onBackPressedListener() {
            @Override
            public void onBackPressed() {
                ((MainActivity) activity).showMainView();
            }
        };

        // Create RecyclerView for vertical scrolling of bin items
        RecyclerView recyclerView = new RecyclerView(activity);
        recyclerView.setLayoutManager(new LinearLayoutManager(activity));

        // Build list of ParamAdapter items representing each bin
        ArrayList<ParamAdapter.item> items = new ArrayList<>();
        for (int i = 0; i < bins.size(); i++) {
            ParamAdapter.item item = new ParamAdapter.item();
            // Convert bin ID into a localized name string
            item.title = KonaBessStr.convertBins(bins.get(i).id, activity);
            item.subtitle = ""; // Placeholder for additional info
            items.add(item);
        }

        // Set up the RecyclerView with a MaterialBinAdapter and click handler
        recyclerView.setAdapter(new MaterialBinAdapter(items, activity, (position) -> {
            try {
                // Generate the levels UI for the selected bin
                generateLevels(activity, position, page);
            } catch (Exception e) {
                e.printStackTrace();
                DialogUtil.showError(activity, R.string.error_occur);
            }
        }));

        // Wrap the RecyclerView in a styled MaterialCardView
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

        // Replace existing page content with the refreshed section
        page.removeAllViews();
        page.addView(section);
    }

    /**
     * Constructs a toolbar with a save button for the frequency table editor.
     *
     * @param activity Calling activity for context and styling.
     * @return A View containing the toolbar and horizontal button scroll.
     */
    private static View generateToolBar(AppCompatActivity activity) {
        MaterialCardView card = createEditorCard(activity);

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        content.setPadding(dp(activity, 24), dp(activity, 20), dp(activity, 24), dp(activity, 20));
        card.addView(content);

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

        return card;
    }

    /**
     * RecyclerView.Adapter implementation for displaying GPU frequency levels and bins.
     */
    public static class MaterialLevelAdapter extends RecyclerView.Adapter<MaterialLevelAdapter.ViewHolder> {
        private final ArrayList<ParamAdapter.item> items; // Data items to display
        private final Context context;                    // Activity context for theming
        private final OnItemClickListener listener;       // Click callback interface

        public MaterialLevelAdapter(ArrayList<ParamAdapter.item> items, Context context, OnItemClickListener listener) {
            this.items = items;
            this.context = context;
            this.listener = listener;
        }

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

        @Override
        public int getItemCount() {
            return items.size();
        }

        /**
         * Interface for item click callbacks.
         */
        public interface OnItemClickListener {
            void onItemClick(int position);
        }

        /**
         * ViewHolder holds references to the card view and text view for reuse.
         * <p>
         * This inner class caches the child views for a single RecyclerView item,
         * avoiding repeated findViewById calls and improving scroll performance.
         * </p>
         */
        static class ViewHolder extends RecyclerView.ViewHolder {
            final MaterialTextView titleView;
            final MaterialTextView subtitleView;

            public ViewHolder(@NonNull View itemView,
                              MaterialTextView titleView,
                              MaterialTextView subtitleView) {
                super(itemView);
                this.titleView = titleView;
                this.subtitleView = subtitleView;
            }
        }
    }

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

    private static MaterialTextView createSectionTitle(Context context, int textRes) {
        MaterialTextView title = new MaterialTextView(context);
        title.setText(textRes);
        title.setTextAppearance(R.style.TextAppearance_Material3_TitleMedium);
        title.setTextColor(DialogUtil.getDynamicColor(context, com.google.android.material.R.attr.colorOnSurface));
        return title;
    }

    private static MaterialTextView createSectionBody(Context context, int textRes) {
        return createSectionBody(context, context.getString(textRes));
    }

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

    private static int dp(Context context, int value) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    /**
     * RecyclerView.Adapter implementation for displaying GPU table bins in a Material-styled list.
     */
    public static class MaterialBinAdapter extends RecyclerView.Adapter<MaterialBinAdapter.ViewHolder> {
        // List of items to display (one per bin)
        private final ArrayList<ParamAdapter.item> items;
        // Context used for inflating views and accessing resources
        private final Context context;
        // Callback for handling item clicks
        private final OnItemClickListener listener;

        /**
         * Constructor for the adapter.
         *
         * @param items    Data items representing each bin.
         * @param context  Activity context for theming.
         * @param listener Click listener for handling selection.
         */
        public MaterialBinAdapter(ArrayList<ParamAdapter.item> items, Context context, OnItemClickListener listener) {
            this.items = items;
            this.context = context;
            this.listener = listener;
        }

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

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            // Bind the title for the bin at this position
            ParamAdapter.item item = items.get(position);
            holder.titleView.setText(item.title);
            if (item.subtitle == null || item.subtitle.isEmpty()) {
                holder.subtitleView.setVisibility(View.GONE);
            } else {
                holder.subtitleView.setVisibility(View.VISIBLE);
                holder.subtitleView.setText(item.subtitle);
            }

            // Forward click events to the provided listener
            holder.itemView.setOnClickListener(v -> listener.onItemClick(position));
        }

        @Override
        public int getItemCount() {
            // Total number of bins
            return items.size();
        }

        /**
         * Interface for handling click events on bins.
         */
        public interface OnItemClickListener {
            void onItemClick(int position);
        }

        /**
         * ViewHolder holds references to the card view and its text.
         */
        static class ViewHolder extends RecyclerView.ViewHolder {
            final MaterialTextView titleView;
            final MaterialTextView subtitleView;

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
     * Data class representing a GPU DVFS table bin with all related parameters.
     */
    private static class bin {
        int id;                   // Unique identifier for this bin
        List<String> header;      // Raw header lines (if any)
        List<level> levels;       // DVFS frequency levels
        List<level> meta;         // Metadata associated with each level
        List<level> dvfsSize;     // Table size information
        List<level> max;          // Maximum clock values
        List<level> maxLimit;     // Maximum clock limit values
        List<level> min;          // Minimum clock values
    }

    /**
     * Simple container for lines of hex or numeric data representing a table element.
     */
    private static class level {
        List<String> lines;       // Raw string lines (e.g., "0x1234 0x8")
    }

    /**
     * Thread to handle GPU table extraction, parsing, and UI generation in background.
     */
    static class gpuTableLogic extends Thread {
        private final AppCompatActivity activity; // Activity context for UI updates
        private AlertDialog waiting;             // Wait dialog displayed during processing
        private final LinearLayout showedView;   // Container to display toolbar and bins
        private LinearLayout page;               // Sub-container for bin/level views

        /**
         * Constructor for the processing thread.
         *
         * @param activity   Activity context for UI.
         * @param showedView Parent layout where UI will be injected.
         */
        public gpuTableLogic(AppCompatActivity activity, LinearLayout showedView) {
            this.activity = activity;
            this.showedView = showedView;
        }

        @Override
        public void run() {
            // Show a wait dialog on the UI thread before starting work
            activity.runOnUiThread(() -> {
                waiting = DialogUtil.getWaitDialog(activity, R.string.getting_freq_table);
                waiting.show();
            });

            try {
                // Perform initialization and decoding steps
                init();
                decode();
            } catch (Exception e) {
                // Show error dialog if extraction or parsing fails
                activity.runOnUiThread(() -> DialogUtil.showError(
                        activity,
                        R.string.getting_freq_table_failed + " " + e
                ));
            }

            // Once done, update the UI: dismiss dialog, show toolbar and bins
            activity.runOnUiThread(() -> {
                waiting.dismiss();              // Hide the wait dialog
                showedView.removeAllViews();   // Clear existing content

                // Add the save toolbar at the top
                showedView.addView(generateToolBar(activity));

                // Prepare page container for bins
                page = new LinearLayout(activity);
                page.setOrientation(LinearLayout.VERTICAL);

                try {
                    // Generate and display the list of bins
                    generateBins(activity, page);
                } catch (Exception e) {
                    DialogUtil.showError(activity, "Failed to generate bins");
                }

                // Add the bins page below the toolbar
                showedView.addView(page);
            });
        }
    }
}