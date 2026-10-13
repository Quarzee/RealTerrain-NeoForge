package de.quarzee.realterrain.worldgen;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

/** One-shot choice passed from the world-creation screen to the integrated server. */
public final class StartingBiomeSelection {
    private static volatile PendingSelection pending;

    private StartingBiomeSelection() {}

    public static void select(ClimateType climateType, long worldSeed) {
        pending = climateType == null ? null : new PendingSelection(climateType, worldSeed);
    }

    public static ClimateType currentClimateType() {
        return pending == null ? null : pending.climateType();
    }

    public static ClimateType consume(long worldSeed) {
        PendingSelection selection = pending;
        pending = null;
        return selection != null && selection.worldSeed() == worldSeed ? selection.climateType() : null;
    }

    public enum ClimateType {
        TROPICAL_RAINFOREST("tropical_rainforest"),
        TROPICAL_SEASONAL_FOREST("tropical_seasonal_forest"),
        SAVANNA("savanna"),
        HOT_DESERT("hot_desert"),
        MEDITERRANEAN_WOODLAND("mediterranean_woodland"),
        TEMPERATE_GRASSLAND("temperate_grassland"),
        TEMPERATE_DECIDUOUS_FOREST("temperate_deciduous_forest"),
        TEMPERATE_RAINFOREST("temperate_rainforest"),
        COLD_DESERT_STEPPE("cold_desert_steppe"),
        BOREAL_FOREST("boreal_forest"),
        TUNDRA("tundra"),
        POLAR_DESERT("polar_desert"),
        ICE_SEA("ice_sea");

        private final ResourceKey<Biome> biomeKey;

        ClimateType(String biomeId) {
            this.biomeKey = ResourceKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("realterrain", biomeId));
        }

        public boolean matches(Holder<Biome> biome) {
            return biome.is(biomeKey);
        }
    }

    private record PendingSelection(ClimateType climateType, long worldSeed) {}
}
