# RealTerrain

RealTerrain is an opt-in NeoForge 26.3 world preset. It generates seed-driven, irregular, very large continents separated by broad ocean basins. The pattern continues across the playable world with no custom 500,000-block generation boundary or fixed total continent count. The preset currently skips all vanilla cave carvers and cave density branches.

## Building

Use Java 25 and run:

```sh
bash gradlew build
```

The jar is written to the shared `IdeaProjects/builds` folder.

## World creation

Choose **Continental World** when creating a new world. The **Starting biome** selector offers these biomes from hot to cold: Tropical rainforest, Tropical seasonal forest, Savanna, Hot desert, Mediterranean woodland and shrubland, Temperate grassland, Temperate deciduous forest, Temperate rainforest, Alpine, Cold desert and steppe, Boreal forest, Tundra, Polar desert, and Ice sea. These are custom biome definitions with surface features, mob spawns, and effects tailored to each climate. The selected biome is searched near the world origin when the first player joins. **Default spawn** keeps normal spawn behavior.

## Whole-world map

In a singleplayer Continental World, run `/realterrain map` and open the displayed local link. The browser map previews a 3,000,000 × 3,000,000 block region of the unbounded world without generating every chunk. Switch between biome coverage, height (blue at sea level with a hue shift every 10 blocks above sea level), and the worldgen temperature signal. Click a map location to inspect its biome, surface height, temperature signal, and coordinates. Panoramic regions outside this preview continue to generate normally as the player travels.

## Generation design

A deterministic, unbounded lattice places large seed-specific continent shapes about 1,350,000 blocks apart, with varied sizes, warped shorelines, and broad ocean gaps. One continent is centered on the origin for reliable initial land access; the remaining continents continue across the world. The land-to-ocean share emerges from the spacing and size of nearby continents rather than a finite-map calibration. A separate field of tectonic mountain belts provides broad relief, ridges, and high peaks. Temperature blends a pole-cold/equator-warm latitude gradient (85%) with vanilla variation (15%); a custom multi-noise source divides land into the fourteen custom biome types and retains vanilla ocean types.

The preset's vertical range is Y=-64 to Y=2031, with mountain peaks designed to approach Y=1900. Newly generated chunks use the current terrain, biome, and cave settings; existing chunks are unchanged.

## Distant Horizons

For far-distance terrain rendering on Minecraft 26.3, install Distant Horizons 3.3.4-26.3 alongside RealTerrain. Distant Horizons remains optional and is not bundled in the RealTerrain jar. RealTerrain's Continental World uses Minecraft's standard `NoiseBasedChunkGenerator` with custom density functions, allowing Distant Horizons to use its normal LOD world-generation path. Distant Horizons creates its own LOD data; it does not raise Minecraft's chunk render distance. Its world-generation settings control how much distant terrain is prepared and should be adjusted to available memory.
