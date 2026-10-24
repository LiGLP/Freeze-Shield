package ac.grim.grimac.utils.fakebase;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.utils.anticheat.LogUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The bases {@link ac.grim.grimac.checks.impl.misc.FakeBases} picks from: the built-in ones plus every
 * {@code .schem} file in {@code plugins/FreezeShield/fake-bases/}. Reloaded with the config.
 * <p>
 * The schematics bundled with Freeze Shield are copied into that folder once; deleting one keeps it deleted.
 */
public final class FakeBaseSchematics {
    public static final String FOLDER = "fake-bases";
    // Remembers which bundled files were already copied out, so deleted ones don't come back
    private static final String BUNDLED_MARKER = ".bundled";

    /** Schematics shipped in the jar under {@code fake-bases/}. */
    private static final List<String> BUNDLED = List.of(
            "bonemealfarm", "kelpfarm", "sugarcanefarm", "smallbambofarm", "bigbambofarm",
            "greekstash", "icestash", "magicalstash", "blackstonestash");

    // Farms stand in a room dug a bit bigger than them, like a real one: {sides, top}
    private static final Map<String, int[]> DEFAULT_ROOMS = Map.of(
            "bonemealfarm", new int[]{3, 2},
            "kelpfarm", new int[]{3, 2},
            "sugarcanefarm", new int[]{3, 2},
            "smallbambofarm", new int[]{3, 2},
            "bigbambofarm", new int[]{3, 2});
    private static final int[] NO_ROOM = {0, 0};

    private static volatile List<BaseSchematic> schematics = BaseSchematic.BUILT_IN;

    private FakeBaseSchematics() {}

    public static List<BaseSchematic> get() {
        return schematics;
    }

    public static void reload(ConfigManager config) {
        boolean useBuiltIn = config.getBooleanElse("fake-bases.schematics.use-built-in", true);
        int maxSize = Math.max(1, config.getIntElse("fake-bases.schematics.max-size", 128));
        Map<Object, Object> weights = config.getMapElse("fake-bases.schematics.weights", Map.of());
        Map<Object, Object> rooms = config.getMapElse("fake-bases.schematics.rooms", Map.of());

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
            extractBundled(folder.toPath());

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
                        int[] room = room(rooms, name);
                        loaded.add(result.schematic().withRoom(room[0], room[1]));
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

    private static void extractBundled(Path folder) {
        Path marker = folder.resolve(BUNDLED_MARKER);
        Set<String> done = new LinkedHashSet<>();
        try {
            if (Files.isRegularFile(marker)) {
                for (String line : Files.readAllLines(marker, StandardCharsets.UTF_8)) {
                    if (!line.isBlank()) done.add(line.trim());
                }
            }
        } catch (IOException e) {
            LogUtil.warn("Could not read " + marker + ": " + e.getMessage());
            return; // better than bringing back schematics the admin deleted
        }

        boolean changed = false;
        for (String name : BUNDLED) {
            if (done.contains(name)) continue;
            Path target = folder.resolve(name + ".schem");
            if (!Files.exists(target)) {
                try (InputStream in = FakeBaseSchematics.class.getResourceAsStream("/" + FOLDER + "/" + name + ".schem")) {
                    if (in == null) {
                        LogUtil.warn("Bundled fake base " + name + ".schem is missing from the jar");
                        continue;
                    }
                    Files.copy(in, target);
                } catch (IOException e) {
                    LogUtil.warn("Could not copy the bundled fake base " + name + ".schem: " + e.getMessage());
                    continue;
                }
            }
            done.add(name);
            changed = true;
        }

        if (changed) {
            try {
                Files.write(marker, done, StandardCharsets.UTF_8);
            } catch (IOException e) {
                LogUtil.warn("Could not write " + marker + ": " + e.getMessage());
            }
        }
    }

    // Missing = 1, 0 or less disables it
    private static int weight(Map<Object, Object> weights, String name) {
        Object weight = weights.get(name);
        if (weight == null) return 1;
        Integer parsed = parse(weight);
        if (parsed == null) {
            LogUtil.warn("Fake base weight for " + name + " isn't a number: " + weight);
            return 1;
        }
        return parsed;
    }

    // "3" or "3 2": blocks of air on each side, then above (above defaults to the sides)
    private static int[] room(Map<Object, Object> rooms, String name) {
        Object room = rooms.get(name);
        if (room == null) return DEFAULT_ROOMS.getOrDefault(name, NO_ROOM);

        String[] parts = room.toString().trim().split("[ ,]+");
        Integer sides = parse(parts[0]);
        Integer top = parts.length > 1 ? parse(parts[1]) : sides;
        if (sides == null || top == null) {
            LogUtil.warn("Fake base room for " + name + " should be \"<sides> <top>\", got: " + room);
            return DEFAULT_ROOMS.getOrDefault(name, NO_ROOM);
        }
        return new int[]{sides, top};
    }

    private static Integer parse(Object value) {
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
