package ua.borlock.autotap;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Temporary drawing surface for marking a board frame or a tile sample. */
final class CalibrationOverlay {
    interface Listener {
        boolean isValid(RectF selection);
        void onSave(RectF selection);
        void onCancel();
    }

    private final Context context;
    private final WindowManager windowManager;
    private final String instructions;
    private final int accentColor;
    private final Listener listener;
    private CalibrationDrawView drawView;
    private TextView saveButton;
    private FrameLayout root;

    CalibrationOverlay(Context context, WindowManager windowManager, String instructions,
                       int accentColor, Listener listener) {
        this.context = context;
        this.windowManager = windowManager;
        this.instructions = instructions;
        this.accentColor = accentColor;
        this.listener = listener;
    }

    void show() {
        root = createRoot();
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                -1, -1, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        windowManager.addView(root, params);
    }

    void dismiss() {
        if (root == null) return;
        try { windowManager.removeView(root); }
        catch (IllegalArgumentException ignored) { }
        root = null;
        drawView = null;
        saveButton = null;
    }

    private FrameLayout createRoot() {
        FrameLayout frame = new FrameLayout(context);
        drawView = new CalibrationDrawView(context, accentColor, this::updateSaveButton);
        frame.addView(drawView, new FrameLayout.LayoutParams(-1, -1));
        frame.addView(createToolbar(), toolbarParams());
        return frame;
    }

    private LinearLayout createToolbar() {
        LinearLayout toolbar = new LinearLayout(context);
        toolbar.setOrientation(LinearLayout.VERTICAL);
        toolbar.setPadding(dp(12), dp(9), dp(12), dp(10));
        toolbar.setBackground(round(0xF52F2025, 14));
        TextView hint = text(instructions, CREAM, 13);
        hint.setMinLines(2);
        hint.setMaxLines(2);
        toolbar.addView(hint, new LinearLayout.LayoutParams(-1, -2));
        toolbar.addView(createActions(), new LinearLayout.LayoutParams(-1, dp(42)));
        return toolbar;
    }

    private LinearLayout createActions() {
        LinearLayout actions = new LinearLayout(context);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        addAction(actions, t(R.string.calibration_retry), PURPLE, drawView::clearSelection, 0);
        saveButton = text(t(R.string.save), CREAM, 10);
        saveButton.setBackground(round(GREEN, 9));
        saveButton.setEnabled(false);
        saveButton.setAlpha(0.45f);
        saveButton.setOnClickListener(view -> saveSelection());
        LinearLayout.LayoutParams saveParams = actionParams();
        saveParams.setMargins(dp(6), 0, dp(6), 0);
        actions.addView(saveButton, saveParams);
        addAction(actions, t(R.string.cancel), ROSE, this::cancel, 0);
        return actions;
    }

    private void addAction(LinearLayout actions, String label, int color,
                           Runnable action, int margin) {
        TextView button = text(label, CREAM, 10);
        button.setBackground(round(color, 9));
        button.setOnClickListener(view -> action.run());
        LinearLayout.LayoutParams params = actionParams();
        params.setMargins(dp(margin), 0, dp(margin), 0);
        actions.addView(button, params);
    }

    private LinearLayout.LayoutParams actionParams() {
        return new LinearLayout.LayoutParams(0, dp(42), 1f);
    }

    private FrameLayout.LayoutParams toolbarParams() {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, -2,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        params.setMargins(dp(12), dp(12), dp(12), dp(20));
        return params;
    }

    private void updateSaveButton(RectF selection) {
        if (saveButton == null) return;
        saveButton.setEnabled(listener.isValid(selection));
        saveButton.setAlpha(saveButton.isEnabled() ? 1f : 0.45f);
    }

    private void saveSelection() {
        RectF selection = drawView == null ? null : drawView.getSelection();
        if (!listener.isValid(selection)) return;
        dismiss();
        listener.onSave(selection);
    }

    private void cancel() {
        dismiss();
        listener.onCancel();
    }

    private TextView text(String value, int color, int sizeSp) {
        TextView view = new TextView(context);
        view.setTextLocale(AppLanguage.locale(context));
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(sizeSp);
        view.setGravity(Gravity.CENTER);
        view.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        view.setIncludeFontPadding(false);
        view.setPadding(dp(5), 0, dp(5), 0);
        view.setClickable(true);
        return view;
    }

    private android.graphics.drawable.GradientDrawable round(int color, int radiusDp) {
        android.graphics.drawable.GradientDrawable drawable =
                new android.graphics.drawable.GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static final int CREAM = 0xFFF8ECDA;
    private static final int GREEN = 0xFF347451;
    private static final int ROSE = 0xFF71414D;
    private static final int PURPLE = 0xFF59445B;

    private String t(int id, Object... args) {
        return AppLanguage.text(context, id, args);
    }
}
