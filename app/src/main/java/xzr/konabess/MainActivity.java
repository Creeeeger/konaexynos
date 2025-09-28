package xzr.konabess;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.IOException;
import java.util.ArrayList;

import xzr.konabess.adapters.ParamAdapter;
import xzr.konabess.utils.DialogUtil;

public class MainActivity extends AppCompatActivity {
    // Listener for handling back press events; can be set by child components
    onBackPressedListener onBackPressedListener = null;

    /**
     * Retrieve a Material You dynamic color attribute from the current theme.
     *
     * @param context The context used to access the theme
     * @param attr    The attribute resource ID (e.g., a Material color attribute)
     * @return The resolved color integer value
     */
    private static int getDynamicColor(Context context, int attr) {
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(attr, typedValue, true);
        return typedValue.data;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Apply Material You dynamic color theming to all activities if supported (Android 12+)
        DynamicColors.applyToActivitiesIfAvailable(getApplication());

        // Initialize ChipInfo state to unknown before any logic runs
        ChipInfo.which = ChipInfo.type.unknown;

        // Append the app version name to the Activity's title for easy reference
        try {
            setTitle(getTitle() + " " + getPackageManager().getPackageInfo(getPackageName(), 0).versionName);
        } catch (PackageManager.NameNotFoundException ignored) {
            // If version info is unavailable, ignore silently
        }

        // Prepare the environment for KonaBessCore; show error dialog on failure
        try {
            KonaBessCore.cleanEnv(this);
            KonaBessCore.setupEnv(this);
        } catch (Exception e) {
            DialogUtil.showError(this, R.string.environ_setup_failed);
            return; // Abort initialization if environment setup fails
        }

        // Kick off unpacking logic on a background thread
        new unpackLogic().start();
    }

    @Override
    public void onBackPressed() {
        // If a custom back press listener is set, delegate to it; otherwise, perform default
        if (onBackPressedListener != null) {
            onBackPressedListener.onBackPressed();
        } else {
            super.onBackPressed();
        }
    }

    /**
     * Build and display the main user interface programmatically.
     */
    void showMainView() {
        // Reset any custom back press listener when showing main view
        onBackPressedListener = null;

        // --- Root Layout Configuration ---
        LinearLayout mainView = new LinearLayout(this);
        mainView.setOrientation(LinearLayout.VERTICAL);
        mainView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        ));
        // Use a subtle surface variant background from the dynamic theme
        mainView.setBackgroundColor(getDynamicColor(this, com.google.android.material.R.attr.colorSurfaceVariant));
        mainView.setPadding(32, 32, 32, 32); // Add spacious padding
        setContentView(mainView);

        // --- Toolbar Section Setup ---
        MaterialCardView toolbarCard = new MaterialCardView(this, null, R.style.Widget_Material3_CardView_Elevated);
        toolbarCard.setRadius(24); // Softer rounded corners for a modern look
        toolbarCard.setCardElevation(12); // Smooth shadow for depth
        toolbarCard.setStrokeWidth(2);
        // Accent border using primary color from dynamic theme
        toolbarCard.setStrokeColor(getDynamicColor(this, R.attr.colorPrimary));
        // Card background using surface color
        toolbarCard.setCardBackgroundColor(getDynamicColor(this, com.google.android.material.R.attr.colorSurface));
        toolbarCard.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        toolbarCard.setPadding(16, 16, 16, 16);

        // Create horizontal layout inside the card for toolbar items
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL); // Center items both vertically and horizontally
        toolbar.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        toolbar.setPadding(8, 8, 8, 8);

        // Add the toolbar layout to the card, and the card to the root view
        toolbarCard.addView(toolbar);
        mainView.addView(toolbarCard);

        // --- Scrollable Editor Section ---
        HorizontalScrollView editorScroll = new HorizontalScrollView(this);
        LinearLayout editor = new LinearLayout(this);
        editor.setOrientation(LinearLayout.HORIZONTAL);
        editor.setPadding(16, 16, 16, 16); // Padding for readability of code/editor content
        editorScroll.addView(editor);
        // Light container background for the editor area
        editorScroll.setBackgroundColor(getDynamicColor(this, com.google.android.material.R.attr.colorSurfaceVariant));
        editorScroll.setPadding(12, 12, 12, 12);
        editorScroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        editorScroll.setElevation(6); // Subtle depth effect
        mainView.addView(editorScroll);

        // --- Content Display Section ---
        LinearLayout showdView = new LinearLayout(this);
        showdView.setOrientation(LinearLayout.VERTICAL);
        showdView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        showdView.setPadding(24, 24, 24, 24);
        // Accent container using primary container color from dynamic theme
        showdView.setBackgroundColor(getDynamicColor(this, com.google.android.material.R.attr.colorPrimaryContainer));
        showdView.setElevation(8); // Slight shadow for emphasis
        mainView.addView(showdView);

        // --- Add Buttons to Toolbar with Associated Actions ---
        // Button for repacking and flashing logic
        addToolbarButton(toolbar, R.string.repack_and_flash, v -> new repackLogic().start());
        // Button for launching GPU frequency table editor
        addToolbarButton(toolbar, R.string.edit_gpu_freq_table, v ->
                new GpuTableEditor.gpuTableLogic(this, showdView).start()
        );
    }

    /**
     * Adds a Material 3 styled button to the toolbar
     */
    private void addToolbarButton(LinearLayout toolbar, int textId, View.OnClickListener onClickListener) {
        // Create a new MaterialButton with outlined style
        MaterialButton button = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        // Set the button text from string resources
        button.setText(textId);
        // Define width and height layout parameters (wrap content)
        button.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        // Apply larger padding for better touch targets and accessibility
        button.setPadding(32, 16, 32, 16); // Larger padding for accessibility
        // Set corner radius for a modern, rounded look
        button.setCornerRadius(24); // Rounded corners for modern look
        // Set subtle border width
        button.setStrokeWidth(2); // Subtle border
        // Apply accent border color dynamically based on theme's secondary color
        button.setStrokeColor(ColorStateList.valueOf(getDynamicColor(this, com.google.android.material.R.attr.colorSecondary))); // Accent border
        // Apply text color dynamically based on theme's onSecondary color
        button.setTextColor(getDynamicColor(this, com.google.android.material.R.attr.colorOnSecondary)); // Text color
        // Set the provided click listener to handle actions
        button.setOnClickListener(onClickListener);
        // Add the fully styled button into the provided toolbar layout
        toolbar.addView(button); // Add button to toolbar
    }

    // Abstract listener to handle custom back press behavior
    public static abstract class onBackPressedListener {
        public abstract void onBackPressed();
    }

    // Thread class handling repacking and flashing logic
    class repackLogic extends Thread {
        private String errorMessage = "";          // To store any error messages encountered
        private AlertDialog waitingDialog;          // Dialog shown during long operations

        @Override
        public void run() {
            // Step 1: Repacking Process
            showWaitDialog(R.string.repacking);
            if (!performRepack()) {
                // If repack fails, hide dialog and show detailed error
                dismissWaitDialog();
                showDetailedError(errorMessage);
                return;
            }
            // Repacking succeeded, dismiss wait dialog
            dismissWaitDialog();

            // Step 2: Flashing Process
            showWaitDialog(R.string.flashing_boot);
            if (!performFlashing()) {
                // If flashing fails, hide dialog and show simple error
                dismissWaitDialog();
                showErrorDialog(R.string.flashing_failed);
                return;
            }
            // Flashing succeeded, dismiss wait dialog
            dismissWaitDialog();

            // Step 3: Prompt user to reboot the device
            showRebootDialog();
        }

        /**
         * Shows a styled wait dialog with the given message resource
         */
        private void showWaitDialog(int messageId) {
            runOnUiThread(() -> {
                waitingDialog = DialogUtil.getWaitDialog(MainActivity.this, messageId);
                waitingDialog.show();
            });
        }

        /**
         * Dismisses the currently active wait dialog if it's showing
         */
        private void dismissWaitDialog() {
            runOnUiThread(() -> {
                if (waitingDialog != null && waitingDialog.isShowing()) {
                    waitingDialog.dismiss();
                }
            });
        }

        /**
         * Handles the repacking process and captures any exception message
         */
        private boolean performRepack() {
            try {
                KonaBessCore.dts2bootImage(MainActivity.this);  // Convert DTS to boot image
                return true; // Success
            } catch (Exception e) {
                errorMessage = e.getMessage();  // Store error for display
                return false; // Failure
            }
        }

        /**
         * Handles the flashing process and returns success or failure
         */
        private boolean performFlashing() {
            try {
                KonaBessCore.writeDtbImage(MainActivity.this); // Write DTB image to device
                return true; // Success
            } catch (Exception e) {
                return false; // Failure, no detailed message stored
            }
        }

        /**
         * Displays a detailed error dialog with provided details
         */
        private void showDetailedError(String details) {
            runOnUiThread(() -> DialogUtil.showDetailedError(MainActivity.this, 2131689664, details));
        }

        /**
         * Displays a simple error dialog using a string resource
         */
        private void showErrorDialog(int messageRes) {
            runOnUiThread(() -> DialogUtil.showError(MainActivity.this, messageRes));
        }

        /**
         * Shows a confirmation dialog for rebooting the device
         */
        private void showRebootDialog() {
            runOnUiThread(() -> new MaterialAlertDialogBuilder(MainActivity.this)
                    .setTitle(R.string.reboot_complete_title)
                    .setMessage(R.string.reboot_complete_msg)
                    .setPositiveButton(R.string.yes, (dialog, which) -> {
                        try {
                            KonaBessCore.reboot();  // Attempt device reboot
                        } catch (IOException e) {
                            showErrorDialog(R.string.failed_reboot);  // Show error if reboot fails
                        }
                    })
                    .setNegativeButton(R.string.no, null) // Do nothing on "No"
                    .create()
                    .show());
        }
    }

    // Thread class handling unpacking logic
    class unpackLogic extends Thread {
        private String errorMessage = "";  // To capture error details during steps
        private int dtbIndex;               // To store selected DTB index after compatibility check
        private AlertDialog waitingDialog;  // Dialog shown during long operations

        @Override
        public void run() {
            // Step 1: Retrieve boot image; exit on failure
            if (!performStep(() -> {
                try {
                    KonaBessCore.getDtImage(MainActivity.this); // Get DT image from boot
                } catch (IOException e) {
                    throw new RuntimeException(e); // Wrap and propagate
                }
            })) {
                showErrorDialog(R.string.failed_get_boot);
                return;
            }

            // Step 2: Unpack boot image to DTS; show detailed error if fails
            if (!performStepWithErrorDetails(R.string.unpacking, () -> {
                try {
                    KonaBessCore.dtbImage2dts(MainActivity.this); // Convert DTB image to DTS files
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            })) {
                showDetailedErrorDialog(R.string.unpack_failed, errorMessage);
                return;
            }

            // Step 3: Check device compatibility and retrieve DTB index
            if (!performStepWithErrorDetails(R.string.checking_device, () -> {
                try {
                    KonaBessCore.checkDevice(MainActivity.this); // Verify device compatibility
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                try {
                    dtbIndex = KonaBessCore.getDtbIndex(); // Get default or suggested DTB index
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            })) {
                showDetailedErrorDialog(R.string.failed_checking_platform, errorMessage);
                return;
            }

            // Step 4: Handle user selection of DTB variant
            handleDtbSelection();
        }

        /**
         * Performs a processing step with a wait dialog; returns success state
         */
        private boolean performStep(Runnable task) {
            showWaitDialog(R.string.wait); // Show generic wait message
            try {
                task.run();                // Execute provided task
                return true;               // Return success if no exception
            } catch (Exception e) {
                return false;              // Return failure on exception
            } finally {
                dismissWaitDialog();       // Always dismiss dialog afterwards
            }
        }

        /**
         * Performs a processing step with a wait dialog and captures error messages.
         */
        private boolean performStepWithErrorDetails(int messageId, Runnable task) {
            // Show a blocking wait dialog on the UI with the given message resource ID
            showWaitDialog(messageId);
            try {
                // Execute the provided task on the current thread
                task.run();
                // If no exception occurred, return true to indicate success
                return true;
            } catch (Exception e) {
                // Capture the exception message for later use (e.g., in an error dialog)
                errorMessage = e.getMessage();
                // Return false to indicate that the task failed
                return false;
            } finally {
                // Always dismiss the wait dialog regardless of success or failure
                dismissWaitDialog();
            }
        }

        /**
         * Displays a DTB selection dialog.
         */
        private void handleDtbSelection() {
            runOnUiThread(() -> {
                // If there are no DTBs available, show an incompatibility error and exit
                if (KonaBessCore.dtbs.isEmpty()) {
                    showErrorDialog(R.string.incompatible_device);
                    return;
                }

                // If exactly one DTB is available, auto-select it and proceed to the main view
                if (KonaBessCore.dtbs.size() == 1) {
                    KonaBessCore.chooseTarget(KonaBessCore.dtbs.get(0), MainActivity.this);
                    showMainView();
                    return;
                }

                // Create a ListView to display multiple DTB options
                ListView listView = new ListView(MainActivity.this);
                ArrayList<ParamAdapter.item> items = new ArrayList<>();

                // Populate the list with each DTB's ID and type description
                for (KonaBessCore.dtb dtb : KonaBessCore.dtbs) {
                    items.add(new ParamAdapter.item() {{
                        title = dtb.id + " " + ChipInfo.name2ChipDesc(dtb.type, MainActivity.this);
                        // Mark the current DTB index with a subtitle hint
                        subtitle = dtb.id == dtbIndex ? MainActivity.this.getString(R.string.possible_dtb) : "";
                    }});
                }

                // Attach the custom adapter to the ListView
                listView.setAdapter(new ParamAdapter(items, MainActivity.this));

                // Build and show a non-cancelable dialog with the DTB list
                AlertDialog dialog = new MaterialAlertDialogBuilder(MainActivity.this)
                        .setTitle(R.string.select_dtb_title)
                        .setMessage(R.string.select_dtb_msg)
                        .setView(listView)
                        .setCancelable(false)
                        .create();
                dialog.show();

                // Handle user selection: choose the selected DTB, dismiss dialog, and show main view
                listView.setOnItemClickListener((parent, view, position, id) -> {
                    KonaBessCore.chooseTarget(KonaBessCore.dtbs.get(position), MainActivity.this);
                    dialog.dismiss();
                    showMainView();
                });
            });
        }

        /**
         * Shows a styled wait dialog.
         */
        private void showWaitDialog(int messageId) {
            runOnUiThread(() -> {
                // Create and display a waiting dialog with a spinner and the given message
                waitingDialog = DialogUtil.getWaitDialog(MainActivity.this, messageId);
                waitingDialog.show();
            });
        }

        /**
         * Dismisses the active wait dialog.
         */
        private void dismissWaitDialog() {
            runOnUiThread(() -> {
                // Safely dismiss the dialog if it exists and is currently visible
                if (waitingDialog != null && waitingDialog.isShowing()) {
                    waitingDialog.dismiss();
                }
            });
        }

        /**
         * Displays a simple error dialog.
         */
        private void showErrorDialog(int messageId) {
            runOnUiThread(() ->
                    // Use a utility method to show a standard error alert with the given message
                    DialogUtil.showError(MainActivity.this, messageId)
            );
        }

        /**
         * Displays a detailed error dialog.
         */
        private void showDetailedErrorDialog(int titleId, String details) {
            runOnUiThread(() ->
                    // Show an error dialog including both a title and detailed message text
                    DialogUtil.showDetailedError(MainActivity.this, titleId, details)
            );
        }
    }
}