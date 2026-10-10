package com.bleid.vestments.client.fx;

import static com.bleid.vestments.client.fx.FxSystem.*;

import java.util.Random;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

/** Эффекты боя паладина: попадания, блоки щитом, ударные волны, приёмы мечей. */
@Environment(EnvType.CLIENT)
final class PaladinFx {
    private static final Random R = new Random();
    private static final float[] WHITE = { 1f, 1f, 1f };
    private static final float[] STEEL = { 0.78f, 0.86f, 1f };
    private static final float[] WARM = { 1f, 0.94f, 0.78f };
    private static final float[] GOLD = { 1f, 0.74f, 0.26f };
    private static final float[] DEEP = { 0.95f, 0.5f, 0.12f };
    private static final float[] DUST = { 0.32f, 0.27f, 0.2f };

    private PaladinFx() { }

    static boolean play(ClientWorld w, String type, Vec3d pos, Entity e, int dur, float a, float b, float c) {
        switch (type) {
            case "pal_hit" -> hit(pos, (int) a, b, c);
            case "pal_block" -> block(pos, (int) a, false);
            case "pal_parry" -> block(pos, (int) a, true);
            case "pal_shock" -> shock(w, pos, a, (int) b);
            case "pal_burst" -> burst(pos, a, (int) b);
            case "pal_launch" -> launch(pos, (int) a);
            case "pal_consecrate" -> consecrate(e, dur);
            case "pal_wave" -> wave(pos, dur, a, (int) b, c);
            case "pal_judgment" -> judgment(w, pos, a);
            case "pal_undying" -> undying(e);
            case "pal_aura" -> aura(e, a);
            default -> { return PaladinMagicFx.play(w, type, pos, e, dur, a, b, c); }
        }
        return true;
    }

    private static float rf(float lo, float hi) { return lo + R.nextFloat() * (hi - lo); }
    private static double ang() { return R.nextDouble() * Math.PI * 2; }

    private static void sparks(Vec3d p, int n, float speed, float[] c0, float[] c1, float size, int life, float grav) {
        for (int i = 0; i < n; i++) {
            double th = ang(), ph = Math.acos(2 * R.nextDouble() - 1);
            double s = speed * rf(0.4f, 1f);
            spawn(STREAK, p.x, p.y, p.z).vel(Math.sin(ph) * Math.cos(th) * s, Math.abs(Math.cos(ph)) * s * 0.8 + 0.05,
                            Math.sin(ph) * Math.sin(th) * s)
                    .gravity(grav).drag(0.9f).stretch(3.2f).size(size, size * 0.3f).colors(c0, c1)
                    .life(life + R.nextInt(Math.max(1, life / 2))).fade(0f, 0.5f);
        }
    }

    /** Попадание мечом: «разрез», вспышка, искры; с званием — кольцо и звёзды. */
    private static void hit(Vec3d p, int tier, float roll, float shake) {
        float[] col = tier >= 3 ? GOLD : STEEL;
        float[] core = tier >= 3 ? WARM : WHITE;
        float sz = 0.55f + tier * 0.13f;
        spawn(SLASH, p.x, p.y, p.z).size(sz * 0.8f, sz * 1.15f).ease().rot((float) Math.toRadians(roll + rf(-25, 25) + 180), 0)
                .color(core).life(5).fade(0f, 0.7f);
        spawn(SLASH, p.x, p.y, p.z).size(sz * 1.1f, sz * 1.5f).ease().rot((float) Math.toRadians(roll + rf(-25, 25)), 0)
                .color(col).alpha(0.7f).life(6).fade(0f, 0.8f);
        if (tier >= 2) spawn(FLARE, p.x, p.y, p.z).size(0.6f + tier * 0.2f, 0.2f).color(core).life(4).fade(0f, 1f);
        spawn(ORB, p.x, p.y, p.z).size(0.3f, 0.9f + tier * 0.15f).ease().color(col).alpha(0.6f).life(5).fade(0f, 1f);
        sparks(p, 6 + tier * 3, 0.28f + tier * 0.05f, core, col, 0.04f + tier * 0.006f, 8, 0.035f);
        if (tier >= 3) {
            spawn(SHOCK, p.x, p.y, p.z).size(0.2f, 0.9f + tier * 0.15f).ease().color(col).alpha(0.7f).life(7).fade(0f, 0.8f);
        }
        if (tier >= 4) {
            for (int i = 0; i < 6; i++) {
                double th = ang();
                spawn(GLINT, p.x, p.y, p.z).vel(Math.cos(th) * 0.12, rf(0.02f, 0.12f), Math.sin(th) * 0.12).drag(0.88f)
                        .size(0.16f, 0.02f).color(WARM).rot((float) ang(), 0.2f).life(14).fade(0f, 0.6f);
            }
        }
        if (tier >= 5) {
            spawn(STAR, p.x, p.y, p.z).size(0.4f, 1.6f).ease().rot(0, 0.25f).color(WARM).life(8).fade(0f, 0.9f);
        }
    }

    private static void block(Vec3d p, int rank, boolean parry) {
        float[] col = rank >= 3 ? GOLD : STEEL;
        spawn(FLARE, p.x, p.y, p.z).size(parry ? 1.6f : 0.8f, 0.2f).color(WHITE).life(parry ? 6 : 4).fade(0f, 1f);
        spawn(ORB, p.x, p.y, p.z).size(0.3f, parry ? 1.6f : 0.8f).ease().color(col).alpha(0.7f).life(6).fade(0f, 1f);
        sparks(p, parry ? 22 : 9, parry ? 0.4f : 0.25f, WHITE, col, 0.045f, 8, 0.04f);
        if (parry) {
            spawn(SHOCK, p.x, p.y, p.z).size(0.3f, 1.8f).ease().color(col).life(9).fade(0f, 0.8f);
            spawn(STAR, p.x, p.y, p.z).size(0.3f, 1.3f).ease().rot(0, 0.3f).color(WARM).life(8).fade(0f, 0.9f);
        }
    }

    /** Удар по земле: волна, трещины, пыль, обломки; у верхних званий — солнце и свет. */
    private static void shock(ClientWorld w, Vec3d p, float r, int tier) {
        float[] col = tier >= 3 ? GOLD : STEEL;
        spawn(SHOCK, p.x, p.y + 0.06, p.z).mode(Mode.FLAT).size(0.4f, r).ease().colors(WHITE, col).life(14).fade(0f, 0.7f);
        spawn(SHOCK, p.x, p.y + 0.07, p.z).mode(Mode.FLAT).size(0.3f, r * 0.7f).ease().color(col).alpha(0.6f).life(18).fade(0f, 0.7f).delay(3);
        spawn(CRACK, p.x, p.y + 0.05, p.z).mode(Mode.FLAT).size(r * 0.45f, r * 0.55f).ease().rot((float) ang(), 0)
                .colors(tier >= 3 ? WARM : STEEL, DEEP).alpha(0.9f).life(40).fade(0f, 0.6f);
        for (int i = 0; i < 16; i++) {
            double th = i * Math.PI * 2 / 16 + rf(-0.1f, 0.1f);
            spawn(SMOKE, p.x, p.y + 0.2, p.z).vel(Math.cos(th) * 0.22, 0.03, Math.sin(th) * 0.22).drag(0.86f)
                    .size(0.3f, 1.0f).rot((float) ang(), 0.03f).color(DUST).alpha(0.7f).life(26).fade(0.1f, 0.6f);
        }
        sparks(p.add(0, 0.2, 0), 14 + tier * 4, 0.35f, WARM, col, 0.05f, 12, 0.05f);
        if (tier >= 4) {
            spawn(SUNBURST, p.x, p.y + 0.08, p.z).mode(Mode.FLAT).size(0.5f, r * 0.8f).ease().rot((float) ang(), 0.05f)
                    .colors(WARM, GOLD).life(20).fade(0f, 0.7f);
            spawn(BEAM, p.x, p.y, p.z).mode(Mode.BEAM).height(5f).size(0.9f, 0.2f).color(WARM).life(10).fade(0f, 1f);
        }
        w.playSound(p.x, p.y, p.z, SoundEvents.BLOCK_STONE_BREAK, SoundCategory.PLAYERS, 1f, 0.6f, false);
    }

    private static void burst(Vec3d p, float r, int tier) {
        float[] col = tier >= 3 ? GOLD : STEEL;
        spawn(ORB, p.x, p.y, p.z).size(0.6f, r * 1.4f).ease().color(WARM).life(8).fade(0f, 1f);
        spawn(FLARE, p.x, p.y, p.z).size(r * 1.2f, 0.4f).color(WHITE).life(6).fade(0f, 1f);
        spawn(SUNBURST, p.x, p.y, p.z).size(0.5f, r * 0.9f).ease().rot((float) ang(), 0.15f).colors(WARM, col).life(12).fade(0f, 0.8f);
        spawn(SHOCK, p.x, p.y, p.z).size(0.4f, r).ease().colors(WHITE, col).life(10).fade(0f, 0.8f);
        sparks(p, 40, 0.6f, WHITE, col, 0.05f, 12, 0.02f);
        for (int i = 0; i < 10; i++) {
            double th = ang();
            spawn(GLINT, p.x, p.y, p.z).vel(Math.cos(th) * 0.18, rf(-0.05f, 0.15f), Math.sin(th) * 0.18).drag(0.88f)
                    .size(0.2f, 0.03f).color(WARM).rot((float) ang(), 0.2f).life(18).fade(0f, 0.6f);
        }
    }

    private static void launch(Vec3d p, int tier) {
        float[] col = tier >= 3 ? GOLD : STEEL;
        for (int i = 0; i < 24; i++) {
            spawn(STREAK, p.x + rf(-0.6f, 0.6f), p.y + 0.2, p.z + rf(-0.6f, 0.6f)).vel(rf(-0.05f, 0.05f), rf(0.4f, 0.8f), rf(-0.05f, 0.05f))
                    .drag(0.86f).stretch(5f).size(0.05f, 0.02f).colors(WARM, col).life(12).fade(0f, 0.5f);
        }
        spawn(SHOCK, p.x, p.y + 0.06, p.z).mode(Mode.FLAT).size(0.3f, 2.2f).ease().color(col).life(12).fade(0f, 0.7f);
    }

    /** «Освящённый клинок»: столп с неба, руны под ногами, огоньки вокруг на всё время действия. */
    private static void consecrate(Entity e, int dur) {
        if (e == null) return;
        float h = e.getHeight();
        spawn(BEAM, 0, 0, 0).mode(Mode.BEAM).followLocal(e).height(18f).size(0.3f, 1.4f).ease().colors(WARM, GOLD).life(22).fade(0f, 0.7f);
        spawn(BEAM, 0, 0, 0).mode(Mode.BEAM).followLocal(e).height(18f).size(0.35f).color(WHITE).life(14).fade(0f, 0.8f);
        spawn(SHOCK, e.getX(), e.getY() + 0.06, e.getZ()).mode(Mode.FLAT).size(0.4f, 3.5f).ease().colors(WHITE, GOLD).life(16).fade(0f, 0.7f);
        spawn(RUNES, 0, 0.06, 0).mode(Mode.FLAT).followLocal(e).size(0.3f, 1.5f).ease().rot(0, 0.04f).color(GOLD).alpha(0.8f)
                .life(dur).fade(0.05f, 0.1f).pulse(0.2f, 0.3f);
        for (int k = 0; k < 3; k++) {
            spawn(GLINT, 0, 0, 0).orbit(k * Math.PI * 2 / 3, 0.9, 0.12, 0, h * 0.6, 0).follow(e).size(0.16f).color(WARM)
                    .pulse(0.3f, 0.6f).life(dur).fade(0.05f, 0.1f);
        }
        task(age -> {
            if (age >= dur || e.isRemoved()) return false;
            if (age % 3 == 0) {
                double th = ang();
                spawn(ORB, Math.cos(th) * 0.7, 0.1, Math.sin(th) * 0.7).followLocal(e).vel(0, rf(0.03f, 0.06f), 0)
                        .size(0.07f, 0.01f).colors(WARM, GOLD).life(24).fade(0.2f, 0.5f);
            }
            return true;
        });
    }

    /** Волна света «Небесного разреза»: светящийся полумесяц летит вперёд. */
    private static void wave(Vec3d start, int dur, float yawDeg, int tier, float speed) {
        double yaw = Math.toRadians(yawDeg);
        Vec3d dir = new Vec3d(-Math.sin(yaw), 0, Math.cos(yaw));
        task(age -> {
            if (age > dur) return false;
            Vec3d p = start.add(dir.multiply(speed * age));
            spawn(SLASH, p.x, p.y + 0.3, p.z).size(1.6f, 2.0f).rot((float) Math.PI, 0).color(WARM).life(4).fade(0f, 1f);
            spawn(SLASH, p.x, p.y + 0.3, p.z).size(2.2f, 2.6f).rot((float) Math.PI, 0).color(GOLD).alpha(0.6f).life(5).fade(0f, 1f);
            spawn(SHOCK, p.x, p.y - 0.94, p.z).mode(Mode.FLAT).size(0.4f, 1.4f).ease().color(GOLD).alpha(0.5f).life(10).fade(0f, 0.8f);
            for (int i = 0; i < 4; i++) {
                spawn(STREAK, p.x + rf(-1f, 1f), p.y + rf(-0.6f, 1.1f), p.z + rf(-1f, 1f)).vel(dir.x * 0.4, 0.02, dir.z * 0.4)
                        .drag(0.85f).stretch(4f).size(0.05f).colors(WHITE, GOLD).life(8).fade(0f, 0.5f);
            }
            return true;
        });
    }

    /** «Суд небес»: столп света, руны, трещины, кольцо столпов, ударные волны, перья. */
    private static void judgment(ClientWorld w, Vec3d p, float r) {
        spawn(BEAM, p.x, p.y, p.z).mode(Mode.BEAM).height(40f).size(0.8f, 2.8f).ease().color(GOLD).life(30).fade(0f, 0.75f);
        spawn(BEAM, p.x, p.y, p.z).mode(Mode.BEAM).height(40f).size(0.4f, 1.2f).ease().color(WHITE).life(24).fade(0f, 0.7f);
        spawn(ORB, p.x, p.y + 1, p.z).size(1f, 8f).ease().color(WARM).life(9).fade(0f, 1f);
        spawn(FLARE, p.x, p.y + 1, p.z).size(8f, 1f).color(WHITE).life(7).fade(0f, 1f);
        spawn(SUNBURST, p.x, p.y + 0.08, p.z).mode(Mode.FLAT).size(1f, r * 1.3f).ease().rot((float) ang(), 0.03f).colors(WARM, GOLD).life(40).fade(0f, 0.7f);
        spawn(RUNES, p.x, p.y + 0.07, p.z).mode(Mode.FLAT).size(r * 0.5f, r).ease().rot((float) ang(), 0.02f).color(GOLD).life(50).fade(0.05f, 0.5f);
        spawn(CRACK, p.x, p.y + 0.05, p.z).mode(Mode.FLAT).size(r * 0.5f, r * 0.75f).ease().rot((float) ang(), 0).colors(WARM, DEEP).life(70).fade(0f, 0.5f);
        for (int k = 0; k < 3; k++) {
            spawn(SHOCK, p.x, p.y + 0.06 + k * 0.01, p.z).mode(Mode.FLAT).size(0.5f, r * (1.3f - k * 0.25f)).ease()
                    .colors(WHITE, GOLD).alpha(1f - k * 0.25f).life(18).fade(0f, 0.7f).delay(k * 5);
        }
        for (int k = 0; k < 8; k++) {
            double th = k * Math.PI / 4;
            double x = p.x + Math.cos(th) * r * 0.85, z = p.z + Math.sin(th) * r * 0.85;
            spawn(BEAM, x, p.y, z).mode(Mode.BEAM).height(14f).size(0.1f, 0.6f).ease().colors(WARM, GOLD).life(22).fade(0f, 0.7f).delay(4 + k);
            spawn(FLARE, x, p.y + 0.3, z).size(1.5f, 0.3f).color(WARM).life(6).fade(0f, 1f).delay(4 + k);
        }
        sparks(p.add(0, 0.3, 0), 80, 0.9f, WHITE, GOLD, 0.06f, 20, 0.04f);
        for (int i = 0; i < 24; i++) {
            double th = i * Math.PI * 2 / 24;
            spawn(SMOKE, p.x, p.y + 0.2, p.z).vel(Math.cos(th) * 0.35, 0.04, Math.sin(th) * 0.35).drag(0.88f)
                    .size(0.4f, 1.4f).rot((float) ang(), 0.03f).color(DUST).alpha(0.7f).life(30).fade(0.1f, 0.6f);
        }
        for (int i = 0; i < 26; i++) {
            double th = ang(), d = Math.sqrt(R.nextDouble()) * r;
            spawn(FEATHER, p.x + Math.cos(th) * d, p.y, p.z + Math.sin(th) * d)
                    .orbit(ang(), rf(0.15f, 0.4f), rf(0.06f, 0.12f) * (R.nextBoolean() ? 1 : -1), 0, rf(5f, 9f), -rf(0.03f, 0.05f))
                    .size(rf(0.14f, 0.22f)).rot((float) ang(), rf(-0.06f, 0.06f)).color(WARM).alpha(0.9f).life(70).fade(0.15f, 0.35f)
                    .delay(6 + R.nextInt(14));
        }
        w.playSound(p.x, p.y, p.z, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.5f, 0.5f, false);
    }

    private static void undying(Entity e) {
        if (e == null) return;
        float h = e.getHeight();
        spawn(BUBBLE, 0, h * 0.5, 0).followLocal(e).size(0.5f, 1.6f).ease().color(GOLD).alpha(0.7f).life(24).fade(0f, 0.7f);
        spawn(FLARE, 0, h * 0.6, 0).followLocal(e).size(3f, 0.5f).color(WHITE).life(8).fade(0f, 1f);
        for (int i = 0; i < 16; i++) {
            for (int k = 0; k < 2; k++) {
                spawn(GLINT, 0, 0, 0).orbit(i * 0.5 + k * Math.PI, 1.0, 0.25, -0.01, 0.1 + i * 0.03, h / 14.0).follow(e)
                        .size(0.16f, 0.02f).colors(WARM, GOLD).life(22).fade(0.1f, 0.5f).delay(i / 2);
            }
        }
    }

    private static void aura(Entity e, float r) {
        if (e == null) return;
        spawn(RING, e.getX(), e.getY() + 0.06, e.getZ()).mode(Mode.FLAT).size(0.4f, r).ease().colors(WARM, GOLD)
                .alpha(0.6f).life(18).fade(0f, 0.7f);
    }
}
