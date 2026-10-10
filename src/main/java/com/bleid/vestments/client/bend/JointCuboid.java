package com.bleid.vestments.client.bend;

import io.github.kosmx.bendylib.impl.BendableCuboid;
import io.github.kosmx.bendylib.impl.IBendable;
import io.github.kosmx.bendylib.impl.RememberingPos;
import net.minecraft.util.math.Direction;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Деталь брони на сгибающейся конечности. Изгиб идёт вокруг сустава (локоть/колено), а не вокруг
 * центра детали. Если деталь целиком ниже сустава — она поворачивается вместе с предплечьем/голенью
 * как жёсткое тело (rigid); если пересекает сустав — гнётся плавно, как кожа игрока.
 */
public class JointCuboid extends BendableCuboid {
    private final boolean rigid;

    protected JointCuboid(Quad[] sides, RememberingPos[] positions, float minX, float minY, float minZ, float maxX,
                          float maxY, float maxZ, float fixX, float fixY, float fixZ, Direction direction,
                          IBendable.Plane basePlane, IBendable.Plane otherPlane, float fullSize, boolean rigid) {
        super(sides, positions, minX, minY, minZ, maxX, maxY, maxZ, fixX, fixY, fixZ, direction, basePlane, otherPlane, fullSize);
        this.rigid = rigid;
    }

    /** Строитель для детали: jx/jy/jz — сустав в координатах части, rigid — целиком ниже сустава. */
    public static io.github.kosmx.bendylib.ICuboidBuilder<io.github.kosmx.bendylib.impl.ICuboid> builder(
            float jx, float jy, float jz, boolean rigid) {
        return builder(jx, jy, jz, rigid, Direction.UP);
    }

    /** dir: UP — двигается нижняя часть (руки, ноги), DOWN — верхняя (корпус гнётся в пояснице). */
    public static io.github.kosmx.bendylib.ICuboidBuilder<io.github.kosmx.bendylib.impl.ICuboid> builder(
            float jx, float jy, float jz, boolean rigid, Direction dirn) {
        return data -> new BendableCuboid.Builder().setDirection(dirn).build(data,
                (sides, pos, minX, minY, minZ, maxX, maxY, maxZ, fx, fy, fz, dir, base, other, size) ->
                        new JointCuboid(sides, pos, minX, minY, minZ, maxX, maxY, maxZ, jx, jy, jz, dir, base, other, size, rigid));
    }

    @Override
    public Matrix4f applyBend(float bendAxis, float bendValue) {
        if (!rigid) return super.applyBend(bendAxis, bendValue);
        // жёсткий поворот всех вершин вокруг сустава
        Vector3f axis = new Vector3f((float) Math.cos(bendAxis), 0, (float) Math.sin(bendAxis));
        axis.mul(new Matrix3f().set(getBendDirection().getRotationQuaternion()));
        Matrix4f m = new Matrix4f().translate(getBendX(), getBendY(), getBendZ()).rotate(bendValue, axis)
                .translate(-getBendX(), -getBendY(), -getBendZ());
        iteratePositions(p -> {
            Vector3f o = new Vector3f(p.getOriginalPos());
            Vector4f v = new Vector4f(o, 1f).mul(m);
            p.setPos(new Vector3f(v.x, v.y, v.z));
        });
        return m;
    }
}
