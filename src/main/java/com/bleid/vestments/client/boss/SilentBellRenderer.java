package com.bleid.vestments.client.boss;

import static com.bleid.vestments.boss.SilentBellEntity.*;

import com.bleid.vestments.boss.SilentBellEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.world.World;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Рендер Безмолвного Колокола: парение и покачивание, лохмотья рясы, взгляд глаза, моргание при открытии,
 * дрожь после расширения трещины, тень-метка падения, раскол надвое при смерти.
 */
@Environment(EnvType.CLIENT)
public class SilentBellRenderer extends EntityRenderer<SilentBellEntity> {
    private static final Identifier TEX = new Identifier("vestments", "textures/entity/silent_bell.png");
    private static final Identifier MODEL = new Identifier("vestments", "models/entity/silent_bell.vxm");
    private static final Identifier SHADOW = new Identifier("textures/misc/shadow.png");
    private static VoxelModel model;
    private static int BELL_R, BELL_RG, BELL_L, BELL_LG, EYE, EYE_G, ROBE, CHAINS;
    static final Springs SPRINGS = new Springs();
    // поза: наклон X, Z, взгляд yaw, pitch, ряса X, Z
    private static final float[] OMEGA = { 5, 5, 9, 9, 6, 6 };

    public SilentBellRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        shadowRadius = 2.2f;
        shadowOpacity = 0.6f;
    }

    static VoxelModel model() {
        if (model == null) {
            model = VoxelModel.load(MODEL);
            BELL_R = model.index("bell_r"); BELL_RG = model.index("bell_r#g");
            BELL_L = model.index("bell_l"); BELL_LG = model.index("bell_l#g");
            EYE = model.index("eye"); EYE_G = model.index("eye#g");
            ROBE = model.index("robe"); CHAINS = model.index("chains");
        }
        return model;
    }

    @Override
    public Identifier getTexture(SilentBellEntity e) {
        return TEX;
    }

    private static float sm(float x) {
        x = MathHelper.clamp(x, 0f, 1f);
        return x * x * (3 - 2 * x);
    }

    @Override
    public void render(SilentBellEntity e, float yaw, float td, MatrixStack ms, VertexConsumerProvider vcp, int light) {
        VoxelModel m = model();
        if (m.parts.isEmpty()) return;
        float t = e.age + td;
        int st = e.state();
        float sk = e.age - e.clientStateStart + td;
        int a = st == S_FIGHT ? e.attack() : IDLE;
        float k = e.age - e.clientAttackStart + td;

        float scale = 1f, robeVis = 1f;
        if (st == S_AWAKEN) {
            float u = sm((sk - A_EYE) / (A_RISEN - A_EYE));
            scale = 1f;                                   // колокол уже вырос, накрыв Звонаря
            robeVis = u;
        }

        // ---- наклоны
        float[] p = new float[6];
        boolean calm = st == S_AWAKEN && sk < A_TOLL;
        p[0] = calm ? 0 : MathHelper.sin(t * 0.037f) * 3;
        p[1] = calm ? 0 : MathHelper.cos(t * 0.029f) * 3;
        if (st == S_AWAKEN && sk >= A_TOLL && sk < A_EYE + 10) {
            float q = sk - A_TOLL;
            p[0] += 9 * MathHelper.sin(q * 0.8f) * (float) Math.exp(-q * 0.12f);   // единственный удар — колокол качнуло
        }
        if (a == RING) {
            int fire = coneFire(e.widened());
            p[0] += k < fire ? -12 * sm(k / fire) : 16 * (float) Math.exp(-(k - fire) * 0.18f);
        }
        if (a == DROP) {
            p[0] += k < 40 ? MathHelper.sin(k * 0.3f) * 5 : 0;
            p[1] += k < 40 ? MathHelper.cos(k * 0.27f) * 5 : 0;
        }
        boolean wide = e.widened();
        if (a == RAM) p[0] += k < 12 ? -16 * sm(k / 10f) : 0;          // замах тарана — колокол откидывается назад
        if (a == VOLLEY) {                                              // отдача после каждой сферы
            int gap = volleyGap(wide), n = volleyCount(wide);
            float i = k - VOLLEY_FIRST;
            if (i >= 0) {
                int last = Math.min(n - 1, (int) (i / gap));
                float since = i - last * gap;
                p[0] -= 11 * (float) Math.exp(-since * 0.35f);
            } else p[0] -= 6 * sm(k / VOLLEY_FIRST);
        }
        if (a == KNELL) {                                               // раскачивается, отбивая звон
            float amp = sm(k / 8f) * 15;
            p[1] += amp * MathHelper.sin(k * 0.45f);
            p[0] += amp * 0.4f * MathHelper.cos(k * 0.45f);
        }
        if (a == SPIN) p[0] += 8 * sm(k / SPIN_UP);                        // вихрь: колокол клонится, лохмотья разлетаются
        float bodyYaw = MathHelper.lerpAngleDegrees(td, e.prevBodyYaw, e.bodyYaw);
        float headYaw = MathHelper.lerpAngleDegrees(td, e.prevHeadYaw, e.headYaw);
        p[2] = MathHelper.clamp(MathHelper.wrapDegrees(headYaw - bodyYaw), -35, 35);
        p[3] = MathHelper.clamp(e.getPitch(td), -30, 30);
        double vx = e.getX() - e.prevX, vz = e.getZ() - e.prevZ;
        float vf = (float) (vx * -MathHelper.sin(bodyYaw * MathHelper.RADIANS_PER_DEGREE) + vz * MathHelper.cos(bodyYaw * MathHelper.RADIANS_PER_DEGREE));
        float vl = (float) (vx * MathHelper.cos(bodyYaw * MathHelper.RADIANS_PER_DEGREE) + vz * MathHelper.sin(bodyYaw * MathHelper.RADIANS_PER_DEGREE));
        if (st == S_FIGHT) {                                            // крен по ходу движения — рывки видно
            p[0] += MathHelper.clamp(vf * 24, -24, 24);
            p[1] += MathHelper.clamp(-vl * 24, -24, 24);
        }
        p[4] = MathHelper.sin(t * 0.08f) * 6 + vf * 60;
        p[5] = MathHelper.cos(t * 0.07f) * 5;
        Springs.step(SPRINGS.get(e.getId()), p, OMEGA, 0.7f);

        int n = m.parts.size();
        Vector3f[] rot = new Vector3f[n];
        float[] sc = new float[n];
        for (int i = 0; i < n; i++) { rot[i] = new Vector3f(); sc[i] = 1f; }
        boolean open = e.eyeOpen();
        float since = e.age - e.clientEyeChange + td;
        if (EYE >= 0) {
            rot[EYE].set(p[3], -p[2], 0);
            sc[EYE] = open ? 1f + 0.18f * (float) Math.exp(-since * 0.35f) : 0.92f;
        }
        if (ROBE >= 0) {
            rot[ROBE].set(MathHelper.clamp(p[4], -25, 25), 0, p[5]);
            sc[ROBE] = Math.max(0.01f, robeVis);
        }
        if (CHAINS >= 0) rot[CHAINS].set(MathHelper.sin(t * 0.05f) * 8, 0, MathHelper.cos(t * 0.043f) * 6);
        // вихрь цепей: отдельное вращение вокруг оси (не через пружины)
        float spinDeg = 0;
        if (a == SPIN) {
            int len = wide ? 64 : 52;
            float kk = Math.min(k, SPIN_UP + len + 6);
            spinDeg = kk < SPIN_UP ? 2.2f * kk * kk : 2.2f * SPIN_UP * SPIN_UP + 44f * (kk - SPIN_UP);
            float x = Math.max(0, kk - SPIN_UP - len);
            spinDeg -= 44f * x * x / 12f;                               // торможение за 6 тактов
            float total = 2.2f * SPIN_UP * SPIN_UP + 44f * (len + 6) - 44f * 3f;
            spinDeg *= Math.round(total / 360f) * 360f / total;         // встаёт ровно лицом вперёд — без рывка
            if (ROBE >= 0) rot[ROBE].set(-28, 0, 0);
            if (CHAINS >= 0) rot[CHAINS].set(-35 + MathHelper.sin(t * 0.9f) * 6, 0, 0);
        }

        // ---- смерть: глаз гаснет, колокол раскалывается надвое
        boolean[] hide = new boolean[n];
        float dim = 1f, eyeDim = open ? 1f : 0.4f;
        float jx = 0, jz = 0;
        if (st == S_DYING) {
            float tremble = sm(sk / 40f) * (sk < 40 ? 1 : 0);
            jx = (float) Math.sin(t * 2.7) * 0.8f * tremble;
            jz = (float) Math.cos(t * 3.1) * 0.8f * tremble;
            eyeDim = Math.max(0.15f, 0.4f * (1 - sm(sk / 30f)));
            if (sk >= 40) {
                float u = MathHelper.clamp((sk - 40) / 12f, 0f, 1f);
                float fall = u * u;                                        // падают с ускорением, затем отскок
                float bounce = sk > 52 ? MathHelper.sin((sk - 52) * 0.8f) * 6 * Math.max(0, 1 - (sk - 52) / 16f) : 0;
                float ang = 80 * fall - Math.abs(bounce);
                if (BELL_L >= 0) rot[BELL_L].set(0, 0, -ang);
                if (BELL_R >= 0) rot[BELL_R].set(0, 0, ang);
                if (EYE >= 0) sc[EYE] = Math.max(0.01f, 1 - sm((sk - 40) / 8f));
                if (CHAINS >= 0) hide[CHAINS] = true;
                if (ROBE >= 0) {
                    sc[ROBE] = Math.max(0.01f, 1 - 0.4f * sm((sk - 40) / 20f));
                    rot[ROBE].set(0, 0, 0);
                }
            }
            if (sk > 130) dim = 1f - 0.5f * sm((sk - 130) / 30f);
            for (int i : new int[] { BELL_RG, BELL_LG }) if (i >= 0 && sk > 40) hide[i] = sk > 40 + (i == BELL_RG ? 22 : 28);
        } else if (e.widened()) {
            // гул: колокол мелко дрожит
            jx = (float) Math.sin(t * 3.9) * 0.35f;
            jz = (float) Math.cos(t * 4.3) * 0.35f;
        }
        if (!open && EYE_G >= 0) hide[EYE_G] = true;
        Matrix4f[] mats = m.pose(rot, sc);

        ms.push();
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-bodyYaw - spinDeg));
        ms.scale(scale / 16f, scale / 16f, scale / 16f);
        ms.translate(jx, 30 * robeVis, jz);
        if (st != S_DYING || sk < 40) {
            ms.translate(0, 50, 0);
            ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(p[0]));
            ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(p[1]));
            ms.translate(0, -50, 0);
        }
        int overlay = st == S_FIGHT ? LivingEntityRenderer.getOverlay(e, 0f) : OverlayTexture.DEFAULT_UV;
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(TEX));
        boolean[] noEye = hide.clone(), eyeOnly = new boolean[n];
        for (int i = 0; i < n; i++) eyeOnly[i] = true;
        if (EYE >= 0) { noEye[EYE] = true; eyeOnly[EYE] = false; }
        if (EYE_G >= 0) { noEye[EYE_G] = true; eyeOnly[EYE_G] = hide[EYE_G]; }
        m.render(ms, vc, false, light, overlay, mats, dim, dim, dim, -1e9f, 1e9f, noEye, null);
        m.render(ms, vc, false, light, overlay, mats, eyeDim, eyeDim * 1.05f, eyeDim, -1e9f, 1e9f, eyeOnly, null);
        ms.pop();

        if (st == S_FIGHT && a == DROP && k < 46) dropShadow(e, td, k, ms, vcp);
        super.render(e, yaw, td, ms, vcp, light);
    }

    /**
     * Безмолвный Колокол как страж-статуя (спящий Звонарь) и в начале пробуждения. ms — в системе модели
     * (поворот по телу, масштаб 1/16, начало у ног). hover — подъём над землёй в единицах модели.
     */
    static void renderGuard(MatrixStack ms, VertexConsumerProvider vcp, int light, float t, float hover, float robe, boolean eyeOpen,
                            float eyeSince, float tremble) {
        VoxelModel m = model();
        if (m.parts.isEmpty()) return;
        int n = m.parts.size();
        Vector3f[] rot = new Vector3f[n];
        float[] sc = new float[n];
        for (int i = 0; i < n; i++) { rot[i] = new Vector3f(); sc[i] = 1f; }
        if (ROBE >= 0) {
            rot[ROBE].set(MathHelper.sin(t * 0.08f) * 4 * robe, 0, MathHelper.cos(t * 0.07f) * 3 * robe);
            sc[ROBE] = Math.max(0.01f, robe);
        }
        if (EYE >= 0) sc[EYE] = eyeOpen ? 1f + 0.18f * (float) Math.exp(-eyeSince * 0.35f) : 0.92f;
        boolean[] hide = new boolean[n];
        if (!eyeOpen && EYE_G >= 0) hide[EYE_G] = true;
        if (robe <= 0.02f && ROBE >= 0) hide[ROBE] = true;
        Matrix4f[] mats = m.pose(rot, sc);
        ms.push();
        float jx = (float) Math.sin(t * 2.9) * 0.6f * tremble, jz = (float) Math.cos(t * 3.3) * 0.6f * tremble;
        ms.translate(jx, hover, jz);
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(TEX));
        boolean[] noEye = hide.clone(), eyeOnly = new boolean[n];
        for (int i = 0; i < n; i++) eyeOnly[i] = true;
        if (EYE >= 0) { noEye[EYE] = true; eyeOnly[EYE] = false; }
        if (EYE_G >= 0) { noEye[EYE_G] = true; eyeOnly[EYE_G] = hide[EYE_G]; }
        float ed = eyeOpen ? 1f : 0.35f;
        m.render(ms, vc, false, light, OverlayTexture.DEFAULT_UV, mats, 1f, 1f, 1f, -1e9f, 1e9f, noEye, null);
        m.render(ms, vc, false, light, OverlayTexture.DEFAULT_UV, mats, ed, ed * 1.05f, ed, -1e9f, 1e9f, eyeOnly, null);
        ms.pop();
    }

    /** Тень-метка под поднявшимся колоколом: растёт и темнеет к моменту падения. */
    private void dropShadow(SilentBellEntity e, float td, float k, MatrixStack ms, VertexConsumerProvider vcp) {
        World w = e.getWorld();
        double ex = MathHelper.lerp(td, e.prevX, e.getX()), ey = MathHelper.lerp(td, e.prevY, e.getY()), ez = MathHelper.lerp(td, e.prevZ, e.getZ());
        int bx = MathHelper.floor(ex), bz = MathHelper.floor(ez);
        double gy = ey - 30;
        for (int y = MathHelper.floor(ey); y > MathHelper.floor(ey) - 30; y--) {
            BlockPos bp = new BlockPos(bx, y, bz);
            if (!w.getBlockState(bp).getCollisionShape(w, bp).isEmpty()) {
                gy = y + 1;
                break;
            }
        }
        float u = MathHelper.clamp(k / 40f, 0f, 1f);
        float r = 2.8f * (0.55f + 0.45f * u), al = 0.35f + 0.6f * u;
        float yy = (float) (gy - ey + 0.02);
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityShadow(SHADOW));
        MatrixStack.Entry en = ms.peek();
        Matrix4f pm = en.getPositionMatrix();
        Matrix3f nm = en.getNormalMatrix();
        int ov = OverlayTexture.DEFAULT_UV, li = 0xF000F0;
        for (int pass = 0; pass < 2; pass++) {
            float rr = pass == 0 ? r : r * 0.6f;
            vc.vertex(pm, -rr, yy, -rr).color(1f, 1f, 1f, al).texture(0, 0).overlay(ov).light(li).normal(nm, 0, 1, 0).next();
            vc.vertex(pm, -rr, yy, rr).color(1f, 1f, 1f, al).texture(0, 1).overlay(ov).light(li).normal(nm, 0, 1, 0).next();
            vc.vertex(pm, rr, yy, rr).color(1f, 1f, 1f, al).texture(1, 1).overlay(ov).light(li).normal(nm, 0, 1, 0).next();
            vc.vertex(pm, rr, yy, -rr).color(1f, 1f, 1f, al).texture(1, 0).overlay(ov).light(li).normal(nm, 0, 1, 0).next();
        }
    }
}
