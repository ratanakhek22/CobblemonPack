package com.ratana.cobbleforge.research.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class RuinBlockEntity extends BlockEntity {
    private final Set<UUID> claimedBy = new HashSet<>();

    public RuinBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RUIN_BLOCK.get(), pos, state);
    }

    public boolean hasClaimed(Player player) {
        return claimedBy.contains(player.getUUID());
    }

    public void markClaimed(Player player) {
        claimedBy.add(player.getUUID());
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag list = new ListTag();
        for (UUID id : claimedBy) {
            list.add(NbtUtils.createUUID(id));
        }
        tag.put("ClaimedBy", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        claimedBy.clear();
        ListTag list = tag.getList("ClaimedBy", Tag.TAG_INT_ARRAY);
        for (int i = 0; i < list.size(); i++) {
            claimedBy.add(NbtUtils.loadUUID(list.get(i)));
        }
    }
}
