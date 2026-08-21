package com.ratana.cobbleforge.research.item;

import com.ratana.cobbleforge.research.menu.ModJournalMenu;
import com.ratana.cobbleforge.research.network.ResearchNetworking;
import com.ratana.cobbleforge.research.player.ModAttachments;
import com.ratana.cobbleforge.research.player.ResearchPlayerData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public class ResearchJournal extends Item {
    public ResearchJournal(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, Player player, @NotNull InteractionHand hand) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            sp.openMenu(new MenuProvider() {
                @Override
                public @NotNull Component getDisplayName() {
                    return Component.translatable("container.cobbleforge.journal");
                }

                @Override
                public AbstractContainerMenu createMenu(int containerId, @NotNull Inventory inventory, @NotNull Player p) {
                    return new ModJournalMenu(containerId, inventory);
                }
            });

            ResearchPlayerData data = sp.getData(ModAttachments.RESEARCH_DATA.get());
            ResearchNetworking.sendSync(sp, data);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
}