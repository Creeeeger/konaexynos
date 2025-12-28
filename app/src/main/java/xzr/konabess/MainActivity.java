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

/**
 * Hosts environment preparation, image workflows, and the programmatically built editor interface.
 */
public class MainActivity extends AppCompatActivity {
    /** Optional navigation handler installed by the current editor screen. */
    onBackPressedListener onBackPressedListener = null;

    /**
     * Resolves a color attribute from the active theme.
     *
     * @param context themed context
     * @param attr color attribute resource
     * @return resolved packed color
     */
    private static int getDynamicColor(Context context, int attr) {
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(attr, typedValue, true);
        return typedValue.data;
    }

    /**
     * Applies dynamic color, prepares bundled tools, and starts initial image extraction.
     *
     * @param savedInstanceState previously saved activity state
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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

    /**
     * Delegates back navigation to the active editor screen when one has registered a handler.
     */
    @Override
    public void onBackPressed() {
        if (onBackPressedListener != null) {
            onBackPressedListener.onBackPressed();
        } else {
            super.onBackPressed();
        }
    }

    /**
     * Replaces the activity content with the detected-chip summary, workflow actions, and editor
     * workspace.
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
     * Adds a full-width workflow button to an action container.
     *
     * @param container layout receiving the button
     * @param textId button-label resource
     * @param onClickListener workflow started by the button
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

    /**
     * Creates the vertical content layout used inside main-screen cards.
     *
     * @return match-width, wrap-content layout
     */
    private LinearLayout createCardContentLayout() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        return content;
    }

    /**
     * Creates a main-screen card using current surface and outline colors.
     *
     * @return configured empty card
     */
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

    /**
     * Creates a card heading.
     *
     * @param textRes heading resource
     * @return themed heading view
     */
    private MaterialTextView createHeadlineTextView(int textRes) {
        MaterialTextView textView = new MaterialTextView(this);
        textView.setText(textRes);
        textView.setTextAppearance(R.style.TextAppearance_Material3_TitleLarge);
        textView.setTextColor(getDynamicColor(this, com.google.android.material.R.attr.colorOnSurface));
        return textView;
    }

    /**
     * Creates card body text with standard spacing and surface-variant coloring.
     *
     * @param text body content
     * @return themed body view
     */
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

    /**
     * Creates card body text from a string resource.
     *
     * @param textRes body resource
     * @return themed body view
     */
    private MaterialTextView createBodyTextView(int textRes) {
        return createBodyTextView(getString(textRes));
    }

    /**
     * Converts a density-independent size to rounded physical pixels.
     *
     * @param value size in dp
     * @return size in pixels
     */
    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    /** Navigation callback installed by editor subpages. */
    public static abstract class onBackPressedListener {
        /** Handles a back press for the active subpage. */
        public abstract void onBackPressed();
    }

    /** Background workflow that compiles, repacks, and flashes the edited image. */
    class repackLogic extends Thread {
        private String errorMessage = "";
        private AlertDialog waitingDialog;

        /**
         * Executes repacking and flashing in sequence, stopping at the first failed stage.
         */
        @Override
        public void run() {
            showWaitDialog(R.string.repacking);
            if (!performRepack()) {
                dismissWaitDialog();
                showDetailedError(errorMessage);
                return;
            }

            dismissWaitDialog();

            showWaitDialog(R.string.flashing_boot);
            if (!performFlashing()) {
                dismissWaitDialog();
                showErrorDialog(R.string.flashing_failed);
                return;
            }

            dismissWaitDialog();

            showRebootDialog();
        }

        /**
         * Shows a progress dialog on the UI thread.
         *
         * @param messageId progress-message resource
         */
        private void showWaitDialog(int messageId) {
            runOnUiThread(() -> {
                waitingDialog = DialogUtil.getWaitDialog(MainActivity.this, messageId);
                waitingDialog.show();
            });
        }

        /** Dismisses the active progress dialog on the UI thread. */
        private void dismissWaitDialog() {
            runOnUiThread(() -> {
                if (waitingDialog != null && waitingDialog.isShowing()) {
                    waitingDialog.dismiss();
                }
            });
        }

        /**
         * Compiles and repacks the edited device tree.
         *
         * @return {@code true} on success; on failure, stores the exception message
         */
        private boolean performRepack() {
            try {
                KonaBessCore.dts2bootImage(MainActivity.this);
                return true;
            } catch (Exception e) {
                errorMessage = e.getMessage();
                return false;
            }
        }

        /**
         * Writes the generated image to the selected block partition.
         *
         * @return {@code true} when the root write completes successfully
         */
        private boolean performFlashing() {
            try {
                KonaBessCore.writeDtbImage(MainActivity.this);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        /**
         * Displays repack diagnostics on the UI thread.
         *
         * @param details diagnostic text
         */
        private void showDetailedError(String details) {
            runOnUiThread(() -> DialogUtil.showDetailedError(MainActivity.this, 2131689664, details));
        }

        /**
         * Displays a resource-backed error on the UI thread.
         *
         * @param messageRes error-message resource
         */
        private void showErrorDialog(int messageRes) {
            runOnUiThread(() -> DialogUtil.showError(MainActivity.this, messageRes));
        }

        /**
         * Prompts for an immediate reboot after flashing and reports reboot failures.
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

    /** Background workflow that copies, extracts, decompiles, and identifies the active image. */
    class unpackLogic extends Thread {
        private String errorMessage = "";
        private int dtbIndex;
        private AlertDialog waitingDialog;

        /**
         * Copies the source partition, extracts and decompiles its DTB, detects the chip, and opens
         * target selection.
         */
        @Override
        public void run() {
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

            handleDtbSelection();
        }

        /**
         * Runs an initialization step while showing the generic progress message.
         *
         * @param task blocking operation
         * @return {@code true} when the task completes without an exception
         */
        private boolean performStep(Runnable task) {
            showWaitDialog(R.string.wait);
            try {
                task.run();
                return true;
            } catch (Exception e) {
                return false;
            } finally {
                dismissWaitDialog();
            }
        }

        /**
         * Runs an initialization step and retains its exception message for a detailed error.
         *
         * @param messageId progress-message resource
         * @param task blocking operation
         * @return {@code true} when the task completes without an exception
         */
        private boolean performStepWithErrorDetails(int messageId, Runnable task) {
            showWaitDialog(messageId);
            try {
                task.run();

                return true;
            } catch (Exception e) {
                errorMessage = e.getMessage();

                return false;
            } finally {
                dismissWaitDialog();
            }
        }

        /**
         * Activates the only detected target or displays a non-cancelable target picker.
         *
         * <p>The kernel DTBO index marks the likely list entry when multiple targets are available.
         */
        private void handleDtbSelection() {
            runOnUiThread(() -> {
                if (KonaBessCore.dtbs.isEmpty()) {
                    showErrorDialog(R.string.incompatible_device);
                    return;
                }

                if (KonaBessCore.dtbs.size() == 1) {
                    KonaBessCore.chooseTarget(KonaBessCore.dtbs.get(0), MainActivity.this);
                    showMainView();
                    return;
                }

                ListView listView = new ListView(MainActivity.this);
                ArrayList<ParamAdapter.item> items = new ArrayList<>();

                for (KonaBessCore.dtb dtb : KonaBessCore.dtbs) {
                    items.add(new ParamAdapter.item() {{
                        title = dtb.id + " " + ChipInfo.name2ChipDesc(dtb.type, MainActivity.this);

                        subtitle = dtb.id == dtbIndex ? MainActivity.this.getString(R.string.possible_dtb) : "";
                    }});
                }

                listView.setAdapter(new ParamAdapter(items, MainActivity.this));

                AlertDialog dialog = new MaterialAlertDialogBuilder(MainActivity.this)
                        .setTitle(R.string.select_dtb_title)
                        .setMessage(R.string.select_dtb_msg)
                        .setView(listView)
                        .setCancelable(false)
                        .create();
                dialog.show();

                listView.setOnItemClickListener((parent, view, position, id) -> {
                    KonaBessCore.chooseTarget(KonaBessCore.dtbs.get(position), MainActivity.this);
                    dialog.dismiss();
                    showMainView();
                });
            });
        }

        /**
         * Shows a progress dialog on the UI thread.
         *
         * @param messageId progress-message resource
         */
        private void showWaitDialog(int messageId) {
            runOnUiThread(() -> {
                waitingDialog = DialogUtil.getWaitDialog(MainActivity.this, messageId);
                waitingDialog.show();
            });
        }

        /** Dismisses the active progress dialog on the UI thread. */
        private void dismissWaitDialog() {
            runOnUiThread(() -> {
                if (waitingDialog != null && waitingDialog.isShowing()) {
                    waitingDialog.dismiss();
                }
            });
        }

        /**
         * Displays a resource-backed error on the UI thread.
         *
         * @param messageId error-message resource
         */
        private void showErrorDialog(int messageId) {
            runOnUiThread(() ->
                    DialogUtil.showError(MainActivity.this, messageId)
            );
        }

        /**
         * Displays initialization diagnostics on the UI thread.
         *
         * @param titleId error-summary resource
         * @param details diagnostic text
         */
        private void showDetailedErrorDialog(int titleId, String details) {
            runOnUiThread(() ->
                    DialogUtil.showDetailedError(MainActivity.this, titleId, details)
            );
        }
    }
}
