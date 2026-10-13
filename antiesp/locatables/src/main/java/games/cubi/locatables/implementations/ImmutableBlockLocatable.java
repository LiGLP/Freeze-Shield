package games.cubi.locatables.implementations;

import games.cubi.locatables.api.BlockLocatableEquality;

import java.util.UUID;

public record ImmutableBlockLocatable(UUID world, int blockX, int blockY, int blockZ)
        implements games.cubi.locatables.api.ImmutableBlockLocatable, BlockLocatableEquality {

    public ImmutableBlockLocatable(UUID world, double x, double y, double z) {
        this(world, (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }

    @Override
    public LocatableType getType() {
        return LocatableType.ImmutableBlockLocation;
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

    @Override
    public boolean strictlyEquals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ImmutableBlockLocatable that)) return false;
        return blockX == that.blockX
                && blockY == that.blockY
                && blockZ == that.blockZ
                && world.equals(that.world);
    }
}
