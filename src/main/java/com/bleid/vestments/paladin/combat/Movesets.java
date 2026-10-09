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
        BY_SWORD.put(m.id, m);
    }

    public static Moveset of(String swordId) {
        return BY_SWORD.get(swordId);
    }

    static {
        junior();
        recruit();
        senior();
        lord();
        highLord();
        general();
    }

    // ================================================================ 1. Младший рекрут — просто и тяжеловато
    private static void junior() {
        Moveset m = new Moveset("junior_recruit_sword", 0, 1.45f)
                .trail(new float[] { 0.55f, 0.57f, 0.62f }, new float[] { 0.8f, 0.82f, 0.86f }, 0.35f, 0.08f, 0.7f, false);
        Clip c1 = C(m, "c1", 0.8f, 0.12f).strike(S(0.2f, 0.26f, 0.38f, 0.58f, 10, 70, -55).dmg(1f).kb(0.3f).targets(1));
        c1.lean.key(0, 0).key(0.26f, -4).key(0.38f, 8).key(0.8f, 0);
        c1.stance.key(0.3f, 0).key(0.38f, 0.4f).key(0.8f, 0);
        Clip c2 = C(m, "c2", 0.9f, 0.12f).strike(S(0.22f, 0.3f, 0.44f, 0.66f, 90, 100, -30).dmg(1.15f).kb(0.4f).targets(1));
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
        Clip c3 = C(m, "c3", 0.85f, 0.08f).strike(S(0.16f, 0.2f, 0.3f, 0.58f, 0, 0, 0).reach(1.0f, 1.95f)
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
        Clip skill = C(m, "crush", 1.1f, 0.1f).strike(S(0.3f, 0.38f, 0.5f, 0.8f, 90, 130, -45).heavy()
                .dmg(1.9f).kb(0.9f).targets(5).width(0.7f).shake(0.3f).hitstop(2).event("crush"));
        skill.lean.key(0, 0).key(0.38f, -12).key(0.5f, 20).key(1.1f, 0);
        skill.stance.key(0, 0).key(0.5f, 1).key(1.1f, 0);
        skill.lift.key(0, 0).key(0.3f, 0.15f).key(0.5f, 0);
        skill.skill = true;
        m.combo = new Clip[] { c1, c2, c3 };
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
        Clip c3 = C(m, "c3", 0.95f, 0.08f).strike(S(0.22f, 0.27f, 0.38f, 0.7f, 88, 120, -40).heavy()
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
        Clip c4 = C(m, "c4", 0.95f, 0.08f).strike(S(0.2f, 0.25f, 0.35f, 0.66f, 0, 0, 0).reach(1.1f, 2.4f)
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
        Clip c3 = C(m, "c3", 0.98f, 0.08f).strike(S(0.22f, 0.3f, 0.42f, 0.72f, 90, 130, -45).heavy()
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
        Clip skill = C(m, "cleave", 1.1f, 0.1f).strike(S(0.22f, 0.3f, 0.42f, 0.8f, 75, 120, -60).heavy()
                .dmg(1.8f).kb(0.9f).targets(6).width(0.7f).hitstop(2).shake(0.45f).event("wave"));
        skill.step(0.28f, 0.42f, 0.5f);
        skill.lean.key(0, 0).key(0.3f, -10).key(0.42f, 18).key(1.1f, 0);
        skill.stance.key(0, 0).key(0.42f, 1).key(1.1f, 0);
        skill.skill = true;
        m.combo = new Clip[] { c1, c2, c3, c4 };
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
        Clip c4 = C(m, "c4", 1.05f, 0.06f).strike(S(0.2f, 0.32f, 0.46f, 0.78f, 90, 140, -55).heavy()
                .dmg(1.6f).kb(0.9f).targets(6).width(0.7f).hitstop(3).shake(0.6f).event("shock"));
        c4.lift.key(0, 0).key(0.1f, 0).key(0.3f, 1.1f).key(0.46f, 0);
        c4.lean.key(0, 0).key(0.32f, -14).key(0.46f, 22).key(1.05f, 0);
        c4.step(0.15f, 0.46f, 0.9f);
        c4.stance.key(0, 0).key(0.46f, 1).key(1.05f, 0);
        Clip c5 = C(m, "c5", 1.1f, 0.06f).strike(S(0.25f, 0.3f, 0.4f, 0.84f, 0, 0, 0).reach(1.2f, 2.9f)
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
        Clip skill = C(m, "judgment", 1.5f, 0.1f).strike(S(0.4f, 0.5f, 0.62f, 1.1f, 90, 150, -60).heavy()
                .dmg(3f).kb(1.5f).targets(10).width(0.9f).hitstop(5).shake(1f).event("judgment"));
        skill.leap(0.14f, 0.85f, 0.5f, -1.6f);
        skill.lean.key(0, 0).key(0.2f, -10).key(0.5f, -14).key(0.62f, 24).key(1.5f, 0);
        skill.stance.key(0, 0).key(0.62f, 1).key(1.5f, 0);
        skill.skill = true;
        m.combo = new Clip[] { c1, c2, c3, c4, c5 };
        m.dash = dash; m.air = air; m.skill = skill;
        add(m);
    }
}
