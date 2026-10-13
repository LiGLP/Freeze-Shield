package games.cubi.locatables.api;

/** Block-coordinate equality policy for spatials, including fractional spatials projected to blocks. */
public interface BlockSpatialEquality {
    int blockX();
    int blockY();
    int blockZ();

    default boolean isEqualTo(Object other) {
        if (this == other) return true;
        if (!(other instanceof BlockSpatialEquality that)) return false;
        return blockX() == that.blockX() && blockY() == that.blockY() && blockZ() == that.blockZ();
    }

    default int makeHash() {
        int hash = 17;
        hash = 31 * hash + blockX();
        hash = 31 * hash + blockY();
        hash = 31 * hash + blockZ();
        return hash;
    }
}
