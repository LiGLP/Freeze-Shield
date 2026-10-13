package games.cubi.locatables.api;

/** A spatial whose exact coordinates are integral block coordinates. */
public sealed interface BlockSpatial extends Spatial permits BlockLocatable, ImmutableBlockSpatial, MutableBlockSpatial {
    @Override
    int blockX();

    @Override
    int blockY();

    @Override
    int blockZ();

    @Override
    default double x() {
        return blockX();
    }

    @Override
    default double y() {
        return blockY();
    }

    @Override
    default double z() {
        return blockZ();
    }

    default long blockLengthSquared() {
        long x = blockX();
        long y = blockY();
        long z = blockZ();
        return x * x + y * y + z * z;
    }

    default double blockLength() {
        return Math.sqrt(blockLengthSquared());
    }

    default long blockDistanceSquared(BlockSpatial spatial) {
        long dx = (long) blockX() - spatial.blockX();
        long dy = (long) blockY() - spatial.blockY();
        long dz = (long) blockZ() - spatial.blockZ();
        return dx * dx + dy * dy + dz * dz;
    }

    default long blockDistanceSquared(int x, int y, int z) {
        long dx = (long) blockX() - x;
        long dy = (long) blockY() - y;
        long dz = (long) blockZ() - z;
        return dx * dx + dy * dy + dz * dz;
    }

    default double blockDistance(BlockSpatial spatial) {
        return Math.sqrt(blockDistanceSquared(spatial));
    }
}
