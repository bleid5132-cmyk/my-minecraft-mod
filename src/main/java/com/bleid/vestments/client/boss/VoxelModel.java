package com.bleid.vestments.client.boss;

import com.mojang.blaze3d.systems.RenderSystem;
import java.io.DataInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Воксельная модель из файла .vxm (экспорт из схемы постройки): части с опорными точками и иерархией,
 * каждая — готовый меш в видеопамяти (VertexBuffer), цвета вокселей — в атласе-текстуре.
 * Координаты в вокселях: X — вбок, Y — вверх, Z — вперёд.
 */
@Environment(EnvType.CLIENT)
public final class VoxelModel {
    public static final class Part {
        public final String name;
        public final int parent;
        public final Vector3f pivot;
        final float[] data;     // x y z u v на вершину
        VertexBuffer vbo;

        Part(String name, int parent, Vector3f pivot, float[] data) {
            this.name = name;
            this.parent = parent;
            this.pivot = pivot;
            this.data = data;
        }
    }

    public final List<Part> parts = new ArrayList<>();
    private final Identifier texture;
    private boolean uploaded;

    private VoxelModel(Identifier texture) {
        this.texture = texture;
    }

    public static VoxelModel load(Identifier file, Identifier texture) {
        VoxelModel m = new VoxelModel(texture);
        try (InputStream in = MinecraftClient.getInstance().getResourceManager().open(file);
             DataInputStream d = new DataInputStream(new java.io.BufferedInputStream(in))) {
            byte[] magic = new byte[4];
            d.readFully(magic);
            int n = d.readInt();
            float aw = d.readInt(), ah = d.readInt();
            for (int i = 0; i < n; i++) {
                String name = d.readUTF();
                int parent = d.readInt();
                Vector3f piv = new Vector3f(d.readFloat(), d.readFloat(), d.readFloat());
                int q = d.readInt();
                float[] data = new float[q * 4 * 5];
                int o = 0;
                for (int k = 0; k < q; k++) {
                    d.readByte(); d.readByte(); d.readByte();          // нормаль (затенение запечено в атлас)
                    for (int c = 0; c < 4; c++) {
                        data[o++] = d.readShort() / 2f;
                        data[o++] = d.readShort() / 2f;
                        data[o++] = d.readShort() / 2f;
                        data[o++] = d.readUnsignedShort() / aw;
                        data[o++] = d.readUnsignedShort() / ah;
                    }
                }
                m.parts.add(new Part(name, parent, piv, data));
            }
        } catch (Exception e) {
            com.bleid.vestments.Vestments.LOGGER.error("Не удалось загрузить модель {}", file, e);
        }
        return m;
    }

    public int index(String name) {
        for (int i = 0; i < parts.size(); i++) if (parts.get(i).name.equals(name)) return i;
        return -1;
    }

    private void upload() {
        uploaded = true;
        for (Part p : parts) {
            BufferBuilder b = new BufferBuilder(p.data.length / 5 * 24 + 256);
            b.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR_TEXTURE);
            float[] a = p.data;
            for (int i = 0; i < a.length; i += 5) {
                b.vertex(a[i], a[i + 1], a[i + 2]).color(255, 255, 255, 255).texture(a[i + 3], a[i + 4]).next();
            }
            p.vbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
            p.vbo.bind();
            p.vbo.upload(b.end());
            VertexBuffer.unbind();
        }
    }

    /**
     * Рисует все части. mats[i] — полная матрица части (вид · модель), color — модуляция (свет, вспышка урона),
     * glowPart — часть, которая всегда светится в полную силу (нимб).
     */
    public void draw(Matrix4f[] mats, float r, float g, float b, int glowPart) {
        if (!uploaded) upload();
        RenderSystem.setShader(GameRenderer::getPositionColorTexProgram);
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        Matrix4f proj = RenderSystem.getProjectionMatrix();
        for (int i = 0; i < parts.size(); i++) {
            Part p = parts.get(i);
            if (p.vbo == null || mats[i] == null) continue;
            if (i == glowPart) RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            else RenderSystem.setShaderColor(r, g, b, 1f);
            p.vbo.bind();
            p.vbo.draw(mats[i], proj, RenderSystem.getShader());
        }
        VertexBuffer.unbind();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableCull();
    }
}
