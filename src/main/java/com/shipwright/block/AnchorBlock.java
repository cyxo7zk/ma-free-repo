package com.shipwright.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Right-click to drop or raise. A ship with any dropped anchor cannot move or turn. */
public class AnchorBlock extends Block {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty DROPPED = BooleanProperty.create("dropped");
    public static final int REACH = 24;
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 16, 13);

    public AnchorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(DROPPED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, DROPPED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        boolean dropping = !state.getValue(DROPPED);
        if (dropping && !hasSeabed(level, pos)) {
            player.displayClientMessage(Component.literal("The anchor finds nothing to hold on to."), true);
            return InteractionResult.SUCCESS;
        }
        level.setBlock(pos, state.setValue(DROPPED, dropping), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.7f, dropping ? 0.6f : 1.2f);
        player.displayClientMessage(Component.literal(dropping ? "Anchor dropped." : "Anchor raised."), true);
        return InteractionResult.SUCCESS;
    }

    private static boolean hasSeabed(Level level, BlockPos pos) {
        for (int i = 1; i <= REACH; i++) {
            BlockPos p = pos.below(i);
            if (!level.hasChunkAt(p)) return false;
            if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) return true;
        }
        return false;
    }
}
