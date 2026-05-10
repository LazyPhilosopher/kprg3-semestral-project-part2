package structure;

import structure.mesh.Mesh;
import transforms.Vec3D;

import static structure.mesh.MeshBufferGenerator.getAxisEntries;

public class PlaneDirection {

    private final Vec3D position;
    private final Vec3D orientation;
    private final Vec3D color;

    public PlaneDirection(
            Vec3D position,
            Vec3D orientation,
            Vec3D color
    ) {
        this.position = position;

        this.orientation = orientation.normalized()
                .orElse(new Vec3D(0, 1, 0));

        this.color = color;
    }

    public Vec3D getPosition() {
        return position;
    }

    public Vec3D getOrientation() {
        return orientation;
    }

    public Vec3D getColor() {
        return color;
    }

//    public Mesh createMesh(int shaderProgram) {
//        return new Mesh(
//                getAxisEntries(
//                        orientation,
//                        color,
//                        shaderProgram
//                ),
//                0.25f,
//                position,
//                orientation
//        );
//    }
}