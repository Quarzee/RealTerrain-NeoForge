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

/** Climate signal that maps the generated convergent plate belts to high erosion values. */
public record TectonicMountainFunction() implements DensityFunction {
    public static final MapCodec<TectonicMountainFunction> CODEC = MapCodec.unit(new TectonicMountainFunction());
    private static final Identifier LAYOUT_SEED = Identifier.fromNamespaceAndPath("realterrain", "continental_layout");
    private static final Identifier RELIEF_SEED = Identifier.fromNamespaceAndPath("realterrain", "tectonic_relief");

    @Override
    public DensitySampler compileSampler(CompileContext context) {
        long layoutSeed = context.createRandom(LAYOUT_SEED).nextLong();
        long reliefSeed = context.createRandom(RELIEF_SEED).nextLong();
        var land = ContinentalLandmassFunction.createSampler(layoutSeed);
        return new DensitySampler() {
            private float sample(int x, int z) {
                return (float) (TectonicTerrainFunction.mountainStrength(reliefSeed, x, z, land) * 2.0 - 1.0);
            }

            @Override public float sampleValue(SamplerContext context, int x, int y, int z) { return sample(x, z); }

            @Override
            public void sampleVolume(SamplerContext context, DensityBuffer out, DensityVolume volume) {
                int index = 0;
                for (int zi = 0; zi < volume.sizeZ(); zi++) {
                    int z = volume.blockZ(zi);
                    for (int xi = 0; xi < volume.sizeX(); xi++) {
                        float value = sample(volume.blockX(xi), z);
                        for (int yi = 0; yi < volume.sizeY(); yi++) out.set(index++, value);
                    }
                }
            }
        };
    }

    @Override public DensityFunction rewriteChildren(DfRewriteRule rule) { return this; }
    @Override public Interval range() { return Interval.of(-1.0F, 1.0F); }
    @Override public @DensityFunction.Axes int domainAxes() { return DensityFunction.AXIS_X | DensityFunction.AXIS_Z; }
    @Override public MapCodec<TectonicMountainFunction> codec() { return CODEC; }
}
