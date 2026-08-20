package com.ratana.cobbleforge.research.menu;

import com.ratana.cobbleforge.CobbleForgeMod;
import com.ratana.cobbleforge.research.network.ResearchSyncPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.ratana.cobbleforge.research.block.ModBlocks;
import com.ratana.cobbleforge.research.block.entity.ResearchTableBlockEntity;
import com.ratana.cobbleforge.research.player.NodeProgress;

import org.jetbrains.annotations.NotNull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

public class ModResearchMenu extends AbstractContainerMenu {

    public static final int SLOT_ANCIENT_ITEM = 0;
    public static final int SLOT_BRUSH = 1;
    public static final int SLOT_FORGOTTEN_KNOWLEDGE = 2;
    public static final int SLOT_JOURNAL = 3;
    private static final int INPUT_SLOT_COUNT = 4;

    public enum MenuView { EXPLORE, REDEEM }

    private final Player player;
    private final ContainerLevelAccess access;

    private final Container inputContainer;

    private final ContainerData viewData;
    private static final int DATA_VIEW = 0;

    private ResearchSyncPayload.RedeemResult lastRedeemResult;

    public void setLastRedeemResult(ResearchSyncPayload.RedeemResult result) {
        this.lastRedeemResult = result;
    }

    public ResearchSyncPayload.RedeemResult getLastRedeemResult() {
        return lastRedeemResult;
    }

    public void clearLastRedeemResult() {
        this.lastRedeemResult = null;
    }

    private int cachedPoints = 0;
    private List<ResourceLocation> cachedSlotOrder = List.of();
    private final Map<ResourceLocation, NodeProgress> cachedProgress = new HashMap<>();
    private final Map<ResourceLocation, Integer> cachedInvested = new HashMap<>();

    public ModResearchMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, ContainerLevelAccess.NULL,
                new SimpleContainer(INPUT_SLOT_COUNT));
    }

    public ModResearchMenu(int containerId, Inventory playerInventory, ContainerLevelAccess access,
                           Container inputContainer) {
        super(ModMenuTypes.RESEARCH_TABLE.get(), containerId);
        this.player = playerInventory.player;
        this.access = access;
        this.inputContainer = inputContainer;
        this.viewData = new SimpleContainerData(1);
        addDataSlots(viewData);

        this.addSlot(new ConditionalSlot(inputContainer, SLOT_ANCIENT_ITEM, 12, 92,
                () -> getView() == MenuView.REDEEM, ResearchTableBlockEntity::isAncientItem));
        this.addSlot(new ConditionalSlot(inputContainer, SLOT_BRUSH, 12, 120,
                () -> getView() == MenuView.REDEEM, stack -> stack.is(Items.BRUSH)));
        this.addSlot(new ConditionalSlot(inputContainer, SLOT_FORGOTTEN_KNOWLEDGE, 152, 92,
                () -> getView() == MenuView.REDEEM, ResearchTableBlockEntity::isForgottenKnowledge));
        this.addSlot(new ConditionalSlot(inputContainer, SLOT_JOURNAL, 152, 120,
                () -> getView() == MenuView.REDEEM, ResearchTableBlockEntity::isResearchJournal));

        layoutPlayerInventorySlots(playerInventory, 8, 160);
    }

    private void layoutPlayerInventorySlots(Inventory inv, int left, int top) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new ConditionalSlot(inv, col + row * 9 + 9, left + col * 18, top + row * 18,
                        () -> getView() == MenuView.REDEEM, stack -> true));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new ConditionalSlot(inv, col, left + col * 18, top + 58,
                    () -> getView() == MenuView.REDEEM, stack -> true));
        }
    }

    public ResearchTableBlockEntity getBlockEntity() {
        return inputContainer instanceof ResearchTableBlockEntity be ? be : null;
    }

    // ---------------- view state ----------------

    public MenuView getView() {
        return viewData.get(DATA_VIEW) == 1 ? MenuView.REDEEM : MenuView.EXPLORE;
    }

    /** Server calls this in response to the view-switch packet (not written yet — same
     *  ResearchActionPayload pattern as INVEST, per our earlier discussion). Client-side, call
     *  it directly for the optimistic local flip. */
    public void setView(MenuView view) {
        viewData.set(DATA_VIEW, view == MenuView.REDEEM ? 1 : 0);
    }

    // ---------------- slot validity (moved from the old inline inputContainer) ----------------

    private static boolean isAncientItem(ItemStack stack) {
        return ResearchTableBlockEntity.isAncientItem(stack);
    }

    private static boolean isForgottenKnowledge(ItemStack stack) {
        return ResearchTableBlockEntity.isForgottenKnowledge(stack);
    }

    private static boolean isResearchJournal(ItemStack stack) {
        return ResearchTableBlockEntity.isResearchJournal(stack);
    }

    // ---------------- client-side cache access (used by ModResearchScreen) ----------------

    public void applySync(int points, List<ResourceLocation> orderedNodeIds,
                          Map<ResourceLocation, NodeProgress> progress,
                          Map<ResourceLocation, Integer> invested) {
        this.cachedPoints = points;
        this.cachedSlotOrder = List.copyOf(orderedNodeIds);
        this.cachedProgress.clear();
        this.cachedProgress.putAll(progress);
        this.cachedInvested.clear();
        this.cachedInvested.putAll(invested);
    }

    public int getCachedPoints() { return cachedPoints; }
    public List<ResourceLocation> getCachedSlotOrder() { return cachedSlotOrder; }

    public NodeProgress getCachedProgress(ResourceLocation nodeId) {
        return cachedProgress.getOrDefault(nodeId, NodeProgress.LOCKED);
    }

    public int getCachedInvested(ResourceLocation nodeId) {
        return cachedInvested.getOrDefault(nodeId, 0);
    }

    public int getForgottenKnowledgeCount() {
        return getSlot(SLOT_FORGOTTEN_KNOWLEDGE).getItem().getCount();
    }

    public boolean hasJournalClient() {
        // Only valid journals can ever occupy this slot (mayPlace already enforces that), so
        // non-empty is a sufficient check here — no need to re-run isResearchJournal client-side.
        return !getSlot(SLOT_JOURNAL).getItem().isEmpty();
    }

    public boolean hasAncientItemClient() {
        return !getSlot(SLOT_ANCIENT_ITEM).getItem().isEmpty();
    }

    public boolean hasBrushClient() {
        return !getSlot(SLOT_BRUSH).getItem().isEmpty();
    }

    // ---------------- vanilla container plumbing ----------------

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack original = slot.getItem();
        ItemStack copy = original.copy();

        if (index < INPUT_SLOT_COUNT) {
            if (!moveItemStackTo(original, INPUT_SLOT_COUNT, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            boolean movedToTable = false;

            // Only route into the shared slots while REDEEM is actually open — otherwise a
            // shift-click from EXPLORE would silently place an item into a slot the player
            // can't currently see or interact with, which we flagged earlier as a real bug.
            if (getView() == MenuView.REDEEM) {
                if (isAncientItem(copy)) {
                    movedToTable = moveItemStackTo(original, SLOT_ANCIENT_ITEM, SLOT_ANCIENT_ITEM + 1, false);
                } else if (copy.is(Items.BRUSH)) {
                    movedToTable = moveItemStackTo(original, SLOT_BRUSH, SLOT_BRUSH + 1, false);
                } else if (isForgottenKnowledge(copy)) {
                    movedToTable = moveItemStackTo(original, SLOT_FORGOTTEN_KNOWLEDGE, SLOT_FORGOTTEN_KNOWLEDGE + 1, false);
                } else if (isResearchJournal(copy)) {
                    movedToTable = moveItemStackTo(original, SLOT_JOURNAL, SLOT_JOURNAL + 1, false);
                }
            }

            if (!movedToTable) {
                int hotbarStart = INPUT_SLOT_COUNT + 27;
                int hotbarEnd = INPUT_SLOT_COUNT + 36;

                if (index < hotbarStart) {
                    if (!moveItemStackTo(original, hotbarStart, hotbarEnd, false)) return ItemStack.EMPTY;
                } else if (index < hotbarEnd) {
                    if (!moveItemStackTo(original, INPUT_SLOT_COUNT, hotbarStart, false)) return ItemStack.EMPTY;
                } else {
                    return ItemStack.EMPTY;
                }
            }
        }

        if (original.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return stillValid(access, player, ModBlocks.RESEARCH_TABLE.get());
    }

    private static class ConditionalSlot extends Slot {
        private final java.util.function.Predicate<ItemStack> placeCheck;
        private final BooleanSupplier active;

        ConditionalSlot(Container container, int index, int x, int y,
                        BooleanSupplier active, java.util.function.Predicate<ItemStack> placeCheck) {
            super(container, index, x, y);
            this.active = active;
            this.placeCheck = placeCheck;
        }

        @Override
        public boolean isActive() {
            return active.getAsBoolean();
        }

        @Override
        public boolean mayPlace(@NotNull ItemStack stack) {
            return active.getAsBoolean() && placeCheck.test(stack);
        }

        @Override
        public boolean mayPickup(@NotNull Player player) {
            return active.getAsBoolean() && super.mayPickup(player);
        }
    }
}