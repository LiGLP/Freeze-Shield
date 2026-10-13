package games.cubi.locatables.api;

import games.cubi.locatables.implementations.MutableSpatialImpl;

/**
 * A position or vector in three-dimensional space without an associated world.
 * Implementations must explicitly choose a mutable or immutable branch and, when
 * applicable, a block branch.
 */
public sealed interface Spatial extends SpatialChunkSection permits BlockSpatial, ImmutableSpatial, Locatable, MutableSpatial {
    double x();
    double y();
    double z();

    default int blockX() {
        return (int) Math.floor(x());
    }

    default int blockY() {
        return (int) Math.floor(y());
    }

    default int blockZ() {
        return (int) Math.floor(z());
    }

    @Override
    default int chunkX() {
        return blockX() >> 4;
    }

    @Override
    default int chunkY() {
        return blockY() >> 4;
    }

    @Override
    default int chunkZ() {
        return blockZ() >> 4;
    }

    default double length() {
        return Math.sqrt(lengthSquared());
    }

    default double lengthSquared() {
        double x = x();
        double y = y();
        double z = z();
        return x * x + y * y + z * z;
    }

    default double distance(Spatial spatial) {
        return Math.sqrt(distanceSquared(spatial));
    }

    default double distanceSquared(Spatial spatial) {
        double otherX = spatial.x();
        double otherY = spatial.y();
        double otherZ = spatial.z();
        double dx = x() - otherX;
        double dy = y() - otherY;
        double dz = z() - otherZ;
        return dx * dx + dy * dy + dz * dz;
    }

    default double distanceSquared(double x, double y, double z) {
        double dx = this.x() - x;
        double dy = this.y() - y;
        double dz = this.z() - z;
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Centres this spatial within its containing block. Continuous mutable
     * spatials are updated in place. Immutable and block spatials return a new
     * continuous mutable result because they cannot represent the centred value.
     *
     * @return the mutated receiver or a new continuous mutable spatial
     */
    default MutableFloatingSpatial centre() {
        double centreX = blockX() + 0.5;
        double centreY = blockY() + 0.5;
        double centreZ = blockZ() + 0.5;
        if (this instanceof MutableFloatingSpatial spatial) {
            spatial.setPosition(centreX, centreY, centreZ);
            return spatial;
        }
        return new MutableSpatialImpl(centreX, centreY, centreZ);
    }

    default MutableFloatingSpatial cloneAndIfBlockThenCentre() {
        if (this instanceof BlockSpatial) {
            return centre();
        }
        return new MutableSpatialImpl(x(), y(), z());
    }

    default String toStringForm() {
        return getClass().getSimpleName() +
                "{" +
                "x=" + x() +
                ", y=" + y() +
                ", z=" + z() +
                '}';
    }

}
