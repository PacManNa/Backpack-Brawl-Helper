package ua.borlock.autotap;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Insets;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** Independent timer window. Closing it never stops countdowns or the bot window. */
final class TimerOverlay {
    private static final int CREAM = 0xFFF8EFE4, MUTED = 0xFFC7B5BD, GOLD = 0xFFF4C27E, GREEN = 0xFF99CCA0;
    private final AccessibilityService service;
    private final WindowManager manager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<Row> rows = new ArrayList<>();
    private final Rect area = new Rect();
    private LinearLayout root, body, list;
    private ScrollView scroll;
    private TextView bubble;
    private WindowManager.LayoutParams params;
    private long renderedRevision = -1;
    private String orientation;
    private boolean collapsed, restorePending;
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (root == null) return;
            refresh();
            TimerNotificationReceiver.deliverDue(service);
            handler.postDelayed(this, 1000);
        }
    };

    TimerOverlay(AccessibilityService service) {
        this.service = service;
        manager = (WindowManager) service.getSystemService(AccessibilityService.WINDOW_SERVICE);
    }

    boolean show() {
        if (manager == null) return false;
        if (root != null) { collapse(false); return true; }
        TimerStore.rescheduleAll(service);
        collapsed = false;
        renderedRevision = -1;
        createView();
        params = new WindowManager.LayoutParams(dp(304), WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        try {
            manager.addView(root, params);
            relayout();
            refresh();
            handler.post(ticker);
            return true;
        } catch (WindowManager.BadTokenException | IllegalStateException error) {
            root = null;
            return false;
        }
    }

    void minimize() {
        if (root != null && !collapsed) collapse(true);
    }

    void close() {
        savePlacement();
        handler.removeCallbacks(ticker);
        if (root != null) {
            try { manager.removeView(root); } catch (IllegalArgumentException ignored) { }
        }
        root = body = list = null;
        scroll = null;
        bubble = null;
        params = null;
        area.setEmpty();
        restorePending = false;
        rows.clear();
        renderedRevision = -1;
    }

    void onConfigurationChanged() {
        if (root == null) return;
        savePlacement();
        relayout();
    }

    private void createView() {
        root = column();
        root.setPadding(dp(7), dp(7), dp(7), dp(7));
        root.setBackground(shape(0xF52F2025, 17));
        body = column();
        LinearLayout header = new LinearLayout(service);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("⏱ Нагадування", 13, CREAM);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setMinHeight(dp(34));
        title.setContentDescription("Перетягнути меню нагадувань");
        title.setOnTouchListener(drag(false));
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView minimize = button("В іконку", 10, MUTED);
        minimize.setContentDescription("Згорнути нагадування в іконку");
        minimize.setOnClickListener(v -> collapse(true));
        header.addView(minimize, new LinearLayout.LayoutParams(dp(59), dp(30)));
        TextView close = button("×", 27, CREAM);
        close.setContentDescription("Закрити меню. Таймери продовжать відлік");
        close.setOnClickListener(v -> close());
        header.addView(close, new LinearLayout.LayoutParams(dp(32), dp(32)));
        body.addView(header);
        scroll = new ScrollView(service);
        scroll.setFillViewport(false);
        list = column();
        scroll.addView(list, new ScrollView.LayoutParams(-1, -2));
        body.addView(scroll, new LinearLayout.LayoutParams(-1, dp(276)));
        TextView manage = button("✎ Керувати таймерами", 11, GOLD);
        manage.setBackground(shape(0xFF49353C, 9));
        manage.setOnClickListener(v -> {
            collapse(true);
            service.startActivity(new Intent(service, TimerActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        });
        LinearLayout.LayoutParams footer = new LinearLayout.LayoutParams(-1, dp(34));
        footer.topMargin = dp(5);
        body.addView(manage, footer);
        root.addView(body, new LinearLayout.LayoutParams(-1, -2));
        bubble = button("⏱", 12, CREAM);
        bubble.setMinHeight(dp(46));
        bubble.setGravity(Gravity.CENTER);
        bubble.setOnClickListener(v -> collapse(false));
        bubble.setOnTouchListener(drag(true));
        bubble.setVisibility(View.GONE);
        root.addView(bubble, new LinearLayout.LayoutParams(-1, dp(48)));
        final LinearLayout owner = root;
        root.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            if (root != owner || params == null) return;
            if (restorePending) {
                restorePending = false;
                ClickerPreferences.Position point = ClickerPreferences.position(service, profile(), orientation,
                        0.02f, collapsed ? .80f : .18f);
                params.x = ClickerPreferences.coordinate(point.x, area.left, area.width(), root.getWidth());
                params.y = ClickerPreferences.coordinate(point.y, area.top, area.height(), root.getHeight());
                updateWindow();
            } else clamp();
        });
    }

    private void refresh() {
        if (root == null) return;
        List<TimerItem> timers = TimerStore.getTimers(service);
        long revision = TimerStore.getRevision(service);
        long now = SystemClock.elapsedRealtime();
        if (revision != renderedRevision) {
            renderedRevision = revision;
            timers.sort((a, b) -> a.isActive() == b.isActive()
                    ? a.isActive() ? Long.compare(a.deadlineElapsedMs, b.deadlineElapsedMs) : 0
                    : a.isActive() ? -1 : 1);
            rows.clear();
            list.removeAllViews();
            for (TimerItem item : timers) addRow(item);
            if (timers.isEmpty()) {
                TextView empty = text("Додай таймер у застосунку", 12, MUTED);
                empty.setPadding(dp(6), dp(18), dp(6), dp(18));
                list.addView(empty);
            }
            resizeList();
        }
        TimerItem next = null;
        int readyCount = 0;
        for (Row row : rows) {
            row.countdown.setText(TimerDisplay.status(row.item, now));
            row.countdown.setTextColor(row.item.isReady(now) ? GREEN : row.item.isActive() ? GOLD : MUTED);
            if (row.item.isReady(now)) readyCount++;
            if (row.item.isActive() && (next == null || row.item.deadlineElapsedMs < next.deadlineElapsedMs)) next = row.item;
        }
        bubble.setText(readyCount > 0 ? "⏱ Готово: " + readyCount
                : next != null ? "⏱\n" + TimerDisplay.duration(next.remainingMs(now)) : "⏱\nЗупинено");
        bubble.setTextColor(readyCount > 0 ? GREEN : CREAM);
        bubble.setContentDescription("Відкрити нагадування. " + (next == null ? "Немає активних таймерів"
                : next.name + ": " + TimerDisplay.status(next, now)));
    }

    private void addRow(TimerItem item) {
        LinearLayout row = new LinearLayout(service);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(7), dp(6), dp(5), dp(6));
        row.setBackground(shape(0xFF392B31, 10));
        LinearLayout labels = column();
        TextView name = text(item.emoji + " " + item.name, 11, CREAM);
        name.setMaxLines(2);
        TextView countdown = text("", 11, GOLD);
        countdown.setSingleLine(true);
        countdown.setEllipsize(android.text.TextUtils.TruncateAt.END);
        labels.addView(name, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams countdownParams = new LinearLayout.LayoutParams(-1, -2);
        countdownParams.topMargin = dp(4);
        labels.addView(countdown, countdownParams);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        TextView start = button(item.isActive() ? "↻ Заново" : "▶ Старт", 10, CREAM);
        start.setBackground(shape(0xFF526A4D, 8));
        start.setContentDescription((item.isActive() ? "Перезапустити " : "Запустити ") + item.name);
        start.setOnClickListener(v -> { TimerStore.restartTimer(service, item.id); refresh(); });
        LinearLayout.LayoutParams startParams = new LinearLayout.LayoutParams(dp(61), dp(34));
        startParams.leftMargin = dp(4);
        row.addView(start, startParams);
        TextView stop = button("■ Стоп", 10, CREAM);
        stop.setBackground(shape(0xFF78504E, 8));
        stop.setContentDescription("Зупинити " + item.name);
        stop.setEnabled(item.isActive());
        stop.setAlpha(item.isActive() ? 1f : .35f);
        stop.setOnClickListener(v -> { TimerStore.stopTimer(service, item.id); refresh(); });
        LinearLayout.LayoutParams stopParams = new LinearLayout.LayoutParams(dp(49), dp(34));
        stopParams.leftMargin = dp(4);
        row.addView(stop, stopParams);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
        rowParams.bottomMargin = dp(5);
        list.addView(row, rowParams);
        rows.add(new Row(item, countdown));
    }

    private void collapse(boolean value) {
        if (root == null) return;
        if (collapsed != value) savePlacement();
        collapsed = value;
        body.setVisibility(value ? View.GONE : View.VISIBLE);
        bubble.setVisibility(value ? View.VISIBLE : View.GONE);
        root.setBackground(shape(0xF52F2025, value ? 24 : 17));
        relayout();
        refresh();
    }

    private void relayout() {
        if (root == null || params == null) return;
        area.set(availableArea());
        orientation = service.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE
                ? "landscape" : "portrait";
        float scale = Math.max(1f, service.getResources().getConfiguration().fontScale);
        params.width = Math.min(area.width(), dp(collapsed ? 94 * scale
                : Math.max(304, ClickerPreferences.panelWidthDp(service, ClickerPreferences.TIMERS_PROFILE))));
        restorePending = true;
        resizeList();
        updateWindow();
        root.requestLayout();
    }

    private void resizeList() {
        if (scroll == null) return;
        int perRow = dp(68 * Math.max(1f, service.getResources().getConfiguration().fontScale));
        scroll.getLayoutParams().height = Math.min(Math.max(dp(52), Math.max(1, rows.size()) * perRow),
                Math.max(dp(52), Math.min(dp(340), area.height() - dp(100))));
        scroll.requestLayout();
    }

    private String profile() { return collapsed ? "timers_bubble" : ClickerPreferences.TIMERS_PROFILE; }
    private void savePlacement() {
        if (root == null || params == null || restorePending || root.getWidth() <= 0 || area.isEmpty()) return;
        ClickerPreferences.savePosition(service, profile(), orientation,
                ClickerPreferences.fraction(params.x, area.left, area.width(), root.getWidth()),
                ClickerPreferences.fraction(params.y, area.top, area.height(), root.getHeight()));
    }
    private void clamp() {
        int x = Math.max(area.left, Math.min(params.x, area.right - root.getWidth()));
        int y = Math.max(area.top, Math.min(params.y, area.bottom - root.getHeight()));
        if (x != params.x || y != params.y) { params.x = x; params.y = y; updateWindow(); }
    }
    private void updateWindow() {
        try { manager.updateViewLayout(root, params); } catch (IllegalArgumentException ignored) { }
    }
    private Rect availableArea() {
        if (Build.VERSION.SDK_INT >= 30) {
            android.view.WindowMetrics metrics = manager.getCurrentWindowMetrics();
            Rect bounds = metrics.getBounds();
            Insets insets = metrics.getWindowInsets().getInsetsIgnoringVisibility(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            return new Rect(insets.left, insets.top, Math.max(insets.left + 1, bounds.width() - insets.right),
                    Math.max(insets.top + 1, bounds.height() - insets.bottom));
        }
        Point point = new Point();
        manager.getDefaultDisplay().getRealSize(point);
        int statusId = service.getResources().getIdentifier("status_bar_height", "dimen", "android");
        int navId = service.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        int status = statusId == 0 ? 0 : service.getResources().getDimensionPixelSize(statusId);
        int nav = navId == 0 ? 0 : service.getResources().getDimensionPixelSize(navId);
        return new Rect(0, status, Math.max(1, point.x), Math.max(status + 1, point.y - nav));
    }
    private View.OnTouchListener drag(boolean click) {
        return new View.OnTouchListener() {
            float downX, downY;
            int x, y;
            boolean moved;
            @Override public boolean onTouch(View view, MotionEvent event) {
                if (params == null) return false;
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        restorePending = false;
                        downX = event.getRawX(); downY = event.getRawY();
                        x = params.x; y = params.y; moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - downX, dy = event.getRawY() - downY;
                        if (Math.hypot(dx, dy) > dp(8)) moved = true;
                        if (moved) { params.x = x + Math.round(dx); params.y = y + Math.round(dy); clamp(); updateWindow(); }
                        return true;
                    case MotionEvent.ACTION_UP:
                        savePlacement();
                        if (click && !moved) view.performClick();
                        return true;
                    case MotionEvent.ACTION_CANCEL: savePlacement(); return true;
                    default: return false;
                }
            }
        };
    }
    private LinearLayout column() {
        LinearLayout view = new LinearLayout(service); view.setOrientation(LinearLayout.VERTICAL); return view;
    }
    private TextView text(String label, int size, int color) {
        TextView view = new TextView(service);
        view.setText(label); view.setTextSize(size); view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD); view.setIncludeFontPadding(false);
        return view;
    }
    private TextView button(String label, int size, int color) {
        TextView view = text(label, size, color);
        view.setGravity(Gravity.CENTER); view.setClickable(true); view.setFocusable(true);
        return view;
    }
    private GradientDrawable shape(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color); drawable.setCornerRadius(dp(radius));
        drawable.setStroke(dp(1), 0xFF6F5563); return drawable;
    }
    private int dp(float value) { return Math.round(value * service.getResources().getDisplayMetrics().density); }
    private static final class Row {
        final TimerItem item;
        final TextView countdown;
        Row(TimerItem item, TextView countdown) { this.item = item; this.countdown = countdown; }
    }
}
