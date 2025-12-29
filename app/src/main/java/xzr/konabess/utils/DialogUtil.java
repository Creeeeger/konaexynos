package xzr.konabess.utils;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.google.android.material.textview.MaterialTextView;

import xzr.konabess.R;

/** Builds consistently themed error, progress, and content dialogs. */
public class DialogUtil {
    /**
     * Displays a modal error dialog.
     *
     * @param activity activity that owns the dialog
     * @param message error text
     */
    public static void showError(AppCompatActivity activity, String message) {
        createAlertDialog(activity, activity.getString(R.string.error), message).show();
    }

    /**
     * Displays a modal error dialog using a string resource.
     *
     * @param activity activity that owns the dialog
     * @param messageId error-message resource
     */
    public static void showError(AppCompatActivity activity, int messageId) {
        showError(activity, activity.getString(messageId));
    }

    /**
     * Displays an error summary with selectable, scrollable detail text.
     *
     * @param activity activity that owns the dialog
     * @param title summary shown above the detail card
     * @param detail diagnostic text placed in the card
     */
    public static void showDetailedError(AppCompatActivity activity, String title, String detail) {
        String message = title + "\n" + activity.getString(R.string.long_press_to_copy);

        MaterialCardView cardView = createDynamicCard(activity, detail);

        createAlertDialog(activity, activity.getString(R.string.error), message, cardView).show();
    }

    /**
     * Displays a detailed error using a string resource for the summary.
     *
     * @param activity activity that owns the dialog
     * @param titleId summary resource
     * @param detail diagnostic text placed in the card
     */
    public static void showDetailedError(AppCompatActivity activity, int titleId, String detail) {
        showDetailedError(activity, activity.getString(titleId), detail);
    }

    /**
     * Creates a progress dialog whose back-press and outside-touch cancellation is disabled.
     *
     * @param context themed context used to build the dialog
     * @param messageId progress-message resource
     * @return dialog that has not yet been shown
     */
    public static AlertDialog getWaitDialog(Context context, int messageId) {
        return getWaitDialog(context, context.getString(messageId));
    }

    /**
     * Creates a progress dialog whose back-press and outside-touch cancellation is disabled.
     *
     * @param context themed context used to build the dialog
     * @param message progress text
     * @return dialog that has not yet been shown
     */
    public static AlertDialog getWaitDialog(Context context, String message) {
        ProgressBar progressBar = createDynamicProgressBar(context);

        MaterialTextView textView = createDynamicTextView(context, message);

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.setPadding(32, 32, 32, 32);
        layout.addView(progressBar);
        layout.addView(textView);

        MaterialCardView cardView = createDynamicCard(context, layout);

        return createAlertDialog(context, null, null, cardView, false);
    }

    /**
     * Creates a cancelable text-only alert.
     *
     * @param context themed context
     * @param title optional title
     * @param message optional message
     * @return configured dialog
     */
    private static AlertDialog createAlertDialog(Context context, String title, String message) {
        return createAlertDialog(context, title, message, null, true);
    }

    /**
     * Creates a cancelable alert with an optional custom view.
     *
     * @param context themed context
     * @param title optional title
     * @param message optional message
     * @param view optional custom content
     * @return configured dialog
     */
    private static AlertDialog createAlertDialog(Context context, String title, String message, View view) {
        return createAlertDialog(context, title, message, view, true);
    }

    /**
     * Applies the common Material dialog shape, OK action, and message styling.
     *
     * @param context themed context
     * @param title optional title
     * @param message optional message
     * @param view optional custom content
     * @param cancelable whether back presses and outside touches may dismiss the dialog
     * @return configured dialog
     */
    private static AlertDialog createAlertDialog(Context context, String title, String message, View view, boolean cancelable) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                .setCancelable(cancelable)
                .setPositiveButton(R.string.ok, (dialog, which) -> dialog.dismiss());

        if (title != null) builder.setTitle(title);
        if (message != null) builder.setMessage(message);
        if (view != null) builder.setView(view);

        builder.setBackground(createDialogBackground(context));

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> styleDialogMessage(dialog, context));
        return dialog;
    }

    /**
     * Wraps a view in the card style used by editor sections and dialog details.
     *
     * <p>If the view already has a parent, it is detached before being added to the card.
     *
     * @param context themed context
     * @param child content to place in the card
     * @return configured card containing {@code child}
     */
    @NonNull
    public static MaterialCardView createDynamicCard(Context context, View child) {
        MaterialCardView card = new MaterialCardView(context);

        int surface = DialogUtil.getDynamicColor(context, com.google.android.material.R.attr.colorSurface);
        int outlineColor = DialogUtil.getDynamicColor(context, com.google.android.material.R.attr.colorOutline);

        card.setCardBackgroundColor(surface);
        card.setStrokeColor(outlineColor);
        card.setStrokeWidth(1);
        card.setCardElevation(0f);
        card.setRadius(dp(context, 24));
        card.setUseCompatPadding(false);
        card.setPreventCornerOverlap(false);
        int padding = dp(context, 20);
        card.setContentPadding(padding, padding, padding, padding);
        if (child.getParent() instanceof ViewGroup) {
            ((ViewGroup) child.getParent()).removeView(child);
        }
        card.addView(child);
        return card;
    }

    /**
     * Wraps selectable text in a scroll view and styled card.
     *
     * @param context themed context
     * @param content text displayed in the card
     * @return scrollable content card
     */
    @NonNull
    private static MaterialCardView createDynamicCard(Context context, String content) {
        ScrollView scrollView = new ScrollView(context);
        MaterialTextView textView = createDynamicTextView(context, content);
        scrollView.addView(textView);

        return createDynamicCard(context, scrollView);
    }

    /**
     * Applies the theme's secondary surface text color to the platform message view.
     *
     * @param dialog dialog whose message view is styled
     * @param context context used to resolve theme attributes
     */
    private static void styleDialogMessage(AlertDialog dialog, Context context) {
        TextView messageView = dialog.findViewById(android.R.id.message);
        if (messageView != null) {
            int textColor = getDynamicColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant);
            messageView.setTextColor(textColor);
        }
    }

    /**
     * Creates the rounded surface and outline used behind alerts.
     *
     * @param context context used to resolve theme colors and density
     * @return themed dialog background
     */
    @NonNull
    private static MaterialShapeDrawable createDialogBackground(Context context) {
        float radius = dp(context, 28);
        ShapeAppearanceModel shapeAppearanceModel = ShapeAppearanceModel.builder()
                .setAllCornerSizes(radius)
                .build();

        MaterialShapeDrawable drawable = new MaterialShapeDrawable(shapeAppearanceModel);
        int surface = getDynamicColor(context, com.google.android.material.R.attr.colorSurface);
        int outlineColor = getDynamicColor(context, com.google.android.material.R.attr.colorOutlineVariant);
        drawable.setFillColor(ColorStateList.valueOf(surface));
        drawable.setStroke(1f, outlineColor);
        return drawable;
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

    /**
     * Creates selectable text using the current surface foreground color.
     *
     * @param context themed context
     * @param text initial text
     * @return configured text view
     */
    @NonNull
    private static MaterialTextView createDynamicTextView(Context context, String text) {
        MaterialTextView textView = new MaterialTextView(context);
        textView.setTextIsSelectable(true);
        textView.setText(text);

        int textColor = getDynamicColor(context, com.google.android.material.R.attr.colorOnSurface);
        textView.setTextColor(textColor);
        textView.setPadding(16, 16, 16, 16);
        return textView;
    }

    /**
     * Creates an indeterminate progress indicator tinted with the current primary color.
     *
     * @param context themed context
     * @return configured progress indicator
     */
    @NonNull
    private static ProgressBar createDynamicProgressBar(Context context) {
        ProgressBar progressBar = new ProgressBar(context);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.gravity = Gravity.CENTER;
        progressBar.setLayoutParams(params);

        int tintColor = getDynamicColor(context, R.attr.colorPrimary);
        progressBar.setIndeterminateTintList(ColorStateList.valueOf(tintColor));
        return progressBar;
    }

    /**
     * Resolves a color-valued attribute from the current theme.
     *
     * @param context themed context
     * @param attr attribute resource to resolve
     * @return resolved packed color value
     */
    public static int getDynamicColor(Context context, int attr) {
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(attr, typedValue, true);
        return typedValue.data;
    }
}
