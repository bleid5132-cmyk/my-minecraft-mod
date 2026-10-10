package com.bleid.vestments.client.fx;

import static com.bleid.vestments.client.fx.FxSystem.*;

import java.util.Random;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

/** Эффекты магии и нового оружия паладина. */
@Environment(EnvType.CLIENT)
final class PaladinMagicFx {
    private static final Random R = new Random();
    private static final float[] WHITE = { 1f, 1f, 1f };
    private static final float[] WARM = { 1f, 0.94f, 0.78f };
    private static final float[] GOLD = { 1f, 0.74f, 0.26f };
    private static final float[] RED = { 1f, 0.32f, 0.22f };
    private static final float[] BLUE = { 0.55f, 0.75f, 1f };
    private static final float[] GREEN = { 0.6f, 1f, 0.55f };

    private PaladinMagicFx() { }

    private static float rf(float lo, float hi) { return lo + R.nextFloat() * (hi - lo); }
    private static double ang() { return R.nextDouble() * Math.PI * 2; }

    static boolean play(ClientWorld w, String type, Vec3d pos, Entity e, int dur, float a, float b, float c) {
        switch (type) {
            case "pal_hammer" -> {
                com.bleid.vestments.client.HammerClient.update(e, pos, a > 0.5f, (int) b);
                hammer(pos, a > 0.5f, (int) b);
            }
            case "pal_hammer_end" -> com.bleid.vestments.client.HammerClient.end(e);
            case "pal_dawn" -> dawn(pos, new Vec3d(a, b, c), dur);
            case "pal_stun" -> stun(e, dur, a > 0.5f);
            case "pal_taunt" -> taunt(e, a);
            case "pal_aura2" -> aura(e, a, (int) b);
            case "pal_dash" -> dash(e);
            case "pal_dash_trail" -> dashTrail(pos);
            case "pal_wings" -> wings(e, dur);
            case "pal_pillar" -> pillar(pos);
            case "pal_martyr" -> martyr(pos, e, a > 0.5f);
            case "pal_chain" -> chain(pos, a);
            case "pal_heaven" -> heaven(pos, a);
            case "pal_relic" -> relic(e);
            default -> { return false; }
        }
        return true;
    }

    private static void sparks(Vec3d p, int n, float speed, float[] c0, float[] c1, float size, int life) {
        for (int i = 0; i < n; i++) {
            double th = ang(), ph = Math.acos(2 * R.nextDouble() - 1);
            double s = speed * rf(0.4f, 1f);
            spawn(STREAK, p.x, p.y, p.z).vel(Math.sin(ph) * Math.cos(th) * s, Math.abs(Math.cos(ph)) * s * 0.7 + 0.04,
                            Math.sin(ph) * Math.sin(th) * s)
                    .gravity(0.03f).drag(0.9f).stretch(3f).size(size, size * 0.3f).colors(c0, c1).life(life + R.nextInt(6)).fade(0f, 0.5f);
        }
    }

    /** Летящий молот: вращающийся золотой блик со следом искр. */
    private static void hammer(Vec3d p, boolean back, int age) {
        spawn(FLARE, p.x, p.y, p.z).size(0.55f, 0.3f).rot(age * 0.9f, 0.5f).colors(WHITE, GOLD).life(3).fade(0f, 1f);
        spawn(SUNBURST, p.x, p.y, p.z).size(0.45f, 0.25f).rot(-age * 0.7f, -0.4f).color(GOLD).alpha(0.7f).life(3).fade(0f, 1f);
        for (int i = 0; i < 3; i++) {
            spawn(GLINT, p.x + rf(-0.2f, 0.2f), p.y + rf(-0.2f, 0.2f), p.z + rf(-0.2f, 0.2f))
                    .vel(rf(-0.02f, 0.02f), rf(0f, 0.03f), rf(-0.02f, 0.02f)).size(0.08f, 0f).color(back ? WARM : GOLD).life(10).fade(0f, 0.6f);
        }
    }

    /** След копья рассвета: светящаяся лента, медленно гаснет, над ней поднимаются искры. */
    private static void dawn(Vec3d a, Vec3d d, int dur) {
        double len = d.length();
        int n = Math.max(1, (int) (len / 0.3));
        for (int i = 0; i <= n; i++) {
            Vec3d p = a.add(d.multiply((double) i / n));
            spawn(ORB, p.x, p.y, p.z).size(0.22f, 0.12f).colors(WHITE, WARM).alpha(0.8f).life(dur + R.nextInt(20)).fade(0.02f, 0.4f)
                    .pulse(0.2f, 0.5f);
            if (R.nextInt(3) == 0) {
                spawn(STAR, p.x, p.y, p.z).vel(0, rf(0.01f, 0.03f), 0).size(0.08f, 0f).color(GOLD).life(30 + R.nextInt(20)).fade(0.1f, 0.5f);
            }
        }
        spawn(FLARE, a.x + d.x, a.y + d.y, a.z + d.z).size(0.6f, 0.2f).color(WHITE).life(3).fade(0f, 1f);
    }

    /** Оглушение: звёзды кружат над головой; метка «Вызова» — алая вспышка. */
    private static void stun(Entity e, int dur, boolean mark) {
        if (e == null) return;
        float h = e.getHeight();
        if (mark) {
            spawn(FLARE, 0, h + 0.4, 0).followLocal(e).size(0.4f, 0.1f).color(RED).life(20).fade(0f, 0.7f);
            spawn(CROSS, 0, h + 0.5, 0).followLocal(e).size(0.2f, 0.25f).color(RED).life(30).fade(0.1f, 0.6f);
            return;
        }
        for (int k = 0; k < 3; k++) {
            spawn(STAR, 0, 0, 0).orbit(k * Math.PI * 2 / 3, 0.45, 0.25, 0, h + 0.25, 0).follow(e).size(0.14f).color(GOLD)
                    .life(dur).fade(0.05f, 0.2f).pulse(0.3f, 0.8f);
        }
    }

    /** «Вызов»: алое кольцо разлетается по земле, руны под ногами, короткий столп. */
    private static void taunt(Entity e, float r) {
        if (e == null) return;
        Vec3d p = e.getPos();
        spawn(SHOCK, p.x, p.y + 0.06, p.z).mode(Mode.FLAT).size(0.4f, r).ease().colors(WARM, RED).life(18).fade(0f, 0.7f);
        spawn(RING, p.x, p.y + 0.07, p.z).mode(Mode.FLAT).size(0.3f, r * 0.8f).ease().color(GOLD).alpha(0.7f).life(22).fade(0f, 0.7f).delay(3);
        spawn(RUNES, 0, 0.06, 0).mode(Mode.FLAT).followLocal(e).size(0.4f, 1.8f).ease().rot(0, 0.06f).color(RED).life(30).fade(0.05f, 0.6f);
        spawn(BEAM, 0, 0, 0).mode(Mode.BEAM).followLocal(e).height(5f).size(0.6f, 1.2f).ease().colors(WARM, RED).life(12).fade(0f, 0.8f);
        sparks(p.add(0, 1, 0), 30, 0.45f, WARM, RED, 0.06f, 12);
    }

    /** Аура: кольцо цвета ауры по земле и огоньки по кругу. */
    private static void aura(Entity e, float r, int type) {
        if (e == null) return;
        float[] c = type == 1 ? BLUE : type == 2 ? GREEN : RED;
        Vec3d p = e.getPos();
        spawn(RING, p.x, p.y + 0.06, p.z).mode(Mode.FLAT).size(0.5f, r).ease().colors(WARM, c).alpha(0.55f).life(24).fade(0f, 0.7f);
        for (int i = 0; i < 10; i++) {
            double th = i * Math.PI * 2 / 10 + rf(-0.2f, 0.2f);
            double rr = r * rf(0.5f, 1f);
            spawn(GLINT, p.x + Math.cos(th) * rr, p.y + 0.2, p.z + Math.sin(th) * rr).vel(0, rf(0.03f, 0.06f), 0)
                    .size(0.1f, 0f).color(c).life(26).fade(0.1f, 0.5f);
        }
    }

    private static void dash(Entity e) {
        if (e == null) return;
        Vec3d p = e.getPos();
        spawn(SHOCK, p.x, p.y + 0.06, p.z).mode(Mode.FLAT).size(0.3f, 2.2f).ease().colors(WHITE, GOLD).life(12).fade(0f, 0.7f);
        for (int i = 0; i < 10; i++) {
            spawn(FEATHER, p.x + rf(-0.5f, 0.5f), p.y + rf(0.4f, 1.6f), p.z + rf(-0.5f, 0.5f)).vel(rf(-0.04f, 0.04f), rf(0f, 0.05f), rf(-0.04f, 0.04f))
                    .gravity(-0.002f).drag(0.94f).rot((float) ang(), rf(-0.1f, 0.1f)).size(0.16f, 0.06f).color(WARM).life(30).fade(0f, 0.6f);
        }
    }

    private static void dashTrail(Vec3d p) {
        for (int i = 0; i < 4; i++) {
            spawn(STREAK, p.x + rf(-0.3f, 0.3f), p.y + rf(0.2f, 1.7f), p.z + rf(-0.3f, 0.3f)).vel(0, 0.01, 0).stretch(2f)
                    .size(0.07f, 0.02f).colors(WHITE, GOLD).life(10).fade(0f, 0.6f);
        }
        spawn(GLINT, p.x, p.y + 1, p.z).size(0.3f, 0.05f).color(WARM).life(8).fade(0f, 0.8f);
    }

    /** Ангельские крылья: включаем отрисовку крыльев и осыпаем перьями. */
    private static void wings(Entity e, int dur) {
        if (e == null) return;
        com.bleid.vestments.client.WingsClient.start(e, dur);
        Vec3d p = e.getPos();
        spawn(FLARE, p.x, p.y + 1.4, p.z).size(2.5f, 0.3f).color(WHITE).life(8).fade(0f, 1f);
        spawn(SHOCK, p.x, p.y + 0.06, p.z).mode(Mode.FLAT).size(0.4f, 4f).ease().colors(WHITE, GOLD).life(16).fade(0f, 0.7f);
        spawn(BEAM, 0, 0, 0).mode(Mode.BEAM).followLocal(e).height(20f).size(0.4f, 1.6f).ease().colors(WHITE, GOLD).life(18).fade(0f, 0.8f);
        for (int i = 0; i < 24; i++) {
            spawn(FEATHER, p.x + rf(-1.2f, 1.2f), p.y + rf(1f, 2.6f), p.z + rf(-1.2f, 1.2f)).vel(rf(-0.03f, 0.03f), rf(-0.02f, 0.03f), rf(-0.03f, 0.03f))
                    .gravity(0.002f).drag(0.96f).rot((float) ang(), rf(-0.08f, 0.08f)).size(0.18f, 0.1f).color(WARM).life(50 + R.nextInt(30))
                    .fade(0.05f, 0.5f);
        }
    }

    /** Столп света с неба на цель. */
    private static void pillar(Vec3d p) {
        spawn(BEAM, p.x, p.y, p.z).mode(Mode.BEAM).height(14f).size(1.0f, 0.2f).colors(WHITE, WARM).life(12).fade(0f, 0.8f);
        spawn(BEAM, p.x, p.y, p.z).mode(Mode.BEAM).height(14f).size(0.4f, 0.1f).color(WHITE).life(8).fade(0f, 1f);
        spawn(FLARE, p.x, p.y + 1, p.z).size(1.4f, 0.2f).color(WHITE).life(6).fade(0f, 1f);
        spawn(SHOCK, p.x, p.y + 0.06, p.z).mode(Mode.FLAT).size(0.3f, 1.8f).ease().colors(WHITE, GOLD).life(12).fade(0f, 0.7f);
        sparks(p.add(0, 0.5, 0), 16, 0.35f, WHITE, GOLD, 0.05f, 10);
    }

    /** Нить света от союзника к паладину (мученичество / наложение рук). */
    private static void martyr(Vec3d ally, Entity pal, boolean heal) {
        if (pal == null) return;
        Vec3d to = pal.getPos().add(0, pal.getHeight() * 0.6, 0);
        Vec3d d = to.subtract(ally);
        int n = (int) Math.max(4, d.length() / 0.25);
        float[] c = heal ? GREEN : GOLD;
        for (int i = 0; i <= n; i++) {
            Vec3d p = ally.add(d.multiply((double) i / n));
            spawn(GLINT, p.x, p.y, p.z).size(0.12f, 0f).colors(WHITE, c).life(14 + i % 6).delay(i / 3).fade(0f, 0.6f);
        }
        spawn(HEART, ally.x, ally.y + 0.9, ally.z).vel(0, 0.03, 0).size(0.3f, 0.2f).color(heal ? GREEN : RED).life(30).fade(0.1f, 0.5f);
        if (!heal) {
            spawn(FLARE, ally.x, ally.y, ally.z).size(2f, 0.2f).color(WARM).life(8).fade(0f, 1f);
            spawn(BEAM, ally.x, ally.y - 1, ally.z).mode(Mode.BEAM).height(10f).size(0.8f, 0.2f).color(GOLD).life(16).fade(0f, 0.8f);
        }
    }

    /** «Карающая цепь»: кольцо стягивается к паладину. */
    private static void chain(Vec3d p, float r) {
        spawn(RING, p.x, p.y + 0.06, p.z).mode(Mode.FLAT).size(r, 0.4f).ease().colors(GOLD, WARM).life(14).fade(0f, 0.6f);
        for (int i = 0; i < 24; i++) {
            double th = i * Math.PI * 2 / 24;
            double sp = r * 0.07;
            spawn(STREAK, p.x + Math.cos(th) * r, p.y + 0.8, p.z + Math.sin(th) * r).vel(-Math.cos(th) * sp, 0, -Math.sin(th) * sp)
                    .drag(0.92f).stretch(4f).size(0.07f, 0.03f).colors(WARM, GOLD).life(14).fade(0f, 0.5f);
        }
    }

    /** «Сокрушение небес»: удар, раскалывающий землю светом. */
    private static void heaven(Vec3d p, float r) {
        spawn(SHOCK, p.x, p.y + 0.06, p.z).mode(Mode.FLAT).size(0.5f, r * 1.2f).ease().colors(WHITE, GOLD).life(18).fade(0f, 0.7f);
        spawn(CRACK, p.x, p.y + 0.05, p.z).mode(Mode.FLAT).size(r * 0.6f, r * 0.75f).ease().rot((float) ang(), 0)
                .colors(WARM, GOLD).life(50).fade(0f, 0.6f);
        spawn(SUNBURST, p.x, p.y + 0.08, p.z).mode(Mode.FLAT).size(0.6f, r).ease().rot((float) ang(), 0.05f).colors(WHITE, GOLD)
                .life(22).fade(0f, 0.7f);
        spawn(BEAM, p.x, p.y, p.z).mode(Mode.BEAM).height(16f).size(1.6f, 0.3f).colors(WHITE, GOLD).life(14).fade(0f, 0.8f);
        spawn(FLARE, p.x, p.y + 0.6, p.z).size(3f, 0.4f).color(WHITE).life(7).fade(0f, 1f);
        for (int i = 0; i < 8; i++) {
            double th = ang(), rr = rf(0.5f, r);
            spawn(BEAM, p.x + Math.cos(th) * rr, p.y, p.z + Math.sin(th) * rr).mode(Mode.BEAM).height(rf(1.5f, 3.5f)).size(0.3f, 0.05f)
                    .color(WARM).life(10 + R.nextInt(8)).delay(R.nextInt(5)).fade(0f, 0.8f);
        }
        sparks(p.add(0, 0.3, 0), 40, 0.6f, WHITE, GOLD, 0.06f, 14);
    }

    private static void relic(Entity e) {
        if (e == null) return;
        for (int k = 0; k < 4; k++) {
            spawn(GLINT, 0, 0, 0).orbit(k * Math.PI / 2, 0.7, 0.1, 0, 0.9, 0.01).follow(e).size(0.1f, 0f).color(WARM).life(30).fade(0.1f, 0.5f);
        }
    }
}
