package com.ratana.cobbleforge.research.block;

import com.ratana.cobbleforge.CobbleForgeMod;
import com.ratana.cobbleforge.research.block.entity.RuinBlockEntity;
import com.ratana.cobbleforge.research.item.ResearchJournal;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class RuinBlock extends Block implements EntityBlock {

    public RuinBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RuinBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hitResult) {
        if (hand != InteractionHand.MAIN_HAND) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (!(player.getOffhandItem().getItem() instanceof ResearchJournal)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (!stack.is(Items.PAPER)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof RuinBlockEntity ruinBlockEntity)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (ruinBlockEntity.hasClaimed(player)) {
            player.displayClientMessage(
                    Component.literal("You've already claimed knowledge from this ruin."), true);
            return ItemInteractionResult.CONSUME;
        }

        stack.shrink(1);
        ItemStack forgottenKnowledge = new ItemStack(CobbleForgeMod.FORGOTTEN_KNOWLEDGE.get());
        if (!player.getInventory().add(forgottenKnowledge)) {
            player.drop(forgottenKnowledge, false);
        }

        ruinBlockEntity.markClaimed(player);
        return ItemInteractionResult.SUCCESS;
    }
}