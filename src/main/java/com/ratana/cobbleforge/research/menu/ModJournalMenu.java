package com.ratana.cobbleforge.research.menu;

import com.ratana.cobbleforge.CobbleForgeMod;
import com.ratana.cobbleforge.research.block.entity.ResearchTableBlockEntity;
import com.ratana.cobbleforge.research.player.NodeProgress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Read-only browsing menu opened by right-clicking a Research Journal in hand. No slots,
 *  no block, no actions -- purely a client-side display of the player's existing progress,
 *  same data shape as ModResearchMenu's cache but with nothing to interact with. */
public class ModJournalMenu extends AbstractContainerMenu {

    private final Player player;

    private int cachedPoints = 0;
    private List<ResourceLocation> cachedSlotOrder = List.of();
    private final Map<ResourceLocation, NodeProgress> cachedProgress = new HashMap<>();
    private final Map<ResourceLocation, Integer> cachedInvested = new HashMap<>();

    public ModJournalMenu(int containerId, Inventory playerInventory) {
        super(ModMenuTypes.RESEARCH_JOURNAL.get(), containerId);
        this.player = playerInventory.player;
    }

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

    @Override
    public @NotNull net.minecraft.world.item.ItemStack quickMoveStack(@NotNull Player player, int index) {
        return net.minecraft.world.item.ItemStack.EMPTY; // no slots exist; nothing to shift-click
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return player.getMainHandItem().is(CobbleForgeMod.RESEARCH_JOURNAL.get())
                || player.getOffhandItem().is(CobbleForgeMod.RESEARCH_JOURNAL.get());
    }
}