package de.quarzee.realterrain.worldgen;

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
    private static final double CELL_SPACING = 1_350_000.0;
    private static final double MAX_CENTER_JITTER = 120_000.0;
    private static final double MIN_RADIUS = 340_000.0;

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

        LandmassSampler(long seed) {
            this.seed = seed;
        }

        float sample(int x, int z) {
            double warpX = valueNoise(x, z, 520_000.0, 0x13579BDFL) * 48_000.0
                    + valueNoise(x, z, 210_000.0, 0x2468ACE0L) * 22_000.0
                    + valueNoise(x, z, 75_000.0, 0xC0AC29B7L) * 8_000.0;
            double warpZ = valueNoise(x, z, 480_000.0, 0x10293847L) * 42_000.0
                    + valueNoise(x, z, 195_000.0, 0x56473829L) * 20_000.0
                    + valueNoise(x, z, 68_000.0, 0x3F84D5B5L) * 7_000.0;
            int cellX = floor((x + warpX + CELL_SPACING * 0.5) / CELL_SPACING);
            int cellZ = floor((z + warpZ + CELL_SPACING * 0.5) / CELL_SPACING);
            double best = -10.0;
            for (int gz = cellZ - 1; gz <= cellZ + 1; gz++) {
                for (int gx = cellX - 1; gx <= cellX + 1; gx++) {
                    double nominalDx = x + warpX - gx * CELL_SPACING;
                    double nominalDz = z + warpZ - gz * CELL_SPACING;
                    if (nominalDx * nominalDx + nominalDz * nominalDz > 1_400_000.0 * 1_400_000.0) continue;
                    best = Math.max(best, continentScore(x + warpX, z + warpZ, gx, gz));
                }
            }
            return (float) clamp(best * 2.4, -1.0, 1.0);
        }

        private double continentScore(double x, double z, int cellX, int cellZ) {
            long cellSeed = mix64(seed ^ (long) cellX * 0x9E3779B97F4A7C15L
                    ^ (long) cellZ * 0xC2B2AE3D27D4EB4FL);
            boolean originCell = cellX == 0 && cellZ == 0;
            double centerX = cellX * CELL_SPACING + (originCell ? 0.0
                    : (unit(mix64(cellSeed + 0x510E527FADE682D1L)) - 0.5) * 2.0 * MAX_CENTER_JITTER);
            double centerZ = cellZ * CELL_SPACING + (originCell ? 0.0
                    : (unit(mix64(cellSeed + 0x9B05688C2B3E6C1FL)) - 0.5) * 2.0 * MAX_CENTER_JITTER);
            double radius = MIN_RADIUS + unit(mix64(cellSeed + 0x1F83D9ABFB41BD6BL)) * 140_000.0;
            double aspect = 0.86 + unit(mix64(cellSeed + 0x5BE0CD19137E2179L)) * 0.28;
            double rotation = unit(mix64(cellSeed + 0xBB67AE8584CAA73BL)) * Math.PI * 2.0;
            double dx = x - centerX;
            double dz = z - centerZ;
            double cos = Math.cos(rotation);
            double sin = Math.sin(rotation);
            double u = (dx * cos - dz * sin) / (radius * Math.sqrt(aspect));
            double v = (dx * sin + dz * cos) / (radius / Math.sqrt(aspect));
            double angle = Math.atan2(v, u);
            double radial = Math.sqrt(u * u + v * v);
            double lobes = 0.0;
            for (int harmonic = 2; harmonic <= 7; harmonic++) {
                long h = mix64(cellSeed + harmonic * 0xD6E8FEB86659FD93L);
                double phase = unit(h) * Math.PI * 2.0;
                double amplitude = 0.06 + unit(mix64(h)) * 0.08;
                lobes += Math.sin(harmonic * angle + phase) * amplitude;
            }
            double edge = valueNoise(x, z, 190_000.0, cellSeed) * 0.14
                    + valueNoise(x, z, 76_000.0, cellSeed ^ 0x6A09E667F3BCC909L) * 0.08
                    + valueNoise(x, z, 26_000.0, cellSeed ^ 0xBB67AE8584CAA73BL) * 0.04
                    + valueNoise(x, z, 8_000.0, cellSeed ^ 0x3C6EF372FE94F82BL) * 0.02;
            return 1.0 - radial / Math.max(0.45, 1.0 + lobes) + edge;
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
        private static double unit(long value) { return (value >>> 11) * 0x1.0p-53; }
    }
}
