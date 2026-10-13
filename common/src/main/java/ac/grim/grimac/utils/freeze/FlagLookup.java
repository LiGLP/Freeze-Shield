package ac.grim.grimac.utils.freeze;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.api.storage.history.HistoryService;
import ac.grim.grimac.api.storage.history.SessionDetail;
import ac.grim.grimac.api.storage.history.SessionSummary;
import ac.grim.grimac.api.storage.history.ViolationEntry;
import ac.grim.grimac.manager.datastore.DataStoreLifecycle;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Flag summary of a player for freeze-api's {@code anticheat.flags} action (Discord /lookup), built from the
 * history datastore. Returns JSON:
 * <pre>
 * {"enabled": true, "sessions": 12, "scannedSessions": 10, "flags": 340,
 *  "topChecks": [{"check": "Simulation", "count": 120}, ...],
 *  "recent": [{"check", "vl", "verbose", "time"}, ...]}
 * </pre>
 * {@code flags} and {@code topChecks} cover the newest {@code scannedSessions} sessions.
 */
final class FlagLookup {

    private static final int SESSIONS = 10;
    private static final int DETAIL_SESSIONS = 5;
    private static final int TOP_CHECKS = 8;
    private static final int RECENT = 15;
    private static final int MAX_VERBOSE = 120;

    private FlagLookup() {
    }

    static CompletableFuture<String> lookup(UUID uuid) {
        CompletableFuture<String> future = new CompletableFuture<>();
        GrimAPI.INSTANCE.getScheduler().getAsyncScheduler().runNow(GrimAPI.INSTANCE.getGrimPlugin(), () -> {
            try {
                future.complete(build(uuid).toString());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    private static JsonObject build(UUID uuid) throws Exception {
        JsonObject result = new JsonObject();
        DataStoreLifecycle lifecycle = GrimAPI.INSTANCE.getDataStoreLifecycle();
        HistoryService history = lifecycle == null || !lifecycle.isEnabled() || !lifecycle.isLoaded()
                ? null : lifecycle.historyService();
        if (history == null) {
            result.addProperty("enabled", false);
            return result;
        }
        result.addProperty("enabled", true);

        long sessionCount = history.countSessions(uuid).toCompletableFuture().get(5, TimeUnit.SECONDS);
        List<SessionSummary> sessions = history.listSessions(uuid, null, SESSIONS)
                .toCompletableFuture().get(5, TimeUnit.SECONDS).items();

        long flags = 0;
        for (SessionSummary session : sessions) flags += session.violationCount();
        result.addProperty("sessions", sessionCount);
        result.addProperty("scannedSessions", sessions.size());
        result.addProperty("flags", flags);

        // Per-check counts and the newest flags need the session details; only the newest sessions with flags
        Map<String, Integer> perCheck = new HashMap<>();
        List<JsonObject> recent = new ArrayList<>();
        int detailed = 0;
        for (SessionSummary session : sessions) {
            if (session.violationCount() == 0) continue;
            if (++detailed > DETAIL_SESSIONS) break;
            SessionDetail detail = history.getSessionDetail(uuid, session.sessionId())
                    .toCompletableFuture().get(5, TimeUnit.SECONDS);
            if (detail == null) continue;
            for (ViolationEntry v : detail.violations()) {
                perCheck.merge(v.displayName(), 1, Integer::sum);
                JsonObject flag = new JsonObject();
                flag.addProperty("check", v.displayName());
                flag.addProperty("vl", v.vl());
                flag.addProperty("verbose", shorten(v.verbose()));
                flag.addProperty("time", detail.startedEpochMs() + v.offsetFromSessionStartMs());
                recent.add(flag);
            }
        }

        JsonArray top = new JsonArray();
        perCheck.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(TOP_CHECKS)
                .forEach(e -> {
                    JsonObject o = new JsonObject();
                    o.addProperty("check", e.getKey());
                    o.addProperty("count", e.getValue());
                    top.add(o);
                });
        result.add("topChecks", top);

        JsonArray newest = new JsonArray();
        recent.stream()
                .sorted(Comparator.comparingLong((JsonObject o) -> o.get("time").getAsLong()).reversed())
                .limit(RECENT)
                .forEach(newest::add);
        result.add("recent", newest);
        return result;
    }

    private static String shorten(String verbose) {
        if (verbose == null) return "";
        String text = verbose.replaceAll(" /gl .*", "").strip();
        return text.length() > MAX_VERBOSE ? text.substring(0, MAX_VERBOSE - 1) + "…" : text;
    }
}
