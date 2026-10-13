package games.cubi.locatables.implementations;

import games.cubi.locatables.api.MutableSpatial;
import games.cubi.locatables.api.MutableFloatingSpatial;
import games.cubi.locatables.api.FloatingSpatialEquality;

public class MutableSpatialImpl implements MutableSpatial, MutableFloatingSpatial, FloatingSpatialEquality {
    private double x;
    private double y;
    private double z;

    public MutableSpatialImpl(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
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
    public MutableSpatialImpl setX(double x) {
        this.x = x;
        return this;
    }

    @Override
    public MutableSpatialImpl setY(double y) {
        this.y = y;
        return this;
    }

    @Override
    public MutableSpatialImpl setZ(double z) {
        this.z = z;
        return this;
    }

    @Override
    public MutableSpatialImpl setPosition(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
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
}
