package games.cubi.locatables.implementations;

import games.cubi.locatables.api.BlockLocatableEquality;
import games.cubi.locatables.api.MutableFloatingLocatable;
import games.cubi.locatables.api.Spatial;

import java.util.UUID;

/**
 * A continuous mutable locatable whose equality policy projects coordinates to
 * blocks. It is deliberately not a {@code BlockSpatial}, because its exact
 * coordinates may be fractional.
 */
public class MutableBlockVector implements MutableFloatingLocatable, BlockLocatableEquality {
    private UUID world;
    private double mutableX;
    private double mutableY;
    private double mutableZ;

    public MutableBlockVector(UUID world, double x, double y, double z) {
        this.world = world;
        this.mutableX = x;
        this.mutableY = y;
        this.mutableZ = z;
    }

    public MutableBlockVector(UUID world, int x, int y, int z) {
        this(world, (double) x, (double) y, (double) z);
    }

    @Override
    public LocatableType getType() {
        return LocatableType.MutableBlockVector;
    }

    @Override
    public MutableBlockVector normalise() {
        double length = length();
        if (length == 0.0) {
            throw new ArithmeticException("Cannot normalise a zero-length spatial");
        }
        return scalarMultiply(1.0 / length);
    }

    @Override
    public MutableBlockVector add(Spatial spatial) {
        double otherX = spatial.x();
        double otherY = spatial.y();
        double otherZ = spatial.z();
        mutableX += otherX;
        mutableY += otherY;
        mutableZ += otherZ;
        return this;
    }

    @Override
    public MutableBlockVector add(double x, double y, double z) {
        mutableX += x;
        mutableY += y;
        mutableZ += z;
        return this;
    }

    @Override
    public MutableBlockVector subtract(Spatial spatial) {
        double otherX = spatial.x();
        double otherY = spatial.y();
        double otherZ = spatial.z();
        mutableX -= otherX;
        mutableY -= otherY;
        mutableZ -= otherZ;
        return this;
    }

    @Override
    public MutableBlockVector scalarMultiply(double factor) {
        mutableX *= factor;
        mutableY *= factor;
        mutableZ *= factor;
        return this;
    }

    @Override
    public UUID world() {
        return world;
    }

    @Override
    public int blockX() {
        return (int) Math.floor(mutableX);
    }

    @Override
    public int blockY() {
        return (int) Math.floor(mutableY);
    }

    @Override
    public int blockZ() {
        return (int) Math.floor(mutableZ);
    }

    @Override
    public double x() {
        return mutableX;
    }

    @Override
    public double y() {
        return mutableY;
    }

    @Override
    public double z() {
        return mutableZ;
    }

    @Override
    public MutableBlockVector setX(double x) {
        mutableX = x;
        return this;
    }

    @Override
    public MutableBlockVector setY(double y) {
        mutableY = y;
        return this;
    }

    @Override
    public MutableBlockVector setZ(double z) {
        mutableZ = z;
        return this;
    }

    @Override
    public MutableBlockVector setPosition(double x, double y, double z) {
        mutableX = x;
        mutableY = y;
        mutableZ = z;
        return this;
    }

    @Override
    public MutableBlockVector setWorld(UUID world) {
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
        if (!(other instanceof MutableBlockVector that)) return false;
        return world.equals(that.world)
                && Double.doubleToLongBits(mutableX) == Double.doubleToLongBits(that.mutableX)
                && Double.doubleToLongBits(mutableY) == Double.doubleToLongBits(that.mutableY)
                && Double.doubleToLongBits(mutableZ) == Double.doubleToLongBits(that.mutableZ);
    }
}
