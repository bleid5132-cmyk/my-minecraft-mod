package com.bleid.vestments.client;

import com.bleid.vestments.StaffOfLightItem;
import com.bleid.vestments.Vestments;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BeaconBlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Рисует «Благословение»: луч маяка золотого цвета из руки каждого игрока, который держит
 * зажатую ПКМ с Посохом Света. Используется та же бегущая светящаяся текстура, что у маяка.
 */
@Environment(EnvType.CLIENT)
public final class StaffBeamRenderer {
    private static final float[] GOLD = { 1.0f, 0.80f, 0.22f };
    private static final float INNER_RADIUS = 0.07f;
    private static final float OUTER_RADIUS = 0.13f;

    private StaffBeamRenderer() { }

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            World world = context.world();
            VertexConsumerProvider consumers = context.consumers();
            if (world == null || consumers == null) return;
            MatrixStack matrices = context.matrixStack();
            Camera camera = context.camera();
            float tickDelta = context.tickDelta();
            Vec3d cam = camera.getPos();

            MinecraftClient client = MinecraftClient.getInstance();
            for (PlayerEntity player : world.getPlayers()) {
                if (!player.isUsingItem() || !player.getActiveItem().isOf(Vestments.STAFF_OF_LIGHT)) continue;

                Vec3d eye = player.getCameraPosVec(tickDelta);
                Vec3d dir = player.getRotationVec(tickDelta);
                Vec3d end = StaffOfLightItem.beamEnd(world, player, eye, dir);

                // начало луча — центр креста на навершии посоха
                boolean rightHand = (player.getActiveHand() == Hand.MAIN_HAND) == (player.getMainArm() == Arm.RIGHT);
                Vec3d right = dir.crossProduct(new Vec3d(0, 1, 0));
                right = right.lengthSquared() < 1.0e-4 ? new Vec3d(1, 0, 0) : right.normalize();
                Vec3d up = right.crossProduct(dir).normalize();
                double side = rightHand ? 1.0 : -1.0;
                boolean firstPerson = player == client.player && client.options.getPerspective().isFirstPerson();
                Vec3d start = firstPerson
                        // от первого лица посох наведён вперёд: крест чуть правее и ниже центра экрана
                        ? eye.add(dir.multiply(1.25)).add(right.multiply(0.17 * side)).add(up.multiply(-0.17))
                        // от третьего лица — перед игроком, у навершия поднятого посоха
                        : eye.add(dir.multiply(0.75)).add(right.multiply(0.38 * side)).add(up.multiply(-0.05));

                Vec3d beam = end.subtract(start);
                double length = beam.length();
                if (length < 0.2) continue;
                Vec3d axis = beam.multiply(1.0 / length);
                int whole = MathHelper.ceil(length);

                matrices.push();
                matrices.translate(start.x - cam.x, start.y - cam.y, start.z - cam.z);
                matrices.multiply(new Quaternionf().rotationTo(new Vector3f(0, 1, 0),
                        new Vector3f((float) axis.x, (float) axis.y, (float) axis.z)));
                matrices.scale(1.0f, (float) (length / whole), 1.0f);   // точная длина луча
                matrices.translate(-0.5, 0.0, -0.5);                       // renderBeam сам сдвигает на центр блока
                BeaconBlockEntityRenderer.renderBeam(matrices, consumers, BeaconBlockEntityRenderer.BEAM_TEXTURE,
                        tickDelta, 1.0f, world.getTime(), 0, whole, GOLD, INNER_RADIUS, OUTER_RADIUS);
                matrices.pop();
            }
        });
    }
}
