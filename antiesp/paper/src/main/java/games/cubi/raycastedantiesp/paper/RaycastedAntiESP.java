/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright © 2026 Cubicake.
 * Modified for Freeze Shield (https://github.com/LiGLP/Freeze-Shield): runs as a component inside the Freeze Shield
 * plugin instead of as its own plugin, and keeps the anticheat in sync through GrimSync.
 * This file is part of RaycastedAntiESP. RaycastedAntiESP is free software: you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License v3.0 only, which can be accessed at
 * https://www.gnu.org/licenses/agpl-3.0.html. See README.md for warranty disclaimer and further information.
 */

package games.cubi.raycastedantiesp.paper;

import games.cubi.raycastedantiesp.core.Core;
import games.cubi.raycastedantiesp.packetevents.GrimSync;
import games.cubi.raycastedantiesp.paper.commands.Attribution;
import games.cubi.raycastedantiesp.paper.commands.AttributionBrigadier;
import games.cubi.raycastedantiesp.paper.commands.RaycastedAntiESPCommandBrigadier;
import games.cubi.raycastedantiesp.paper.config.PaperEntityTypeExclusionResolver;
import games.cubi.raycastedantiesp.paper.engine.PaperAsyncEngine;
import games.cubi.raycastedantiesp.paper.config.PaperBlockSelectorMatcher;
import games.cubi.raycastedantiesp.packetevents.view.PacketEventsBlockView;
import games.cubi.raycastedantiesp.packetevents.view.PacketEventsEntityView;
import games.cubi.raycastedantiesp.core.view.ViewRegistry;
import games.cubi.raycastedantiesp.packetevents.viewcontrollers.PacketEventsCommonViewController;
import games.cubi.raycastedantiesp.paper.packets.PaperPacketEventsBlockViewController;
import games.cubi.raycastedantiesp.paper.packets.PaperPacketEventsCommonViewController;
import games.cubi.raycastedantiesp.paper.packets.PaperPacketEventsEntityViewController;
import games.cubi.raycastedantiesp.paper.packets.PacketEventsPaperBlockInfoResolver;
import games.cubi.raycastedantiesp.core.config.ConfigManager;
import games.cubi.logs.Logger;

import games.cubi.raycastedantiesp.paper.utils.FoliaTicker;
import games.cubi.raycastedantiesp.paper.utils.PaperTicker;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;

import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/**
 * Entry point used by Freeze Shield. Freeze Shield loads this class from the embedded anti-ESP jar on Paper 1.21.4+
 * and calls {@link #enable(JavaPlugin, Path, BiConsumer, BooleanSupplier)} from its own onEnable.
 */
public final class RaycastedAntiESP {
    private static ConfigManager config;
    private static PaperPacketEventsEntityViewController packetEventsController;
    private static PaperAsyncEngine engine;
    private static JavaPlugin host;
    private static PaperLoggerAdapter loggerAdapter;
    // Freeze Shield: everything registered on the host plugin, so it can be removed again on disable or a failed start
    private static final List<PacketListenerCommon> packetListeners = new CopyOnWriteArrayList<>();
    private static final List<Listener> bukkitListeners = new CopyOnWriteArrayList<>();

    public static final boolean isFolia = getClass("io.papermc.paper.threadedregions.RegionizedServer") != null;

    private RaycastedAntiESP() {}

    public static @Nullable Class<?> getClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    /**
     * @param plugin    the Freeze Shield plugin, used for schedulers, listeners and commands
     * @param dataDir   where the anti-ESP keeps its config and logs
     * @param grimSync  receives every packet the anti-ESP sends or changes behind the anticheat's back
     * @param clientOnlyPacket whether the block packet being encoded on this thread was made up by the anticheat
     *                         (fake bases, resyncs), so the anti-ESP leaves it alone
     */
    public static void enable(JavaPlugin plugin, Path dataDir, BiConsumer<Object, byte[]> grimSync, BooleanSupplier clientOnlyPacket) {
        host = plugin;
        java.util.logging.Logger logger = java.util.logging.Logger.getLogger("FreezeShield-AntiESP");
        logger.setParent(plugin.getLogger());
        loggerAdapter = new PaperLoggerAdapter(logger, dataDir.resolve("logs/" + System.currentTimeMillis() + ".log"));
        Core.initialize(loggerAdapter);
        GrimSync.setSink(grimSync);
        GrimSync.setClientOnlyPacketCheck(clientOnlyPacket);

        config = ConfigManager.initialiseConfigManager(dataDir);

        IntSupplier currentTickSupplier;
        if (isFolia) {
            Logger.info("Folia detected. Some features may not work as expected.", 5);
            currentTickSupplier = new FoliaTicker();
        }
        else {
            currentTickSupplier = new PaperTicker();
        }
        config.completeInitialLoad();
        PaperEntityTypeExclusionResolver.resolveAndInitialise(config.getEntityConfig().excludedTypes());
        PacketEventsPaperBlockInfoResolver blockInfoResolver = new PacketEventsPaperBlockInfoResolver(
                new PaperBlockSelectorMatcher(config.getTileEntityConfig().excludedBlocks())
        );
        boolean trackAllBlocks = config.getBlockProcessorConfig().trackAllBlocks();
        ViewRegistry.initialise(worldEpoch -> new PacketEventsBlockView(blockInfoResolver, trackAllBlocks, worldEpoch), PacketEventsEntityView::createEntityView, PacketEventsEntityView::createPlayerView);
        PacketEventsCommonViewController.initialise(new PaperPacketEventsCommonViewController(currentTickSupplier));
        packetEventsController = new PaperPacketEventsEntityViewController(currentTickSupplier);
        // PaperPacketEventsEntityViewController must be constructed before PaperPacketEventsBlockViewController
        // as the entity controller must run before the block controller
        new PaperPacketEventsBlockViewController(blockInfoResolver, trackAllBlocks, currentTickSupplier);

        engine = new PaperAsyncEngine(plugin, config, currentTickSupplier);
        // Freeze Shield ships the anti-ESP inside its own jar, so the standalone update check and bStats are not run
        EventListener.initialise(plugin, engine, currentTickSupplier);

        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS.newHandler(event -> {
            RaycastedAntiESPCommandBrigadier.register(event.registrar());
            AttributionBrigadier.register(event.registrar());
        }));

        /*Do not delete, this is a legal notice*/Attribution.sendAttributionMessage(Bukkit.getConsoleSender()); // Legal notice as required by AGPLv3, it prominently offers users of this plugin the source code and displays an appropriate copyright notice. If you are a fork developer, do NOT remove this unless you have a thorough understanding of the AGPL and have replaced it with a suitable equivalent notice which is "prominently visible", displays the copyright notice, and includes a link to the source code of your fork which is accessible to all users of the plugin.
        new FancyCompatibility();
    }

    public static void disable() {
        // Stop ticking and packet handling first, the classes are about to go away with the classloader
        engine = null;
        for (PacketListenerCommon listener : packetListeners) {
            try {
                PacketEvents.getAPI().getEventManager().unregisterListener(listener);
            } catch (RuntimeException ignored) {
                // PacketEvents is already shut down
            }
        }
        packetListeners.clear();
        for (Listener listener : bukkitListeners) {
            HandlerList.unregisterAll(listener);
        }
        bukkitListeners.clear();
        GrimSync.setSink(null);
        GrimSync.setClientOnlyPacketCheck(null);
        if (loggerAdapter != null) {
            loggerAdapter.forceFlushToFileNow();
        }
    }

    public static void registerPacketListener(PacketListener listener, PacketListenerPriority priority) {
        packetListeners.add(PacketEvents.getAPI().getEventManager().registerListener(listener, priority));
    }

    public static void registerBukkitListener(Listener listener) {
        Bukkit.getPluginManager().registerEvents(listener, host);
        bukkitListeners.add(listener);
    }

    public static ConfigManager getConfigManager() {
        return config;
    }
    public static PaperPacketEventsEntityViewController getPacketEventsController() {
        return packetEventsController;
    }
    public static PaperAsyncEngine getEngine() {
        return engine;
    }
    public static JavaPlugin get() {
        return host;
    }
}
