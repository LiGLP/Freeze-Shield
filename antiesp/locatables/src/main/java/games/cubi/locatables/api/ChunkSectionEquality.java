/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright © 2026 Cubicake.
 * This file is part of RaycastedAntiESP.
 * RaycastedAntiESP is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License v3.0 only, which can be accessed at https://www.gnu.org/licenses/agpl-3.0.html.
 * See README.md for warranty disclaimer and further information.
 */

package games.cubi.locatables.api;

public interface ChunkSectionEquality {
    int chunkX();
    int chunkY();
    int chunkZ();

    default boolean isEqualTo(Object other) {
        if (this == other) return true;
        if (!(other instanceof ChunkSectionEquality that)) return false;
        return this.chunkX() == that.chunkX() && this.chunkY() == that.chunkY() && this.chunkZ() == that.chunkZ();
    }

    static boolean equals(SpatialChunkSection o1, SpatialChunkSection o2) {
        return o1.chunkX() == o2.chunkX() && o1.chunkY() == o2.chunkY() && o1.chunkZ() == o2.chunkZ();
    }

    default int makeHash() {
        int hash = 17;
        hash = 31 * hash + chunkX();
        hash = 31 * hash + chunkY();
        hash = 31 * hash + chunkZ();
        return hash;
    }
}
