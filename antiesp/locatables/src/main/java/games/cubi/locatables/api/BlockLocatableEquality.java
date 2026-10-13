package games.cubi.locatables.api;

import java.util.Objects;
import java.util.UUID;

/** World-and-block-coordinate equality policy for locatables. */
public interface BlockLocatableEquality {
    UUID world();
    int blockX();
    int blockY();
    int blockZ();

    default boolean isEqualTo(Object other) {
        if (this == other) return true;
        if (!(other instanceof BlockLocatableEquality that)) return false;
        return Objects.equals(world(), that.world())
                && blockX() == that.blockX()
                && blockY() == that.blockY()
                && blockZ() == that.blockZ();
    }

    default int makeHash() {
        int hash = 17;
        hash = 31 * hash + Objects.hashCode(world());
        hash = 31 * hash + blockX();
        hash = 31 * hash + blockY();
        hash = 31 * hash + blockZ();
        return hash;
    }
}
