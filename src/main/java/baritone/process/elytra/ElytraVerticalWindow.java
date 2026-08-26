package baritone.process.elytra;

final class ElytraVerticalWindow {

    private ElytraVerticalWindow() {}

    static int choose(int minY, int height, int sourceY, int destinationY, boolean predictTerrain) {
        if (predictTerrain) {
            return 0;
        }
        final int maxOffset = Math.max(minY, minY + height - 128);
        final int desired = Math.min(sourceY, destinationY) - 16;
        final int clamped = Math.max(minY, Math.min(maxOffset, desired));
        return Math.floorDiv(clamped, 16) * 16;
    }
}
