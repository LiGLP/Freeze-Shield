package games.cubi.locatables.api;

/** A locatable whose exact coordinates are integral block coordinates. */
public sealed interface BlockLocatable extends Locatable, BlockSpatial permits ImmutableBlockLocatable, MutableBlockLocatable {
}
