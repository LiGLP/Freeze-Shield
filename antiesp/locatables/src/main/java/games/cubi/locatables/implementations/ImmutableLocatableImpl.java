package games.cubi.locatables.implementations;

import games.cubi.locatables.api.ImmutableLocatable;
import games.cubi.locatables.api.FloatingLocatableEquality;

import java.util.UUID;

public record ImmutableLocatableImpl(UUID world, double x, double y, double z) implements ImmutableLocatable, FloatingLocatableEquality {
    @Override
    public LocatableType getType() {
        return LocatableType.Immutable;
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
        if (!(other instanceof ImmutableLocatableImpl that)) return false;
        return world.equals(that.world)
                && Double.doubleToLongBits(x) == Double.doubleToLongBits(that.x)
                && Double.doubleToLongBits(y) == Double.doubleToLongBits(that.y)
                && Double.doubleToLongBits(z) == Double.doubleToLongBits(that.z);
    }
}
