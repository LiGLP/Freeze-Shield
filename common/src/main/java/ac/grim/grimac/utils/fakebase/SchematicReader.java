package ac.grim.grimac.utils.fakebase;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.type.StateType;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * Reads Sponge schematics ({@code .schem}, versions 1-3), the format WorldEdit and FAWE save with
 * {@code //schem save}. Only the blocks are read; block entity data (chest contents, sign text) is ignored.
 * <p>
 * Air is placed as air, so the base is hollowed out of the rock. Structure voids leave the rock untouched,
 * which is how to give a base an irregular shape.
 */
public final class SchematicReader {
    private static final ClientVersion SERVER_STATE_VERSION = PacketEvents.getAPI().getServerManager().getVersion().toClientVersion();
    private static final Set<String> AIR_NAMES = Set.of("air", "cave_air", "void_air");
    private static final int MAX_ARRAY_LENGTH = 16 * 1024 * 1024;

    private SchematicReader() {}

    /** The result of reading a file: the schematic, plus block names this server version doesn't know. */
    public record Result(BaseSchematic schematic, List<String> unknownBlocks) {}

    public static Result read(Path file, String name, int weight, int maxSize) throws IOException {
        Map<String, Object> root;
        try (InputStream in = Files.newInputStream(file)) {
            root = readRoot(in);
        }

        // Version 3 nests everything in a "Schematic" compound, versions 1 and 2 are the root itself
        Map<String, Object> schematic = root.get("Schematic") instanceof Map<?, ?> nested ? cast(nested) : root;

        int width = unsignedShort(schematic, "Width");
        int height = unsignedShort(schematic, "Height");
        int length = unsignedShort(schematic, "Length");
        if (width <= 0 || height <= 0 || length <= 0) throw new IOException("the schematic is empty");
        if (width > maxSize || height > maxSize || length > maxSize) {
            throw new IOException("it is " + width + "x" + height + "x" + length + ", bigger than max-size (" + maxSize + ")");
        }

        Map<String, Object> palette;
        byte[] data;
        if (schematic.get("Blocks") instanceof Map<?, ?> blocks) { // version 3
            palette = cast(blocks.get("Palette"));
            data = (byte[]) blocks.get("Data");
        } else if (schematic.containsKey("Palette")) { // versions 1 and 2
            palette = cast(schematic.get("Palette"));
            data = (byte[]) schematic.get("BlockData");
        } else {
            throw new IOException("not a Sponge schematic; legacy MCEdit .schematic files aren't supported, re-save it with //schem save");
        }
        if (palette == null || data == null) throw new IOException("the schematic has no blocks");

        List<String> unknown = new ArrayList<>();
        Map<Integer, Integer> paletteIds = new HashMap<>();
        for (Map.Entry<String, Object> entry : palette.entrySet()) {
            paletteIds.put(((Number) entry.getValue()).intValue(), toGlobalId(entry.getKey(), unknown));
        }

        // [y][z][x], stored in the file as varints in x, then z, then y order
        int[][][] ids = new int[height][length][width];
        int index = 0;
        int position = 0;
        while (index < data.length && position < width * height * length) {
            int value = 0;
            int shift = 0;
            byte b;
            do {
                b = data[index++];
                value |= (b & 0x7F) << shift;
                shift += 7;
                if (shift > 35) throw new IOException("corrupt block data");
            } while ((b & 0x80) != 0 && index < data.length);

            Integer id = paletteIds.get(value);
            int y = position / (width * length);
            int z = (position % (width * length)) / width;
            int x = position % width;
            ids[y][z][x] = id == null ? BaseSchematic.KEEP : id;
            position++;
        }
        if (position != width * height * length) throw new IOException("the block data is cut short");

        return new Result(new BaseSchematic(name, weight, ids), unknown);
    }

    private static int toGlobalId(String state, List<String> unknown) {
        String lower = state.toLowerCase(Locale.ROOT);
        int bracket = lower.indexOf('[');
        String block = (bracket == -1 ? lower : lower.substring(0, bracket)).replace("minecraft:", "");

        if (block.equals("structure_void")) return BaseSchematic.KEEP;
        if (AIR_NAMES.contains(block)) {
            return WrappedBlockState.getDefaultState(SERVER_STATE_VERSION, StateTypes.AIR).getGlobalId();
        }

        // Exact state first; unknown strings come back as air
        WrappedBlockState exact = WrappedBlockState.getByString(SERVER_STATE_VERSION, lower, false);
        if (exact != null && !exact.getType().isAir()) return exact.getGlobalId();

        // Properties from another version or in another order, fall back to the block's default state
        StateType type = StateTypes.getByName(block);
        if (type != null) {
            WrappedBlockState fallback = WrappedBlockState.getDefaultState(SERVER_STATE_VERSION, type);
            if (fallback != null && !fallback.getType().isAir()) return fallback.getGlobalId();
        }

        if (!unknown.contains(block)) unknown.add(block);
        return BaseSchematic.KEEP;
    }

    private static int unsignedShort(Map<String, Object> compound, String key) throws IOException {
        if (!(compound.get(key) instanceof Number number)) throw new IOException("missing " + key);
        return number.intValue() & 0xFFFF;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Object compound) {
        return (Map<String, Object>) compound;
    }

    // Minimal NBT reader: compounds become maps, lists become lists, arrays stay arrays

    private static Map<String, Object> readRoot(InputStream raw) throws IOException {
        BufferedInputStream buffered = new BufferedInputStream(raw);
        buffered.mark(2);
        int magic = buffered.read() | (buffered.read() << 8);
        buffered.reset();
        InputStream in = magic == GZIPInputStream.GZIP_MAGIC ? new GZIPInputStream(buffered) : buffered;

        DataInputStream data = new DataInputStream(in);
        if (data.readByte() != 10) throw new IOException("not an NBT file");
        data.readUTF(); // root name
        return cast(readPayload(data, 10, 0));
    }

    private static Object readPayload(DataInputStream in, int type, int depth) throws IOException {
        if (depth > 64) throw new IOException("NBT nested too deeply");
        return switch (type) {
            case 1 -> in.readByte();
            case 2 -> in.readShort();
            case 3 -> in.readInt();
            case 4 -> in.readLong();
            case 5 -> in.readFloat();
            case 6 -> in.readDouble();
            case 7 -> {
                byte[] bytes = new byte[arrayLength(in)];
                in.readFully(bytes);
                yield bytes;
            }
            case 8 -> in.readUTF();
            case 9 -> {
                int elementType = in.readByte();
                int size = arrayLength(in);
                List<Object> list = new ArrayList<>(Math.min(size, 1024));
                for (int i = 0; i < size; i++) list.add(readPayload(in, elementType, depth + 1));
                yield list;
            }
            case 10 -> {
                Map<String, Object> compound = new HashMap<>();
                int childType;
                while ((childType = in.readByte()) != 0) {
                    String key = in.readUTF();
                    compound.put(key, readPayload(in, childType, depth + 1));
                }
                yield compound;
            }
            case 11 -> {
                int[] ints = new int[arrayLength(in)];
                for (int i = 0; i < ints.length; i++) ints[i] = in.readInt();
                yield ints;
            }
            case 12 -> {
                long[] longs = new long[arrayLength(in)];
                for (int i = 0; i < longs.length; i++) longs[i] = in.readLong();
                yield longs;
            }
            default -> throw new IOException("unknown NBT tag type " + type);
        };
    }

    private static int arrayLength(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > MAX_ARRAY_LENGTH) throw new IOException("NBT array too large: " + length);
        return length;
    }
}
