package com.bleid.vestments.client;

import com.bleid.vestments.SetBonus;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Сандалии паломника: хождение по воде до 15 секунд. Движение игрока считает клиент, поэтому
 * держим игрока на поверхности здесь. SHIFT — сразу уйти под воду. Заряд восстанавливается,
 * когда игрок снова стоит на твёрдой земле.
 */
@Environment(EnvType.CLIENT)
public final class WaterWalkClient {
    private static final int MAX_TICKS = 15 * 20;   // 15 секунд

    private static int usedTicks = 0;               // сколько уже прошёл по воде
    private static boolean wasWalking = false;

    private WaterWalkClient() { }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientPlayerEntity player = client.player;
            if (player == null) return;
            if (!SetBonus.hasFullSet(player)) {
                usedTicks = 0;
                wasWalking = false;
                return;
            }
            // на твёрдой земле заряд полностью восстанавливается
            if (player.isOnGround() && !wasWalking && !player.isTouchingWater()) {
                usedTicks = 0;
            }

            boolean walking = false;
            if (!player.isSneaking() && usedTicks < MAX_TICKS && !player.getAbilities().flying
                    && !player.isFallFlying() && !player.isSubmergedInWater()) {
                Double surface = waterSurface(player.getWorld(), player.getPos());
                Vec3d v = player.getVelocity();
                if (surface != null && player.getY() <= surface + 0.2 && player.getY() >= surface - 0.6 && v.y <= 0.05) {
                    player.setPosition(player.getX(), surface, player.getZ());
                    player.setVelocity(v.x, 0.0, v.z);
                    player.setOnGround(true);
                    player.fallDistance = 0f;
                    walking = true;
                    usedTicks++;
                }
            }

            if (walking) {
                int left = (MAX_TICKS - usedTicks + 19) / 20;
                player.sendMessage(Text.translatable("message.vestments.water_walk", left)
                        .formatted(left <= 3 ? Formatting.RED : Formatting.AQUA), true);
            } else if (wasWalking && usedTicks >= MAX_TICKS) {
                player.sendMessage(Text.translatable("message.vestments.water_walk_end")
                        .formatted(Formatting.GRAY), true);
            }
            wasWalking = walking;
        });
    }

    /** Высота поверхности воды под игроком (верхний блок воды), или null, если воды нет. */
    private static Double waterSurface(World world, Vec3d pos) {
        BlockPos feet = BlockPos.ofFloored(pos.x, pos.y - 0.05, pos.z);
        for (BlockPos p : new BlockPos[] { feet, feet.down() }) {
            FluidState fluid = world.getFluidState(p);
            if (!fluid.isIn(FluidTags.WATER)) continue;
            if (world.getFluidState(p.up()).isIn(FluidTags.WATER)) continue;   // это не верхний слой
            if (!world.getBlockState(p.up()).getCollisionShape(world, p.up()).isEmpty()) continue;
            return p.getY() + (double) fluid.getHeight(world, p);
        }
        return null;
    }
}
