package com.ratana.cobbleforge.research.block;

import com.ratana.cobbleforge.research.menu.ModResearchMenu;
import com.ratana.cobbleforge.research.network.ResearchNetworking;
import com.ratana.cobbleforge.research.player.ModAttachments;
import com.ratana.cobbleforge.research.player.ResearchPlayerData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ResearchTableBlock extends Block {

    public ResearchTableBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, Level level, @NotNull BlockPos pos,
                                                        @NotNull Player player, @NotNull BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new MenuProvider() {
                @Override
                public @NotNull Component getDisplayName() {
                    return Component.translatable("container.cobbleforge.research_table");
                }

                @Override
                public AbstractContainerMenu createMenu(int containerId, @NotNull Inventory inventory, @NotNull Player p) {
                    return new ModResearchMenu(containerId, inventory, ContainerLevelAccess.create(level, pos));
                }
            });

            ResearchPlayerData data = serverPlayer.getData(ModAttachments.RESEARCH_DATA.get());
            ResearchNetworking.sendSync(serverPlayer, data);
        }

        return InteractionResult.CONSUME;
    }
}