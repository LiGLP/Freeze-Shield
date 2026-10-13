package games.cubi.locatables.api;

/**
 * Opt-in arithmetic for mutable continuous spatials. Discrete block spatials do
 * not implement this interface because normalization and scaling generally
 * produce fractional coordinates.
 */
public interface MutableFloatingSpatial extends MutableSpatial {
    default MutableFloatingSpatial normalise() {
        double x = x();
        double y = y();
        double z = z();
        double length = Math.sqrt(x * x + y * y + z * z);
        if (length == 0.0) {
            throw new ArithmeticException("Cannot normalise a zero-length spatial");
        }
        double factor = 1.0 / length;
        setPosition(x * factor, y * factor, z * factor);
        return this;
    }

    default MutableFloatingSpatial add(Spatial spatial) {
        double x = x();
        double y = y();
        double z = z();
        double otherX = spatial.x();
        double otherY = spatial.y();
        double otherZ = spatial.z();
        setPosition(x + otherX, y + otherY, z + otherZ);
        return this;
    }

    default MutableFloatingSpatial add(double x, double y, double z) {
        double currentX = x();
        double currentY = y();
        double currentZ = z();
        setPosition(currentX + x, currentY + y, currentZ + z);
        return this;
    }

    default MutableFloatingSpatial subtract(Spatial spatial) {
        double x = x();
        double y = y();
        double z = z();
        double otherX = spatial.x();
        double otherY = spatial.y();
        double otherZ = spatial.z();
        setPosition(x - otherX, y - otherY, z - otherZ);
        return this;
    }

    default MutableFloatingSpatial scalarMultiply(double factor) {
        double x = x();
        double y = y();
        double z = z();
        setPosition(x * factor, y * factor, z * factor);
        return this;
    }
}
