package games.cubi.locatables.api;

/** The mutable spatial branch. Block implementations use {@link MutableBlockSpatial}. */
public non-sealed interface MutableSpatial extends Spatial {
    MutableSpatial setX(double x);
    MutableSpatial setY(double y);
    MutableSpatial setZ(double z);

    /**
     * Updates all three coordinates as one logical operation.
     *
     * @param x the new x coordinate
     * @param y the new y coordinate
     * @param z the new z coordinate
     * @return this mutable spatial
     */
    MutableSpatial setPosition(double x, double y, double z);

    /**
     * Copies coordinates through ordinary getters; no atomic read is promised.
     *
     * @param spatial the coordinate source
     * @return this mutable spatial
     */
    default MutableSpatial setPosition(Spatial spatial) {
        double x = spatial.x();
        double y = spatial.y();
        double z = spatial.z();
        return setPosition(x, y, z);
    }
}
