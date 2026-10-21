package ua.borlock.autotap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Backward-compatible persistence, including the original six-field running timers. */
final class TimerCodec {
    static final long MAX_DURATION_MS = 365L * 24 * 60 * 60 * 1000;
    private TimerCodec() { }

    static List<TimerItem> decode(String json) {
        ArrayList<TimerItem> result = new ArrayList<>();
        HashSet<String> ids = new HashSet<>();
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                String id = item.optString("id", "").trim();
                String name = item.optString("name", "").trim();
                String emoji = item.optString("emoji", "⏱️").trim();
                long duration = item.optLong("duration_ms", 0);
                if (id.isEmpty() || name.isEmpty() || !validDuration(duration) || ids.contains(id)) continue;
                long wall = Math.max(0, item.optLong("deadline_wall_ms", item.optLong("deadline_ms", 0)));
                long elapsed = Math.max(0, item.optLong("deadline_elapsed_ms", 0));
                String token = item.optString("run_token", wall > 0 ? "legacy:" + wall : "");
                if (wall > 0 && token.isEmpty()) token = "legacy:" + wall;
                if (wall == 0) { elapsed = 0; token = ""; }
                result.add(new TimerItem(id, name, emoji.isEmpty() ? "⏱️" : emoji,
                        duration, wall, elapsed, token, item.optInt("boot_count", -1)));
                ids.add(id);
            }
        } catch (JSONException ignored) { }
        return result;
    }

    static String encode(List<TimerItem> timers) {
        JSONArray array = new JSONArray();
        for (TimerItem item : timers) {
            JSONObject object = new JSONObject();
            try {
                object.put("id", item.id).put("name", item.name).put("emoji", item.emoji)
                        .put("duration_ms", item.durationMs).put("deadline_wall_ms", item.deadlineWallMs)
                        .put("deadline_elapsed_ms", item.deadlineElapsedMs).put("run_token", item.runToken)
                        .put("boot_count", item.bootCount);
                array.put(object);
            } catch (JSONException ignored) { }
        }
        return array.toString();
    }

    static boolean validDuration(long value) { return value > 0 && value <= MAX_DURATION_MS; }
}
