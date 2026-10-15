package ua.borlock.autotap;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.RectF;

/** Stores user-calibrated Merge Madness board geometry for the current app installation. */
final class MergeMadnessCalibration {
    private static final String KEY_BOARD_CENTER_X = "merge_board_center_x_ratio";
    private static final String KEY_BOARD_CENTER_Y = "merge_board_center_y_ratio";
    private static final String KEY_BOARD_STEP_X = "merge_board_step_x_ratio";
    private static final String KEY_BOARD_STEP_Y = "merge_board_step_y_ratio";

    private MergeMadnessCalibration() { }

    static boolean saveBoardRectangle(Context context, int screenWidth, int screenHeight,
                                      RectF rectangle) {
        if (!isValidBoardRectangle(rectangle, screenWidth, screenHeight)) return false;
        float width = rectangle.width();
        float height = rectangle.height();

        SharedPreferences.Editor editor = preferences(context).edit();
        editor.putFloat(KEY_BOARD_CENTER_X, (rectangle.left + width * 0.125f) / screenWidth);
        editor.putFloat(KEY_BOARD_CENTER_Y, (rectangle.top + height * 0.125f) / screenHeight);
        editor.putFloat(KEY_BOARD_STEP_X, width * 0.25f / screenWidth);
        editor.putFloat(KEY_BOARD_STEP_Y, height * 0.25f / screenHeight);
        return editor.commit();
    }

    static boolean isValidBoardRectangle(RectF rectangle, int screenWidth, int screenHeight) {
        if (rectangle == null || screenWidth <= 0 || screenHeight <= 0) return false;
        float width = rectangle.width();
        float height = rectangle.height();
        float aspect = width / Math.max(1f, height);
        return width >= screenWidth * 0.35f && width <= screenWidth * 0.96f
                && height >= screenHeight * 0.16f && height <= screenHeight * 0.75f
                && aspect >= 0.85f && aspect <= 1.17f
                && rectangle.left >= 0 && rectangle.top >= 0
                && rectangle.right <= screenWidth && rectangle.bottom <= screenHeight;
    }

    static BoardVision.Geometry boardGeometry(Context context, int screenWidth, int screenHeight) {
        SharedPreferences preferences = preferences(context);
        if (!preferences.contains(KEY_BOARD_CENTER_X)
                || !preferences.contains(KEY_BOARD_CENTER_Y)
                || !preferences.contains(KEY_BOARD_STEP_X)
                || !preferences.contains(KEY_BOARD_STEP_Y)
                || screenWidth <= 0 || screenHeight <= 0) return null;
        return new BoardVision.Geometry(
                preferences.getFloat(KEY_BOARD_CENTER_X, 0f) * screenWidth,
                preferences.getFloat(KEY_BOARD_CENTER_Y, 0f) * screenHeight,
                preferences.getFloat(KEY_BOARD_STEP_X, 0f) * screenWidth,
                preferences.getFloat(KEY_BOARD_STEP_Y, 0f) * screenHeight);
    }

    static boolean hasBoardGeometry(Context context) {
        return preferences(context).contains(KEY_BOARD_CENTER_X);
    }

    static boolean clearBoardGeometry(Context context) {
        return preferences(context).edit()
                .remove(KEY_BOARD_CENTER_X)
                .remove(KEY_BOARD_CENTER_Y)
                .remove(KEY_BOARD_STEP_X)
                .remove(KEY_BOARD_STEP_Y)
                .commit();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(TapAccessibilityService.PREFS, Context.MODE_PRIVATE);
    }
}
