package games.cubi.locatables.implementations;

import games.cubi.locatables.api.BlockSpatialEquality;
import games.cubi.locatables.api.ImmutableBlockSpatial;

/** An immutable integral block position without an associated world. */
public record ImmutableBlockSpatialImpl(int blockX, int blockY, int blockZ) implements ImmutableBlockSpatial, BlockSpatialEquality {
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
