package vini.evictmap;

import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.world.Block;
import mindustry.world.Tile;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * First visual test for Evict-style resource generation.
 *
 * The values near the top are intentionally easy to tune after comparing a
 * few generated maps against experienced-player feedback.
 */
final class ResourceGenerator {

    // ---------------------------------------------------------------------
    // Independent seed stream
    // ---------------------------------------------------------------------

    private static final long RESOURCE_SEED_XOR = 0x4f52452d45564943L;

    // ---------------------------------------------------------------------
    // Safe placement areas
    // ---------------------------------------------------------------------

    /**
     * Liquids stay inside the stable room interior so they do not block the
     * narrow connections between rooms.
     */
    private static final int LIQUID_MAX_RADIUS = 30;
    private static final int LIQUID_MAX_RADIUS_SQUARED =
        LIQUID_MAX_RADIUS * LIQUID_MAX_RADIUS;

    /**
     * Keep liquids away from the centered Nucleus.
     */
    private static final int LIQUID_CORE_SAFE_RADIUS = 9;
    private static final int LIQUID_CORE_SAFE_RADIUS_SQUARED =
        LIQUID_CORE_SAFE_RADIUS * LIQUID_CORE_SAFE_RADIUS;

    /**
     * Ores may spread farther toward the room edge but not directly underneath
     * the Nucleus footprint.
     */
    private static final int ORE_MAX_RADIUS = 36;
    private static final int ORE_MAX_RADIUS_SQUARED =
        ORE_MAX_RADIUS * ORE_MAX_RADIUS;

    private static final int ORE_CORE_SAFE_RADIUS = 4;
    private static final int ORE_CORE_SAFE_RADIUS_SQUARED =
        ORE_CORE_SAFE_RADIUS * ORE_CORE_SAFE_RADIUS;

    // ---------------------------------------------------------------------
    // Water test preset
    // ---------------------------------------------------------------------

    private static final int WATER_MIN_CLUSTERS = 2;
    private static final double WATER_THIRD_CLUSTER_CHANCE = 0.58;
    private static final double WATER_FOURTH_CLUSTER_CHANCE = 0.22;
    private static final double WATER_FIFTH_CLUSTER_CHANCE = 0.07;

    private static final double WATER_LARGE_CLUSTER_CHANCE = 0.15;
    private static final int WATER_NORMAL_MIN_SIZE = 3;
    private static final int WATER_NORMAL_MAX_SIZE = 8;
    private static final int WATER_LARGE_MIN_SIZE = 12;
    private static final int WATER_LARGE_MAX_SIZE = 14;

    // ---------------------------------------------------------------------
    // Tar / oil test preset
    // ---------------------------------------------------------------------

    /**
     * Oil is intentionally much rarer than water.
     *
     * Resulting distribution:
     * 0 clusters: 55%
     * 1 cluster : 34%
     * 2 clusters:  9%
     * 3 clusters:  1.5%
     * 4 clusters:  0.5%
     */
    private static final double TAR_ZERO_CLUSTER_THRESHOLD = 0.55;
    private static final double TAR_ONE_CLUSTER_THRESHOLD = 0.89;
    private static final double TAR_TWO_CLUSTER_THRESHOLD = 0.98;
    private static final double TAR_THREE_CLUSTER_THRESHOLD = 0.995;

    private static final double TAR_LARGE_CLUSTER_CHANCE = 0.14;
    private static final int TAR_NORMAL_MIN_SIZE = 2;
    private static final int TAR_NORMAL_MAX_SIZE = 5;
    private static final int TAR_LARGE_MIN_SIZE = 6;
    private static final int TAR_LARGE_MAX_SIZE = 9;

    // ---------------------------------------------------------------------
    // Ore test preset
    // ---------------------------------------------------------------------

    /**
     * Order is rare -> common. This gives rarer ores a chance to claim space
     * before common ores fill the remaining floor.
     */
    private static final OrePreset[] ORE_PRESETS = new OrePreset[]{
        new OrePreset(Blocks.oreThorium, 2, 5, 5, 16),
        new OrePreset(Blocks.oreTitanium, 2, 5, 5, 17),
        new OrePreset(Blocks.oreCoal, 3, 6, 7, 23),
        new OrePreset(Blocks.oreLead, 4, 7, 8, 28),
        new OrePreset(Blocks.oreCopper, 5, 8, 10, 32)
    };

    private static final int[][] DIRECTIONS = new int[][]{
        {-1, -1}, {0, -1}, {1, -1},
        {-1,  0},          {1,  0},
        {-1,  1}, {0,  1}, {1,  1}
    };

    private ResourceGenerator() {
    }

    static Summary generate(long mapSeed, List<HexCenter> centers) {
        Random random = new Random(mapSeed ^ RESOURCE_SEED_XOR);
        MutableSummary summary = new MutableSummary();

        /**
         * Floors first, overlays second:
         * - water and tar replace Dark Sand floor
         * - ores are then placed only on remaining Dark Sand
         */
        for (HexCenter center : centers) {
            generateWater(random, center, summary);
            generateTar(random, center, summary);
        }

        for (HexCenter center : centers) {
            generateOres(random, center, summary);
        }

        return summary.freeze();
    }

    static String presetDescription() {
        return "test preset: ores + 2-5 water clusters per normal hex + rare 0-4 tar clusters";
    }

    private static void generateWater(
        Random random,
        HexCenter center,
        MutableSummary summary
    ) {
        int clusterCount = WATER_MIN_CLUSTERS;

        if (random.nextDouble() < WATER_THIRD_CLUSTER_CHANCE) {
            clusterCount++;
        }

        if (random.nextDouble() < WATER_FOURTH_CLUSTER_CHANCE) {
            clusterCount++;
        }

        if (random.nextDouble() < WATER_FIFTH_CLUSTER_CHANCE) {
            clusterCount++;
        }

        for (int cluster = 0; cluster < clusterCount; cluster++) {
            int targetSize = random.nextDouble() < WATER_LARGE_CLUSTER_CHANCE
                ? inclusiveRandom(random, WATER_LARGE_MIN_SIZE, WATER_LARGE_MAX_SIZE)
                : inclusiveRandom(random, WATER_NORMAL_MIN_SIZE, WATER_NORMAL_MAX_SIZE);

            int placed = placeFloorCluster(
                random,
                center,
                Blocks.darksandWater,
                targetSize
            );

            summary.waterClusters++;
            summary.waterTiles += placed;
        }
    }

    private static void generateTar(
        Random random,
        HexCenter center,
        MutableSummary summary
    ) {
        int clusterCount = chooseTarClusterCount(random);

        for (int cluster = 0; cluster < clusterCount; cluster++) {
            int targetSize = random.nextDouble() < TAR_LARGE_CLUSTER_CHANCE
                ? inclusiveRandom(random, TAR_LARGE_MIN_SIZE, TAR_LARGE_MAX_SIZE)
                : inclusiveRandom(random, TAR_NORMAL_MIN_SIZE, TAR_NORMAL_MAX_SIZE);

            int placed = placeFloorCluster(
                random,
                center,
                Blocks.tar,
                targetSize
            );

            summary.tarClusters++;
            summary.tarTiles += placed;
        }
    }

    private static int chooseTarClusterCount(Random random) {
        double value = random.nextDouble();

        if (value < TAR_ZERO_CLUSTER_THRESHOLD) {
            return 0;
        }

        if (value < TAR_ONE_CLUSTER_THRESHOLD) {
            return 1;
        }

        if (value < TAR_TWO_CLUSTER_THRESHOLD) {
            return 2;
        }

        if (value < TAR_THREE_CLUSTER_THRESHOLD) {
            return 3;
        }

        return 4;
    }

    private static void generateOres(
        Random random,
        HexCenter center,
        MutableSummary summary
    ) {
        for (OrePreset preset : ORE_PRESETS) {
            int clusterCount = inclusiveRandom(
                random,
                preset.minimumClusters,
                preset.maximumClusters
            );

            for (int cluster = 0; cluster < clusterCount; cluster++) {
                int targetSize = inclusiveRandom(
                    random,
                    preset.minimumSize,
                    preset.maximumSize
                );

                int placed = placeOreCluster(
                    random,
                    center,
                    preset.overlay,
                    targetSize
                );

                summary.addOre(preset.overlay, placed);
            }
        }
    }

    private static int placeFloorCluster(
        Random random,
        HexCenter center,
        Block targetFloor,
        int targetSize
    ) {
        TilePoint start = findFloorStart(random, center, targetFloor);

        if (start == null) {
            return 0;
        }

        Set<TilePoint> placed = new LinkedHashSet<>();
        placed.add(start);
        applyFloor(start, targetFloor);

        int attempts = 0;
        int maximumAttempts = Math.max(80, targetSize * 35);

        while (placed.size() < targetSize && attempts++ < maximumAttempts) {
            TilePoint anchor = randomElement(random, placed);
            int[] direction = DIRECTIONS[random.nextInt(DIRECTIONS.length)];

            TilePoint candidate = new TilePoint(
                anchor.x + direction[0],
                anchor.y + direction[1]
            );

            if (
                !placed.contains(candidate)
                    && canPlaceLiquidFloor(candidate, center, targetFloor)
            ) {
                placed.add(candidate);
                applyFloor(candidate, targetFloor);
            }
        }

        return placed.size();
    }

    private static int placeOreCluster(
        Random random,
        HexCenter center,
        Block oreOverlay,
        int targetSize
    ) {
        TilePoint start = findOreStart(random, center);

        if (start == null) {
            return 0;
        }

        Set<TilePoint> placed = new LinkedHashSet<>();
        placed.add(start);
        applyOre(start, oreOverlay);

        int attempts = 0;
        int maximumAttempts = Math.max(100, targetSize * 40);

        while (placed.size() < targetSize && attempts++ < maximumAttempts) {
            TilePoint anchor = randomElement(random, placed);
            int[] direction = DIRECTIONS[random.nextInt(DIRECTIONS.length)];

            TilePoint candidate = new TilePoint(
                anchor.x + direction[0],
                anchor.y + direction[1]
            );

            if (
                !placed.contains(candidate)
                    && canPlaceOre(candidate, center)
            ) {
                placed.add(candidate);
                applyOre(candidate, oreOverlay);
            }
        }

        return placed.size();
    }

    private static TilePoint findFloorStart(
        Random random,
        HexCenter center,
        Block targetFloor
    ) {
        for (int attempt = 0; attempt < 160; attempt++) {
            TilePoint candidate = randomPointInRadius(
                random,
                center,
                LIQUID_MAX_RADIUS
            );

            if (canPlaceLiquidFloor(candidate, center, targetFloor)) {
                return candidate;
            }
        }

        return null;
    }

    private static TilePoint findOreStart(Random random, HexCenter center) {
        for (int attempt = 0; attempt < 220; attempt++) {
            TilePoint candidate = randomPointInRadius(
                random,
                center,
                ORE_MAX_RADIUS
            );

            if (canPlaceOre(candidate, center)) {
                return candidate;
            }
        }

        return null;
    }

    private static TilePoint randomPointInRadius(
        Random random,
        HexCenter center,
        int radius
    ) {
        int x;
        int y;

        do {
            x = center.x + random.nextInt(radius * 2 + 1) - radius;
            y = center.y + random.nextInt(radius * 2 + 1) - radius;
        } while (squaredDistance(x, y, center.x, center.y) > radius * radius);

        return new TilePoint(x, y);
    }

    private static boolean canPlaceLiquidFloor(
        TilePoint point,
        HexCenter center,
        Block targetFloor
    ) {
        Tile tile = Vars.world.tile(point.x, point.y);

        if (tile == null || tile.block() != Blocks.air) {
            return false;
        }

        int distanceSquared = squaredDistance(
            point.x,
            point.y,
            center.x,
            center.y
        );

        if (
            distanceSquared < LIQUID_CORE_SAFE_RADIUS_SQUARED
                || distanceSquared > LIQUID_MAX_RADIUS_SQUARED
        ) {
            return false;
        }

        return tile.floor() == Blocks.darksand || tile.floor() == targetFloor;
    }

    private static boolean canPlaceOre(TilePoint point, HexCenter center) {
        Tile tile = Vars.world.tile(point.x, point.y);

        if (
            tile == null
                || tile.block() != Blocks.air
                || tile.floor() != Blocks.darksand
                || tile.overlay() != Blocks.air
        ) {
            return false;
        }

        int distanceSquared = squaredDistance(
            point.x,
            point.y,
            center.x,
            center.y
        );

        return distanceSquared >= ORE_CORE_SAFE_RADIUS_SQUARED
            && distanceSquared <= ORE_MAX_RADIUS_SQUARED;
    }

    private static void applyFloor(TilePoint point, Block floor) {
        Tile tile = Vars.world.tile(point.x, point.y);

        if (tile != null) {
            Tile.setFloor(tile, floor, Blocks.air);
        }
    }

    private static void applyOre(TilePoint point, Block oreOverlay) {
        Tile tile = Vars.world.tile(point.x, point.y);

        if (tile != null) {
            tile.setOverlay(oreOverlay);
        }
    }

    private static TilePoint randomElement(
        Random random,
        Set<TilePoint> points
    ) {
        int targetIndex = random.nextInt(points.size());
        int index = 0;

        for (TilePoint point : points) {
            if (index++ == targetIndex) {
                return point;
            }
        }

        throw new IllegalStateException("Could not select random cluster tile.");
    }

    private static int inclusiveRandom(Random random, int minimum, int maximum) {
        return minimum + random.nextInt(maximum - minimum + 1);
    }

    private static int squaredDistance(int x1, int y1, int x2, int y2) {
        int deltaX = x1 - x2;
        int deltaY = y1 - y2;

        return deltaX * deltaX + deltaY * deltaY;
    }

    record HexCenter(int x, int y) {
    }

    record Summary(
        int waterClusters,
        int waterTiles,
        int tarClusters,
        int tarTiles,
        int copperTiles,
        int leadTiles,
        int coalTiles,
        int titaniumTiles,
        int thoriumTiles
    ) {
        String compact() {
            return "water=" + waterClusters + "/" + waterTiles
                + ", tar=" + tarClusters + "/" + tarTiles
                + ", copper=" + copperTiles
                + ", lead=" + leadTiles
                + ", coal=" + coalTiles
                + ", titanium=" + titaniumTiles
                + ", thorium=" + thoriumTiles;
        }
    }

    private record TilePoint(int x, int y) {
    }

    private record OrePreset(
        Block overlay,
        int minimumClusters,
        int maximumClusters,
        int minimumSize,
        int maximumSize
    ) {
    }

    private static final class MutableSummary {
        private int waterClusters;
        private int waterTiles;
        private int tarClusters;
        private int tarTiles;
        private int copperTiles;
        private int leadTiles;
        private int coalTiles;
        private int titaniumTiles;
        private int thoriumTiles;

        private void addOre(Block overlay, int amount) {
            if (overlay == Blocks.oreCopper) {
                copperTiles += amount;
            } else if (overlay == Blocks.oreLead) {
                leadTiles += amount;
            } else if (overlay == Blocks.oreCoal) {
                coalTiles += amount;
            } else if (overlay == Blocks.oreTitanium) {
                titaniumTiles += amount;
            } else if (overlay == Blocks.oreThorium) {
                thoriumTiles += amount;
            }
        }

        private Summary freeze() {
            return new Summary(
                waterClusters,
                waterTiles,
                tarClusters,
                tarTiles,
                copperTiles,
                leadTiles,
                coalTiles,
                titaniumTiles,
                thoriumTiles
            );
        }
    }
}
