# RealTerrain

RealTerrain is an opt-in NeoForge 26.3 world preset. It generates a seed-driven layout of 6–8 landmasses across a 500,000 × 500,000 block footprint, including one polar landmass at each end of the north–south axis. Polar desert dominates both caps, with cold ocean around them. The preset currently skips all vanilla cave carvers and cave density branches.

## Building

Use Java 25 and run:

```sh
bash gradlew build
```

The jar is written to the shared `IdeaProjects/builds` folder.

## World creation

Choose **Continental World** when creating a new world. The **Starting biome** selector offers these biomes from hot to cold: Tropical rainforest, Tropical seasonal forest, Savanna, Hot desert, Mediterranean woodland and shrubland, Temperate grassland, Temperate deciduous forest, Temperate rainforest, Cold desert and steppe, Boreal forest, Tundra, Polar desert, and Ice sea. These are custom biome definitions with copied Minecraft surface features, mob spawns, and effects tailored to each climate. The selected biome is searched across the footprint when the first player joins. **Default spawn** keeps normal spawn behavior.

## Whole-world map

In a singleplayer Continental World, run `/realterrain map` and open the displayed local link. The browser map samples the active world's density and climate samplers across the full footprint without generating every chunk. Switch between biome coverage, height (blue at sea level to red at the highest sampled point), and the worldgen temperature signal. Click a map location to inspect its biome, surface height, temperature signal, and coordinates. Detailed terrain, structures, and chunk-level features appear as the game generates chunks.

## Generation design

A deterministic 256 × 256 calibration samples the shoreline threshold to target 25–35% land. The north and south landmasses are placed at extreme latitudes; other landmasses occupy warmer middle latitudes. Warped coastlines and a separate field of mountain belts provide broad relief, ridges, and occasional high peaks. Temperature blends a pole-cold/equator-warm latitude gradient (85%) with vanilla variation (15%); a custom multi-noise source divides land into the thirteen custom biome types and retains vanilla ocean types away from the polar seas.

The preset's vertical range is Y=-64 to Y=2031, with mountain peaks designed to approach Y=1900. The footprint is a layout target, not a world border. Newly generated worlds are required to see the current terrain, biome, and cave settings; existing worlds and already generated chunks are unchanged.

## Distant Horizons

For far-distance terrain rendering on Minecraft 26.3, install Distant Horizons 3.3.4-26.3 alongside RealTerrain. Distant Horizons remains optional and is not bundled in the RealTerrain jar. RealTerrain's Continental World uses Minecraft's standard `NoiseBasedChunkGenerator` with custom density functions, allowing Distant Horizons to use its normal LOD world-generation path. Distant Horizons creates its own LOD data; it does not raise Minecraft's chunk render distance. Its world-generation settings control how much distant terrain is prepared and should be adjusted to available memory.
