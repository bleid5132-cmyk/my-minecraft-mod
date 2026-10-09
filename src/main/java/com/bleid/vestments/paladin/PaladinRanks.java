package com.bleid.vestments.paladin;

/** Звания паладина по очкам доблести (те же очки за убийство враждебных мобов, что и у священника). */
public final class PaladinRanks {
    public static final String[] IDS = { "junior_recruit", "recruit", "senior_recruit", "lord", "high_lord", "general" };
    public static final int[] THRESHOLD = { 0, 60, 180, 400, 800, 1500 };

    private PaladinRanks() { }

    public static int count() {
        return IDS.length;
    }

    public static int rankFor(int points) {
        int r = 0;
        for (int i = 0; i < THRESHOLD.length; i++) if (points >= THRESHOLD[i]) r = i;
        return r;
    }

    public static boolean isMax(int rank) {
        return rank >= IDS.length - 1;
    }

    public static String nameKey(int rank) {
        return "rank.vestments.paladin." + IDS[rank];
    }
}
