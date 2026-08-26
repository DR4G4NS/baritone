# Pathfinding architecture and validation

Baritone keeps its detailed action graph as the authority for walking, mining,
placing, pillar, falling, ascending and parkour. Long terrestrial goals first
use a bounded 64-by-64-block region graph. LPA* repairs that graph after chunk
or block invalidation and returns a corridor with a one-region margin. The
detailed A* receives only a soft corridor preference; it can leave the corridor
whenever the abstraction is incomplete or a real action requires it. Searches
larger than 65,536 abstract regions skip the hierarchy and use the established
detailed behavior.

Each detailed search has a monotonic generation. Cancellation detaches the
search atomically, and a late result cannot replace a newer path. Node identity
uses lossless serialized block coordinates, distance arithmetic avoids integer
overflow, and vertical bounds use `minY + height`. Search metrics include elapsed
time, expansions, movement evaluations, reopens, discovered nodes, peak open-set
size, estimated retained bytes and final cost.

Elytra continues to use the native global Nether pathfinder and its chunk
packing/culling pipeline. A corridor-local Lazy Theta* pass performs deferred
line-of-sight checks over the swept player volume with the configured avoidance
margin. Long or excessively steep shortcuts are split by retaining native path
points. The existing physical simulator/controller remains responsible for
lookahead, yaw/pitch smoothing, energy-aware rockets, blockage repair, landing
and emergency behavior. `#elytragoto <x> <z>` and
`#elytragoto <x> <y> <z>` validate and start that same process directly.

## Local validation

Use JDK 25. The production build remains on its existing Gradle wrapper; the
Fabric Client GameTest is intentionally an isolated Gradle 9.5.1 build.

```text
./gradlew test deterministicTest headlessReplayTest
./gradlew benchmark
./gradlew :fabric:remapJar
./client-gametest/gradlew -p client-gametest compileGametestJava
./client-gametest/gradlew -p client-gametest runClientGameTest
```

On Linux, run the last command as:

```text
LIBGL_ALWAYS_SOFTWARE=1 MESA_LOADER_DRIVER_OVERRIDE=llvmpipe \
  xvfb-run -a ./client-gametest/gradlew -p client-gametest runClientGameTest --no-daemon
```

The fast CI tier runs unit, deterministic and headless replay tests plus client
scenarios for a flat route, stairs/height changes, an enclosed mining tunnel,
a late obstacle and replan, pillar placement, and cancellation followed by a
new destination. Failures preserve Minecraft logs and screenshots. The
nightly/manual workflow additionally records the reproducible benchmark JSON.

## Evidence and scope

`build/benchmarks/pathfinding-testkit.json` contains the environment, seed,
warmups, samples, cost, expansions, peak queues and per-thread allocated bytes.
It records both the first bounded-suboptimal Weighted A* route and its subsequent
optimal improvement against Dijkstra on the same generated graph. These are
algorithmic measurements, not a claim about whole-client tick time.

The headless Elytra replay checks swept-volume collision, final distance,
rocket use and bounded completion. The current real-client tier proves loading,
ordinary walking/mining and cancellation lifecycle. Client GameTest also
covers `#elytragoto` landings in the Overworld, Nether and End: the player
must finish on the prepared surface, stop gliding, stay within the pad, and
lose at most one heart. Full automated Nether fixtures for walls, narrow
corridors, late chunk obstacles and impossible destinations remain future
integration coverage; they must not be described as executed merely because
their algorithmic counterparts pass in replay.
