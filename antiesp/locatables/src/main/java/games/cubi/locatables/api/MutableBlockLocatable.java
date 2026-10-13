package games.cubi.locatables.api;

import java.util.UUID;

/** A mutable, discrete block locatable. */
public non-sealed interface MutableBlockLocatable extends BlockLocatable, MutableLocatable, MutableBlockSpatial {
    @Override
    default MutableBlockLocatable setX(double x) {
        MutableBlockSpatial.super.setX(x);
        return this;
    }

    @Override
    default MutableBlockLocatable setY(double y) {
        MutableBlockSpatial.super.setY(y);
        return this;
    }

    @Override
    default MutableBlockLocatable setZ(double z) {
        MutableBlockSpatial.super.setZ(z);
        return this;
    }

    @Override
    default MutableBlockLocatable setPosition(double x, double y, double z) {
        MutableBlockSpatial.super.setPosition(x, y, z);
        return this;
    }

    @Override
    default MutableBlockLocatable setPosition(Spatial spatial) {
        MutableBlockSpatial.super.setPosition(spatial);
        return this;
    }

    @Override
    MutableBlockLocatable setBlockX(int x);

    @Override
    MutableBlockLocatable setBlockY(int y);

    @Override
    MutableBlockLocatable setBlockZ(int z);

    @Override
    MutableBlockLocatable setBlockPosition(int x, int y, int z);

    @Override
    MutableBlockLocatable setWorld(UUID world);

    @Override
    default MutableBlockLocatable setLocation(Locatable locatable) {
        MutableLocatable.super.setLocation(locatable);
        return this;
    }

    @Override
    default MutableBlockLocatable setLocation(UUID world, double x, double y, double z) {
        MutableLocatable.super.setLocation(world, x, y, z);
        return this;
    }
}
