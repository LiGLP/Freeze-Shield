package games.cubi.locatables.implementations;

import games.cubi.locatables.api.BlockSpatialEquality;
import games.cubi.locatables.api.MutableBlockSpatial;

public class MutableBlockSpatialImpl implements MutableBlockSpatial, BlockSpatialEquality {
    private int x;
    private int y;
    private int z;

    public MutableBlockSpatialImpl(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public int blockX() {
        return x;
    }

    @Override
    public int blockY() {
        return y;
    }

    @Override
    public int blockZ() {
        return z;
    }

    @Override
    public MutableBlockSpatialImpl setBlockX(int x) {
        this.x = x;
        return this;
    }

    @Override
    public MutableBlockSpatialImpl setBlockY(int y) {
        this.y = y;
        return this;
    }

    @Override
    public MutableBlockSpatialImpl setBlockZ(int z) {
        this.z = z;
        return this;
    }

    @Override
    public MutableBlockSpatialImpl setBlockPosition(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
        return this;
    }

    @Override
    public boolean equals(Object other) {
        return isEqualTo(other);
    }

    @Override
    public int hashCode() {
        return makeHash();
    }

    @Override
    public String toString() {
        return toStringForm();
    }
}
