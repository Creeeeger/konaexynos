package xzr.konabess;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textview.MaterialTextView;

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
        onBackPressedListener = null;

        ScrollView rootScroll = new ScrollView(this);
        rootScroll.setFillViewport(true);
        rootScroll.setBackgroundResource(R.drawable.bg_main_gradient);

        LinearLayout mainView = new LinearLayout(this);
        mainView.setOrientation(LinearLayout.VERTICAL);
        mainView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        mainView.setPadding(dp(16), dp(32), dp(16), dp(24));
        rootScroll.addView(mainView);
        setContentView(rootScroll);

        MaterialCardView toolbarCard = new MaterialCardView(this, null, R.style.Widget_Material3_CardView_Filled);
        LinearLayout.LayoutParams toolbarCardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        toolbarCardParams.bottomMargin = dp(28);
        toolbarCard.setLayoutParams(toolbarCardParams);
        toolbarCard.setRadius(dp(32));
        toolbarCard.setCardElevation(0f);
        toolbarCard.setUseCompatPadding(false);
        toolbarCard.setPreventCornerOverlap(false);
        toolbarCard.setCardBackgroundColor(getDynamicColor(this, com.google.android.material.R.attr.colorSurfaceVariant));
        toolbarCard.setStrokeWidth(0);

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle(getString(R.string.app_name));
        toolbar.setSubtitle(R.string.toolbar_subtitle);
        toolbar.setBackgroundColor(Color.TRANSPARENT);
        int onSurfaceColor = getDynamicColor(this, com.google.android.material.R.attr.colorOnSurface);
        int onSurfaceVariant = getDynamicColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant);
        toolbar.setTitleTextColor(onSurfaceColor);
        toolbar.setSubtitleTextColor(onSurfaceVariant);
        toolbar.setContentInsetStartWithNavigation(0);
        toolbar.setElevation(0f);
        toolbar.setPadding(dp(20), dp(18), dp(20), dp(18));

        toolbarCard.addView(toolbar);
        mainView.addView(toolbarCard);

        MaterialCardView heroCard = createSurfaceCard();
        LinearLayout heroContent = createCardContentLayout();
        heroContent.addView(createHeadlineTextView(R.string.chipset_card_title));

        String chipName = ChipInfo.name2ChipDesc(ChipInfo.which, this);
        boolean isUnknownChip = ChipInfo.which == ChipInfo.type.unknown;
        MaterialTextView heroBody = createBodyTextView(
                isUnknownChip
                        ? getString(R.string.chipset_unknown_hint)
                        : getString(R.string.chipset_card_body, chipName)
        );
        heroContent.addView(heroBody);

        Chip chipBadge = new Chip(this);
        chipBadge.setText(isUnknownChip ? getString(R.string.unknown) : chipName);
        chipBadge.setTextAppearance(R.style.TextAppearance_Material3_LabelLarge);
        chipBadge.setChipBackgroundColor(ColorStateList.valueOf(
                getDynamicColor(this, com.google.android.material.R.attr.colorPrimaryContainer)
        ));
        chipBadge.setTextColor(getDynamicColor(this, com.google.android.material.R.attr.colorOnPrimaryContainer));
        chipBadge.setChipStrokeWidth(dp(1));
        chipBadge.setChipStrokeColor(ColorStateList.valueOf(
                getDynamicColor(this, com.google.android.material.R.attr.colorOnPrimary)
        ));
        LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        chipParams.topMargin = dp(16);
        chipBadge.setLayoutParams(chipParams);
        heroContent.addView(chipBadge);
        heroCard.addView(heroContent);
        mainView.addView(heroCard);

        MaterialCardView actionsCard = createSurfaceCard();
        LinearLayout actionsContent = createCardContentLayout();
        actionsContent.addView(createHeadlineTextView(R.string.actions_card_title));
        actionsContent.addView(createBodyTextView(getString(R.string.actions_card_body)));

        LinearLayout buttonColumn = new LinearLayout(this);
        buttonColumn.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams buttonColumnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        buttonColumnParams.topMargin = dp(12);
        buttonColumn.setLayoutParams(buttonColumnParams);
        actionsContent.addView(buttonColumn);
        actionsCard.addView(actionsContent);
        mainView.addView(actionsCard);

        MaterialCardView workspaceCard = createSurfaceCard();
        LinearLayout workspaceContent = createCardContentLayout();
        workspaceContent.addView(createHeadlineTextView(R.string.workspace_title));

        LinearLayout showdView = new LinearLayout(this);
        showdView.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams showdViewParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        showdViewParams.topMargin = dp(12);
        showdView.setLayoutParams(showdViewParams);
        workspaceContent.addView(showdView);
        workspaceCard.addView(workspaceContent);
        mainView.addView(workspaceCard);

        addActionButton(buttonColumn, R.string.repack_and_flash, v -> new repackLogic().start());
        addActionButton(buttonColumn, R.string.edit_gpu_freq_table, v ->
                new GpuTableEditor.gpuTableLogic(this, showdView).start()
        );
    }

    /**
     * Adds a Material 3 styled button to the primary action column.
     */
    private void addActionButton(LinearLayout container, int textId, View.OnClickListener onClickListener) {
        MaterialButton button = new MaterialButton(this);
        button.setText(textId);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = dp(12);
        button.setLayoutParams(params);
        button.setAllCaps(false);
        button.setCornerRadius(dp(18));
        int primaryContainer = getDynamicColor(this, com.google.android.material.R.attr.colorPrimaryContainer);
        int onPrimaryContainer = getDynamicColor(this, com.google.android.material.R.attr.colorOnPrimaryContainer);
        int primary = getDynamicColor(this, com.google.android.material.R.attr.colorOnPrimary);
        button.setBackgroundTintList(ColorStateList.valueOf(primaryContainer));
        button.setTextColor(onPrimaryContainer);
        button.setRippleColor(ColorStateList.valueOf(primary));
        button.setStrokeWidth(0);
        button.setOnClickListener(onClickListener);
        container.addView(button);
    }

    private LinearLayout createCardContentLayout() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        return content;
    }

    private MaterialCardView createSurfaceCard() {
        MaterialCardView card = new MaterialCardView(this, null, R.style.Widget_Material3_CardView_Filled);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = dp(20);
        card.setLayoutParams(params);
        card.setCardBackgroundColor(getDynamicColor(this, com.google.android.material.R.attr.colorSurface));
        card.setStrokeColor(getDynamicColor(this, com.google.android.material.R.attr.colorOutline));
        card.setStrokeWidth(dp(1));
        card.setRadius(dp(24));
        card.setCardElevation(0f);
        card.setUseCompatPadding(false);
        card.setContentPadding(dp(24), dp(28), dp(24), dp(24));
        return card;
    }

    private MaterialTextView createHeadlineTextView(int textRes) {
        MaterialTextView textView = new MaterialTextView(this);
        textView.setText(textRes);
        textView.setTextAppearance(R.style.TextAppearance_Material3_TitleLarge);
        textView.setTextColor(getDynamicColor(this, com.google.android.material.R.attr.colorOnSurface));
        return textView;
    }

    private MaterialTextView createBodyTextView(String text) {
        MaterialTextView textView = new MaterialTextView(this);
        textView.setText(text);
        textView.setTextAppearance(R.style.TextAppearance_Material3_BodyMedium);
        textView.setTextColor(getDynamicColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = dp(8);
        textView.setLayoutParams(params);
        return textView;
    }

    private MaterialTextView createBodyTextView(int textRes) {
        return createBodyTextView(getString(textRes));
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
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