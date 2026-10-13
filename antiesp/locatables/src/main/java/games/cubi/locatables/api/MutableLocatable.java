package games.cubi.locatables.api;

import java.util.UUID;

/**
 * The mutable locatable branch. Continuous implementations that support
 * fractional arithmetic use {@link MutableFloatingLocatable}; block
 * implementations use {@link MutableBlockLocatable}.
 */
public non-sealed interface MutableLocatable extends Locatable, MutableSpatial {
    @Override
    MutableLocatable setX(double x);

    @Override
    MutableLocatable setY(double y);

    @Override
    MutableLocatable setZ(double z);

    /**
     * Default compatibility implementation using component setters. Implementations
     * should override this when they support a bulk or atomic update.
     *
     * @param x the new x coordinate
     * @param y the new y coordinate
     * @param z the new z coordinate
     * @return this mutable locatable
     */
    @Override
    default MutableLocatable setPosition(double x, double y, double z) {
        setX(x);
        setY(y);
        setZ(z);
        return this;
    }

    MutableLocatable setWorld(UUID world);

    default MutableLocatable setX(int x) {
        return setX((double) x);
    }

    default MutableLocatable setY(int y) {
        return setY((double) y);
    }

    default MutableLocatable setZ(int z) {
        return setZ((double) z);
    }

    @Override
    default MutableLocatable setPosition(Spatial spatial) {
        double x = spatial.x();
        double y = spatial.y();
        double z = spatial.z();
        return setPosition(x, y, z);
    }

    default MutableLocatable setLocation(Locatable locatable) {
        UUID world = locatable.world();
        double x = locatable.x();
        double y = locatable.y();
        double z = locatable.z();
        return setLocation(world, x, y, z);
    }

    default MutableLocatable setLocation(UUID world, double x, double y, double z) {
        setWorld(world);
        return setPosition(x, y, z);
    }

    default MutableLocatable set(Locatable locatable) {
        return setLocation(locatable);
    }

    default MutableLocatable set(double x, double y, double z) {
        return setPosition(x, y, z);
    }

    default MutableLocatable set(int x, int y, int z) {
        return setPosition(x, y, z);
    }

    default MutableLocatable set(double x, double y, double z, UUID world) {
        return setLocation(world, x, y, z);
    }

}
