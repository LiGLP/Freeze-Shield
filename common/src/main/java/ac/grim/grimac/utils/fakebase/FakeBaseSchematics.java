package ac.grim.grimac.utils.fakebase;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.utils.anticheat.LogUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The bases {@link ac.grim.grimac.checks.impl.misc.FakeBases} picks from: the built-in ones plus every
 * {@code .schem} file in {@code plugins/FreezeShield/fake-bases/}. Reloaded with the config.
 */
public final class FakeBaseSchematics {
    public static final String FOLDER = "fake-bases";

    private static volatile List<BaseSchematic> schematics = BaseSchematic.BUILT_IN;

    private FakeBaseSchematics() {}

    public static List<BaseSchematic> get() {
        return schematics;
    }

    public static void reload(ConfigManager config) {
        boolean useBuiltIn = config.getBooleanElse("fake-bases.schematics.use-built-in", true);
        int maxSize = Math.max(1, config.getIntElse("fake-bases.schematics.max-size", 64));
        int cellSize = Math.max(24, config.getIntElse("fake-bases.cell-size", 32));
        Map<Object, Object> weights = config.getMapElse("fake-bases.schematics.weights", Map.of());

        List<BaseSchematic> loaded = new ArrayList<>();
        if (useBuiltIn) {
            for (BaseSchematic builtIn : BaseSchematic.BUILT_IN) {
                int weight = weight(weights, builtIn.name);
                if (weight > 0) loaded.add(new BaseSchematic(builtIn.name, weight, builtIn));
            }
        }

        File folder = new File(GrimAPI.INSTANCE.getGrimPlugin().getDataFolder(), FOLDER);
        if (!folder.isDirectory() && !folder.mkdirs()) {
            LogUtil.warn("Could not create the fake base schematic folder " + folder);
        }

        int fromFiles = 0;
        if (folder.isDirectory()) {
            try (DirectoryStream<Path> files = Files.newDirectoryStream(folder.toPath(), "*.{schem,SCHEM}")) {
                for (Path file : files) {
                    String fileName = file.getFileName().toString();
                    String name = fileName.substring(0, fileName.lastIndexOf('.')).toLowerCase(Locale.ROOT);
                    int weight = weight(weights, name);
                    if (weight <= 0) continue;

                    try {
                        SchematicReader.Result result = SchematicReader.read(file, name, weight, maxSize);
                        if (!result.unknownBlocks().isEmpty()) {
                            LogUtil.warn("Fake base " + fileName + " uses blocks this server doesn't have, they're left as rock: "
                                    + String.join(", ", result.unknownBlocks()));
                        }
                        BaseSchematic schematic = result.schematic();
                        if (Math.max(schematic.sizeX, schematic.sizeZ) > cellSize - 4) {
                            LogUtil.warn("Fake base " + fileName + " is " + schematic.sizeX + "x" + schematic.sizeZ + " wide, raise fake-bases.cell-size to at least "
                                    + (Math.max(schematic.sizeX, schematic.sizeZ) + 4) + " or it is never placed");
                        }
                        loaded.add(schematic);
                        fromFiles++;
                    } catch (IOException | RuntimeException e) {
                        LogUtil.warn("Skipping fake base " + fileName + ": " + e.getMessage());
                    }
                }
            } catch (IOException e) {
                LogUtil.error("Failed to list fake base schematics in " + folder, e);
            }
        }

        if (loaded.isEmpty()) {
            LogUtil.warn("No fake base schematics are enabled, falling back to the built-in ones");
            loaded.addAll(BaseSchematic.BUILT_IN);
        }

        schematics = List.copyOf(loaded);
        if (fromFiles > 0) LogUtil.info("Loaded " + fromFiles + " fake base schematic(s) from " + folder);
    }

    // Missing = 1, 0 or less disables it
    private static int weight(Map<Object, Object> weights, String name) {
        Object weight = weights.get(name);
        if (weight == null) return 1;
        if (weight instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(weight.toString().trim());
        } catch (NumberFormatException e) {
            LogUtil.warn("Fake base weight for " + name + " isn't a number: " + weight);
            return 1;
        }
    }
}
