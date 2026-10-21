package ua.borlock.autotap;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.drawable.GradientDrawable;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Runs the board bot and hosts an independent reward timer window. */
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
    private static final float COMPACT_CONTROL_HEIGHT_DP = 48f * 0.60f;
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
    private TimerOverlay timerOverlay;
    private LinearLayout panel;
    private LinearLayout panelBody;
    private TextView panelIcon;
    private TextView bubbleButton;
    private boolean collapsedToBubble;
    private boolean restorePlacementPending;
    private String panelProfile = BotMode.BACKPACK_BLITZ.id;
    private String panelOrientation = "portrait";
    private Rect panelArea = new Rect();
    private WindowManager.LayoutParams panelParams;
    private TextView panelTitle;
    private TextView stateChip;
    private TextView playButton;
    private TextView detailsToggle;
    private TextView boardInfoView;
    private TextView messageView;
    private TextView scoreView;
    private TextView mergeDetailsView;
    private TextView[][] boardCells;
    private final int[] displayedBlitzColors = new int[GameBoard.CELL_COUNT];
    private TextView[][] mergeBoardCells;
    private LinearLayout blitzBoardGrid;
    private LinearLayout mergeBoardGrid;
    private LinearLayout diagnosticsPanel;
    private boolean diagnosticsExpanded;
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
    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        timerOverlay = new TimerOverlay(this);
        mergeMadnessVision = new MergeMadnessVision(this);
        TimerStore.rescheduleAll(this);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }

    @Override public void onConfigurationChanged(Configuration configuration) {
        savePanelPlacement();
        super.onConfigurationChanged(configuration);
        restorePanelPlacement();
        if (timerOverlay != null) timerOverlay.onConfigurationChanged();
    }

    @Override public void onInterrupt() { stopAutoPlay("Призупинено Android"); }

    @Override public boolean onUnbind(android.content.Intent intent) {
        closePanel();
        if (timerOverlay != null) timerOverlay.close();
        if (instance == this) instance = null;
        return super.onUnbind(intent);
    }

    @Override public void onDestroy() {
        closePanel();
        if (timerOverlay != null) timerOverlay.close();
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
        return instance != null && instance.timerOverlay != null && instance.timerOverlay.show();
    }

    static void minimizeTimerPanel() {
        if (instance != null && instance.timerOverlay != null) instance.timerOverlay.minimize();
    }

    static void refreshModulePanel(BotMode mode) {
        if (instance != null && instance.activeMode == mode) {
            instance.savePanelPlacement();
            instance.restorePanelPlacement();
        }
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
            return true;
        }
        panel = createPanel();
        panelParams = new WindowManager.LayoutParams(
                dp(ClickerPreferences.panelWidthDp(this, panelProfile)),
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        panelParams.gravity = Gravity.TOP | Gravity.START;
        try {
            windowManager.addView(panel, panelParams);
            restorePanelPlacement();
            updatePanel();
            return true;
        } catch (WindowManager.BadTokenException | IllegalStateException error) {
            panel = null;
            return false;
        }
    }

    private void selectBot(BotMode mode) {
        if (mode == null) mode = BotMode.BACKPACK_BLITZ;
        savePanelPlacement();
        if (autoPlaying && activeMode != mode) stopAutoPlay("Зупинено");
        activeMode = mode;
        panelProfile = mode.id;
        collapsedToBubble = false;
        diagnosticsExpanded = ClickerPreferences.diagnosticsExpanded(this, panelProfile);
        updateBotModeViews();
        updatePanelContentVisibility();
        restorePanelPlacement();
    }

    private void updateBotModeViews() {
        ClickerModule module = ClickerCatalog.get(activeMode);
        if (panelTitle != null) panelTitle.setText(module.name);
        if (panelIcon != null) {
            panelIcon.setText(module.icon);
            panelIcon.setTextColor(module.theme.accent);
        }
        if (bubbleButton != null) {
            bubbleButton.setText(bubbleLabel(module.icon));
            bubbleButton.setContentDescription("Розгорнути панель: " + (module.name));
        }
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
            playButton.setContentDescription((autoPlaying ? "Зупинити" : "Запустити")
                    + " бота «" + module.name + "»");
            playButton.setVisibility(View.VISIBLE);
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

        panelBody = new LinearLayout(this);
        panelBody.setOrientation(LinearLayout.VERTICAL);
        root.addView(panelBody, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        panelIcon = button(ClickerCatalog.get(activeMode).icon, CREAM, 18);
        panelIcon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        panelIcon.setClickable(false);
        panelIcon.setFocusable(false);
        titleRow.addView(panelIcon, new LinearLayout.LayoutParams(dp(24), dp(48)));
        LinearLayout dragHandle = new LinearLayout(this);
        dragHandle.setOrientation(LinearLayout.VERTICAL);
        dragHandle.setGravity(Gravity.CENTER_VERTICAL);
        dragHandle.setMinimumHeight(dp(48));
        dragHandle.setOnTouchListener(createDragListener(false));
        panelTitle = button(ClickerCatalog.get(activeMode).name, CREAM, 12);
        panelTitle.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        panelTitle.setMaxLines(2);
        panelTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        panelTitle.setClickable(false);
        panelTitle.setFocusable(false);
        dragHandle.addView(panelTitle, new LinearLayout.LayoutParams(-1, -2));
        TextView dragHint = smallInfoText("⠿ Перетягнути");
        dragHint.setPadding(dp(5), dp(2), 0, 0);
        dragHint.setSingleLine(true);
        dragHint.setTextColor(0xFFD4B7BF);
        dragHandle.addView(dragHint, new LinearLayout.LayoutParams(-1, -2));
        titleRow.addView(dragHandle, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView minimize = button("В іконку", CREAM, 10);
        minimize.setMinHeight(dp(COMPACT_CONTROL_HEIGHT_DP));
        minimize.setContentDescription("Згорнути панель до іконки; бот продовжить роботу");
        minimize.setBackground(round(0xFF49353C, 10));
        minimize.setOnClickListener(view -> setCollapsedToBubble(true));
        titleRow.addView(minimize, new LinearLayout.LayoutParams(dp(68), -2));
        TextView close = button("×", CREAM, 30);
        close.setMinHeight(dp(48));
        close.setContentDescription("Зупинити бота й закрити панель");
        close.setOnClickListener(view -> closePanel());
        titleRow.addView(close, new LinearLayout.LayoutParams(dp(48), -2));
        panelBody.addView(titleRow, new LinearLayout.LayoutParams(-1, -2));

        stateChip = button("Готовий", CREAM, 10);
        stateChip.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        stateChip.setBackground(round(0xFF49353C, 10));
        stateChip.setSingleLine(true);
        stateChip.setEllipsize(android.text.TextUtils.TruncateAt.END);
        stateChip.setClickable(false);
        stateChip.setFocusable(false);
        LinearLayout.LayoutParams stateParams = new LinearLayout.LayoutParams(-1, dp(22));
        stateParams.setMargins(0, dp(3), 0, dp(4));
        panelBody.addView(stateChip, stateParams);

        LinearLayout controlRow = new LinearLayout(this);
        controlRow.setGravity(Gravity.CENTER_VERTICAL);
        detailsToggle = button("▼ Показати поле", CREAM, 11);
        detailsToggle.setMinHeight(dp(COMPACT_CONTROL_HEIGHT_DP));
        detailsToggle.setBackground(round(0xFF49353C, 10));
        detailsToggle.setOnClickListener(view -> setDiagnosticsExpanded(!diagnosticsExpanded));
        controlRow.addView(detailsToggle, new LinearLayout.LayoutParams(0, -2, 1f));
        playButton = button("▶ Старт", CREAM, 11);
        playButton.setMinHeight(dp(COMPACT_CONTROL_HEIGHT_DP));
        playButton.setBackground(round(0xFF526A4D, 10));
        playButton.setOnClickListener(view -> {
            if (autoPlaying) stopAutoPlay("Зупинено");
            else startAutoPlay();
        });
        LinearLayout.LayoutParams playParams = new LinearLayout.LayoutParams(dp(84), -2);
        playParams.leftMargin = dp(5);
        controlRow.addView(playButton, playParams);
        panelBody.addView(controlRow, new LinearLayout.LayoutParams(-1, -2));

        diagnosticsPanel = new LinearLayout(this);
        diagnosticsPanel.setOrientation(LinearLayout.VERTICAL);
        diagnosticsPanel.setPadding(0, dp(5), 0, 0);

        boardCells = new TextView[GameBoard.SIDE][GameBoard.SIDE];
        Arrays.fill(displayedBlitzColors, -1);
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
        panelBody.addView(diagnosticsPanel, new LinearLayout.LayoutParams(-1, -2));

        bubbleButton = button("", CREAM, 10);
        bubbleButton.setText(bubbleLabel(ClickerCatalog.get(activeMode).icon));
        bubbleButton.setMinHeight(dp(48));
        bubbleButton.setMaxLines(2);
        bubbleButton.setPadding(dp(4), dp(2), dp(4), dp(2));
        bubbleButton.setOnClickListener(view -> setCollapsedToBubble(false));
        bubbleButton.setOnTouchListener(createDragListener(true));
        bubbleButton.setVisibility(View.GONE);
        root.addView(bubbleButton, new LinearLayout.LayoutParams(-1, -2));
        root.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            if (panel != root || panelParams == null) return;
            if (restorePlacementPending) {
                restorePlacementPending = false;
                ClickerPreferences.Position position = ClickerPreferences.position(
                        TapAccessibilityService.this, panelProfile, panelOrientation);
                panelParams.x = ClickerPreferences.coordinate(position.x, panelArea.left,
                        panelArea.width(), root.getWidth());
                panelParams.y = ClickerPreferences.coordinate(position.y, panelArea.top,
                        panelArea.height(), root.getHeight());
                updatePanelWindow();
            } else {
                clampPanelPlacement();
            }
        });
        updateBotModeViews();
        updatePanelContentVisibility();
        return root;
    }

    private void updatePanelContentVisibility() {
        if (detailsToggle != null) {
            String label = (diagnosticsExpanded ? "▲ Сховати " : "▼ Показати ")
                    + "поле";
            detailsToggle.setText(label);
            detailsToggle.setContentDescription(label);
            if (Build.VERSION.SDK_INT >= 30) {
                detailsToggle.setStateDescription(diagnosticsExpanded ? "Показано" : "Сховано");
            }
        }
        if (panelBody != null) panelBody.setVisibility(collapsedToBubble ? View.GONE : View.VISIBLE);
        if (bubbleButton != null) bubbleButton.setVisibility(collapsedToBubble ? View.VISIBLE : View.GONE);
        if (diagnosticsPanel != null) {
            diagnosticsPanel.setVisibility(diagnosticsExpanded ? View.VISIBLE : View.GONE);
        }
        if (panel != null) panel.requestLayout();
    }

    private void setCollapsedToBubble(boolean collapsed) {
        savePanelPlacement();
        collapsedToBubble = collapsed;
        updatePanelContentVisibility();
        updatePanelAppearance();
        restorePanelPlacement();
    }

    private Rect availablePanelArea() {
        if (Build.VERSION.SDK_INT >= 30) {
            android.view.WindowMetrics metrics = windowManager.getCurrentWindowMetrics();
            Rect bounds = metrics.getBounds();
            android.graphics.Insets insets = metrics.getWindowInsets().getInsetsIgnoringVisibility(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            return new Rect(insets.left, insets.top,
                    Math.max(insets.left + 1, bounds.width() - insets.right),
                    Math.max(insets.top + 1, bounds.height() - insets.bottom));
        }
        Point size = new Point();
        windowManager.getDefaultDisplay().getRealSize(size);
        int statusId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        int status = statusId == 0 ? 0 : getResources().getDimensionPixelSize(statusId);
        int navigationId = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        int navigation = navigationId == 0 ? 0 : getResources().getDimensionPixelSize(navigationId);
        return new Rect(0, status, Math.max(1, size.x), Math.max(status + 1, size.y - navigation));
    }

    private void restorePanelPlacement() {
        if (panel == null || panelParams == null || windowManager == null) return;
        panelArea = availablePanelArea();
        panelOrientation = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE ? "landscape" : "portrait";
        int width = collapsedToBubble ? dp(78 * Math.max(1f, getResources().getConfiguration().fontScale))
                : dp(ClickerPreferences.panelWidthDp(this, panelProfile));
        panelParams.width = Math.min(panelArea.width(), width);
        restorePlacementPending = true;
        updatePanelWindow();
        panel.requestLayout();
    }

    private void savePanelPlacement() {
        if (panel == null || panelParams == null || restorePlacementPending
                || panel.getWidth() <= 0 || panel.getHeight() <= 0 || panelArea.isEmpty()) return;
        ClickerPreferences.savePosition(this, panelProfile, panelOrientation,
                ClickerPreferences.fraction(panelParams.x, panelArea.left, panelArea.width(), panel.getWidth()),
                ClickerPreferences.fraction(panelParams.y, panelArea.top, panelArea.height(), panel.getHeight()));
    }

    private void clampPanelPlacement() {
        if (panel == null || panelParams == null || panelArea.isEmpty()) return;
        int x = Math.max(panelArea.left,
                Math.min(panelParams.x, panelArea.right - panel.getWidth()));
        int y = Math.max(panelArea.top,
                Math.min(panelParams.y, panelArea.bottom - panel.getHeight()));
        if (x != panelParams.x || y != panelParams.y) {
            panelParams.x = x;
            panelParams.y = y;
            updatePanelWindow();
        }
    }

    private void updatePanelWindow() {
        if (panel == null || panelParams == null || windowManager == null) return;
        try { windowManager.updateViewLayout(panel, panelParams); }
        catch (IllegalArgumentException ignored) { }
    }

    private View.OnTouchListener createDragListener(boolean clickOnTap) {
        return new View.OnTouchListener() {
            private float downX;
            private float downY;
            private int startX;
            private int startY;
            private boolean moved;

            @Override public boolean onTouch(View view, android.view.MotionEvent event) {
                if (panelParams == null) return false;
                if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                    restorePlacementPending = false;
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
                        clampPanelPlacement();
                        updatePanelWindow();
                    }
                    return true;
                }
                if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                    savePanelPlacement();
                    if (clickOnTap && !moved) view.performClick();
                    return true;
                }
                if (event.getAction() == android.view.MotionEvent.ACTION_CANCEL) {
                    savePanelPlacement();
                    return true;
                }
                return false;
            }
        };
    }

    private void setDiagnosticsExpanded(boolean expanded) {
        diagnosticsExpanded = expanded;
        ClickerPreferences.setDiagnosticsExpanded(this, panelProfile, expanded);
        updatePanelContentVisibility();
    }

    private CharSequence bubbleLabel(String icon) {
        SpannableString label = new SpannableString(icon + "\nВідкрити");
        label.setSpan(new RelativeSizeSpan(2.2f), 0, icon.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return label;
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

    private void startAutoPlay() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Toast.makeText(this, "Автогра потребує Android 11 або новішу версію", Toast.LENGTH_LONG).show();
            return;
        }
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
                    displayedBlitzColors[row * GameBoard.SIDE + column] = 0;
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
                    && elapsedSinceSwipe < ClickerPreferences.timeoutMs(this, activeMode)) {
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
                    && elapsedSinceSwipe < ClickerPreferences.timeoutMs(this, activeMode)) {
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
                if (color != 0) occupied++;
                int index = row * GameBoard.SIDE + column;
                if (displayedBlitzColors[index] == color) continue;
                TextView cell = boardCells[row][column];
                if (color == 0) {
                    cell.setText("·");
                    cell.setTextColor(0xFF937D80);
                    cell.setBackground(round(EMPTY_CELL, 5));
                } else {
                    cell.setText(GEM_LABELS[color]);
                    cell.setTextColor(CREAM);
                    cell.setBackground(round(GEM_COLORS[color], 5));
                }
                displayedBlitzColors[index] = color;
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
        else updatePanel("Готовий");
    }

    private void updatePanel(String label) {
        if (stateChip != null) stateChip.setText("Стан: " + label);
        if (playButton != null) {
            playButton.setText(autoPlaying ? "■ Стоп" : "▶ Старт");
            playButton.setBackground(round(autoPlaying ? 0xFF78504E : 0xFF526A4D, 10));
            playButton.setContentDescription((autoPlaying ? "Зупинити" : "Запустити")
                    + " бота «" + ClickerCatalog.get(activeMode).name + "»");
            playButton.setVisibility(View.VISIBLE);
        }
        updatePanelAppearance();
    }

    private void updatePanelAppearance() {
        if (panel == null) return;
        ClickerModule module = ClickerCatalog.get(activeMode);
        GradientDrawable background = round(0xF52F2025, collapsedToBubble ? 31 : 17);
        background.setStroke(dp(1), autoPlaying ? 0xFF7EAE74 : module.theme.border);
        panel.setBackground(background);
        if (stateChip != null) stateChip.setBackground(round(autoPlaying ? 0xFF3D5540 : 0xFF49353C, 10));
        if (bubbleButton != null) {
            bubbleButton.setContentDescription((module.name) + ". "
                    + (autoPlaying ? "Бот працює" : "Бот зупинений") + ". Натисни, щоб розгорнути панель.");
        }
    }

    private void closePanel() {
        savePanelPlacement();
        stopAutoPlay("Зупинено");
        if (panel != null && windowManager != null) {
            try { windowManager.removeView(panel); } catch (IllegalArgumentException ignored) { }
        }
        panel = null;
        panelBody = null;
        panelIcon = null;
        bubbleButton = null;
        collapsedToBubble = false;
        restorePlacementPending = false;
        panelArea.setEmpty();
        panelParams = null;
        panelTitle = null;
        stateChip = null;
        playButton = null;
        detailsToggle = null;
        boardInfoView = null;
        messageView = null;
        scoreView = null;
        mergeDetailsView = null;
        boardCells = null;
        mergeBoardCells = null;
        blitzBoardGrid = null;
        mergeBoardGrid = null;
        diagnosticsPanel = null;
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

    private int dp(float value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
}
