package com.bleid.vestments.service;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;

/** Проверка сана игрока на обеих сторонах. Клиент знает свой сан из пакетов ServicePoints. */
public final class RankView {
    /** Только клиент: священник ли локальный игрок и сколько у него очков (итог с сервера). */
    public static volatile boolean clientPriest;
    public static volatile int clientPoints;
    /** Только клиент: класс локального игрока ("" — не выбран). */
    public static volatile String clientClass = "";

    private RankView() { }

    public static int clientRank() {
        return clientPriest ? Ranks.rankFor(clientPoints) : -1;
    }

    public static boolean clientPaladin() {
        return "paladin".equals(clientClass);
    }

    public static int clientPaladinRank() {
        return clientPaladin() ? com.bleid.vestments.paladin.PaladinRanks.rankFor(clientPoints) : -1;
    }

    /** Класс игрока на обеих сторонах. */
    public static String classOf(PlayerEntity player) {
        if (player instanceof ServerPlayerEntity sp) {
            String c = com.bleid.vestments.classes.PlayerClasses.of(sp);
            return c == null ? "" : c;
        }
        return clientClass;
    }

    /** Достиг ли паладин звания rank. */
    public static boolean hasPaladin(PlayerEntity player, int rank) {
        if (player instanceof ServerPlayerEntity sp) {
            return ServicePoints.isPaladin(sp)
                    && com.bleid.vestments.paladin.PaladinRanks.rankFor(ServicePoints.get(sp.getServer(), sp.getUuid())) >= rank;
        }
        return clientPaladinRank() >= rank;
    }

    /** Достиг ли игрок сана rank. */
    public static boolean has(PlayerEntity player, int rank) {
        if (player instanceof ServerPlayerEntity sp) {
            return ServicePoints.canServe(sp) && ServicePoints.rank(sp) >= rank;
        }
        return clientRank() >= rank;
    }
}
