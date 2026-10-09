package com.bleid.vestments.client.bend;

import io.github.kosmx.bendylib.ModelPartAccessor;
import io.github.kosmx.bendylib.MutableCuboid;
import io.github.kosmx.bendylib.impl.BendableCuboid;
import io.github.kosmx.bendylib.impl.ICuboid;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;

/**
 * Настоящий изгиб локтей и коленей (bendy-lib). Кожа игрока гнётся посередине руки/ноги;
 * детали 3D-брони гнутся вокруг того же сустава (или поворачиваются вместе с предплечьем/голенью),
 * предмет в руке следует за предплечьем.
 *
 * Значения (радианы, ≥ 0): локоть — предплечье сгибается вперёд, колено — голень уходит назад.
 */
@Environment(EnvType.CLIENT)
public final class Bends {
    public static final String KEY = "vestments_bend";
    /** Сустав в координатах части модели (пиксели): локоть правой/левой руки, колено. */
    public static final float ELBOW_Y = 4f, KNEE_Y = 6f;
    public static final float ARM_X_R = -1f, ARM_X_L = 1f;

    /** Изгибы последнего вычисленного кадра для сущности: локоть П, локоть Л, колено П, колено Л. */
    private static final Map<LivingEntity, float[]> LAST = new WeakHashMap<>();
    /** Изгибы для текущего вызова setAngles (null — не трогать, например рука от первого лица). */
    public static float[] current;

    private Bends() { }

    public static void store(LivingEntity e, float[] v) {
        current = v;
        if (e != null && v != null) LAST.put(e, v);
    }

    public static float[] of(LivingEntity e) {
        return e == null ? null : LAST.get(e);
    }

    /** Подготовить часть игрока к изгибу (кубоид 0 — сама рука/нога/рукав/штанина). */
    public static void init(ModelPart part) {
        ModelPartAccessor.optionalGetCuboid(part, 0).ifPresent(c ->
                c.registerMutator(KEY, data -> new BendableCuboid.Builder().setDirection(Direction.UP).build(data)));
    }

    /** Согнуть часть игрока: elbow=true — локоть (вперёд), иначе колено (назад). */
    public static void bend(ModelPart part, float amount, boolean elbow) {
        ModelPartAccessor.optionalGetCuboid(part, 0).ifPresent(c -> set(c, amount, elbow));
    }

    private static void set(MutableCuboid c, float amount, boolean elbow) {
        if (Math.abs(amount) < 1e-3f) {
            c.getAndActivateMutator(null);
            return;
        }
        ICuboid m = c.getAndActivateMutator(KEY);
        if (m instanceof BendableCuboid b) b.applyBend(0f, elbow ? -amount : amount);
    }

    // ---------------------------------------------------------------- броня

    /** Согнуть детали 3D-брони на руках и ногах модели брони так же, как у игрока. */
    public static void applyArmor(BipedEntityModel<?> model, LivingEntity e) {
        float[] v = of(e);
        float eR = v == null ? 0 : v[0], eL = v == null ? 0 : v[1], kR = v == null ? 0 : v[2], kL = v == null ? 0 : v[3];
        walk(model.rightArm, ARM_X_R, ELBOW_Y, 0, 0, 0, eR, true, true);
        walk(model.leftArm, ARM_X_L, ELBOW_Y, 0, 0, 0, eL, true, true);
        walk(model.rightLeg, 0, KNEE_Y, 0, 0, 0, kR, false, true);
        walk(model.leftLeg, 0, KNEE_Y, 0, 0, 0, kL, false, true);
    }

    /** Обойти часть и её детей без собственного поворота, согнув все кубоиды вокруг сустава. */
    private static void walk(ModelPart part, float jx, float jy, float ox, float oy, float oz, float amount, boolean elbow,
                             boolean top) {
        if (part == null) return;
        if (!top && (part.pitch != 0 || part.yaw != 0 || part.roll != 0)) return;   // наплечники, плащи, наклонные пластины
        float lx = jx - ox, ly = jy - oy, lz = -oz;
        java.util.List<ModelPart.Cuboid> cubes = ModelPartAccessor.getCuboids(part);
        if (cubes != null) {
            for (int i = 0; i < cubes.size(); i++) {
                ModelPart.Cuboid cube = cubes.get(i);
                MutableCuboid mc = (MutableCuboid) cube;
                if (cube.maxY <= ly + 0.25f) continue;            // целиком выше сустава — не двигается
                if (!mc.hasMutator(KEY)) {
                    boolean below = cube.minY >= ly - 0.25f;      // целиком ниже — поворачивается целиком
                    mc.registerMutator(KEY, JointCuboid.builder(lx, ly, lz, below));
                }
                if (Math.abs(amount) < 1e-3f) {
                    mc.getAndActivateMutator(null);
                } else if (mc.getAndActivateMutator(KEY) instanceof BendableCuboid b) {
                    b.applyBend(0f, elbow ? -amount : amount);
                }
            }
        }
        for (ModelPart child : ModelPartAccessor.getChildren(part).values()) {
            walk(child, jx, jy, ox + child.pivotX, oy + child.pivotY, oz + child.pivotZ, amount, elbow, false);
        }
    }

    // ---------------------------------------------------------------- предмет в руке

    /** Повернуть систему координат руки так, как повернулось предплечье (для предмета в руке). */
    public static void forearm(MatrixStack m, Arm arm, float elbow) {
        if (Math.abs(elbow) < 1e-3f) return;
        float x = (arm == Arm.RIGHT ? ARM_X_R : ARM_X_L) / 16f, y = ELBOW_Y / 16f;
        m.translate(x, y, 0);
        m.multiply(RotationAxis.POSITIVE_X.rotation(-elbow));
        m.translate(-x, -y, 0);
    }
}
