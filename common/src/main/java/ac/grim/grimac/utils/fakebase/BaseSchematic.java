package ac.grim.grimac.utils.fakebase;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.world.BlockFace;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.enums.Axis;
import com.github.retrooper.packetevents.protocol.world.states.type.StateType;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import com.github.retrooper.packetevents.protocol.world.states.type.StateValue;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import java.util.List;
import java.util.Map;

/**
 * A decoy base for {@link ac.grim.grimac.checks.impl.misc.FakeBases}, written as layers from the floor up.
 * Each layer is a list of rows along Z, each row a string along X. A '.' is air (the base is hollowed out of the
 * rock), any other character comes from {@link #PALETTE}. More bases can be loaded from WorldEdit schematics,
 * see {@link FakeBaseSchematics}.
 */
public final class BaseSchematic {
    private static final ClientVersion SERVER_STATE_VERSION = PacketEvents.getAPI().getServerManager().getVersion().toClientVersion();

    private static final Map<Character, StateType> PALETTE = Map.ofEntries(
            Map.entry('.', StateTypes.AIR),
            Map.entry('O', StateTypes.OBSIDIAN),
            Map.entry('P', StateTypes.STONE_BRICKS),
            Map.entry('Y', StateTypes.COBBLESTONE),
            Map.entry('M', StateTypes.MOSSY_COBBLESTONE),
            Map.entry('W', StateTypes.OAK_PLANKS),
            Map.entry('C', StateTypes.CHEST),
            Map.entry('E', StateTypes.ENDER_CHEST),
            Map.entry('S', StateTypes.PURPLE_SHULKER_BOX),
            Map.entry('B', StateTypes.BARREL),
            Map.entry('F', StateTypes.FURNACE),
            Map.entry('T', StateTypes.CRAFTING_TABLE),
            Map.entry('A', StateTypes.ANVIL),
            Map.entry('N', StateTypes.ENCHANTING_TABLE),
            Map.entry('Q', StateTypes.BOOKSHELF),
            Map.entry('H', StateTypes.HOPPER),
            Map.entry('X', StateTypes.SPAWNER),
            Map.entry('K', StateTypes.BEACON),
            Map.entry('I', StateTypes.IRON_BLOCK),
            Map.entry('D', StateTypes.DIAMOND_BLOCK),
            Map.entry('G', StateTypes.GLOWSTONE),
            Map.entry('L', StateTypes.LANTERN)
    );

    public static final List<BaseSchematic> BUILT_IN = List.of(
            new BaseSchematic("stash",
                    layer("OOOOOOO", "OOOOOOO", "OOOOOOO", "OOOOOOO", "OOOOOOO", "OOOOOOO", "OOOOOOO"),
                    layer("OOOOOOO", "OSCSCSO", "OC...CO", "OS...SO", "OC...CO", "OSCSCSO", "OOOOOOO"),
                    layer("OOOOOOO", "OSESESO", "OE...EO", "OS...SO", "OE...EO", "OSESESO", "OOOOOOO"),
                    layer("OOOOOOO", "O.....O", "O.....O", "O..L..O", "O.....O", "O.....O", "OOOOOOO"),
                    layer("OOOOOOO", "OOOOOOO", "OOOOOOO", "OOOGOOO", "OOOOOOO", "OOOOOOO", "OOOOOOO")),
            new BaseSchematic("bunker",
                    layer("PPPPPPPPPPP", "PPPPPPPPPPP", "PPPPPPPPPPP", "PPPPPPPPPPP", "PPPPPPPPPPP", "PPPPPPPPPPP", "PPPPPPPPPPP", "PPPPPPPPPPP", "PPPPPPPPPPP"),
                    layer("PPPPPPPPPPP", "PCCCC.BBBBP", "P.........P", "PF.......TP", "PF...A...TP", "PF.......NP", "P.........P", "PCCCC.BBBBP", "PPPPPPPPPPP"),
                    layer("PPPPPPPPPPP", "PCCCC.BBBBP", "P.........P", "P.........P", "P.........P", "P.........P", "P.........P", "PCCCC.BBBBP", "PPPPPPPPPPP"),
                    layer("PPPPPPPPPPP", "P.........P", "P.........P", "P.........P", "P.........P", "P.........P", "P.........P", "P.........P", "PPPPPPPPPPP"),
                    layer("PPPPPPPPPPP", "P.........P", "P.........P", "P.........P", "P.........P", "P.........P", "P.........P", "P.........P", "PPPPPPPPPPP"),
                    layer("PPPPPPPPPPP", "PPPPPPPPPPP", "PPGPPPPPGPP", "PPPPPPPPPPP", "PPPPPGPPPPP", "PPPPPPPPPPP", "PPGPPPPPGPP", "PPPPPPPPPPP", "PPPPPPPPPPP")),
            new BaseSchematic("spawner",
                    layer("YYMYYYMYY", "YYYYMYYYY", "MYYYYYYYM", "YYYMYYYYY", "YYYYYYMYY", "YMYYYYYYY", "YYYYYMYYY", "YYMYYYYMY", "YYYYMYYYY"),
                    layer("YYMYYYMYY", "YC.....CY", "Y.......Y", "Y.......Y", "M...X...M", "Y.......Y", "Y.......Y", "YC.....CY", "YYMYYYMYY"),
                    layer("YYMYYYMYY", "Y.......Y", "Y.......Y", "Y.......Y", "M.......M", "Y.......Y", "Y.......Y", "Y.......Y", "YYMYYYMYY"),
                    layer("YYYYMYYYY", "Y.......Y", "M.......M", "Y.......Y", "Y.......Y", "Y.......Y", "M.......M", "Y.......Y", "YYYYMYYYY"),
                    layer("YYMYYYMYY", "Y.......Y", "Y.......Y", "Y.......Y", "M.......M", "Y.......Y", "Y.......Y", "Y.......Y", "YYMYYYMYY"),
                    layer("YYYYYYYYY", "YYMYYYYYY", "YYYYYYMYY", "YYYYYYYYY", "YMYYYYYYY", "YYYYYYYYY", "YYYYMYYYY", "YYYYYYYMY", "YYYYYYYYY")),
            new BaseSchematic("enchanting",
                    layer("WWWWWWWWW", "WWWWWWWWW", "WWWWWWWWW", "WWWWWWWWW", "WWWWWWWWW", "WWWWWWWWW", "WWWWWWWWW", "WWWWWWWWW", "WWWWWWWWW"),
                    layer("WWWWWWWWW", "W.......W", "W.QQQQQ.W", "W.Q...Q.W", "W.Q.N.Q.W", "W.Q...Q.W", "W.QQ.QQ.W", "W.......W", "WWWWWWWWW"),
                    layer("WWWWWWWWW", "WA.....CW", "W.QQQQQ.W", "W.Q...Q.W", "W.Q...Q.W", "W.Q...Q.W", "W.QQ.QQ.W", "WC.....EW", "WWWWWWWWW"),
                    layer("WWWWWWWWW", "W.......W", "W.......W", "W.......W", "W...L...W", "W.......W", "W.......W", "W.......W", "WWWWWWWWW"),
                    layer("WWWWWWWWW", "WWWWWWWWW", "WWWWWWWWW", "WWWWWWWWW", "WWWWGWWWW", "WWWWWWWWW", "WWWWWWWWW", "WWWWWWWWW", "WWWWWWWWW")),
            new BaseSchematic("vault",
                    layer("OOOOOOOOO", "OOOOOOOOO", "OOOOOOOOO", "OOOOOOOOO", "OOOOOOOOO", "OOOOOOOOO", "OOOOOOOOO", "OOOOOOOOO", "OOOOOOOOO"),
                    layer("OOOOOOOOO", "OSSS.SSSO", "OS.....SO", "OS.III.SO", "O..III..O", "OS.III.SO", "OS.....SO", "OSSS.SSSO", "OOOOOOOOO"),
                    layer("OOOOOOOOO", "ODDD.DDDO", "OD.....DO", "OD.....DO", "O...K...O", "OD.....DO", "OD.....DO", "ODDD.DDDO", "OOOOOOOOO"),
                    layer("OOOOOOOOO", "O.......O", "O.......O", "O.......O", "O.......O", "O.......O", "O.......O", "O.......O", "OOOOOOOOO"),
                    layer("OOOOOOOOO", "O.......O", "O.......O", "O.......O", "O.......O", "O.......O", "O.......O", "O.......O", "OOOOOOOOO"),
                    layer("OOOOOOOOO", "OGOOOOOGO", "OOOOOOOOO", "OOOOOOOOO", "OOOOOOOOO", "OOOOOOOOO", "OOOOOOOOO", "OGOOOOOGO", "OOOOOOOOO")),
            new BaseSchematic("storage",
                    layer("PPPPPPPPPPPPPPP", "PPPPPPPPPPPPPPP", "PPPPPPPPPPPPPPP", "PPPPPPPPPPPPPPP", "PPPPPPPPPPPPPPP", "PPPPPPPPPPPPPPP", "PPPPPPPPPPPPPPP"),
                    layer("PPPPPPPPPPPPPPP", "PCCCCCCCCCCCCCP", "P.............P", "PF...H...H...TP", "P.............P", "PBBBBBBBBBBBBBP", "PPPPPPPPPPPPPPP"),
                    layer("PPPPPPPPPPPPPPP", "PCCCCCCCCCCCCCP", "P.............P", "P.............P", "P.............P", "PBBBBBBBBBBBBBP", "PPPPPPPPPPPPPPP"),
                    layer("PPPPPPPPPPPPPPP", "P.............P", "P.............P", "P..L...L...L..P", "P.............P", "P.............P", "PPPPPPPPPPPPPPP"),
                    layer("PPPPPPPPPPPPPPP", "PPPPPPPPPPPPPPP", "PPPGPPPGPPPGPPP", "PPPPPPPPPPPPPPP", "PPPGPPPGPPPGPPP", "PPPPPPPPPPPPPPP", "PPPPPPPPPPPPPPP"))
    );

    /** Block id for "leave the rock that's there". */
    public static final int KEEP = -1;

    public final String name;
    /** How often this base is picked compared to the others. */
    public final int weight;
    public final int sizeX, sizeY, sizeZ;
    // [y][z][x] global block state ids, or KEEP
    private final int[][][] blocks;
    // Global id -> its id after 0-3 clockwise turns, only for blocks that face a direction
    private final Int2ObjectOpenHashMap<int[]> rotatedIds;

    /** @param blocks global block state ids (or {@link #KEEP}) indexed [y][z][x] */
    public BaseSchematic(String name, int weight, int[][][] blocks) {
        this.name = name;
        this.weight = Math.max(1, weight);
        this.blocks = blocks;
        this.sizeY = blocks.length;
        this.sizeZ = blocks[0].length;
        this.sizeX = blocks[0][0].length;
        this.rotatedIds = rotateStates(blocks);
    }

    /** The same blocks with another name and weight. */
    public BaseSchematic(String name, int weight, BaseSchematic copyOf) {
        this(name, weight, copyOf.blocks);
    }

    private BaseSchematic(String name, String[]... layers) {
        this(name, 1, parseLayers(name, layers));
    }

    private static int[][][] parseLayers(String name, String[]... layers) {
        int sizeY = layers.length;
        int sizeZ = layers[0].length;
        int sizeX = layers[0][0].length();
        int[][][] blocks = new int[sizeY][sizeZ][sizeX];

        for (int y = 0; y < sizeY; y++) {
            if (layers[y].length != sizeZ) throw new IllegalArgumentException(name + ": layer " + y + " has the wrong number of rows");
            for (int z = 0; z < sizeZ; z++) {
                String row = layers[y][z];
                if (row.length() != sizeX) throw new IllegalArgumentException(name + ": row " + z + " of layer " + y + " has the wrong length");
                for (int x = 0; x < sizeX; x++) {
                    StateType type = PALETTE.get(row.charAt(x));
                    if (type == null) throw new IllegalArgumentException(name + ": unknown block '" + row.charAt(x) + "'");
                    WrappedBlockState state = WrappedBlockState.getDefaultState(SERVER_STATE_VERSION, type);
                    // Blocks this server version doesn't have become obsidian
                    if (state == null || (state.getType().isAir() && !type.isAir())) {
                        state = WrappedBlockState.getDefaultState(SERVER_STATE_VERSION, StateTypes.OBSIDIAN);
                    }
                    blocks[y][z][x] = state.getGlobalId();
                }
            }
        }
        return blocks;
    }

    private static String[] layer(String... rows) {
        return rows;
    }

    /** Footprint along X once rotated by {@code rotation} quarter turns. */
    public int width(int rotation) {
        return (rotation & 1) == 0 ? sizeX : sizeZ;
    }

    /** Footprint along Z once rotated by {@code rotation} quarter turns. */
    public int depth(int rotation) {
        return (rotation & 1) == 0 ? sizeZ : sizeX;
    }

    /** Block at rotated coordinates, {@code 0 <= x < width(rotation)}, {@code 0 <= z < depth(rotation)}. */
    public int blockAt(int x, int y, int z, int rotation) {
        int id = switch (rotation & 3) {
            case 0 -> blocks[y][z][x];
            case 1 -> blocks[y][sizeZ - 1 - x][z];
            case 2 -> blocks[y][sizeZ - 1 - z][sizeX - 1 - x];
            default -> blocks[y][x][sizeX - 1 - z];
        };
        if (id == KEEP || (rotation & 3) == 0) return id;
        int[] turned = rotatedIds.get(id);
        return turned == null ? id : turned[rotation & 3];
    }

    // Each rotation turns the base clockwise, so directional blocks have to turn with it
    private static Int2ObjectOpenHashMap<int[]> rotateStates(int[][][] blocks) {
        Int2ObjectOpenHashMap<int[]> rotated = new Int2ObjectOpenHashMap<>();
        for (int[][] layer : blocks) {
            for (int[] row : layer) {
                for (int id : row) {
                    if (id == KEEP || rotated.containsKey(id)) continue;
                    WrappedBlockState state = WrappedBlockState.getByGlobalId(SERVER_STATE_VERSION, id, false);
                    Map<StateValue, Object> data = state.getInternalData();
                    boolean facing = data.get(StateValue.FACING) instanceof BlockFace face && face != BlockFace.UP && face != BlockFace.DOWN;
                    boolean rotation = data.containsKey(StateValue.ROTATION);
                    boolean axis = data.get(StateValue.AXIS) instanceof Axis a && a != Axis.Y;
                    if (!facing && !rotation && !axis) continue;

                    int[] turns = new int[4];
                    try {
                        turns[0] = id;
                        for (int turn = 1; turn < 4; turn++) {
                            WrappedBlockState turnedState = state.clone();
                            if (facing) {
                                BlockFace face = state.getFacing();
                                for (int i = 0; i < turn; i++) face = face.getCW();
                                turnedState.setFacing(face);
                            }
                            if (rotation) turnedState.setRotation((state.getRotation() + 4 * turn) & 15);
                            if (axis && (turn & 1) == 1) turnedState.setAxis(state.getAxis() == Axis.X ? Axis.Z : Axis.X);
                            turns[turn] = turnedState.getGlobalId();
                        }
                    } catch (RuntimeException e) {
                        continue; // a state that can't be turned, keep it as it is
                    }
                    rotated.put(id, turns);
                }
            }
        }
        return rotated;
    }
}
