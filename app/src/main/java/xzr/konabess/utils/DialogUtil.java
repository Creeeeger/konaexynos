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

public class DialogUtil {
    // Simple Error Dialog
    public static void showError(AppCompatActivity activity, String message) {
        createAlertDialog(activity, activity.getString(R.string.error), message).show();
    }

    public static void showError(AppCompatActivity activity, int messageId) {
        showError(activity, activity.getString(messageId));
    }

    // Detailed Error Dialog
    public static void showDetailedError(AppCompatActivity activity, String title, String detail) {
        String message = title + "\n" + activity.getString(R.string.long_press_to_copy);

        // Dynamic Material You Card
        MaterialCardView cardView = createDynamicCard(activity, detail);

        createAlertDialog(activity, activity.getString(R.string.error), message, cardView).show();
    }

    public static void showDetailedError(AppCompatActivity activity, int titleId, String detail) {
        showDetailedError(activity, activity.getString(titleId), detail);
    }

    // Wait Dialog with ProgressBar
    public static AlertDialog getWaitDialog(Context context, int messageId) {
        return getWaitDialog(context, context.getString(messageId));
    }

    public static AlertDialog getWaitDialog(Context context, String message) {
        // ProgressBar styled with Material You dynamic color
        ProgressBar progressBar = createDynamicProgressBar(context);

        // Styled TextView
        MaterialTextView textView = createDynamicTextView(context, message);

        // Layout with padding and gravity
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.setPadding(32, 32, 32, 32);
        layout.addView(progressBar);
        layout.addView(textView);

        // Wrap in dynamic Material Card
        MaterialCardView cardView = createDynamicCard(context, layout);

        return createAlertDialog(context, null, null, cardView, false);
    }

    // Create a basic styled alert dialog with dynamic colors
    private static AlertDialog createAlertDialog(Context context, String title, String message) {
        return createAlertDialog(context, title, message, null, true);
    }

    private static AlertDialog createAlertDialog(Context context, String title, String message, View view) {
        return createAlertDialog(context, title, message, view, true);
    }

    private static AlertDialog createAlertDialog(Context context, String title, String message, View view, boolean cancelable) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                .setCancelable(cancelable)
                .setPositiveButton(R.string.ok, (dialog, which) -> dialog.dismiss());

        if (title != null) builder.setTitle(title);
        if (message != null) builder.setMessage(message);
        if (view != null) builder.setView(view);

        return builder.create();
    }

    // Dynamic Material CardView
    @NonNull
    public static MaterialCardView createDynamicCard(Context context, View child) {
        MaterialCardView cardView = new MaterialCardView(context);
        cardView.setCardElevation(6f); // Subtle shadow effect
        cardView.setStrokeWidth(2);

        // Use Material You dynamic color
        int colorPrimary = getDynamicColor(context, com.google.android.material.R.attr.colorPrimaryContainer);
        int strokeColor = getDynamicColor(context, com.google.android.material.R.attr.colorPrimary);

        cardView.setCardBackgroundColor(colorPrimary);
        cardView.setStrokeColor(strokeColor);
        cardView.addView(child);
        return cardView;
    }

    // Dynamic Card with String Content
    @NonNull
    private static MaterialCardView createDynamicCard(Context context, String content) {
        ScrollView scrollView = new ScrollView(context);
        MaterialTextView textView = createDynamicTextView(context, content);
        scrollView.addView(textView);

        return createDynamicCard(context, scrollView);
    }

    // Dynamic Material TextView
    @NonNull
    private static MaterialTextView createDynamicTextView(Context context, String text) {
        MaterialTextView textView = new MaterialTextView(context);
        textView.setTextIsSelectable(true);
        textView.setText(text);

        // Apply Material You dynamic text color
        int textColor = getDynamicColor(context, com.google.android.material.R.attr.colorOnSurface);
        textView.setTextColor(textColor);
        textView.setPadding(16, 16, 16, 16);
        return textView;
    }

    // Dynamic ProgressBar
    @NonNull
    private static ProgressBar createDynamicProgressBar(Context context) {
        ProgressBar progressBar = new ProgressBar(context);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.gravity = Gravity.CENTER;
        progressBar.setLayoutParams(params);

        // Tint progress bar dynamically
        int tintColor = getDynamicColor(context, com.google.android.material.R.attr.colorPrimary);
        progressBar.setIndeterminateTintList(ColorStateList.valueOf(tintColor));
        return progressBar;
    }

    // Fetch Material You Dynamic Color
    public static int getDynamicColor(Context context, int attr) {
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(attr, typedValue, true);
        return typedValue.data;
    }
}