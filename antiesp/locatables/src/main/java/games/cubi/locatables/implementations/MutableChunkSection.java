/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright © 2026 Cubicake.
 * This file is part of RaycastedAntiESP.
 * RaycastedAntiESP is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License v3.0 only, which can be accessed at https://www.gnu.org/licenses/agpl-3.0.html.
 * See README.md for warranty disclaimer and further information.
 */

package games.cubi.locatables.implementations;

import games.cubi.locatables.api.ChunkSectionEquality;
import games.cubi.locatables.api.SpatialChunkSection;

public class MutableChunkSection implements SpatialChunkSection, ChunkSectionEquality {
    int chunkX;
    int chunkY;
    int chunkZ;


    @Override
    public int chunkX() {
        return chunkX;
    }

    @Override
    public int chunkY() {
        return chunkY;
    }

    @Override
    public int chunkZ() {
        return chunkZ;
    }

    public MutableChunkSection updateFrom(SpatialChunkSection other) {
        this.chunkX = other.chunkX();
        this.chunkY = other.chunkY();
        this.chunkZ = other.chunkZ();

        return this;
    }
}
