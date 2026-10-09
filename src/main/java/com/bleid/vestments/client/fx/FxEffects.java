package com.bleid.vestments.client.fx;

import static com.bleid.vestments.client.fx.FxSystem.*;

import com.bleid.vestments.fx.Fx;
import java.util.Random;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

/** Постановка эффектов способностей: что, где, когда и как красиво светится. */
@Environment(EnvType.CLIENT)
public final class FxEffects {
    private static final float[] WHITE = { 1f, 1f, 1f };
    private static final float[] WARM = { 1f, 0.94f, 0.78f };
    private static final float[] GOLD = { 1f, 0.74f, 0.26f };
    private static final float[] DEEP_GOLD = { 0.95f, 0.52f, 0.12f };
    private static final float[] ROSE = { 1f, 0.5f, 0.62f };
    private static final float[] FIRE = { 1f, 0.42f, 0.1f };
    private static final float[] SKY = { 0.55f, 0.8f, 1f };
    private static final float[] ICE = { 0.62f, 0.92f, 1f };
    private static final float[] LIFE_GREEN = { 0.62f, 1f, 0.55f };
    private static final float[] SMOKE_C = { 0.42f, 0.36f, 0.26f };

    private static final Random R = new Random();

    private FxEffects() { }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(Fx.PACKET, (client, handler, buf, sender) -> {
            String type = buf.readString();
            double x = buf.readDouble(), y = buf.readDouble(), z = buf.readDouble();
            int id = buf.readVarInt(), dur = buf.readVarInt();
            float a = buf.readFloat(), b = buf.readFloat(), c = buf.readFloat();
            client.execute(() -> {
                if (client.world == null) return;
                Entity e = id >= 0 ? client.world.getEntityById(id) : null;
                play(client.world, type, new Vec3d(x, y, z), e, dur, a, b, c);
            });
        });
    }

    private static void play(ClientWorld w, String type, Vec3d pos, Entity e, int dur, float a, float b, float c) {
        switch (type) {
            case "consolation" -> consolation(w, pos, e, a);
            case "heal" -> heal(e, false);
            case "heal_small" -> heal(e, true);
            case "candle" -> candle(w, pos, e, a);
            case "candle_hit" -> candleHit(e);
            case "fortitude" -> fortitude(w, e, dur);
            case "seal" -> seal(w, pos, a, dur);
            case "cleanse" -> cleanse(e);
            case "blinding" -> blinding(w, pos, e, a);
            case "frozen" -> frozen(w, e, dur);
            case "heavenly_light" -> heavenlyLight(w, pos, a);
            case "smite" -> smite(e, dur);
            case "smite_small" -> smiteSmall(e);
            case "veil" -> veil(e, a, dur, b > 0.5f);
            case "wave" -> wave(w, pos, a, b, c, dur);
            case "light_start" -> lightStart(e);
            case "light_end" -> lightEnd(e);
            default -> PaladinFx.play(w, type, pos, e, dur, a, b, c);
        }
    }

    // ================================================================ утилиты

    private static float rf(float lo, float hi) { return lo + R.nextFloat() * (hi - lo); }
    private static double ang() { return R.nextDouble() * Math.PI * 2; }

    private static void sound(ClientWorld w, Vec3d p, SoundEvent s, float vol, float pitch) {
        w.playSound(p.x, p.y, p.z, s, SoundCategory.PLAYERS, vol, pitch, false);
    }

    private static void soundLater(ClientWorld w, Vec3d p, int delay, SoundEvent s, float vol, float pitch) {
        task(age -> {
            if (age < delay) return true;
            sound(w, p, s, vol, pitch);
            return false;
        });
    }

    /** Плоский спрайт на земле. */
    private static P flat(int sprite, Vec3d p, double yOff) {
        return spawn(sprite, p.x, p.y + yOff, p.z).mode(Mode.FLAT);
    }

    private static P flatOn(int sprite, Entity e, Vec3d p, double yOff) {
        P q = flat(sprite, p, yOff);
        return e != null ? q.follow(e) : q;
    }

    /** Ударная волна по земле. */
    private static void shock(Entity e, Vec3d p, float r0, float r1, int life, float[] c0, float[] c1, float alpha, int delay) {
        flatOn(SHOCK, e, p, 0.06).size(r0, r1).ease().colors(c0, c1).life(life).fade(0f, 0.65f).alpha(alpha).delay(delay);
    }

    /** Вспышка-шар. */
    private static P flash(double x, double y, double z, float s0, float s1, int life, float[] c) {
        return spawn(ORB, x, y, z).size(s0, s1).ease().color(c).life(life).fade(0f, 1f);
    }

    /** Знак, который сначала разворачивается до размера size, затем держится hold тиков. */
    private static void growHold(int sprite, Entity e, Vec3d p, double yOff, float size, int grow, int hold, float spin,
                                 float[] col, float alpha, float pulse, int delay) {
        float rot0 = (float) ang();
        flatOn(sprite, e, p, yOff).size(0.1f, size).ease().rot(rot0, spin + 0.08f).color(col).alpha(alpha)
                .life(grow).fade(0.4f, 0f).delay(delay);
        P h = flatOn(sprite, e, p, yOff).size(size).rot(rot0 + grow * (spin + 0.08f) * 0.75f, spin).color(col).alpha(alpha)
                .life(hold).fade(0f, 0.12f).delay(delay + grow);
        if (pulse > 0) h.pulse(pulse, 0.18f);
    }

    /** Разлёт искр из точки. */
    private static void burst(int sprite, double x, double y, double z, int n, float speed, float[] c0, float[] c1,
                              float size, int life, float gravity, float drag, int delay, boolean streak) {
        for (int i = 0; i < n; i++) {
            double th = ang(), ph = Math.acos(2 * R.nextDouble() - 1);
            double s = speed * rf(0.5f, 1f);
            double vx = Math.sin(ph) * Math.cos(th) * s, vy = Math.cos(ph) * s, vz = Math.sin(ph) * Math.sin(th) * s;
            P p = spawn(sprite, x, y, z).vel(vx, vy, vz).drag(drag).gravity(gravity).size(size, size * 0.15f)
                    .colors(c0, c1).life(life + R.nextInt(Math.max(1, life / 2))).fade(0f, 0.5f).delay(delay)
                    .rot((float) ang(), rf(-0.2f, 0.2f));
            if (streak) p.stretch(3.5f);
        }
    }

    /** Падающие с кружением перья. */
    private static void feathers(Vec3d c, double radius, int n, double y0, double y1, float[] col, int delay) {
        for (int i = 0; i < n; i++) {
            double t = ang(), d = Math.sqrt(R.nextDouble()) * radius;
            double y = c.y + y0 + R.nextDouble() * (y1 - y0);
            spawn(FEATHER, c.x + Math.cos(t) * d, c.y, c.z + Math.sin(t) * d)
                    .orbit(ang(), rf(0.15f, 0.4f), rf(0.06f, 0.12f) * (R.nextBoolean() ? 1 : -1), 0, y - c.y, -rf(0.025f, 0.045f))
                    .size(rf(0.12f, 0.2f)).rot((float) ang(), rf(-0.06f, 0.06f)).color(col).alpha(0.9f)
                    .life(50 + R.nextInt(30)).fade(0.15f, 0.35f).delay(delay + R.nextInt(14));
        }
    }

    private static float height(Entity e) {
        return e != null ? e.getHeight() : 1.8f;
    }

    // ================================================================ I. Утешение

    private static void consolation(ClientWorld w, Vec3d p, Entity e, float r) {
        float h = height(e);
        growHold(SUNBURST, e, p, 0.07, 2.4f, 8, 26, 0.04f, ROSE, 0.85f, 0f, 0);
        shock(e, p, 0.4f, r, 18, WHITE, ROSE, 1f, 0);
        shock(e, p, 0.3f, r * 0.8f, 20, WARM, GOLD, 0.6f, 5);
        flatOn(RING, e, p, 0.05).size(r).rot(0, 0.02f).color(GOLD).alpha(0.75f).pulse(0.35f, 0.5f).life(46).fade(0.2f, 0.45f);
        // две спирали искр поднимаются вокруг священника
        task(age -> {
            if (age >= 22) return false;
            for (int k = 0; k < 2; k++) {
                P q = spawn(GLINT, p.x, p.y, p.z).orbit(age * 0.55 + k * Math.PI, 0.95, 0.2, -0.012, 0.1, 0.075)
                        .size(0.14f, 0.02f).colors(WARM, ROSE).life(26).fade(0.1f, 0.5f);
                if (e != null) q.follow(e);
            }
            // светлые пылинки по всему кругу
            for (int k = 0; k < 3; k++) {
                double t = ang(), d = Math.sqrt(R.nextDouble()) * r;
                spawn(ORB, p.x + Math.cos(t) * d, p.y + 0.1, p.z + Math.sin(t) * d).vel(0, rf(0.02f, 0.05f), 0)
                        .size(rf(0.05f, 0.09f)).colors(ROSE, GOLD).life(30).fade(0.2f, 0.6f);
            }
            return true;
        });
        feathers(p, r * 0.8, 22, 2.8, 4.2, WARM, 2);
        P heart = spawn(HEART, 0, h + 0.5, 0).vel(0, 0.015, 0).size(0.05f, 0.4f).ease().color(ROSE).life(34).fade(0.1f, 0.5f);
        if (e != null) heart.followLocal(e); else { heart.x += p.x; heart.y += p.y; heart.z += p.z; heart.px = heart.x; heart.py = heart.y; heart.pz = heart.z; }
        sound(w, p, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.2f, 1.6f);
        soundLater(w, p, 6, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 2.0f);
    }

    /** Исцеление на игроке: двойная спираль, сердечки и вспышка креста над головой. */
    private static void heal(Entity e, boolean small) {
        if (e == null) return;
        float h = height(e), w = Math.max(0.45f, e.getWidth() * 0.8f);
        int n = small ? 6 : 12;
        for (int i = 0; i < n; i++) {
            for (int k = 0; k < 2; k++) {
                spawn(GLINT, 0, 0, 0).orbit(i * 0.55 + k * Math.PI, w, 0.24, 0, 0.1 + i * 0.02, h / 18.0)
                        .follow(e).size(0.12f, 0.02f).colors(WARM, ROSE).life(20).fade(0.1f, 0.5f).delay(i);
            }
        }
        int hearts = small ? 1 : 3;
        for (int k = 0; k < hearts; k++) {
            spawn(HEART, rf(-0.3f, 0.3f), h * rf(0.6f, 0.9f), rf(-0.3f, 0.3f)).followLocal(e).vel(0, 0.035, 0)
                    .size(0.06f, small ? 0.12f : 0.18f).ease().color(ROSE).life(24).fade(0.1f, 0.5f).delay(k * 4);
        }
        if (!small) {
            spawn(CROSS, 0, h + 0.55, 0).followLocal(e).vel(0, 0.01, 0).size(0.05f, 0.3f).ease().color(GOLD).life(26).fade(0.1f, 0.55f).delay(3);
            spawn(ORB, 0, h + 0.55, 0).followLocal(e).size(0.2f, 0.7f).ease().color(WARM).alpha(0.6f).life(14).fade(0f, 1f).delay(3);
        }
    }

    // ================================================================ I. Свеча веры

    private static final int CANDLE_DELAY = 10;

    private static void candle(ClientWorld w, Vec3d p, Entity e, float r) {
        if (e == null) e = w.getClosestPlayer(p.x, p.y, p.z, 1.5, false);
        if (e == null) return;
        float h = height(e);
        // 1) над головой разгорается свеча, к ней стягиваются огоньки
        spawn(FLAME, 0, h + 0.75, 0).followLocal(e).size(0.05f, 0.5f).ease().colors(GOLD, FIRE).pulse(0.25f, 1.4f)
                .life(CANDLE_DELAY + 2).fade(0.3f, 0.15f);
        spawn(ORB, 0, h + 0.65, 0).followLocal(e).size(0.1f, 0.45f).ease().color(WARM).life(CANDLE_DELAY + 2).fade(0.3f, 0.2f);
        final Entity fe = e;
        task(age -> {
            if (age >= CANDLE_DELAY) return false;
            for (int k = 0; k < 4; k++) {
                double t = ang(), ph = R.nextDouble() * Math.PI, d = 2.6;
                double ox = Math.sin(ph) * Math.cos(t) * d, oy = Math.cos(ph) * d * 0.6, oz = Math.sin(ph) * Math.sin(t) * d;
                P q = spawn(STREAK, ox, h + 0.7 + oy, oz).vel(-ox / 9, -oy / 9, -oz / 9).stretch(3f).size(0.05f, 0.03f)
                        .colors(FIRE, GOLD).life(9).fade(0.2f, 0.2f);
                q.followLocal(fe);
            }
            return true;
        });
        // 2) вспышка: огненная волна по земле, пламя и искры во все стороны
        Vec3d c = e != null ? e.getPos() : p;
        double cy = c.y + h * 0.6;
        int d = CANDLE_DELAY;
        flash(c.x, c.y + h + 0.7, c.z, 1f, 4.5f, 8, WARM).delay(d);
        flash(c.x, cy, c.z, 0.8f, 3f, 12, GOLD).alpha(0.7f).delay(d);
        shock(null, c, 0.5f, r, 16, WARM, FIRE, 1f, d);
        shock(null, c, 0.4f, r * 0.75f, 20, GOLD, DEEP_GOLD, 0.6f, d + 3);
        flat(SUNBURST, c, 0.07).size(1f, r * 0.65f).ease().rot((float) ang(), 0.05f).colors(GOLD, FIRE).life(24).fade(0f, 0.7f).delay(d);
        spawn(BEAM, c.x, c.y, c.z).mode(Mode.BEAM).height(6f).size(1.1f, 0.2f).color(WARM).life(12).fade(0f, 1f).delay(d);
        for (int k = 0; k < 48; k++) {
            double t = k * Math.PI * 2 / 48 + rf(-0.05f, 0.05f);
            double s = rf(0.38f, 0.55f);
            spawn(FLAME, c.x, cy, c.z).vel(Math.cos(t) * s, rf(0.0f, 0.06f), Math.sin(t) * s).drag(0.87f).size(0.28f, 0.06f)
                    .colors(GOLD, FIRE).life(15 + R.nextInt(8)).fade(0f, 0.5f).delay(d);
        }
        burst(STREAK, c.x, cy, c.z, 44, 0.75f, WARM, FIRE, 0.06f, 14, 0.02f, 0.92f, d, true);
        burst(ORB, c.x, cy, c.z, 24, 0.25f, GOLD, FIRE, 0.09f, 24, -0.004f, 0.93f, d, false);
        soundLater(w, c, d, SoundEvents.ENTITY_BLAZE_SHOOT, 0.7f, 1.3f);
        soundLater(w, c, d, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.2f, 0.8f);
    }

    private static void candleHit(Entity e) {
        if (e == null) return;
        float h = height(e), wd = Math.max(0.3f, e.getWidth() * 0.6f);
        int d = CANDLE_DELAY + 2;
        for (int k = 0; k < 10; k++) {
            spawn(FLAME, rf(-wd, wd), h * rf(0.1f, 0.8f), rf(-wd, wd)).followLocal(e).vel(0, rf(0.03f, 0.07f), 0)
                    .size(rf(0.12f, 0.2f), 0.02f).colors(GOLD, FIRE).life(16 + R.nextInt(8)).fade(0.1f, 0.5f).delay(d + R.nextInt(6));
        }
        spawn(GLINT, 0, h * 0.6, 0).followLocal(e).size(0.3f, 0.9f).ease().color(WARM).life(9).fade(0f, 1f).delay(d);
    }

    // ================================================================ II. Благословение стойкости

    private static void fortitude(ClientWorld w, Entity e, int dur) {
        if (e == null) return;
        float h = height(e);
        Vec3d p = e.getPos();
        // столп света с неба на цель
        spawn(BEAM, 0, 0, 0).mode(Mode.BEAM).followLocal(e).height(16f).size(0.15f, 1.2f).ease().colors(WARM, GOLD).life(18).fade(0f, 0.6f);
        spawn(BEAM, 0, 0, 0).mode(Mode.BEAM).followLocal(e).height(16f).size(0.35f).color(WHITE).life(12).fade(0f, 0.8f);
        for (int k = 0; k < 24; k++) {
            spawn(STREAK, rf(-0.5f, 0.5f), rf(3f, 14f), rf(-0.5f, 0.5f)).followLocal(e).vel(0, -rf(0.6f, 0.9f), 0).stretch(5f)
                    .size(0.05f).color(WARM).life(14).fade(0f, 0.3f).delay(R.nextInt(4));
        }
        shock(e, p, 0.3f, 3.2f, 16, WARM, GOLD, 1f, 2);
        flash(0, h * 0.55, 0, 0.6f, 2.6f, 8, WARM).followLocal(e).delay(2);
        // золотой купол-щит, руны под ногами и три креста на орбите
        float sz = Math.max(h, e.getWidth()) * 0.85f;
        spawn(BUBBLE, 0, h * 0.5, 0).followLocal(e).size(sz * 0.6f, sz).ease().color(GOLD).alpha(0.5f).pulse(0.25f, 0.25f)
                .life(dur).fade(0.05f, 0.06f).delay(2);
        spawn(BUBBLE, 0, h * 0.5, 0).followLocal(e).size(sz * 0.9f).rot(0, 0.03f).color(WARM).alpha(0.18f).life(dur).fade(0.1f, 0.06f).delay(2);
        flatOn(RUNES, e, p, 0.06).size(0.2f, 1.35f).ease().rot(0, 0.05f).color(GOLD).alpha(0.85f).life(dur).fade(0.05f, 0.1f).delay(2);
        for (int k = 0; k < 3; k++) {
            spawn(CROSS, 0, 0, 0).orbit(k * Math.PI * 2 / 3, Math.max(0.95, e.getWidth() + 0.4), 0.07, 0, h * 0.55, 0)
                    .follow(e).size(0.22f).color(GOLD).alpha(0.95f).pulse(0.25f, 0.3f).life(dur).fade(0.08f, 0.08f).delay(4);
        }
        task(age -> {
            if (e.isRemoved()) return false;
            if (age < dur && age % 3 == 0) {
                double t = ang();
                spawn(GLINT, Math.cos(t) * 0.9, rf(0f, 0.4f), Math.sin(t) * 0.9).followLocal(e).vel(0, rf(0.03f, 0.06f), 0)
                        .size(0.11f, 0.02f).colors(WARM, GOLD).life(22).fade(0.15f, 0.5f);
            }
            if (age == dur) {          // щит рассыпается золотыми искрами
                burst(STAR, e.getX(), e.getY() + h * 0.55, e.getZ(), 34, 0.3f, WARM, GOLD, 0.16f, 18, 0.006f, 0.9f, 0, false);
                flash(e.getX(), e.getY() + h * 0.55, e.getZ(), 0.8f, 2.6f, 7, WARM);
                sound(w, e.getPos(), SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK, 1f, 1.2f);
                return false;
            }
            return true;
        });
        sound(w, p, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.2f, 1.2f);
    }

    // ================================================================ II. Печать чистоты

    private static void seal(ClientWorld w, Vec3d p, float r, int dur) {
        spawn(BEAM, p.x, p.y, p.z).mode(Mode.BEAM).height(18f).size(0.3f, 1.5f).ease().colors(WARM, GOLD).life(24).fade(0f, 0.7f);
        spawn(BEAM, p.x, p.y, p.z).mode(Mode.BEAM).height(18f).size(0.4f).color(WHITE).life(14).fade(0f, 0.8f);
        flash(p.x, p.y + 1, p.z, 1f, 4f, 8, WARM);
        shock(null, p, 0.5f, r, 18, WARM, GOLD, 1f, 0);
        int hold = Math.max(20, dur - 16);
        growHold(RUNES, null, p, 0.06, r, 16, hold, 0.012f, GOLD, 0.95f, 0.15f, 0);
        growHold(RUNES, null, p, 0.08, r * 0.55f, 16, hold, -0.022f, WARM, 0.55f, 0f, 2);
        growHold(SUNBURST, null, p, 0.07, r * 0.42f, 16, hold, 0.03f, GOLD, 0.45f, 0.2f, 0);
        flat(RING, p, 0.05).size(r * 1.04f).color(GOLD).alpha(0.8f).pulse(0.2f, 0.3f).life(dur).fade(0.08f, 0.1f);
        task(age -> {
            if (age >= dur) {          // печать гаснет — искры поднимаются от круга
                for (int k = 0; k < 40; k++) {
                    double t = ang();
                    spawn(STAR, p.x + Math.cos(t) * r, p.y + 0.1, p.z + Math.sin(t) * r).vel(0, rf(0.05f, 0.12f), 0).drag(0.96f)
                            .size(0.14f, 0.02f).colors(WARM, GOLD).life(24 + R.nextInt(10)).fade(0f, 0.6f);
                }
                return false;
            }
            if (age % 2 == 0) {
                for (int k = 0; k < 2; k++) {
                    double t = ang();
                    spawn(GLINT, p.x + Math.cos(t) * r, p.y + 0.1, p.z + Math.sin(t) * r).vel(0, rf(0.03f, 0.06f), 0)
                            .size(0.12f, 0.02f).colors(WARM, GOLD).life(28).fade(0.15f, 0.5f);
                }
            }
            double t = ang(), d = Math.sqrt(R.nextDouble()) * r;
            spawn(ORB, p.x + Math.cos(t) * d, p.y + 0.1, p.z + Math.sin(t) * d).vel(0, rf(0.015f, 0.03f), 0)
                    .size(rf(0.04f, 0.08f)).color(WARM).alpha(0.7f).life(40).fade(0.3f, 0.5f);
            if (age % 4 == 0) {        // кометы бегут по кругу печати
                spawn(STREAK, p.x, p.y, p.z).orbit(ang(), r, 0.09, 0, 0.12, 0).stretch(4f).size(0.07f)
                        .colors(WARM, GOLD).life(22).fade(0.2f, 0.4f);
            }
            if (age > 0 && age % 20 == 0) shock(null, p, 0.3f, r, 20, GOLD, DEEP_GOLD, 0.5f, 0);
            return true;
        });
    }

    private static void cleanse(Entity e) {
        if (e == null) return;
        float h = height(e);
        for (int i = 0; i < 10; i++) {
            spawn(GLINT, 0, 0, 0).orbit(i * 0.63, 0.6, 0.28, 0, 0.1 + i * 0.03, h / 16.0).follow(e)
                    .size(0.12f, 0.02f).colors(WARM, LIFE_GREEN).life(18).fade(0.1f, 0.5f).delay(i);
        }
        spawn(STAR, 0, h + 0.3, 0).followLocal(e).size(0.1f, 0.5f).ease().color(LIFE_GREEN).life(14).fade(0f, 0.8f).delay(4);
    }

    // ================================================================ III. Ослепление

    private static void blinding(ClientWorld w, Vec3d p, Entity e, float r) {
        float h = height(e);
        Vec3d c = e != null ? e.getPos() : p;
        double ey = c.y + h * 0.9;
        flash(c.x, ey, c.z, 0.5f, 8f, 10, WHITE);
        flash(c.x, ey, c.z, 0.3f, 3.5f, 16, SKY).alpha(0.8f);
        spawn(STAR, c.x, ey, c.z).size(0.5f, 3f).ease().rot(0, 0.15f).color(WHITE).life(12).fade(0f, 0.9f);
        flat(SUNBURST, c, 0.07).size(0.5f, r * 0.6f).ease().rot((float) ang(), 0.05f).colors(WHITE, SKY).life(28).fade(0f, 0.7f);
        shock(null, c, 0.5f, r, 22, WHITE, ICE, 1f, 0);
        shock(null, c, 0.5f, r * 0.75f, 22, SKY, ICE, 0.6f, 5);
        shock(null, c, 0.5f, r * 0.45f, 22, WHITE, SKY, 0.4f, 10);
        for (int k = 0; k < 100; k++) {
            double t = ang();
            double vy = rf(-0.12f, 0.3f), s = rf(1.0f, 1.6f);
            spawn(STREAK, c.x, ey - 0.3, c.z).vel(Math.cos(t) * s, vy, Math.sin(t) * s).drag(0.86f).stretch(6f)
                    .size(0.06f, 0.02f).colors(WHITE, ICE).life(14 + R.nextInt(8)).fade(0f, 0.5f);
        }
        for (int k = 0; k < 44; k++) {
            double t = ang(), s = rf(0.2f, 0.45f);
            spawn(SNOW, c.x, ey - 0.4, c.z).vel(Math.cos(t) * s, rf(0f, 0.12f), Math.sin(t) * s).drag(0.9f).gravity(0.003f)
                    .size(rf(0.12f, 0.2f), 0.04f).rot((float) ang(), rf(-0.2f, 0.2f)).colors(WHITE, ICE)
                    .life(30 + R.nextInt(25)).fade(0f, 0.5f);
        }
        sound(w, c, SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK, 1.2f, 0.7f);
    }

    /** Заморозка: ледяная оболочка, кружащиеся снежинки, по окончании — осколки. */
    private static void frozen(ClientWorld w, Entity e, int dur) {
        if (e == null) return;
        float h = height(e), wd = e.getWidth();
        float sz = Math.max(h, wd) * 0.75f;
        spawn(BUBBLE, 0, h * 0.5, 0).followLocal(e).size(sz * 0.4f, sz).ease().color(ICE).alpha(0.5f).pulse(0.15f, 0.2f)
                .life(dur).fade(0.06f, 0.05f);
        spawn(ORB, 0, h * 0.5, 0).followLocal(e).size(sz * 1.3f).color(SKY).alpha(0.25f).life(dur).fade(0.1f, 0.1f);
        for (int k = 0; k < 4; k++) {
            spawn(SNOW, 0, 0, 0).orbit(k * Math.PI / 2, wd * 0.6 + 0.35, 0.05, 0, h * rf(0.2f, 0.9f), 0).follow(e)
                    .size(0.14f).rot((float) ang(), 0.05f).color(ICE).alpha(0.9f).life(dur).fade(0.1f, 0.08f);
        }
        task(age -> {
            if (e.isRemoved()) return false;
            if (age < dur && age % 6 == 0) {
                spawn(SNOW, rf(-wd, wd) * 0.6, h + 0.4, rf(-wd, wd) * 0.6).followLocal(e).vel(0, -0.03, 0)
                        .size(0.08f, 0.03f).rot((float) ang(), 0.1f).color(ICE).life(24).fade(0.2f, 0.4f);
            }
            if (age == dur) {
                burst(STAR, e.getX(), e.getY() + h * 0.5, e.getZ(), 22, 0.28f, WHITE, ICE, 0.12f, 14, 0.02f, 0.9f, 0, false);
                burst(SNOW, e.getX(), e.getY() + h * 0.5, e.getZ(), 10, 0.2f, ICE, SKY, 0.12f, 20, 0.01f, 0.92f, 0, false);
                sound(w, e.getPos(), SoundEvents.BLOCK_GLASS_BREAK, 0.5f, 1.5f);
                return false;
            }
            return true;
        });
    }

    // ================================================================ III. Небесный свет

    private static final int LIGHT_DELAY = 6;

    private static void heavenlyLight(ClientWorld w, Vec3d p, float r) {
        int d = LIGHT_DELAY;
        // предвестие: тонкий луч и падающие с неба штрихи света
        spawn(BEAM, p.x, p.y, p.z).mode(Mode.BEAM).height(34f).size(0.04f, 0.3f).color(WARM).life(d + 2).fade(0.5f, 0f);
        for (int k = 0; k < 28; k++) {
            spawn(STREAK, p.x + rf(-0.5f, 0.5f), p.y + rf(8f, 30f), p.z + rf(-0.5f, 0.5f)).vel(0, -rf(1.6f, 2.4f), 0)
                    .stretch(7f).size(0.06f).color(WARM).life(10).fade(0f, 0.3f).delay(R.nextInt(d));
        }
        // удар
        spawn(BEAM, p.x, p.y, p.z).mode(Mode.BEAM).height(36f).size(0.6f, 2.4f).ease().color(GOLD).alpha(0.95f).life(28).fade(0f, 0.75f).delay(d);
        spawn(BEAM, p.x, p.y, p.z).mode(Mode.BEAM).height(36f).size(0.3f, 0.9f).ease().color(WHITE).life(22).fade(0f, 0.7f).delay(d);
        flash(p.x, p.y + 1, p.z, 1f, 7f, 8, WARM).delay(d);
        flat(SUNBURST, p, 0.07).size(1f, r * 1.5f).ease().rot((float) ang(), 0.03f).colors(WARM, GOLD).life(36).fade(0f, 0.7f).delay(d);
        flat(RUNES, p, 0.06).size(r * 0.5f, r * 1.05f).ease().rot((float) ang(), 0.02f).color(GOLD).alpha(0.95f).life(48).fade(0.1f, 0.5f).delay(d);
        shock(null, p, 0.5f, r * 1.9f, 16, WHITE, GOLD, 1f, d);
        shock(null, p, 0.5f, r * 1.3f, 20, WARM, DEEP_GOLD, 0.6f, d + 8);
        for (int k = 0; k < 64; k++) {
            double t = ang(), s = rf(0.45f, 0.9f), vy = rf(0.25f, 0.9f);
            spawn(STREAK, p.x, p.y + 0.3, p.z).vel(Math.cos(t) * s, vy, Math.sin(t) * s).drag(0.95f).gravity(0.04f)
                    .stretch(4f).size(0.05f, 0.02f).colors(WARM, GOLD).life(18 + R.nextInt(12)).fade(0f, 0.5f).delay(d);
        }
        feathers(p, r * 0.9, 28, 5, 9, WARM, d + 4);
        task(age -> {
            if (age < d) return true;
            if (age >= d + 32) return false;
            for (int k = 0; k < 3; k++) {
                double t = ang(), dd = Math.sqrt(R.nextDouble()) * 1.3;
                spawn(ORB, p.x + Math.cos(t) * dd, p.y + 0.2, p.z + Math.sin(t) * dd).vel(0, rf(0.1f, 0.2f), 0)
                        .size(rf(0.05f, 0.09f)).color(WARM).life(22).fade(0.2f, 0.5f);
            }
            return true;
        });
        soundLater(w, p, d, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.5f, 0.6f);
    }

    /** Удар светом по врагу. dur — задержка, чтобы совпасть с основным эффектом. */
    private static void smite(Entity e, int delay) {
        if (e == null) return;
        float h = height(e);
        spawn(GLINT, 0, h * 0.6, 0).followLocal(e).size(0.3f, 1.1f).ease().color(WARM).life(9).fade(0f, 1f).delay(delay);
        for (int k = 0; k < 14; k++) {
            double t = ang(), s = rf(0.15f, 0.3f);
            spawn(STREAK, 0, h * 0.6, 0).followLocal(e).vel(Math.cos(t) * s, rf(0.05f, 0.25f), Math.sin(t) * s).drag(0.9f)
                    .gravity(0.02f).stretch(3f).size(0.04f).colors(WARM, GOLD).life(12).fade(0f, 0.5f).delay(delay);
        }
    }

    private static void smiteSmall(Entity e) {
        if (e == null) return;
        float h = height(e);
        for (int k = 0; k < 4; k++) {
            spawn(FLAME, rf(-0.3f, 0.3f), h * rf(0.2f, 0.7f), rf(-0.3f, 0.3f)).followLocal(e).vel(0, 0.05, 0)
                    .size(0.14f, 0.02f).colors(GOLD, FIRE).life(14).fade(0.1f, 0.5f).delay(R.nextInt(4));
        }
        spawn(GLINT, 0, h * 0.6, 0).followLocal(e).size(0.2f, 0.5f).ease().color(WARM).life(7).fade(0f, 1f);
    }

    // ================================================================ Посохи: Молитвенный покров

    private static void veil(Entity e, float r, int dur, boolean rich) {
        if (e == null) return;
        Vec3d p = e.getPos();
        shock(e, p, 0.3f, r, 16, WARM, GOLD, 1f, 0);
        flatOn(SUNBURST, e, p, 0.07).size(0.4f, r * 0.7f).ease().rot(0, 0.06f).colors(WARM, GOLD).life(14).fade(0f, 0.8f);
        flatOn(RING, e, p, 0.05).size(r).rot(0, 0.02f).color(GOLD).alpha(0.8f).pulse(0.2f, 0.3f).life(dur).fade(0.06f, 0.12f);
        if (rich) {
            growHold(RUNES, e, p, 0.06, r * 0.97f, 12, Math.max(10, dur - 12), -0.012f, GOLD, 0.6f, 0.15f, 0);
        }
        int comets = rich ? 6 : 4;
        for (int k = 0; k < comets; k++) {
            spawn(ORB, 0, 0, 0).orbit(k * Math.PI * 2 / comets, r, 0.07, 0, 0.2, 0).follow(e).size(0.13f).color(GOLD)
                    .pulse(0.3f, 0.5f).life(dur).fade(0.06f, 0.12f);
        }
        task(age -> {
            if (age >= dur || e.isRemoved()) return false;
            if (age % 2 == 0) {
                // тёплый ладанный дымок и поднимающиеся искры
                double t = ang(), d = Math.sqrt(R.nextDouble()) * r;
                spawn(SMOKE, Math.cos(t) * d, 0.2, Math.sin(t) * d).followLocal(e).vel(rf(-0.006f, 0.006f), rf(0.012f, 0.025f), rf(-0.006f, 0.006f))
                        .size(0.3f, 1.1f).rot((float) ang(), rf(-0.02f, 0.02f)).colors(SMOKE_C, new float[] { 0.25f, 0.2f, 0.14f })
                        .alpha(0.55f).life(40).fade(0.3f, 0.5f);
                t = ang(); d = Math.sqrt(R.nextDouble()) * r;
                spawn(rich ? STAR : GLINT, Math.cos(t) * d, 0.1, Math.sin(t) * d).followLocal(e).vel(0, rf(0.03f, 0.06f), 0)
                        .size(0.1f, 0.02f).colors(WARM, GOLD).life(28).fade(0.15f, 0.5f);
            }
            if (age % 3 == 0) {        // хвосты комет
                spawn(STREAK, 0, 0, 0).orbit(ang(), r, 0.08, 0, 0.15, 0).follow(e).stretch(3f).size(0.05f)
                        .colors(WARM, GOLD).alpha(0.8f).life(14).fade(0.2f, 0.5f);
            }
            return true;
        });
    }

    // ================================================================ Посох Света

    /** Начало «Благословения»: солнце под ногами, волна и восходящие искры. */
    private static void lightStart(Entity e) {
        if (e == null) return;
        Vec3d p = e.getPos();
        float h = height(e);
        flatOn(SUNBURST, e, p, 0.07).size(0.4f, 2.6f).ease().rot((float) ang(), 0.05f).colors(WARM, GOLD).life(22).fade(0f, 0.7f);
        shock(e, p, 0.3f, 3.5f, 16, WHITE, GOLD, 1f, 0);
        flatOn(RUNES, e, p, 0.06).size(0.4f, 1.6f).ease().rot(0, 0.06f).color(GOLD).alpha(0.8f).life(26).fade(0.1f, 0.6f);
        for (int i = 0; i < 14; i++) {
            for (int k = 0; k < 2; k++) {
                spawn(GLINT, 0, 0, 0).orbit(i * 0.5 + k * Math.PI, 0.85, 0.22, -0.01, 0.1 + i * 0.03, h / 14.0).follow(e)
                        .size(0.13f, 0.02f).colors(WARM, GOLD).life(20).fade(0.1f, 0.5f).delay(i / 2);
            }
        }
        spawn(STAR, 0, h + 0.2, 0).followLocal(e).size(0.2f, 0.9f).ease().rot(0, 0.1f).color(WARM).life(12).fade(0f, 0.9f);
    }

    /** Конец «Благословения»: луч гаснет, искры осыпаются. */
    private static void lightEnd(Entity e) {
        if (e == null) return;
        float h = height(e);
        burst(STAR, e.getX(), e.getY() + h * 0.75, e.getZ(), 18, 0.18f, WARM, GOLD, 0.12f, 16, 0.01f, 0.9f, 0, false);
        flash(e.getX(), e.getY() + h * 0.75, e.getZ(), 0.3f, 1.4f, 7, WARM);
    }

    // ================================================================ Посохи: Веяние серафима

    private static void wave(ClientWorld w, Vec3d eye, float yawDeg, float pitchDeg, float range, int coneDeg) {
        double yaw = Math.toRadians(yawDeg), pitch = Math.toRadians(pitchDeg);
        Vec3d dir = new Vec3d(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch)).normalize();
        Vec3d o = eye.add(0, -0.3, 0);
        double half = Math.toRadians(coneDeg) / 2;
        float speed = range / 8f;
        // три слоя светового полумесяца летят веером вперёд
        for (int layer = 0; layer < 3; layer++) {
            int n = 34;
            for (int i = 0; i <= n; i++) {
                double off = -half + 2 * half * i / n;
                Vec3d d = rotY(dir, off);
                float edge = (float) Math.abs(off / half);
                float s = speed * (1f - layer * 0.12f) * rf(0.95f, 1.05f);
                spawn(STREAK, o.x + d.x * 0.8, o.y + d.y * 0.8 + (layer - 1) * 0.15, o.z + d.z * 0.8).vel(d.x * s, d.y * s, d.z * s)
                        .drag(0.9f).stretch(3.2f).size(0.08f - edge * 0.03f, 0.03f).colors(layer == 1 ? WHITE : WARM, GOLD)
                        .life(10 + R.nextInt(3)).fade(0f, 0.55f).delay(layer);
            }
        }
        for (int i = 0; i < 16; i++) {
            Vec3d d = rotY(dir, rf((float) -half, (float) half));
            float s = speed * rf(0.4f, 0.9f);
            spawn(i % 2 == 0 ? GLINT : FEATHER, o.x + d.x, o.y + d.y + rf(-0.3f, 0.3f), o.z + d.z).vel(d.x * s, d.y * s + 0.02, d.z * s)
                    .drag(0.88f).size(i % 2 == 0 ? 0.14f : 0.16f, 0.04f).rot((float) ang(), rf(-0.15f, 0.15f))
                    .colors(WARM, GOLD).life(16 + R.nextInt(8)).fade(0f, 0.5f);
        }
        Vec3d hand = o.add(dir.multiply(1.1));
        flash(hand.x, hand.y, hand.z, 0.3f, 1.4f, 6, WARM);
        spawn(SUNBURST, hand.x, hand.y, hand.z).size(0.2f, 1.2f).ease().rot((float) ang(), 0.2f).color(GOLD).life(8).fade(0f, 0.8f);
    }

    private static Vec3d rotY(Vec3d v, double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new Vec3d(v.x * c - v.z * s, v.y, v.x * s + v.z * c);
    }

    static MinecraftClient mc() {
        return MinecraftClient.getInstance();
    }
}
