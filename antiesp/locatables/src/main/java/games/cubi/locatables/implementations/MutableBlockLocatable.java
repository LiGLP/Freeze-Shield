package games.cubi.locatables.implementations;

import games.cubi.locatables.api.BlockLocatableEquality;

import java.util.UUID;

public class MutableBlockLocatable
        implements games.cubi.locatables.api.MutableBlockLocatable, BlockLocatableEquality {
    private int x;
    private int y;
    private int z;
    private UUID world;

    public MutableBlockLocatable(UUID world) {
        this.world = world;
    }

    public MutableBlockLocatable(UUID world, int x, int y, int z) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public UUID world() {
        return world;
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
    public LocatableType getType() {
        return LocatableType.MutableBlock;
    }

    @Override
    public MutableBlockLocatable setBlockX(int x) {
        this.x = x;
        return this;
    }

    @Override
    public MutableBlockLocatable setBlockY(int y) {
        this.y = y;
        return this;
    }

    @Override
    public MutableBlockLocatable setBlockZ(int z) {
        this.z = z;
        return this;
    }

    @Override
    public MutableBlockLocatable setBlockPosition(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
        return this;
    }

    @Override
    public MutableBlockLocatable setWorld(UUID world) {
        this.world = world;
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

    @Override
    public boolean strictlyEquals(Object other) {
        if (this == other) return true;
        if (!(other instanceof MutableBlockLocatable that)) return false;
        return x == that.x && y == that.y && z == that.z && world.equals(that.world);
    }
}
