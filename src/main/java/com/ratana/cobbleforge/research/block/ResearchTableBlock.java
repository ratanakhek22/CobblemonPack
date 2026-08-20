package com.ratana.cobbleforge.research.block;

import com.mojang.serialization.MapCodec;
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
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;

import com.ratana.cobbleforge.research.block.entity.ResearchTableBlockEntity;
import com.ratana.cobbleforge.research.menu.ModResearchMenu;
import com.ratana.cobbleforge.research.network.ResearchNetworking;
import com.ratana.cobbleforge.research.player.ModAttachments;
import com.ratana.cobbleforge.research.player.ResearchPlayerData;

public class ResearchTableBlock extends BaseEntityBlock {

    public ResearchTableBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
        // BaseEntityBlock defaults to INVISIBLE; this block still wants its normal model.
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new ResearchTableBlockEntity(pos, state);
    }

    @Override
    protected @NotNull MapCodec<ResearchTableBlock> codec() {
        return simpleCodec(ResearchTableBlock::new);
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, Level level, @NotNull BlockPos pos,
                                                        @NotNull Player player, @NotNull BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof ResearchTableBlockEntity be) {
            serverPlayer.openMenu(new MenuProvider() {
                @Override
                public @NotNull Component getDisplayName() {
                    return Component.translatable("container.cobbleforge.research_table");
                }

                @Override
                public AbstractContainerMenu createMenu(int containerId, @NotNull Inventory inventory, @NotNull Player p) {
                    // NOTE: this ModResearchMenu(int, Inventory, ContainerLevelAccess, ResearchTableBlockEntity)
                    // constructor doesn't exist yet — that's the next step, wiring the menu to take
                    // the block entity instead of owning its own container. This call site is shown
                    // now so the shape is visible; it won't compile until that constructor exists.
                    return new ModResearchMenu(containerId, inventory, ContainerLevelAccess.create(level, pos), be);
                }
            });

            ResearchPlayerData data = serverPlayer.getData(ModAttachments.RESEARCH_DATA.get());
            ResearchNetworking.sendSync(serverPlayer, data);
        }

        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                         @NotNull BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof ResearchTableBlockEntity be) {
                be.dropContents();
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}