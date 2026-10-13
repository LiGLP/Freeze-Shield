package games.cubi.locatables.implementations;

import games.cubi.locatables.api.FloatingLocatableEquality;
import games.cubi.locatables.api.MutableFloatingLocatable;

import java.util.UUID;

public class MutableLocatableImpl implements MutableFloatingLocatable, FloatingLocatableEquality {
    private double x;
    private double y;
    private double z;
    private UUID world;

    public MutableLocatableImpl(UUID world, double x, double y, double z) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public LocatableType getType() {
        return LocatableType.Mutable;
    }

    @Override
    public UUID world() {
        return world;
    }

    @Override
    public double x() {
        return x;
    }

    @Override
    public double y() {
        return y;
    }

    @Override
    public double z() {
        return z;
    }

    @Override
    public MutableLocatableImpl setX(double x) {
        this.x = x;
        return this;
    }

    @Override
    public MutableLocatableImpl setY(double y) {
        this.y = y;
        return this;
    }

    @Override
    public MutableLocatableImpl setZ(double z) {
        this.z = z;
        return this;
    }

    @Override
    public MutableLocatableImpl setPosition(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
        return this;
    }

    @Override
    public MutableLocatableImpl setWorld(UUID world) {
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
        if (!(other instanceof MutableLocatableImpl that)) return false;
        return world.equals(that.world)
                && Double.doubleToLongBits(x) == Double.doubleToLongBits(that.x)
                && Double.doubleToLongBits(y) == Double.doubleToLongBits(that.y)
                && Double.doubleToLongBits(z) == Double.doubleToLongBits(that.z);
    }
}
