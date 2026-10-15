package de.quarzee.realterrain.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Saved world-generation controls used when unexplored chunks are generated later. */
public final class WorldGenerationSettingsData extends SavedData {
    public static final SavedDataType<WorldGenerationSettingsData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("realterrain", "world_generation_settings"),
            WorldGenerationSettingsData::new,
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.LONG.optionalFieldOf("world_size", 3_000_000L).forGetter(data -> data.worldSize),
                    Codec.INT.optionalFieldOf("land_coverage_percent", 29).forGetter(data -> data.landCoveragePercent)
            ).apply(instance, WorldGenerationSettingsData::new)));

    private long worldSize = 3_000_000L;
    private int landCoveragePercent = 29;

    public WorldGenerationSettingsData() {}

    private WorldGenerationSettingsData(long worldSize, int landCoveragePercent) {
        this.worldSize = normalizeWorldSize(worldSize);
        this.landCoveragePercent = normalizeCoverage(landCoveragePercent);
    }

    public long worldSize() {
        return worldSize;
    }

    public int landCoveragePercent() {
        return landCoveragePercent;
    }

    public void update(long worldSize, int landCoveragePercent) {
        long normalizedSize = normalizeWorldSize(worldSize);
        int normalizedCoverage = normalizeCoverage(landCoveragePercent);
        if (this.worldSize == normalizedSize && this.landCoveragePercent == normalizedCoverage) return;
        this.worldSize = normalizedSize;
        this.landCoveragePercent = normalizedCoverage;
        setDirty();
    }

    private static long normalizeWorldSize(long value) {
        return Math.max(100_000L, Math.min(10_000_000L, value));
    }

    private static int normalizeCoverage(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
