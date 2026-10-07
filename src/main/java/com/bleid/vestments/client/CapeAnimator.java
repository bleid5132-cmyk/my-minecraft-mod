package com.bleid.vestments.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.rpg_foundation.armor_api.client.model.GeoArmorModel;

/**
 * Живой плащ: каждый кадр наклоняет кость плаща назад в зависимости от скорости движения,
 * слегка покачивает в такт шагам, приподнимает при падении и отводит вбок при движении боком.
 */
@Environment(EnvType.CLIENT)
public final class CapeAnimator {
    /** Кость плаща в priest_vestments.geo.json. */
    public static final String CAPE_BONE = "vestments_cape";
    /** Наклон плаща в покое (как в модели), градусы. */
    private static final float BASE_DEG = 8f;

    /** Сущность, броня которой рисуется сейчас (ставит ArmorRenderDispatcherMixin). */
    public static LivingEntity current;

    private CapeAnimator() { }

    public static void apply(GeoArmorModel model) {
        ModelPart body = model.armorBone("armorBody");
        if (body == null || !body.hasChild(CAPE_BONE)) {
            return; // чужая броня — не трогаем
        }
        ModelPart cape = body.getChild(CAPE_BONE);
        float pitchDeg = BASE_DEG;
        float rollDeg = 0f;
        LivingEntity e = current;
        if (e != null) {
            float td = MinecraftClient.getInstance().getTickDelta();
            double dx = e.getX() - e.prevX;
            double dy = e.getY() - e.prevY;
            double dz = e.getZ() - e.prevZ;
            float yaw = MathHelper.lerpAngleDegrees(td, e.prevBodyYaw, e.bodyYaw) * MathHelper.RADIANS_PER_DEGREE;
            double forward = dx * -MathHelper.sin(yaw) + dz * MathHelper.cos(yaw);
            double sideways = dx * MathHelper.cos(yaw) + dz * MathHelper.sin(yaw);

            float back = (float) MathHelper.clamp(forward * 170.0, 0.0, 50.0);   // ходьба ~15°, бег ~45°
            float lift = (float) MathHelper.clamp(-dy * 70.0, 0.0, 35.0);       // при падении плащ подлетает
            float limbPos = e.limbAnimator.getPos(td);
            float limbSpeed = e.limbAnimator.getSpeed(td);
            float sway = MathHelper.sin(limbPos * 1.3324f) * 5f * limbSpeed;   // покачивание в такт шагам
            if (e.isInSneakingPose()) {
                back += 12f;
            }
            pitchDeg += back + lift + sway;
            rollDeg = (float) MathHelper.clamp(sideways * 80.0, -12.0, 12.0);
        }
        cape.pitch = pitchDeg * MathHelper.RADIANS_PER_DEGREE;
        cape.roll = rollDeg * MathHelper.RADIANS_PER_DEGREE;
    }
}
