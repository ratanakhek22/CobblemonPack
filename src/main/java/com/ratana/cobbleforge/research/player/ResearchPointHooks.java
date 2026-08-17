package com.ratana.cobbleforge.research.player;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokemon.PokedexDataChangedEvent;
import com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent;
import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress;
import com.ratana.cobbleforge.CobbleForgeMod;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

public class ResearchPointHooks {

    /** Call once during mod setup. */
    public static void register() {
        CobblemonEvents.POKEMON_CAPTURED.subscribe(Priority.NORMAL, (Function1<? super PokemonCapturedEvent, Unit>) ResearchPointHooks::onCaptured);
        CobblemonEvents.POKEDEX_DATA_CHANGED_POST.subscribe(Priority.NORMAL, (Function1<? super PokedexDataChangedEvent.Post, Unit>) ResearchPointHooks::onPokedexChanged);
    }

    private static Unit onCaptured(PokemonCapturedEvent event) {
        ServerPlayer player = event.getPlayer();
        ResourceLocation speciesId = event.getPokemon().getSpecies().getResourceIdentifier();

        ResearchPlayerData data = player.getData(com.ratana.cobbleforge.research.player.ModAttachments.RESEARCH_DATA.get());
        if (data.markCaptureAwarded(speciesId)) {
            data.addPoints(1);
            CobbleForgeMod.LOGGER.debug("Awarded capture point for {} to {}", speciesId, player.getName().getString());
        }
        return Unit.INSTANCE;
    }

    private static Unit onPokedexChanged(PokedexDataChangedEvent.Post event) {
        // Only award once a species reaches at least ENCOUNTERED — CAUGHT also satisfies this,
        // so a capture (which fires this event too) won't double-award; markDexAwarded already
        // guards the "first time only" part regardless of which knowledge level triggered it.
        if (event.getKnowledge() == PokedexEntryProgress.NONE) {
            return Unit.INSTANCE;
        }

        ResourceLocation speciesId = event.getRecord().getSpeciesDexRecord().getId();
        ServerPlayer player = ServerLifecycleHooks.getCurrentServer()
                .getPlayerList()
                .getPlayer(event.getPlayerUUID());

        if (player == null) {
            return Unit.INSTANCE; // defensive: shouldn't normally happen, event fires for an online player
        }

        ResearchPlayerData data = player.getData(com.ratana.cobbleforge.research.player.ModAttachments.RESEARCH_DATA.get());
        if (data.markDexAwarded(speciesId)) {
            data.addPoints(1);
            CobbleForgeMod.LOGGER.debug("Awarded dex point for {} to {}", speciesId, player.getName().getString());
        }
        return Unit.INSTANCE;
    }
}