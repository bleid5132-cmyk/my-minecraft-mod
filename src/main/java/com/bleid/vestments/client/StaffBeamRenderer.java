package com.bleid.vestments.client;

import com.bleid.vestments.StaffOfLightItem;
import com.bleid.vestments.Vestments;
import com.bleid.vestments.client.fx.FxSystem;
import java.util.Random;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * «Благословение» Посоха Света: сияющий луч из навершия посоха. Слои: широкое золотое свечение,
 * мерцающая середина и белое ядро; вокруг луча вьётся двойная спираль искр, вдоль него
 * бегут огоньки к цели; у навершия — звезда, в точке попадания — вращающееся солнце, вспышка и искры.
 */
@Environment(EnvType.CLIENT)
public final class StaffBeamRenderer {
    private static final Random R = new Random();

    private StaffBeamRenderer() { }

    /** Откуда идёт луч: центр креста на навершии посоха. */
    private static Vec3d beamStart(MinecraftClient client, PlayerEntity player, float tickDelta) {
        Vec3d eye = player.getCameraPosVec(tickDelta);
        Vec3d dir = player.getRotationVec(tickDelta);
        boolean rightHand = (player.getActiveHand() == Hand.MAIN_HAND) == (player.getMainArm() == Arm.RIGHT);
        Vec3d right = dir.crossProduct(new Vec3d(0, 1, 0));
        right = right.lengthSquared() < 1.0e-4 ? new Vec3d(1, 0, 0) : right.normalize();
        Vec3d up = right.crossProduct(dir).normalize();
        double side = rightHand ? 1.0 : -1.0;
        boolean firstPerson = player == client.player && client.options.getPerspective().isFirstPerson();
        return firstPerson
                // от первого лица посох наведён вперёд: крест чуть правее и ниже центра экрана
                ? eye.add(dir.multiply(1.25)).add(right.multiply(0.17 * side)).add(up.multiply(-0.17))
                // от третьего лица — перед игроком, у навершия поднятого посоха
                : eye.add(dir.multiply(0.75)).add(right.multiply(0.38 * side)).add(up.multiply(-0.05));
    }

    private static boolean beaming(PlayerEntity p) {
        return p.isUsingItem() && p.getActiveItem().isOf(Vestments.STAFF_OF_LIGHT);
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(StaffBeamRenderer::tick);
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            World world = context.world();
            VertexConsumerProvider consumers = context.consumers();
            if (world == null || consumers == null) return;
            MatrixStack matrices = context.matrixStack();
            Camera camera = context.camera();
            float td = context.tickDelta();
            Vec3d cam = camera.getPos();
            MinecraftClient client = MinecraftClient.getInstance();
            VertexConsumer vc = null;
            for (PlayerEntity player : world.getPlayers()) {
                if (!beaming(player)) continue;
                if (vc == null) vc = consumers.getBuffer(FxSystem.layer());
                Vec3d start = beamStart(client, player, td);
                Vec3d end = StaffOfLightItem.beamEnd(world, player, player.getCameraPosVec(td), player.getRotationVec(td));
                renderBeam(vc, matrices.peek().getPositionMatrix(), camera.getRotation(), start.subtract(cam), end.subtract(cam),
                        world.getTime() + td, player.getItemUseTime() + td);
            }
        });
    }

    private static void renderBeam(VertexConsumer vc, Matrix4f mat, Quaternionf rot, Vec3d a, Vec3d b, float time, float used) {
        Vec3d axis = b.subtract(a);
        double len = axis.length();
        if (len < 0.2) return;
        Vec3d dir = axis.multiply(1 / len);
        float grow = MathHelper.clamp(used / 6f, 0f, 1f);                // луч «выстреливает» за треть секунды
        Vec3d bEnd = a.add(axis.multiply(grow));
        float flick = 0.88f + 0.12f * MathHelper.sin(time * 1.7f) * MathHelper.sin(time * 0.63f + 1.3f);
        float w = 0.85f + 0.15f * MathHelper.sin(time * 0.9f);
        // слои луча (на конце чуть тусклее)
        FxSystem.drawStrip(vc, mat, FxSystem.BEAM, a, bEnd, 0.42f * w, 0.95f * flick, 0.62f * flick, 0.18f * flick, 0.7f, 0.42f, 0.1f);
        FxSystem.drawStrip(vc, mat, FxSystem.BEAM, a, bEnd, 0.2f, 1f, 0.85f * flick, 0.45f, 0.95f, 0.7f, 0.3f);
        FxSystem.drawStrip(vc, mat, FxSystem.BEAM, a, bEnd, 0.07f, 1f, 1f, 0.95f, 1f, 0.95f, 0.85f);

        // двойная спираль искр вокруг луча
        Vec3d ref = Math.abs(dir.y) > 0.9 ? new Vec3d(1, 0, 0) : new Vec3d(0, 1, 0);
        Vec3d u = dir.crossProduct(ref).normalize(), v = dir.crossProduct(u).normalize();
        double shown = len * grow;
        for (double s = 0.3; s < shown; s += 0.32) {
            for (int k = 0; k < 2; k++) {
                double ang = s * 2.2 - time * 0.45 + k * Math.PI;
                double rad = 0.22 + 0.04 * Math.sin(s * 3 + time * 0.3);
                Vec3d p = a.add(dir.multiply(s)).add(u.multiply(Math.cos(ang) * rad)).add(v.multiply(Math.sin(ang) * rad));
                float fade = (float) Math.min(1, s / 1.5) * (float) Math.min(1, (shown - s) / 1.2);
                float tw = 0.6f + 0.4f * MathHelper.sin((float) (s * 5 + time * 0.8 + k * 2));
                FxSystem.drawSprite(vc, mat, rot, FxSystem.GLINT, (float) p.x, (float) p.y, (float) p.z, 0.075f, 0,
                        fade * tw, fade * tw * 0.82f, fade * tw * 0.4f);
            }
        }
        // бегущие к цели огоньки
        for (int k = 0; k < 6; k++) {
            double s = ((time * 0.9 + k * len / 6) % len);
            if (s > shown) continue;
            Vec3d p = a.add(dir.multiply(s));
            FxSystem.drawSprite(vc, mat, rot, FxSystem.ORB, (float) p.x, (float) p.y, (float) p.z, 0.16f, 0, 1f, 0.9f, 0.6f);
        }
        // звезда у навершия посоха
        float pulse = 0.85f + 0.15f * MathHelper.sin(time * 0.8f);
        FxSystem.drawSprite(vc, mat, rot, FxSystem.ORB, (float) a.x, (float) a.y, (float) a.z, 0.45f * pulse, 0, 1f, 0.8f, 0.35f);
        FxSystem.drawSprite(vc, mat, rot, FxSystem.STAR, (float) a.x, (float) a.y, (float) a.z, 0.32f * pulse, time * 0.05f, 1f, 1f, 0.9f);
        // точка попадания: вращающееся солнце, вспышка и звезда
        if (grow >= 1f) {
            FxSystem.drawSprite(vc, mat, rot, FxSystem.SUNBURST, (float) b.x, (float) b.y, (float) b.z, 0.9f * pulse, time * 0.06f,
                    1f, 0.72f, 0.25f);
            FxSystem.drawSprite(vc, mat, rot, FxSystem.ORB, (float) b.x, (float) b.y, (float) b.z, 0.7f * w, 0, 1f, 0.85f, 0.5f);
            FxSystem.drawSprite(vc, mat, rot, FxSystem.GLINT, (float) b.x, (float) b.y, (float) b.z, 0.5f, -time * 0.04f, 1f, 1f, 0.92f);
        }
    }

    /** Искры у точки попадания и золотая пыль вдоль луча. */
    private static void tick(MinecraftClient client) {
        if (client.world == null || client.isPaused()) return;
        for (PlayerEntity player : client.world.getPlayers()) {
            if (!beaming(player) || player.getItemUseTime() < 6) continue;
            Vec3d start = beamStart(client, player, 1f);
            Vec3d end = StaffOfLightItem.beamEnd(client.world, player, player.getCameraPosVec(1f), player.getRotationVec(1f));
            Vec3d back = start.subtract(end).normalize();
            for (int k = 0; k < 3; k++) {
                double sp = 0.15 + R.nextDouble() * 0.2;
                Vec3d v = back.multiply(sp * 0.6).add((R.nextDouble() - 0.5) * sp, R.nextDouble() * sp, (R.nextDouble() - 0.5) * sp);
                FxSystem.spawn(FxSystem.STREAK, end.x, end.y, end.z).vel(v.x, v.y, v.z).gravity(0.03f).drag(0.94f).stretch(3.5f)
                        .size(0.045f, 0.015f).color(1f, 0.85f, 0.45f).life(10 + R.nextInt(8)).fade(0f, 0.5f);
            }
            if (player.getItemUseTime() % 10 == 0) {      // импульс луча — волна-кольцо в точке попадания
                FxSystem.spawn(FxSystem.SHOCK, end.x, end.y, end.z).size(0.2f, 1.3f).ease().color(1f, 0.8f, 0.35f).life(10).fade(0f, 0.7f);
            }
            double len = start.distanceTo(end);
            Vec3d dir = end.subtract(start).normalize();
            for (int k = 0; k < 2; k++) {
                double s = R.nextDouble() * len;
                Vec3d p = start.add(dir.multiply(s)).add((R.nextDouble() - 0.5) * 0.5, (R.nextDouble() - 0.5) * 0.5, (R.nextDouble() - 0.5) * 0.5);
                FxSystem.spawn(FxSystem.ORB, p.x, p.y, p.z).vel(0, 0.015, 0).size(0.05f, 0.01f).color(1f, 0.82f, 0.4f)
                        .life(16 + R.nextInt(10)).fade(0.2f, 0.6f);
            }
        }
    }
}
