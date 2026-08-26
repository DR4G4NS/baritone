/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package baritone.testkit.benchmark;

import baritone.testkit.pathfinding.ReferenceDijkstra;
import baritone.testkit.pathfinding.SearchResult;
import baritone.testkit.pathfinding.TestGraphs;
import baritone.testkit.pathfinding.TestkitSeeds;
import baritone.testkit.pathfinding.WeightedAStar;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.lang.management.ManagementFactory;

public final class PathfindingTestkitBenchmark {

    private static final int GRID_SIZE = 64;
    private static final double WEIGHT = 1.0;
    private static final double FAST_WEIGHT = 1.5;
    private static final int WARMUP_ITERATIONS = 5;
    private static final int MEASURED_ITERATIONS = 10;

    private PathfindingTestkitBenchmark() {}

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected output JSON path");
        }
        TestGraphs.GridGraph fixture = TestGraphs.directedGrid(GRID_SIZE, GRID_SIZE, TestkitSeeds.PATHFINDING);
        double expectedCost = ReferenceDijkstra.search(fixture.graph(), fixture.start(), fixture.goal()).cost();

        SearchResult baselineResult = null;
        SearchResult fastResult = null;
        SearchResult result = null;
        for (int iteration = 0; iteration < WARMUP_ITERATIONS; iteration++) {
            baselineResult = ReferenceDijkstra.search(fixture.graph(), fixture.start(), fixture.goal());
            fastResult = run(fixture, FAST_WEIGHT);
            result = run(fixture);
            verifyResult(expectedCost, baselineResult);
            verifyBound(expectedCost, fastResult, FAST_WEIGHT);
            verifyResult(expectedCost, result);
        }

        long[] baselineElapsedNanos = new long[MEASURED_ITERATIONS];
        long[] fastElapsedNanos = new long[MEASURED_ITERATIONS];
        long[] elapsedNanos = new long[MEASURED_ITERATIONS];
        long[] baselineAllocatedBytes = new long[MEASURED_ITERATIONS];
        long[] fastAllocatedBytes = new long[MEASURED_ITERATIONS];
        long[] allocatedBytes = new long[MEASURED_ITERATIONS];
        for (int iteration = 0; iteration < MEASURED_ITERATIONS; iteration++) {
            long allocatedBefore = allocatedBytes();
            long baselineStarted = System.nanoTime();
            SearchResult measuredBaseline = ReferenceDijkstra.search(fixture.graph(), fixture.start(), fixture.goal());
            baselineElapsedNanos[iteration] = System.nanoTime() - baselineStarted;
            baselineAllocatedBytes[iteration] = allocationDelta(allocatedBefore, allocatedBytes());
            verifyResult(expectedCost, measuredBaseline);
            verifyResult(baselineResult, measuredBaseline);
            baselineResult = measuredBaseline;

            allocatedBefore = allocatedBytes();
            long fastStarted = System.nanoTime();
            SearchResult measuredFast = run(fixture, FAST_WEIGHT);
            fastElapsedNanos[iteration] = System.nanoTime() - fastStarted;
            fastAllocatedBytes[iteration] = allocationDelta(allocatedBefore, allocatedBytes());
            verifyBound(expectedCost, measuredFast, FAST_WEIGHT);
            verifyResult(fastResult, measuredFast);
            fastResult = measuredFast;

            allocatedBefore = allocatedBytes();
            long started = System.nanoTime();
            SearchResult measured = run(fixture);
            elapsedNanos[iteration] = System.nanoTime() - started;
            allocatedBytes[iteration] = allocationDelta(allocatedBefore, allocatedBytes());
            verifyResult(expectedCost, measured);
            verifyResult(result, measured);
            result = measured;
        }
        Arrays.sort(baselineElapsedNanos);
        Arrays.sort(fastElapsedNanos);
        Arrays.sort(elapsedNanos);
        Arrays.sort(baselineAllocatedBytes);
        Arrays.sort(fastAllocatedBytes);
        Arrays.sort(allocatedBytes);

        Path output = Path.of(args[0]);
        Files.createDirectories(output.getParent());
        Files.writeString(output, json(baselineResult, baselineElapsedNanos, baselineAllocatedBytes,
                fastResult, fastElapsedNanos, fastAllocatedBytes, result, elapsedNanos, allocatedBytes), StandardCharsets.UTF_8);
        System.out.println(output.toAbsolutePath());
    }

    private static SearchResult run(TestGraphs.GridGraph fixture) {
        return run(fixture, WEIGHT);
    }

    private static SearchResult run(TestGraphs.GridGraph fixture, double weight) {
        return WeightedAStar.search(
                fixture.graph(), fixture.start(), fixture.goal(), weight, fixture.manhattanHeuristic()
        );
    }

    private static void verifyBound(double optimalCost, SearchResult actual, double weight) {
        if (!Double.isFinite(actual.cost()) || actual.cost() > optimalCost * weight) {
            throw new IllegalStateException("Weighted search violated its quality bound: " + actual.cost());
        }
    }

    private static void verifyResult(double expectedCost, SearchResult actual) {
        if (Double.compare(expectedCost, actual.cost()) != 0) {
            throw new IllegalStateException("Benchmark search returned cost " + actual.cost() + " instead of " + expectedCost);
        }
    }

    private static void verifyResult(SearchResult expected, SearchResult actual) {
        if (Double.compare(expected.cost(), actual.cost()) != 0
                || expected.expansions() != actual.expansions()
                || expected.peakOpenSetSize() != actual.peakOpenSetSize()) {
            throw new IllegalStateException("Benchmark search produced non-deterministic results");
        }
    }

    private static String json(SearchResult baselineResult, long[] baselineElapsedNanos, long[] baselineAllocatedBytes,
                               SearchResult fastResult, long[] fastElapsedNanos, long[] fastAllocatedBytes,
                               SearchResult result, long[] elapsedNanos, long[] allocatedBytes) {
        long baselineTotalNanos = 0L;
        for (long elapsed : baselineElapsedNanos) {
            baselineTotalNanos += elapsed;
        }
        long totalNanos = 0L;
        for (long elapsed : elapsedNanos) {
            totalNanos += elapsed;
        }
        return String.format(Locale.ROOT,
                "{\n"
                        + "  \"environment\": {\n"
                        + "    \"javaVersion\": \"%s\",\n"
                        + "    \"javaVendor\": \"%s\",\n"
                        + "    \"osName\": \"%s\",\n"
                        + "    \"osArch\": \"%s\"\n"
                        + "  },\n"
                        + "  \"seed\": %d,\n"
                        + "  \"gridSize\": %d,\n"
                        + "  \"weight\": %.1f,\n"
                        + "  \"warmupIterations\": %d,\n"
                        + "  \"measuredIterations\": %d,\n"
                        + "  \"referenceDijkstra\": {\n"
                        + "    \"elapsedNanosMin\": %d,\n"
                        + "    \"elapsedNanosMedian\": %d,\n"
                        + "    \"elapsedNanosMean\": %.1f,\n"
                        + "    \"expansions\": %d,\n"
                        + "    \"cost\": %.6f,\n"
                        + "    \"peakQueue\": %d,\n"
                        + "    \"allocatedBytesMedian\": %d\n"
                        + "  },\n"
                        + "  \"weightedAStarFirst\": {\n"
                        + "    \"weight\": %.1f,\n"
                        + "    \"elapsedNanosMedian\": %d,\n"
                        + "    \"allocatedBytesMedian\": %d,\n"
                        + "    \"expansions\": %d,\n"
                        + "    \"cost\": %.6f,\n"
                        + "    \"qualityRatio\": %.6f,\n"
                        + "    \"peakQueue\": %d\n"
                        + "  },\n"
                        + "  \"weightedAStar\": {\n"
                        + "    \"elapsedNanosMin\": %d,\n"
                        + "    \"elapsedNanosMedian\": %d,\n"
                        + "    \"elapsedNanosMean\": %.1f,\n"
                        + "    \"expansions\": %d,\n"
                        + "    \"cost\": %.6f,\n"
                        + "    \"peakQueue\": %d,\n"
                        + "    \"allocatedBytesMedian\": %d,\n"
                        + "    \"timeThroughImprovedRouteNanosMedian\": %d\n"
                        + "  },\n"
                        + "  \"medianSpeedup\": %.4f\n"
                        + "}\n",
                escape(System.getProperty("java.version")),
                escape(System.getProperty("java.vendor")),
                escape(System.getProperty("os.name")),
                escape(System.getProperty("os.arch")),
                TestkitSeeds.PATHFINDING,
                GRID_SIZE,
                WEIGHT,
                WARMUP_ITERATIONS,
                MEASURED_ITERATIONS,
                baselineElapsedNanos[0],
                baselineElapsedNanos[baselineElapsedNanos.length / 2],
                (double) baselineTotalNanos / baselineElapsedNanos.length,
                baselineResult.expansions(),
                baselineResult.cost(),
                baselineResult.peakOpenSetSize(),
                baselineAllocatedBytes[baselineAllocatedBytes.length / 2],
                FAST_WEIGHT,
                fastElapsedNanos[fastElapsedNanos.length / 2],
                fastAllocatedBytes[fastAllocatedBytes.length / 2],
                fastResult.expansions(),
                fastResult.cost(),
                fastResult.cost() / baselineResult.cost(),
                fastResult.peakOpenSetSize(),
                elapsedNanos[0],
                elapsedNanos[elapsedNanos.length / 2],
                (double) totalNanos / elapsedNanos.length,
                result.expansions(),
                result.cost(),
                result.peakOpenSetSize(),
                allocatedBytes[allocatedBytes.length / 2],
                fastElapsedNanos[fastElapsedNanos.length / 2] + elapsedNanos[elapsedNanos.length / 2],
                (double) baselineElapsedNanos[baselineElapsedNanos.length / 2]
                        / elapsedNanos[elapsedNanos.length / 2]
        );
    }

    private static long allocatedBytes() {
        java.lang.management.ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        if (bean instanceof com.sun.management.ThreadMXBean allocationBean
                && allocationBean.isThreadAllocatedMemorySupported()) {
            if (!allocationBean.isThreadAllocatedMemoryEnabled()) {
                allocationBean.setThreadAllocatedMemoryEnabled(true);
            }
            return allocationBean.getThreadAllocatedBytes(Thread.currentThread().threadId());
        }
        return -1L;
    }

    private static long allocationDelta(long before, long after) {
        return before < 0L || after < before ? -1L : after - before;
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
