package games.cubi.locatables.api;

import java.util.Objects;
import java.util.UUID;

/** World-and-coordinate equality policy for floating-point locatables. */
public interface FloatingLocatableEquality {
    UUID world();
    double x();
    double y();
    double z();

    default boolean isEqualTo(Object other) {
        if (this == other) return true;
        if (!(other instanceof FloatingLocatableEquality that)) return false;
        return Objects.equals(world(), that.world())
                && Double.doubleToLongBits(x()) == Double.doubleToLongBits(that.x())
                && Double.doubleToLongBits(y()) == Double.doubleToLongBits(that.y())
                && Double.doubleToLongBits(z()) == Double.doubleToLongBits(that.z());
    }

    default int makeHash() {
        int hash = 3;
        hash = 19 * hash + Objects.hashCode(world());
        hash = 19 * hash + Long.hashCode(Double.doubleToLongBits(x()));
        hash = 19 * hash + Long.hashCode(Double.doubleToLongBits(y()));
        hash = 19 * hash + Long.hashCode(Double.doubleToLongBits(z()));
        return hash;
    }
}
