package games.cubi.locatables.api;

import java.util.UUID;

/**
 * A mutable locatable that can represent continuous coordinates. Discrete block
 * locatables do not implement this interface because arithmetic and centring can
 * produce fractional coordinates.
 */
public interface MutableFloatingLocatable extends MutableLocatable, MutableFloatingSpatial {
    @Override
    MutableFloatingLocatable setX(double x);

    @Override
    MutableFloatingLocatable setY(double y);

    @Override
    MutableFloatingLocatable setZ(double z);

    @Override
    default MutableFloatingLocatable setPosition(double x, double y, double z) {
        MutableLocatable.super.setPosition(x, y, z);
        return this;
    }

    @Override
    MutableFloatingLocatable setWorld(UUID world);

    @Override
    default MutableFloatingLocatable setX(int x) {
        setX((double) x);
        return this;
    }

    @Override
    default MutableFloatingLocatable setY(int y) {
        setY((double) y);
        return this;
    }

    @Override
    default MutableFloatingLocatable setZ(int z) {
        setZ((double) z);
        return this;
    }

    @Override
    default MutableFloatingLocatable setPosition(Spatial spatial) {
        double x = spatial.x();
        double y = spatial.y();
        double z = spatial.z();
        setPosition(x, y, z);
        return this;
    }

    @Override
    default MutableFloatingLocatable setLocation(Locatable locatable) {
        UUID world = locatable.world();
        double x = locatable.x();
        double y = locatable.y();
        double z = locatable.z();
        setLocation(world, x, y, z);
        return this;
    }

    @Override
    default MutableFloatingLocatable setLocation(UUID world, double x, double y, double z) {
        setWorld(world);
        setPosition(x, y, z);
        return this;
    }

    @Override
    default MutableFloatingLocatable set(Locatable locatable) {
        setLocation(locatable);
        return this;
    }

    @Override
    default MutableFloatingLocatable set(double x, double y, double z) {
        setPosition(x, y, z);
        return this;
    }

    @Override
    default MutableFloatingLocatable set(int x, int y, int z) {
        setPosition(x, y, z);
        return this;
    }

    @Override
    default MutableFloatingLocatable set(double x, double y, double z, UUID world) {
        setLocation(world, x, y, z);
        return this;
    }

    @Override
    default MutableFloatingLocatable normalise() {
        MutableFloatingSpatial.super.normalise();
        return this;
    }

    default MutableFloatingLocatable add(Locatable locatable) {
        return add((Spatial) locatable);
    }

    @Override
    default MutableFloatingLocatable add(Spatial spatial) {
        MutableFloatingSpatial.super.add(spatial);
        return this;
    }

    @Override
    default MutableFloatingLocatable add(double x, double y, double z) {
        MutableFloatingSpatial.super.add(x, y, z);
        return this;
    }

    default MutableFloatingLocatable subtract(Locatable locatable) {
        return subtract((Spatial) locatable);
    }

    @Override
    default MutableFloatingLocatable subtract(Spatial spatial) {
        MutableFloatingSpatial.super.subtract(spatial);
        return this;
    }

    @Override
    default MutableFloatingLocatable scalarMultiply(double factor) {
        MutableFloatingSpatial.super.scalarMultiply(factor);
        return this;
    }
}
