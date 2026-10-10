package com.bleid.vestments.paladin.combat;

/** Боевой стиль одного меча: серия ударов, удар с разбега, удар в прыжке, особый приём и вид следа. */
public final class Moveset {
    public final String id;
    public final int tier;          // 0 — младший рекрут … 5 — генерал
    public final float length;      // длина клинка с рукой (вылет от плеча), блоки
    public Clip[] combo;
    /** Вторая серия — начинается, если игрок бьёт на ходу (другие удары той же стойки). */
    public Clip[] comboB = new Clip[0];
    /** Третья серия — при движении вбок или назад (уклоняющиеся, круговые удары). */
    public Clip[] comboC = new Clip[0];
    public Clip dash, air, skill, draw, sheathe;
    // след клинка
    public float[] trail = { 1f, 1f, 1f };
    public float[] trailCore = { 1f, 1f, 1f };
    public float trailAlpha = 0.6f, trailLife = 0.12f, trailWidth = 1f;
    public boolean sparkles;

    public Moveset(String id, int tier, float length) {
        this.id = id;
        this.tier = tier;
        this.length = length;
    }

    public Moveset trail(float[] color, float[] core, float alpha, float life, float width, boolean sparkles) {
        this.trail = color;
        this.trailCore = core;
        this.trailAlpha = alpha;
        this.trailLife = life;
        this.trailWidth = width;
        this.sparkles = sparkles;
        return this;
    }

    /** Приём по коду запроса: 0.. — серия, 50.. — вторая серия, 100 — с разбега, 101 — в прыжке, 200 — особый. */
    public Clip byKind(int kind) {
        if (kind == 100) return dash;
        if (kind == 101) return air;
        if (kind == 200) return skill;
        if (kind >= 70 && kind < 70 + comboC.length) return comboC[kind - 70];
        if (kind >= 50 && kind < 50 + comboB.length) return comboB[kind - 50];
        if (kind >= 0 && kind < combo.length) return combo[kind];
        return null;
    }
}
