package xzr.konabess.utils;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textview.MaterialTextView;

import xzr.konabess.R;

/**
 * Utility class for creating and displaying various types of Material You styled dialogs.
 */
public class DialogUtil {
    // --- BASIC ERROR DIALOGS ---

    /**
     * Show a simple error dialog with a given message (String version).
     *
     * @param activity Current AppCompatActivity context.
     * @param message  Error message to display.
     */
    public static void showError(AppCompatActivity activity, String message) {
        createAlertDialog(activity, activity.getString(R.string.error), message).show();
    }

    /**
     * Show a simple error dialog with a message from string resources (int version).
     *
     * @param activity  Current AppCompatActivity context.
     * @param messageId Resource ID for the error message.
     */
    public static void showError(AppCompatActivity activity, int messageId) {
        showError(activity, activity.getString(messageId));
    }

    // --- DETAILED ERROR DIALOGS (e.g. for stack traces, logs, etc.) ---

    /**
     * Show an error dialog with a custom title and a detailed message inside a styled card.
     *
     * @param activity Current AppCompatActivity context.
     * @param title    Error title (main message).
     * @param detail   Detailed error description (e.g. stack trace, log output).
     */
    public static void showDetailedError(AppCompatActivity activity, String title, String detail) {
        // Add "long press to copy" hint.
        String message = title + "\n" + activity.getString(R.string.long_press_to_copy);

        // Wrap the detail in a scrollable MaterialCardView.
        MaterialCardView cardView = createDynamicCard(activity, detail);

        createAlertDialog(activity, activity.getString(R.string.error), message, cardView).show();
    }

    /**
     * Overload: Show detailed error with title from string resources.
     */
    public static void showDetailedError(AppCompatActivity activity, int titleId, String detail) {
        showDetailedError(activity, activity.getString(titleId), detail);
    }

    // --- WAIT DIALOGS (loading/progress indicators) ---

    /**
     * Show a wait dialog (progress bar + message) with message from string resources.
     *
     * @param context   Context for dialog.
     * @param messageId Resource ID for message.
     * @return Configured AlertDialog (caller should show() it).
     */
    public static AlertDialog getWaitDialog(Context context, int messageId) {
        return getWaitDialog(context, context.getString(messageId));
    }

    /**
     * Show a wait dialog (progress bar + message) with a string message.
     *
     * @param context Context for dialog.
     * @param message Message string.
     * @return Configured AlertDialog (caller should show() it).
     */
    public static AlertDialog getWaitDialog(Context context, String message) {
        // Create Material You styled ProgressBar.
        ProgressBar progressBar = createDynamicProgressBar(context);

        // Styled message TextView.
        MaterialTextView textView = createDynamicTextView(context, message);

        // Vertical linear layout for spacing and centering.
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.setPadding(32, 32, 32, 32);
        layout.addView(progressBar);
        layout.addView(textView);

        // Wrap content in a MaterialCardView for Material You look.
        MaterialCardView cardView = createDynamicCard(context, layout);

        return createAlertDialog(context, null, null, cardView, false);
    }

    // --- ALERT DIALOG CREATION HELPERS ---

    /**
     * Basic Material You AlertDialog with title & message.
     */
    private static AlertDialog createAlertDialog(Context context, String title, String message) {
        return createAlertDialog(context, title, message, null, true);
    }

    /**
     * Material You AlertDialog with custom view.
     */
    private static AlertDialog createAlertDialog(Context context, String title, String message, View view) {
        return createAlertDialog(context, title, message, view, true);
    }

    /**
     * Generalized builder: Material You AlertDialog with title, message, custom view, and cancelable flag.
     * Always adds a positive "OK" button.
     */
    private static AlertDialog createAlertDialog(Context context, String title, String message, View view, boolean cancelable) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                .setCancelable(cancelable)
                .setPositiveButton(R.string.ok, (dialog, which) -> dialog.dismiss());

        if (title != null) builder.setTitle(title);
        if (message != null) builder.setMessage(message);
        if (view != null) builder.setView(view);

        return builder.create();
    }

    // --- MATERIAL YOU COMPONENT HELPERS ---

    /**
     * Create a MaterialCardView with Material You colors and one child view.
     *
     * @param context Context for styling.
     * @param child   Child view to place inside the card.
     * @return Configured MaterialCardView.
     */
    @NonNull
    public static MaterialCardView createDynamicCard(Context context, View child) {
        MaterialCardView cardView = new MaterialCardView(context);
        cardView.setCardElevation(6f); // Subtle Material shadow
        cardView.setStrokeWidth(2);

        // Fetch dynamic colors from theme (Material You)
        int colorPrimary = getDynamicColor(context, com.google.android.material.R.attr.colorPrimaryContainer);
        int strokeColor = getDynamicColor(context, com.google.android.material.R.attr.colorPrimary);

        cardView.setCardBackgroundColor(colorPrimary);
        cardView.setStrokeColor(strokeColor);
        cardView.addView(child);
        return cardView;
    }

    /**
     * Create a MaterialCardView containing scrollable text content.
     *
     * @param context Context for styling.
     * @param content String to display inside the card.
     * @return Configured MaterialCardView with scrollable content.
     */
    @NonNull
    private static MaterialCardView createDynamicCard(Context context, String content) {
        ScrollView scrollView = new ScrollView(context);
        MaterialTextView textView = createDynamicTextView(context, content);
        scrollView.addView(textView);

        return createDynamicCard(context, scrollView);
    }

    /**
     * Create a MaterialTextView with selectable text and Material You text color.
     *
     * @param context Context for styling.
     * @param text    Text to display.
     * @return Configured MaterialTextView.
     */
    @NonNull
    private static MaterialTextView createDynamicTextView(Context context, String text) {
        MaterialTextView textView = new MaterialTextView(context);
        textView.setTextIsSelectable(true); // Allow user to copy text
        textView.setText(text);

        // Apply Material You dynamic text color
        int textColor = getDynamicColor(context, com.google.android.material.R.attr.colorOnSurface);
        textView.setTextColor(textColor);
        textView.setPadding(16, 16, 16, 16);
        return textView;
    }

    /**
     * Create an indeterminate ProgressBar with Material You dynamic color.
     *
     * @param context Context for styling.
     * @return Configured ProgressBar.
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

        // Set indeterminate tint using dynamic color
        int tintColor = getDynamicColor(context, com.google.android.material.R.attr.colorPrimary);
        progressBar.setIndeterminateTintList(ColorStateList.valueOf(tintColor));
        return progressBar;
    }

    /**
     * Utility: Fetch a color value from current theme attributes (Material You support).
     *
     * @param context Context for resolving theme.
     * @param attr    Attribute ID to resolve (e.g. colorPrimary, colorOnSurface).
     * @return Color int value.
     */
    public static int getDynamicColor(Context context, int attr) {
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(attr, typedValue, true);
        return typedValue.data;
    }
}