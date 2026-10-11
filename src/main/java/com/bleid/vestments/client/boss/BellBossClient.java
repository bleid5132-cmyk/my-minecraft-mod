package com.bleid.vestments.client.boss;

import static com.bleid.vestments.client.fx.FxSystem.*;

import com.bleid.vestments.boss.BellRingerEntity;
import com.bleid.vestments.boss.BossBase;
import com.bleid.vestments.boss.SilentBellEntity;
import com.bleid.vestments.client.combat.CameraShake;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

/**
 * Эффекты Звонаря и Безмолвного Колокола: пепел и свечное пламя, трещина и волна, звуковые кольца набата,
 * искры цепей, зелёные руны и звуковые волны колокола, хор, раскол.
 */
@Environment(EnvType.CLIENT)
public final class BellBossClient {
    private BellBossClient() { }

    static final float[] ASH = { 0.55f, 0.52f, 0.5f }, ASH_D = { 0.3f, 0.28f, 0.27f }, EMBER = { 1f, 0.55f, 0.18f },
            CANDLE = { 1f, 0.78f, 0.36f }, BRONZE = { 0.85f, 0.6f, 0.3f }, GREEN = { 0.42f, 1f, 0.55f },
            PATINA = { 0.3f, 0.75f, 0.62f }, PALE = { 0.85f, 1f, 0.9f }, WHITE = { 1f, 1f, 0.95f };
    private static final Map<Integer, Integer> SEEN = new HashMap<>();

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            ClientWorld w = mc.world;
            if (w == null || mc.player == null) { SEEN.clear(); return; }
            if (mc.isPaused()) return;
            for (Entity en : w.getEntities()) {
                if (!(en instanceof BossBase b) || !(en instanceof BellRingerEntity || en instanceof SilentBellEntity)) continue;
                if (b.squaredDistanceTo(mc.player) > 110 * 110) continue;
                int ev = b.eventValue();
                Integer last = SEEN.put(b.getId(), ev);
                if (last != null && last != ev && ev != 0) {
                    if (b instanceof BellRingerEntity r) ringer(r, ev & 31, b.eventPos(), b.eventArg());
                    else bell((SilentBellEntity) b, ev & 31, b.eventPos(), b.eventArg());
                }
                if (b instanceof BellRingerEntity r) tickRinger(r);
                else tickBell((SilentBellEntity) b);
            }
            if (w.getTime() % 100 == 0) {
                SEEN.keySet().removeIf(id -> w.getEntityById(id) == null);
                BellRingerRenderer.SPRINGS.retain(id -> w.getEntityById(id) != null);
                SilentBellRenderer.SPRINGS.retain(id -> w.getEntityById(id) != null);
            }
        });
    }

    private static Random R() {
        return MinecraftClient.getInstance().world.random;
    }

    private static double rr(double a, double b) {
        return a + R().nextDouble() * (b - a);
    }

    private static void shake(Vec3d p, float s, double range) {
        var me = MinecraftClient.getInstance().player;
        if (me == null) return;
        double d = me.getPos().distanceTo(p);
        if (d < range) CameraShake.add(s * (float) (1 - d / range));
    }

    private static double groundY(Vec3d p) {
        ClientWorld w = MinecraftClient.getInstance().world;
        int x = MathHelper.floor(p.x), z = MathHelper.floor(p.z), top = MathHelper.floor(p.y) + 2;
        for (int y = top; y > top - 30; y--) {
            BlockPos bp = new BlockPos(x, y, z);
            if (!w.getBlockState(bp).getCollisionShape(w, bp).isEmpty()) return y + 1;
        }
        return p.y;
    }

    private static Vec3d dirOf(float yaw) {
        float y = yaw * MathHelper.RADIANS_PER_DEGREE;
        return new Vec3d(-MathHelper.sin(y), 0, MathHelper.cos(y));
    }

    private static void dust(Vec3d p, int n, double spread, float size) {
        for (int i = 0; i < n; i++) {
            double ang = rr(0, Math.PI * 2), sp = rr(0.05, 0.25) * spread;
            spawn(SMOKE, p.x, p.y + 0.2, p.z).vel(Math.cos(ang) * sp, rr(0.02, 0.12), Math.sin(ang) * sp).drag(0.9f)
                    .size(size * 0.6f, size * 1.6f).color(ASH_D).alpha(0.55f).life(26 + R().nextInt(18)).rot((float) rr(0, 6), 0.03f);
        }
    }

    private static void sparks(Vec3d p, int n, float[] c, double sp) {
        for (int i = 0; i < n; i++) {
            spawn(STREAK, p.x, p.y, p.z).vel(rr(-sp, sp), rr(-sp * 0.3, sp), rr(-sp, sp)).gravity(0.03f).drag(0.9f)
                    .stretch(3f).size(0.05f).color(c).life(8 + R().nextInt(8));
        }
    }

    // ================================================================== Звонарь

    private static void ringer(BellRingerEntity e, int type, Vec3d p, float arg) {
        switch (type) {
            case BellRingerEntity.E_STEP -> {
                shake(p, 0.18f, 14);
                dust(p, 6, 1, 0.5f);
            }
            case BellRingerEntity.E_SLAM -> {
                shake(p, 0.9f, 18);
                spawn(SHOCK, p.x, p.y + 0.06, p.z).mode(Mode.FLAT).size(0.5f, 3.2f).ease().colors(EMBER, ASH).life(12).fade(0f, 0.8f);
                spawn(RING, p.x, p.y + 0.08, p.z).mode(Mode.FLAT).size(0.6f, 4.4f).ease().color(CANDLE).life(10).alpha(0.8f).fade(0f, 0.7f);
                spawn(FLARE, p.x, p.y + 0.4, p.z).size(2.2f, 0.4f).color(EMBER).life(7);
                dust(p, 22, 2.2, 0.9f);
                sparks(p.add(0, 0.3, 0), 24, EMBER, 0.45);
            }
            case BellRingerEntity.E_CRACK -> {
                Vec3d f = dirOf(arg);
                for (int i = 0; i < 16; i++) {
                    double s = 0.6 + i * 0.56;
                    Vec3d q = p.add(f.multiply(s));
                    double gy = groundY(q.add(0, 1, 0)) + 0.04;
                    spawn(CRACK, q.x, gy, q.z).mode(Mode.FLAT).delay(i / 2).size(0.75f, 0.85f).rot((float) rr(0, 6.28), 0f)
                            .color(EMBER).life(26 - i / 2).alpha(0.95f).pulse(0.25f, 0.6f).fade(0.05f, 0.25f);
                    if (i % 3 == 0) spawn(FLAME, q.x, gy + 0.1, q.z).delay(i / 2).vel(0, 0.03, 0).size(0.18f, 0.05f).color(EMBER).life(14);
                }
            }
            case BellRingerEntity.E_WAVE -> {
                Vec3d f = dirOf(arg);
                for (int i = 0; i < 18; i++) {
                    double s = 0.6 + i * 0.52;
                    Vec3d q = p.add(f.multiply(s));
                    double gy = groundY(q.add(0, 1, 0));
                    int dl = (int) (i * 0.45);
                    spawn(SHOCK, q.x, gy + 0.05, q.z).mode(Mode.FLAT).delay(dl).size(0.3f, 1.7f).ease().colors(CANDLE, ASH).life(9).fade(0f, 0.8f);
                    for (int j = 0; j < 3; j++) {
                        spawn(FLAME, q.x + rr(-0.4, 0.4), gy + 0.1, q.z + rr(-0.4, 0.4)).delay(dl).vel(rr(-0.03, 0.03), rr(0.18, 0.32), rr(-0.03, 0.03))
                                .drag(0.88f).size(0.32f, 0.08f).colors(CANDLE, EMBER).life(12 + R().nextInt(6));
                    }
                    spawn(SMOKE, q.x, gy + 0.4, q.z).delay(dl).vel(0, 0.1, 0).drag(0.92f).size(0.5f, 1.4f).color(ASH_D).alpha(0.5f).life(30);
                    if (i % 4 == 0) {
                        int d = dl;
                        Vec3d qq = new Vec3d(q.x, gy, q.z);
                        task(age -> {
                            if (age == d) shake(qq, 0.35f, 10);
                            return age < d;
                        });
                    }
                }
            }
            case BellRingerEntity.E_TOLL -> {
                shake(p, 0.5f, 20);
                double gy = groundY(e.getPos().add(0, 1, 0)) + 0.08;
                // звуковое кольцо идёт от звонаря; под колоколом — тихий островок
                for (int h = 0; h < 3; h++) {
                    spawn(RING, e.getX(), gy + h * 0.7, e.getZ()).mode(Mode.FLAT).delay(h).size(0.6f, 12.5f).color(h == 0 ? PALE : WHITE)
                            .alpha(0.8f - h * 0.2f).life(16).fade(0f, 0.5f);
                }
                spawn(RING, p.x, p.y, p.z).size(0.8f, 4.5f).ease().color(WHITE).life(10).fade(0f, 0.8f);
                spawn(RING, p.x, p.y, p.z).delay(3).size(0.6f, 3.4f).ease().color(CANDLE).life(10).fade(0f, 0.8f);
                Vec3d safe = e.local(0, 0, -1.1);
                spawn(ORB, safe.x, gy - 0.04, safe.z).mode(Mode.FLAT).size(2.6f).color(CANDLE).alpha(0.18f).life(24).fade(0.1f, 0.5f);
                for (int i = 0; i < 6; i++) {
                    double ang = rr(0, Math.PI * 2);
                    spawn(GLINT, p.x, p.y, p.z).vel(Math.cos(ang) * 0.2, rr(0, 0.15), Math.sin(ang) * 0.2).drag(0.9f).size(0.25f, 0.05f).color(WHITE).life(16);
                }
            }
            case BellRingerEntity.E_CHAIN_SHOT -> sparks(p, 8, BRONZE, 0.2);
            case BellRingerEntity.E_CHAIN_HIT -> {
                sparks(p, 14, BRONZE, 0.3);
                spawn(FLARE, p.x, p.y, p.z).size(0.9f, 0.2f).color(BRONZE).life(5);
            }
            case BellRingerEntity.E_CHAIN_BREAK -> {
                spawn(FLARE, p.x, p.y, p.z).size(1.6f, 0.2f).color(WHITE).life(6);
                spawn(RING, p.x, p.y, p.z).size(0.2f, 1.6f).ease().color(CANDLE).life(8);
                sparks(p, 30, CANDLE, 0.5);
                for (int i = 0; i < 9; i++) {
                    spawn(STAR, p.x, p.y, p.z).vel(rr(-0.2, 0.2), rr(0.05, 0.3), rr(-0.2, 0.2)).gravity(0.05f).drag(0.96f)
                            .rot((float) rr(0, 6), 0.3f).size(0.09f).color(ASH).life(26);
                }
            }
            case BellRingerEntity.E_SWING_HIT -> {
                shake(p, 0.4f, 8);
                spawn(SHOCK, p.x, p.y, p.z).size(0.4f, 1.8f).ease().color(CANDLE).life(8);
                sparks(p, 16, BRONZE, 0.4);
            }
            case BellRingerEntity.E_JUMP -> dust(p, 16, 1.6, 0.8f);
            case BellRingerEntity.E_LAND -> {
                shake(p, 1.0f, 20);
                double gy = groundY(p.add(0, 1, 0)) + 0.06;
                spawn(SHOCK, p.x, gy, p.z).mode(Mode.FLAT).size(0.6f, 4.6f).ease().colors(CANDLE, ASH).life(12).fade(0f, 0.8f);
                spawn(RING, p.x, gy + 0.02, p.z).mode(Mode.FLAT).size(0.6f, 5f).ease().color(EMBER).alpha(0.8f).life(12).fade(0f, 0.7f);
                for (int i = 0; i < 10; i++) {
                    double ang = i * Math.PI / 5 + rr(-0.2, 0.2);
                    spawn(CRACK, p.x + Math.cos(ang) * 1.8, gy, p.z + Math.sin(ang) * 1.8).mode(Mode.FLAT).size(0.8f).rot((float) ang, 0)
                            .color(EMBER).life(30).fade(0.05f, 0.4f);
                }
                dust(p, 34, 3, 1.1f);
            }
            case BellRingerEntity.E_HAMMER_DROP -> {
                shake(p, 0.3f, 10);
                dust(p, 12, 1.2, 0.7f);
            }
            case BellRingerEntity.E_SNAP -> {
                sparks(p, 18, BRONZE, 0.35);
                spawn(FLARE, p.x, p.y, p.z).size(0.8f, 0.1f).color(CANDLE).life(5);
                for (int i = 0; i < 5; i++) {
                    spawn(STAR, p.x, p.y, p.z).vel(rr(-0.12, 0.12), rr(0, 0.15), rr(-0.12, 0.12)).gravity(0.06f).drag(0.96f)
                            .rot((float) rr(0, 6), 0.3f).size(0.08f).color(ASH).life(30);
                }
            }
            case BellRingerEntity.E_BELL_FALL -> {
                shake(p, 0.45f, 14);
                dust(p, 20, 1.8, 0.9f);
            }
            case BellRingerEntity.E_ASH -> {
                task(age -> {
                    if (!e.isAlive()) return false;
                    double top = 4.6 - age * 0.18;
                    for (int i = 0; i < 6; i++) {
                        double ang = rr(0, Math.PI * 2), r = rr(0, 1.0);
                        spawn(SMOKE, e.getX() + Math.cos(ang) * r, e.getY() + rr(0.3, Math.max(0.6, top)), e.getZ() + Math.sin(ang) * r)
                                .vel(rr(-0.02, 0.02), rr(-0.03, 0.04), rr(-0.02, 0.02)).gravity(-0.002f).drag(0.95f)
                                .size(0.25f, 0.8f).color(ASH).alpha(0.5f).life(40 + R().nextInt(20));
                        spawn(ORB, e.getX() + Math.cos(ang) * r, e.getY() + rr(0.3, Math.max(0.6, top)), e.getZ() + Math.sin(ang) * r)
                                .vel(rr(-0.04, 0.04), rr(-0.02, 0.05), rr(-0.04, 0.04)).gravity(0.004f).size(0.04f).color(EMBER).life(30);
                    }
                    return age < 24;
                });
            }
            case BellRingerEntity.E_RAGE -> {
                Vec3d h = e.local(0, 4.1, 1.1);
                spawn(FLARE, h.x, h.y, h.z).size(2.4f, 0.4f).color(CANDLE).life(10);
                for (int i = 0; i < 24; i++) {
                    spawn(FLAME, h.x, h.y, h.z).vel(rr(-0.2, 0.2), rr(0, 0.25), rr(-0.2, 0.2)).drag(0.9f).size(0.2f, 0.05f).colors(CANDLE, EMBER).life(16);
                }
            }
            default -> { }
        }
    }

    private static void tickRinger(BellRingerEntity e) {
        Random r = R();
        if (!e.dying() || e.age - e.clientDyingStart < BellRingerEntity.D_ASH) {
            // свечные глаза в пустоте капюшона
            if (e.age % 2 == 0) {
                for (int s = -1; s <= 1; s += 2) {
                    Vec3d h = e.local(s * 0.17, 4.12, 1.15);
                    spawn(FLAME, h.x + rr(-0.02, 0.02), h.y, h.z + rr(-0.02, 0.02)).vel(0, 0.012, 0).size(0.07f, 0.02f)
                            .colors(CANDLE, EMBER).life(6 + r.nextInt(4)).fade(0.1f, 0.5f);
                }
            }
            if (e.rage() && e.age % 3 == 0) {
                Vec3d h = e.local(rr(-0.5, 0.5), rr(1.5, 4), rr(-0.6, 0.8));
                spawn(ORB, h.x, h.y, h.z).vel(0, 0.03, 0).size(0.05f, 0.01f).color(EMBER).life(20);
            }
        }
        // пепел осыпается с рясы
        if (!e.dying() && e.age % 4 == 0) {
            Vec3d h = e.local(rr(-0.8, 0.8), rr(0.5, 3.5), rr(-0.8, 0.6));
            spawn(SMOKE, h.x, h.y, h.z).vel(0, -0.01, 0).size(0.08f, 0.2f).color(ASH).alpha(0.45f).life(30);
        }
        // след колокола при размахе
        if (!e.dying() && e.attack() == BellRingerEntity.SWING) {
            float k = e.age - e.clientAttackStart;
            if (k > 22 && k < 104) {
                Vec3d b = BellRingerRenderer.swingBell(e, k, 0f);
                spawn(SMOKE, b.x, b.y, b.z).size(0.6f, 1.2f).color(ASH_D).alpha(0.4f).life(14);
                spawn(GLINT, b.x + rr(-0.6, 0.6), b.y + rr(-0.6, 0.6), b.z + rr(-0.6, 0.6)).size(0.12f, 0.02f).color(CANDLE).life(8);
                double gy = groundY(b);
                if (k % 2 == 0) spawn(SMOKE, b.x, gy + 0.1, b.z).vel(rr(-0.05, 0.05), 0.02, rr(-0.05, 0.05)).size(0.4f, 1f).color(ASH_D).alpha(0.4f).life(20);
            }
        }
    }

    // ================================================================== Колокол

    private static void bell(SilentBellEntity e, int type, Vec3d p, float arg) {
        switch (type) {
            case SilentBellEntity.E_AWAKE -> {
                shake(p, 0.7f, 30);
                double gy = groundY(p.add(0, 1, 0)) + 0.06;
                spawn(RING, p.x, gy, p.z).mode(Mode.FLAT).size(1f, 18f).color(GREEN).alpha(0.7f).life(30).fade(0f, 0.6f);
                spawn(RING, p.x, gy + 0.02, p.z).mode(Mode.FLAT).delay(6).size(1f, 12f).color(PATINA).alpha(0.5f).life(30).fade(0f, 0.6f);
                dust(p, 26, 2.5, 1f);
            }
            case SilentBellEntity.E_EYE_OPEN -> {
                spawn(FLARE, p.x, p.y, p.z).size(1.8f, 0.3f).color(GREEN).life(8);
                spawn(RING, p.x, p.y, p.z).size(0.3f, 1.6f).ease().color(PALE).life(8);
            }
            case SilentBellEntity.E_CLANG -> {
                spawn(FLARE, p.x, p.y, p.z).size(0.7f, 0.1f).color(BRONZE).life(4);
                sparks(p, 10, BRONZE, 0.3);
            }
            case SilentBellEntity.E_CONE_CHARGE -> {
                int dur = (int) arg;
                for (int i = 0; i < 26; i++) {
                    double ang = rr(0, Math.PI * 2), r = rr(2.5, 4);
                    spawn(RUNES, p.x + Math.cos(ang) * r, p.y + rr(-1.5, 1.5), p.z + Math.sin(ang) * r).delay(R().nextInt(Math.max(1, dur - 6)))
                            .vel(-Math.cos(ang) * r / 8, 0, -Math.sin(ang) * r / 8).size(0.22f, 0.05f).color(GREEN).life(8);
                }
                spawn(ORB, p.x, p.y, p.z).size(0.2f, 1.1f).color(GREEN).alpha(0.7f).life(dur).fade(0.3f, 0.1f);
            }
            case SilentBellEntity.E_CONE -> {
                Vec3d f = dirOf(arg);
                shake(p, 0.6f, 20);
                double half = Math.toRadians(SilentBellEntity.CONE_DEG);
                for (int i = 0; i < 150; i++) {
                    double a = rr(-half, half), el = rr(-0.25, 0.2);
                    double ca = Math.cos(a), sa = Math.sin(a);
                    Vec3d d = new Vec3d(f.x * ca - f.z * sa, el, f.z * ca + f.x * sa).normalize();
                    double sp = rr(0.9, 1.25);
                    spawn(i % 3 == 0 ? GLINT : ORB, p.x, p.y, p.z).vel(d.x * sp, d.y * sp, d.z * sp).drag(0.97f)
                            .size(0.14f, 0.5f).colors(PALE, GREEN).alpha(0.8f).life(11 + R().nextInt(3)).fade(0.05f, 0.5f);
                }
                // волновые фронты — дуги колец по ходу конуса
                for (int w = 0; w < 4; w++) {
                    for (int j = -4; j <= 4; j++) {
                        double a = half * j / 4.0, ca = Math.cos(a), sa = Math.sin(a);
                        Vec3d d = new Vec3d(f.x * ca - f.z * sa, 0, f.z * ca + f.x * sa);
                        spawn(RING, p.x, p.y - 1.2, p.z).delay(w * 2).vel(d.x * 1.15, 0, d.z * 1.15).size(0.6f, 2.6f).color(GREEN)
                                .alpha(0.55f).life(11).fade(0f, 0.6f);
                    }
                }
                spawn(FLARE, p.x, p.y, p.z).size(3f, 0.4f).color(GREEN).life(8);
            }
            case SilentBellEntity.E_SHADOW -> {
                task(age -> {
                    if (!e.isAlive() || e.attack() != SilentBellEntity.DROP) return false;
                    double gy = groundY(e.getPos());
                    if (age % 3 == 0) spawn(RING, e.getX(), gy + 0.05, e.getZ()).mode(Mode.FLAT).size(3.1f, 2.6f).color(GREEN).alpha(0.5f).life(4);
                    if (age % 2 == 0) spawn(RUNES, e.getX() + rr(-2, 2), gy + 0.1, e.getZ() + rr(-2, 2)).vel(0, 0.05, 0).size(0.2f, 0.05f).color(GREEN).life(10);
                    return age < 40;
                });
            }
            case SilentBellEntity.E_DROP_LAND -> {
                shake(p, 1.1f, 22);
                spawn(SHOCK, p.x, p.y + 0.06, p.z).mode(Mode.FLAT).size(1f, 6.4f).ease().colors(PALE, GREEN).life(12).fade(0f, 0.8f);
                spawn(RING, p.x, p.y + 0.08, p.z).mode(Mode.FLAT).size(1f, 7f).ease().color(PATINA).alpha(0.8f).life(14).fade(0f, 0.7f);
                dust(p, 40, 3.4, 1.2f);
            }
            case SilentBellEntity.E_TRAP -> {
                for (int i = 0; i < 20; i++) {
                    double ang = i * Math.PI / 10;
                    spawn(RUNES, p.x + Math.cos(ang) * 2, p.y + 0.3, p.z + Math.sin(ang) * 2).vel(0, 0.04, 0).size(0.3f, 0.1f).color(GREEN).life(30);
                }
            }
            case SilentBellEntity.E_TRAP_HIT -> {
                spawn(FLARE, p.x, p.y, p.z).size(1.4f + arg * 0.3f, 0.2f).color(GREEN).life(6);
                sparks(p, 12, GREEN, 0.3);
                shake(p, 0.25f, 6);
            }
            case SilentBellEntity.E_RELEASE -> {
                if (arg > 0) {
                    shake(p, 0.6f, 12);
                    spawn(SHOCK, p.x, p.y + 1.3, p.z).size(0.6f, 4f).ease().color(GREEN).life(10);
                    sparks(p.add(0, 1.5, 0), 26, PALE, 0.5);
                }
            }
            case SilentBellEntity.E_CHOIR -> {
                spawn(FLARE, p.x, p.y, p.z).size(1.4f, 0.2f).color(PALE).life(10);
                for (int i = 0; i < 14; i++) {
                    spawn(SMOKE, p.x, p.y, p.z).vel(rr(-0.08, 0.08), rr(-0.02, 0.1), rr(-0.08, 0.08)).drag(0.92f).size(0.3f, 0.9f)
                            .color(PALE).alpha(0.35f).life(30);
                }
            }
            case SilentBellEntity.E_HEAL -> {
                Vec3d to = e.getPos().add(0, SilentBellEntity.EYE_Y, 0);
                for (int i = 0; i < 6; i++) {
                    Vec3d d = to.subtract(p);
                    int life = 14;
                    spawn(i % 2 == 0 ? STAR : ORB, p.x, p.y, p.z).delay(i * 2).vel(d.x / life, d.y / life, d.z / life).size(0.14f, 0.08f)
                            .colors(PALE, GREEN).life(life).fade(0.1f, 0.2f);
                }
            }
            case SilentBellEntity.E_GROUND_RING -> {
                int life = (int) (SilentBellEntity.GROUND_RING_MAX / SilentBellEntity.GROUND_RING_SPEED);
                spawn(RING, p.x, p.y + 0.06, p.z).mode(Mode.FLAT).size(0.1f, SilentBellEntity.GROUND_RING_MAX * 1.05f).color(GREEN)
                        .alpha(0.75f).life(life).fade(0.02f, 0.2f).pulse(0.15f, 0.4f);
                spawn(RING, p.x, p.y + 0.5, p.z).mode(Mode.FLAT).size(0.1f, SilentBellEntity.GROUND_RING_MAX * 1.05f).color(PATINA)
                        .alpha(0.3f).life(life).fade(0.02f, 0.2f);
            }
            case SilentBellEntity.E_RING_HIT -> sparks(p.add(0, 0.3, 0), 8, GREEN, 0.25);
            case SilentBellEntity.E_WIDEN -> {
                shake(p, 0.8f, 24);
                spawn(FLARE, p.x, p.y, p.z).size(4f, 0.5f).color(GREEN).life(14);
                spawn(RING, p.x, p.y, p.z).size(0.5f, 6f).ease().color(PALE).life(14);
                sparks(p, 40, GREEN, 0.6);
            }
            case SilentBellEntity.E_DARK -> {
                for (int i = 0; i < 20; i++) {
                    spawn(SMOKE, p.x, p.y, p.z).vel(rr(-0.05, 0.05), rr(0, 0.08), rr(-0.05, 0.05)).size(0.3f, 0.9f).color(ASH_D).alpha(0.5f).life(40);
                }
            }
            case SilentBellEntity.E_SPLIT -> {
                shake(p, 1.2f, 30);
                spawn(BEAM, p.x, p.y, p.z).mode(Mode.BEAM).height(9f).size(0.6f, 0.05f).color(GREEN).life(30).fade(0f, 0.7f);
                spawn(FLARE, p.x, p.y + 3, p.z).size(5f, 0.5f).color(PALE).life(16);
                for (int i = 0; i < 40; i++) {
                    spawn(STAR, p.x + rr(-0.4, 0.4), p.y + rr(0.5, 5.5), p.z + rr(-0.4, 0.4)).vel(rr(-0.3, 0.3), rr(0, 0.3), rr(-0.3, 0.3))
                            .gravity(0.04f).drag(0.95f).rot((float) rr(0, 6), 0.3f).size(0.12f).color(BRONZE).life(40);
                }
                for (int r = 0; r < 4; r++) {
                    spawn(RING, p.x, p.y + 0.1, p.z).mode(Mode.FLAT).delay(r * 8).size(1f, 14f - r * 2).color(r % 2 == 0 ? GREEN : PATINA)
                            .alpha(0.6f - r * 0.12f).life(30).fade(0f, 0.6f);
                }
                dust(p, 40, 3.4, 1.2f);
            }
            default -> { }
        }
    }

    private static void tickBell(SilentBellEntity e) {
        int st = e.state();
        Random r = R();
        if (st == SilentBellEntity.S_AWAKEN && e.age - e.clientStateStart < SilentBellEntity.A_TOLL) return;   // тишина
        if (st == SilentBellEntity.S_DYING && e.age - e.clientStateStart > 40) return;
        // зелёные искры поднимаются из трещины; лохмотья роняют пыль
        if (e.age % 2 == 0) {
            Vec3d c = e.eyePos();
            spawn(ORB, c.x + rr(-0.6, 0.6), c.y + rr(-1.4, 1.4), c.z + rr(-0.6, 0.6)).vel(0, rr(0.01, 0.04), 0).size(0.05f, 0.01f)
                    .color(GREEN).life(26 + r.nextInt(10));
        }
        if (e.age % 5 == 0) {
            double ang = rr(0, Math.PI * 2);
            spawn(RUNES, e.getX() + Math.cos(ang) * 1.9, e.getY() + rr(2.9, 4.1), e.getZ() + Math.sin(ang) * 1.9).vel(0, 0.01, 0)
                    .size(0.16f, 0.05f).color(GREEN).alpha(0.7f).life(20);
        }
        if (e.widened() && st == SilentBellEntity.S_FIGHT && e.age % 24 == 0) {
            Vec3d c = e.eyePos();
            spawn(RING, c.x, c.y, c.z).size(0.6f, 3.5f).color(GREEN).alpha(0.4f).life(16).fade(0f, 0.7f);
        }
        if (e.eyeOpen() && e.age % 3 == 0) {
            Vec3d c = e.eyePos();
            spawn(GLINT, c.x, c.y, c.z).size(0.3f, 0.1f).color(PALE).alpha(0.6f).life(4);
        }
    }
}
