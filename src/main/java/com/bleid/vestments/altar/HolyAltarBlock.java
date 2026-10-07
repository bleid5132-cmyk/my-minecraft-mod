package com.bleid.vestments.altar;

import com.bleid.vestments.service.ServicePoints;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/**
 * Священный алтарь. Стоит у колокола в деревнях жителей (см. AltarSpawner).
 * Священник ПКМ кладёт подношение (SHIFT — весь стак) и получает очки служения,
 * которые огоньками летят от алтаря к нему.
 */
public class HolyAltarBlock extends HorizontalFacingBlock {
    private static final VoxelShape SHAPE = VoxelShapes.union(
            Block.createCuboidShape(1, 0, 1, 15, 2, 15),
            Block.createCuboidShape(2, 2, 2, 14, 11, 14),
            Block.createCuboidShape(1, 11, 1, 15, 12.5, 15));

    /** Ценность подношений в очках служения за 1 предмет. */
    private static final Map<Item, Integer> OFFERINGS = new HashMap<>();
    static {
        OFFERINGS.put(Items.BREAD, 1);
        OFFERINGS.put(Items.WHEAT, 1);
        OFFERINGS.put(Items.HONEY_BOTTLE, 2);
        OFFERINGS.put(Items.HONEYCOMB, 2);
        OFFERINGS.put(Items.GOLD_NUGGET, 1);
        OFFERINGS.put(Items.GOLD_INGOT, 6);
        OFFERINGS.put(Items.EMERALD, 8);
        OFFERINGS.put(Items.DIAMOND, 15);
        OFFERINGS.put(Items.GOLDEN_APPLE, 20);
        OFFERINGS.put(Items.GOLD_BLOCK, 54);
        OFFERINGS.put(Items.TOTEM_OF_UNDYING, 60);
        OFFERINGS.put(Items.EMERALD_BLOCK, 72);
        OFFERINGS.put(Items.DIAMOND_BLOCK, 135);
        OFFERINGS.put(Items.ENCHANTED_GOLDEN_APPLE, 150);
        OFFERINGS.put(Items.NETHER_STAR, 200);
    }

    public HolyAltarBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, net.minecraft.util.math.Direction.NORTH));
    }

    public static int valueOf(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        if (stack.isIn(ItemTags.CANDLES)) return 2;
        return OFFERINGS.getOrDefault(stack.getItem(), 0);
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    @SuppressWarnings("deprecation")
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
        ItemStack stack = player.getStackInHand(hand);
        if (world.isClient) return ActionResult.SUCCESS;
        ServerPlayerEntity sp = (ServerPlayerEntity) player;

        if (!ServicePoints.canServe(sp)) {
            sp.sendMessage(Text.translatable("message.vestments.altar_not_priest"), true);
            return ActionResult.CONSUME;
        }
        int value = valueOf(stack);
        if (value <= 0) {
            sp.sendMessage(Text.translatable(stack.isEmpty()
                    ? "message.vestments.altar_hint" : "message.vestments.altar_refuses"), true);
            return ActionResult.CONSUME;
        }

        int count = player.isSneaking() ? stack.getCount() : 1;
        if (!player.getAbilities().creativeMode) stack.decrement(count);

        Vec3d top = Vec3d.ofCenter(pos).add(0, 0.6, 0);
        ServerWorld sw = (ServerWorld) world;
        sw.spawnParticles(ParticleTypes.FLAME, top.x, top.y, top.z, 8, 0.25, 0.1, 0.25, 0.01);
        sw.spawnParticles(ParticleTypes.END_ROD, top.x, top.y + 0.2, top.z, 6, 0.2, 0.3, 0.2, 0.03);
        world.playSound(null, pos, SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.BLOCKS, 1.0f, 1.2f);
        world.playSound(null, pos, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 1.0f, 0.8f);

        ServicePoints.add(sp, value * count, top);
        return ActionResult.CONSUME;
    }

    /** Огоньки свечей. */
    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        var facing = state.get(FACING);
        // свечи стоят по бокам задней части: поворачиваем точки модели (для north) по направлению
        double[][] candles = { { 3.75, 4.75 }, { 12.25, 4.75 } };
        for (double[] c : candles) {
            double lx = c[0] / 16.0 - 0.5, lz = c[1] / 16.0 - 0.5;
            double x, z;
            switch (facing) {
                case SOUTH -> { x = -lx; z = -lz; }
                case EAST -> { x = -lz; z = lx; }
                case WEST -> { x = lz; z = -lx; }
                default -> { x = lx; z = lz; }
            }
            if (random.nextInt(3) == 0) {
                world.addParticle(ParticleTypes.SMALL_FLAME, pos.getX() + 0.5 + x, pos.getY() + 1.08, pos.getZ() + 0.5 + z, 0, 0, 0);
            }
        }
        if (random.nextInt(4) == 0) {
            world.addParticle(ParticleTypes.WAX_OFF, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.8,
                    pos.getY() + 1.0 + random.nextDouble() * 0.6, pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.8, 0, 0, 0);
        }
    }
}
