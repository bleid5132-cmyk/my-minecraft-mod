package com.bleid.vestments.client;

import com.bleid.vestments.SetBonus;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Сандалии паломника: хождение по воде до 15 секунд. Движение игрока считает клиент, поэтому
 * держим игрока на воде здесь.
 *
 * <ul>
 *   <li>Идём по воде на уровне верха блока воды — вровень с берегом, без ступеньки (нет тряски на кромке).</li>
 *   <li>Если игрок чуть провалился ниже, его плавно поднимает скоростью, а не телепортом.</li>
 *   <li>Время тратится всё время «сеанса» — с первого шага на воду до возвращения на твёрдую землю,
 *       включая прыжки. Заряд восстанавливается только на твёрдой земле.</li>
 *   <li>SHIFT — сразу уйти под воду.</li>
 * </ul>
 */
@Environment(EnvType.CLIENT)
public final class WaterWalkClient {
    private static final int MAX_TICKS = 15 * 20;   // 15 секунд

    private static int usedTicks = 0;               // сколько потрачено в текущем сеансе
    private static boolean session = false;         // ушли на воду и ещё не вернулись на землю
    private static boolean exhaustedShown = false;

    private WaterWalkClient() { }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientPlayerEntity player = client.player;
            if (player == null) return;
            if (!SetBonus.hasFullSet(player)) {
                usedTicks = 0;
                session = false;
                return;
            }
            World world = player.getWorld();
            Double surface = waterSurface(world, player.getPos());

            // на твёрдой земле (не над водой) — сеанс закончен, заряд восстановлен
            if (player.isOnGround() && surface == null && !player.isTouchingWater()) {
                session = false;
                usedTicks = 0;
                exhaustedShown = false;
            }

            // время идёт весь сеанс — и когда стоим на воде, и в прыжке над ней
            if (session && usedTicks < MAX_TICKS) {
                usedTicks++;
            }

            boolean canWalk = usedTicks < MAX_TICKS && !player.isSneaking()
                    && !player.getAbilities().flying && !player.isFallFlying() && !player.isSubmergedInWater();

            if (canWalk && surface != null) {
                Vec3d v = player.getVelocity();
                double y = player.getY();
                if (y < surface - 0.001 && y > surface - 0.9) {
                    // провалились чуть ниже — мягко поднимаем к поверхности
                    double up = Math.min((surface - y) * 0.6, 0.35);
                    player.setVelocity(v.x, Math.max(v.y, up), v.z);
                    session = true;
                } else if (y <= surface + 0.02 && v.y <= 0.0) {
                    // стоим на воде
                    if (y < surface) player.setPosition(player.getX(), surface, player.getZ());
                    player.setVelocity(v.x, 0.0, v.z);
                    player.setOnGround(true);
                    player.fallDistance = 0f;
                    session = true;
                }
            }

            if (session) {
                int left = Math.max(0, (MAX_TICKS - usedTicks + 19) / 20);
                if (usedTicks < MAX_TICKS) {
                    player.sendMessage(Text.translatable("message.vestments.water_walk", left)
                            .formatted(left <= 3 ? Formatting.RED : Formatting.AQUA), true);
                } else if (!exhaustedShown) {
                    player.sendMessage(Text.translatable("message.vestments.water_walk_end")
                            .formatted(Formatting.GRAY), true);
                    exhaustedShown = true;
                }
            }
        });
    }

    /**
     * Уровень, на котором стоим над водой: верх верхнего блока воды (вровень с берегом),
     * или null, если под ногами нет воды.
     */
    private static Double waterSurface(World world, Vec3d pos) {
        BlockPos feet = BlockPos.ofFloored(pos.x, pos.y - 0.05, pos.z);
        for (BlockPos p : new BlockPos[] { feet, feet.down() }) {
            if (!world.getFluidState(p).isIn(FluidTags.WATER)) continue;
            if (world.getFluidState(p.up()).isIn(FluidTags.WATER)) continue;   // не верхний слой
            if (!world.getBlockState(p.up()).getCollisionShape(world, p.up()).isEmpty()) continue;
            return p.getY() + 1.0;
        }
        return null;
    }
}
