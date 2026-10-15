package de.quarzee.realterrain.worldgen;

import java.util.Arrays;

import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Interval;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

/** Seeded land/water coverage field scaled to the selected world footprint. */
public record ContinentalLandmassFunction() implements DensityFunction {
    public static final MapCodec<ContinentalLandmassFunction> CODEC = MapCodec.unit(new ContinentalLandmassFunction());

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
                for (int yi = 0; yi < volume.sizeY(); yi++) {
                    for (int zi = 0; zi < volume.sizeZ(); zi++) {
                        int z = volume.blockZ(zi);
                        for (int xi = 0; xi < volume.sizeX(); xi++) {
                            out.set(index++, sampler.sample(volume.blockX(xi), z));
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
        return new LandmassSampler(seed, WorldSizeSelection.halfWorldSize(),
                WorldSizeSelection.currentLandCoveragePercent());
    }

    static final class LandmassSampler {
        private static final double REFERENCE_HALF_SIZE = 1_500_000.0;
        private static final int CALIBRATION_RESOLUTION = 256;
        private static volatile ThresholdCache cachedThreshold;

        private final long seed;
        private final double coordinateScale;
        private final double threshold;

        LandmassSampler(long seed, double halfWorldSize, int landCoveragePercent) {
            this.seed = seed;
            this.coordinateScale = REFERENCE_HALF_SIZE / Math.max(50_000.0, halfWorldSize);
            this.threshold = calibratedThreshold(seed, landCoveragePercent);
        }

        float sample(int x, int z) {
            double normalizedX = x * coordinateScale;
            double normalizedZ = z * coordinateScale;
            double score = landScore(normalizedX, normalizedZ);
            // Zero is the calibrated coastline. The terrain envelope eases the shore
            // through sea level while biome climate ranges remain continuous at zero.
            return (float) clamp((score - threshold) * 5.0, -1.0, 1.0);
        }

        private double calibratedThreshold(long worldSeed, int coveragePercent) {
            ThresholdCache cached = cachedThreshold;
            if (cached != null && cached.seed() == worldSeed && cached.coverage() == coveragePercent) {
                return cached.threshold();
            }
            synchronized (LandmassSampler.class) {
                cached = cachedThreshold;
                if (cached != null && cached.seed() == worldSeed && cached.coverage() == coveragePercent) {
                    return cached.threshold();
                }
                double[] samples = new double[CALIBRATION_RESOLUTION * CALIBRATION_RESOLUTION];
                int index = 0;
                double step = (REFERENCE_HALF_SIZE * 2.0) / CALIBRATION_RESOLUTION;
                for (int gz = 0; gz < CALIBRATION_RESOLUTION; gz++) {
                    double z = -REFERENCE_HALF_SIZE + (gz + 0.5) * step;
                    for (int gx = 0; gx < CALIBRATION_RESOLUTION; gx++) {
                        double x = -REFERENCE_HALF_SIZE + (gx + 0.5) * step;
                        samples[index++] = landScore(x, z);
                    }
                }
                Arrays.sort(samples);
                if (coveragePercent == 0) {
                    double threshold = 1.000001;
                    cachedThreshold = new ThresholdCache(worldSeed, coveragePercent, threshold);
                    return threshold;
                }
                if (coveragePercent == 100) {
                    double threshold = -1.000001;
                    cachedThreshold = new ThresholdCache(worldSeed, coveragePercent, threshold);
                    return threshold;
                }
                int landSamples = (int) Math.round(samples.length * coveragePercent / 100.0);
                int firstLandIndex = samples.length - landSamples;
                double threshold = (samples[firstLandIndex - 1] + samples[firstLandIndex]) * 0.5;
                cachedThreshold = new ThresholdCache(worldSeed, coveragePercent, threshold);
                return threshold;
            }
        }

        private double landScore(double x, double z) {
            double warpedX = x
                    + valueNoise(x, z, 620_000.0, 0x13579BDFL) * 155_000.0
                    + valueNoise(x, z, 235_000.0, 0x2468ACE0L) * 58_000.0
                    + valueNoise(x, z, 82_000.0, 0xC0AC29B7L) * 18_000.0;
            double warpedZ = z
                    + valueNoise(x, z, 590_000.0, 0x10293847L) * 140_000.0
                    + valueNoise(x, z, 220_000.0, 0x56473829L) * 52_000.0
                    + valueNoise(x, z, 76_000.0, 0x3F84D5B5L) * 16_000.0;

            return valueNoise(warpedX, warpedZ, 720_000.0, 0x510E527FADE682D1L) * 0.58
                    + valueNoise(warpedX, warpedZ, 330_000.0, 0x9B05688C2B3E6C1FL) * 0.25
                    + valueNoise(warpedX, warpedZ, 145_000.0, 0x1F83D9ABFB41BD6BL) * 0.12
                    + valueNoise(warpedX, warpedZ, 58_000.0, 0x5BE0CD19137E2179L) * 0.05;
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

        private static long mix64(long value) {
            value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
            value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
            return value ^ (value >>> 31);
        }

        private static int floor(double value) { int integer = (int) value; return value < integer ? integer - 1 : integer; }
        private static double smooth(double t) { return t * t * (3.0 - 2.0 * t); }
        private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
        private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }

        private record ThresholdCache(long seed, int coverage, double threshold) {}
    }
}
