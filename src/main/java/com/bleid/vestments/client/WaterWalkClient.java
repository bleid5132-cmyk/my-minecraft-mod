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
 *   <li>Время тратится, пока стоим на воде или прыгаем над ней; под водой отсчёт замирает.
 *       Заряд восстанавливается только на твёрдой земле.</li>
 *   <li>Прыжок с воды обрабатываем сами — он работает всегда, в том числе чтобы выбраться на берег.</li>
 *   <li>SHIFT — сразу уйти под воду.</li>
 * </ul>
 */
@Environment(EnvType.CLIENT)
public final class WaterWalkClient {
    private static int MAX_TICKS = 15 * 20;          // зависит от комплекта (у Патриарха 15 секунд)

    private static int usedTicks = 0;               // сколько потрачено в текущем сеансе
    private static boolean session = false;         // ушли на воду и ещё не вернулись на землю
    private static boolean exhaustedShown = false;

    private WaterWalkClient() { }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientPlayerEntity player = client.player;
            if (player == null) return;
            var set = SetBonus.activeSet(player);
            if (set == null || set.waterWalkSec <= 0) {
                usedTicks = 0;
                session = false;
                return;
            }
            MAX_TICKS = set.waterWalkSec * 20;
            World world = player.getWorld();
            Double surface = waterSurface(world, player.getPos());

            // на твёрдой земле (не над водой) — сеанс закончен, заряд восстановлен
            if (player.isOnGround() && surface == null && !player.isTouchingWater()) {
                session = false;
                usedTicks = 0;
                exhaustedShown = false;
            }

            // время идёт, пока стоим на воде или прыгаем над ней; под водой — замирает
            boolean underwater = player.isTouchingWater() || player.isSubmergedInWater();
            if (session && usedTicks < MAX_TICKS && !underwater) {
                usedTicks++;
            }

            boolean canWalk = usedTicks < MAX_TICKS && !player.isSneaking()
                    && !player.getAbilities().flying && !player.isFallFlying() && !player.isSubmergedInWater();

            if (canWalk && surface != null) {
                Vec3d v = player.getVelocity();
                double y = player.getY();
                if (y < surface - 0.35 && y > surface - 0.9) {
                    // провалились глубоко — мягко поднимаем к поверхности
                    double up = Math.min((surface - y) * 0.6, 0.35);
                    player.setVelocity(v.x, Math.max(v.y, up), v.z);
                    session = true;
                } else if (y >= surface - 0.35 && y <= surface + 0.02 && v.y <= 0.0) {
                    // стоим на воде: сразу на поверхность, чтобы можно было прыгать
                    if (y < surface) player.setPosition(player.getX(), surface, player.getZ());
                    player.setOnGround(true);
                    player.fallDistance = 0f;
                    session = true;
                    if (client.options.jumpKey.isPressed()) {
                        player.jump();                       // прыжок с воды обрабатываем сами
                    } else {
                        player.setVelocity(v.x, 0.0, v.z);
                    }
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
