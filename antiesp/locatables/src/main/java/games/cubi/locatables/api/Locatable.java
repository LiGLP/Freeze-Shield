package games.cubi.locatables.api;

import games.cubi.locatables.implementations.ImmutableBlockLocatable;
import games.cubi.locatables.implementations.ImmutableLocatableImpl;
import games.cubi.locatables.implementations.MutableBlockVector;
import games.cubi.locatables.implementations.MutableLocatableImpl;
import games.cubi.locatables.implementations.ThreadSafeLocatable;

import java.util.UUID;

/** A spatial position associated with a specific world. */
public sealed interface Locatable extends ChunkSectionLocatable, Spatial, StrictEquality permits BlockLocatable, ImmutableLocatable, MutableLocatable {
    UUID world();

    @Override
    default int chunkX() {
        return Spatial.super.chunkX();
    }

    @Override
    default int chunkY() {
        return Spatial.super.chunkY();
    }

    @Override
    default int chunkZ() {
        return Spatial.super.chunkZ();
    }

    /**
     * Centres this locatable within its containing block while preserving its
     * world. Continuous mutable locatables are updated in place; immutable and
     * block locatables return a new continuous mutable locatable.
     *
     * @return the mutated receiver or a new continuous mutable locatable
     */
    @Override
    default MutableFloatingLocatable centre() {
        double centreX = blockX() + 0.5;
        double centreY = blockY() + 0.5;
        double centreZ = blockZ() + 0.5;
        if (this instanceof MutableFloatingLocatable locatable) {
            locatable.setPosition(centreX, centreY, centreZ);
            return locatable;
        }
        return new MutableLocatableImpl(world(), centreX, centreY, centreZ);
    }

    LocatableType getType();

    default String toStringForm() {
        return getType() +
                "{" +
                "world=" + world() +
                ", x=" + x() +
                ", y=" + y() +
                ", z=" + z() +
                '}';
    }

    enum LocatableType {
        ThreadSafe,
        MutableBlockVector,
        ImmutableBlockLocation,
        Immutable,
        Mutable,
        MutableBlock,
        ExternalMutable,
        ExternalImmutable,
        ExternalMutableBlock,
        ExternalImmutableBlock,
    }

    static Locatable convertLocatable(Locatable from, LocatableType to, boolean clone) {
        return switch (to) {
            case ThreadSafe -> from instanceof ThreadSafeLocatable && !clone
                    ? from
                    : new ThreadSafeLocatable(from.world(), from.x(), from.y(), from.z());
            case MutableBlockVector -> from instanceof MutableBlockVector && !clone
                    ? from
                    : new MutableBlockVector(from.world(), from.x(), from.y(), from.z());
            case MutableBlock, ExternalMutableBlock -> from instanceof games.cubi.locatables.implementations.MutableBlockLocatable && !clone
                    ? from
                    : new games.cubi.locatables.implementations.MutableBlockLocatable(from.world(), from.blockX(), from.blockY(), from.blockZ());
            case ImmutableBlockLocation -> from instanceof ImmutableBlockLocatable && !clone
                    ? from
                    : new ImmutableBlockLocatable(from.world(), from.x(), from.y(), from.z());
            case Immutable, ExternalImmutable -> from instanceof ImmutableLocatableImpl && !clone
                    ? from
                    : new ImmutableLocatableImpl(from.world(), from.x(), from.y(), from.z());
            case ExternalImmutableBlock -> from instanceof ImmutableBlockLocatable && !clone
                    ? from
                    : new ImmutableBlockLocatable(from.world(), from.x(), from.y(), from.z());
            case Mutable -> from instanceof MutableLocatableImpl && !clone
                    ? from
                    : new MutableLocatableImpl(from.world(), from.x(), from.y(), from.z());
            case ExternalMutable -> new MutableLocatableImpl(from.world(), from.x(), from.y(), from.z());
            default -> throw new IllegalArgumentException("Unsupported Locatable type: " + to);
        };
    }

    static Locatable copyOf(Locatable locatable) {
        return convertLocatable(locatable, locatable.getType(), true);
    }

    static Locatable create(UUID world, double x, double y, double z, LocatableType type) {
        return switch (type) {
            case ThreadSafe -> new ThreadSafeLocatable(world, x, y, z);
            case MutableBlockVector -> new MutableBlockVector(world, x, y, z);
            case MutableBlock, ExternalMutableBlock -> new games.cubi.locatables.implementations.MutableBlockLocatable(
                    world, (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
            case Mutable, ExternalMutable -> new MutableLocatableImpl(world, x, y, z);
            case ImmutableBlockLocation -> new ImmutableBlockLocatable(world, x, y, z);
            case ExternalImmutableBlock -> new ImmutableBlockLocatable(world, x, y, z);
            case Immutable, ExternalImmutable -> new ImmutableLocatableImpl(world, x, y, z);
            default -> throw new IllegalArgumentException("Unsupported Locatable type: " + type);
        };
    }

    @SuppressWarnings("unchecked")
    static <T extends Locatable> T create(UUID world, double x, double y, double z, Class<T> type) {
        Locatable created;
        if (type == ThreadSafeLocatable.class) {
            created = new ThreadSafeLocatable(world, x, y, z);
        } else if (type == MutableBlockVector.class) {
            created = new MutableBlockVector(world, x, y, z);
        } else if (type == ImmutableBlockLocatable.class) {
            created = new ImmutableBlockLocatable(world, x, y, z);
        } else if (type == MutableLocatableImpl.class) {
            created = new MutableLocatableImpl(world, x, y, z);
        } else if (type == ImmutableLocatableImpl.class) {
            created = new ImmutableLocatableImpl(world, x, y, z);
        } else if (type == games.cubi.locatables.implementations.MutableBlockLocatable.class) {
            created = new games.cubi.locatables.implementations.MutableBlockLocatable(
                    world, (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
        } else {
            throw new IllegalArgumentException("Unsupported Locatable type: " + type.getName());
        }
        return (T) created;
    }

    static Locatable create(UUID world, double x, double y, double z) {
        return create(world, x, y, z, LocatableType.Mutable);
    }
}
