package ua.borlock.autotap;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.drawable.GradientDrawable;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.ArrayList;
import java.util.List;

/** Runs the selected board bot and the shared local timer overlay. */
public final class TapAccessibilityService extends AccessibilityService {
    static final String PREFS = "pelyustka_settings";
    static final String KEY_NO_CHANGE_TIMEOUT = "move_delay_ms";
    static final String KEY_SWIPE_CELLS_TENTHS = "swipe_cells_tenths";
    static final String KEY_SWIPE_DURATION_MS = "swipe_duration_ms";
    private static final long DEFAULT_NO_CHANGE_TIMEOUT_MS = 900L;
    static final long MIN_NO_CHANGE_TIMEOUT_MS = 400L;
    static final long MAX_NO_CHANGE_TIMEOUT_MS = 4_000L;
    static final long NO_CHANGE_TIMEOUT_STEP_MS = 100L;
    private static final long DEFAULT_SWIPE_DURATION_MS = 120L;
    private static final long MIN_SWIPE_DURATION_MS = 100L;
    private static final long MAX_SWIPE_DURATION_MS = 350L;
    private static final int STABLE_FRAME_COUNT = 2;
    private static final int MAX_STABILIZATION_READS = 12;
    private static final long MIN_POST_SWIPE_SETTLE_MS = 120L;
    private static final long MIN_SCREENSHOT_INTERVAL_MS = 334L;
    private static final int PLUM = Color.rgb(48, 32, 38);
    private static final int ROSE = Color.rgb(218, 139, 147);
    private static final int CREAM = Color.rgb(248, 236, 218);
    private static final int GREEN = Color.rgb(126, 174, 116);
    private static final int EMPTY_CELL = Color.rgb(56, 39, 45);
    private static final int[] GEM_COLORS = {
            EMPTY_CELL, 0xFFE74958, 0xFF45BD68, 0xFFF4C542, 0xFFBD5AD8, 0xFF3BBDE2,
            0xFF35C9C2, 0xFFEA853D, 0xFFE9A438, 0xFF484B58, 0xFF9A58D1
    };
    private static final String[] GEM_LABELS = {
            "·", "R", "G", "Y", "P", "B", "C", "O", "➤", "●", "✦"
    };

    private static TapAccessibilityService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService solverExecutor = Executors.newSingleThreadExecutor();
    private final BoardVision boardVision = new BoardVision();
    private final GameSolver gameSolver = new GameSolver();
    private final MergeMadnessSolver mergeMadnessSolver = new MergeMadnessSolver();
    private final MergeMadnessSpawnModel mergeSpawnModel = new MergeMadnessSpawnModel();
    private MergeMadnessVision mergeMadnessVision;
    private WindowManager windowManager;
    private LinearLayout panel;
    private WindowManager.LayoutParams panelParams;
    private TextView panelTitle;
    private TextView stateChip;
    private TextView playButton;
    private TextView boardInfoView;
    private TextView messageView;
    private TextView scoreView;
    private TextView mergeDetailsView;
    private TextView[][] boardCells;
    private TextView[][] mergeBoardCells;
    private LinearLayout blitzBoardGrid;
    private LinearLayout mergeBoardGrid;
    private LinearLayout diagnosticsPanel;
    private LinearLayout timerPanel;
    private LinearLayout timerRowsContainer;
    private LinearLayout compactTimerPreview;
    private TextView compactTimerTitle;
    private TextView compactTimerCountdown;
    private TextView compactTimerRestart;
    private String compactTimerSelectedId;
    private final ArrayList<TimerRow> timerRows = new ArrayList<>();
    private long renderedTimerRevision = -1L;
    private boolean diagnosticsExpanded;
    private boolean timerMode;
    private boolean autoPlaying;
    private volatile BotMode activeMode = BotMode.BACKPACK_BLITZ;
    private boolean screenshotPending;
    private long lastSuccessfulScreenshotAtMs;
    private int moveCount;
    private int runGeneration;
    private GameBoard previousBoard;
    private GameBoard stableCandidateBoard;
    private MergeMadnessBoard previousMergeBoard;
    private MergeMadnessBoard stableCandidateMergeBoard;
    private BoardVision.Geometry stableCandidateMergeGeometry;
    private int stableCandidateReads;
    private int stabilizationReads;
    private int unchangedSwipeRetries;
    private long lastSwipeCompletedAtMs;
    private String lastRecognitionError;
    private GameSolver.Choice lastChoice;
    private BoardVision.Geometry lastGeometry;
    private MergeMadnessSolver.Move lastMergeMove;
    private BoardVision.Geometry lastMergeGeometry;
    private boolean awaitingMergeTransition;
    private int noMoveReads;
    private final Runnable timerTicker = new Runnable() {
        @Override public void run() {
            if (panel == null || panel.getVisibility() != View.VISIBLE) return;
            refreshTimerPanel();
            handler.postDelayed(this, 1_000L);
        }
    };

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        mergeMadnessVision = new MergeMadnessVision(this);
        TimerStore.rescheduleAll(this);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }

    @Override public void onInterrupt() { stopAutoPlay("Призупинено Android"); }

    @Override public boolean onUnbind(android.content.Intent intent) {
        closePanel();
        if (instance == this) instance = null;
        return super.onUnbind(intent);
    }

    @Override public void onDestroy() {
        closePanel();
        solverExecutor.shutdownNow();
        if (instance == this) instance = null;
        super.onDestroy();
    }

    static boolean showControlPanel() {
        return instance != null && instance.showPanel(instance.activeMode);
    }

    static boolean showControlPanel(BotMode mode) {
        return instance != null && instance.showPanel(mode);
    }

    static boolean showTimerPanel() {
        if (instance == null || instance.panel == null) return false;
        instance.diagnosticsExpanded = true;
        instance.setTimerMode(true);
        return true;
    }

    static long getNoChangeTimeoutMs(Context context) {
        long value = context.getSharedPreferences(PREFS, MODE_PRIVATE)
                .getLong(KEY_NO_CHANGE_TIMEOUT, DEFAULT_NO_CHANGE_TIMEOUT_MS);
        return Math.max(MIN_NO_CHANGE_TIMEOUT_MS, Math.min(MAX_NO_CHANGE_TIMEOUT_MS, value));
    }

    static long getSwipeDurationMs(Context context) {
        long value = context.getSharedPreferences(PREFS, MODE_PRIVATE)
                .getLong(KEY_SWIPE_DURATION_MS, DEFAULT_SWIPE_DURATION_MS);
        return Math.max(MIN_SWIPE_DURATION_MS, Math.min(MAX_SWIPE_DURATION_MS, value));
    }

    private boolean showPanel(BotMode mode) {
        if (windowManager == null) return false;
        selectBot(mode);
        if (panel != null) {
            panel.setVisibility(View.VISIBLE);
            updatePanel();
            refreshTimerPanel();
            startTimerTicker();
            return true;
        }
        panel = createPanel();
        panelParams = new WindowManager.LayoutParams(
                dp(272),
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        panelParams.gravity = Gravity.TOP | Gravity.START;
        panelParams.x = dp(14);
        panelParams.y = dp(4);
        try {
            windowManager.addView(panel, panelParams);
            refreshTimerPanel();
            startTimerTicker();
            return true;
        } catch (WindowManager.BadTokenException | IllegalStateException error) {
            panel = null;
            return false;
        }
    }

    private void selectBot(BotMode mode) {
        if (mode == null) mode = BotMode.BACKPACK_BLITZ;
        if (autoPlaying && activeMode != mode) stopAutoPlay("Зупинено");
        activeMode = mode;
        timerMode = false;
        diagnosticsExpanded = true;
        updateBotModeViews();
        updatePanelContentVisibility();
    }

    private void updateBotModeViews() {
        if (panelTitle != null) panelTitle.setText(activeMode.panelTitle);
        if (blitzBoardGrid != null) {
            blitzBoardGrid.setVisibility(activeMode == BotMode.BACKPACK_BLITZ ? View.VISIBLE : View.GONE);
        }
        if (mergeBoardGrid != null) {
            mergeBoardGrid.setVisibility(activeMode == BotMode.MERGE_MADNESS ? View.VISIBLE : View.GONE);
        }
        if (mergeDetailsView != null) {
            mergeDetailsView.setVisibility(activeMode == BotMode.MERGE_MADNESS
                    ? View.VISIBLE : View.GONE);
        }
        if (playButton != null) {
            playButton.setContentDescription("Запустити бота «" + activeMode.displayName + "»");
        }
        if (boardInfoView != null && !autoPlaying) {
            boardInfoView.setText(activeMode == BotMode.BACKPACK_BLITZ
                    ? "Поле ще не прочитане · 8×8" : "Поле ще не прочитане · 4×4");
        }
        if (scoreView != null && !autoPlaying) {
            scoreView.setText(activeMode == BotMode.BACKPACK_BLITZ
                    ? "Шукаю сусідні заміни" : "Шукаю найкращий напрямок");
        }
    }

    private LinearLayout createPanel() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(7), dp(5), dp(7), dp(6));
        GradientDrawable panelBackground = new GradientDrawable();
        panelBackground.setColor(0xF52F2025);
        panelBackground.setCornerRadius(dp(17));
        panelBackground.setStroke(dp(1), 0xCC9E7078);
        root.setBackground(panelBackground);
        root.setElevation(dp(8));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        panelTitle = button(activeMode.panelTitle, CREAM, 10);
        panelTitle.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        panelTitle.setPadding(dp(4), 0, dp(4), 0);
        panelTitle.setOnTouchListener(createDragListener());
        titleRow.addView(panelTitle, new LinearLayout.LayoutParams(0, dp(22), 1f));
        TextView versionView = button(readVersionName(), ROSE, 9);
        versionView.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        versionView.setContentDescription("Версія застосунку " + readVersionName());
        versionView.setOnTouchListener(createDragListener());
        titleRow.addView(versionView, new LinearLayout.LayoutParams(-2, dp(22)));
        root.addView(titleRow);

        stateChip = button("ГОТОВИЙ", CREAM, 10);
        stateChip.setBackground(round(0xFF62464D, 10));
        stateChip.setSingleLine(true);
        stateChip.setEllipsize(android.text.TextUtils.TruncateAt.END);
        stateChip.setContentDescription("Торкнись, щоб показати діагностику; перетягни, щоб пересунути панель.");
        stateChip.setOnTouchListener(createDragListener(true));
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, dp(25));
        statusParams.setMargins(0, dp(2), 0, dp(3));
        root.addView(stateChip, statusParams);

        diagnosticsPanel = new LinearLayout(this);
        diagnosticsPanel.setOrientation(LinearLayout.VERTICAL);
        diagnosticsPanel.setVisibility(View.VISIBLE);
        diagnosticsExpanded = true;

        boardCells = new TextView[GameBoard.SIDE][GameBoard.SIDE];
        blitzBoardGrid = new LinearLayout(this);
        blitzBoardGrid.setOrientation(LinearLayout.VERTICAL);
        blitzBoardGrid.setPadding(dp(2), dp(1), dp(2), dp(1));
        blitzBoardGrid.setBackground(round(0xFF21171B, 9));
        for (int row = 0; row < GameBoard.SIDE; row++) {
            LinearLayout boardRow = new LinearLayout(this);
            boardRow.setOrientation(LinearLayout.HORIZONTAL);
            for (int column = 0; column < GameBoard.SIDE; column++) {
                TextView cell = button("·", 0xFF937D80, 7);
                cell.setBackground(round(EMPTY_CELL, 3));
                cell.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
                boardCells[row][column] = cell;
                LinearLayout.LayoutParams cellParams = new LinearLayout.LayoutParams(0, dp(10), 1f);
                cellParams.setMargins(dp(1), dp(1), dp(1), dp(1));
                boardRow.addView(cell, cellParams);
            }
            blitzBoardGrid.addView(boardRow, new LinearLayout.LayoutParams(-1, -2));
        }
        diagnosticsPanel.addView(blitzBoardGrid, new LinearLayout.LayoutParams(-1, -2));

        mergeBoardCells = new TextView[MergeMadnessBoard.SIDE][MergeMadnessBoard.SIDE];
        mergeBoardGrid = new LinearLayout(this);
        mergeBoardGrid.setOrientation(LinearLayout.VERTICAL);
        mergeBoardGrid.setPadding(dp(2), dp(1), dp(2), dp(1));
        mergeBoardGrid.setBackground(round(0xFF21171B, 9));
        for (int row = 0; row < MergeMadnessBoard.SIDE; row++) {
            LinearLayout boardRow = new LinearLayout(this);
            boardRow.setOrientation(LinearLayout.HORIZONTAL);
            for (int column = 0; column < MergeMadnessBoard.SIDE; column++) {
                TextView cell = button("·", 0xFF937D80, 10);
                cell.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
                cell.setBackground(round(EMPTY_CELL, 4));
                mergeBoardCells[row][column] = cell;
                LinearLayout.LayoutParams cellParams = new LinearLayout.LayoutParams(0, dp(18), 1f);
                cellParams.setMargins(dp(1), dp(1), dp(1), dp(1));
                boardRow.addView(cell, cellParams);
            }
            mergeBoardGrid.addView(boardRow, new LinearLayout.LayoutParams(-1, -2));
        }
        diagnosticsPanel.addView(mergeBoardGrid, new LinearLayout.LayoutParams(-1, -2));

        boardInfoView = smallInfoText("Поле ще не прочитане");
        boardInfoView.setSingleLine(true);
        boardInfoView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(-1, -2);
        infoParams.setMargins(dp(2), dp(2), dp(2), 0);
        diagnosticsPanel.addView(boardInfoView, infoParams);

        mergeDetailsView = smallInfoText("Зсув поля · ланцюжок · впевненість");
        mergeDetailsView.setMinLines(2);
        mergeDetailsView.setMaxLines(2);
        mergeDetailsView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        mergeDetailsView.setVisibility(activeMode == BotMode.MERGE_MADNESS
                ? View.VISIBLE : View.GONE);
        LinearLayout.LayoutParams mergeDetailsParams = new LinearLayout.LayoutParams(-1, -2);
        mergeDetailsParams.setMargins(dp(2), dp(1), dp(2), 0);
        diagnosticsPanel.addView(mergeDetailsView, mergeDetailsParams);

        messageView = smallInfoText("Відкрий гру й натисни «Старт»");
        messageView.setTextColor(CREAM);
        messageView.setMinLines(1);
        messageView.setMaxLines(1);
        messageView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(-1, -2);
        messageParams.setMargins(dp(2), dp(1), dp(2), 0);
        diagnosticsPanel.addView(messageView, messageParams);

        scoreView = smallInfoText("Бот шукає найкращий збіг");
        scoreView.setTypeface(Typeface.MONOSPACE);
        scoreView.setSingleLine(true);
        scoreView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams scoreParams = new LinearLayout.LayoutParams(-1, -2);
        scoreParams.setMargins(dp(2), dp(1), dp(2), dp(1));
        diagnosticsPanel.addView(scoreView, scoreParams);
        timerPanel = createTimerPanel();

        compactTimerPreview = createCompactTimerPreview();
        compactTimerPreview.setVisibility(View.GONE);

        LinearLayout contentRow = new LinearLayout(this);
        contentRow.setGravity(Gravity.TOP);

        LinearLayout panelContent = new LinearLayout(this);
        panelContent.setOrientation(LinearLayout.VERTICAL);
        panelContent.addView(diagnosticsPanel, new LinearLayout.LayoutParams(-1, -2));
        panelContent.addView(timerPanel, new LinearLayout.LayoutParams(-1, -2));
        panelContent.addView(compactTimerPreview, new LinearLayout.LayoutParams(-1, dp(38)));
        contentRow.addView(panelContent, new LinearLayout.LayoutParams(0, -2, 1f));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setGravity(Gravity.TOP);
        LinearLayout.LayoutParams controlsColumnParams = new LinearLayout.LayoutParams(dp(52), -2);
        controlsColumnParams.setMargins(dp(4), 0, 0, 0);
        contentRow.addView(controls, controlsColumnParams);

        playButton = button("▶\nСтарт", CREAM, 9);
        playButton.setBackground(round(0xFF526A4D, 10));
        playButton.setOnClickListener(view -> startAutoPlay());
        playButton.setContentDescription("Запустити бота Рюкзак-Бліц");
        controls.addView(playButton, stackedControlParams());

        TextView stopButton = button("■\nСтоп", CREAM, 9);
        stopButton.setBackground(round(0xFF78504E, 10));
        stopButton.setOnClickListener(view -> stopAutoPlay("Зупинено"));
        stopButton.setContentDescription("Зупинити бота");
        controls.addView(stopButton, stackedControlParams());

        TextView closeButton = button("×\nСховати", CREAM, 9);
        closeButton.setBackground(round(0xFF493339, 10));
        closeButton.setOnClickListener(view -> closePanel());
        closeButton.setContentDescription("Сховати плаваючу панель");
        controls.addView(closeButton, stackedControlParams());

        root.addView(contentRow, new LinearLayout.LayoutParams(-1, -2));
        updateBotModeViews();
        updatePanelContentVisibility();
        return root;
    }

    private LinearLayout createTimerPanel() {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(2), dp(1), dp(2), dp(2));

        TextView heading = smallInfoText("ТАЙМЕРИ · НАГАДУВАННЯ");
        heading.setTextColor(0xFFFFD99B);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(-1, dp(20));
        headingParams.bottomMargin = dp(3);
        body.addView(heading, headingParams);

        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        timerRowsContainer = new LinearLayout(this);
        timerRowsContainer.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(timerRowsContainer, new android.widget.ScrollView.LayoutParams(-1, -2));
        body.addView(scroll, new LinearLayout.LayoutParams(-1, dp(55)));

        TextView manage = button("＋ Налаштувати таймери", 0xFFE8D6C7, 8);
        manage.setBackground(round(0xFF48343B, 8));
        manage.setOnClickListener(view -> {
            android.content.Intent intent = new android.content.Intent(this, TimerActivity.class)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        });
        LinearLayout.LayoutParams manageParams = new LinearLayout.LayoutParams(-1, dp(27));
        manageParams.topMargin = dp(4);
        body.addView(manage, manageParams);
        return body;
    }

    private LinearLayout createCompactTimerPreview() {
        LinearLayout preview = new LinearLayout(this);
        preview.setGravity(Gravity.CENTER_VERTICAL);
        preview.setPadding(dp(6), dp(3), dp(4), dp(3));
        preview.setBackground(round(0xFF35272D, 9));
        preview.setOnClickListener(view -> setDiagnosticsExpanded(true));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        compactTimerTitle = button("Немає таймерів", 0xFFF4E7D8, 9);
        compactTimerTitle.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        compactTimerTitle.setSingleLine(true);
        compactTimerTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        compactTimerCountdown = button("Додай таймер у застосунку", 0xFFD4B7BF, 8);
        compactTimerCountdown.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        compactTimerCountdown.setSingleLine(true);
        View.OnClickListener expandTimers = view -> setDiagnosticsExpanded(true);
        compactTimerTitle.setOnClickListener(expandTimers);
        compactTimerCountdown.setOnClickListener(expandTimers);
        labels.addView(compactTimerTitle, new LinearLayout.LayoutParams(-1, dp(15)));
        labels.addView(compactTimerCountdown, new LinearLayout.LayoutParams(-1, dp(14)));
        preview.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));

        compactTimerRestart = button("↻", CREAM, 14);
        compactTimerRestart.setBackground(round(0xFF526A4D, 8));
        compactTimerRestart.setVisibility(View.GONE);
        compactTimerRestart.setContentDescription("Перезапустити готовий таймер");
        compactTimerRestart.setOnClickListener(view -> restartCompactTimer());
        preview.addView(compactTimerRestart, new LinearLayout.LayoutParams(dp(34), dp(30)));
        return preview;
    }

    private void setTimerMode(boolean enabled) {
        timerMode = enabled;
        refreshTimerPanel();
        updatePanelContentVisibility();
    }

    private void updatePanelContentVisibility() {
        if (diagnosticsPanel != null) {
            diagnosticsPanel.setVisibility(!timerMode && diagnosticsExpanded ? View.VISIBLE : View.GONE);
        }
        if (timerPanel != null) {
            timerPanel.setVisibility(timerMode && diagnosticsExpanded ? View.VISIBLE : View.GONE);
        }
        if (compactTimerPreview != null) {
            compactTimerPreview.setVisibility(timerMode && !diagnosticsExpanded ? View.VISIBLE : View.GONE);
        }
        if (panel != null && panelParams != null && windowManager != null) {
            try { windowManager.updateViewLayout(panel, panelParams); }
            catch (IllegalArgumentException ignored) { }
        }
    }

    private void startTimerTicker() {
        handler.removeCallbacks(timerTicker);
        if (panel != null && panel.getVisibility() == View.VISIBLE) handler.post(timerTicker);
    }

    private void refreshTimerPanel() {
        if (panel == null || timerRowsContainer == null) return;
        long revision = TimerStore.getRevision(this);
        if (revision != renderedTimerRevision) rebuildTimerRows();

        long now = SystemClock.elapsedRealtime();
        for (TimerRow row : timerRows) {
            boolean ready = row.item.isReady(now);
            row.countdown.setText(ready ? "ГОТОВО" : formatTimerDuration(row.item.deadlineElapsedMs - now));
            row.countdown.setTextColor(ready ? 0xFF9DD09E : 0xFFFFD99B);
            row.restart.setVisibility(ready ? View.VISIBLE : View.GONE);
        }
        updateCompactTimerPreview(now);
    }

    private void rebuildTimerRows() {
        timerRowsContainer.removeAllViews();
        timerRows.clear();
        List<TimerItem> items = TimerStore.getTimers(this);
        for (TimerItem item : items) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(5), dp(3), dp(4), dp(3));
            row.setBackground(round(0xFF392B31, 8));

            LinearLayout labels = new LinearLayout(this);
            labels.setOrientation(LinearLayout.VERTICAL);
            TextView name = button(item.emoji + " " + item.name, 0xFFF1E5D9, 9);
            name.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            TextView countdown = smallInfoText("");
            countdown.setSingleLine(true);
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(-1, dp(17));
            labels.addView(name, nameParams);
            labels.addView(countdown, new LinearLayout.LayoutParams(-1, dp(15)));
            row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));

            TextView restart = button("↻", CREAM, 13);
            restart.setBackground(round(0xFF526A4D, 8));
            restart.setVisibility(View.GONE);
            restart.setContentDescription("Виконавши дію, перезапусти таймер «" + item.name + "»");
            restart.setOnClickListener(view -> {
                TimerStore.restartTimer(this, item.id);
                refreshTimerPanel();
            });
            row.addView(restart, new LinearLayout.LayoutParams(dp(32), dp(30)));
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
            rowParams.bottomMargin = dp(4);
            timerRowsContainer.addView(row, rowParams);
            timerRows.add(new TimerRow(item, countdown, restart));
        }

        if (items.isEmpty()) {
            TextView empty = smallInfoText("Немає таймерів · додай їх у застосунку");
            empty.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL);
            timerRowsContainer.addView(empty, new LinearLayout.LayoutParams(-1, dp(43)));
        }
        int visibleCount = Math.max(1, Math.min(3, items.size()));
        View scroll = timerRowsContainer.getParent() instanceof View ? (View) timerRowsContainer.getParent() : null;
        if (scroll != null && scroll.getLayoutParams() != null) {
            scroll.getLayoutParams().height = dp(items.isEmpty() ? 42 : visibleCount * 42);
            scroll.requestLayout();
        }
        renderedTimerRevision = TimerStore.getRevision(this);
    }

    private void updateCompactTimerPreview(long now) {
        List<TimerItem> items = TimerStore.getTimers(this);
        TimerItem selected = null;
        for (TimerItem item : items) {
            if (item.isReady(now)) {
                if (selected == null || !selected.isReady(now)
                        || item.deadlineElapsedMs < selected.deadlineElapsedMs) selected = item;
            } else if (selected == null || (!selected.isReady(now)
                    && item.deadlineElapsedMs < selected.deadlineElapsedMs)) {
                selected = item;
            }
        }
        if (selected == null) {
            compactTimerSelectedId = null;
            compactTimerTitle.setText("Немає таймерів");
            compactTimerCountdown.setText("Додай таймер у застосунку");
            compactTimerRestart.setVisibility(View.GONE);
            compactTimerRestart.setOnClickListener(null);
            return;
        }
        compactTimerSelectedId = selected.id;
        boolean ready = selected.isReady(now);
        compactTimerTitle.setText(selected.emoji + " " + selected.name);
        compactTimerCountdown.setText(ready ? "ГОТОВО · виконай дію"
                : formatTimerDuration(selected.deadlineElapsedMs - now));
        compactTimerCountdown.setTextColor(ready ? 0xFF9DD09E : 0xFFD4B7BF);
        compactTimerRestart.setVisibility(ready ? View.VISIBLE : View.GONE);
        compactTimerRestart.setOnClickListener(view -> restartCompactTimer());
    }

    private void restartCompactTimer() {
        String selectedId = compactTimerSelectedId;
        if (selectedId != null && TimerStore.restartTimer(this, selectedId)) refreshTimerPanel();
    }

    private String formatTimerDuration(long durationMs) {
        long seconds = Math.max(0L, (durationMs + 999L) / 1_000L);
        long days = seconds / 86_400L;
        long hours = (seconds % 86_400L) / 3_600L;
        long minutes = (seconds % 3_600L) / 60L;
        long remainder = seconds % 60L;
        if (days > 0L) return String.format(java.util.Locale.ROOT, "%dд %02d:%02d:%02d", days, hours, minutes, remainder);
        if (hours > 0L) return String.format(java.util.Locale.ROOT, "%02d:%02d:%02d", hours, minutes, remainder);
        return String.format(java.util.Locale.ROOT, "%02d:%02d", minutes, remainder);
    }

    private static final class TimerRow {
        final TimerItem item;
        final TextView countdown;
        final TextView restart;

        TimerRow(TimerItem item, TextView countdown, TextView restart) {
            this.item = item;
            this.countdown = countdown;
            this.restart = restart;
        }
    }


    private View.OnTouchListener createDragListener() {
        return createDragListener(false);
    }

    private View.OnTouchListener createDragListener(boolean toggleDiagnosticsOnTap) {
        return new View.OnTouchListener() {
            private float downX;
            private float downY;
            private int startX;
            private int startY;
            private boolean moved;

            @Override public boolean onTouch(View view, android.view.MotionEvent event) {
                if (panelParams == null) return false;
                if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                    downX = event.getRawX();
                    downY = event.getRawY();
                    startX = panelParams.x;
                    startY = panelParams.y;
                    moved = false;
                    return true;
                }
                if (event.getAction() == android.view.MotionEvent.ACTION_MOVE) {
                    float dx = event.getRawX() - downX;
                    float dy = event.getRawY() - downY;
                    if (!moved && Math.hypot(dx, dy) >= dp(8)) moved = true;
                    if (moved) {
                        panelParams.x = startX + Math.round(dx);
                        panelParams.y = startY + Math.round(dy);
                        try { windowManager.updateViewLayout(panel, panelParams); }
                        catch (IllegalArgumentException ignored) { }
                    }
                    return true;
                }
                if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                    if (toggleDiagnosticsOnTap && !moved) setDiagnosticsExpanded(!diagnosticsExpanded);
                    return true;
                }
                return event.getAction() == android.view.MotionEvent.ACTION_CANCEL;
            }
        };
    }

    private void setDiagnosticsExpanded(boolean expanded) {
        diagnosticsExpanded = expanded;
        if (expanded) refreshTimerPanel();
        updatePanelContentVisibility();
    }

    private TextView smallInfoText(String initialText) {
        TextView view = new TextView(this);
        view.setText(initialText);
        view.setTextSize(9);
        view.setTextColor(0xFFE0CDD0);
        view.setIncludeFontPadding(false);
        view.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        view.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return view;
    }

    private String readVersionName() {
        try {
            String name = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            return "v" + (name == null ? "?" : name);
        } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {
            return "v?";
        }
    }

    private void startAutoPlay() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Toast.makeText(this, "Автогра потребує Android 11 або новішу версію", Toast.LENGTH_LONG).show();
            return;
        }
        if (timerMode) setTimerMode(false);
        if (autoPlaying) return;
        int currentRun = ++runGeneration;
        autoPlaying = true;
        screenshotPending = false;
        moveCount = 0;
        previousBoard = null;
        stableCandidateBoard = null;
        previousMergeBoard = null;
        stableCandidateMergeBoard = null;
        stableCandidateMergeGeometry = null;
        stableCandidateReads = 0;
        stabilizationReads = 0;
        unchangedSwipeRetries = 0;
        lastSwipeCompletedAtMs = 0L;
        lastRecognitionError = null;
        lastChoice = null;
        lastGeometry = null;
        lastMergeMove = null;
        lastMergeGeometry = null;
        awaitingMergeTransition = false;
        mergeSpawnModel.reset();
        noMoveReads = 0;
        clearBoardPreview();
        updatePanel("АНАЛІЗ ПОЛЯ");
        updateDiagnosticMessage("Очікую на стабільне поле…");
        handler.postDelayed(() -> readBoardAndMove(currentRun), MIN_POST_SWIPE_SETTLE_MS);
    }

    private void clearBoardPreview() {
        if (boardCells != null) {
            for (int row = 0; row < GameBoard.SIDE; row++) {
                for (int column = 0; column < GameBoard.SIDE; column++) {
                    TextView cell = boardCells[row][column];
                    cell.setText("·");
                    cell.setTextColor(0xFF937D80);
                    cell.setBackground(round(EMPTY_CELL, 5));
                }
            }
        }
        if (mergeBoardCells != null) {
            for (int row = 0; row < MergeMadnessBoard.SIDE; row++) {
                for (int column = 0; column < MergeMadnessBoard.SIDE; column++) {
                    TextView cell = mergeBoardCells[row][column];
                    cell.setText("·");
                    cell.setTextColor(0xFF937D80);
                    cell.setBackground(round(EMPTY_CELL, 4));
                }
            }
        }
        if (activeMode == BotMode.MERGE_MADNESS) {
            if (boardInfoView != null) boardInfoView.setText("Чекаю на поле 4×4");
            if (scoreView != null) scoreView.setText("Шукаю найкращий напрямок");
            if (mergeDetailsView != null) mergeDetailsView.setText("Зсув поля · ланцюжок · впевненість");
        } else {
            if (boardInfoView != null) boardInfoView.setText("Чекаю на поле 8×8");
            if (scoreView != null) scoreView.setText("Шукаю сусідні заміни");
        }
    }

    private void readBoardAndMove(int currentRun) {
        if (!isCurrentRun(currentRun) || screenshotPending) return;
        long elapsedSinceScreenshot = SystemClock.uptimeMillis() - lastSuccessfulScreenshotAtMs;
        long screenshotWait = MIN_SCREENSHOT_INTERVAL_MS - elapsedSinceScreenshot;
        if (lastSuccessfulScreenshotAtMs > 0L && screenshotWait > 0L) {
            handler.postDelayed(() -> readBoardAndMove(currentRun), screenshotWait);
            return;
        }
        screenshotPending = true;
        takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
            @Override public void onSuccess(ScreenshotResult screenshot) {
                if (!isCurrentRun(currentRun)) return;
                lastSuccessfulScreenshotAtMs = SystemClock.uptimeMillis();
                Bitmap bitmap = copyScreenshot(screenshot);
                if (bitmap == null) {
                    screenshotPending = false;
                    stopAutoPlay("Не вдалося прочитати екран");
                    return;
                }
                BoardVision.Geometry geometryAnchor = activeMode == BotMode.MERGE_MADNESS
                        ? lastMergeGeometry : null;
                BoardVision.Geometry calibratedGeometry = activeMode == BotMode.MERGE_MADNESS
                        ? MergeMadnessCalibration.boardGeometry(TapAccessibilityService.this,
                        bitmap.getWidth(), bitmap.getHeight()) : null;
                solverExecutor.execute(() -> {
                    if (activeMode == BotMode.MERGE_MADNESS) {
                        MergeMadnessVision.Result result = mergeMadnessVision == null
                                ? MergeMadnessVision.Result.error("Розпізнавач 2048 не готовий.")
                                : mergeMadnessVision.recognize(bitmap, geometryAnchor,
                                calibratedGeometry);
                        bitmap.recycle();
                        handler.post(() -> handleMergeBoardRead(currentRun, result));
                        return;
                    }
                    BoardVision.Result result = boardVision.recognize(bitmap);
                    bitmap.recycle();
                    handler.post(() -> handleBoardRead(currentRun, result));
                });
            }

            @Override public void onFailure(int errorCode) {
                if (!isCurrentRun(currentRun)) return;
                screenshotPending = false;
                if (errorCode == ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT) {
                    long elapsed = SystemClock.uptimeMillis() - lastSuccessfulScreenshotAtMs;
                    long wait = Math.max(100L, MIN_SCREENSHOT_INTERVAL_MS - elapsed + 25L);
                    handler.postDelayed(() -> readBoardAndMove(currentRun), wait);
                    return;
                }
                stopAutoPlay("Android не дозволив зняти екран (" + errorCode + ")");
            }
        });
    }

    private Bitmap copyScreenshot(ScreenshotResult screenshot) {
        HardwareBuffer buffer = screenshot.getHardwareBuffer();
        if (buffer == null) return null;
        Bitmap hardwareBitmap = null;
        try {
            hardwareBitmap = Bitmap.wrapHardwareBuffer(buffer, screenshot.getColorSpace());
            if (hardwareBitmap == null) return null;
            return hardwareBitmap.copy(Bitmap.Config.ARGB_8888, false);
        } catch (RuntimeException error) {
            return null;
        } finally {
            if (hardwareBitmap != null) hardwareBitmap.recycle();
            buffer.close();
        }
    }

    private void handleBoardRead(int currentRun, BoardVision.Result result) {
        if (!isCurrentRun(currentRun)) return;
        screenshotPending = false;
        if (!result.isSuccess()) {
            lastRecognitionError = result.error;
            updateDiagnosticMessage("КАДР НЕЧІТКИЙ · " + stabilizationReads + "/"
                    + MAX_STABILIZATION_READS + " · " + result.error);
            scheduleStabilityRead(currentRun);
            return;
        }

        lastRecognitionError = null;
        if (lastSwipeCompletedAtMs > 0L && previousBoard != null
                && !result.board.equals(previousBoard) && !result.board.hasAnyMatch()
                && result.similarity >= 0.60) {
            stableCandidateBoard = result.board;
            stableCandidateReads = STABLE_FRAME_COUNT;
            stabilizationReads = 0;
            updateDiagnostic(result.board, result.similarity, "ПОЛЕ ЗМІНИЛОСЯ");
            chooseMoveForStableBoard(currentRun, result);
            return;
        }
        if (result.board.equals(stableCandidateBoard)) stableCandidateReads++;
        else {
            stableCandidateBoard = result.board;
            stableCandidateReads = 1;
        }
        String phase = stableCandidateReads >= STABLE_FRAME_COUNT ? "СТАБІЛЬНЕ" : "ПЕРЕВІРКА";
        updateDiagnostic(result.board, result.similarity, phase);
        if (stableCandidateReads < STABLE_FRAME_COUNT) {
            scheduleStabilityRead(currentRun);
            return;
        }

        stabilizationReads = 0;
        chooseMoveForStableBoard(currentRun, result);
    }

    private void handleMergeBoardRead(int currentRun, MergeMadnessVision.Result result) {
        if (!isCurrentRun(currentRun)) return;
        screenshotPending = false;
        if (!result.isSuccess()) {
            lastRecognitionError = result.error;
            if (result.geometry != null && result.partialRanks != null
                    && result.recognizedCells != null) {
                updateMergePartialDiagnostic(result);
            } else {
                clearMergeDiagnostic("Кадр не розпізнано");
            }
            updateDiagnosticMessage("КАДР НЕЧІТКИЙ · " + stabilizationReads + "/"
                    + MAX_STABILIZATION_READS + " · " + result.error);
            scheduleStabilityRead(currentRun);
            return;
        }

        lastRecognitionError = null;
        MergeMadnessTransition transition = null;
        if (awaitingMergeTransition && previousMergeBoard != null && lastMergeMove != null
                && !result.board.equals(previousMergeBoard)) {
            transition = MergeMadnessTransition.analyze(previousMergeBoard,
                    lastMergeMove.direction, result.board);
        }
        boolean verifiedMove = MergeMadnessTurnPolicy.canAdvance(transition,
                result.similarity, lastMergeGeometry, result.geometry,
                SystemClock.uptimeMillis() - lastSwipeCompletedAtMs);
        if (verifiedMove) {
            stableCandidateMergeBoard = result.board;
            stableCandidateMergeGeometry = result.geometry;
            stableCandidateReads = STABLE_FRAME_COUNT;
        } else if (result.board.equals(stableCandidateMergeBoard)
                && MergeMadnessLayout.isStable(stableCandidateMergeGeometry, result.geometry)) {
            stableCandidateReads++;
        } else {
            stableCandidateMergeBoard = result.board;
            stableCandidateMergeGeometry = result.geometry;
            stableCandidateReads = 1;
        }
        String phase = verifiedMove ? "ХІД ПІДТВЕРДЖЕНО"
                : stableCandidateReads >= STABLE_FRAME_COUNT ? "СТАБІЛЬНЕ" : "ПЕРЕВІРКА";
        updateMergeDiagnostic(result, phase);
        if (stableCandidateReads < STABLE_FRAME_COUNT) {
            scheduleStabilityRead(currentRun);
            return;
        }

        if (transition != null) {
            if (!transition.expected) {
                lastRecognitionError = "Зміна поля не відповідає свайпу й одній новій плитці";
                updateMergeDiagnostic(result, "ПЕРЕВІРЯЮ ПЕРЕХІД");
                updateDiagnosticMessage("Перевіряю, чи завершилося переміщення плиток…");
                scheduleStabilityRead(currentRun);
                return;
            }
            mergeSpawnModel.observeIfConfirmed(transition);
            awaitingMergeTransition = false;
        }

        stabilizationReads = 0;
        chooseMergeMoveForStableBoard(currentRun, result);
    }

    private long timeUntilNextScreenshotMs() {
        if (lastSuccessfulScreenshotAtMs <= 0L) return 0L;
        return Math.max(0L, MIN_SCREENSHOT_INTERVAL_MS
                - (SystemClock.uptimeMillis() - lastSuccessfulScreenshotAtMs));
    }

    private void scheduleStabilityRead(int currentRun) {
        stabilizationReads++;
        if (stabilizationReads >= MAX_STABILIZATION_READS) {
            String message = lastRecognitionError == null
                    ? "Поле не стабілізувалося після свайпу"
                    : "Не вдалося впевнено прочитати поле: " + lastRecognitionError;
            stopAutoPlay(message);
            return;
        }
        updatePanel("ЧЕКАЮ НА КАДР");
        handler.postDelayed(() -> readBoardAndMove(currentRun), timeUntilNextScreenshotMs());
    }

    private void chooseMoveForStableBoard(int currentRun, BoardVision.Result result) {
        if (result.board.hasAnyMatch()) {
            previousBoard = null;
            lastChoice = null;
            updateDiagnostic(result.board, result.similarity, "ЗБІГИ ОЧИЩУЮТЬСЯ");
            updateDiagnosticMessage("Чекаю, поки гра завершить анімацію…");
            handler.postDelayed(() -> readBoardAndMove(currentRun), timeUntilNextScreenshotMs());
            return;
        }

        if (result.board.equals(previousBoard)) {
            if (lastChoice == null && noMoveReads > 0) {
                noMoveReads++;
                if (noMoveReads <= 4) {
                    updatePanel("ШУКАЮ ХІД");
                    updateDiagnosticMessage("Немає готового збігу · чекаю перемішування");
                    handler.postDelayed(() -> readBoardAndMove(currentRun), 500L);
                } else {
                    stopAutoPlay("Не знайшов заміну, що створює збіг");
                }
                return;
            }
            long elapsedSinceSwipe = SystemClock.uptimeMillis() - lastSwipeCompletedAtMs;
            if (lastSwipeCompletedAtMs > 0L
                    && elapsedSinceSwipe < getNoChangeTimeoutMs(this)) {
                updatePanel("ОЧІКУЮ ЗМІНУ ПОЛЯ");
                updateDiagnosticMessage("Чекаю, поки гра перемістить клітинки…");
                handler.postDelayed(() -> readBoardAndMove(currentRun), timeUntilNextScreenshotMs());
                return;
            }
            if (lastChoice == null || lastGeometry == null || unchangedSwipeRetries >= 1) {
                stopAutoPlay("Обмін не змінив поле. Перевір гру та повтори запуск.");
                return;
            }

            unchangedSwipeRetries++;
            updateDiagnostic(result.board, result.similarity, "ПОВТОР ОБМІНУ");
            updatePanel("ПОВТОР ОБМІНУ");
            dispatchSwap(lastChoice, lastGeometry, currentRun, true);
            return;
        }

        unchangedSwipeRetries = 0;
        noMoveReads = 0;
        previousBoard = result.board;
        solverExecutor.execute(() -> {
            GameSolver.Choice choice = gameSolver.chooseMove(result.board);
            handler.post(() -> handleDecision(currentRun, result, choice));
        });
    }

    private void chooseMergeMoveForStableBoard(int currentRun, MergeMadnessVision.Result result) {
        if (result.board.equals(previousMergeBoard)) {
            long elapsedSinceSwipe = SystemClock.uptimeMillis() - lastSwipeCompletedAtMs;
            if (lastSwipeCompletedAtMs > 0L
                    && elapsedSinceSwipe < getNoChangeTimeoutMs(this)) {
                updatePanel("ОЧІКУЮ ЗМІНУ ПОЛЯ");
                updateDiagnosticMessage("Чекаю, поки плитки посунуться…");
                handler.postDelayed(() -> readBoardAndMove(currentRun), timeUntilNextScreenshotMs());
                return;
            }
            if (lastMergeMove == null || lastMergeGeometry == null || unchangedSwipeRetries >= 1) {
                stopAutoPlay("Свайп не змінив поле. Перевір гру та повтори запуск.");
                return;
            }

            unchangedSwipeRetries++;
            updateMergeDiagnostic(result, "ПОВТОР СВАЙПУ");
            updatePanel("ПОВТОР СВАЙПУ");
            lastMergeGeometry = result.geometry;
            dispatchMergeSwipe(lastMergeMove, result.geometry, currentRun, true);
            return;
        }

        unchangedSwipeRetries = 0;
        previousMergeBoard = result.board;
        solverExecutor.execute(() -> {
            MergeMadnessSolver.Move move = mergeMadnessSolver.chooseMove(
                    result.board, mergeSpawnModel);
            handler.post(() -> handleMergeDecision(currentRun, result, move));
        });
    }

    private void handleDecision(int currentRun, BoardVision.Result result, GameSolver.Choice choice) {
        if (!isCurrentRun(currentRun)) return;
        if (choice.waitingForResolution) {
            previousBoard = null;
            lastChoice = null;
            updateDiagnostic(result.board, result.similarity, "ЗБІГИ ОЧИЩУЮТЬСЯ");
            updateDiagnosticMessage("На полі ще " + choice.unresolvedMatches + " самоцвітів у збігах");
            handler.postDelayed(() -> readBoardAndMove(currentRun), timeUntilNextScreenshotMs());
            return;
        }
        if (choice.firstCell < 0) {
            previousBoard = result.board;
            lastChoice = null;
            lastGeometry = null;
            noMoveReads++;
            if (noMoveReads <= 4) {
                updatePanel("ШУКАЮ ХІД");
                updateDiagnosticMessage("Немає готового збігу · чекаю перемішування");
                handler.postDelayed(() -> readBoardAndMove(currentRun), 500L);
            } else {
                stopAutoPlay("Не знайшов заміну, що створює збіг");
            }
            return;
        }

        updateDiagnostic(result.board, result.similarity, "НАСТУПНИЙ ОБМІН");
        if (messageView != null) {
            int firstRow = choice.firstCell / GameBoard.SIDE + 1;
            int firstColumn = choice.firstCell % GameBoard.SIDE + 1;
            int secondRow = choice.secondCell / GameBoard.SIDE + 1;
            int secondColumn = choice.secondCell % GameBoard.SIDE + 1;
            String action = choice.specialType == 0
                    ? "збіг " + choice.matchedCount
                    : "активація " + specialName(choice.specialType) + " · ~" + choice.matchedCount;
            messageView.setText("Обмін " + firstRow + "," + firstColumn + " ↔ "
                    + secondRow + "," + secondColumn + " · " + action
                    + "\nОцінка " + Math.round(choice.score)
                    + (choice.cascadeCount > 0 ? " · каскади " + choice.cascadeCount : ""));
        }
        appendChoiceSummary(choice);
        lastChoice = choice;
        lastGeometry = result.geometry;
        updatePanel((choice.specialType == 0 ? "ХІД " : "АКТИВАЦІЯ ")
                + (moveCount + 1) + " · " + choice.matchedCount);
        dispatchSwap(choice, result.geometry, currentRun, false);
    }

    private void handleMergeDecision(int currentRun, MergeMadnessVision.Result result,
                                     MergeMadnessSolver.Move move) {
        if (!isCurrentRun(currentRun)) return;
        if (move == null) {
            updateMergeDiagnostic(result, "НЕМАЄ ДОСТУПНОГО ХОДУ");
            stopAutoPlay("На полі більше немає доступних свайпів.");
            return;
        }

        updateMergeDiagnostic(result, "НАСТУПНИЙ СВАЙП");
        if (messageView != null) {
            messageView.setText("Свайп " + move.direction.label + " · злиттів " + move.mergedCount
                    + "\nРозрахункова оцінка " + Math.round(move.value));
        }
        if (scoreView != null) {
            scoreView.setText("Глибина " + move.searchDepth + "/"
                    + MergeMadnessSolver.MAX_SEARCH_DEPTH + " · " + move.elapsedMs + " мс");
        }
        lastMergeMove = move;
        lastMergeGeometry = result.geometry;
        updatePanel("СВАЙП " + move.direction.label.toUpperCase(java.util.Locale.ROOT));
        dispatchMergeSwipe(move, result.geometry, currentRun, false);
    }

    private String specialName(int specialType) {
        if (specialType == GameBoard.ROCKET) return "ракети";
        if (specialType == GameBoard.BOMB) return "бомби";
        if (specialType == GameBoard.PORTAL) return "вихору";
        return "спецфішки";
    }

    private void appendChoiceSummary(GameSolver.Choice choice) {
        if (scoreView == null) return;
        scoreView.setText("Збіг " + choice.matchedCount + " · оцінка "
                + Math.round(choice.score) + " · варіантів " + choice.candidateCount);
    }

    private void dispatchSwap(GameSolver.Choice choice, BoardVision.Geometry geometry,
                              int currentRun, boolean retry) {
        if (choice == null || geometry == null) {
            stopAutoPlay("Немає координат для обміну");
            return;
        }
        int firstRow = choice.firstCell / GameBoard.SIDE;
        int firstColumn = choice.firstCell % GameBoard.SIDE;
        int secondRow = choice.secondCell / GameBoard.SIDE;
        int secondColumn = choice.secondCell % GameBoard.SIDE;
        float startX = geometry.centerX(firstColumn);
        float startY = geometry.centerY(firstRow);
        float endX = geometry.centerX(secondColumn);
        float endY = geometry.centerY(secondRow);
        dispatchSwipeGesture(startX, startY, endX, endY, currentRun, retry);
    }

    private void dispatchMergeSwipe(MergeMadnessSolver.Move move, BoardVision.Geometry geometry,
                                    int currentRun, boolean retry) {
        if (move == null || geometry == null) {
            stopAutoPlay("Немає координат для свайпу");
            return;
        }
        float startX;
        float startY;
        float endX;
        float endY;
        switch (move.direction) {
            case LEFT:
                startX = geometry.centerX(3);
                startY = geometry.centerY(1);
                endX = geometry.centerX(1);
                endY = startY;
                break;
            case RIGHT:
                startX = geometry.centerX(0);
                startY = geometry.centerY(2);
                endX = geometry.centerX(2);
                endY = startY;
                break;
            case UP:
                startX = geometry.centerX(1);
                startY = geometry.centerY(3);
                endX = startX;
                endY = geometry.centerY(1);
                break;
            case DOWN:
            default:
                startX = geometry.centerX(2);
                startY = geometry.centerY(0);
                endX = startX;
                endY = geometry.centerY(2);
                break;
        }
        dispatchSwipeGesture(startX, startY, endX, endY, currentRun, retry);
    }

    private void dispatchSwipeGesture(float startX, float startY, float endX, float endY,
                                      int currentRun, boolean retry) {
        long durationMs = getSwipeDurationMs(this);
        if (retry) durationMs = Math.min(MAX_SWIPE_DURATION_MS, durationMs + 60L);

        Path path = new Path();
        path.moveTo(startX, startY);
        path.lineTo(endX, endY);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0L, durationMs))
                .build();
        boolean accepted = dispatchGesture(gesture, new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription completedGesture) {
                if (!isCurrentRun(currentRun)) return;
                moveCount++;
                lastSwipeCompletedAtMs = SystemClock.uptimeMillis();
                stableCandidateBoard = null;
                stableCandidateMergeBoard = null;
                stableCandidateMergeGeometry = null;
                stableCandidateReads = 0;
                stabilizationReads = 0;
                lastRecognitionError = null;
                if (activeMode == BotMode.MERGE_MADNESS) awaitingMergeTransition = true;
                updatePanel("ЧЕКАЮ НА КАДР");
                long settleDelay = activeMode == BotMode.MERGE_MADNESS
                        ? MergeMadnessTurnPolicy.MIN_SETTLE_MS : MIN_POST_SWIPE_SETTLE_MS;
                long scanDelay = Math.max(settleDelay,
                        timeUntilNextScreenshotMs());
                handler.postDelayed(() -> readBoardAndMove(currentRun), scanDelay);
            }

            @Override public void onCancelled(GestureDescription cancelledGesture) {
                if (isCurrentRun(currentRun)) stopAutoPlay("Свайп не виконано");
            }
        }, handler);
        if (!accepted && isCurrentRun(currentRun)) stopAutoPlay("Android відхилив свайп");
    }

    private boolean isCurrentRun(int currentRun) {
        return autoPlaying && currentRun == runGeneration;
    }

    private void updateDiagnostic(GameBoard board, double similarity, String phase) {
        if (boardCells == null) return;
        int occupied = 0;
        for (int row = 0; row < GameBoard.SIDE; row++) {
            for (int column = 0; column < GameBoard.SIDE; column++) {
                int color = board.get(row, column);
                TextView cell = boardCells[row][column];
                if (color == 0) {
                    cell.setText("·");
                    cell.setTextColor(0xFF937D80);
                    cell.setBackground(round(EMPTY_CELL, 5));
                } else {
                    occupied++;
                    cell.setText(GEM_LABELS[color]);
                    cell.setTextColor(CREAM);
                    cell.setBackground(round(GEM_COLORS[color], 5));
                }
            }
        }
        if (boardInfoView != null) {
            boardInfoView.setText(occupied + "/64 · розпізнано до "
                    + Math.round(similarity * 100) + "%");
        }
        if (messageView != null) {
            messageView.setText(phase);
        }
    }

    private void updateMergeDiagnostic(MergeMadnessVision.Result result, String phase) {
        if (mergeBoardCells == null) return;
        MergeMadnessBoard board = result.board;
        int occupied = 0;
        int maxRank = 0;
        for (int row = 0; row < MergeMadnessBoard.SIDE; row++) {
            for (int column = 0; column < MergeMadnessBoard.SIDE; column++) {
                int rank = board.get(row, column);
                TextView cell = mergeBoardCells[row][column];
                if (rank == 0) {
                    cell.setText("·");
                    cell.setTextColor(0xFF937D80);
                    cell.setBackground(round(EMPTY_CELL, 4));
                } else {
                    occupied++;
                    maxRank = Math.max(maxRank, rank);
                    cell.setText(Integer.toString(rank));
                    cell.setTextColor(CREAM);
                    cell.setBackground(round(mergeRankColor(rank), 4));
                }
            }
        }
        if (boardInfoView != null) {
            boardInfoView.setText(occupied + "/16 плиток · макс. рівень " + maxRank
                    + " · " + Math.round(result.similarity * 100) + "%");
        }
        if (mergeDetailsView != null) {
            mergeDetailsView.setText("Підказки: " + result.unlockedProgressionRanks + " відкрито"
                    + (result.nextLockedRank > 0 ? " · наступний " + result.nextLockedRank : "")
                    + "\nПоле Y=" + Math.round(result.geometry.firstCenterY)
                    + " px · крок " + Math.round(result.geometry.cellStepPx) + " px");
        }
        if (messageView != null) messageView.setText(phase);
    }

    private void updateMergePartialDiagnostic(MergeMadnessVision.Result result) {
        if (mergeBoardCells == null) return;
        int known = 0;
        int occupied = 0;
        int maxRank = 0;
        for (int row = 0; row < MergeMadnessBoard.SIDE; row++) {
            for (int column = 0; column < MergeMadnessBoard.SIDE; column++) {
                int index = row * MergeMadnessBoard.SIDE + column;
                TextView cell = mergeBoardCells[row][column];
                if (!result.recognizedCells[index]) {
                    cell.setText("?");
                    cell.setTextColor(0xFFFFD4BA);
                    cell.setBackground(round(0xFF49343A, 4));
                    continue;
                }
                known++;
                int rank = result.partialRanks[index];
                if (rank == 0) {
                    cell.setText("·");
                    cell.setTextColor(0xFF937D80);
                    cell.setBackground(round(EMPTY_CELL, 4));
                } else {
                    occupied++;
                    maxRank = Math.max(maxRank, rank);
                    cell.setText(Integer.toString(rank));
                    cell.setTextColor(CREAM);
                    cell.setBackground(round(mergeRankColor(rank), 4));
                }
            }
        }
        if (boardInfoView != null) {
            boardInfoView.setText(known + "/16 клітинок · прочитано " + occupied
                    + " мишей · макс. рівень " + maxRank);
        }
        if (mergeDetailsView != null) {
            mergeDetailsView.setText("Поле Y=" + Math.round(result.geometry.firstCenterY)
                    + " px · крок " + Math.round(result.geometry.cellStepPx)
                    + " px · ланцюжок "
                    + result.progressionRows + " ряди, " + result.progressionRanks
                    + " рівнів\nНевизначені клітинки позначені «?»");
        }
        if (scoreView != null) scoreView.setText("Очікую на чіткий кадр 4×4");
    }

    private void clearMergeDiagnostic(String status) {
        if (mergeBoardCells != null) {
            for (TextView[] row : mergeBoardCells) {
                for (TextView cell : row) {
                    cell.setText("?");
                    cell.setTextColor(0xFFFFD4BA);
                    cell.setBackground(round(0xFF49343A, 4));
                }
            }
        }
        if (boardInfoView != null) boardInfoView.setText(status + " · дані поля невідомі");
        if (scoreView != null) scoreView.setText("Очікую на чіткий кадр 4×4");
        if (mergeDetailsView != null) mergeDetailsView.setText("Зсув поля невідомий · ланцюжок нечиткий");
    }

    private int mergeRankColor(int rank) {
        switch (rank) {
            case 1: return 0xFF785B51;
            case 2: return 0xFF49464A;
            case 3: return 0xFF686B70;
            case 4: return 0xFF76645F;
            case 5: return 0xFF568E53;
            case 6: return 0xFF638344;
            case 7: return 0xFFB18B42;
            case 8: return 0xFF914F50;
            default:
                int shade = Math.min(100, 57 + rank * 2);
                return Color.rgb(shade, 39, Math.min(100, shade + 18));
        }
    }

    private void updateDiagnosticMessage(String message) {
        if (messageView != null) messageView.setText(message);
    }

    private void stopAutoPlay(String message) {
        autoPlaying = false;
        runGeneration++;
        screenshotPending = false;
        updatePanel(message);
        updateDiagnosticMessage("Зупинено".equals(message) ? "Автогру зупинено" : message);
        if (panel != null && !"Зупинено".equals(message)) Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void updatePanel() {
        if (autoPlaying) updatePanel("АВТО · ХІД " + moveCount);
        else updatePanel(activeMode.panelTitle + " · ГОТОВИЙ");
    }

    private void updatePanel(String label) {
        if (stateChip != null) stateChip.setText(label);
        if (playButton != null) playButton.setText(autoPlaying ? "●\nПрацює" : "▶\nСтарт");
    }

    private void closePanel() {
        handler.removeCallbacks(timerTicker);
        stopAutoPlay("Зупинено");
        if (panel != null && windowManager != null) {
            try { windowManager.removeView(panel); } catch (IllegalArgumentException ignored) { }
        }
        panel = null;
        panelParams = null;
        panelTitle = null;
        stateChip = null;
        playButton = null;
        boardInfoView = null;
        messageView = null;
        scoreView = null;
        mergeDetailsView = null;
        boardCells = null;
        mergeBoardCells = null;
        blitzBoardGrid = null;
        mergeBoardGrid = null;
        diagnosticsPanel = null;
        timerPanel = null;
        timerRowsContainer = null;
        compactTimerPreview = null;
        compactTimerTitle = null;
        compactTimerCountdown = null;
        compactTimerRestart = null;
        compactTimerSelectedId = null;
        timerRows.clear();
        renderedTimerRevision = -1L;
        timerMode = false;
        diagnosticsExpanded = false;
    }

    private TextView button(String label, int color, int sizeSp) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextColor(color);
        view.setTextSize(sizeSp);
        view.setGravity(Gravity.CENTER);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setIncludeFontPadding(false);
        view.setClickable(true);
        view.setFocusable(true);
        view.setPadding(dp(5), 0, dp(5), 0);
        return view;
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private LinearLayout.LayoutParams stackedControlParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(44));
        params.setMargins(0, dp(2), 0, dp(2));
        return params;
    }

    private int dp(float value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
}
