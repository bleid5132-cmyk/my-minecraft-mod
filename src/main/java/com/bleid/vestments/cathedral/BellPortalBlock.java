package com.bleid.vestments.cathedral;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;

/**
 * Звуковая завеса — заполнение портала в Безмолвный Собор. Рама — резная медь (любой степени патины, вощёная тоже),
 * зажигается касанием осколка аметиста. Без рамы завеса рассыпается.
 */
public class BellPortalBlock extends Block {
    public static final EnumProperty<Direction.Axis> AXIS = Properties.HORIZONTAL_AXIS;
    private static final VoxelShape X_SHAPE = Block.createCuboidShape(0, 0, 6, 16, 16, 10);
    private static final VoxelShape Z_SHAPE = Block.createCuboidShape(6, 0, 0, 10, 16, 16);

    public BellPortalBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(AXIS, Direction.Axis.X));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return state.get(AXIS) == Direction.Axis.Z ? Z_SHAPE : X_SHAPE;
    }

    @Override
    public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighbor, WorldAccess world,
                                                BlockPos pos, BlockPos neighborPos) {
        Direction.Axis axis = state.get(AXIS);
        boolean inPlane = direction.getAxis() == axis || direction.getAxis() == Direction.Axis.Y;
        if (inPlane && !neighbor.isOf(this) && !CathedralPortals.isFrame(neighbor)) return net.minecraft.block.Blocks.AIR.getDefaultState();
        return super.getStateForNeighborUpdate(state, direction, neighbor, world, pos, neighborPos);
    }

    @Override
    public void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
        if (!world.isClient) CathedralPortals.touch(entity, pos, state.get(AXIS));
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        if (random.nextInt(120) == 0) {
            world.playSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE,
                    SoundCategory.BLOCKS, 0.6f, 0.5f + random.nextFloat() * 0.3f, false);
        }
        for (int i = 0; i < 2; i++) {
            double x = pos.getX() + random.nextDouble(), y = pos.getY() + random.nextDouble(), z = pos.getZ() + random.nextDouble();
            world.addParticle(ParticleTypes.GLOW, x, y, z, 0, 0.02, 0);
        }
        if (random.nextInt(4) == 0) {
            world.addParticle(ParticleTypes.NOTE, pos.getX() + random.nextDouble(), pos.getY() + 1, pos.getZ() + random.nextDouble(),
                    random.nextDouble(), 0, 0);
        }
    }

    @Override
    public ItemStack getPickStack(BlockView world, BlockPos pos, BlockState state) {
        return ItemStack.EMPTY;
    }
}
