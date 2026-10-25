package ac.grim.grimac.utils.freeze;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.api.config.ConfigManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Discord logs sent through the freeze-api bot. Channel keys and the on/off switch live in discord.yml
 * under {@code freeze-api}.
 */
public final class FreezeLogs {

    private static final int RED = 0xE53935;
    private static final int ORANGE = 0xFB8C00;

    private FreezeLogs() {
    }

    private static ConfigManager config() {
        return GrimAPI.INSTANCE.getConfigManager().getConfig();
    }

    public static boolean enabled() {
        return config().getBooleanElse("freeze-api.enabled", true);
    }

    /** @return true if alerts can go through the freeze-api bot instead of the webhook */
    public static boolean available() {
        return enabled() && FreezeApiBridge.isInstalled();
    }

    public static String anticheatChannel() {
        return config().getStringElse("freeze-api.anticheat-channel", "anticheat-log");
    }

    public static String banChannel() {
        return config().getStringElse("freeze-api.ban-channel", "ban-log");
    }

    /** Queues a raw message to the anticheat log. @return false if it wasn't queued */
    public static boolean anticheat(String json) {
        return enabled() && FreezeApiBridge.queueDiscord(anticheatChannel(), json);
    }

    /** A blacklisted client was found on join, {@code action} is e.g. "Kicked" or "Banned for 3 days". */
    public static void clientDetected(String player, UUID uuid, String client, String action) {
        JsonObject embed = embed("Hacked client detected", ORANGE, uuid);
        JsonArray fields = new JsonArray();
        fields.add(field("Player", "`" + player + "`", true));
        fields.add(field("Client", client, true));
        fields.add(field("Action", action, true));
        embed.add("fields", fields);
        anticheat(message(embed, null).toString());
    }

    /** An automatic ban; posted to the ban log with freeze-api's Unban button. */
    public static void banned(String player, UUID uuid, String reason, Duration duration) {
        if (!enabled()) return;
        Instant expires = Instant.now().plus(duration);
        long days = duration.toDays();
        JsonObject embed = embed("Player Banned", RED, uuid);
        JsonArray fields = new JsonArray();
        fields.add(field("Player", "`" + player + "`", true));
        fields.add(field("Banned by", "`Freeze Shield`", true));
        fields.add(field("Reason", reason, false));
        fields.add(field("Duration", days + (days == 1 ? " day" : " days"), true));
        fields.add(field("Account Wipe", "No", true));
        fields.add(field("Expires", "<t:" + expires.getEpochSecond() + ":F> (<t:" + expires.getEpochSecond() + ":R>)", false));
        fields.add(field("Unban", "```/unban " + player + "```", false));
        embed.add("fields", fields);
        JsonObject footer = new JsonObject();
        footer.addProperty("text", "Automatic ban by Freeze Shield");
        embed.add("footer", footer);

        JsonObject unban = new JsonObject();
        unban.addProperty("uuid", uuid.toString());
        unban.addProperty("name", player);
        FreezeApiBridge.queueDiscord(banChannel(), message(embed, unban).toString());
    }

    private static JsonObject embed(String title, int color, UUID uuid) {
        JsonObject embed = new JsonObject();
        embed.addProperty("title", title);
        embed.addProperty("color", color);
        embed.addProperty("timestamp", Instant.now().toString());
        JsonObject thumb = new JsonObject();
        thumb.addProperty("url", "https://mc-heads.net/avatar/" + uuid + "/64");
        embed.add("thumbnail", thumb);
        return embed;
    }

    private static JsonObject field(String name, String value, boolean inline) {
        JsonObject field = new JsonObject();
        field.addProperty("name", name);
        field.addProperty("value", value);
        field.addProperty("inline", inline);
        return field;
    }

    private static JsonObject message(JsonObject embed, JsonObject unban) {
        JsonObject message = new JsonObject();
        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        message.add("embeds", embeds);
        if (unban != null) message.add("unban", unban);
        return message;
    }
}
