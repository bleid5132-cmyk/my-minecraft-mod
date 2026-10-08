package com.bleid.vestments.altar;

import com.bleid.vestments.service.ServicePoints;
import java.util.List;
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
 * Священный алтарь (стоит в церкви). ПКМ открывает окно алтаря: список подношений;
 * священник жертвует, нажимая на предмет (Shift — все такие предметы), и получает очки служения,
 * которые огоньками летят от алтаря к нему.
 */
public class HolyAltarBlock extends HorizontalFacingBlock {
    private static final VoxelShape SHAPE = VoxelShapes.union(
            Block.createCuboidShape(1, 0, 1, 15, 2, 15),
            Block.createCuboidShape(2, 2, 2, 14, 11, 14),
            Block.createCuboidShape(1, 11, 1, 15, 12.5, 15));

    /** Подношение: предмет и ценность в очках служения за 1 шт. (свеча — любая свеча). */
    public record Offering(Item item, int points) {
        public boolean matches(ItemStack st) {
            if (item == Items.CANDLE) return st.isIn(ItemTags.CANDLES);
            return st.isOf(item);
        }
    }

    /** Что принимает алтарь — в порядке показа в окне алтаря. */
    public static final List<Offering> OFFERINGS = List.of(
            new Offering(Items.BREAD, 1), new Offering(Items.WHEAT, 1), new Offering(Items.GOLD_NUGGET, 1),
            new Offering(Items.HONEY_BOTTLE, 2), new Offering(Items.HONEYCOMB, 2), new Offering(Items.CANDLE, 2),
            new Offering(Items.GOLD_INGOT, 6), new Offering(Items.EMERALD, 8), new Offering(Items.DIAMOND, 15),
            new Offering(Items.GOLDEN_APPLE, 20), new Offering(Items.GOLD_BLOCK, 54), new Offering(Items.TOTEM_OF_UNDYING, 60),
            new Offering(Items.EMERALD_BLOCK, 72), new Offering(Items.DIAMOND_BLOCK, 135),
            new Offering(Items.ENCHANTED_GOLDEN_APPLE, 150), new Offering(Items.NETHER_STAR, 200));

    public static final net.minecraft.util.Identifier OPEN = new net.minecraft.util.Identifier("vestments", "open_altar");
    public static final net.minecraft.util.Identifier DONATE = new net.minecraft.util.Identifier("vestments", "altar_donate");

    public HolyAltarBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, net.minecraft.util.math.Direction.NORTH));
    }

    public static int valueOf(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        for (Offering o : OFFERINGS) if (o.matches(stack)) return o.points();
        return 0;
    }

    /** Приём пожертвования из окна алтаря: idx — номер подношения, all — все такие предметы из инвентаря. */
    public static void registerNetworking() {
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(DONATE, (server, player, handler, buf, sender) -> {
            BlockPos pos = buf.readBlockPos();
            int idx = buf.readVarInt();
            boolean all = buf.readBoolean();
            server.execute(() -> donate(player, pos, idx, all));
        });
    }

    private static void donate(ServerPlayerEntity sp, BlockPos pos, int idx, boolean all) {
        if (idx < 0 || idx >= OFFERINGS.size()) return;
        World world = sp.getWorld();
        if (!(world.getBlockState(pos).getBlock() instanceof HolyAltarBlock)) return;
        if (sp.squaredDistanceTo(Vec3d.ofCenter(pos)) > 8 * 8) return;
        if (!ServicePoints.canServe(sp)) {
            sp.sendMessage(Text.translatable("message.vestments.altar_not_priest"), true);
            return;
        }
        Offering o = OFFERINGS.get(idx);
        var inv = sp.getInventory();
        int need = all ? Integer.MAX_VALUE : 1, taken = 0;
        for (int i = 0; i < inv.size() && taken < need; i++) {
            ItemStack st = inv.getStack(i);
            if (st.isEmpty() || !o.matches(st)) continue;
            int n = Math.min(st.getCount(), need - taken);
            if (!sp.getAbilities().creativeMode) st.decrement(n);
            taken += n;
        }
        if (taken == 0) {
            sp.sendMessage(Text.translatable("message.vestments.altar_none"), true);
            return;
        }
        offer(sp, (ServerWorld) world, pos, o.points() * taken);
    }

    private static void offer(ServerPlayerEntity sp, ServerWorld sw, BlockPos pos, int points) {
        Vec3d top = Vec3d.ofCenter(pos).add(0, 0.6, 0);
        sw.spawnParticles(ParticleTypes.FLAME, top.x, top.y, top.z, 8, 0.25, 0.1, 0.25, 0.01);
        sw.spawnParticles(ParticleTypes.END_ROD, top.x, top.y + 0.2, top.z, 6, 0.2, 0.3, 0.2, 0.03);
        sw.playSound(null, pos, SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.BLOCKS, 1.0f, 1.2f);
        sw.playSound(null, pos, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 1.0f, 0.8f);
        ServicePoints.add(sp, points, top);
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
        if (world.isClient) return ActionResult.SUCCESS;
        // открываем окно алтаря: список подношений, пожертвование — нажатием на предмет
        net.minecraft.network.PacketByteBuf buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buf.writeBlockPos(pos);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send((ServerPlayerEntity) player, OPEN, buf);
        world.playSound(null, pos, SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.BLOCKS, 0.8f, 1.0f);
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
