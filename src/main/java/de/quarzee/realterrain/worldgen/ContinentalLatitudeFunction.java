package de.quarzee.realterrain.worldgen;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.Interval;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

/** Latitude gradient scaled to the selected square world footprint. */
public record ContinentalLatitudeFunction() implements DensityFunction {
    public static final MapCodec<ContinentalLatitudeFunction> CODEC = MapCodec.unit(new ContinentalLatitudeFunction());

    private static float sample(int z) {
        double normalizedLatitude = Math.min(1.0, Math.abs(z) / WorldSizeSelection.halfWorldSize());
        return (float) (1.0 - 2.0 * normalizedLatitude);
    }

    @Override
    public DensitySampler compileSampler(CompileContext context) {
        return new DensitySampler() {
            @Override public float sampleValue(SamplerContext context, int x, int y, int z) { return sample(z); }

            @Override
            public void sampleVolume(SamplerContext context, DensityBuffer out, DensityVolume volume) {
                int index = 0;
                for (int zi = 0; zi < volume.sizeZ(); zi++) {
                    float value = sample(volume.blockZ(zi));
                    for (int xi = 0; xi < volume.sizeX(); xi++) {
                        for (int yi = 0; yi < volume.sizeY(); yi++) out.set(index++, value);
                    }
                }
            }
        };
    }

    @Override public DensityFunction rewriteChildren(DfRewriteRule rule) { return this; }
    @Override public Interval range() { return Interval.of(-1.0F, 1.0F); }
    @Override public @DensityFunction.Axes int domainAxes() { return DensityFunction.AXIS_Z; }
    @Override public MapCodec<ContinentalLatitudeFunction> codec() { return CODEC; }
}
