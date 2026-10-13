package ac.grim.grimac.platform.bukkit.utils;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.utils.anticheat.LogUtil;
import ac.grim.grimac.utils.freeze.TempBans;
import io.papermc.paper.ban.BanListType;
import org.bukkit.BanList;
import org.bukkit.Bukkit;

import java.time.Duration;
import java.util.Date;
import java.util.UUID;

/** Adds automatic bans to the server's ban list, where /pardon and freeze-core's /unban find them. */
public final class BukkitTempBans implements TempBans.Banner {

    @Override
    public void ban(UUID uuid, String name, String reason, Duration duration, String source) {
        GrimAPI.INSTANCE.getScheduler().getGlobalRegionScheduler().run(GrimAPI.INSTANCE.getGrimPlugin(), () -> {
            try {
                Bukkit.getBanList(BanListType.PROFILE).addBan(Bukkit.createProfile(uuid, name), reason, duration, source);
            } catch (NoClassDefFoundError | NoSuchMethodError e) {
                // Servers older than 1.20.4 only have the name ban list
                addNameBan(name, reason, duration, source);
            } catch (RuntimeException e) {
                LogUtil.error("Could not ban " + name, e);
            }
        });
    }

    @SuppressWarnings("deprecation")
    private static void addNameBan(String name, String reason, Duration duration, String source) {
        Bukkit.getBanList(BanList.Type.NAME).addBan(name, reason, new Date(System.currentTimeMillis() + duration.toMillis()), source);
    }
}
