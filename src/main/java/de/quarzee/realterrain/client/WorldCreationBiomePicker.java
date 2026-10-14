package de.quarzee.realterrain.client;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

import de.quarzee.realterrain.RealTerrain;
import de.quarzee.realterrain.worldgen.StartingBiomeSelection;
import de.quarzee.realterrain.worldgen.WorldSizeSelection;

@EventBusSubscriber(modid = RealTerrain.MODID, value = Dist.CLIENT)
public final class WorldCreationBiomePicker {
    private static final Choice DEFAULT = new Choice(null);
    private static final List<Long> WORLD_SIZES = List.of(100_000L, 250_000L, 500_000L, 1_000_000L,
            2_000_000L, 3_000_000L, 5_000_000L, 10_000_000L);
    private static final List<Choice> CHOICES = List.of(
            DEFAULT,
            new Choice(StartingBiomeSelection.ClimateType.TROPICAL_RAINFOREST),
            new Choice(StartingBiomeSelection.ClimateType.TROPICAL_SEASONAL_FOREST),
            new Choice(StartingBiomeSelection.ClimateType.SAVANNA),
            new Choice(StartingBiomeSelection.ClimateType.HOT_DESERT),
            new Choice(StartingBiomeSelection.ClimateType.MEDITERRANEAN_WOODLAND),
            new Choice(StartingBiomeSelection.ClimateType.TEMPERATE_GRASSLAND),
            new Choice(StartingBiomeSelection.ClimateType.TEMPERATE_DECIDUOUS_FOREST),
            new Choice(StartingBiomeSelection.ClimateType.TEMPERATE_RAINFOREST),
            new Choice(StartingBiomeSelection.ClimateType.COLD_DESERT_STEPPE),
            new Choice(StartingBiomeSelection.ClimateType.BOREAL_FOREST),
            new Choice(StartingBiomeSelection.ClimateType.TUNDRA),
            new Choice(StartingBiomeSelection.ClimateType.POLAR_DESERT),
            new Choice(StartingBiomeSelection.ClimateType.ICE_SEA));

    private WorldCreationBiomePicker() {}

    @SubscribeEvent
    public static void onCreateWorldScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof CreateWorldScreen screen)) return;

        StartingBiomeSelection.ClimateType selected = StartingBiomeSelection.currentClimateType();
        Choice initial = CHOICES.stream().filter(choice -> Objects.equals(choice.climateType(), selected)).findFirst().orElse(DEFAULT);
        WorldSizeSelection.select(WorldSizeSelection.currentSize(), screen.getUiState().getSettings().options().seed());
        if (selected != null) {
            StartingBiomeSelection.select(selected, screen.getUiState().getSettings().options().seed());
        }
        screen.getUiState().addListener(state -> {
            WorldSizeSelection.select(WorldSizeSelection.currentSize(), state.getSettings().options().seed());
            StartingBiomeSelection.ClimateType current = StartingBiomeSelection.currentClimateType();
            if (current != null) {
                StartingBiomeSelection.select(current, state.getSettings().options().seed());
            }
        });
        String allowCommandsLabel = Component.translatable("selectWorld.allowCommands").getString();
        AbstractWidget allowCommandsButton = screen.children().stream()
                .filter(AbstractWidget.class::isInstance)
                .map(AbstractWidget.class::cast)
                .filter(widget -> widget.getMessage().getString().startsWith(allowCommandsLabel))
                .findFirst()
                .orElse(null);
        if (allowCommandsButton == null) return;

        CycleButton<Choice> picker = CycleButton.builder(Choice::label, DEFAULT)
                .withValues(CHOICES)
                .create(allowCommandsButton.getX(), allowCommandsButton.getY() + allowCommandsButton.getHeight() + 8,
                        allowCommandsButton.getWidth(), 20, Component.translatable("gui.realterrain.starting_biome"),
                        (button, choice) -> StartingBiomeSelection.select(choice.climateType(), screen.getUiState().getSettings().options().seed()));
        picker.setValue(initial);
        event.addListener(picker);

        CycleButton<Long> sizePicker = CycleButton.<Long>builder(WorldCreationBiomePicker::formatWorldSize, 3_000_000L)
                .withValues(WORLD_SIZES)
                .create(allowCommandsButton.getX(), picker.getY() + picker.getHeight() + 8,
                        allowCommandsButton.getWidth(), 20, Component.translatable("gui.realterrain.world_size"),
                        (button, value) -> WorldSizeSelection.select(value, screen.getUiState().getSettings().options().seed()));
        sizePicker.setValue(WorldSizeSelection.currentSize());
        event.addListener(sizePicker);
    }

    private static Component formatWorldSize(Long size) {
        String formatted = String.format(Locale.ROOT, "%,d × %,d", size, size);
        return Component.literal(formatted);
    }

    private record Choice(StartingBiomeSelection.ClimateType climateType) {
        private Component label() {
            return climateType == null
                    ? Component.translatable("gui.realterrain.starting_biome.default")
                    : Component.translatable("gui.realterrain.starting_biome." + climateType.name().toLowerCase(Locale.ROOT));
        }
    }
}
