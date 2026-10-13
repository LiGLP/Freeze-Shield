package ac.grim.grimac.checks.impl.misc;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.checks.GrimProcessor;
import ac.grim.grimac.checks.type.PacketReceiveListener;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.LogUtil;
import ac.grim.grimac.utils.anticheat.MessageUtil;
import ac.grim.grimac.utils.freeze.FreezeLogs;
import ac.grim.grimac.utils.freeze.TempBans;
import ac.grim.grimac.utils.math.GrimMath;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.nbt.NBTByte;
import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.nbt.NBTList;
import com.github.retrooper.packetevents.protocol.nbt.NBTString;
import com.github.retrooper.packetevents.protocol.nbt.NBTType;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.world.blockentity.BlockEntityTypes;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.type.StateType;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientUpdateSign;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockEntityData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerCloseWindow;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerOpenSignEditor;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import static com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying.isFlying;

/**
 * Detects blacklisted client mods (e.g. Meteor Client) shortly after join.
 * <p>
 * Brands are trivially spoofed (Meteor reports "vanilla" by default), so instead we ask the client to resolve
 * keybind components. A client only resolves a keybind it has registered: vanilla returns the raw key
 * (e.g. "key.meteor-client.open-gui"), a client with the mod returns the bound key's name (e.g. "Right Shift").
 * <p>
 * The probe places a client-side sign holding the keybinds, opens the sign editor and closes it in the same tick.
 * The client answers with the resolved text, which we cancel so the server never sees it. A vanilla keybind
 * is sent alongside as a control; if that one doesn't resolve either, the client blocks this technique and
 * the result is inconclusive.
 */
public class ClientDetection extends GrimProcessor implements PacketReceiveListener {

    private static final ServerVersion SERVER_VERSION = PacketEvents.getAPI().getServerManager().getVersion();
    private static final ClientVersion SERVER_STATE_VERSION = SERVER_VERSION.toClientVersion();
    private static final String CONTROL_KEY = "key.jump";
    private static final int KEYS_PER_PROBE = 3; // line 0 is the control
    private static final int RESPONSE_TIMEOUT_TICKS = 100;
    private static final int MAX_PLACEMENT_ATTEMPTS = 10;

    private boolean enabled;
    private int delayTicks;
    private int kickDelayMinSeconds;
    private int kickDelayMaxSeconds;
    private boolean kickOnNoResponse;
    private String kickMessage;
    private String alertFormat;
    private final Set<String> banClients = new HashSet<>();
    private int banDays;
    private String banReason;
    private String banMessage;
    private final Map<String, String> keyToClient = new LinkedHashMap<>();

    private State state = State.WAITING;
    private int ticks;
    private int placementAttempts;
    private final List<List<String>> pendingProbes = new ArrayList<>();
    private List<String> currentProbe;
    private Vector3i probePosition;
    private String detectedClient;
    private long kickAt;
    private String pendingKickMessage;

    public ClientDetection(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        enabled = config.getBooleanElse("client-detection.enabled", true);
        delayTicks = Math.max(1, config.getIntElse("client-detection.delay-ticks", 40));
        kickDelayMinSeconds = Math.max(0, config.getIntElse("client-detection.kick-delay.min-seconds", 3));
        kickDelayMaxSeconds = Math.max(kickDelayMinSeconds, config.getIntElse("client-detection.kick-delay.max-seconds", 15));
        kickOnNoResponse = config.getBooleanElse("client-detection.kick-on-no-response", false);
        kickMessage = config.getStringElse("client-detection.kick-message", "<lang:disconnect.timeout>");
        alertFormat = config.getStringElse("client-detection.alert-format", "%prefix% &f%player% &cwas detected using &f%client%");

        banClients.clear();
        banClients.addAll(config.getStringListElse("client-detection.ban.clients", List.of("Meteor Client")));
        banDays = Math.max(1, config.getIntElse("client-detection.ban.days", 3));
        banReason = config.getStringElse("client-detection.ban.reason",
                "%client% detected | Unban: open a ticket on discord.gg/freezesmp-official");
        banMessage = config.getStringElse("client-detection.ban.message",
                "<red><bold>You have been banned for %days% days</bold><newline><newline><gray>Reason: <white>%client% detected"
                        + "<newline><newline><gray>Think this is a mistake? Open a ticket for an unban:<newline><aqua>discord.gg/freezesmp-official");

        keyToClient.clear();
        Map<Object, Object> clients = config.getMapElse("client-detection.clients", null);
        if (clients == null) {
            clients = Map.of(
                    "Meteor Client", List.of("key.meteor-client.open-gui", "key.meteor-client.open-commands"),
                    // key.freecam.toggle exists in every version, the other two cover current and older releases
                    "Freecam", List.of("key.freecam.toggle", "key.freecam.controlPlayer.toggle", "key.freecam.playerControl"));
        }

        for (Map.Entry<Object, Object> entry : clients.entrySet()) {
            if (!(entry.getValue() instanceof List<?> keys)) continue;
            for (Object key : keys) {
                if (key != null) keyToClient.put(key.toString(), entry.getKey().toString());
            }
        }
    }

    @Override
    public void onPacketReceive(final PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.UPDATE_SIGN) {
            handleSignUpdate(event);
            return;
        }

        if (!isFlying(event.getPacketType())) return;

        switch (state) {
            case WAITING -> {
                if (!enabled || keyToClient.isEmpty() || !isSupported()) {
                    state = State.DONE;
                    return;
                }
                if (!player.getSetbackTeleportUtil().hasAcceptedSpawnTeleport) return;
                if (++ticks < delayTicks) return;

                List<String> keys = new ArrayList<>(keyToClient.keySet());
                for (int i = 0; i < keys.size(); i += KEYS_PER_PROBE) {
                    pendingProbes.add(keys.subList(i, Math.min(keys.size(), i + KEYS_PER_PROBE)));
                }
                state = State.READY;
            }
            case READY -> sendNextProbe();
            case AWAITING_RESPONSE -> {
                if (++ticks > RESPONSE_TIMEOUT_TICKS) {
                    LogUtil.info(player.getName() + " did not answer the client detection probe");
                    if (kickOnNoResponse) {
                        detect("unknown (probe ignored)");
                    } else {
                        state = State.DONE;
                    }
                }
            }
            case KICK_PENDING -> {
                if (System.currentTimeMillis() >= kickAt) {
                    state = State.DONE;
                    player.disconnect(MessageUtil.miniMessage(MessageUtil.replacePlaceholders(player, pendingKickMessage)));
                }
            }
            case DONE -> {}
        }
    }

    private static boolean isSupported() {
        // Keybind components and the sign editor packets we rely on; Meteor itself only exists for modern versions
        return SERVER_VERSION.isNewerThanOrEquals(ServerVersion.V_1_13);
    }

    private void sendNextProbe() {
        if (pendingProbes.isEmpty()) {
            state = State.DONE;
            return;
        }

        if (player.getClientVersion().isOlderThan(ClientVersion.V_1_16)) {
            state = State.DONE;
            return;
        }

        Vector3i position = findProbePosition();
        if (position == null) {
            // Retry next tick, the player is probably in a tight space or in a fluid
            if (++placementAttempts > MAX_PLACEMENT_ATTEMPTS * 20) state = State.DONE;
            return;
        }

        currentProbe = pendingProbes.remove(0);
        probePosition = position;
        ticks = 0;
        state = State.AWAITING_RESPONSE;

        StateType originalType = player.compensatedWorld.getBlock(position).getType();
        WrappedBlockState sign = WrappedBlockState.getDefaultState(SERVER_STATE_VERSION, StateTypes.OAK_SIGN);
        WrappedBlockState original = WrappedBlockState.getDefaultState(SERVER_STATE_VERSION, originalType);

        // Sent silently so neither Grim's world tracking nor other plugins see the fake sign
        send(new WrapperPlayServerBlockChange(position, sign.getGlobalId()));
        send(new WrapperPlayServerBlockEntityData(position, BlockEntityTypes.SIGN, buildSignNbt(currentProbe)));
        send(new WrapperPlayServerOpenSignEditor(position, true));
        send(new WrapperPlayServerCloseWindow(0));
        send(new WrapperPlayServerBlockChange(position, original.getGlobalId()));
    }

    private void send(PacketWrapper<?> wrapper) {
        PacketEvents.getAPI().getProtocolManager().sendPacketSilently(player.user.getChannel(), wrapper);
    }

    // An air block within sign editing range; a sign has no collision, so swapping it in for one tick
    // cannot change the player's movement
    private Vector3i findProbePosition() {
        int x = GrimMath.floor(player.x);
        int z = GrimMath.floor(player.z);
        int eyeY = GrimMath.floor(player.y + player.getEyeHeight());
        int feetY = GrimMath.floor(player.y);

        int[][] candidates = {
                {x, eyeY, z}, {x, eyeY + 1, z}, {x, feetY, z},
                {x + 1, eyeY, z}, {x - 1, eyeY, z}, {x, eyeY, z + 1}, {x, eyeY, z - 1},
                {x + 1, feetY, z}, {x - 1, feetY, z}, {x, feetY, z + 1}, {x, feetY, z - 1},
        };

        for (int[] candidate : candidates) {
            if (!player.compensatedWorld.isChunkLoaded(candidate[0] >> 4, candidate[2] >> 4)) continue;
            if (player.compensatedWorld.getBlock(candidate[0], candidate[1], candidate[2]).getType().isAir()) {
                return new Vector3i(candidate[0], candidate[1], candidate[2]);
            }
        }

        return null;
    }

    private static NBTCompound buildSignNbt(List<String> keys) {
        List<String> lines = new ArrayList<>(4);
        lines.add(CONTROL_KEY);
        lines.addAll(keys);

        NBTCompound nbt = new NBTCompound();
        if (SERVER_VERSION.isOlderThan(ServerVersion.V_1_20)) {
            for (int i = 0; i < 4; i++) {
                nbt.setTag("Text" + (i + 1), new NBTString(i < lines.size() ? keybindJson(lines.get(i)) : "\"\""));
            }
            return nbt;
        }

        NBTCompound front = new NBTCompound();
        if (SERVER_VERSION.isNewerThanOrEquals(ServerVersion.V_1_21_5)) {
            // 1.21.5+ stores text components as NBT instead of JSON strings
            NBTList<NBTCompound> messages = new NBTList<>(NBTType.COMPOUND);
            for (int i = 0; i < 4; i++) {
                NBTCompound component = new NBTCompound();
                if (i < lines.size()) {
                    component.setTag("keybind", new NBTString(lines.get(i)));
                } else {
                    component.setTag("text", new NBTString(""));
                }
                messages.addTag(component);
            }
            front.setTag("messages", messages);
        } else {
            NBTList<NBTString> messages = new NBTList<>(NBTType.STRING);
            for (int i = 0; i < 4; i++) {
                messages.addTag(new NBTString(i < lines.size() ? keybindJson(lines.get(i)) : "\"\""));
            }
            front.setTag("messages", messages);
        }
        front.setTag("color", new NBTString("black"));
        front.setTag("has_glowing_text", new NBTByte((byte) 0));

        nbt.setTag("front_text", front);
        nbt.setTag("is_waxed", new NBTByte((byte) 0));
        return nbt;
    }

    private static String keybindJson(String key) {
        return "{\"keybind\":\"" + key.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
    }

    private void handleSignUpdate(PacketReceiveEvent event) {
        if (state != State.AWAITING_RESPONSE) return;

        WrapperPlayClientUpdateSign packet = new WrapperPlayClientUpdateSign(event);
        if (!packet.getBlockPosition().equals(probePosition)) return;

        // The sign never existed on the server, don't let it see the edit
        event.setCancelled(true);

        String[] lines = packet.getTextLines();
        if (lines == null || lines.length < 1 + currentProbe.size()) {
            state = State.DONE;
            return;
        }

        if (lines[0].equals(CONTROL_KEY)) {
            // Even the vanilla keybind didn't resolve: an anti-detection mod or a client that patched this
            LogUtil.info(player.getName() + " blocks keybind resolution, client detection is inconclusive");
            state = State.DONE;
            return;
        }

        for (int i = 0; i < currentProbe.size(); i++) {
            String key = currentProbe.get(i);
            String resolved = lines[i + 1];
            if (!resolved.isEmpty() && !resolved.equals(key)) {
                detect(keyToClient.get(key));
                return;
            }
        }

        state = State.READY;
    }

    private void detect(String client) {
        detectedClient = client;
        LogUtil.info(player.getName() + " was detected using " + client);

        String message = alertFormat.replace("%client%", client);
        Component component = MessageUtil.replacePlaceholders(player, MessageUtil.miniMessage(message));
        GrimAPI.INSTANCE.getAlertManager().sendAlert(component, null);

        // Banned right away so leaving before the kick doesn't avoid it
        String action = "Kicked";
        pendingKickMessage = kickMessage;
        if (banClients.contains(client)) {
            Duration duration = Duration.ofDays(banDays);
            String days = Integer.toString(banDays);
            String reason = banReason.replace("%client%", client).replace("%days%", days);
            if (TempBans.ban(player.getUniqueId(), player.getName(), reason, duration, "Freeze Shield")) {
                action = "Banned for " + banDays + (banDays == 1 ? " day" : " days");
                pendingKickMessage = banMessage.replace("%client%", client).replace("%days%", days);
                LogUtil.info("Banned " + player.getName() + " for " + banDays + " days (" + client + ")");
                FreezeLogs.banned(player.getName(), player.getUniqueId(), reason, duration);
            }
        }
        FreezeLogs.clientDetected(player.getName(), player.getUniqueId(), client, action);

        // A random delay so the kick isn't obviously tied to joining
        int delay = kickDelayMinSeconds == kickDelayMaxSeconds ? kickDelayMinSeconds
                : ThreadLocalRandom.current().nextInt(kickDelayMinSeconds, kickDelayMaxSeconds + 1);
        kickAt = System.currentTimeMillis() + delay * 1000L;
        state = State.KICK_PENDING;
    }

    public String getDetectedClient() {
        return detectedClient;
    }

    private enum State {
        WAITING,
        READY,
        AWAITING_RESPONSE,
        KICK_PENDING,
        DONE
    }
}
