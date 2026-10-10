package com.bleid.vestments.classes;

import com.bleid.vestments.Vestments;
import com.bleid.vestments.fx.Fx;
import com.bleid.vestments.paladin.combat.CombatServer;
import com.bleid.vestments.paladin.combat.Movesets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;

/**
 * Увороты паладина и священника (по образцу Epic Fight): кувырок в сторону движения или отскок назад.
 * Короткое окно неуязвимости к ударам и снарядам; перезарядка ~0.9 с; немного утомляет (голод).
 */
public final class Dodge {
    public static final Identifier PACKET = new Identifier(Vestments.MOD_ID, "dodge");
    public static final int COOLDOWN = 18, IFRAMES = 8;

    private static final Map<UUID, long[]> STATE = new HashMap<>();   // {готов с, неуязвим до}

    private Dodge() { }

    public static boolean allowed(String cls) {
        return "paladin".equals(cls) || "priest".equals(cls);
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(PACKET, (server, player, handler, buf, sender) -> {
            int dir = buf.readVarInt();
            server.execute(() -> dodge(player, dir));
        });
        ServerPlayConnectionEvents.DISCONNECT.register((h, s) -> STATE.remove(h.getPlayer().getUuid()));
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayerEntity p)) return true;
            long[] st = STATE.get(p.getUuid());
            if (st == null || p.getServerWorld().getTime() > st[1] || !dodgeable(source)) return true;
            // идеальный уворот: удар проходит мимо
            Fx.play(p.getServerWorld(), "pal_dash_trail", p.getPos(), null, 0, 0, 0, 0);
            p.getServerWorld().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_NODAMAGE,
                    SoundCategory.PLAYERS, 0.8f, 1.6f);
            return false;
        });
    }

    private static boolean dodgeable(DamageSource s) {
        if (s.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        return s.getAttacker() != null || s.getSource() != null || s.isIn(DamageTypeTags.IS_PROJECTILE)
                || s.isIn(DamageTypeTags.IS_EXPLOSION);
    }

    private static void dodge(ServerPlayerEntity p, int dir) {
        if (dir < 0 || dir > 3 || !p.isAlive() || p.isSpectator() || !allowed(PlayerClasses.of(p))) return;
        long now = p.getServerWorld().getTime();
        long[] st = STATE.computeIfAbsent(p.getUuid(), k -> new long[2]);
        if (now < st[0] - 2) return;
        st[0] = now + COOLDOWN;
        st[1] = now + IFRAMES;
        p.addExhaustion(0.4f);
        p.fallDistance = 0;
        CombatServer.cancel(p);
        CombatServer.broadcast(p, Movesets.DODGE[dir]);
        p.getServerWorld().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP,
                SoundCategory.PLAYERS, 0.35f, 1.7f);
    }
}
