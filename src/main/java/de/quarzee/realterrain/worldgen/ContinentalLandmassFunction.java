package de.quarzee.realterrain.worldgen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;

import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Interval;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

/** Seeded 2D continentalness field shared by vanilla terrain and spawn selection. */
public record ContinentalLandmassFunction() implements DensityFunction {
    public static final MapCodec<ContinentalLandmassFunction> CODEC = MapCodec.unit(new ContinentalLandmassFunction());
    private static final int HALF_FOOTPRINT = 250_000;
    private static final int CALIBRATION_SIDE = 256;

    @Override
    public DensitySampler compileSampler(CompileContext context) {
        long seed = context.createRandom(Identifier.fromNamespaceAndPath("realterrain", "continental_layout")).nextLong();
        LandmassSampler sampler = createSampler(seed);
        return new DensitySampler() {
            @Override
            public float sampleValue(SamplerContext context, int x, int y, int z) {
                return sampler.sample(x, z);
            }

            @Override
            public void sampleVolume(SamplerContext context, DensityBuffer out, DensityVolume volume) {
                int index = 0;
                for (int zi = 0; zi < volume.sizeZ(); zi++) {
                    int z = volume.blockZ(zi);
                    for (int xi = 0; xi < volume.sizeX(); xi++) {
                        float value = sampler.sample(volume.blockX(xi), z);
                        for (int yi = 0; yi < volume.sizeY(); yi++) {
                            out.set(index++, value);
                        }
                    }
                }
            }
        };
    }

    @Override public DensityFunction rewriteChildren(DfRewriteRule rule) { return this; }
    @Override public Interval range() { return Interval.of(-1.0F, 1.0F); }
    @Override public @DensityFunction.Axes int domainAxes() { return DensityFunction.AXIS_X | DensityFunction.AXIS_Z; }
    @Override public MapCodec<ContinentalLandmassFunction> codec() { return CODEC; }

    static LandmassSampler createSampler(long seed) {
        return new LandmassSampler(seed);
    }

    static final class LandmassSampler {
        private final long seed;
        private final List<Ellipse> ellipses;
        private final float[] grid;

        LandmassSampler(long seed) {
            this.seed = seed;
            SplittableRandom random = new SplittableRandom(seed);
            int count = 6 + random.nextInt(3);
            // Correlate map coverage slightly with landmass count so all layouts
            // fit cleanly in the target square without neighboring masses merging.
            double minLand = 0.25 + (count - 6) * 0.025;
            double maxLand = 0.29 + (count - 6) * 0.030;
            double targetLand = minLand + random.nextDouble() * (maxLand - minLand);
            this.ellipses = List.copyOf(createEllipses(random, count, targetLand));

            int side = CALIBRATION_SIDE;
            double[] rawScores = new double[side * side];
            double[] sortedScores = new double[side * side];
            int i = 0;
            for (int gz = 0; gz < side; gz++) {
                int z = -HALF_FOOTPRINT + (int) ((long) (2 * HALF_FOOTPRINT) * gz / (side - 1));
                for (int gx = 0; gx < side; gx++) {
                    int x = -HALF_FOOTPRINT + (int) ((long) (2 * HALF_FOOTPRINT) * gx / (side - 1));
                    double score = rawScore(x, z);
                    rawScores[i] = score;
                    sortedScores[i++] = score;
                }
            }
            Arrays.sort(sortedScores);
            int landIndex = Math.max(0, Math.min(sortedScores.length - 1,
                    (int) Math.floor((1.0 - targetLand) * sortedScores.length)));
            double threshold = sortedScores[landIndex];
            this.grid = new float[rawScores.length];
            for (int cell = 0; cell < rawScores.length; cell++) {
                this.grid[cell] = (float) clamp((rawScores[cell] - threshold) * 2.4, -1.0, 1.0);
            }
        }

        private static List<Ellipse> createEllipses(SplittableRandom random, int count, double targetLand) {
            // Keep two large landmasses at the north and south edges for the polar
            // desert caps. Remaining landmasses sit in the warmer middle latitudes.
            int[] cells = {0, 1, 2, 3, 4, 5};
            for (int i = cells.length - 1; i > 0; i--) {
                int j = random.nextInt(i + 1);
                int t = cells[i]; cells[i] = cells[j]; cells[j] = t;
            }
            List<Ellipse> result = new ArrayList<>(count);
            double radius = Math.sqrt(targetLand * (4.0 * HALF_FOOTPRINT * HALF_FOOTPRINT)
                    / (Math.PI * count));

            for (int sign : new int[]{-1, 1}) {
                double aspect = 0.94 + random.nextDouble() * 0.12;
                double rx = radius * Math.sqrt(aspect);
                double rz = radius / Math.sqrt(aspect);
                result.add(new Ellipse((random.nextDouble() - 0.5) * 24_000.0,
                        sign * (HALF_FOOTPRINT - 40_000.0), rx, rz,
                        random.nextDouble() * Math.PI, random.nextLong()));
            }

            // Six jittered cells keep the remaining continents separated, while
            // reserving a central cell guarantees land near the default spawn.
            List<Integer> extraCells = new ArrayList<>(count - 2);
            extraCells.add(random.nextBoolean() ? 1 : 4);
            for (int cell : cells) {
                if (extraCells.size() == count - 2) break;
                if (!extraCells.contains(cell)) extraCells.add(cell);
            }
            for (int cell : extraCells) {
                int cx = cell % 3;
                int cz = cell / 3;
                double centerX = (cx - 1) * 155_000.0 + (random.nextDouble() - 0.5) * 18_000.0;
                double centerZ = (cz == 0 ? -42_000.0 : 42_000.0) + (random.nextDouble() - 0.5) * 12_000.0;
                double aspect = 0.92 + random.nextDouble() * 0.16;
                double rx = radius * Math.sqrt(aspect);
                double rz = radius / Math.sqrt(aspect);
                result.add(new Ellipse(centerX, centerZ, rx, rz, random.nextDouble() * Math.PI,
                        random.nextLong()));
            }
            return result;
        }

        float sample(int x, int z) {
            if (x < -HALF_FOOTPRINT || x > HALF_FOOTPRINT || z < -HALF_FOOTPRINT || z > HALF_FOOTPRINT) {
                return -1.0F;
            }
            double gx = (x + (double) HALF_FOOTPRINT) * (CALIBRATION_SIDE - 1) / (2.0 * HALF_FOOTPRINT);
            double gz = (z + (double) HALF_FOOTPRINT) * (CALIBRATION_SIDE - 1) / (2.0 * HALF_FOOTPRINT);
            int x0 = Math.min(CALIBRATION_SIDE - 2, (int) gx);
            int z0 = Math.min(CALIBRATION_SIDE - 2, (int) gz);
            double tx = gx - x0;
            double tz = gz - z0;
            int row = z0 * CALIBRATION_SIDE;
            double a = lerp(grid[row + x0], grid[row + x0 + 1], tx);
            double b = lerp(grid[row + CALIBRATION_SIDE + x0], grid[row + CALIBRATION_SIDE + x0 + 1], tx);
            return (float) lerp(a, b, tz);
        }

        private double rawScore(double x, double z) {
            // Strong, multiscale displacement bends the broad continental margins into
            // peninsulas and deep embayments instead of merely roughening an ellipse.
            double warpX = valueNoise(x, z, 125_000.0, 0x13579BDFL) * 34_000.0
                    + valueNoise(x, z, 48_000.0, 0x2468ACE0L) * 17_000.0
                    + valueNoise(x, z, 17_000.0, 0xC0AC29B7L) * 6_000.0;
            double warpZ = valueNoise(x, z, 118_000.0, 0x10293847L) * 30_000.0
                    + valueNoise(x, z, 43_000.0, 0x56473829L) * 15_000.0
                    + valueNoise(x, z, 15_000.0, 0x3F84D5B5L) * 5_500.0;
            double best = -10.0;
            for (Ellipse e : ellipses) {
                double dx = x + warpX - e.x;
                double dz = z + warpZ - e.z;
                double u = (dx * e.cos - dz * e.sin) / e.radiusX;
                double v = (dx * e.sin + dz * e.cos) / e.radiusZ;
                double angle = Math.atan2(v, u);
                double radial = Math.sqrt(u * u + v * v);
                double lobes = 0.0;
                for (int harmonic = 2; harmonic <= 7; harmonic++) {
                    long h = mix64(e.noiseSalt + harmonic * 0x9E3779B97F4A7C15L);
                    double phase = (h >>> 11) * 0x1.0p-53 * Math.PI * 2.0;
                    double amplitude = 0.07 + ((mix64(h) >>> 11) * 0x1.0p-53) * 0.12;
                    lobes += Math.sin(harmonic * angle + phase) * amplitude;
                }
                double edge = valueNoise(x, z, 72_000.0, e.noiseSalt) * 0.20
                        + valueNoise(x, z, 31_000.0, e.noiseSalt ^ 0x6A09E667F3BCC909L) * 0.13
                        + valueNoise(x, z, 11_000.0, e.noiseSalt ^ 0xBB67AE8584CAA73BL) * 0.065
                        + valueNoise(x, z, 3_500.0, e.noiseSalt ^ 0x3C6EF372FE94F82BL) * 0.025;
                best = Math.max(best, 1.0 - radial / Math.max(0.45, 1.0 + lobes) + edge);
            }
            return best;
        }

        private double valueNoise(double x, double z, double scale, long salt) {
            double gx = x / scale;
            double gz = z / scale;
            int x0 = floor(gx);
            int z0 = floor(gz);
            double tx = smooth(gx - x0);
            double tz = smooth(gz - z0);
            double a = lattice(x0, z0, salt);
            double b = lattice(x0 + 1, z0, salt);
            double c = lattice(x0, z0 + 1, salt);
            double d = lattice(x0 + 1, z0 + 1, salt);
            return lerp(lerp(a, b, tx), lerp(c, d, tx), tz);
        }

        private double lattice(int x, int z, long salt) {
            long h = mix64(seed ^ salt ^ (long) x * 0x9E3779B97F4A7C15L ^ (long) z * 0xC2B2AE3D27D4EB4FL);
            return ((h >>> 11) * 0x1.0p-52) - 1.0;
        }
        private static long mix64(long z) {
            z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
            z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
            return z ^ (z >>> 31);
        }
        private static int floor(double value) { int i = (int) value; return value < i ? i - 1 : i; }
        private static double smooth(double t) { return t * t * (3.0 - 2.0 * t); }
        private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
        private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
    }

    private record Ellipse(double x, double z, double radiusX, double radiusZ, double rotation,
                           double cos, double sin, long noiseSalt) {
        private Ellipse(double x, double z, double radiusX, double radiusZ, double rotation, long noiseSalt) {
            this(x, z, radiusX, radiusZ, rotation, Math.cos(rotation), Math.sin(rotation), noiseSalt);
        }
    }
}
