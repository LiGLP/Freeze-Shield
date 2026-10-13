package games.cubi.locatables.api;

/** Supports class-strict comparison in addition to an implementation's public equality policy. */
public interface StrictEquality {
    boolean strictlyEquals(Object other);
}
