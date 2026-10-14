package de.quarzee.realterrain.worldgen;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/** Passes the world creation size choice to world generation and applies it as the world border. */
public final class WorldSizeSelection {
    private static final long DEFAULT_SIZE = 3_000_000L;
    private static final ResourceKey<NoiseGeneratorSettings> CONTINENTAL_SETTINGS = ResourceKey.create(
            Registries.NOISE_SETTINGS, Identifier.fromNamespaceAndPath("realterrain", "continental"));
    private static volatile long size = DEFAULT_SIZE;
    private static volatile PendingSelection pending;

    private WorldSizeSelection() {}

    public static void select(long worldSize, long worldSeed) {
        size = worldSize;
        pending = new PendingSelection(worldSize, worldSeed);
    }

    public static long currentSize() {
        return size;
    }

    public static double halfWorldSize() {
        return size / 2.0;
    }

    public static void onServerStarting(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (!(overworld.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator generator)
                || !generator.generatorSettings().is(CONTINENTAL_SETTINGS)) {
            pending = null;
            return;
        }

        PendingSelection selection = pending;
        if (selection != null && selection.worldSeed() == overworld.getSeed()) {
            overworld.getWorldBorder().setCenter(0.0, 0.0);
            overworld.getWorldBorder().setSize(selection.size());
            size = selection.size();
        } else {
            size = Math.max(100_000L, Math.round(overworld.getWorldBorder().getSize()));
        }
        pending = null;
    }

    private record PendingSelection(long size, long worldSeed) {}
}
