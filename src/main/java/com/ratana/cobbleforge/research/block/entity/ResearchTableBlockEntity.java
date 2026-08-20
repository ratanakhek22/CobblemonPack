package com.ratana.cobbleforge.research.block.entity;

import com.ratana.cobbleforge.CobbleForgeMod;
import com.ratana.cobbleforge.research.block.ModBlocks;
import com.ratana.cobbleforge.research.item.AncientItem;
import com.ratana.cobbleforge.research.node.TypeGroup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

public class ResearchTableBlockEntity extends BlockEntity implements Container {

    public static final int SLOT_ANCIENT_ITEM = 0;
    public static final int SLOT_BRUSH = 1;
    public static final int SLOT_FORGOTTEN_KNOWLEDGE = 2;
    public static final int SLOT_JOURNAL = 3;
    private static final int SLOT_COUNT = 4;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);

    public ResearchTableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RESEARCH_TABLE.get(), pos, state);
    }

    // ---------------- slot validity ----------------

    @Override
    public boolean canPlaceItem(int slot, @NotNull ItemStack stack) {
        return switch (slot) {
            case SLOT_ANCIENT_ITEM -> isAncientItem(stack);
            case SLOT_BRUSH -> stack.is(Items.BRUSH);
            case SLOT_FORGOTTEN_KNOWLEDGE -> isForgottenKnowledge(stack);
            case SLOT_JOURNAL -> isResearchJournal(stack);
            default -> false;
        };
    }

    public static TypeGroup ancientItemGroup(ItemStack stack) {
        return stack.getItem() instanceof AncientItem ai ? ai.group() : null;
    }

    public static boolean isAncientItem(ItemStack stack) {
        return stack.getItem() instanceof AncientItem;
    }

    public static boolean isForgottenKnowledge(ItemStack stack) {
        return stack.is(CobbleForgeMod.FORGOTTEN_KNOWLEDGE.get());
    }

    public static boolean isResearchJournal(ItemStack stack) {
        return stack.is(CobbleForgeMod.RESEARCH_JOURNAL.get());
    }

    /** Canonical Journal-gate check. Every action handler (INVEST, NEXT_STEP, REDEEM) should
     *  call this rather than re-checking the slot independently. */
    public boolean hasJournal() {
        return isResearchJournal(items.get(SLOT_JOURNAL));
    }

    public int forgottenKnowledgeCount() {
        return items.get(SLOT_FORGOTTEN_KNOWLEDGE).getCount();
    }

    // ---------------- net.minecraft.world.Container implementation ----------------

    @Override
    public int getContainerSize() {
        return SLOT_COUNT;
    }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public @NotNull ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public @NotNull ItemStack removeItem(int slot, int count) {
        ItemStack result = ContainerHelper.removeItem(items, slot, count);
        if (!result.isEmpty()) setChanged();
        return result;
    }

    @Override
    public @NotNull ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, @NotNull ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        setChanged();
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        if (level == null || level.getBlockEntity(worldPosition) != this) return false;
        return player.distanceToSqr(
                worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5
        ) <= 64.0;
    }

    @Override
    public void clearContent() {
        items.clear();
        setChanged();
    }

    // ---------------- NBT persistence ----------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.clear();
        ContainerHelper.loadAllItems(tag, items, registries);
    }

    // ---------------- drop-on-break ----------------
    // Call from ResearchTableBlock#onRemove, before the block entity is actually removed.

    public void dropContents() {
        if (level == null) return;
        Containers.dropContents(level, worldPosition, items);
    }
}