package games.cubi.locatables.implementations;

import games.cubi.locatables.api.FloatingSpatialEquality;
import games.cubi.locatables.api.ImmutableSpatial;

/** An immutable floating-point position or vector without an associated world. */
public record ImmutableSpatialImpl(double x, double y, double z) implements ImmutableSpatial, FloatingSpatialEquality {
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
