/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package baritone.cache;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;

public class CachedWorldLifecycleTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void cacheWorkersAndPackingQueueAreBoundedByWorldLifetime() throws Exception {
        CachedWorld world = new CachedWorld(temporaryFolder.newFolder("cache").toPath(), null, null, 32);
        try {
            assertTrue(world.packingQueueCapacity() > 0);
            assertTrue(world.packingQueueCapacity() == 32);
        } finally {
            world.close();
        }

        assertTrue(world.isClosed());
        assertTrue(world.awaitTermination(5L, TimeUnit.SECONDS));
    }
}
