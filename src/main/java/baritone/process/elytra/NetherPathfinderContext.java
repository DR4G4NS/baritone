/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.process.elytra;

import baritone.Baritone;
import baritone.api.event.events.BlockChangeEvent;
import baritone.utils.accessor.IPalettedContainer;
import dev.babbaj.pathfinder.NetherPathfinder;
import dev.babbaj.pathfinder.Octree;
import dev.babbaj.pathfinder.PathSegment;
import net.minecraft.core.BlockPos;
import net.minecraft.util.BitStorage;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PaletteResize;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.phys.Vec3;

import java.lang.ref.SoftReference;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author Brady
 */
public final class NetherPathfinderContext {

    private static final BlockState AIR_BLOCK_STATE = Blocks.AIR.defaultBlockState();
    // This lock must be held while there are active pointers to chunks in java,
    // but we just hold it for the entire tick so we don't have to think much about it.
    public final Object cullingLock = new Object();

    // Visible for access in BlockStateOctreeInterface
    final long context;
    private final long seed;
    private final int verticalOffset;
    private final boolean predictTerrain;
    private final ThreadPoolExecutor executor;
    private final AtomicBoolean destroyed = new AtomicBoolean();
    private final ReentrantReadWriteLock lifecycleLock = new ReentrantReadWriteLock();

    public NetherPathfinderContext(long seed) {
        this(seed, 0, Baritone.settings().elytraPredictTerrain.value);
    }

    public NetherPathfinderContext(long seed, int verticalOffset, boolean predictTerrain) {
        this.context = NetherPathfinder.newContext(seed);
        this.seed = seed;
        this.verticalOffset = verticalOffset;
        this.predictTerrain = predictTerrain;
        int queueCapacity = Math.max(16, Baritone.settings().chunkPackerQueueMaxSize.value);
        this.executor = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                runnable -> {
                    Thread thread = new Thread(runnable, "Baritone Elytra Native Context");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    public boolean hasChunk(ChunkPos pos) {
        this.lifecycleLock.readLock().lock();
        try {
            return !this.destroyed.get()
                    && NetherPathfinder.hasChunkFromJava(this.context, pos.x(), pos.z());
        } finally {
            this.lifecycleLock.readLock().unlock();
        }
    }

    public void queueCacheCulling(int chunkX, int chunkZ, int maxDistanceBlocks, BlockStateOctreeInterface boi) {
        executeBestEffort(() -> {
            synchronized (this.cullingLock) {
                boi.chunkPtr = 0L;
                NetherPathfinder.cullFarChunks(this.context, chunkX, chunkZ, maxDistanceBlocks);
            }
        });
    }

    public void queueForPacking(final LevelChunk chunkIn) {
        final SoftReference<LevelChunk> ref = new SoftReference<>(chunkIn);
        executeBestEffort(() -> {
            // TODO: Prioritize packing recent chunks and/or ones that the path goes through,
            //       and prune the oldest chunks per chunkPackerQueueMaxSize
            final LevelChunk chunk = ref.get();
            if (chunk != null) {
                long ptr = NetherPathfinder.getOrCreateChunk(this.context, chunk.getPos().x(), chunk.getPos().z());
                writeChunkData(chunk, ptr);
            }
        });
    }

    public void queueBlockUpdate(BlockChangeEvent event) {
        if (!executeCritical(() -> {
            ChunkPos chunkPos = event.getChunkPos();
            long ptr = NetherPathfinder.getChunkPointer(this.context, chunkPos.x(), chunkPos.z());
            if (ptr == 0) return; // this shouldn't ever happen
            event.getBlocks().forEach(pair -> {
                BlockPos pos = pair.first();
                final int nativeY = toNativeY(pos.getY());
                if (nativeY < 0 || nativeY >= 128) return;
                boolean isSolid = pair.second() != AIR_BLOCK_STATE;
                Octree.setBlock(ptr, pos.getX() & 15, nativeY, pos.getZ() & 15, isSolid);
            });
        })) {
            this.cancel();
        }
    }

    public CompletableFuture<UnpackedSegment> pathFindAsync(final BlockPos src, final BlockPos dst) {
        CompletableFuture<UnpackedSegment> future = new CompletableFuture<>();
        Runnable rejected = () -> future.completeExceptionally(
                new RejectedExecutionException("Elytra native task queue is full or closed")
        );
        if (!executeCritical(() -> {
            try {
                final int sourceY = Mth.clamp(toNativeY(src.getY()), 1, 126);
                final int destinationY = Mth.clamp(toNativeY(dst.getY()), 1, 126);
                final boolean destinationRepresentable = containsWorldY(dst.getY());
                final PathSegment segment = NetherPathfinder.pathFind(
                        this.context,
                        src.getX(), sourceY, src.getZ(),
                        dst.getX(), destinationY, dst.getZ(),
                        true,
                        false,
                        10000,
                        !this.predictTerrain
                );
                if (segment == null) {
                    throw new PathCalculationException("Path calculation failed");
                }
                future.complete(UnpackedSegment.from(
                        segment,
                        this.verticalOffset,
                        segment.finished && destinationRepresentable
                ));
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        }, rejected)) {
            rejected.run();
        }
        return future;
    }

    /**
     * Performs a raytrace from the given start position to the given end position, returning {@code true} if there is
     * visibility between the two points.
     *
     * @param startX The start X coordinate
     * @param startY The start Y coordinate
     * @param startZ The start Z coordinate
     * @param endX   The end X coordinate
     * @param endY   The end Y coordinate
     * @param endZ   The end Z coordinate
     * @return {@code true} if there is visibility between the points
     */
    public boolean raytrace(final double startX, final double startY, final double startZ,
                            final double endX, final double endY, final double endZ) {
        this.lifecycleLock.readLock().lock();
        try {
            return !this.destroyed.get()
                    && NetherPathfinder.isVisible(this.context, NetherPathfinder.CACHE_MISS_SOLID,
                    startX, startY - this.verticalOffset, startZ,
                    endX, endY - this.verticalOffset, endZ);
        } finally {
            this.lifecycleLock.readLock().unlock();
        }
    }

    /**
     * Performs a raytrace from the given start position to the given end position, returning {@code true} if there is
     * visibility between the two points.
     *
     * @param start The starting point
     * @param end   The ending point
     * @return {@code true} if there is visibility between the points
     */
    public boolean raytrace(final Vec3 start, final Vec3 end) {
        return raytrace(start.x, start.y, start.z, end.x, end.y, end.z);
    }

    public boolean raytrace(final int count, final double[] src, final double[] dst, final int visibility) {
        final double[] nativeSrc = translateY(src, -this.verticalOffset);
        final double[] nativeDst = translateY(dst, -this.verticalOffset);
        this.lifecycleLock.readLock().lock();
        try {
            if (this.destroyed.get()) {
                return false;
            }
            switch (visibility) {
                case Visibility.ALL:
                    return NetherPathfinder.isVisibleMulti(this.context, NetherPathfinder.CACHE_MISS_SOLID, count, nativeSrc, nativeDst, false) == -1;
                case Visibility.NONE:
                    return NetherPathfinder.isVisibleMulti(this.context, NetherPathfinder.CACHE_MISS_SOLID, count, nativeSrc, nativeDst, true) == -1;
                case Visibility.ANY:
                    return NetherPathfinder.isVisibleMulti(this.context, NetherPathfinder.CACHE_MISS_SOLID, count, nativeSrc, nativeDst, true) != -1;
                default:
                    throw new IllegalArgumentException("lol");
            }
        } finally {
            this.lifecycleLock.readLock().unlock();
        }
    }

    public void raytrace(final int count, final double[] src, final double[] dst, final boolean[] hitsOut, final double[] hitPosOut) {
        this.lifecycleLock.readLock().lock();
        try {
            if (this.destroyed.get()) {
                return;
            }
            NetherPathfinder.raytrace(this.context, NetherPathfinder.CACHE_MISS_SOLID, count,
                    translateY(src, -this.verticalOffset), translateY(dst, -this.verticalOffset), hitsOut, hitPosOut);
        } finally {
            this.lifecycleLock.readLock().unlock();
        }
        for (int i = 1; i < hitPosOut.length; i += 3) {
            hitPosOut[i] += this.verticalOffset;
        }
    }

    public void cancel() {
        NetherPathfinder.cancel(this.context);
    }

    public void destroy() {
        if (!destroyed.compareAndSet(false, true)) {
            return;
        }
        this.cancel();
        // Ignore anything that was queued up, just shutdown the executor
        this.executor.shutdownNow().forEach(task -> {
            if (task instanceof ContextTask contextTask) {
                contextTask.discard();
            }
        });

        final boolean terminated;
        try {
            terminated = this.executor.awaitTermination(10L, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        if (!terminated) {
            System.err.println("Baritone Elytra native executor did not terminate; preserving context to avoid use-after-free");
            return;
        }

        this.lifecycleLock.writeLock().lock();
        try {
            // Any solver raytrace that started before destruction has now returned.
            NetherPathfinder.cancel(this.context);
            NetherPathfinder.freeContext(this.context);
        } finally {
            this.lifecycleLock.writeLock().unlock();
        }
    }

    private void executeBestEffort(Runnable task) {
        if (destroyed.get()) {
            return;
        }
        try {
            executor.execute(new ContextTask(task, false));
        } catch (RejectedExecutionException ignored) {
            // Chunk packing and cache culling are recoverable from newer world data.
        }
    }

    private synchronized boolean executeCritical(Runnable task) {
        return executeCritical(task, null);
    }

    private synchronized boolean executeCritical(Runnable task, Runnable onDiscard) {
        if (destroyed.get()) {
            return false;
        }
        ContextTask queued = new ContextTask(task, true, onDiscard);
        try {
            executor.execute(queued);
            return true;
        } catch (RejectedExecutionException first) {
            if (executor.isShutdown()) {
                return false;
            }
            Runnable staleBestEffort = executor.getQueue().stream()
                    .filter(candidate -> candidate instanceof ContextTask contextTask && !contextTask.critical)
                    .findFirst()
                    .orElse(null);
            if (staleBestEffort == null || !executor.getQueue().remove(staleBestEffort)) {
                return false;
            }
            try {
                executor.execute(queued);
                return true;
            } catch (RejectedExecutionException second) {
                return false;
            }
        }
    }

    private static final class ContextTask implements Runnable {
        private final Runnable delegate;
        private final boolean critical;
        private final Runnable onDiscard;

        private ContextTask(Runnable delegate, boolean critical) {
            this(delegate, critical, null);
        }

        private ContextTask(Runnable delegate, boolean critical, Runnable onDiscard) {
            this.delegate = delegate;
            this.critical = critical;
            this.onDiscard = onDiscard;
        }

        @Override
        public void run() {
            delegate.run();
        }

        private void discard() {
            if (onDiscard != null) {
                onDiscard.run();
            }
        }
    }

    public long getSeed() {
        return this.seed;
    }

    public boolean containsWorldY(int y) {
        return y >= this.verticalOffset && y < this.verticalOffset + 128;
    }

    public int getVerticalOffset() {
        return this.verticalOffset;
    }

    private int toNativeY(int worldY) {
        return worldY - this.verticalOffset;
    }

    private static double[] translateY(double[] coordinates, int deltaY) {
        final double[] translated = coordinates.clone();
        for (int i = 1; i < translated.length; i += 3) {
            translated[i] += deltaY;
        }
        return translated;
    }

    private void writeChunkData(LevelChunk chunk, long ptr) {
        try {
            LevelChunkSection[] chunkInternalStorageArray = chunk.getSections();
            for (int y0 = 0; y0 < 8; y0++) {
                final int worldSectionY = this.verticalOffset + (y0 << 4);
                final int sectionIndex = chunk.getSectionIndex(worldSectionY);
                if (sectionIndex < 0 || sectionIndex >= chunkInternalStorageArray.length) {
                    continue;
                }
                final LevelChunkSection extendedblockstorage = chunkInternalStorageArray[sectionIndex];
                if (extendedblockstorage == null) {
                    continue;
                }
                final PalettedContainer<BlockState> bsc = extendedblockstorage.getStates();
                IPalettedContainer<BlockState> iPalettedContainer = (IPalettedContainer<BlockState>) bsc;
                int airId = -1;
                if (iPalettedContainer.getPalette().maybeHas(state -> state.equals(AIR_BLOCK_STATE))) {
                    airId = iPalettedContainer.getPalette().idFor(AIR_BLOCK_STATE, PaletteResize.noResizeExpected());
                }
                // pasted from FasterWorldScanner
                final BitStorage array = iPalettedContainer.getStorage();
                if (array == null) continue;
                final long[] longArray = array.getRaw();
                final int arraySize = array.getSize();
                int bitsPerEntry = array.getBits();
                long maxEntryValue = (1L << bitsPerEntry) - 1L;

                final int yReal = y0 << 4;
                for (int i = 0, idx = 0; i < longArray.length && idx < arraySize; ++i) {
                    long l = longArray[i];
                    for (int offset = 0; offset <= (64 - bitsPerEntry) && idx < arraySize; offset += bitsPerEntry, ++idx) {
                        int value = (int) ((l >> offset) & maxEntryValue);
                        int x = (idx & 15);
                        int y = yReal + (idx >> 8);
                        int z = ((idx >> 4) & 15);
                        Octree.setBlock(ptr, x, y, z, value != airId);
                    }
                }
            }
            Octree.setIsFromJava(ptr);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public static final class Visibility {

        public static final int ALL = 0;
        public static final int NONE = 1;
        public static final int ANY = 2;

        private Visibility() {}
    }

    public static boolean isSupported() {
        return NetherPathfinder.isThisSystemSupported();
    }
}
