package baritone.testkit.pathfinding;

public final class VoxelGrid {
    private final int width;
    private final int height;
    private final int depth;
    private final boolean[] blocked;

    public VoxelGrid(int width, int height, int depth) {
        if (width <= 0 || height <= 0 || depth <= 0) throw new IllegalArgumentException("Invalid grid size");
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.blocked = new boolean[Math.multiplyExact(Math.multiplyExact(width, height), depth)];
    }

    public void setBlocked(int x, int y, int z, boolean value) {
        blocked[index(x, y, z)] = value;
    }

    public boolean isBlocked(int x, int y, int z) {
        return !contains(x, y, z) || blocked[index(x, y, z)];
    }

    public boolean contains(int x, int y, int z) {
        return x >= 0 && x < width && y >= 0 && y < height && z >= 0 && z < depth;
    }

    public boolean isSweptAabbClear(double fromX, double fromY, double fromZ,
                                    double toX, double toY, double toZ,
                                    double halfWidth, double bodyHeight, double margin) {
        if (halfWidth < 0.0D || bodyHeight <= 0.0D || margin < 0.0D
                || !Double.isFinite(halfWidth) || !Double.isFinite(bodyHeight) || !Double.isFinite(margin)) {
            throw new IllegalArgumentException("Invalid swept AABB dimensions");
        }
        double dx = toX - fromX;
        double dy = toY - fromY;
        double dz = toZ - fromZ;
        int samples = Math.max(1, (int) Math.ceil(Math.sqrt(dx * dx + dy * dy + dz * dz) * 10.0D));
        double radius = halfWidth + margin;
        for (int step = 0; step <= samples; step++) {
            double t = (double) step / samples;
            double x = fromX + dx * t;
            double y = fromY + dy * t;
            double z = fromZ + dz * t;
            int minX = (int) Math.floor(x - radius);
            int maxX = (int) Math.floor(x + radius);
            int minY = (int) Math.floor(y - margin);
            int maxY = (int) Math.floor(y + bodyHeight + margin);
            int minZ = (int) Math.floor(z - radius);
            int maxZ = (int) Math.floor(z + radius);
            for (int bx = minX; bx <= maxX; bx++) {
                for (int by = minY; by <= maxY; by++) {
                    for (int bz = minZ; bz <= maxZ; bz++) {
                        if (isBlocked(bx, by, bz)) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    /** Conservative sampled swept-cube check; out-of-world space is solid. */
    public boolean hasLineOfSight(VoxelPoint from, VoxelPoint to, double margin) {
        if (margin < 0 || !Double.isFinite(margin)) throw new IllegalArgumentException("Invalid margin");
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        int samples = Math.max(1, (int) Math.ceil(Math.sqrt(dx * dx + dy * dy + dz * dz) * 8.0));
        double radius = Math.max(margin, 1.0E-7); // include cells touched exactly at an edge/corner
        for (int step = 0; step <= samples; step++) {
            double t = (double) step / samples;
            double x = from.x + 0.5 + dx * t;
            double y = from.y + 0.5 + dy * t;
            double z = from.z + 0.5 + dz * t;
            int minX = (int) Math.floor(x - radius);
            int maxX = (int) Math.floor(x + radius);
            int minY = (int) Math.floor(y - radius);
            int maxY = (int) Math.floor(y + radius);
            int minZ = (int) Math.floor(z - radius);
            int maxZ = (int) Math.floor(z + radius);
            for (int bx = minX; bx <= maxX; bx++) {
                for (int by = minY; by <= maxY; by++) {
                    for (int bz = minZ; bz <= maxZ; bz++) {
                        if (isBlocked(bx, by, bz)) return false;
                    }
                }
            }
        }
        return true;
    }

    private int index(int x, int y, int z) {
        if (!contains(x, y, z)) throw new IndexOutOfBoundsException(x + "," + y + "," + z);
        return (y * depth + z) * width + x;
    }
}
