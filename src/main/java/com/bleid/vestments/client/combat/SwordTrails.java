package com.bleid.vestments.client.combat;

import com.bleid.vestments.client.fx.FxSystem;
import com.bleid.vestments.paladin.combat.Blade;
import com.bleid.vestments.paladin.combat.Moveset;
import com.bleid.vestments.paladin.combat.Strike;
import java.util.Map;
import java.util.Random;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * След клинка. Дуга удара известна точно (Blade), поэтому след строится прямо по ней: от момента
 * «сейчас минус длина следа» до «сейчас», мелкими шагами — получается гладкая лента без рывков
 * при любом FPS. Два слоя: широкое свечение и яркая кромка у острия. Чем выше звание, тем
 * длиннее, шире и ярче след; у верхних званий с острия сыплются искры.
 */
@Environment(EnvType.CLIENT)
public final class SwordTrails {
    private static final Random R = new Random();
    private static final int STEPS = 18;

    private SwordTrails() { }

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(ctx -> {
            VertexConsumerProvider consumers = ctx.consumers();
            if (consumers == null || ctx.world() == null) return;
            Camera cam = ctx.camera();
            Matrix4f mat = ctx.matrixStack().peek().getPositionMatrix();
            VertexConsumer vc = null;
            for (Map.Entry<Integer, CombatClient.Playback> e : CombatClient.all().entrySet()) {
                Entity ent = ctx.world().getEntityById(e.getKey());
                if (!(ent instanceof LivingEntity le)) continue;
                if (vc == null) vc = consumers.getBuffer(FxSystem.layer());
                draw(vc, mat, cam.getPos(), le, e.getValue(), ctx.tickDelta());
            }
        });
    }

    private static Vec3d[] frame(LivingEntity e, float td) {
        return Blade.frame(MathHelper.lerpAngleDegrees(td, e.prevHeadYaw, e.headYaw), e.getPitch(td));
    }

    private static Vec3d base(LivingEntity e, float td) {
        return new Vec3d(MathHelper.lerp(td, e.prevX, e.getX()), MathHelper.lerp(td, e.prevY, e.getY()),
                MathHelper.lerp(td, e.prevZ, e.getZ()));
    }

    private static void draw(VertexConsumer vc, Matrix4f mat, Vec3d cam, LivingEntity e, CombatClient.Playback pb, float td) {
        Moveset ms = pb.ms;
        float t = CombatClient.time(pb, td);
        float life = ms.trailLife;
        Vec3d[] fr = frame(e, td);
        Vec3d sh = Blade.shoulder(base(e, td), fr, e.getHeight());
        for (Strike k : pb.clip.strikes) {
            if (k.maxTargets <= 0 && k.event == null) continue;
            float ws = k.pre - 0.03f, we = k.contact + 0.06f;
            float t0 = Math.max(ws, t - life), t1 = Math.min(we, t);
            if (t1 <= t0) continue;
            float fadeAll = t > we ? 1f - (t - we) / life : 1f;
            if (fadeAll <= 0) continue;
            Vec3d[] in = new Vec3d[STEPS + 1], out = new Vec3d[STEPS + 1], mid = new Vec3d[STEPS + 1];
            float[] age = new float[STEPS + 1];
            for (int i = 0; i <= STEPS; i++) {
                float tt = t0 + (t1 - t0) * i / STEPS;
                Blade.State b = Blade.at(pb.clip, tt, ms.length);
                Vec3d d = Blade.toWorld(b.dir(), fr);
                double reach = b.reach() + 0.08;
                double inner = Math.max(Blade.INNER, reach - 1.15 * ms.trailWidth);
                in[i] = sh.add(d.multiply(inner)).subtract(cam);
                out[i] = sh.add(d.multiply(reach)).subtract(cam);
                mid[i] = sh.add(d.multiply(reach - (reach - inner) * 0.22)).subtract(cam);
                age[i] = MathHelper.clamp(1f - (t - tt) / life, 0f, 1f);
            }
            float a = ms.trailAlpha * fadeAll * (pb.clip.visualOnly ? 0.45f : 1f);
            for (int i = 0; i < STEPS; i++) {
                float a0 = age[i] * age[i] * a, a1 = age[i + 1] * age[i + 1] * a;
                float u0 = (float) i / STEPS, u1 = (float) (i + 1) / STEPS;
                float[] c = ms.trail, k2 = ms.trailCore;
                // широкое свечение
                FxSystem.drawQuad(vc, mat, FxSystem.TRAIL, out[i], out[i + 1], in[i + 1], in[i],
                        new float[] { u0, 0, u1, 0, u1, 1, u0, 1 },
                        new float[] { c[0] * a0, c[1] * a0, c[2] * a0, c[0] * a1, c[1] * a1, c[2] * a1,
                                c[0] * a1 * 0.2f, c[1] * a1 * 0.2f, c[2] * a1 * 0.2f, c[0] * a0 * 0.2f, c[1] * a0 * 0.2f, c[2] * a0 * 0.2f });
                // яркая кромка у острия
                float b0 = a0 * 1.2f, b1 = a1 * 1.2f;
                FxSystem.drawQuad(vc, mat, FxSystem.TRAIL, out[i], out[i + 1], mid[i + 1], mid[i],
                        new float[] { u0, 0, u1, 0, u1, 0.6f, u0, 0.6f },
                        new float[] { k2[0] * b0, k2[1] * b0, k2[2] * b0, k2[0] * b1, k2[1] * b1, k2[2] * b1,
                                k2[0] * b1 * 0.3f, k2[1] * b1 * 0.3f, k2[2] * b1 * 0.3f, k2[0] * b0 * 0.3f, k2[1] * b0 * 0.3f, k2[2] * b0 * 0.3f });
            }
        }
    }

    /** Искры с острия у верхних званий. */
    static void tick(MinecraftClient mc) {
        for (Map.Entry<Integer, CombatClient.Playback> e : CombatClient.all().entrySet()) {
            CombatClient.Playback pb = e.getValue();
            if (!pb.ms.sparkles || pb.hitstop > 0 || pb.clip.visualOnly) continue;
            Entity ent = mc.world.getEntityById(e.getKey());
            if (!(ent instanceof LivingEntity le)) continue;
            for (Strike k : pb.clip.strikes) {
                if (pb.t < k.pre - 0.02f || pb.t > k.contact + 0.03f) continue;
                Vec3d[] fr = frame(le, 1f);
                Vec3d sh = Blade.shoulder(le.getPos(), fr, le.getHeight());
                Blade.State b = Blade.at(pb.clip, pb.t, pb.ms.length);
                Vec3d d = Blade.toWorld(b.dir(), fr);
                int n = pb.ms.tier >= 5 ? 4 : 2;
                for (int i = 0; i < n; i++) {
                    Vec3d p = sh.add(d.multiply(b.reach() * (0.55 + R.nextDouble() * 0.45)));
                    float[] c = pb.ms.trailCore;
                    FxSystem.spawn(R.nextBoolean() ? FxSystem.GLINT : FxSystem.STAR, p.x, p.y, p.z)
                            .vel((R.nextDouble() - 0.5) * 0.05, R.nextDouble() * 0.04, (R.nextDouble() - 0.5) * 0.05)
                            .size(0.08f + R.nextFloat() * 0.06f, 0.01f).color(c[0], c[1] * 0.92f, c[2] * 0.7f)
                            .rot(R.nextFloat() * 6f, (R.nextFloat() - 0.5f) * 0.3f).life(10 + R.nextInt(8)).fade(0f, 0.6f);
                }
            }
        }
    }
}
