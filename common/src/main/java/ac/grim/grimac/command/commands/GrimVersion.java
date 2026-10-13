package ac.grim.grimac.command.commands;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.command.BuildableCommand;
import ac.grim.grimac.platform.api.manager.cloud.CloudPlatformCommandArguments;
import ac.grim.grimac.platform.api.sender.Sender;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;

// Freeze Shield doesn't query Grim's update server, so this only reports the installed version
public class GrimVersion implements BuildableCommand {

    @Override
    public void register(CommandManager<Sender> commandManager, CloudPlatformCommandArguments arguments) {
        commandManager.command(
                commandManager.commandBuilder("freezeshield", "fs")
                        .literal("version")
                        .permission("freezeshield.version")
                        .handler(this::handleVersion)
        );
    }

    private void handleVersion(@NotNull CommandContext<Sender> context) {
        context.sender().sendMessage(Component.text()
                .append(Component.text("Freeze Shield Version: ").color(NamedTextColor.GRAY))
                .append(Component.text(GrimAPI.INSTANCE.getExternalAPI().getGrimVersion()).color(NamedTextColor.AQUA))
                .build());
    }
}
