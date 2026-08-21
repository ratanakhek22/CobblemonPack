package com.ratana.cobbleforge.research.block.entity;

import com.ratana.cobbleforge.CobbleForgeMod;
import com.ratana.cobbleforge.research.node.ModResearchNodes;
import com.ratana.cobbleforge.research.node.ResearchNodeDefinition;
import com.ratana.cobbleforge.research.player.ModAttachments;
import com.ratana.cobbleforge.research.player.NodeProgress;
import com.ratana.cobbleforge.research.player.ResearchPlayerData;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.particles.ParticleTypes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * No inventory, no GUI. State is purely transient (not NBT-saved) -- a server restart or
 * chunk unload mid-attempt just loses the in-progress accumulation, which is an acceptable
 * (arguably correct) outcome for a live sacrifice ritual rather than something worth
 * persisting.
 */
public class AltarBlockEntity extends BlockEntity {

    private static final int TICK_INTERVAL = 20;         // matches "every ~1 second" per design
    private static final long TIMEOUT_TICKS = 20 * 30;    // 30s to complete a sacrifice once started; tune freely

    private UUID committedThrower;
    private long startTick;
    private final Map<ResourceLocation, Integer> accumulated = new HashMap<>();

    public AltarBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ALTAR.get(), pos, state);
    }

    public static void serverTick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, AltarBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (level.getGameTime() % TICK_INTERVAL != 0) return;

        be.scanAndProcess(serverLevel, pos);

        if (be.committedThrower != null && level.getGameTime() - be.startTick > TIMEOUT_TICKS) {
            be.fail(serverLevel, pos);
        }
    }

    /** ASSUMPTION: the detection box shape/size here is a first pass -- tune the inflate
     *  values once you see it in-game relative to your actual altar model's top surface. */
    private void scanAndProcess(ServerLevel level, BlockPos pos) {
        AABB detectionBox = new AABB(pos).expandTowards(0, 1.5, 0).inflate(0.4, 0, 0.4);
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, detectionBox);

        for (ItemEntity itemEntity : items) {
            // ASSUMPTION: ItemEntity#getThrower() returning a nullable UUID is the correct
            // 1.21.1 API for "who dropped this" -- unset for anything not player-thrown
            // (tree saplings, hopper output, loot drops). Verify on first compile; if the
            // method name/signature differs, this is the one line to fix.
            Entity ownerEntity = itemEntity.getOwner();
            UUID thrower = ownerEntity != null ? ownerEntity.getUUID() : null;
            if (thrower == null) continue;

            if (committedThrower == null) {
                committedThrower = thrower;
                startTick = level.getGameTime();
            } else if (!committedThrower.equals(thrower)) {
                // A second player's item landed mid-attempt -- immediate cancel per design.
                itemEntity.discard(); // this interfering item is also consumed, not left behind
                fail(level, pos);
                return;
            }

            ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(itemEntity.getItem().getItem());
            int count = itemEntity.getItem().getCount();

            if (!fitsAnyCandidateRecipe(level, thrower, itemId)) {
                itemEntity.discard();
                fail(level, pos);
                return;
            }

            accumulated.merge(itemId, count, Integer::sum);
            itemEntity.discard();

            checkForMatch(level, pos, thrower);
        }
    }

    private boolean fitsAnyCandidateRecipe(ServerLevel level, UUID thrower, ResourceLocation itemId) {
        for (ResearchNodeDefinition def : candidateRecipes(level, thrower)) {
            Map<ResourceLocation, Integer> required = normalized(def.requiredItems());
            int alreadyHave = accumulated.getOrDefault(itemId, 0);
            int requiredCount = required.getOrDefault(itemId, 0);
            if (requiredCount > 0 && alreadyHave < requiredCount) return true;
        }
        return false;
    }

    private void checkForMatch(ServerLevel level, BlockPos pos, UUID thrower) {
        for (ResearchNodeDefinition def : candidateRecipes(level, thrower)) {
            if (normalized(def.requiredItems()).equals(accumulated)) {
                summon(level, pos, thrower, def);
                return;
            }
        }
    }

    private List<ResearchNodeDefinition> candidateRecipes(ServerLevel level, UUID thrower) {
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(thrower);
        if (player == null) return List.of();

        ResearchPlayerData data = player.getData(ModAttachments.RESEARCH_DATA.get());
        return ModResearchNodes.all().values().stream()
                .filter(def -> !def.bespoke())
                .filter(def -> data.getProgress(def.speciesId()) == NodeProgress.READY_FOR_SACRIFICE)
                .toList();
    }

    private static Map<ResourceLocation, Integer> normalized(List<ResearchNodeDefinition.RequiredItem> items) {
        Map<ResourceLocation, Integer> map = new HashMap<>();
        for (var req : items) {
            map.merge(req.item(), req.count(), Integer::sum);
        }
        return map;
    }

    private void summon(ServerLevel level, BlockPos pos, UUID thrower, ResearchNodeDefinition def) {
        // TODO: replace with the real Cobblemon spawn call once verified (you mentioned an
        // in-game command exists, so the underlying capability is confirmed real -- this is
        // a placeholder so the rest of the state machine is fully testable in the meantime).
        EntityType.CHICKEN.spawn(level, pos.above(), net.minecraft.world.entity.MobSpawnType.MOB_SUMMONED);

        level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0F, 1.0F);
        reset();
    }

    private void fail(ServerLevel level, BlockPos pos) {
        level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                20, 0.3, 0.3, 0.3, 0.02);
        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 1.0F);
        reset();
    }

    private void reset() {
        committedThrower = null;
        accumulated.clear();
        startTick = 0;
    }
}