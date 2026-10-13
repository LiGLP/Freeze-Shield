package ac.grim.grimac.utils.freeze;

import ac.grim.grimac.utils.anticheat.LogUtil;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Talks to the freeze-api plugin without a compile-time dependency. freeze-api's Discord bot posts what we
 * queue here (anticheat log, ban log) and answers /lookup with {@link FlagLookup}.
 * <p>
 * Only JDK types cross the plugin boundary (JSON travels as strings), so the gson relocation in our jar
 * doesn't matter. Every method is a no-op returning false when freeze-api isn't installed.
 */
public final class FreezeApiBridge {

    private static final String API_CLASS = "de.liglp.freeze.api.FreezeApi";

    private static volatile boolean resolved;
    private static volatile @Nullable Method queueDiscordJson;
    private static volatile @Nullable Method setAnticheatProvider;

    private FreezeApiBridge() {
    }

    private static void resolve() {
        if (resolved) return;
        synchronized (FreezeApiBridge.class) {
            if (resolved) return;
            try {
                Class<?> api = Class.forName(API_CLASS);
                queueDiscordJson = api.getMethod("queueDiscordJson", String.class, String.class);
                setAnticheatProvider = api.getMethod("setAnticheatProvider", Function.class);
                LogUtil.info("Found freeze-api, Discord logs and /lookup flags go through its bot");
            } catch (ClassNotFoundException ignored) {
                // freeze-api isn't installed
            } catch (ReflectiveOperationException | LinkageError e) {
                LogUtil.warn("freeze-api is installed but too old for Freeze Shield (" + e + "), update it");
            }
            resolved = true;
        }
    }

    /**
     * Queues a Discord message for the freeze-api bot.
     *
     * @param channel channel key the bot maps to a Discord channel, e.g. "anticheat-log" or "ban-log"
     * @param json    Discord message JSON ({"embeds": [...]}, optionally "unban": {"uuid", "name"} for an Unban button)
     * @return false if freeze-api isn't available, the caller should fall back then
     */
    public static boolean queueDiscord(String channel, String json) {
        resolve();
        Method method = queueDiscordJson;
        if (method == null) return false;
        try {
            return (boolean) method.invoke(null, channel, json);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            LogUtil.warn("Could not queue a Discord message in freeze-api: " + e);
            return false;
        }
    }

    /** Lets freeze-api's {@code anticheat.flags} action (used by /lookup) read our flag history. */
    public static void registerFlagLookup() {
        setProvider(FlagLookup::lookup);
    }

    public static void unregisterFlagLookup() {
        setProvider(null);
    }

    private static void setProvider(@Nullable Function<UUID, CompletableFuture<String>> provider) {
        resolve();
        Method method = setAnticheatProvider;
        if (method == null) return;
        try {
            method.invoke(null, provider);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            LogUtil.warn("Could not register the flag lookup in freeze-api: " + e);
        }
    }
}
