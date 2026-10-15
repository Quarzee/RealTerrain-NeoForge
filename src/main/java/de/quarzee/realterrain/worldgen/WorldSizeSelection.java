package de.quarzee.realterrain.worldgen;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.event.level.LevelEvent;

/** Passes world creation controls to generation and restores them from the world save. */
public final class WorldSizeSelection {
    private static final long DEFAULT_SIZE = 3_000_000L;
    private static final int DEFAULT_LAND_COVERAGE = 29;
    private static final ResourceKey<NoiseGeneratorSettings> CONTINENTAL_SETTINGS = ResourceKey.create(
            Registries.NOISE_SETTINGS, Identifier.fromNamespaceAndPath("realterrain", "continental"));
    private static volatile long size = DEFAULT_SIZE;
    private static volatile int landCoveragePercent = DEFAULT_LAND_COVERAGE;
    private static volatile PendingSelection pending;

    private WorldSizeSelection() {}

    public static void select(long worldSize, int coveragePercent, long worldSeed) {
        size = normalizeSize(worldSize);
        landCoveragePercent = normalizeCoverage(coveragePercent);
        pending = new PendingSelection(size, landCoveragePercent, worldSeed);
    }

    public static long currentSize() {
        return size;
    }

    public static int currentLandCoveragePercent() {
        return landCoveragePercent;
    }

    public static double halfWorldSize() {
        return size / 2.0;
    }

    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension().equals(Level.OVERWORLD)) {
            initializeForWorld(level);
        }
    }

    public static void onServerStarting(MinecraftServer server) {
        initializeForWorld(server.overworld());
    }

    private static synchronized void initializeForWorld(ServerLevel level) {
        if (!(level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator generator)
                || !generator.generatorSettings().is(CONTINENTAL_SETTINGS)) {
            pending = null;
            return;
        }

        WorldGenerationSettingsData saved = level.getDataStorage().get(WorldGenerationSettingsData.TYPE);
        PendingSelection selection = pending;
        if (selection != null && selection.worldSeed() == level.getSeed()) {
            size = selection.size();
            landCoveragePercent = selection.landCoveragePercent();
            level.getWorldBorder().setCenter(0.0, 0.0);
            level.getWorldBorder().setSize(size);
            if (saved == null) saved = level.getDataStorage().computeIfAbsent(WorldGenerationSettingsData.TYPE);
            saved.update(size, landCoveragePercent);
        } else {
            if (saved == null) {
                double borderSize = level.getWorldBorder().getSize();
                size = borderSize >= 100_000.0 && borderSize <= 10_000_000.0
                        ? Math.round(borderSize) : DEFAULT_SIZE;
                landCoveragePercent = DEFAULT_LAND_COVERAGE;
                saved = level.getDataStorage().computeIfAbsent(WorldGenerationSettingsData.TYPE);
                saved.update(size, landCoveragePercent);
            } else {
                size = saved.worldSize();
                landCoveragePercent = saved.landCoveragePercent();
            }
        }
        pending = null;
    }

    private static long normalizeSize(long value) {
        return Math.max(100_000L, Math.min(10_000_000L, value));
    }

    private static int normalizeCoverage(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private record PendingSelection(long size, int landCoveragePercent, long worldSeed) {}
}
