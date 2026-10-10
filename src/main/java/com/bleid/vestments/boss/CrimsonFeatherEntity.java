package com.bleid.vestments.boss;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;
import org.joml.Vector3f;

/** Багровое перо, которым стреляет серафим: летит прямо, без гравитации, оставляет красный след. */
public class CrimsonFeatherEntity extends ThrownItemEntity {
    private static final DustParticleEffect TRAIL = new DustParticleEffect(new Vector3f(0.75f, 0.08f, 0.08f), 1.1f);
    float damage = 7f;

    public CrimsonFeatherEntity(EntityType<? extends CrimsonFeatherEntity> type, World world) {
        super(type, world);
    }

    public CrimsonFeatherEntity(World world, LivingEntity owner, float damage) {
        super(Bosses.CRIMSON_FEATHER_PROJECTILE, owner, world);
        this.damage = damage;
    }

    @Override
    protected Item getDefaultItem() {
        return Bosses.CRIMSON_FEATHER;
    }

    @Override
    protected float getGravity() {
        return 0f;
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) {
            getWorld().addParticle(TRAIL, getX(), getY() + 0.1, getZ(), 0, 0, 0);
        } else if (age > 80) {
            discard();
        }
    }

    @Override
    protected void onEntityHit(EntityHitResult hit) {
        super.onEntityHit(hit);
        Entity e = hit.getEntity();
        Entity owner = getOwner();
        if (e == owner || e instanceof CrimsonFeatherEntity) return;
        e.damage(getWorld().getDamageSources().mobProjectile(this, owner instanceof LivingEntity l ? l : null), damage);
    }

    @Override
    protected void onCollision(HitResult hit) {
        super.onCollision(hit);
        if (!getWorld().isClient) {
            getWorld().sendEntityStatus(this, (byte) 3);
            discard();
        }
    }

    @Override
    public void handleStatus(byte status) {
        if (status == 3) {
            for (int i = 0; i < 6; i++) {
                getWorld().addParticle(TRAIL, getX(), getY(), getZ(), random.nextGaussian() * 0.1, random.nextGaussian() * 0.1, random.nextGaussian() * 0.1);
            }
        } else {
            super.handleStatus(status);
        }
    }
}
