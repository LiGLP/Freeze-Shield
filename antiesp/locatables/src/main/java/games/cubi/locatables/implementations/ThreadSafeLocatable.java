package games.cubi.locatables.implementations;

import games.cubi.locatables.api.Locatable;
import games.cubi.locatables.api.FloatingLocatableEquality;
import games.cubi.locatables.api.MutableFloatingLocatable;
import games.cubi.locatables.api.Spatial;

import java.util.UUID;
import java.util.concurrent.locks.StampedLock;

public class ThreadSafeLocatable implements MutableFloatingLocatable, FloatingLocatableEquality {
    private UUID world;
    private double x;
    private double y;
    private double z;

    private final StampedLock lock = new StampedLock();

    public ThreadSafeLocatable(UUID world, double x, double y, double z) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    private void withWriteLock(Runnable body) {
        long stamp = lock.writeLock();
        try {
            body.run();
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    private State state() {
        long stamp = lock.tryOptimisticRead();
        State result = new State(world, x, y, z);
        if (!lock.validate(stamp)) {
            stamp = lock.readLock();
            try {
                result = new State(world, x, y, z);
            } finally {
                lock.unlockRead(stamp);
            }
        }
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof FloatingLocatableEquality that)) return false;

        State here = state();
        if (that instanceof ThreadSafeLocatable threadSafe) {
            State there = threadSafe.state();
            return here.sameLocation(there);
        }
        return here.world.equals(that.world())
                && Double.doubleToLongBits(here.x) == Double.doubleToLongBits(that.x())
                && Double.doubleToLongBits(here.y) == Double.doubleToLongBits(that.y())
                && Double.doubleToLongBits(here.z) == Double.doubleToLongBits(that.z());
    }

    @Override
    public int hashCode() {
        State state = state();
        int hash = 3;
        hash = 19 * hash + state.world.hashCode();
        hash = 19 * hash + Long.hashCode(Double.doubleToLongBits(state.x));
        hash = 19 * hash + Long.hashCode(Double.doubleToLongBits(state.y));
        hash = 19 * hash + Long.hashCode(Double.doubleToLongBits(state.z));
        return hash;
    }

    @Override
    public String toString() {
        State state = state();
        return getType() +
                "{" +
                "world=" + state.world +
                ", x=" + state.x +
                ", y=" + state.y +
                ", z=" + state.z +
                '}';
    }

    @Override
    public LocatableType getType() {
        return LocatableType.ThreadSafe;
    }

    @Override
    public double lengthSquared() {
        State state = state();
        return state.x * state.x + state.y * state.y + state.z * state.z;
    }

    @Override
    public ThreadSafeLocatable normalise() {
        long stamp = lock.writeLock();
        try {
            double length = Math.sqrt(x * x + y * y + z * z);
            if (length == 0.0) {
                throw new ArithmeticException("Cannot normalise a zero-length spatial");
            }
            x /= length;
            y /= length;
            z /= length;
        } finally {
            lock.unlockWrite(stamp);
        }
        return this;
    }

    @Override
    public ThreadSafeLocatable add(Spatial spatial) {
        double otherX;
        double otherY;
        double otherZ;
        if (spatial instanceof ThreadSafeLocatable threadSafe) {
            State other = threadSafe.state();
            otherX = other.x;
            otherY = other.y;
            otherZ = other.z;
        } else {
            otherX = spatial.x();
            otherY = spatial.y();
            otherZ = spatial.z();
        }
        withWriteLock(() -> {
            x += otherX;
            y += otherY;
            z += otherZ;
        });
        return this;
    }

    @Override
    public ThreadSafeLocatable add(double x, double y, double z) {
        withWriteLock(() -> {
            this.x += x;
            this.y += y;
            this.z += z;
        });
        return this;
    }

    @Override
    public ThreadSafeLocatable subtract(Spatial spatial) {
        double otherX;
        double otherY;
        double otherZ;
        if (spatial instanceof ThreadSafeLocatable threadSafe) {
            State other = threadSafe.state();
            otherX = other.x;
            otherY = other.y;
            otherZ = other.z;
        } else {
            otherX = spatial.x();
            otherY = spatial.y();
            otherZ = spatial.z();
        }
        withWriteLock(() -> {
            x -= otherX;
            y -= otherY;
            z -= otherZ;
        });
        return this;
    }

    @Override
    public ThreadSafeLocatable scalarMultiply(double factor) {
        withWriteLock(() -> {
            x *= factor;
            y *= factor;
            z *= factor;
        });
        return this;
    }

    @Override
    public UUID world() {
        return state().world;
    }

    @Override
    public int blockX() {
        return (int) Math.floor(x());
    }

    @Override
    public int blockY() {
        return (int) Math.floor(y());
    }

    @Override
    public int blockZ() {
        return (int) Math.floor(z());
    }

    @Override
    public double x() {
        long stamp = lock.tryOptimisticRead();
        double result = x;
        if (!lock.validate(stamp)) {
            stamp = lock.readLock();
            try {
                result = x;
            } finally {
                lock.unlockRead(stamp);
            }
        }
        return result;
    }

    @Override
    public double y() {
        long stamp = lock.tryOptimisticRead();
        double result = y;
        if (!lock.validate(stamp)) {
            stamp = lock.readLock();
            try {
                result = y;
            } finally {
                lock.unlockRead(stamp);
            }
        }
        return result;
    }

    @Override
    public double z() {
        long stamp = lock.tryOptimisticRead();
        double result = z;
        if (!lock.validate(stamp)) {
            stamp = lock.readLock();
            try {
                result = z;
            } finally {
                lock.unlockRead(stamp);
            }
        }
        return result;
    }

    @Override
    public ThreadSafeLocatable setX(double x) {
        withWriteLock(() -> this.x = x);
        return this;
    }

    @Override
    public ThreadSafeLocatable setY(double y) {
        withWriteLock(() -> this.y = y);
        return this;
    }

    @Override
    public ThreadSafeLocatable setZ(double z) {
        withWriteLock(() -> this.z = z);
        return this;
    }

    @Override
    public ThreadSafeLocatable setPosition(double x, double y, double z) {
        withWriteLock(() -> {
            this.x = x;
            this.y = y;
            this.z = z;
        });
        return this;
    }

    @Override
    public ThreadSafeLocatable setPosition(Spatial spatial) {
        if (spatial instanceof ThreadSafeLocatable threadSafe) {
            State other = threadSafe.state();
            return setPosition(other.x, other.y, other.z);
        }
        double x = spatial.x();
        double y = spatial.y();
        double z = spatial.z();
        return setPosition(x, y, z);
    }

    @Override
    public ThreadSafeLocatable setWorld(UUID world) {
        withWriteLock(() -> this.world = world);
        return this;
    }

    @Override
    public ThreadSafeLocatable setLocation(Locatable locatable) {
        if (locatable instanceof ThreadSafeLocatable threadSafe) {
            State other = threadSafe.state();
            return setLocation(other.world, other.x, other.y, other.z);
        }
        UUID world = locatable.world();
        double x = locatable.x();
        double y = locatable.y();
        double z = locatable.z();
        return setLocation(world, x, y, z);
    }

    @Override
    public ThreadSafeLocatable setLocation(UUID world, double x, double y, double z) {
        withWriteLock(() -> {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
        });
        return this;
    }

    @Override
    public ThreadSafeLocatable centre() {
        long stamp = lock.writeLock();
        try {
            x = Math.floor(x) + 0.5;
            y = Math.floor(y) + 0.5;
            z = Math.floor(z) + 0.5;
        } finally {
            lock.unlockWrite(stamp);
        }
        return this;
    }

    @Override
    public boolean strictlyEquals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ThreadSafeLocatable that)) return false;
        return state().sameLocation(that.state());
    }

    private record State(UUID world, double x, double y, double z) {
        private boolean sameLocation(State other) {
            return world.equals(other.world)
                    && Double.doubleToLongBits(x) == Double.doubleToLongBits(other.x)
                    && Double.doubleToLongBits(y) == Double.doubleToLongBits(other.y)
                    && Double.doubleToLongBits(z) == Double.doubleToLongBits(other.z);
        }
    }
}
