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
    onBackPressedListener onBackPressedListener = null;

    /**
     * Fetch Material You Dynamic Color
     */
    private static int getDynamicColor(Context context, int attr) {
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(attr, typedValue, true);
        return typedValue.data;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Apply dynamic colors if supported (Android 12+)
        DynamicColors.applyToActivitiesIfAvailable(getApplication());

        ChipInfo.which = ChipInfo.type.unknown;

        try {
            setTitle(getTitle() + " " + getPackageManager().getPackageInfo(getPackageName(), 0).versionName);
        } catch (PackageManager.NameNotFoundException ignored) {
        }

        try {
            KonaBessCore.cleanEnv(this);
            KonaBessCore.setupEnv(this);
        } catch (Exception e) {
            DialogUtil.showError(this, R.string.environ_setup_failed);
            return;
        }

        new unpackLogic().start();
    }

    @Override
    public void onBackPressed() {
        if (onBackPressedListener != null)
            onBackPressedListener.onBackPressed();
        else
            super.onBackPressed();
    }

    void showMainView() {
        onBackPressedListener = null;

        // --- Root Layout with Improved Colors ---
        LinearLayout mainView = new LinearLayout(this);
        mainView.setOrientation(LinearLayout.VERTICAL);
        mainView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        ));
        mainView.setBackgroundColor(getDynamicColor(this, com.google.android.material.R.attr.colorSurfaceVariant)); // Subtle background
        mainView.setPadding(32, 32, 32, 32); // Spacious padding
        setContentView(mainView);

        // --- Toolbar Section ---
        MaterialCardView toolbarCard = new MaterialCardView(this, null, R.style.Widget_Material3_CardView_Elevated);
        toolbarCard.setRadius(24); // Softer rounded corners
        toolbarCard.setCardElevation(12); // Smooth shadow
        toolbarCard.setStrokeWidth(2);
        toolbarCard.setStrokeColor(getDynamicColor(this, com.google.android.material.R.attr.colorPrimary)); // Accent border
        toolbarCard.setCardBackgroundColor(getDynamicColor(this, com.google.android.material.R.attr.colorSurface)); // Background
        toolbarCard.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        toolbarCard.setPadding(16, 16, 16, 16); // Spacious padding

        // Toolbar layout inside card
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL); // Center vertically
        toolbar.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        toolbar.setPadding(8, 8, 8, 8); // Extra padding
        toolbar.setGravity(Gravity.CENTER_HORIZONTAL); // Align horizontally

        toolbarCard.addView(toolbar);
        mainView.addView(toolbarCard);

        // --- Scrollable Editor Section ---
        HorizontalScrollView editorScroll = new HorizontalScrollView(this);
        LinearLayout editor = new LinearLayout(this);
        editor.setOrientation(LinearLayout.HORIZONTAL);
        editor.setPadding(16, 16, 16, 16); // Padding for readability
        editorScroll.addView(editor);
        editorScroll.setBackgroundColor(getDynamicColor(this, com.google.android.material.R.attr.colorSurfaceVariant)); // Light container color
        editorScroll.setPadding(12, 12, 12, 12);
        editorScroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        editorScroll.setElevation(6); // Adds subtle depth
        mainView.addView(editorScroll);

        // --- Content Display Section ---
        LinearLayout showdView = new LinearLayout(this);
        showdView.setOrientation(LinearLayout.VERTICAL);
        showdView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        showdView.setPadding(24, 24, 24, 24);
        showdView.setBackgroundColor(getDynamicColor(this, com.google.android.material.R.attr.colorPrimaryContainer)); // Accent background
        showdView.setElevation(8); // Slight shadow effect
        mainView.addView(showdView);

        // --- Add Toolbar Buttons ---
        addToolbarButton(toolbar, R.string.repack_and_flash, v -> new repackLogic().start());
        addToolbarButton(toolbar, R.string.edit_gpu_freq_table, v ->
                new GpuTableEditor.gpuTableLogic(this, showdView).start()
        );
    }

    /**
     * Adds a Material 3 styled button to the toolbar
     */
    private void addToolbarButton(LinearLayout toolbar, int textId, View.OnClickListener onClickListener) {
        MaterialButton button = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(textId);
        button.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        button.setPadding(32, 16, 32, 16); // Larger padding for accessibility
        button.setCornerRadius(24); // Rounded corners for modern look
        button.setStrokeWidth(2); // Subtle border
        button.setStrokeColor(ColorStateList.valueOf(getDynamicColor(this, com.google.android.material.R.attr.colorSecondary))); // Accent border
        button.setTextColor(getDynamicColor(this, com.google.android.material.R.attr.colorOnSecondary)); // Text color
        button.setOnClickListener(onClickListener);
        toolbar.addView(button); // Add button to toolbar
    }

    public static abstract class onBackPressedListener {
        public abstract void onBackPressed();
    }

    class repackLogic extends Thread {
        private String errorMessage = "";
        private AlertDialog waitingDialog;

        @Override
        public void run() {
            // Step 1: Repacking Process
            showWaitDialog(R.string.repacking);
            if (!performRepack()) {
                dismissWaitDialog();
                showDetailedError(errorMessage);
                return;
            }
            dismissWaitDialog();

            // Step 2: Flashing Process
            showWaitDialog(R.string.flashing_boot);
            if (!performFlashing()) {
                dismissWaitDialog();
                showErrorDialog(R.string.flashing_failed);
                return;
            }
            dismissWaitDialog();

            // Step 3: Reboot Prompt
            showRebootDialog();
        }

        /**
         * Shows a styled wait dialog with the given message
         */
        private void showWaitDialog(int messageId) {
            runOnUiThread(() -> {
                waitingDialog = DialogUtil.getWaitDialog(MainActivity.this, messageId);
                waitingDialog.show();
            });
        }

        /**
         * Dismisses the currently active wait dialog
         */
        private void dismissWaitDialog() {
            runOnUiThread(() -> {
                if (waitingDialog != null && waitingDialog.isShowing()) {
                    waitingDialog.dismiss();
                }
            });
        }

        /**
         * Handles the repacking process and captures errors
         */
        private boolean performRepack() {
            try {
                KonaBessCore.dts2bootImage(MainActivity.this);
                return true; // Success
            } catch (Exception e) {
                errorMessage = e.getMessage();
                return false; // Failure
            }
        }

        /**
         * Handles the flashing process and captures errors
         */
        private boolean performFlashing() {
            try {
                KonaBessCore.writeDtbImage(MainActivity.this);
                return true; // Success
            } catch (Exception e) {
                return false; // Failure
            }
        }

        /**
         * Displays a detailed error dialog
         */
        private void showDetailedError(String details) {
            runOnUiThread(() -> DialogUtil.showDetailedError(MainActivity.this, 2131689664, details));
        }

        /**
         * Displays a simple error dialog
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
                            KonaBessCore.reboot();
                        } catch (IOException e) {
                            showErrorDialog(R.string.failed_reboot);
                        }
                    })
                    .setNegativeButton(R.string.no, null)
                    .create()
                    .show());
        }
    }

    class unpackLogic extends Thread {
        private String errorMessage = "";
        private int dtbIndex;
        private AlertDialog waitingDialog;

        @Override
        public void run() {
            // Step 1: Get Boot Image
            if (!performStep(() -> {
                try {
                    KonaBessCore.getDtImage(MainActivity.this);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            })) {
                showErrorDialog(R.string.failed_get_boot);
                return;
            }

            // Step 2: Unpack Boot Image
            if (!performStepWithErrorDetails(R.string.unpacking, () -> {
                try {
                    KonaBessCore.dtbImage2dts(MainActivity.this);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            })) {
                showDetailedErrorDialog(R.string.unpack_failed, errorMessage);
                return;
            }

            // Step 3: Check Device Compatibility
            if (!performStepWithErrorDetails(R.string.checking_device, () -> {
                try {
                    KonaBessCore.checkDevice(MainActivity.this);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                try {
                    dtbIndex = KonaBessCore.getDtbIndex();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            })) {
                showDetailedErrorDialog(R.string.failed_checking_platform, errorMessage);
                return;
            }

            // Step 4: Handle DTB Selection
            handleDtbSelection();
        }

        /**
         * Performs a processing step with a wait dialog.
         */
        private boolean performStep(Runnable task) {
            showWaitDialog(R.string.wait);
            try {
                task.run();
                return true; // Success
            } catch (Exception e) {
                return false; // Failure
            } finally {
                dismissWaitDialog();
            }
        }

        /**
         * Performs a processing step with a wait dialog and captures error messages.
         */
        private boolean performStepWithErrorDetails(int messageId, Runnable task) {
            showWaitDialog(messageId);
            try {
                task.run();
                return true; // Success
            } catch (Exception e) {
                errorMessage = e.getMessage();
                return false; // Failure
            } finally {
                dismissWaitDialog();
            }
        }

        /**
         * Displays a DTB selection dialog.
         */
        private void handleDtbSelection() {
            runOnUiThread(() -> {
                if (KonaBessCore.dtbs.isEmpty()) {
                    showErrorDialog(R.string.incompatible_device);
                    return;
                }

                // Auto-select if only one DTB is available
                if (KonaBessCore.dtbs.size() == 1) {
                    KonaBessCore.chooseTarget(KonaBessCore.dtbs.get(0), MainActivity.this);
                    showMainView();
                    return;
                }

                // Create a selection list
                ListView listView = new ListView(MainActivity.this);
                ArrayList<ParamAdapter.item> items = new ArrayList<>();
                for (KonaBessCore.dtb dtb : KonaBessCore.dtbs) {
                    items.add(new ParamAdapter.item() {{
                        title = dtb.id + " " + ChipInfo.name2ChipDesc(dtb.type, MainActivity.this);
                        subtitle = dtb.id == dtbIndex
                                ? MainActivity.this.getString(R.string.possible_dtb) : "";
                    }});
                }

                listView.setAdapter(new ParamAdapter(items, MainActivity.this));

                // Display the selection dialog
                AlertDialog dialog = new MaterialAlertDialogBuilder(MainActivity.this)
                        .setTitle(R.string.select_dtb_title)
                        .setMessage(R.string.select_dtb_msg)
                        .setView(listView)
                        .setCancelable(false)
                        .create();
                dialog.show();

                // Handle selection
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
                waitingDialog = DialogUtil.getWaitDialog(MainActivity.this, messageId);
                waitingDialog.show();
            });
        }

        /**
         * Dismisses the active wait dialog.
         */
        private void dismissWaitDialog() {
            runOnUiThread(() -> {
                if (waitingDialog != null && waitingDialog.isShowing()) {
                    waitingDialog.dismiss();
                }
            });
        }

        /**
         * Displays a simple error dialog.
         */
        private void showErrorDialog(int messageId) {
            runOnUiThread(() -> DialogUtil.showError(MainActivity.this, messageId));
        }

        /**
         * Displays a detailed error dialog.
         */
        private void showDetailedErrorDialog(int titleId, String details) {
            runOnUiThread(() -> DialogUtil.showDetailedError(MainActivity.this, titleId, details));
        }
    }
}