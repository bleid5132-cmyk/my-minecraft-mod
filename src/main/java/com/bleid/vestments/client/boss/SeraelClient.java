package com.bleid.vestments.client.boss;

import static com.bleid.vestments.client.fx.FxSystem.*;

import com.bleid.vestments.boss.SeraelEntity;
import com.bleid.vestments.client.fx.FxSystem;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

/**
 * Клиентские эффекты Сераэль: вихрь пепла и алых лепестков, светящиеся глаза, и для каждой атаки —
 * свои кинематографичные эффекты (дуга тёмной энергии и её взрыв, каскад лепестков, кольцо нимба,
 * пылающее пике с кратером и огнём, сфера барьера и её раскол).
 */
@Environment(EnvType.CLIENT)
public final class SeraelClient {
    private SeraelClient() { }

    static final float[] VIOLET = { 0.62f, 0.2f, 1f }, VOID_DEEP = { 0.34f, 0.04f, 0.7f }, CRIMSON = { 1f, 0.1f, 0.16f },
            EMBER = { 1f, 0.45f, 0.12f }, GOLD = { 1f, 0.8f, 0.36f }, WHITE = { 1f, 0.95f, 0.88f }, PALE = { 0.9f, 0.75f, 1f };
    private static final Map<Integer, Integer> SEEN = new HashMap<>();

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            ClientWorld w = mc.world;
            if (w == null || mc.player == null) { SEEN.clear(); return; }
            if (mc.isPaused()) return;
            for (Entity en : w.getEntities()) {
                if (!(en instanceof SeraelEntity e) || e.squaredDistanceTo(mc.player) > 96 * 96) continue;
                int ev = e.eventValue();
                Integer last = SEEN.put(e.getId(), ev);
                if (last != null && last != ev && ev != 0) event(e, ev & 15, e.eventPos(), e.eventArg());
                tick(w, e);
            }
            if (mc.world.getTime() % 100 == 0) SEEN.keySet().removeIf(id -> mc.world.getEntityById(id) == null);
        });
    }

    private static Random R() {
        return net.minecraft.client.MinecraftClient.getInstance().world.random;
    }

    private static double rr(double a, double b) {
        return a + R().nextDouble() * (b - a);
    }

    // ================================================================== события атак

    private static void event(SeraelEntity e, int type, Vec3d p, float arg) {
        switch (type) {
            case SeraelEntity.E_ARC -> arc(p, arg);
            case SeraelEntity.E_SLASH -> slash(p);
            case SeraelEntity.E_PETAL_ZONE -> petalZone(p, arg);
            case SeraelEntity.E_PETAL_FALL -> petalFall(p, arg);
            case SeraelEntity.E_HALO_RING -> haloRing(p, arg);
            case SeraelEntity.E_DIVE_MARK -> diveMark(p);
            case SeraelEntity.E_IMPACT -> impact(p);
            case SeraelEntity.E_SHIELD_BREAK -> shieldBreak(p);
            case SeraelEntity.E_SHIELD_BOOM -> shieldBoom(p);
            case SeraelEntity.E_SHIELD_HIT -> shieldHit(p, arg);
            case SeraelEntity.E_ROAR -> roar(p);
            default -> { }
        }
    }

    /** «Проклятый взмах»: росчерк клинка оставляет висящую дугу тёмной энергии, через 1,5 с она взрывается волной. */
    private static void arc(Vec3d c, float yawDeg) {
        float y = yawDeg * MathHelper.RADIANS_PER_DEGREE;
        Vec3d f = new Vec3d(-MathHelper.sin(y), 0, MathHelper.cos(y)), r = new Vec3d(-MathHelper.cos(y), 0, -MathHelper.sin(y));
        int N = 18;
        Vec3d[] pts = new Vec3d[N];
        Vec3d[] tan = new Vec3d[N];
        int[] born = new int[N], boom = new int[N];
        for (int i = 0; i < N; i++) {
            float k = i / (N - 1f);
            double ang = Math.toRadians(-75 + 150 * k);
            pts[i] = c.add(f.multiply(Math.cos(ang) * 4.6)).add(r.multiply(-Math.sin(ang) * 4.6)).add(0, 2.6 + 0.6 * Math.sin(k * Math.PI), 0);
            tan[i] = f.multiply(-Math.sin(ang)).add(r.multiply(-Math.cos(ang))).normalize();
            born[i] = Math.round(k * 5);
            boom[i] = born[i] + 30 + Math.round(k * 8);
        }
        Vec3d mid = pts[N / 2];
        spawn(SLASH, mid.x, mid.y, mid.z).size(1.5f, 4.6f).ease().colors(PALE, VIOLET).life(9).fade(0f, 0.6f).rot((float) Math.toRadians(-yawDeg), 0f);
        FxSystem.task(age -> {
            for (int i = 0; i < N; i++) {
                Vec3d p = pts[i];
                if (age == born[i]) {
                    spawn(STREAK, p.x, p.y, p.z).vel(tan[i].x * 0.05, 0, tan[i].z * 0.05).stretch(9f).size(0.35f).colors(WHITE, VIOLET).life(10).fade(0f, 0.7f);
                    spawn(FLARE, p.x, p.y, p.z).size(0.5f, 0.75f).color(VIOLET).alpha(0.75f).life(boom[i] - born[i]).fade(0.1f, 0.05f).pulse(0.25f, 0.5f);
                }
                if (age > born[i] && age < boom[i]) {
                    if ((age + i) % 2 == 0) {
                        spawn(ORB, p.x + rr(-0.3, 0.3), p.y + rr(-0.3, 0.3), p.z + rr(-0.3, 0.3)).vel(0, 0.012, 0)
                                .size(0.22f, 0.05f).colors(VIOLET, VOID_DEEP).alpha(0.7f).life(14).fade(0.2f, 0.6f);
                    }
                    if (R().nextInt(5) == 0) {
                        spawn(GLINT, p.x, p.y, p.z).vel(rr(-0.03, 0.03), rr(0, 0.04), rr(-0.03, 0.03)).size(0.12f, 0f).color(PALE).life(12);
                    }
                    if (age == boom[i] - 6) {
                        spawn(FLARE, p.x, p.y, p.z).size(0.4f, 1.3f).ease().colors(VIOLET, WHITE).life(6).fade(0f, 0.1f);
                    }
                }
                if (age == boom[i]) {
                    spawn(SUNBURST, p.x, p.y, p.z).size(0.6f, 2.6f).ease().colors(WHITE, VIOLET).life(10).fade(0f, 0.7f).rot((float) rr(0, 6.28), 0.05f);
                    spawn(SHOCK, p.x, p.y, p.z).size(0.3f, 2.2f).ease().colors(PALE, VOID_DEEP).life(12).fade(0f, 0.7f);
                    for (int j = 0; j < 7; j++) {
                        spawn(GLINT, p.x, p.y, p.z).vel(rr(-0.25, 0.25), rr(-0.05, 0.3), rr(-0.25, 0.25)).gravity(0.012f).drag(0.9f)
                                .stretch(3f).size(0.12f).colors(WHITE, VIOLET).life(16 + R().nextInt(8));
                    }
                }
            }
            return age < 50;
        });
    }

    /** Удар сверху: трещина в земле и ударная волна. */
    private static void slash(Vec3d p) {
        spawn(CRACK, p.x, p.y + 0.05, p.z).mode(Mode.FLAT).size(0.6f, 2.8f).ease().color(CRIMSON).alpha(0.9f).life(50).fade(0f, 0.5f).rot((float) rr(0, 6.28), 0);
        spawn(SHOCK, p.x, p.y + 0.06, p.z).mode(Mode.FLAT).size(0.4f, 4f).ease().colors(WHITE, CRIMSON).life(14).fade(0f, 0.7f);
        spawn(SUNBURST, p.x, p.y + 0.5, p.z).size(0.5f, 2.4f).ease().colors(WHITE, VIOLET).life(8).fade(0f, 0.7f);
        for (int j = 0; j < 16; j++) {
            spawn(STREAK, p.x, p.y + 0.2, p.z).vel(rr(-0.25, 0.25), rr(0.2, 0.55), rr(-0.25, 0.25)).gravity(0.03f).stretch(3f).size(0.1f)
                    .colors(WHITE, CRIMSON).life(14);
        }
    }

    /** «Дождь из лепестков»: метка-круг на земле и вихрь лепестков над игроком. */
    private static void petalZone(Vec3d c, float rad) {
        spawn(RUNES, c.x, c.y + 0.06, c.z).mode(Mode.FLAT).size(rad * 0.2f, rad).ease().rot(0, 0.03f).color(CRIMSON).alpha(0.75f).life(44).fade(0.1f, 0.15f);
        spawn(RING, c.x, c.y + 0.07, c.z).mode(Mode.FLAT).size(rad).color(CRIMSON).alpha(0.9f).life(44).fade(0.1f, 0.1f).pulse(0.3f, 0.6f);
        FxSystem.task(age -> {
            for (int i = 0; i < 7; i++) {
                double ang = rr(0, Math.PI * 2), r = Math.sqrt(R().nextDouble()) * rad;
                spawn(FEATHER, c.x, c.y, c.z).orbit(ang, r, rr(0.05, 0.11), 0, rr(5.5, 8.5), -0.004).rot((float) rr(0, 6.28), 0.15f)
                        .size(0.16f, 0.12f).colors(CRIMSON, new float[] { 0.6f, 0.02f, 0.06f }).life(Math.max(4, 40 - age)).fade(0.2f, 0.1f);
            }
            if (age % 3 == 0) {
                double ang = rr(0, Math.PI * 2), r = Math.sqrt(R().nextDouble()) * rad;
                spawn(GLINT, c.x + Math.cos(ang) * r, c.y + 0.1, c.z + Math.sin(ang) * r).vel(0, 0.03, 0).size(0.1f, 0f).color(CRIMSON).life(18);
            }
            return age < 40;
        });
    }

    /** Лепестки превращаются в острия и каскадом падают. */
    private static void petalFall(Vec3d c, float rad) {
        spawn(FLARE, c.x, c.y + 7, c.z).size(rad * 0.4f, rad * 0.9f).ease().colors(WHITE, CRIMSON).alpha(0.5f).life(8).fade(0f, 0.8f);
        FxSystem.task(age -> {
            for (int i = 0; i < 9; i++) {
                double ang = rr(0, Math.PI * 2), r = Math.sqrt(R().nextDouble()) * rad;
                double x = c.x + Math.cos(ang) * r, z = c.z + Math.sin(ang) * r;
                spawn(STREAK, x, c.y + 7.5, z).vel(0, -1.1, 0).stretch(6f).size(0.16f).colors(WHITE, CRIMSON).life(7).fade(0f, 0.1f);
                spawn(SHOCK, x, c.y + 0.07, z).mode(Mode.FLAT).delay(6).size(0.1f, 0.9f).ease().colors(WHITE, CRIMSON).life(8).fade(0f, 0.7f);
                spawn(GLINT, x, c.y + 0.2, z).delay(6).vel(rr(-0.1, 0.1), rr(0.05, 0.2), rr(-0.1, 0.1)).gravity(0.02f).size(0.1f).color(CRIMSON).life(10);
            }
            return age < 22;
        });
    }

    /** «Тень нимба»: вспышка нимба и кольцо света, расходящееся по земле и в воздухе. */
    private static void haloRing(Vec3d c, float rad) {
        double hy = c.y + 6.3;
        spawn(SUNBURST, c.x, hy, c.z).size(0.6f, 6f).ease().colors(WHITE, GOLD).life(16).fade(0f, 0.7f).rot(0, 0.04f);
        spawn(FLARE, c.x, hy, c.z).size(1f, 3.5f).ease().colors(WHITE, GOLD).life(12).fade(0f, 0.6f);
        spawn(BEAM, c.x, c.y, c.z).mode(Mode.BEAM).height(9f).size(0.8f, 0.2f).colors(WHITE, GOLD).life(12).fade(0f, 0.7f);
        for (double h : new double[] { 0.07, 1.6, 3.4, 6.3 }) {
            spawn(RING, c.x, c.y + h, c.z).mode(Mode.FLAT).size(0.5f, rad).colors(WHITE, GOLD).alpha(h < 0.1 ? 1f : 0.6f).life(14).fade(0f, 0.5f);
        }
        spawn(SHOCK, c.x, c.y + 0.08, c.z).mode(Mode.FLAT).size(0.5f, rad * 1.05f).colors(WHITE, GOLD).life(13).fade(0f, 0.7f);
        for (int j = 0; j < 60; j++) {
            double ang = j * Math.PI * 2 / 60;
            spawn(STAR, c.x, c.y + rr(0.3, 6.3), c.z).vel(Math.cos(ang) * 0.85, 0, Math.sin(ang) * 0.85).drag(0.93f)
                    .size(0.18f, 0.02f).colors(WHITE, GOLD).life(14 + R().nextInt(6));
        }
    }

    /** Метка пикирования: руны и красный луч с неба. */
    private static void diveMark(Vec3d p) {
        spawn(RUNES, p.x, p.y + 0.06, p.z).mode(Mode.FLAT).size(0.4f, 3f).ease().rot(0, -0.08f).color(CRIMSON).life(28).fade(0.1f, 0.2f);
        spawn(RING, p.x, p.y + 0.07, p.z).mode(Mode.FLAT).size(3.2f).color(EMBER).alpha(0.8f).life(28).fade(0.1f, 0.2f).pulse(0.4f, 1.2f);
        spawn(BEAM, p.x, p.y, p.z).mode(Mode.BEAM).height(16f).size(0.25f).color(CRIMSON).alpha(0.6f).life(28).fade(0.2f, 0.2f);
    }

    /** Удар пике: огненная ударная волна, кратер-трещина и зона горения на 5 секунд. */
    private static void impact(Vec3d p) {
        spawn(SHOCK, p.x, p.y + 0.06, p.z).mode(Mode.FLAT).size(0.5f, 7.5f).ease().colors(WHITE, EMBER).life(18).fade(0f, 0.7f);
        spawn(RING, p.x, p.y + 0.08, p.z).mode(Mode.FLAT).delay(2).size(0.5f, 5f).ease().colors(EMBER, CRIMSON).life(18).fade(0f, 0.7f);
        spawn(CRACK, p.x, p.y + 0.05, p.z).mode(Mode.FLAT).size(1f, 4.6f).ease().colors(EMBER, CRIMSON).alpha(0.9f).life(100).fade(0f, 0.4f).rot((float) rr(0, 6.28), 0);
        spawn(SUNBURST, p.x, p.y + 1, p.z).size(1f, 6.5f).ease().colors(WHITE, EMBER).life(12).fade(0f, 0.7f);
        spawn(FLARE, p.x, p.y + 1, p.z).size(2f, 5f).ease().colors(WHITE, EMBER).life(9).fade(0f, 0.7f);
        for (int j = 0; j < 46; j++) {
            double ang = rr(0, Math.PI * 2), sp = rr(0.2, 0.7);
            spawn(FLAME, p.x, p.y + 0.3, p.z).vel(Math.cos(ang) * sp, rr(0.1, 0.5), Math.sin(ang) * sp).gravity(0.025f).drag(0.9f)
                    .size(0.45f, 0.1f).colors(new float[] { 1f, 0.8f, 0.4f }, EMBER).life(18 + R().nextInt(10));
        }
        FxSystem.task(age -> {
            for (int i = 0; i < 5; i++) {
                double ang = rr(0, Math.PI * 2), r = Math.sqrt(R().nextDouble()) * 4;
                double x = p.x + Math.cos(ang) * r, z = p.z + Math.sin(ang) * r;
                spawn(FLAME, x, p.y + 0.1, z).vel(0, rr(0.04, 0.1), 0).size(rr(0.25, 0.5) > 0.4 ? 0.5f : 0.3f, 0.05f)
                        .colors(new float[] { 1f, 0.75f, 0.3f }, CRIMSON).life(12 + R().nextInt(10)).fade(0.1f, 0.5f);
                if (i == 0) spawn(GLINT, x, p.y + 0.2, z).vel(rr(-0.02, 0.02), rr(0.05, 0.12), rr(-0.02, 0.02)).size(0.08f, 0f).color(EMBER).life(26);
            }
            if (age % 12 == 0) spawn(RING, p.x, p.y + 0.07, p.z).mode(Mode.FLAT).size(4f).color(EMBER).alpha(0.5f).life(14).fade(0.3f, 0.5f);
            return age < 100;
        });
    }

    /** Барьер разбит: осколки фиолетового стекла и вспышка. */
    private static void shieldBreak(Vec3d c) {
        double y = c.y + 3;
        spawn(SHOCK, c.x, y, c.z).size(0.5f, 6.5f).ease().colors(WHITE, VIOLET).life(14).fade(0f, 0.7f);
        spawn(SUNBURST, c.x, y, c.z).size(1f, 5f).ease().colors(WHITE, VIOLET).life(10).fade(0f, 0.7f);
        spawn(SHOCK, c.x, c.y + 0.07, c.z).mode(Mode.FLAT).size(0.5f, 6f).ease().colors(PALE, VIOLET).life(14).fade(0f, 0.7f);
        for (int j = 0; j < 80; j++) {
            double u = rr(-1, 1), th = rr(0, Math.PI * 2), s = Math.sqrt(1 - u * u), sp = rr(0.25, 0.65);
            double dx = s * Math.cos(th), dy = u, dz = s * Math.sin(th);
            spawn(GLINT, c.x + dx * 3.2, y + dy * 3.3, c.z + dz * 3.2).vel(dx * sp, dy * sp + 0.15, dz * sp).gravity(0.025f).drag(0.94f)
                    .rot((float) rr(0, 6.28), 0.3f).stretch(2f).size(0.18f, 0.06f).colors(WHITE, VIOLET).life(22 + R().nextInt(14));
        }
    }

    /** Барьер не разбит — детонирует тёмной волной. */
    private static void shieldBoom(Vec3d c) {
        double y = c.y + 3;
        spawn(SUNBURST, c.x, y, c.z).size(1.5f, 8f).ease().colors(PALE, VOID_DEEP).life(16).fade(0f, 0.7f).rot(0, 0.05f);
        spawn(SHOCK, c.x, c.y + 0.07, c.z).mode(Mode.FLAT).size(0.5f, 8f).ease().colors(VIOLET, VOID_DEEP).life(18).fade(0f, 0.7f);
        spawn(RING, c.x, c.y + 0.08, c.z).mode(Mode.FLAT).delay(3).size(0.5f, 7f).ease().color(VIOLET).life(16).fade(0f, 0.7f);
        for (int j = 0; j < 50; j++) {
            double ang = rr(0, Math.PI * 2), sp = rr(0.3, 0.8);
            spawn(ORB, c.x, y + rr(-1.5, 1.5), c.z).vel(Math.cos(ang) * sp, rr(-0.05, 0.15), Math.sin(ang) * sp).drag(0.9f)
                    .size(0.4f, 0.05f).colors(VIOLET, VOID_DEEP).life(20 + R().nextInt(10));
        }
    }

    private static void shieldHit(Vec3d p, float charge) {
        spawn(FLARE, p.x, p.y, p.z).size(0.4f, 1.2f + charge).ease().colors(WHITE, VIOLET).life(6).fade(0f, 0.7f);
        spawn(CRACK, p.x, p.y, p.z).size(0.6f + charge, 1.2f + charge).color(VIOLET).alpha(0.8f).life(14).fade(0f, 0.6f).rot((float) rr(0, 6.28), 0);
        for (int j = 0; j < 8; j++) {
            spawn(GLINT, p.x, p.y, p.z).vel(rr(-0.2, 0.2), rr(-0.1, 0.25), rr(-0.2, 0.2)).gravity(0.02f).stretch(2f).size(0.1f).color(PALE).life(12);
        }
    }

    private static void roar(Vec3d c) {
        for (int i = 0; i < 3; i++) {
            spawn(SHOCK, c.x, c.y + 0.07, c.z).mode(Mode.FLAT).delay(i * 4).size(0.5f, 9f).ease().colors(WHITE, CRIMSON).life(16).fade(0f, 0.7f);
        }
        spawn(SUNBURST, c.x, c.y + 4.5, c.z).size(1f, 7f).ease().colors(WHITE, CRIMSON).life(16).fade(0f, 0.7f);
        for (int j = 0; j < 40; j++) {
            double ang = rr(0, Math.PI * 2), r = rr(1, 3);
            spawn(FEATHER, c.x + Math.cos(ang) * r, c.y + rr(1, 6), c.z + Math.sin(ang) * r).vel(Math.cos(ang) * 0.5, rr(0, 0.2), Math.sin(ang) * 0.5)
                    .drag(0.92f).rot((float) rr(0, 6.28), 0.2f).size(0.18f, 0.08f).color(CRIMSON).life(30 + R().nextInt(20));
        }
    }

    // ================================================================== постоянные эффекты

    private static void tick(ClientWorld w, SeraelEntity e) {
        Random R = w.random;
        double cx = e.getX(), cy = e.getY(), cz = e.getZ();
        boolean dead = e.deathTime > 0;
        float yaw = e.bodyYaw * MathHelper.RADIANS_PER_DEGREE;
        double lx = Math.cos(yaw), lz = Math.sin(yaw), fx = -Math.sin(yaw), fz = Math.cos(yaw);
        int a = e.attack();
        float tk = e.age - e.clientAttackStart;
        // вихрь лепестков и пепла
        for (int i = 0; i < (dead ? 1 : 3); i++) {
            double ang = R.nextDouble() * Math.PI * 2, r = 2.4 + R.nextDouble() * 2.4, y = 0.3 + R.nextDouble() * 5.5;
            if (i == 0) {
                spawn(FEATHER, cx, cy, cz).orbit(ang, r, 0.06 + R.nextDouble() * 0.04, 0.004, y, 0.01).rot((float) (R.nextDouble() * 6.28), 0.12f)
                        .size(0.11f, 0.07f).color(0.78f + R.nextFloat() * 0.18f, 0.04f, 0.08f).life(40 + R.nextInt(30)).fade(0.15f, 0.4f);
            } else {
                double x = cx + Math.cos(ang) * r, z = cz + Math.sin(ang) * r;
                w.addParticle(R.nextBoolean() ? ParticleTypes.ASH : ParticleTypes.WHITE_ASH, x, cy + y, z, -Math.sin(ang) * 0.08, 0.02, Math.cos(ang) * 0.08);
            }
        }
        // холодное золотое свечение глаз
        if (!dead && a != SeraelEntity.STUNNED) {
            for (int s = -1; s <= 1; s += 2) {
                double ex = cx + lx * s * 0.13 + fx * 0.36, ez = cz + lz * s * 0.13 + fz * 0.36;
                spawn(FLARE, ex, cy + 5.53, ez).size(0.09f).color(GOLD).alpha(0.8f).life(2).fade(0f, 0f);
            }
        }
        // щит «Демонического барьера»
        float ch = e.shieldCharge();
        if (ch >= 0) {
            for (int i = 0; i < 10; i++) {
                double u = R.nextDouble() * 2 - 1, th = R.nextDouble() * Math.PI * 2, s = Math.sqrt(1 - u * u), r = 3.3;
                double x = cx + s * Math.cos(th) * r, y = cy + 2.8 + u * r * 1.05, z = cz + s * Math.sin(th) * r;
                spawn(ORB, x, y, z).size(0.35f, 0.15f).colors(ch > R.nextFloat() ? PALE : VIOLET, VOID_DEEP).alpha(0.35f + 0.4f * ch).life(10).fade(0.3f, 0.5f);
                if (i < 2) w.addParticle(SeraelEntity.DARK, x, y, z, 0, 0, 0);
            }
            if (e.age % 3 == 0) {
                spawn(FLARE, cx, cy + 2.8, cz).size(3.3f).color(VOID_DEEP).alpha(0.18f + 0.25f * ch).life(4).fade(0.3f, 0.3f);
            }
            if (R.nextFloat() < ch * 0.6f) {
                spawn(STREAK, cx + R.nextGaussian(), cy + 2.8 + R.nextGaussian(), cz + R.nextGaussian()).vel(R.nextGaussian() * 0.05, 0.05, R.nextGaussian() * 0.05)
                        .stretch(3f).size(0.08f).color(PALE).life(8);
            }
        }
        // накопление «Тени нимба»: свет стягивается к нимбу
        if (a == SeraelEntity.HALO && tk < 30) {
            double hy = cy + 6.3;
            for (int i = 0; i < 3; i++) {
                double ang = R.nextDouble() * Math.PI * 2, r = 3 + R.nextDouble() * 2;
                double x = cx + Math.cos(ang) * r, z = cz + Math.sin(ang) * r, y = hy + R.nextGaussian();
                spawn(GLINT, x, y, z).vel((cx - x) * 0.09, (hy - y) * 0.09, (cz - z) * 0.09).size(0.14f, 0.02f).colors(WHITE, GOLD).life(10);
            }
            spawn(FLARE, cx, hy, cz).size(0.8f + tk * 0.05f).color(GOLD).alpha(0.5f + 0.4f * MathHelper.sin(tk * 0.8f)).life(2).fade(0f, 0f);
        }
        // пылающие крылья при взлёте и пике
        if (a == SeraelEntity.DIVE && tk < 62) {
            for (int i = 0; i < 6; i++) {
                double side = R.nextBoolean() ? 1 : -1, span = 0.9 + R.nextDouble() * 2.8, up = 3.9 + R.nextDouble() * 2.8, back = -0.9 - R.nextDouble() * 0.8;
                double x = cx + lx * side * span + fx * back, z = cz + lz * side * span + fz * back;
                spawn(FLAME, x, cy + up, z).vel(-fx * 0.05, 0.05, -fz * 0.05).size(0.5f, 0.1f).colors(new float[] { 1f, 0.8f, 0.4f }, EMBER).life(12 + R.nextInt(8));
                if (i == 0) w.addParticle(ParticleTypes.LAVA, x, cy + up, z, 0, 0, 0);
            }
        }
        // оглушена: звёзды вокруг головы
        if (a == SeraelEntity.STUNNED && e.age % 4 == 0) {
            spawn(STAR, cx, cy, cz).orbit(R.nextDouble() * 6.28, 0.7, 0.25, 0, 4.9, 0).size(0.16f).color(GOLD).life(12);
        }
    }
}
