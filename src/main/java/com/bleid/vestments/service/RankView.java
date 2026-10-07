package com.bleid.vestments.service;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;

/** Проверка сана игрока на обеих сторонах. Клиент знает свой сан из пакетов ServicePoints. */
public final class RankView {
    /** Только клиент: священник ли локальный игрок и сколько у него очков (итог с сервера). */
    public static volatile boolean clientPriest;
    public static volatile int clientPoints;

    private RankView() { }

    public static int clientRank() {
        return clientPriest ? Ranks.rankFor(clientPoints) : -1;
    }

    /** Достиг ли игрок сана rank. */
    public static boolean has(PlayerEntity player, int rank) {
        if (player instanceof ServerPlayerEntity sp) {
            return ServicePoints.canServe(sp) && ServicePoints.rank(sp) >= rank;
        }
        return clientRank() >= rank;
    }
}
