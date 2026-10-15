package de.quarzee.realterrain.worldgen;

/** Deterministic moving Voronoi plates used to shape broad mountain belts. */
final class TectonicPlateModel {
    private static final int CELL_SIZE = 125_000;
    // Broad collision zones make mountain chains continuous and easy to encounter.
    private static final double BOUNDARY_WIDTH = 144_000.0;

    private TectonicPlateModel() {}

    static Signal sample(long seed, int x, int z) {
        // Keep comparable plate coverage at every world-size preset. Small worlds
        // otherwise sample only a fraction of one plate and often have no ranges.
        double worldScale = 1_500_000.0 / WorldSizeSelection.halfWorldSize();
        double worldX = x * worldScale;
        double worldZ = z * worldScale;
        int cellX = floor(worldX / CELL_SIZE);
        int cellZ = floor(worldZ / CELL_SIZE);
        Plate first = null;
        Plate second = null;
        double firstDistance = Double.POSITIVE_INFINITY;
        double secondDistance = Double.POSITIVE_INFINITY;

        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                Plate candidate = plate(seed, cellX + dx, cellZ + dz);
                double distance = Math.hypot(worldX - candidate.x(), worldZ - candidate.z());
                if (distance < firstDistance) {
                    second = first;
                    secondDistance = firstDistance;
                    first = candidate;
                    firstDistance = distance;
                } else if (distance < secondDistance) {
                    second = candidate;
                    secondDistance = distance;
                }
            }
        }

        if (first == null || second == null) return Signal.NONE;
        double centerDistance = Math.hypot(second.x() - first.x(), second.z() - first.z());
        if (centerDistance < 1.0) return Signal.NONE;
        double nx = (second.x() - first.x()) / centerDistance;
        double nz = (second.z() - first.z()) / centerDistance;
        double closingSpeed = (first.velocityX() - second.velocityX()) * nx
                + (first.velocityZ() - second.velocityZ()) * nz;
        double boundary = 1.0 - smoothstep(0.0, BOUNDARY_WIDTH, secondDistance - firstDistance);
        double convergence = clamp(closingSpeed / 0.95, 0.0, 1.0);
        double divergence = clamp(-closingSpeed / 0.95, 0.0, 1.0);
        double subduction = first.continental() == second.continental() ? 0.25 : 1.0;
        return new Signal(boundary, convergence, divergence, boundary * convergence * subduction);
    }

    private static Plate plate(long seed, int cellX, int cellZ) {
        long key = mix64(seed ^ (long) cellX * 0x9E3779B97F4A7C15L ^ (long) cellZ * 0xC2B2AE3D27D4EB4FL);
        double offsetX = unit(mix64(key ^ 0x243F6A8885A308D3L)) - 0.5;
        double offsetZ = unit(mix64(key ^ 0x13198A2E03707344L)) - 0.5;
        double angle = unit(mix64(key ^ 0xA4093822299F31D0L)) * Math.PI * 2.0;
        double speed = 0.18 + unit(mix64(key ^ 0x082EFA98EC4E6C89L)) * 0.62;
        double px = (cellX + 0.5 + offsetX * 0.62) * CELL_SIZE;
        double pz = (cellZ + 0.5 + offsetZ * 0.62) * CELL_SIZE;
        // Crust type is deterministic per tectonic plate. Terrain uplift is still
        // masked by the actual continent field in TectonicTerrainFunction.
        boolean continentalCrust = unit(mix64(key ^ 0x452821E638D01377L)) > 0.36;
        return new Plate(px, pz, Math.cos(angle) * speed, Math.sin(angle) * speed, continentalCrust);
    }

    private static double unit(long value) {
        return (value >>> 11) * 0x1.0p-53;
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private static int floor(double value) {
        int integer = (int) value;
        return value < integer ? integer - 1 : integer;
    }

    private static double smoothstep(double low, double high, double value) {
        double t = clamp((value - low) / (high - low), 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    private static double clamp(double value, double low, double high) {
        return Math.max(low, Math.min(high, value));
    }

    record Signal(double boundaryStrength, double convergence, double divergence, double volcanicPotential) {
        private static final Signal NONE = new Signal(0.0, 0.0, 0.0, 0.0);
    }

    private record Plate(double x, double z, double velocityX, double velocityZ, boolean continental) {}
}
