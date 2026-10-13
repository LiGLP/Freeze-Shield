package ac.grim.grimac.utils.freeze;

import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.UUID;

/**
 * Temporary bans for automatic punishments (e.g. a detected Meteor Client). The platform registers the
 * implementation; Bukkit adds them to the server's ban list, so /pardon, freeze-core's /unban and the Unban
 * button in the Discord ban log all lift them.
 */
public final class TempBans {

    public interface Banner {
        /** Adds the ban. May be called from any thread. */
        void ban(UUID uuid, String name, String reason, Duration duration, String source);
    }

    private static volatile @Nullable Banner banner;

    private TempBans() {
    }

    public static void setBanner(@Nullable Banner banner) {
        TempBans.banner = banner;
    }

    /** @return false if this platform can't ban (the player should still be kicked) */
    public static boolean ban(UUID uuid, String name, String reason, Duration duration, String source) {
        Banner current = banner;
        if (current == null) return false;
        current.ban(uuid, name, reason, duration, source);
        return true;
    }
}
