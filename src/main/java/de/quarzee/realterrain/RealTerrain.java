package de.quarzee.realterrain;

import java.io.IOException;
import java.net.URI;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import de.quarzee.realterrain.explorer.TerrainExplorerServer;
import de.quarzee.realterrain.worldgen.ContinentalLandmassFunction;
import de.quarzee.realterrain.worldgen.TectonicTerrainFunction;
import de.quarzee.realterrain.worldgen.TectonicMountainFunction;
import de.quarzee.realterrain.worldgen.ContinentalLatitudeFunction;
import de.quarzee.realterrain.worldgen.WorldSizeSelection;

import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import de.quarzee.realterrain.worldgen.StartingBiomeHandler;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(RealTerrain.MODID)
public class RealTerrain {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "realterrain";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceKey<NoiseGeneratorSettings> CONTINENTAL_SETTINGS = ResourceKey.create(
            Registries.NOISE_SETTINGS, Identifier.fromNamespaceAndPath(MODID, "continental"));

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public RealTerrain(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(RealTerrain::registerDensityFunction);
        NeoForge.EVENT_BUS.addListener(StartingBiomeHandler::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(RealTerrain::registerCommands);
        NeoForge.EVENT_BUS.addListener(RealTerrain::onServerStopping);
        NeoForge.EVENT_BUS.addListener((ServerStartingEvent event) -> WorldSizeSelection.onServerStarting(event.getServer()));
    }

    private static void registerDensityFunction(RegisterEvent event) {
        event.register(Registries.DENSITY_FUNCTION_TYPE,
                Identifier.fromNamespaceAndPath(MODID, "continental_landmass"),
                () -> ContinentalLandmassFunction.CODEC);
        event.register(Registries.DENSITY_FUNCTION_TYPE,
                Identifier.fromNamespaceAndPath(MODID, "tectonic_terrain"),
                () -> TectonicTerrainFunction.CODEC);
        event.register(Registries.DENSITY_FUNCTION_TYPE,
                Identifier.fromNamespaceAndPath(MODID, "tectonic_mountain"),
                () -> TectonicMountainFunction.CODEC);
        event.register(Registries.DENSITY_FUNCTION_TYPE,
                Identifier.fromNamespaceAndPath(MODID, "continental_latitude"),
                () -> ContinentalLatitudeFunction.CODEC);
    }

    private static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("realterrain")
                .then(net.minecraft.commands.Commands.literal("map").executes(context -> openWorldMap(context.getSource()))));
    }

    private static int openWorldMap(CommandSourceStack source) {
        if (source.getServer().isDedicatedServer()) {
            source.sendFailure(Component.literal("The RealTerrain map is available in singleplayer worlds."));
            return 0;
        }
        ServerLevel overworld = source.getServer().overworld();
        if (!(overworld.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator generator)
                || !generator.generatorSettings().is(CONTINENTAL_SETTINGS)) {
            source.sendFailure(Component.literal("The map is available only in a Continental World."));
            return 0;
        }

        try {
            String url = TerrainExplorerServer.start(overworld);
            Component link = Component.literal(url).withStyle(style -> style
                    .withClickEvent(new ClickEvent.OpenUrl(URI.create(url)))
                    .withUnderlined(true));
            source.sendSuccess(() -> Component.literal("RealTerrain world map: ").append(link), false);
            return 1;
        } catch (IOException exception) {
            LOGGER.error("Unable to start the RealTerrain world map server", exception);
            source.sendFailure(Component.literal("Could not start the RealTerrain map: " + exception.getMessage()));
            return 0;
        }
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        TerrainExplorerServer.stop();
    }
}
