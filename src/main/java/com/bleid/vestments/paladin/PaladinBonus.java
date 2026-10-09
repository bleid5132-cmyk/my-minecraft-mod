package com.bleid.vestments.paladin;

import com.bleid.vestments.fx.Fx;
import com.bleid.vestments.service.RankView;
import com.bleid.vestments.service.ServicePoints;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

/** Способности доспехов и щитов паладина. */
public final class PaladinBonus {
    private static final UUID MELEE_ID = UUID.fromString("2f6f0c8e-5a51-4b8f-9d41-7a0b7c3e1a11");
    private static final UUID KNOCK_ID = UUID.fromString("2f6f0c8e-5a51-4b8f-9d41-7a0b7c3e1a12");
    private static final int UNDYING_COOLDOWN = 180 * 20;
    private static final Map<UUID, Long> UNDYING = new HashMap<>();

    private PaladinBonus() { }

    /** Действующий комплект: паладин, полный комплект, звание достигнуто. */
    public static PaladinSet activeSet(LivingEntity e) {
        if (!(e instanceof PlayerEntity p)) return null;
        PaladinSet s = PaladinGear.fullSet(e);
        if (s == null || !RankView.hasPaladin(p, s.rank)) return null;
        return s;
    }

    /** «Стойкость»: снижение итогового урона (вызывается из миксина после брони и зачарований). */
    public static float reduce(LivingEntity e, DamageSource src, float amount) {
        if (e.getWorld().isClient || src.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) return amount;
        PaladinSet s = activeSet(e);
        return s == null ? amount : amount * (1f - s.reduce);
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) tick(p);
        });

        // «Несокрушимость» генерала: смертельный удар раз в 3 минуты оставляет в живых
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayerEntity p)) return true;
            PaladinSet s = activeSet(p);
            if (s == null || !s.undying || source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) return true;
            long now = p.getServer().getTicks();
            if (UNDYING.getOrDefault(p.getUuid(), -1_000_000L) + UNDYING_COOLDOWN > now) return true;
            UNDYING.put(p.getUuid(), now);
            p.setHealth(6f);
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 200, 1));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 100, 2));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 120, 1));
            ServerWorld w = p.getServerWorld();
            w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 0.8f, 1.3f);
            Fx.play(w, "pal_undying", p, 40, 0);
            p.sendMessage(Text.translatable("message.vestments.undying").formatted(Formatting.GOLD), true);
            return false;
        });

        // щиты паладина: отпор, парирование, свет щита
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((target, source, amount) -> {
            if (!(target instanceof ServerPlayerEntity p) || !p.isBlocking()) return true;
            ItemStack active = p.getActiveItem();
            if (!(active.getItem() instanceof PaladinShieldItem shield) || !blocks(p, source)) return true;
            ServerWorld w = p.getServerWorld();
            active.damage(Math.max(1, (int) Math.ceil(amount * 0.5f)), p, e -> e.sendToolBreakStatus(p.getActiveHand()));
            boolean ranked = RankView.hasPaladin(p, shield.rank);
            Entity att = source.getAttacker();
            Vec3d at = p.getEyePos().add(p.getRotationVec(1f).multiply(0.8)).add(0, -0.4, 0);
            Fx.play(w, "pal_block", at, null, 0, shield.rank, 0, 0);
            if (!ranked) return true;
            if (att instanceof LivingEntity le && !source.isIn(net.minecraft.registry.tag.DamageTypeTags.IS_PROJECTILE)) {
                if (shield.bash()) {
                    Vec3d d = le.getPos().subtract(p.getPos()).normalize();
                    le.takeKnockback(0.6 + shield.rank * 0.12, -d.x, -d.z);
                }
                boolean parry = shield.parry() && p.getItemUseTime() <= 10;
                if (parry) {
                    le.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 50, 3));
                    le.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 50, 1));
                    w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 0.5f, 1.8f);
                    Fx.play(w, "pal_parry", at, null, 0, shield.rank, 0, 0);
                }
                if (shield.radiant()) {
                    le.damage(w.getDamageSources().thorns(p), amount * 0.3f);
                }
            }
            if (shield.radiant()) {
                p.heal(1f);
            }
            return true;
        });
    }

    /** Как ванильная проверка щита: блок держится и удар пришёл спереди. */
    private static boolean blocks(ServerPlayerEntity p, DamageSource source) {
        if (source.isIn(DamageTypeTags.BYPASSES_SHIELD)) return false;
        Vec3d from = source.getPosition();
        if (from == null) return false;
        Vec3d look = p.getRotationVec(1f);
        Vec3d rel = from.relativize(p.getPos()).normalize();
        rel = new Vec3d(rel.x, 0, rel.z);
        return rel.dotProduct(look) < 0;
    }

    private static void tick(ServerPlayerEntity p) {
        PaladinSet s = p.isAlive() ? activeSet(p) : null;
        setModifier(p, EntityAttributes.GENERIC_ATTACK_DAMAGE, MELEE_ID, "Paladin melee",
                s != null ? s.melee : 0, EntityAttributeModifier.Operation.MULTIPLY_TOTAL, s != null);
        setModifier(p, EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, KNOCK_ID, "Paladin footing",
                s != null ? s.knockback : 0, EntityAttributeModifier.Operation.ADDITION, s != null);
        if (s == null) return;
        if (s.slowImmune && p.hasStatusEffect(StatusEffects.SLOWNESS)) p.removeStatusEffect(StatusEffects.SLOWNESS);
        if (s.auraInterval > 0 && p.age % s.auraInterval == 0) {
            ServerWorld w = p.getServerWorld();
            double r = s.auraRadius;
            boolean any = false;
            for (PlayerEntity o : w.getPlayers()) {
                if (!o.isAlive() || o.isSpectator() || o.squaredDistanceTo(p) > r * r) continue;
                if (o.getHealth() >= o.getMaxHealth()) continue;
                o.heal(s.auraHeal);
                Fx.play(w, "heal_small", o, 0, 0);
                any = true;
            }
            if (any) Fx.play(w, "pal_aura", p, 0, (float) r);
        }
    }

    private static void setModifier(PlayerEntity player, EntityAttribute attribute, UUID id, String name, double value,
                                    EntityAttributeModifier.Operation op, boolean active) {
        EntityAttributeInstance inst = player.getAttributeInstance(attribute);
        if (inst == null) return;
        EntityAttributeModifier present = inst.getModifier(id);
        if (present != null && (!active || present.getValue() != value)) {
            inst.removeModifier(id);
            present = null;
        }
        if (active && present == null) inst.addTemporaryModifier(new EntityAttributeModifier(id, name, value, op));
    }

    static boolean isPaladin(PlayerEntity p) {
        return p instanceof ServerPlayerEntity sp && ServicePoints.isPaladin(sp);
    }
}
