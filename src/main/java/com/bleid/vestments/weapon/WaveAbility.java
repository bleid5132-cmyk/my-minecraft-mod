package com.bleid.vestments.weapon;

import com.bleid.vestments.StaffOfLightItem;
import com.bleid.vestments.gear.GearSet;
import java.util.List;
import net.minecraft.entity.EntityGroup;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/** «Веяние серафима»: взмах рипидой — волна света конусом вперёд отбрасывает и ранит враждебных мобов. */
public record WaveAbility(double range, double coneDeg, float damage, float knockback, int undeadFireSec)
        implements WeaponAbility {
    @Override
    public String nameKey() {
        return "ability.vestments.wave";
    }

    @Override
    public List<Text> describe() {
        return List.of(Text.translatable("tooltip.vestments.wave.desc", GearSet.num(range), GearSet.num(damage / 2.0))
                        .formatted(Formatting.GRAY),
                Text.translatable("tooltip.vestments.wave.undead", undeadFireSec).formatted(Formatting.GRAY));
    }

    @Override
    public void activate(ServerWorld world, ServerPlayerEntity p, float power) {
        Vec3d eye = p.getEyePos();
        Vec3d dir = p.getRotationVec(1f).normalize();
        double cos = Math.cos(Math.toRadians(coneDeg / 2));
        Box box = new Box(eye, eye).expand(range + 1);
        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, box, e -> e != p && e.isAlive())) {
            if (!StaffOfLightItem.canHit(e)) continue;
            Vec3d to = e.getPos().add(0, e.getHeight() * 0.5, 0).subtract(eye);
            double d = to.length();
            if (d > range || d < 1e-3 || to.multiply(1 / d).dotProduct(dir) < cos) continue;
            e.damage(world.getDamageSources().indirectMagic(p, p), damage * power);
            e.takeKnockback(knockback, -dir.x, -dir.z);
            if (e.getGroup() == EntityGroup.UNDEAD) e.setOnFireFor(undeadFireSec);
        }
        // волна: искры и световые штрихи веером
        for (int i = 1; i <= (int) range; i++) {
            for (int k = -2; k <= 2; k++) {
                double yaw = Math.toRadians(k * coneDeg / 5);
                Vec3d dd = rotateY(dir, yaw);
                Vec3d pt = eye.add(dd.multiply(i)).add(0, -0.3, 0);
                world.spawnParticles(ParticleTypes.END_ROD, pt.x, pt.y, pt.z, 0, dd.x, dd.y, dd.z, 0.25);
            }
        }
        Vec3d s = eye.add(dir.multiply(1.6));
        world.spawnParticles(ParticleTypes.SWEEP_ATTACK, s.x, s.y - 0.3, s.z, 1, 0, 0, 0, 0);
        world.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_PHANTOM_FLAP, SoundCategory.PLAYERS, 1.0f, 1.4f);
        world.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.0f, 1.2f);
    }

    private static Vec3d rotateY(Vec3d v, double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new Vec3d(v.x * c - v.z * s, v.y, v.x * s + v.z * c);
    }
}
