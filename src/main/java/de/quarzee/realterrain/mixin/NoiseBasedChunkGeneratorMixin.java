package de.quarzee.realterrain.mixin;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseBasedChunkGeneratorMixin {
    private static final ResourceKey<NoiseGeneratorSettings> REALTERRAIN_CONTINENTAL = ResourceKey.create(
            Registries.NOISE_SETTINGS,
            Identifier.fromNamespaceAndPath("realterrain", "continental"));

    @Shadow @Final private Holder<NoiseGeneratorSettings> settings;

    @Inject(method = "generateCarvers", at = @At("HEAD"), cancellable = true)
    private void realterrain$skipCaveCarvers(CallbackInfo callback) {
        if (settings.is(REALTERRAIN_CONTINENTAL)) {
            callback.cancel();
        }
    }
}
