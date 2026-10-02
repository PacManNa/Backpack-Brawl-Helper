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

/** Reads the jewel grid and plays one scored adjacent swap at a time. */
public final class TapAccessibilityService extends AccessibilityService {
    static final String PREFS = "pelyustka_settings";
    static final String KEY_NO_CHANGE_TIMEOUT = "move_delay_ms";
    static final String KEY_SWIPE_CELLS_TENTHS = "swipe_cells_tenths";
    static final String KEY_SWIPE_DURATION_MS = "swipe_duration_ms";
    private static final long DEFAULT_NO_CHANGE_TIMEOUT_MS = 900L;
    private static final long MIN_NO_CHANGE_TIMEOUT_MS = 700L;
    private static final long MAX_NO_CHANGE_TIMEOUT_MS = 4_000L;
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
    private WindowManager windowManager;
    private LinearLayout panel;
    private WindowManager.LayoutParams panelParams;
    private TextView stateChip;
    private TextView playButton;
    private TextView boardInfoView;
    private TextView messageView;
    private TextView scoreView;
    private TextView[][] boardCells;
    private LinearLayout diagnosticsPanel;
    private boolean diagnosticsExpanded;
    private boolean autoPlaying;
    private boolean screenshotPending;
    private long lastSuccessfulScreenshotAtMs;
    private int moveCount;
    private int runGeneration;
    private GameBoard previousBoard;
    private GameBoard stableCandidateBoard;
    private int stableCandidateReads;
    private int stabilizationReads;
    private int unchangedSwipeRetries;
    private long lastSwipeCompletedAtMs;
    private String lastRecognitionError;
    private GameSolver.Choice lastChoice;
    private BoardVision.Geometry lastGeometry;
    private int noMoveReads;

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
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
        return instance != null && instance.showPanel();
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

    private boolean showPanel() {
        if (windowManager == null) return false;
        if (panel != null) {
            panel.setVisibility(View.VISIBLE);
            updatePanel();
            return true;
        }
        panel = createPanel();
        panelParams = new WindowManager.LayoutParams(
                dp(210),
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
            return true;
        } catch (WindowManager.BadTokenException | IllegalStateException error) {
            panel = null;
            return false;
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
        TextView title = button("РЮКЗАК-БЛІЦ", CREAM, 10);
        title.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        title.setPadding(dp(4), 0, dp(4), 0);
        title.setOnTouchListener(createDragListener());
        titleRow.addView(title, new LinearLayout.LayoutParams(0, dp(22), 1f));
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
        LinearLayout boardGrid = new LinearLayout(this);
        boardGrid.setOrientation(LinearLayout.VERTICAL);
        boardGrid.setPadding(dp(2), dp(1), dp(2), dp(1));
        boardGrid.setBackground(round(0xFF21171B, 9));
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
            boardGrid.addView(boardRow, new LinearLayout.LayoutParams(-1, -2));
        }
        diagnosticsPanel.addView(boardGrid, new LinearLayout.LayoutParams(-1, -2));

        boardInfoView = smallInfoText("Поле ще не прочитане");
        boardInfoView.setSingleLine(true);
        boardInfoView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(-1, -2);
        infoParams.setMargins(dp(2), dp(3), dp(2), 0);
        diagnosticsPanel.addView(boardInfoView, infoParams);

        messageView = smallInfoText("Відкрий гру й натисни «Старт»");
        messageView.setTextColor(CREAM);
        messageView.setMinLines(2);
        messageView.setMaxLines(2);
        messageView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(-1, -2);
        messageParams.setMargins(dp(2), dp(1), dp(2), 0);
        diagnosticsPanel.addView(messageView, messageParams);

        scoreView = smallInfoText("Бот шукає найкращий збіг");
        scoreView.setTypeface(Typeface.MONOSPACE);
        scoreView.setSingleLine(true);
        scoreView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams scoreParams = new LinearLayout.LayoutParams(-1, -2);
        scoreParams.setMargins(dp(2), dp(2), dp(2), dp(3));
        diagnosticsPanel.addView(scoreView, scoreParams);
        root.addView(diagnosticsPanel, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);

        playButton = button("▶\nСтарт", CREAM, 9);
        playButton.setBackground(round(0xFF526A4D, 10));
        playButton.setOnClickListener(view -> startAutoPlay());
        playButton.setContentDescription("Запустити бота Рюкзак-Бліц");
        controls.addView(playButton, controlWeight());

        TextView stopButton = button("■\nСтоп", CREAM, 9);
        stopButton.setBackground(round(0xFF78504E, 10));
        stopButton.setOnClickListener(view -> stopAutoPlay("Зупинено"));
        stopButton.setContentDescription("Зупинити бота");
        controls.addView(stopButton, controlWeight());

        TextView closeButton = button("×\nСховати", CREAM, 9);
        closeButton.setBackground(round(0xFF493339, 10));
        closeButton.setOnClickListener(view -> closePanel());
        closeButton.setContentDescription("Сховати плаваючу панель");
        controls.addView(closeButton, controlWeight());

        root.addView(controls, new LinearLayout.LayoutParams(-1, dp(36)));
        return root;
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
        if (diagnosticsPanel != null) {
            diagnosticsPanel.setVisibility(expanded ? View.VISIBLE : View.GONE);
        }
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
        if (autoPlaying) return;
        int currentRun = ++runGeneration;
        autoPlaying = true;
        screenshotPending = false;
        moveCount = 0;
        previousBoard = null;
        stableCandidateBoard = null;
        stableCandidateReads = 0;
        stabilizationReads = 0;
        unchangedSwipeRetries = 0;
        lastSwipeCompletedAtMs = 0L;
        lastRecognitionError = null;
        lastChoice = null;
        lastGeometry = null;
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
        if (boardInfoView != null) boardInfoView.setText("Чекаю на поле 8×8");
        if (scoreView != null) scoreView.setText("Шукаю сусідні заміни");
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
                solverExecutor.execute(() -> {
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
                stableCandidateReads = 0;
                stabilizationReads = 0;
                lastRecognitionError = null;
                updatePanel("ЧЕКАЮ НА КАДР");
                long scanDelay = Math.max(MIN_POST_SWIPE_SETTLE_MS,
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
        else updatePanel("РЮКЗАК-БЛІЦ · ГОТОВИЙ");
    }

    private void updatePanel(String label) {
        if (stateChip != null) stateChip.setText(label);
        if (playButton != null) playButton.setText(autoPlaying ? "●\nПрацює" : "▶\nСтарт");
    }

    private void closePanel() {
        stopAutoPlay("Зупинено");
        if (panel != null && windowManager != null) {
            try { windowManager.removeView(panel); } catch (IllegalArgumentException ignored) { }
        }
        panel = null;
        panelParams = null;
        stateChip = null;
        playButton = null;
        boardInfoView = null;
        messageView = null;
        scoreView = null;
        boardCells = null;
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

    private LinearLayout.LayoutParams controlWeight() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -1, 1f);
        params.setMargins(dp(3), 0, dp(3), 0);
        return params;
    }

    private int dp(float value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
}
