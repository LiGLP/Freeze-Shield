package ac.grim.grimac.platform.bukkit.antiesp;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.utils.anticheat.ExternalPacketBridge;
import ac.grim.grimac.utils.anticheat.LogUtil;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.player.User;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.BiConsumer;

/**
 * Starts the embedded anti-ESP (RaycastedAntiESP, AGPL-3.0, https://github.com/Cubicake/RaycastedAntiESP).
 * <p>
 * It's built for Paper 1.21.4+ and Java 21, while Freeze Shield also runs on Spigot, older versions and Java 17,
 * so it ships as a separate jar inside ours and gets its own classloader only where it can run.
 */
public final class AntiEspLoader {
    private static final String JAR_RESOURCE = "antiesp/freezeshield-antiesp.jar";
    private static final String ENTRY_CLASS = "games.cubi.raycastedantiesp.paper.RaycastedAntiESP";

    private static URLClassLoader classLoader;
    private static Class<?> entryClass;

    private AntiEspLoader() {}

    public static void enable(JavaPlugin plugin) {
        if (!GrimAPI.INSTANCE.getConfigManager().getConfig().getBooleanElse("anti-esp.enabled", true)) {
            return;
        }

        String unsupported = getUnsupportedReason();
        if (unsupported != null) {
            LogUtil.info("Anti-ESP is disabled: " + unsupported);
            return;
        }

        try {
            Path jar = extractJar(plugin);
            classLoader = new URLClassLoader(new URL[]{jar.toUri().toURL()}, plugin.getClass().getClassLoader());
            entryClass = Class.forName(ENTRY_CLASS, true, classLoader);

            BiConsumer<Object, byte[]> grimSync = (user, packet) -> ExternalPacketBridge.dispatchSend((User) user, packet);
            Method enable = entryClass.getMethod("enable", JavaPlugin.class, Path.class, BiConsumer.class);
            enable.invoke(null, plugin, plugin.getDataFolder().toPath().resolve("antiesp"), grimSync);
            LogUtil.info("Anti-ESP enabled");
        } catch (Throwable throwable) {
            LogUtil.error("Failed to enable the anti-ESP, Freeze Shield keeps running without it", throwable);
            disable();
        }
    }

    public static void disable() {
        try {
            if (entryClass != null) {
                entryClass.getMethod("disable").invoke(null);
            }
            if (classLoader != null) {
                classLoader.close();
            }
        } catch (Throwable throwable) {
            LogUtil.error("Failed to disable the anti-ESP", throwable);
        } finally {
            entryClass = null;
            classLoader = null;
        }
    }

    private static String getUnsupportedReason() {
        if (Runtime.version().feature() < 21) {
            return "it needs Java 21 or newer";
        }
        if (PacketEvents.getAPI().getServerManager().getVersion().isOlderThan(ServerVersion.V_1_21_4)) {
            return "it needs Minecraft 1.21.4 or newer";
        }
        try {
            Class.forName("io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents");
        } catch (ClassNotFoundException e) {
            return "it needs Paper (or a fork of it such as Purpur or Folia)";
        }
        return null;
    }

    private static Path extractJar(JavaPlugin plugin) throws Exception {
        Path jar = Files.createTempFile("freezeshield-antiesp", ".jar");
        jar.toFile().deleteOnExit();
        try (InputStream in = plugin.getResource(JAR_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(JAR_RESOURCE + " is missing from the Freeze Shield jar");
            }
            Files.copy(in, jar, StandardCopyOption.REPLACE_EXISTING);
        }
        return jar;
    }
}
