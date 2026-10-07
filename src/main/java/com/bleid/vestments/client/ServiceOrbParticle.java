package com.bleid.vestments.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Золотой огонёк очков служения. Вылетает фонтанчиком из места убийства или алтаря,
 * зависает на миг, затем по спирали летит к игроку, оставляя искристый след.
 * При касании игрока очки прибавляются к шкале служения.
 * Без цели — это искра следа: просто гаснет и уменьшается.
 */
@Environment(EnvType.CLIENT)
public class ServiceOrbParticle extends SpriteBillboardParticle {
    static SpriteProvider sprites;

    private final Entity target;
    private final int value;
    private final boolean credit;
    private final float baseScale;
    private final float spin;
    private boolean credited;

    /** Летящий огонёк. */
    public ServiceOrbParticle(ClientWorld world, double x, double y, double z, Entity target, int value, boolean credit) {
        super(world, x, y, z);
        this.target = target;
        this.value = value;
        this.credit = credit;
        this.collidesWithWorld = false;
        this.gravityStrength = 0f;
        this.maxAge = 140;
        this.baseScale = 0.11f + Math.min(value, 20) * 0.006f;
        this.scale = baseScale;
        this.spin = (random.nextBoolean() ? 1f : -1f) * (0.25f + random.nextFloat() * 0.2f);
        // фонтанчик вверх и в стороны
        double a = random.nextDouble() * Math.PI * 2;
        double h = 0.08 + random.nextDouble() * 0.12;
        this.velocityX = Math.cos(a) * h;
        this.velocityZ = Math.sin(a) * h;
        this.velocityY = 0.15 + random.nextDouble() * 0.15;
        setColor(1.0f, 0.86f, 0.38f);
        if (sprites != null) setSprite(sprites.getSprite(random));
    }

    /** Искра следа. */
    private ServiceOrbParticle(ClientWorld world, double x, double y, double z, float scale) {
        super(world, x, y, z);
        this.target = null;
        this.value = 0;
        this.credit = false;
        this.collidesWithWorld = false;
        this.gravityStrength = -0.02f;
        this.maxAge = 10 + random.nextInt(8);
        this.baseScale = scale;
        this.scale = scale;
        this.spin = 0;
        this.velocityX = (random.nextDouble() - 0.5) * 0.01;
        this.velocityY = 0.005;
        this.velocityZ = (random.nextDouble() - 0.5) * 0.01;
        setColor(1.0f, 0.93f, 0.6f);
        if (sprites != null) setSprite(sprites.getSprite(random));
    }

    @Override
    public ParticleTextureSheet getType() {
        return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    protected int getBrightness(float tint) {
        return 0xF000F0;   // светится в темноте
    }

    @Override
    public float getSize(float tickDelta) {
        if (target == null) {
            float life = (age + tickDelta) / maxAge;
            return baseScale * (1f - life);
        }
        float pulse = 1f + 0.18f * MathHelper.sin((age + tickDelta) * 0.6f);
        float grow = Math.min(1f, (age + tickDelta) / 4f);
        return baseScale * pulse * grow;
    }

    @Override
    public void tick() {
        if (target == null) {
            super.tick();
            alpha = 1f - (float) age / maxAge;
            return;
        }
        prevPosX = x;
        prevPosY = y;
        prevPosZ = z;
        if (age++ >= maxAge || target.isRemoved()) {
            absorb(false);
            return;
        }

        Vec3d to = target.getPos().add(0, target.getHeight() * 0.55, 0);
        Vec3d d = to.subtract(x, y, z);
        double dist = d.length();
        if (age > 8 && dist < 0.45) {
            absorb(true);
            return;
        }

        if (age <= 8) {
            // фонтанчик: замедляемся и «зависаем»
            velocityX *= 0.82;
            velocityY *= 0.78;
            velocityZ *= 0.82;
        } else {
            // самонаведение по спирали: скорость растёт, закрутка сбоку слабеет у цели
            Vec3d dir = d.multiply(1.0 / Math.max(dist, 1e-4));
            Vec3d side = dir.crossProduct(new Vec3d(0, 1, 0));
            if (side.lengthSquared() < 1e-4) side = new Vec3d(1, 0, 0);
            side = side.normalize().multiply(spin * Math.min(1.0, dist / 3.0));
            double speed = Math.min(0.12 + (age - 8) * 0.025, 0.95);
            Vec3d want = dir.multiply(speed).add(side.multiply(speed * 0.6));
            double k = 0.2;
            velocityX += (want.x - velocityX) * k;
            velocityY += (want.y - velocityY) * k;
            velocityZ += (want.z - velocityZ) * k;
            // не проскочить мимо
            double step = Math.sqrt(velocityX * velocityX + velocityY * velocityY + velocityZ * velocityZ);
            if (step > dist) {
                double s = dist / step;
                velocityX *= s;
                velocityY *= s;
                velocityZ *= s;
            }
        }
        move(velocityX, velocityY, velocityZ);

        if (age % 2 == 0) {   // искристый след
            MinecraftClient.getInstance().particleManager.addParticle(
                    new ServiceOrbParticle(world, prevPosX, prevPosY, prevPosZ, baseScale * 0.55f));
        }
    }

    private void absorb(boolean touched) {
        if (credit && !credited) {
            credited = true;
            ServiceClient.credit(value);
            if (touched) {
                float pitch = 1.5f + random.nextFloat() * 0.5f;
                MinecraftClient.getInstance().getSoundManager().play(
                        PositionedSoundInstance.master(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, pitch, 0.22f));
            }
        }
        markDead();
    }

    @Override
    public void markDead() {
        if (credit && !credited) {      // огонёк исчез раньше времени — очки всё равно засчитываем
            credited = true;
            ServiceClient.credit(value);
        }
        super.markDead();
    }

    /** Фабрика для частицы vestments:service_orb (искра) — заодно запоминаем спрайты. */
    public static final class Factory implements ParticleFactory<DefaultParticleType> {
        public Factory(SpriteProvider provider) {
            sprites = provider;
        }

        @Override
        public Particle createParticle(DefaultParticleType type, ClientWorld world, double x, double y, double z,
                                       double vx, double vy, double vz) {
            return new ServiceOrbParticle(world, x, y, z, 0.08f);
        }
    }
}
