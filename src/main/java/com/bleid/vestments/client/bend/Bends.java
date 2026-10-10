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

    /** Пояс (середина корпуса): грудь наклоняется и отклоняется вбок вокруг этой точки. */
    public static final float WAIST_Y = 6f;

    /**
     * Изгибы последнего вычисленного кадра для сущности: локоть П, локоть Л, колено П, колено Л,
     * наклон груди вперёд, наклон груди вбок (радианы).
     */
    private static final Map<LivingEntity, float[]> LAST = new WeakHashMap<>();
    /** Изгибы для текущего вызова setAngles (null — не трогать, например рука от первого лица). */
    public static float[] current;

    private Bends() { }

    /** Предел сгиба локтя/колена: дальше bendy-lib искажает руку. */
    public static final float MAX_BEND = 2.2f;

    public static void store(LivingEntity e, float[] v) {
        if (v != null) for (int i = 0; i < Math.min(4, v.length); i++) v[i] = Math.min(v[i], MAX_BEND);
        current = v;
        if (e != null && v != null) LAST.put(e, v);
    }

    public static float[] of(LivingEntity e) {
        return e == null ? null : LAST.get(e);
    }

    /** Подготовить часть игрока к изгибу (кубоид 0 — сама рука/нога/рукав/штанина). */
    public static void init(ModelPart part) {
        init(part, Direction.UP);
    }

    /** Корпус и куртка: гнётся верхняя половина (DOWN). */
    public static void initBody(ModelPart part) {
        init(part, Direction.DOWN);
    }

    private static void init(ModelPart part, Direction d) {
        ModelPartAccessor.optionalGetCuboid(part, 0).ifPresent(c ->
                c.registerMutator(KEY, data -> new BendableCuboid.Builder().setDirection(d).build(data)));
    }

    /** Согнуть часть игрока: elbow=true — локоть (вперёд), иначе колено (назад). */
    public static void bend(ModelPart part, float amount, boolean elbow) {
        ModelPartAccessor.optionalGetCuboid(part, 0).ifPresent(c -> set(c, 0f, elbow ? -amount : amount));
    }

    /** Согнуть корпус: грудь вперёд fwd и вбок side (радианы). */
    public static void bendBody(ModelPart part, float fwd, float side) {
        float[] av = chestAxis(fwd, side);
        ModelPartAccessor.optionalGetCuboid(part, 0).ifPresent(c -> set(c, av[0], av[1]));
    }

    /** Ось и угол изгиба груди: поворот вокруг X (вперёд) и Z (вбок) одним поворотом. */
    private static float[] chestAxis(float fwd, float side) {
        float v = (float) Math.sqrt(fwd * fwd + side * side);
        float a = v < 1e-5f ? 0f : (float) Math.atan2(side, fwd);
        return new float[] { a, v };
    }

    private static void set(MutableCuboid c, float axis, float value) {
        if (Math.abs(value) < 1e-3f) {
            c.getAndActivateMutator(null);
            return;
        }
        ICuboid m = c.getAndActivateMutator(KEY);
        if (m instanceof BendableCuboid b) b.applyBend(axis, value);
    }

    // ---------------------------------------------------------------- броня

    /** Согнуть детали 3D-брони так же, как у игрока: локти, колени и корпус в пояснице. */
    public static void applyArmor(BipedEntityModel<?> model, LivingEntity e) {
        float[] v = of(e);
        float eR = v == null ? 0 : v[0], eL = v == null ? 0 : v[1], kR = v == null ? 0 : v[2], kL = v == null ? 0 : v[3];
        float cf = v == null || v.length < 6 ? 0 : v[4], cs = v == null || v.length < 6 ? 0 : v[5];
        walk(model.rightArm, ELBOW_Y, 0, 0, 0, 0f, -eR, false, true);
        walk(model.leftArm, ELBOW_Y, 0, 0, 0, 0f, -eL, false, true);
        walk(model.rightLeg, KNEE_Y, 0, 0, 0, 0f, kR, false, true);
        walk(model.leftLeg, KNEE_Y, 0, 0, 0, 0f, kL, false, true);
        float[] av = chestAxis(cf, cs);
        walk(model.body, WAIST_Y, 0, 0, 0, av[0], av[1], true, true);
    }

    /**
     * Обойти часть и её детей без собственного поворота и согнуть кубоиды вокруг сустава на высоте jy.
     * topMoves: двигается верх (корпус), иначе низ (руки, ноги).
     */
    private static void walk(ModelPart part, float jy, float ox, float oy, float oz, float axis, float value,
                             boolean topMoves, boolean top) {
        if (part == null) return;
        if (!top && (part.pitch != 0 || part.yaw != 0 || part.roll != 0)) return;   // наплечники, плащи, наклонные пластины
        float lx = -ox, ly = jy - oy, lz = -oz;
        java.util.List<ModelPart.Cuboid> cubes = ModelPartAccessor.getCuboids(part);
        if (cubes != null) {
            for (ModelPart.Cuboid cube : cubes) {
                MutableCuboid mc = (MutableCuboid) cube;
                boolean still = topMoves ? cube.minY >= ly - 0.25f : cube.maxY <= ly + 0.25f;
                if (still) continue;                          // неподвижная сторона сустава
                if (!mc.hasMutator(KEY)) {
                    boolean rigid = topMoves ? cube.maxY <= ly + 0.25f : cube.minY >= ly - 0.25f;
                    mc.registerMutator(KEY, JointCuboid.builder(lx, ly, lz, rigid, topMoves ? Direction.DOWN : Direction.UP));
                }
                set(mc, axis, value);
            }
        }
        for (ModelPart child : ModelPartAccessor.getChildren(part).values()) {
            walk(child, jy, ox + child.pivotX, oy + child.pivotY, oz + child.pivotZ, axis, value, topMoves, false);
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
