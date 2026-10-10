package com.bleid.vestments.paladin.combat;

import java.util.HashMap;
import java.util.Map;

/**
 * Боевые стили шести мечей паладина. Чем выше звание, тем длиннее серия, быстрее и шире удары,
 * ярче след клинка, сильнее «заморозка» кадра и тряска в момент попадания.
 *
 * Тайминги — по образцу Epic Fight: короткое окно удара (0.1–0.15 с), ранний контакт,
 * длинный довод, следующий удар можно начать с середины приёма.
 */
public final class Movesets {
    public static final Map<String, Moveset> BY_SWORD = new HashMap<>();
    public static final Map<String, Clip> CLIPS = new HashMap<>();

    private Movesets() { }

    private static Strike S(float antic, float pre, float contact, float rec, float roll, float a0, float a1) {
        return new Strike(antic, pre, contact, rec, roll, a0, a1);
    }

    private static Clip C(Moveset m, String name, float len, float trans) {
        Clip c = new Clip(m.id + "/" + name, len, trans);
        CLIPS.put(c.id, c);
        return c;
    }

    private static void add(Moveset m) {
        m.draw = draw(m);
        m.sheathe = sheathe(m);
        BY_SWORD.put(m.id, m);
    }

    /** Оружие без ножен (молоты, копьё, кистень): без анимации выхватывания. */
    private static void addWeapon(Moveset m) {
        BY_SWORD.put(m.id, m);
    }

    public static Moveset of(String swordId) {
        return BY_SWORD.get(swordId);
    }

    /** Подъём щита в защитную стойку (общий для всех щитов). */
    public static final Clip SHIELD_DRAW = shieldDraw();

    static {
        warhammer();
        greathammer();
        holySpear();
        flail();
        junior();
        recruit();
        senior();
        lord();
        highLord();
        general();
    }

    private static Clip shieldDraw() {
        Clip c = new Clip("shield/draw", 0.7f, 0.08f);
        c.visualOnly = true;
        c.offOnly = true;
        // щит снимается со спины/бока, широким махом выносится вперёд, «встаёт» в стойку с лёгкой отдачей
        c.offPitch.key(0, 0.35f).key(0.14f, 0.55f).key(0.3f, -1.25f).key(0.42f, -0.62f).key(0.52f, -0.78f).key(0.7f, -0.72f);
        c.offYaw.key(0, -0.2f).key(0.14f, -0.35f).key(0.3f, 0.55f).key(0.42f, 0.38f).key(0.7f, 0.38f);
        c.offRoll.key(0, -0.45f).key(0.14f, -0.6f).key(0.3f, 0.1f).key(0.42f, -0.12f).key(0.7f, -0.12f);
        c.spin.key(0, 0).key(0.14f, 10).key(0.32f, -14).key(0.7f, 0);
        CLIPS.put(c.id, c);
        return c;
    }

    /**
     * Доставание меча. Простые звания — короткий вынос из-за левого бедра в стойку;
     * лорд и выше — с размашистым росчерком; генерал — ещё и вращение клинка над головой.
     */
    private static Clip draw(Moveset m) {
        // рука тянется к рукояти на левом бедре, клинок выходит из ножен, росчерк за плечом — и в стойку
        float L = 1.15f;
        Clip c = C(m, "draw", L, 0.14f);
        c.visualOnly = true;
        c.lockMove = false;
        c.sheath = true;
        // для вида от первого лица — простой плавный вынос (там ножен не видно)
        c.strike(new Strike(0.3f, 0.5f, 0.85f, 0.95f, 40, -130, 25).smooth().targets(0).event("draw"));
        float flourish = 10f + 2.5f * m.tier;
        c.spin.key(0, 0).key(0.25f * L, -16).key(0.47f * L, -10).key(0.59f * L, -2).key(0.71f * L, flourish).key(0.85f * L, 5).key(L, 0);
        c.lean.key(0, 0).key(0.25f * L, 9).key(0.47f * L, 6).key(0.59f * L, 2).key(0.71f * L, -3).key(0.85f * L, 7).key(L, 2);
        c.lift.key(0, 0).key(0.25f * L, -0.04f).key(0.71f * L, 0.02f).key(0.85f * L, -0.05f).key(L, -0.015f);
        return c;
    }

    /** Убрать меч в ножны: то же движение, что и выхватывание, в обратном порядке, чуть быстрее. */
    private static Clip sheathe(Moveset m) {
        float L = 1.0f;
        Clip c = C(m, "sheathe", L, 0.12f);
        c.visualOnly = true;
        c.lockMove = false;
        c.sheath = true;
        c.reverse = true;
        // время идёт назад: u = 1 - t/L
        c.spin.key(0, 0).key(0.15f * L, 5).key(0.29f * L, 10).key(0.41f * L, -2).key(0.53f * L, -10).key(0.75f * L, -16).key(L, 0);
        c.lean.key(0, 0).key(0.15f * L, 6).key(0.29f * L, -2).key(0.53f * L, 6).key(0.75f * L, 9).key(L, 0);
        return c;
    }


    // ================================================================ БОЕВОЙ МОЛОТ — тяжёлые дробящие удары со щитом
    private static void warhammer() {
        Moveset m = new Moveset("warhammer", 2, 1.5f)
                .trail(new float[] { 0.95f, 0.78f, 0.45f }, new float[] { 1f, 0.95f, 0.85f }, 0.55f, 0.12f, 1.2f, false);
        Clip c1 = C(m, "c1", 1.0f, 0.1f).strike(S(0.22f, 0.3f, 0.42f, 0.68f, 80, 115, -40).heavy()
                .dmg(1f).kb(0.6f).targets(2).hitstop(2).shake(0.2f));
        c1.lean.key(0, 0).key(0.3f, -8).key(0.42f, 14).key(1.0f, 0);
        c1.stance.key(0, 0).key(0.42f, 1).key(1.0f, 0);
        c1.step(0.28f, 0.42f, 0.4f);
        Clip c2 = C(m, "c2", 0.85f, 0.08f).strike(S(0.16f, 0.22f, 0.34f, 0.58f, 8, 95, -85).dmg(1f).kb(0.7f).targets(3).hitstop(1));
        c2.spin.key(0, 0).key(0.22f, 20).key(0.34f, -25).key(0.85f, 0);
        Clip c3 = C(m, "c3", 1.0f, 0.08f).strike(S(0.24f, 0.3f, 0.42f, 0.74f, 92, -70, 120).two()
                .dmg(1.2f).kb(0.3f).targets(3).hitstop(2).shake(0.25f).event("launch"));
        c3.lean.key(0, 0).key(0.3f, 12).key(0.42f, -12).key(1.0f, 0);
        c3.lift.key(0, 0).key(0.3f, -0.06f).key(0.42f, 0.15f).key(1.0f, 0);
        Clip b1 = C(m, "b1", 0.85f, 0.1f).strike(S(0.16f, 0.22f, 0.34f, 0.56f, 45, 110, -60).dmg(1f).kb(0.5f).targets(2).hitstop(1));
        b1.step(0.16f, 0.34f, 0.4f);
        Clip b2 = C(m, "b2", 1.05f, 0.08f).strike(S(0.28f, 0.36f, 0.48f, 0.8f, 90, 140, -45).heavy().two()
                .dmg(1.4f).kb(0.8f).targets(4).width(0.6f).hitstop(3).shake(0.35f).event("crush"));
        b2.lean.key(0, 0).key(0.36f, -12).key(0.48f, 18).key(1.05f, 0);
        b2.lift.key(0, 0).key(0.36f, 0.08f).key(0.48f, -0.08f).key(1.05f, 0);
        Clip dash = C(m, "dash", 0.85f, 0.06f).strike(S(0.1f, 0.14f, 0.28f, 0.5f, 0, 0, 0).reach(0.8f, 1.5f).heavy()
                .dmg(1.2f).kb(1.1f).targets(3).width(0.7f).hitstop(2).shake(0.3f));
        dash.step(0.06f, 0.28f, 1.4f);
        dash.lean.key(0, 0).key(0.28f, 14).key(0.85f, 0);
        Clip air = C(m, "air", 0.9f, 0.08f).strike(S(0.1f, 0.16f, 0.3f, 0.6f, 92, 130, -70).heavy()
                .dmg(1.4f).kb(0.6f).targets(4).hitstop(2).shake(0.35f).event("shock"));
        air.lean.key(0, 0).key(0.3f, 20).key(0.9f, 0);
        air.lockMove = false;
        Clip skill = C(m, "hammer_throw", 0.9f, 0.08f).strike(S(0.2f, 0.3f, 0.36f, 0.6f, 90, 140, 10).targets(0).event("hammer_throw"));
        skill.lean.key(0, 0).key(0.3f, -12).key(0.36f, 14).key(0.9f, 0);
        skill.stance.key(0, 0).key(0.36f, 1).key(0.9f, 0);
        skill.skill = true;
        m.combo = new Clip[] { c1, c2, c3 };
        m.comboB = new Clip[] { b1, b2 };
        m.comboC = new Clip[] { c2, b2 };
        m.dash = dash; m.air = air; m.skill = skill;
        addWeapon(m);
    }

    // ================================================================ ДВУРУЧНЫЙ МОЛОТ — всё двумя руками, огромные дуги
    private static void greathammer() {
        Moveset m = new Moveset("greathammer", 4, 2.0f)
                .trail(new float[] { 1f, 0.7f, 0.3f }, new float[] { 1f, 0.96f, 0.86f }, 0.75f, 0.16f, 1.5f, true);
        Clip c1 = C(m, "c1", 1.15f, 0.1f).strike(S(0.26f, 0.34f, 0.5f, 0.82f, 6, 120, -120).heavy().two()
                .dmg(1f).kb(0.9f).targets(5).width(0.7f).hitstop(2).shake(0.3f));
        c1.spin.key(0, 0).key(0.34f, 30).key(0.5f, -40).key(1.15f, 0);
        c1.stance.key(0, 0).key(0.5f, -0.6f).key(1.15f, 0);
        Clip c2 = C(m, "c2", 1.25f, 0.08f).strike(S(0.3f, 0.4f, 0.52f, 0.9f, 90, 140, -50).heavy().two()
                .dmg(1.3f).kb(0.8f).targets(5).width(0.7f).hitstop(3).shake(0.5f).event("shock"));
        c2.lean.key(0, 0).key(0.4f, -14).key(0.52f, 22).key(1.25f, 0);
        c2.lift.key(0, 0).key(0.4f, 0.12f).key(0.52f, -0.1f).key(1.25f, 0);
        c2.stance.key(0, 0).key(0.52f, 1).key(1.25f, 0);
        Clip c3 = C(m, "c3", 1.15f, 0.08f).strike(S(0.2f, 0.26f, 0.62f, 0.86f, -6, 95, -275).two()
                .dmg(1.2f).kb(0.9f).targets(7).width(0.75f).hitstop(2).shake(0.35f));
        c3.spin.key(0, 0).key(0.26f, 0).key(0.62f, -360).key(1.15f, -360);
        c3.stance.key(0, 0).key(0.3f, -0.5f).key(1.15f, 0);
        Clip b1 = C(m, "b1", 1.05f, 0.1f).strike(S(0.22f, 0.3f, 0.44f, 0.72f, 90, -80, 120).two()
                .dmg(1.1f).kb(0.3f).targets(4).hitstop(2).shake(0.3f).event("launch"));
        b1.lean.key(0, 0).key(0.3f, 14).key(0.44f, -14).key(1.05f, 0);
        b1.lift.key(0, 0).key(0.3f, -0.08f).key(0.44f, 0.18f).key(1.05f, 0);
        Clip b2 = C(m, "b2", 1.2f, 0.08f).strike(S(0.28f, 0.36f, 0.5f, 0.86f, 60, 130, -50).heavy().two()
                .dmg(1.35f).kb(0.9f).targets(5).width(0.7f).hitstop(3).shake(0.5f).event("shock"));
        b2.step(0.3f, 0.5f, 0.6f);
        b2.lean.key(0, 0).key(0.36f, -10).key(0.5f, 20).key(1.2f, 0);
        Clip dash = C(m, "dash", 1.1f, 0.06f).strike(S(0.24f, 0.36f, 0.48f, 0.8f, 90, 140, -50).heavy().two()
                .dmg(1.4f).kb(1f).targets(6).width(0.8f).hitstop(3).shake(0.55f).event("shock"));
        dash.leap(0.1f, 0.5f, 0.32f, -0.9f);
        dash.step(0.08f, 0.48f, 1.4f);
        dash.lean.key(0, 0).key(0.36f, -12).key(0.48f, 22).key(1.1f, 0);
        Clip air = C(m, "air", 1.0f, 0.08f).strike(S(0.1f, 0.16f, 0.3f, 0.6f, 92, 130, -70).heavy().two()
                .dmg(1.6f).kb(0.8f).targets(6).hitstop(3).shake(0.5f).event("shock"));
        air.lean.key(0, 0).key(0.3f, 22).key(1.0f, 0);
        air.lockMove = false;
        Clip skill = C(m, "heaven_crush", 1.4f, 0.08f).strike(S(0.36f, 0.46f, 0.58f, 1.0f, 90, 150, -60).heavy().two()
                .dmg(2f).kb(1.2f).targets(8).width(0.9f).hitstop(4).shake(0.9f).event("heaven_crush"));
        skill.leap(0.12f, 0.9f, 0.48f, -1.5f);
        skill.lean.key(0, 0).key(0.2f, -10).key(0.46f, -14).key(0.58f, 24).key(1.4f, 0);
        skill.stance.key(0, 0).key(0.58f, 1).key(1.4f, 0);
        skill.skill = true;
        m.combo = new Clip[] { c1, c2, c3 };
        m.comboB = new Clip[] { b1, b2 };
        m.comboC = new Clip[] { c3, b2 };
        m.dash = dash; m.air = air; m.skill = skill;
        addWeapon(m);
    }

    // ================================================================ СВЯЩЕННОЕ КОПЬЁ — уколы, подсечки, длинная дистанция
    private static void holySpear() {
        Moveset m = new Moveset("holy_spear", 3, 2.6f)
                .trail(new float[] { 1f, 0.86f, 0.5f }, new float[] { 1f, 1f, 0.92f }, 0.6f, 0.12f, 0.8f, true);
        Clip c1 = C(m, "c1", 0.6f, 0.1f).strike(S(0.08f, 0.12f, 0.22f, 0.38f, 0, 0, 0).reach(1.2f, 2.6f).dmg(0.9f).kb(0.3f).targets(2));
        c1.step(0.08f, 0.22f, 0.3f);
        c1.lean.key(0, 0).key(0.22f, 8).key(0.6f, 0);
        c1.stance.key(0, 0).key(0.22f, 1).key(0.6f, 0);
        Clip c2 = C(m, "c2", 0.62f, 0.06f).strike(S(0.1f, 0.13f, 0.24f, 0.4f, 0, 0, 0).reach(1.1f, 2.7f).dmg(1f).kb(0.35f).targets(2));
        c2.lean.key(0, 0).key(0.24f, 9).key(0.62f, 0);
        c2.stance.key(0, 0).key(0.24f, 1).key(0.62f, 0);
        Clip c3 = C(m, "c3", 0.8f, 0.06f).strike(S(0.14f, 0.18f, 0.32f, 0.52f, -18, 100, -100).dmg(1f).kb(0.5f).targets(4).width(0.6f));
        c3.spin.key(0, 0).key(0.18f, 25).key(0.32f, -30).key(0.8f, 0);
        c3.lift.key(0, 0).key(0.32f, -0.08f).key(0.8f, 0);
        Clip c4 = C(m, "c4", 0.95f, 0.06f).strike(S(0.2f, 0.26f, 0.36f, 0.66f, 0, 0, 0).reach(1.0f, 3.2f).two()
                .dmg(1.4f).kb(0.8f).targets(3).hitstop(2).shake(0.25f));
        c4.step(0.22f, 0.36f, 1.2f);
        c4.lean.key(0, 0).key(0.26f, -4).key(0.36f, 16).key(0.95f, 0);
        c4.stance.key(0, 0).key(0.36f, 1).key(0.95f, 0);
        Clip b1 = C(m, "b1", 0.8f, 0.08f).strike(S(0.14f, 0.18f, 0.3f, 0.5f, 90, 110, -30).dmg(1.05f).kb(0.4f).targets(3).hitstop(1));
        b1.lean.key(0, 0).key(0.18f, -6).key(0.3f, 12).key(0.8f, 0);
        Clip b2 = C(m, "b2", 0.95f, 0.06f).strike(S(0.14f, 0.18f, 0.48f, 0.66f, -6, 95, -275).two().dmg(1.15f).kb(0.6f)
                .targets(6).width(0.6f).hitstop(1));
        b2.spin.key(0, 0).key(0.18f, 0).key(0.48f, -360).key(0.95f, -360);
        Clip dash = C(m, "dash", 0.85f, 0.06f).strike(S(0.08f, 0.12f, 0.3f, 0.52f, 0, 0, 0).reach(1.2f, 3.4f).two()
                .dmg(1.3f).kb(0.8f).targets(4).hitstop(2).shake(0.25f));
        dash.step(0.04f, 0.3f, 2.0f);
        dash.lean.key(0, 0).key(0.3f, 14).key(0.85f, 0);
        Clip air = C(m, "air", 0.85f, 0.08f).strike(S(0.1f, 0.14f, 0.28f, 0.5f, 90, -35, -35).reach(1.0f, 2.8f)
                .dmg(1.45f).kb(0.5f).targets(3).hitstop(2).shake(0.3f));
        air.lean.key(0, 0).key(0.28f, 24).key(0.85f, 0);
        air.lockMove = false;
        Clip skill = C(m, "dawn_spear", 1.0f, 0.08f).strike(S(0.24f, 0.34f, 0.4f, 0.62f, 90, 150, 0).targets(0).event("dawn_spear"));
        skill.lean.key(0, 0).key(0.34f, -14).key(0.4f, 16).key(1.0f, 0);
        skill.stance.key(0, 0).key(0.4f, 1).key(1.0f, 0);
        skill.skill = true;
        m.combo = new Clip[] { c1, c2, c3, c4 };
        m.comboB = new Clip[] { c1, b1, b2 };
        m.comboC = new Clip[] { c3, b2 };
        m.dash = dash; m.air = air; m.skill = skill;
        addWeapon(m);
    }

    // ================================================================ КИСТЕНЬ — хлёсткие размашистые удары
    private static void flail() {
        Moveset m = new Moveset("flail", 3, 2.2f)
                .trail(new float[] { 0.85f, 0.8f, 0.7f }, new float[] { 1f, 0.94f, 0.8f }, 0.55f, 0.16f, 0.9f, false);
        Clip c1 = C(m, "c1", 0.85f, 0.1f).strike(S(0.16f, 0.22f, 0.38f, 0.58f, 4, 110, -110).dmg(1f).kb(0.5f).targets(3).width(0.6f));
        c1.spin.key(0, 0).key(0.22f, 22).key(0.38f, -28).key(0.85f, 0);
        Clip c2 = C(m, "c2", 0.95f, 0.08f).strike(S(0.2f, 0.26f, 0.38f, 0.64f, 90, 140, -40).heavy()
                .dmg(1.2f).kb(0.6f).targets(2).hitstop(2).shake(0.25f));
        c2.lean.key(0, 0).key(0.26f, -10).key(0.38f, 16).key(0.95f, 0);
        c2.stance.key(0, 0).key(0.38f, 1).key(0.95f, 0);
        Clip c3 = C(m, "c3", 0.95f, 0.08f)
                .strike(S(0.1f, 0.14f, 0.26f, 0.34f, -4, -100, 100).dmg(0.85f).kb(0.3f).targets(3).width(0.6f))
                .strike(S(0.38f, 0.42f, 0.56f, 0.7f, 4, 100, -110).dmg(1.05f).kb(0.7f).targets(3).width(0.6f).hitstop(2).shake(0.2f));
        c3.spin.key(0, 0).key(0.14f, -20).key(0.26f, 22).key(0.42f, 20).key(0.56f, -26).key(0.95f, 0);
        Clip b1 = C(m, "b1", 1.0f, 0.08f).strike(S(0.16f, 0.2f, 0.52f, 0.7f, -6, 95, -275).dmg(1.1f).kb(0.6f).targets(6).width(0.65f));
        b1.spin.key(0, 0).key(0.2f, 0).key(0.52f, -360).key(1.0f, -360);
        Clip b2 = C(m, "b2", 0.9f, 0.08f).strike(S(0.18f, 0.24f, 0.36f, 0.6f, 90, -70, 120).dmg(1.1f).kb(0.3f)
                .targets(3).hitstop(2).shake(0.2f).event("launch"));
        b2.lean.key(0, 0).key(0.24f, 10).key(0.36f, -10).key(0.9f, 0);
        Clip dash = C(m, "dash", 0.85f, 0.06f).strike(S(0.1f, 0.14f, 0.3f, 0.52f, 8, 100, -100).dmg(1.2f).kb(0.8f).targets(4).width(0.6f).hitstop(1));
        dash.step(0.06f, 0.3f, 1.0f);
        Clip air = C(m, "air", 0.9f, 0.08f).strike(S(0.1f, 0.16f, 0.3f, 0.6f, 92, 130, -70).heavy()
                .dmg(1.45f).kb(0.6f).targets(4).hitstop(2).shake(0.35f).event("shock"));
        air.lean.key(0, 0).key(0.3f, 20).key(0.9f, 0);
        air.lockMove = false;
        Clip skill = C(m, "punishing_chain", 1.25f, 0.08f)
                .strike(S(0.14f, 0.18f, 0.5f, 0.52f, -5, 90, -270).dmg(0.9f).kb(0.3f).targets(8).width(0.7f))
                .strike(S(0.52f, 0.54f, 0.88f, 1.0f, -5, 90, -270).dmg(1f).kb(0.2f).targets(8).width(0.7f).hitstop(2).shake(0.3f)
                        .event("chain_pull"));
        skill.spin.key(0, 0).key(0.18f, 0).key(0.5f, -360).key(0.54f, -360).key(0.88f, -720).key(1.25f, -720);
        skill.stance.key(0, 0).key(0.2f, -0.5f).key(1.0f, -0.5f).key(1.25f, 0);
        skill.skill = true;
        m.combo = new Clip[] { c1, c2, c3 };
        m.comboB = new Clip[] { b1, b2 };
        m.comboC = new Clip[] { c1, b1 };
        m.dash = dash; m.air = air; m.skill = skill;
        addWeapon(m);
    }

    // ================================================================ 1. Младший рекрут — просто и тяжеловато
    private static void junior() {
        Moveset m = new Moveset("junior_recruit_sword", 0, 1.45f)
                .trail(new float[] { 0.55f, 0.57f, 0.62f }, new float[] { 0.8f, 0.82f, 0.86f }, 0.35f, 0.08f, 0.7f, false);
        Clip c1 = C(m, "c1", 0.8f, 0.12f).strike(S(0.2f, 0.26f, 0.38f, 0.58f, 10, 70, -55).dmg(1f).kb(0.3f).targets(1));
        c1.lean.key(0, 0).key(0.26f, -4).key(0.38f, 8).key(0.8f, 0);
        c1.stance.key(0.3f, 0).key(0.38f, 0.4f).key(0.8f, 0);
        Clip c2 = C(m, "c2", 0.9f, 0.12f).strike(S(0.22f, 0.3f, 0.44f, 0.66f, 90, 100, -30).dmg(1.15f).kb(0.4f).targets(1).two());
        c2.lean.key(0, 0).key(0.3f, -6).key(0.44f, 12).key(0.9f, 0);
        Clip dash = C(m, "dash", 0.85f, 0.08f).strike(S(0.12f, 0.18f, 0.3f, 0.55f, 0, 0, 0).reach(1.0f, 1.75f).dmg(1.2f).kb(0.5f).targets(1));
        dash.step(0.16f, 0.3f, 0.9f);
        dash.lean.key(0, 0).key(0.3f, 10).key(0.85f, 0);
        dash.stance.key(0, 0).key(0.3f, 1).key(0.85f, 0);
        Clip air = C(m, "air", 0.8f, 0.1f).strike(S(0.1f, 0.16f, 0.3f, 0.55f, 90, 95, -55).dmg(1.25f).kb(0.4f).targets(1));
        air.lean.key(0, 0).key(0.3f, 12).key(0.8f, 0);
        air.lockMove = false;
        Clip skill = C(m, "lunge", 1.0f, 0.1f).strike(S(0.16f, 0.22f, 0.42f, 0.65f, 0, 0, 0).reach(1.0f, 2.0f)
                .dmg(1.6f).kb(0.8f).targets(4).width(0.6f).shake(0.15f).event("lunge"));
        skill.step(0.2f, 0.42f, 4.2f);
        skill.lean.key(0, 0).key(0.22f, 14).key(0.42f, 16).key(1.0f, 0);
        skill.stance.key(0, 0).key(0.22f, 1).key(0.8f, 1).key(1.0f, 0);
        skill.skill = true;
        m.combo = new Clip[] { c1, c2 };
        // вторая серия (на ходу): косой сверху-слева и тяжёлый рубящий двумя руками
        Clip b1 = C(m, "b1", 0.78f, 0.12f).strike(S(0.18f, 0.24f, 0.36f, 0.56f, 135, 95, -50).dmg(1f).kb(0.35f).targets(1));
        b1.step(0.2f, 0.36f, 0.3f);
        b1.lean.key(0, 0).key(0.24f, -3).key(0.36f, 7).key(0.78f, 0);
        b1.stance.key(0, 0).key(0.36f, 0.5f).key(0.78f, 0);
        Clip b2 = C(m, "b2", 1.0f, 0.12f).strike(S(0.26f, 0.34f, 0.46f, 0.76f, 90, 115, -40).heavy().two()
                .dmg(1.3f).kb(0.55f).targets(2).hitstop(1).shake(0.15f));
        b2.step(0.3f, 0.46f, 0.45f);
        b2.lean.key(0, 0).key(0.34f, -9).key(0.46f, 15).key(1.0f, 0);
        b2.stance.key(0, 0).key(0.46f, 1).key(1.0f, 0);
        b2.lift.key(0, 0).key(0.34f, 0.04f).key(0.46f, -0.08f).key(1.0f, 0);
        m.comboB = new Clip[] { b1, b2 };
        // третья серия (вбок/назад): обратный горизонтальный с шагом в сторону, восходящий косой
        Clip v1 = C(m, "v1", 0.78f, 0.12f).strike(S(0.16f, 0.22f, 0.34f, 0.54f, 6, -80, 70).dmg(1f).kb(0.35f).targets(1));
        v1.stance.key(0, 0).key(0.34f, -0.5f).key(0.78f, 0);
        v1.lean.key(0, 0).key(0.22f, -3).key(0.34f, 5).key(0.78f, 0);
        Clip v2 = C(m, "v2", 0.85f, 0.1f).strike(S(0.18f, 0.24f, 0.36f, 0.6f, 45, -75, 110).dmg(1.15f).kb(0.4f).targets(1).hitstop(1));
        v2.lean.key(0, 0).key(0.24f, 10).key(0.36f, -8).key(0.85f, 0);
        v2.stance.key(0, 0).key(0.24f, 0.7f).key(0.85f, 0);
        v2.lift.key(0, 0).key(0.24f, -0.04f).key(0.36f, 0.05f).key(0.85f, 0);
        m.comboC = new Clip[] { v1, v2 };
        m.dash = dash; m.air = air; m.skill = skill;
        add(m);
    }

    // ================================================================ 2. Рекрут — три удара, шаг вперёд
    private static void recruit() {
        Moveset m = new Moveset("recruit_sword", 1, 1.55f)
                .trail(new float[] { 0.62f, 0.68f, 0.8f }, new float[] { 0.92f, 0.95f, 1f }, 0.45f, 0.1f, 0.85f, false);
        Clip c1 = C(m, "c1", 0.72f, 0.1f).strike(S(0.13f, 0.17f, 0.28f, 0.45f, 45, 105, -60).dmg(1f).kb(0.35f).targets(2));
        c1.step(0.15f, 0.28f, 0.35f);
        c1.lean.key(0, 0).key(0.17f, -3).key(0.28f, 8).key(0.72f, 0);
        c1.stance.key(0, 0).key(0.28f, 0.5f).key(0.72f, 0);
        Clip c2 = C(m, "c2", 0.7f, 0.08f).strike(S(0.1f, 0.12f, 0.24f, 0.42f, 45, -110, 60).dmg(1f).kb(0.35f).targets(2));
        c2.lean.key(0, 0).key(0.24f, -4).key(0.7f, 0);
        c2.stance.key(0, 0).key(0.24f, -0.3f).key(0.7f, 0);
        Clip c3 = C(m, "c3", 0.85f, 0.08f).strike(S(0.16f, 0.2f, 0.3f, 0.58f, 0, 0, 0).reach(1.0f, 1.95f).two()
                .dmg(1.3f).kb(0.6f).targets(2).shake(0.12f));
        c3.step(0.18f, 0.3f, 0.6f);
        c3.lean.key(0, 0).key(0.3f, 12).key(0.85f, 0);
        c3.stance.key(0, 0).key(0.3f, 1).key(0.85f, 0);
        Clip dash = C(m, "dash", 0.8f, 0.08f).strike(S(0.1f, 0.13f, 0.27f, 0.5f, 8, 80, -80).dmg(1.2f).kb(0.5f).targets(3));
        dash.step(0.08f, 0.27f, 0.8f);
        dash.lean.key(0, 0).key(0.27f, 10).key(0.8f, 0);
        Clip air = C(m, "air", 0.8f, 0.1f).strike(S(0.1f, 0.15f, 0.29f, 0.55f, 90, 110, -50).dmg(1.3f).kb(0.5f).targets(2));
        air.lean.key(0, 0).key(0.29f, 14).key(0.8f, 0);
        air.lockMove = false;
        Clip skill = C(m, "crush", 1.1f, 0.1f).strike(S(0.3f, 0.38f, 0.5f, 0.8f, 90, 130, -45).heavy().two()
                .dmg(1.9f).kb(0.9f).targets(5).width(0.7f).shake(0.3f).hitstop(2).event("crush"));
        skill.lean.key(0, 0).key(0.38f, -12).key(0.5f, 20).key(1.1f, 0);
        skill.stance.key(0, 0).key(0.5f, 1).key(1.1f, 0);
        skill.lift.key(0, 0).key(0.3f, 0.15f).key(0.5f, 0);
        skill.skill = true;
        m.combo = new Clip[] { c1, c2, c3 };
        // вторая серия: обратный горизонтальный, восходящий косой, укол двумя руками
        Clip b1 = C(m, "b1", 0.68f, 0.1f).strike(S(0.12f, 0.16f, 0.27f, 0.44f, 4, -85, 75).dmg(1f).kb(0.35f).targets(2));
        b1.step(0.12f, 0.27f, 0.3f);
        b1.stance.key(0, 0).key(0.27f, -0.4f).key(0.68f, 0);
        Clip b2 = C(m, "b2", 0.72f, 0.08f).strike(S(0.12f, 0.16f, 0.28f, 0.46f, 45, -70, 105).dmg(1.05f).kb(0.4f).targets(2));
        b2.lean.key(0, 0).key(0.16f, 8).key(0.28f, -6).key(0.72f, 0);
        b2.stance.key(0, 0).key(0.16f, 0.6f).key(0.72f, 0);
        Clip b3 = C(m, "b3", 0.9f, 0.08f).strike(S(0.2f, 0.26f, 0.36f, 0.62f, 0, 0, 0).reach(0.95f, 2.1f).two()
                .dmg(1.35f).kb(0.65f).targets(2).hitstop(1).shake(0.15f));
        b3.step(0.22f, 0.36f, 0.8f);
        b3.lean.key(0, 0).key(0.26f, -4).key(0.36f, 14).key(0.9f, 0);
        b3.stance.key(0, 0).key(0.36f, 1).key(0.9f, 0);
        m.comboB = new Clip[] { b1, b2, b3 };
        // третья серия: низкий подсекающий, косой сверху справа, разворот с обратным
        Clip v1 = C(m, "v1", 0.72f, 0.1f).strike(S(0.12f, 0.16f, 0.28f, 0.46f, -22, 90, -80).dmg(1f).kb(0.35f).targets(2));
        v1.lean.key(0, 0).key(0.28f, 12).key(0.72f, 0);
        v1.lift.key(0, 0).key(0.28f, -0.08f).key(0.72f, 0);
        v1.stance.key(0, 0).key(0.28f, 1).key(0.72f, 0);
        Clip v2 = C(m, "v2", 0.72f, 0.08f).strike(S(0.12f, 0.15f, 0.27f, 0.45f, 135, 110, -60).dmg(1.05f).kb(0.4f).targets(2));
        v2.spin.key(0, 0).key(0.15f, -10).key(0.27f, 14).key(0.72f, 0);
        Clip v3 = C(m, "v3", 0.85f, 0.08f).strike(S(0.16f, 0.2f, 0.36f, 0.6f, 4, 100, -200).dmg(1.25f).kb(0.6f)
                .targets(3).width(0.55f).hitstop(1).shake(0.12f));
        v3.spin.key(0, 0).key(0.2f, 20).key(0.36f, -60).key(0.85f, 0);
        v3.stance.key(0, 0).key(0.36f, -0.6f).key(0.85f, 0);
        m.comboC = new Clip[] { v1, v2, v3 };
        m.dash = dash; m.air = air; m.skill = skill;
        add(m);
    }

    // ================================================================ 3. Старший рекрут — размашистые удары с разворотом корпуса
    private static void senior() {
        Moveset m = new Moveset("senior_recruit_sword", 2, 1.75f)
                .trail(new float[] { 0.62f, 0.72f, 0.95f }, new float[] { 0.95f, 0.98f, 1f }, 0.55f, 0.12f, 1f, false);
        Clip c1 = C(m, "c1", 0.66f, 0.1f).strike(S(0.12f, 0.15f, 0.26f, 0.42f, 8, 85, -75).dmg(1f).kb(0.4f).targets(3));
        c1.step(0.12f, 0.26f, 0.4f);
        c1.spin.key(0, 0).key(0.15f, 15).key(0.26f, -25).key(0.66f, 0);
        c1.lean.key(0, 0).key(0.26f, 6).key(0.66f, 0);
        Clip c2 = C(m, "c2", 0.64f, 0.08f).strike(S(0.1f, 0.13f, 0.24f, 0.42f, -8, -80, 85).dmg(1f).kb(0.4f).targets(3));
        c2.spin.key(0, 0).key(0.13f, -15).key(0.24f, 25).key(0.64f, 0);
        Clip c3 = C(m, "c3", 0.95f, 0.08f).strike(S(0.22f, 0.27f, 0.38f, 0.7f, 88, 120, -40).heavy().two()
                .dmg(1.45f).kb(0.7f).targets(3).hitstop(1).shake(0.25f));
        c3.step(0.25f, 0.38f, 0.7f);
        c3.lean.key(0, 0).key(0.27f, -10).key(0.38f, 18).key(0.95f, 0);
        c3.stance.key(0, 0).key(0.38f, 1).key(0.95f, 0);
        c3.lift.key(0, 0).key(0.27f, 0.1f).key(0.38f, 0);
        Clip dash = C(m, "dash", 0.8f, 0.08f).strike(S(0.12f, 0.15f, 0.28f, 0.5f, 45, -110, 70).dmg(1.25f).kb(0.5f).targets(3));
        dash.step(0.1f, 0.28f, 1.0f);
        dash.lean.key(0, 0).key(0.28f, 8).key(0.8f, 0);
        Clip air = C(m, "air", 0.85f, 0.1f).strike(S(0.12f, 0.18f, 0.32f, 0.6f, 92, 120, -70).dmg(1.4f).kb(0.6f)
                .targets(3).hitstop(1).shake(0.2f));
        air.lean.key(0, 0).key(0.32f, 16).key(0.85f, 0);
        air.lockMove = false;
        Clip skill = C(m, "whirl", 1.25f, 0.1f)
                .strike(S(0.12f, 0.16f, 0.5f, 0.52f, -5, 90, -270).dmg(1.15f).kb(0.5f).targets(6).width(0.6f))
                .strike(S(0.52f, 0.54f, 0.9f, 1.0f, -5, 90, -270).dmg(1.15f).kb(0.7f).targets(6).width(0.6f).shake(0.2f));
        skill.spin.key(0, 0).key(0.16f, 0).key(0.5f, -360).key(0.54f, -360).key(0.9f, -720).key(1.25f, -720);
        skill.stance.key(0, 0).key(0.2f, -0.5f).key(1.0f, -0.5f).key(1.25f, 0);
        skill.skill = true;
        m.combo = new Clip[] { c1, c2, c3 };
        // вторая серия: восходящий, разворот с обратным, тяжёлый косой двумя руками
        Clip b1 = C(m, "b1", 0.68f, 0.1f).strike(S(0.12f, 0.15f, 0.27f, 0.44f, 90, -70, 120).dmg(1f).kb(0.3f).targets(3));
        b1.lean.key(0, 0).key(0.15f, 10).key(0.27f, -8).key(0.68f, 0);
        b1.lift.key(0, 0).key(0.27f, 0.08f).key(0.68f, 0);
        Clip b2 = C(m, "b2", 0.72f, 0.08f).strike(S(0.12f, 0.15f, 0.3f, 0.46f, 10, 95, -100).dmg(1.05f).kb(0.45f).targets(3));
        b2.spin.key(0, 0).key(0.15f, 30).key(0.3f, -35).key(0.72f, 0);
        b2.step(0.12f, 0.3f, 0.4f);
        Clip b3 = C(m, "b3", 1.0f, 0.08f).strike(S(0.24f, 0.3f, 0.42f, 0.74f, 60, 125, -50).heavy().two()
                .dmg(1.5f).kb(0.75f).targets(3).hitstop(2).shake(0.3f));
        b3.step(0.26f, 0.42f, 0.6f);
        b3.lean.key(0, 0).key(0.3f, -10).key(0.42f, 18).key(1.0f, 0);
        b3.stance.key(0, 0).key(0.42f, 1).key(1.0f, 0);
        b3.lift.key(0, 0).key(0.3f, 0.1f).key(0.42f, -0.06f).key(1.0f, 0);
        m.comboB = new Clip[] { b1, b2, b3 };
        // третья серия: восходящий косой, обратный с подшагом, рубящий двумя руками с разворотом
        Clip v1 = C(m, "v1", 0.66f, 0.1f).strike(S(0.11f, 0.14f, 0.25f, 0.42f, 45, -80, 100).dmg(1f).kb(0.4f).targets(3));
        v1.lean.key(0, 0).key(0.14f, 9).key(0.25f, -7).key(0.66f, 0);
        Clip v2 = C(m, "v2", 0.66f, 0.08f).strike(S(0.1f, 0.13f, 0.25f, 0.42f, -4, -95, 90).dmg(1.05f).kb(0.4f).targets(3));
        v2.spin.key(0, 0).key(0.13f, -25).key(0.25f, 25).key(0.66f, 0);
        v2.step(0.1f, 0.25f, 0.35f);
        Clip v3 = C(m, "v3", 1.0f, 0.08f).strike(S(0.24f, 0.3f, 0.42f, 0.74f, 110, 130, -50).heavy().two()
                .dmg(1.5f).kb(0.75f).targets(3).hitstop(2).shake(0.3f));
        v3.spin.key(0, 0).key(0.3f, -30).key(0.42f, 10).key(1.0f, 0);
        v3.lean.key(0, 0).key(0.3f, -10).key(0.42f, 18).key(1.0f, 0);
        v3.stance.key(0, 0).key(0.42f, 1).key(1.0f, 0);
        m.comboC = new Clip[] { v1, v2, v3 };
        m.dash = dash; m.air = air; m.skill = skill;
        add(m);
    }

    // ================================================================ 4. Лорд — четыре удара, вихрь, выпад; золотой след
    private static void lord() {
        Moveset m = new Moveset("lord_sword", 3, 1.85f)
                .trail(new float[] { 1f, 0.68f, 0.24f }, new float[] { 1f, 0.95f, 0.75f }, 0.65f, 0.14f, 1.1f, false);
        Clip c1 = C(m, "c1", 0.62f, 0.08f).strike(S(0.11f, 0.13f, 0.24f, 0.4f, 45, 105, -60).dmg(1f).kb(0.4f).targets(3).hitstop(1));
        c1.step(0.11f, 0.24f, 0.35f);
        c1.lean.key(0, 0).key(0.24f, 8).key(0.62f, 0);
        c1.spin.key(0, 0).key(0.13f, 10).key(0.24f, -15).key(0.62f, 0);
        Clip c2 = C(m, "c2", 0.62f, 0.08f).strike(S(0.1f, 0.12f, 0.23f, 0.4f, 135, 105, -60).dmg(1f).kb(0.4f).targets(3).hitstop(1));
        c2.spin.key(0, 0).key(0.12f, -10).key(0.23f, 15).key(0.62f, 0);
        Clip c3 = C(m, "c3", 0.85f, 0.08f).strike(S(0.14f, 0.16f, 0.42f, 0.58f, -5, 90, -270).dmg(1.2f).kb(0.6f)
                .targets(5).width(0.55f).hitstop(1));
        c3.spin.key(0, 0).key(0.16f, 0).key(0.42f, -360).key(0.85f, -360);
        c3.stance.key(0, 0).key(0.3f, -0.4f).key(0.85f, 0);
        c3.lean.key(0, 0).key(0.3f, 6).key(0.85f, 0);
        Clip c4 = C(m, "c4", 0.95f, 0.08f).strike(S(0.2f, 0.25f, 0.35f, 0.66f, 0, 0, 0).reach(1.1f, 2.4f).two()
                .dmg(1.55f).kb(0.9f).targets(3).hitstop(2).shake(0.35f));
        c4.step(0.23f, 0.35f, 1.3f);
        c4.lean.key(0, 0).key(0.25f, -5).key(0.35f, 16).key(0.95f, 0);
        c4.stance.key(0, 0).key(0.35f, 1).key(0.95f, 0);
        Clip dash = C(m, "dash", 0.9f, 0.08f).strike(S(0.16f, 0.24f, 0.36f, 0.62f, 90, 125, -45).heavy()
                .dmg(1.4f).kb(0.7f).targets(4).hitstop(2).shake(0.3f));
        dash.step(0.1f, 0.36f, 1.4f);
        dash.lift.key(0, 0).key(0.12f, 0).key(0.24f, 0.45f).key(0.36f, 0);
        dash.lean.key(0, 0).key(0.24f, -8).key(0.36f, 18).key(0.9f, 0);
        Clip air = C(m, "air", 0.85f, 0.08f).strike(S(0.08f, 0.1f, 0.36f, 0.55f, 90, 180, -180).dmg(1.4f).kb(0.6f)
                .targets(4).hitstop(1).shake(0.2f));
        air.lean.key(0, 0).key(0.36f, 30).key(0.85f, 0);
        air.lockMove = false;
        Clip skill = C(m, "consecrate", 1.2f, 0.1f).strike(S(0.25f, 0.3f, 0.55f, 1.0f, 90, 0, 92).targets(0).event("buff"));
        skill.lean.key(0, 0).key(0.3f, -4).key(0.55f, -10).key(1.2f, 0);
        skill.lift.key(0, 0).key(0.55f, 0.1f).key(1.2f, 0);
        skill.skill = true;
        m.combo = new Clip[] { c1, c2, c3, c4 };
        // вторая серия: быстрый укол, крест (два косых), восходящий двумя руками, круговой с шагом
        Clip b1 = C(m, "b1", 0.58f, 0.08f).strike(S(0.08f, 0.11f, 0.2f, 0.36f, 0, 0, 0).reach(1.0f, 2.0f).dmg(0.95f).kb(0.35f).targets(2).hitstop(1));
        b1.step(0.08f, 0.2f, 0.5f);
        b1.lean.key(0, 0).key(0.2f, 10).key(0.58f, 0);
        b1.stance.key(0, 0).key(0.2f, 1).key(0.58f, 0);
        Clip b2 = C(m, "b2", 0.76f, 0.08f)
                .strike(S(0.08f, 0.1f, 0.2f, 0.28f, 45, 110, -70).dmg(0.8f).kb(0.3f).targets(3))
                .strike(S(0.3f, 0.32f, 0.42f, 0.56f, 135, 110, -70).dmg(0.9f).kb(0.45f).targets(3).hitstop(1));
        b2.spin.key(0, 0).key(0.1f, 12).key(0.2f, -14).key(0.32f, -12).key(0.42f, 14).key(0.76f, 0);
        b2.lean.key(0, 0).key(0.2f, 6).key(0.42f, 8).key(0.76f, 0);
        Clip b3 = C(m, "b3", 0.82f, 0.08f).strike(S(0.16f, 0.2f, 0.32f, 0.56f, 85, -80, 130).two()
                .dmg(1.25f).kb(0.3f).targets(4).hitstop(2).shake(0.2f).event("launch"));
        b3.lean.key(0, 0).key(0.2f, 12).key(0.32f, -12).key(0.82f, 0);
        b3.lift.key(0, 0).key(0.2f, -0.06f).key(0.32f, 0.2f).key(0.82f, 0);
        b3.stance.key(0, 0).key(0.2f, 1).key(0.6f, 0);
        Clip b4 = C(m, "b4", 1.0f, 0.08f).strike(S(0.18f, 0.22f, 0.5f, 0.7f, -6, 95, -275).two()
                .dmg(1.4f).kb(0.8f).targets(6).width(0.6f).hitstop(2).shake(0.35f));
        b4.spin.key(0, 0).key(0.22f, 0).key(0.5f, -360).key(1.0f, -360);
        b4.step(0.2f, 0.5f, 0.9f);
        b4.stance.key(0, 0).key(0.3f, -0.5f).key(1.0f, 0);
        m.comboB = new Clip[] { b1, b2, b3, b4 };
        // третья серия: два быстрых укола, восходящий двумя руками, круговой удар двумя руками
        Clip v1 = C(m, "v1", 0.7f, 0.08f)
                .strike(S(0.06f, 0.08f, 0.16f, 0.24f, 0, 0, 0).reach(1.0f, 1.9f).dmg(0.75f).kb(0.2f).targets(2))
                .strike(S(0.26f, 0.28f, 0.36f, 0.5f, 0, 0, 0).reach(1.0f, 2.1f).dmg(0.9f).kb(0.4f).targets(2).hitstop(1));
        v1.step(0.06f, 0.16f, 0.3f).step(0.26f, 0.36f, 0.4f);
        v1.lean.key(0, 0).key(0.16f, 9).key(0.24f, 3).key(0.36f, 12).key(0.7f, 0);
        v1.stance.key(0, 0).key(0.16f, 1).key(0.24f, 0.6f).key(0.36f, 1).key(0.7f, 0);
        Clip v2 = C(m, "v2", 0.8f, 0.08f).strike(S(0.14f, 0.18f, 0.3f, 0.54f, 60, -85, 120).two()
                .dmg(1.2f).kb(0.3f).targets(4).hitstop(2).shake(0.2f).event("launch"));
        v2.lean.key(0, 0).key(0.18f, 12).key(0.3f, -12).key(0.8f, 0);
        v2.lift.key(0, 0).key(0.18f, -0.06f).key(0.3f, 0.18f).key(0.8f, 0);
        Clip v3 = C(m, "v3", 1.0f, 0.08f).strike(S(0.16f, 0.2f, 0.5f, 0.68f, -8, 95, -275).two()
                .dmg(1.4f).kb(0.8f).targets(6).width(0.6f).hitstop(2).shake(0.35f));
        v3.spin.key(0, 0).key(0.2f, 0).key(0.5f, -360).key(1.0f, -360);
        v3.stance.key(0, 0).key(0.3f, -0.5f).key(1.0f, 0);
        m.comboC = new Clip[] { v1, v2, v3 };
        m.dash = dash; m.air = air; m.skill = skill;
        add(m);
    }

    // ================================================================ 5. Верховный лорд — двойные удары, прыжок, широкий взмах
    private static void highLord() {
        Moveset m = new Moveset("high_lord_sword", 4, 1.95f)
                .trail(new float[] { 1f, 0.8f, 0.42f }, new float[] { 1f, 1f, 0.92f }, 0.75f, 0.16f, 1.25f, true);
        Clip c1 = C(m, "c1", 0.76f, 0.08f)
                .strike(S(0.08f, 0.1f, 0.2f, 0.28f, 5, 80, -70).dmg(0.85f).kb(0.3f).targets(3))
                .strike(S(0.3f, 0.32f, 0.42f, 0.58f, -5, -70, 80).dmg(0.95f).kb(0.45f).targets(3).hitstop(1));
        c1.step(0.08f, 0.2f, 0.3f).step(0.3f, 0.42f, 0.3f);
        c1.spin.key(0, 0).key(0.1f, 12).key(0.2f, -18).key(0.32f, -15).key(0.42f, 18).key(0.76f, 0);
        c1.lean.key(0, 0).key(0.2f, 6).key(0.42f, 6).key(0.76f, 0);
        Clip c2 = C(m, "c2", 0.78f, 0.08f).strike(S(0.12f, 0.15f, 0.42f, 0.56f, -15, 90, -270).dmg(1.2f).kb(0.6f)
                .targets(6).width(0.6f).hitstop(1));
        c2.spin.key(0, 0).key(0.15f, 0).key(0.42f, -360).key(0.78f, -360);
        c2.lift.key(0, 0).key(0.25f, 0.15f).key(0.42f, 0);
        Clip c3 = C(m, "c3", 0.98f, 0.08f).strike(S(0.22f, 0.3f, 0.42f, 0.72f, 90, 130, -45).heavy().two()
                .dmg(1.5f).kb(0.8f).targets(4).hitstop(2).shake(0.4f).event("shock"));
        c3.step(0.12f, 0.42f, 1.0f);
        c3.lift.key(0, 0).key(0.14f, 0).key(0.3f, 0.65f).key(0.42f, 0);
        c3.lean.key(0, 0).key(0.3f, -12).key(0.42f, 20).key(0.98f, 0);
        c3.stance.key(0, 0).key(0.42f, 1).key(0.98f, 0);
        Clip c4 = C(m, "c4", 0.98f, 0.08f).strike(S(0.18f, 0.22f, 0.36f, 0.72f, -15, 115, -115).dmg(1.65f).kb(1f)
                .targets(6).width(0.7f).hitstop(3).shake(0.5f).event("burst"));
        c4.spin.key(0, 0).key(0.22f, 30).key(0.36f, -40).key(0.98f, 0);
        c4.lean.key(0, 0).key(0.36f, 10).key(0.98f, 0);
        c4.stance.key(0, 0).key(0.36f, 1).key(0.98f, 0);
        Clip dash = C(m, "dash", 0.82f, 0.06f).strike(S(0.1f, 0.13f, 0.3f, 0.52f, 0, 90, -270).dmg(1.35f).kb(0.6f)
                .targets(6).width(0.6f).hitstop(1));
        dash.spin.key(0, 0).key(0.13f, 0).key(0.3f, -360).key(0.82f, -360);
        dash.step(0.05f, 0.3f, 1.6f);
        Clip air = C(m, "air", 0.9f, 0.08f).strike(S(0.1f, 0.14f, 0.3f, 0.56f, 92, 130, -75).heavy().dmg(1.55f).kb(0.7f)
                .targets(4).hitstop(2).shake(0.35f).event("shock"));
        air.lean.key(0, 0).key(0.3f, 20).key(0.9f, 0);
        air.lockMove = false;
        Clip skill = C(m, "cleave", 1.1f, 0.1f).strike(S(0.22f, 0.3f, 0.42f, 0.8f, 75, 120, -60).heavy().two()
                .dmg(1.8f).kb(0.9f).targets(6).width(0.7f).hitstop(2).shake(0.45f).event("wave"));
        skill.step(0.28f, 0.42f, 0.5f);
        skill.lean.key(0, 0).key(0.3f, -10).key(0.42f, 18).key(1.1f, 0);
        skill.stance.key(0, 0).key(0.42f, 1).key(1.1f, 0);
        skill.skill = true;
        m.combo = new Clip[] { c1, c2, c3, c4 };
        // вторая серия: двойной обратный, восходящий с подскоком, удар с размаху двумя руками, широкий взмах
        Clip b1 = C(m, "b1", 0.74f, 0.08f)
                .strike(S(0.08f, 0.1f, 0.2f, 0.28f, -5, -75, 80).dmg(0.85f).kb(0.3f).targets(3))
                .strike(S(0.3f, 0.32f, 0.42f, 0.56f, 5, 80, -75).dmg(0.95f).kb(0.45f).targets(3).hitstop(1));
        b1.spin.key(0, 0).key(0.1f, -14).key(0.2f, 16).key(0.32f, 14).key(0.42f, -16).key(0.74f, 0);
        b1.step(0.08f, 0.2f, 0.3f).step(0.3f, 0.42f, 0.3f);
        Clip b2 = C(m, "b2", 0.8f, 0.08f).strike(S(0.12f, 0.16f, 0.28f, 0.5f, 92, -75, 130).dmg(1.15f).kb(0.3f)
                .targets(4).hitstop(2).shake(0.2f).event("launch"));
        b2.lift.key(0, 0).key(0.16f, -0.05f).key(0.28f, 0.3f).key(0.8f, 0);
        b2.lean.key(0, 0).key(0.16f, 12).key(0.28f, -14).key(0.8f, 0);
        Clip b3 = C(m, "b3", 1.0f, 0.08f).strike(S(0.24f, 0.32f, 0.44f, 0.74f, 90, 140, -50).heavy().two()
                .dmg(1.55f).kb(0.85f).targets(5).hitstop(3).shake(0.45f).event("shock"));
        b3.step(0.14f, 0.44f, 0.8f);
        b3.lift.key(0, 0).key(0.16f, 0).key(0.32f, 0.5f).key(0.44f, -0.08f).key(1.0f, 0);
        b3.lean.key(0, 0).key(0.32f, -14).key(0.44f, 22).key(1.0f, 0);
        b3.stance.key(0, 0).key(0.44f, 1).key(1.0f, 0);
        Clip b4 = C(m, "b4", 1.0f, 0.08f).strike(S(0.2f, 0.25f, 0.4f, 0.74f, 8, 130, -130).two()
                .dmg(1.6f).kb(1f).targets(6).width(0.7f).hitstop(3).shake(0.45f).event("burst"));
        b4.spin.key(0, 0).key(0.25f, 35).key(0.4f, -45).key(1.0f, 0);
        b4.lean.key(0, 0).key(0.4f, 10).key(1.0f, 0);
        b4.stance.key(0, 0).key(0.4f, -1).key(1.0f, 0);
        m.comboB = new Clip[] { b1, b2, b3, b4 };
        // третья серия: три быстрых разреза, разворот-вихрь, прыжок с ударом двумя руками
        Clip v1 = C(m, "v1", 0.82f, 0.08f)
                .strike(S(0.06f, 0.08f, 0.16f, 0.22f, 20, 80, -70).dmg(0.7f).kb(0.2f).targets(3))
                .strike(S(0.24f, 0.26f, 0.34f, 0.4f, 160, 80, -70).dmg(0.75f).kb(0.25f).targets(3))
                .strike(S(0.42f, 0.44f, 0.54f, 0.66f, 90, 110, -40).dmg(0.95f).kb(0.5f).targets(3).hitstop(2).shake(0.2f));
        v1.spin.key(0, 0).key(0.08f, 12).key(0.16f, -14).key(0.26f, -12).key(0.34f, 12).key(0.54f, 0).key(0.82f, 0);
        v1.lean.key(0, 0).key(0.44f, -6).key(0.54f, 14).key(0.82f, 0);
        v1.step(0.42f, 0.54f, 0.4f);
        Clip v2 = C(m, "v2", 0.82f, 0.06f).strike(S(0.1f, 0.12f, 0.4f, 0.56f, 6, 90, -270).dmg(1.2f).kb(0.6f)
                .targets(6).width(0.6f).hitstop(1));
        v2.spin.key(0, 0).key(0.12f, 0).key(0.4f, -360).key(0.82f, -360);
        v2.lift.key(0, 0).key(0.25f, 0.12f).key(0.4f, 0);
        Clip v3 = C(m, "v3", 1.05f, 0.08f).strike(S(0.26f, 0.34f, 0.46f, 0.78f, 90, 145, -50).heavy().two()
                .dmg(1.6f).kb(0.9f).targets(5).hitstop(3).shake(0.5f).event("shock"));
        v3.lift.key(0, 0).key(0.12f, 0).key(0.32f, 0.7f).key(0.46f, -0.08f).key(1.05f, 0);
        v3.lean.key(0, 0).key(0.34f, -14).key(0.46f, 22).key(1.05f, 0);
        v3.step(0.14f, 0.46f, 0.9f);
        v3.stance.key(0, 0).key(0.46f, 1).key(1.05f, 0);
        m.comboC = new Clip[] { v1, v2, v3 };
        m.dash = dash; m.air = air; m.skill = skill;
        add(m);
    }

    // ================================================================ 6. Генерал — пять ударов: крест, вихрь, подброс, удар с небес, луч
    private static void general() {
        Moveset m = new Moveset("general_sword", 5, 2.25f)
                .trail(new float[] { 1f, 0.74f, 0.3f }, new float[] { 1f, 1f, 0.95f }, 0.9f, 0.2f, 1.45f, true);
        Clip c1 = C(m, "c1", 0.78f, 0.08f)
                .strike(S(0.08f, 0.1f, 0.2f, 0.28f, 45, 110, -65).dmg(0.85f).kb(0.3f).targets(4).hitstop(1))
                .strike(S(0.3f, 0.32f, 0.42f, 0.58f, 135, 110, -65).dmg(0.95f).kb(0.5f).targets(4).hitstop(2).shake(0.2f));
        c1.step(0.08f, 0.2f, 0.35f).step(0.3f, 0.42f, 0.35f);
        c1.spin.key(0, 0).key(0.1f, 14).key(0.2f, -18).key(0.32f, -14).key(0.42f, 18).key(0.78f, 0);
        c1.lean.key(0, 0).key(0.2f, 8).key(0.42f, 8).key(0.78f, 0);
        Clip c2 = C(m, "c2", 0.85f, 0.06f).strike(S(0.1f, 0.12f, 0.48f, 0.62f, -10, 90, -450).dmg(1.25f).kb(0.6f)
                .targets(8).width(0.65f).hitstop(1));
        c2.spin.key(0, 0).key(0.12f, 0).key(0.48f, -540).key(0.85f, -540);
        c2.lift.key(0, 0).key(0.2f, 0.2f).key(0.48f, 0);
        c2.stance.key(0, 0).key(0.3f, -0.5f).key(0.85f, 0);
        Clip c3 = C(m, "c3", 0.74f, 0.06f).strike(S(0.12f, 0.15f, 0.26f, 0.5f, 92, -70, 125).dmg(1.2f).kb(0.2f)
                .targets(5).hitstop(2).shake(0.25f).event("launch"));
        c3.lean.key(0, 0).key(0.15f, 10).key(0.26f, -14).key(0.74f, 0);
        c3.lift.key(0, 0).key(0.26f, 0.2f).key(0.74f, 0);
        c3.stance.key(0, 0).key(0.15f, 1).key(0.5f, 0);
        Clip c4 = C(m, "c4", 1.05f, 0.06f).strike(S(0.2f, 0.32f, 0.46f, 0.78f, 90, 140, -55).heavy().two()
                .dmg(1.6f).kb(0.9f).targets(6).width(0.7f).hitstop(3).shake(0.6f).event("shock"));
        c4.lift.key(0, 0).key(0.1f, 0).key(0.3f, 1.1f).key(0.46f, 0);
        c4.lean.key(0, 0).key(0.32f, -14).key(0.46f, 22).key(1.05f, 0);
        c4.step(0.15f, 0.46f, 0.9f);
        c4.stance.key(0, 0).key(0.46f, 1).key(1.05f, 0);
        Clip c5 = C(m, "c5", 1.1f, 0.06f).strike(S(0.25f, 0.3f, 0.4f, 0.84f, 0, 0, 0).reach(1.2f, 2.9f).two()
                .dmg(2f).kb(1.3f).targets(6).width(0.65f).hitstop(4).shake(0.8f).event("burst"));
        c5.step(0.28f, 0.4f, 1.6f);
        c5.lean.key(0, 0).key(0.3f, -6).key(0.4f, 18).key(1.1f, 0);
        c5.stance.key(0, 0).key(0.4f, 1).key(1.1f, 0);
        Clip dash = C(m, "dash", 0.82f, 0.06f).strike(S(0.08f, 0.1f, 0.32f, 0.5f, -5, 90, -450).dmg(1.4f).kb(0.7f)
                .targets(8).width(0.6f).hitstop(2).shake(0.3f));
        dash.spin.key(0, 0).key(0.1f, 0).key(0.32f, -540).key(0.82f, -540);
        dash.step(0.04f, 0.32f, 2.2f);
        Clip air = C(m, "air", 0.95f, 0.08f).strike(S(0.1f, 0.16f, 0.3f, 0.6f, 90, 140, -80).heavy().dmg(1.7f).kb(0.8f)
                .targets(6).hitstop(3).shake(0.5f).event("shock"));
        air.lean.key(0, 0).key(0.3f, 22).key(0.95f, 0);
        air.lockMove = false;
        Clip skill = C(m, "judgment", 1.5f, 0.1f).strike(S(0.4f, 0.5f, 0.62f, 1.1f, 90, 150, -60).heavy().two()
                .dmg(3f).kb(1.5f).targets(10).width(0.9f).hitstop(5).shake(1f).event("judgment"));
        skill.leap(0.14f, 0.85f, 0.5f, -1.6f);
        skill.lean.key(0, 0).key(0.2f, -10).key(0.5f, -14).key(0.62f, 24).key(1.5f, 0);
        skill.stance.key(0, 0).key(0.62f, 1).key(1.5f, 0);
        skill.skill = true;
        m.combo = new Clip[] { c1, c2, c3, c4, c5 };
        // вторая серия: крест, восходящий двумя руками, вихрь в прыжке, удар с небес, пронзающий выпад
        Clip b1 = C(m, "b1", 0.74f, 0.06f)
                .strike(S(0.07f, 0.09f, 0.18f, 0.26f, 135, 110, -70).dmg(0.85f).kb(0.3f).targets(4).hitstop(1))
                .strike(S(0.28f, 0.3f, 0.4f, 0.54f, 45, 110, -70).dmg(0.95f).kb(0.45f).targets(4).hitstop(2).shake(0.2f));
        b1.spin.key(0, 0).key(0.09f, -12).key(0.18f, 16).key(0.3f, 14).key(0.4f, -16).key(0.74f, 0);
        b1.step(0.07f, 0.18f, 0.35f).step(0.28f, 0.4f, 0.35f);
        Clip b2 = C(m, "b2", 0.8f, 0.06f).strike(S(0.12f, 0.16f, 0.28f, 0.5f, 88, -80, 135).two()
                .dmg(1.25f).kb(0.25f).targets(6).hitstop(2).shake(0.3f).event("launch"));
        b2.lean.key(0, 0).key(0.16f, 14).key(0.28f, -16).key(0.8f, 0);
        b2.lift.key(0, 0).key(0.16f, -0.06f).key(0.28f, 0.35f).key(0.8f, 0);
        b2.stance.key(0, 0).key(0.16f, 1).key(0.5f, 0);
        Clip b3 = C(m, "b3", 0.9f, 0.06f).strike(S(0.12f, 0.14f, 0.44f, 0.6f, -12, 90, -450).dmg(1.25f).kb(0.6f)
                .targets(8).width(0.65f).hitstop(1));
        b3.spin.key(0, 0).key(0.14f, 0).key(0.44f, -540).key(0.9f, -540);
        b3.lift.key(0, 0).key(0.14f, 0).key(0.3f, 0.45f).key(0.44f, 0).key(0.9f, 0);
        Clip b4 = C(m, "b4", 1.08f, 0.06f).strike(S(0.24f, 0.34f, 0.46f, 0.8f, 90, 145, -55).heavy().two()
                .dmg(1.7f).kb(0.95f).targets(6).width(0.7f).hitstop(4).shake(0.65f).event("shock"));
        b4.lift.key(0, 0).key(0.1f, 0).key(0.32f, 1.0f).key(0.46f, -0.1f).key(1.08f, 0);
        b4.lean.key(0, 0).key(0.34f, -16).key(0.46f, 24).key(1.08f, 0);
        b4.step(0.12f, 0.46f, 1.0f);
        b4.stance.key(0, 0).key(0.46f, 1).key(1.08f, 0);
        Clip b5 = C(m, "b5", 1.1f, 0.06f).strike(S(0.24f, 0.3f, 0.4f, 0.84f, 0, 0, 0).reach(1.1f, 3.0f).two()
                .dmg(2f).kb(1.3f).targets(6).width(0.7f).hitstop(4).shake(0.8f).event("burst"));
        b5.step(0.26f, 0.4f, 1.8f);
        b5.lean.key(0, 0).key(0.3f, -8).key(0.4f, 20).key(1.1f, 0);
        b5.stance.key(0, 0).key(0.4f, 1).key(1.1f, 0);
        m.comboB = new Clip[] { b1, b2, b3, b4, b5 };
        // третья серия: шквал из трёх ударов, восходящий крест, вихрь двумя руками со взрывом
        Clip v1 = C(m, "v1", 0.8f, 0.06f)
                .strike(S(0.05f, 0.07f, 0.15f, 0.2f, 30, 90, -70).dmg(0.75f).kb(0.2f).targets(4))
                .strike(S(0.22f, 0.24f, 0.32f, 0.38f, 150, 90, -70).dmg(0.8f).kb(0.25f).targets(4))
                .strike(S(0.4f, 0.42f, 0.52f, 0.64f, 0, 0, 0).reach(1.1f, 2.3f).dmg(1f).kb(0.5f).targets(4).hitstop(2).shake(0.25f));
        v1.spin.key(0, 0).key(0.07f, 12).key(0.15f, -14).key(0.24f, -12).key(0.32f, 14).key(0.42f, 0).key(0.8f, 0);
        v1.step(0.4f, 0.52f, 0.6f);
        v1.lean.key(0, 0).key(0.42f, -4).key(0.52f, 14).key(0.8f, 0);
        v1.stance.key(0, 0).key(0.52f, 1).key(0.8f, 0);
        Clip v2 = C(m, "v2", 0.88f, 0.06f)
                .strike(S(0.08f, 0.1f, 0.2f, 0.28f, 45, -80, 110).dmg(0.9f).kb(0.25f).targets(5).hitstop(1))
                .strike(S(0.3f, 0.32f, 0.44f, 0.6f, 135, -80, 110).two().dmg(1.05f).kb(0.3f).targets(5).hitstop(2).shake(0.3f).event("launch"));
        v2.lean.key(0, 0).key(0.1f, 10).key(0.2f, -8).key(0.32f, 10).key(0.44f, -14).key(0.88f, 0);
        v2.lift.key(0, 0).key(0.32f, -0.05f).key(0.44f, 0.3f).key(0.88f, 0);
        Clip v3 = C(m, "v3", 1.1f, 0.06f).strike(S(0.18f, 0.22f, 0.6f, 0.78f, -10, 95, -450).two()
                .dmg(1.7f).kb(1.1f).targets(8).width(0.7f).hitstop(3).shake(0.6f).event("burst"));
        v3.spin.key(0, 0).key(0.22f, 0).key(0.6f, -540).key(1.1f, -540);
        v3.lift.key(0, 0).key(0.22f, 0).key(0.4f, 0.3f).key(0.6f, 0).key(1.1f, 0);
        v3.stance.key(0, 0).key(0.3f, -0.5f).key(1.1f, 0);
        m.comboC = new Clip[] { v1, v2, v3 };
        m.dash = dash; m.air = air; m.skill = skill;
        add(m);
    }
}
