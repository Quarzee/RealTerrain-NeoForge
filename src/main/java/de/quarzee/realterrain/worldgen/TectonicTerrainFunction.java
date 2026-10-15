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

/** High-altitude terrain envelope with broad warped mountain belts and eroded ridges. */
public record TectonicTerrainFunction() implements DensityFunction {
    public static final MapCodec<TectonicTerrainFunction> CODEC = MapCodec.unit(new TectonicTerrainFunction());
    private static final Identifier LAYOUT_SEED = Identifier.fromNamespaceAndPath("realterrain", "continental_layout");
    private static final Identifier RELIEF_SEED = Identifier.fromNamespaceAndPath("realterrain", "tectonic_relief");

    static double mountainStrength(long seed, int x, int z, ContinentalLandmassFunction.LandmassSampler land) {
        double continentalness = land.sample(x, z);
        TectonicPlateModel.Signal plate = TectonicPlateModel.sample(seed, x, z);
        double beltStrength = Math.max(plate.convergence() * plate.boundaryStrength(),
                Math.max(plate.volcanicPotential(), plate.divergence() * plate.boundaryStrength() * 0.16));
        double inland = smoothstep(0.02, 0.56, continentalness);
        return beltStrength * inland;
    }

    private static double smoothstep(double edge0, double edge1, double value) {
        double t = Math.max(0.0, Math.min(1.0, (value - edge0) / (edge1 - edge0)));
        return t * t * (3.0 - 2.0 * t);
    }

    @Override
    public DensitySampler compileSampler(CompileContext context) {
        long layoutSeed = context.createRandom(LAYOUT_SEED).nextLong();
        long reliefSeed = context.createRandom(RELIEF_SEED).nextLong();
        TerrainSampler sampler = new TerrainSampler(
                ContinentalLandmassFunction.createSampler(layoutSeed), reliefSeed);
        return new DensitySampler() {
            @Override
            public float sampleValue(SamplerContext context, int x, int y, int z) {
                return sampler.density(x, y, z);
            }

            @Override
            public void sampleVolume(SamplerContext context, DensityBuffer out, DensityVolume volume) {
                int index = 0;
                for (int zi = 0; zi < volume.sizeZ(); zi++) {
                    int z = volume.blockZ(zi);
                    for (int xi = 0; xi < volume.sizeX(); xi++) {
                        double surface = sampler.surfaceHeight(volume.blockX(xi), z);
                        for (int yi = 0; yi < volume.sizeY(); yi++) {
                            int y = volume.blockY(yi);
                            // A gentle vertical gradient keeps the surface smooth while leaving
                            // enough range for vanilla's cave and surface density operations.
                            out.set(index++, (float) ((surface - y) * 0.025));
                        }
                    }
                }
            }
        };
    }

    @Override public DensityFunction rewriteChildren(DfRewriteRule rule) { return this; }
    @Override public Interval range() { return Interval.of(-64.0F, 64.0F); }
    @Override public @DensityFunction.Axes int domainAxes() { return DensityFunction.AXIS_X | DensityFunction.AXIS_Y | DensityFunction.AXIS_Z; }
    @Override public MapCodec<TectonicTerrainFunction> codec() { return CODEC; }

    private static final class TerrainSampler {
        private final ContinentalLandmassFunction.LandmassSampler land;
        private final long seed;

        private TerrainSampler(ContinentalLandmassFunction.LandmassSampler land, long seed) {
            this.land = land;
            this.seed = seed;
        }

        private float density(int x, int y, int z) {
            return (float) ((surfaceHeight(x, z) - y) * 0.025);
        }

        private double surfaceHeight(int x, int z) {
            double continentalness = land.sample(x, z);
            // Keep the continental shelves below sea level and lift coasts gradually.
            double inland = smoothstep(-0.24, 0.32, continentalness);
            double oceanNoise = noise(x, z, 24_000.0, 0x6A09E667F3BCC909L);
            double lowlands = 25.0 + oceanNoise * 18.0;
            double plains = 73.0 + inland * (92.0 + noise(x, z, 18_000.0, 0xBB67AE8584CAA73BL) * 34.0);

            // Moving plate boundaries replace the previous arbitrary straight belts.
            // Convergent margins build ranges; subduction margins receive extra volcanic uplift.
            TectonicPlateModel.Signal plate = TectonicPlateModel.sample(seed, x, z);
            double boundaryStrength = plate.boundaryStrength();
            double convergence = plate.convergence() * boundaryStrength;
            double divergence = plate.divergence() * boundaryStrength;
            // The plate model already folds the boundary envelope into volcanic potential.
            double volcanicPotential = plate.volcanicPotential();
            double beltStrength = Math.max(convergence, Math.max(volcanicPotential, divergence * 0.16));
            beltStrength *= smoothstep(0.02, 0.56, continentalness);

            // Broad uplift sets mountain mass; nested ridged and eroded signals shape peaks.
            double regionalUplift = 0.55 + 0.45 * ((noise(x, z, 13_000.0, 0x5BE0CD19137E2179L) + 1.0) * 0.5);
            double mass = Math.pow(Math.max(0.0, beltStrength), 1.15);
            double peakCore = smoothstep(0.25, 0.80, beltStrength) * regionalUplift;
            double alpinePeaks = Math.pow(Math.max(0.0, peakCore), 4.0) * 1_700.0;
            double highland = mass * (950.0 + 220.0 * regionalUplift) + alpinePeaks
                    + Math.pow(Math.max(0.0, volcanicPotential), 2.4) * 300.0;
            double erosion = (noise(x, z, 8_500.0, 0xCBBB9D5DC1059ED8L) + 1.0) * 0.5;
            double ridged = 1.0 - Math.abs(noise(x, z, 3_200.0, 0x629A292A367CD507L));
            double fineRidges = 1.0 - Math.abs(noise(x, z, 1_050.0, 0x9159015A3070DD17L));
            double relief = beltStrength * (ridged * 420.0 + fineRidges * 190.0 - erosion * 105.0);
            double roughLowlands = noise(x, z, 2_800.0, 0x152FECD8F70E5939L) * (8.0 + inland * 42.0);
            double surface = lowlands + inland * (plains - lowlands) + highland + relief + roughLowlands;
            if (surface > 1_820.0) {
                surface = 1_820.0 + 180.0 * Math.tanh((surface - 1_820.0) / 180.0);
            }
            return surface;
        }

        private double noise(double x, double z, double scale, long salt) {
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

        private static double smooth(double t) { return t * t * (3.0 - 2.0 * t); }
        private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
        private static int floor(double value) { int i = (int) value; return value < i ? i - 1 : i; }
        private static long mix64(long z) {
            z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
            z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
            return z ^ (z >>> 31);
        }

    }
}
