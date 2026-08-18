package com.ratana.cobbleforge.research.menu;

import com.ratana.cobbleforge.research.block.ModBlocks;
import com.ratana.cobbleforge.research.player.NodeProgress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModResearchMenu extends AbstractContainerMenu {

    public static final int SLOT_ANCIENT_ITEM = 0;
    public static final int SLOT_BRUSH = 1;
    public static final int SLOT_FORGOTTEN_KNOWLEDGE = 2;
    private static final int INPUT_SLOT_COUNT = 3;

    private final Player player;
    private final ContainerLevelAccess access;
    private final SimpleContainer inputContainer = new SimpleContainer(INPUT_SLOT_COUNT) {
        @Override
        public boolean canPlaceItem(int slot, @NotNull ItemStack stack) {
            return switch (slot) {
                case SLOT_ANCIENT_ITEM -> isAncientItem(stack);
                case SLOT_BRUSH -> stack.is(Items.BRUSH);
                case SLOT_FORGOTTEN_KNOWLEDGE -> isForgottenKnowledge(stack);
                default -> false;
            };
        }
    };

    // ---- client-side display cache, populated by ResearchSyncPayload via applySync() ----
    private int cachedPoints = 0;
    private List<ResourceLocation> cachedSlotOrder = List.of();
    private final Map<ResourceLocation, NodeProgress> cachedProgress = new HashMap<>();
    private final Map<ResourceLocation, Integer> cachedInvested = new HashMap<>();

    /** Client-side / menu-type-factory constructor — no real block position available. */
    public ModResearchMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, ContainerLevelAccess.NULL);
    }

    /** Server-side constructor — used by ResearchTableBlock with the real block's position. */
    public ModResearchMenu(int containerId, Inventory playerInventory, ContainerLevelAccess access) {
        super(ModMenuTypes.RESEARCH_TABLE.get(), containerId);
        this.player = playerInventory.player;
        this.access = access;

        this.addSlot(new Slot(inputContainer, SLOT_ANCIENT_ITEM, 12, 92) {
            @Override
            public boolean mayPlace(@NotNull ItemStack stack) {
                return inputContainer.canPlaceItem(SLOT_ANCIENT_ITEM, stack);
            }
        });
        this.addSlot(new Slot(inputContainer, SLOT_BRUSH, 12, 120) {
            @Override
            public boolean mayPlace(@NotNull ItemStack stack) {
                return inputContainer.canPlaceItem(SLOT_BRUSH, stack);
            }
        });
        this.addSlot(new Slot(inputContainer, SLOT_FORGOTTEN_KNOWLEDGE, 152, 92) {
            @Override
            public boolean mayPlace(@NotNull ItemStack stack) {
                return inputContainer.canPlaceItem(SLOT_FORGOTTEN_KNOWLEDGE, stack);
            }
        });

        layoutPlayerInventorySlots(playerInventory, 8, 160);
    }

    private void layoutPlayerInventorySlots(Inventory inv, int left, int top) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, left + col * 18, top + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, left + col * 18, top + 58));
        }
    }

    private static boolean isAncientItem(ItemStack stack) {
        // TODO point at your actual Ancient Geode/Prismarine/Seed/Amber/Tome/Totem items
        return false;
    }

    private static boolean isForgottenKnowledge(ItemStack stack) {
        // TODO point at your actual Forgotten Knowledge item
        return false;
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
            // Moving FROM the player TO your machine
            boolean movedToTable = false;

            if (isAncientItem(copy)) {
                movedToTable = moveItemStackTo(original, SLOT_ANCIENT_ITEM, SLOT_ANCIENT_ITEM + 1, false);
            } else if (copy.is(Items.BRUSH)) {
                movedToTable = moveItemStackTo(original, SLOT_BRUSH, SLOT_BRUSH + 1, false);
            } else if (isForgottenKnowledge(copy)) {
                movedToTable = moveItemStackTo(original, SLOT_FORGOTTEN_KNOWLEDGE, SLOT_FORGOTTEN_KNOWLEDGE + 1, false);
            }

            // If it didn't go into the machine (either wasn't valid, or the machine slot was full),
            // handle standard hotbar <-> inventory shift-clicking.
            if (!movedToTable) {
                int hotbarStart = INPUT_SLOT_COUNT + 27;       // Index 30
                int hotbarEnd = INPUT_SLOT_COUNT + 36;         // Index 39

                if (index < hotbarStart) {
                    // Clicked in main inventory -> move to hotbar
                    if (!moveItemStackTo(original, hotbarStart, hotbarEnd, false)) return ItemStack.EMPTY;
                } else if (index < hotbarEnd) {
                    // Clicked in hotbar -> move to main inventory
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
}