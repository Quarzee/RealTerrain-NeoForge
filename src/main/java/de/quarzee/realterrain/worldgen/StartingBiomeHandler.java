package de.quarzee.realterrain.worldgen;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.storage.LevelData;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public final class StartingBiomeHandler {
    private static final int SEARCH_RADIUS = 250_000;
    private static final int SAMPLE_STEP_QUARTS = 256; // 1,024 blocks

    private StartingBiomeHandler() {}

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        StartingBiomeSelection.ClimateType target = StartingBiomeSelection.consume(level.getSeed());
        if (target == null || !level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) return;

        BiomeSource biomeSource = level.getChunkSource().getGenerator().getBiomeSource();
        RandomState randomState = level.getChunkSource().randomState();
        Pair<BlockPos, net.minecraft.core.Holder<Biome>> match = biomeSource.findBiomeHorizontal(
                0, 63, 0, SEARCH_RADIUS, SAMPLE_STEP_QUARTS,
                target::matches, RandomSource.create(level.getSeed()), true, randomState);

        if (match == null) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.realterrain.starting_biome_not_found"));
            return;
        }

        int x = match.getFirst().getX();
        int z = match.getFirst().getZ();
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos surface = new BlockPos(x, Math.max(level.getMinY() + 1, y), z);
        if (!target.matches(level.getBiome(surface.below()))) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.realterrain.starting_biome_surface_mismatch"));
            return;
        }

        level.setRespawnData(LevelData.RespawnData.of(level.dimension(), surface, player.getYRot(), player.getXRot()));
        player.teleportTo(x + 0.5, y, z + 0.5);
        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.realterrain.starting_biome_arrived"));
    }

}
