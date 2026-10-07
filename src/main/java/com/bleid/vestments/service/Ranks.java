package com.bleid.vestments.service;

/**
 * Степени и саны священства. Сан определяется суммой очков служения.
 * I степень — Диаконат, II — Пресвитерство, III — Епископат (вершина — Патриарх).
 */
public final class Ranks {
    /** id санов по порядку, имя: rank.vestments.<id>. */
    public static final String[] IDS = {
            // I. Диаконат
            "deacon", "protodeacon", "hierodeacon", "archdeacon",
            // II. Пресвитерство
            "presbyter", "archpriest", "protopresbyter", "hieromonk", "hegumen", "archimandrite",
            // III. Епископат
            "vicar_bishop", "bishop", "archbishop", "metropolitan", "patriarch"
    };
    /** Степень каждого сана (0 — Диаконат, 1 — Пресвитерство, 2 — Епископат). */
    public static final int[] DEGREE = { 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2 };
    /** Сколько всего очков служения нужно для сана. */
    public static final int[] THRESHOLD = {
            0, 40, 100, 180,
            300, 450, 630, 850, 1100, 1400,
            1800, 2300, 2900, 3600, 4500
    };
    public static final String[] DEGREE_IDS = { "diaconate", "presbyterate", "episcopate" };

    private Ranks() { }

    public static int count() {
        return IDS.length;
    }

    public static int rankFor(int points) {
        int r = 0;
        for (int i = 0; i < THRESHOLD.length; i++) {
            if (points >= THRESHOLD[i]) r = i;
        }
        return r;
    }

    public static int degreeOf(int rank) {
        return DEGREE[rank];
    }

    /** Первый сан степени. */
    public static int degreeStart(int degree) {
        for (int i = 0; i < DEGREE.length; i++) if (DEGREE[i] == degree) return i;
        return 0;
    }

    public static int degreeSize(int degree) {
        int n = 0;
        for (int d : DEGREE) if (d == degree) n++;
        return n;
    }

    public static boolean isMax(int rank) {
        return rank >= IDS.length - 1;
    }

    /** Сила «Благословения» (лечение и урон луча) — растёт со степенью, у Патриарха максимум. */
    public static float power(int rank) {
        if (isMax(rank)) return 2.0f;
        return switch (DEGREE[rank]) {
            case 1 -> 1.25f;
            case 2 -> 1.5f;
            default -> 1.0f;
        };
    }

    public static String nameKey(int rank) {
        return "rank.vestments." + IDS[rank];
    }

    public static String degreeKey(int degree) {
        return "degree.vestments." + DEGREE_IDS[degree];
    }
}
