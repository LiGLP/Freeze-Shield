/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright © 2026 Cubicake.
 * This file is part of RaycastedAntiESP. RaycastedAntiESP is free software: you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License v3.0 only, which can be accessed at
 * https://www.gnu.org/licenses/agpl-3.0.html. See README.md for warranty disclaimer and further information.
 */

package games.cubi.raycastedantiesp.paper;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import com.destroystokyo.paper.event.server.ServerTickStartEvent;
import games.cubi.logs.Logger;
import games.cubi.raycastedantiesp.core.entity.EntityBypassRegistry;
import games.cubi.raycastedantiesp.core.players.PlayerRegistry;
import games.cubi.raycastedantiesp.paper.engine.PaperAsyncEngine;
import games.cubi.raycastedantiesp.core.players.PlayerData;
import games.cubi.raycastedantiesp.paper.utils.PaperListener;
import io.papermc.paper.event.player.PlayerClientLoadedWorldEvent;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.function.IntSupplier;


public class EventListener extends PaperListener {
    private final org.bukkit.plugin.java.JavaPlugin plugin;
    private final PaperAsyncEngine engine;
    private final IntSupplier currentTickSupplier;
    private static EventListener instance = null;

    private EventListener(org.bukkit.plugin.java.JavaPlugin plugin, PaperAsyncEngine engine, IntSupplier currentTickSupplier) {
        this.plugin = plugin;
        this.engine = engine;
        this.currentTickSupplier = currentTickSupplier;
    }

    public static EventListener initialise(org.bukkit.plugin.java.JavaPlugin plugin, PaperAsyncEngine engine, IntSupplier currentTickSupplier) {
        if (instance == null) {
            instance = new EventListener(plugin, engine, currentTickSupplier);
        }
        return instance;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) //Paper ignoreCancelled means "do not run this method if the event is cancelled", not "run even if cancelled"
    public void onEntityRemove(EntityRemoveEvent event) {
        EntityBypassRegistry.markEntityDespawned(event.getEntity().getEntityId());
    }

    @EventHandler(priority = EventPriority.LOWEST) //Runs first
    public void onPlayerJoin(PlayerClientLoadedWorldEvent e) {
        Player player = e.getPlayer();

        if (player.hasMetadata("NPC")) return;

        PlayerData playerData = PlayerRegistry.get().getPlayerData(player.getUniqueId());
        if (playerData == null) {
            // Freeze Shield: the anti-ESP is an extra, never kick over it (e.g. players joining through a connection
            // PacketEvents didn't see the login of); they just aren't covered by it
            Logger.warning("Player joined before packet state was registered, the anti-ESP won't cover player=" + player.getName() + " uuid=" + player.getUniqueId(), 2, EventListener.class);
            return;
        }

        // Freeze Shield: no update check, the bundled anti-ESP is updated with Freeze Shield

        boolean hasBypassPermission = player.hasPermission("raycastedantiesp.bypass");
        playerData.setBypassPermission(hasBypassPermission);
        updateOwnLocation(playerData, player.getEyeLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        PlayerData playerData = PlayerRegistry.get().getPlayerData(event.getPlayer().getUniqueId());
        if (playerData == null) return;
        updateOwnLocation(playerData, event.getPlayer().getEyeLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        updateOwnLocation(event.getPlayer(), event.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        updateOwnLocation(event.getPlayer(), event.getPlayer().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        updateOwnLocation(event.getPlayer(), event.getRespawnLocation());
    }

    @EventHandler(priority = EventPriority.LOWEST) //Runs first
    public void serverTickStartEvent(ServerTickStartEvent event) {

    }

    @EventHandler(priority = EventPriority.MONITOR) //Runs last
    public void serverTickStopEvent(ServerTickEndEvent event) {
    }

    private void updateOwnLocation(PlayerData playerData, Location location) {
        if (playerData == null || location == null || location.getWorld() == null) {
            return;
        }
        playerData.updateOwnLocation(location.getWorld().getUID(), location.getX(), location.getY(), location.getZ());
    }

    private void updateOwnLocation(Player player, Location location) {
        PlayerData playerData = PlayerRegistry.get().getPlayerData(player.getUniqueId());
        if (playerData == null || location == null) {
            return;
        }

        Location eyeLocation = location.clone().add(0, player.getEyeHeight(), 0);
        updateOwnLocation(playerData, eyeLocation);
    }

}
