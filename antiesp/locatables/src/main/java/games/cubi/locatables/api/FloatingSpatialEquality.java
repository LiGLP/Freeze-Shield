package games.cubi.locatables.api;

/** Coordinate-only equality policy for floating-point spatials. */
public interface FloatingSpatialEquality {
    double x();
    double y();
    double z();

    default boolean isEqualTo(Object other) {
        if (this == other) return true;
        if (!(other instanceof FloatingSpatialEquality that)) return false;
        return Double.doubleToLongBits(x()) == Double.doubleToLongBits(that.x())
                && Double.doubleToLongBits(y()) == Double.doubleToLongBits(that.y())
                && Double.doubleToLongBits(z()) == Double.doubleToLongBits(that.z());
    }

    default int makeHash() {
        int hash = 3;
        hash = 19 * hash + Long.hashCode(Double.doubleToLongBits(x()));
        hash = 19 * hash + Long.hashCode(Double.doubleToLongBits(y()));
        hash = 19 * hash + Long.hashCode(Double.doubleToLongBits(z()));
        return hash;
    }
}
