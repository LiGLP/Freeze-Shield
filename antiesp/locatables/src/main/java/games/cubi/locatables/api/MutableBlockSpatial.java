package games.cubi.locatables.api;

/** A discrete block spatial whose integral coordinates can change. */
public non-sealed interface MutableBlockSpatial extends BlockSpatial, MutableSpatial {
    MutableBlockSpatial setBlockX(int x);
    MutableBlockSpatial setBlockY(int y);
    MutableBlockSpatial setBlockZ(int z);
    MutableBlockSpatial setBlockPosition(int x, int y, int z);

    @Override
    default MutableBlockSpatial setX(double x) {
        return setBlockX(floorToInt(x));
    }

    @Override
    default MutableBlockSpatial setY(double y) {
        return setBlockY(floorToInt(y));
    }

    @Override
    default MutableBlockSpatial setZ(double z) {
        return setBlockZ(floorToInt(z));
    }

    @Override
    default MutableBlockSpatial setPosition(double x, double y, double z) {
        return setBlockPosition(floorToInt(x), floorToInt(y), floorToInt(z));
    }

    @Override
    default MutableBlockSpatial setPosition(Spatial spatial) {
        double x = spatial.x();
        double y = spatial.y();
        double z = spatial.z();
        return setPosition(x, y, z);
    }

    private static int floorToInt(double value) {
        return (int) Math.floor(value);
    }
}
