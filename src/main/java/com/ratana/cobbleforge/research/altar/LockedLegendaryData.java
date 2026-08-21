package com.ratana.cobbleforge.research.altar;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class LockedLegendaryData extends SavedData {
    private static final String ID = "cobbleforge_locked_legendaries";

    private final Set<ResourceLocation> locked = new HashSet<>();

    public static LockedLegendaryData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new net.minecraft.world.level.saveddata.SavedData.Factory<>(
                        LockedLegendaryData::new, LockedLegendaryData::load, null),
                ID);
    }

    public boolean isLocked(ResourceLocation species) {
        return locked.contains(species);
    }

    public void lock(ResourceLocation species) {
        if (locked.add(species)) setDirty();
    }

    public void unlock(ResourceLocation species) {
        if (locked.remove(species)) setDirty();
    }

    private static LockedLegendaryData load(CompoundTag tag, HolderLookup.Provider registries) {
        LockedLegendaryData data = new LockedLegendaryData();
        ListTag list = tag.getList("locked", 8); // 8 = string tag id
        for (int i = 0; i < list.size(); i++) {
            data.locked.add(ResourceLocation.parse(list.getString(i)));
        }
        return data;
    }

    @Override
    public CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        ListTag list = new ListTag();
        for (ResourceLocation id : locked) {
            list.add(StringTag.valueOf(id.toString()));
        }
        tag.put("locked", list);
        return tag;
    }
}