package structure.mesh;

import structure.quaternion.Quaternion;
import transforms.*;

import java.util.ArrayList;
import java.util.List;

public class Mesh {

    private final ArrayList<MeshEntry> submeshes = new ArrayList<>();
    private final ArrayList<TimeTransform> transforms = new ArrayList<>();

    private boolean visible;
    private boolean highlighted;

    private Vec3D position;

    // REAL orientation state
    private Quaternion rotation;

    private float scale;

    public Mesh() {
        visible = true;
        highlighted = false;

        position = new Vec3D(0, 0, 0);

        rotation = Quaternion.identity();

        scale = 1.0f;
    }

    public Mesh(ArrayList<MeshEntry> submeshes) {
        this();
        this.submeshes.addAll(submeshes);
    }

    public Mesh(ArrayList<MeshEntry> submeshes, float scale) {
        this(submeshes);
        this.scale = scale;
    }

    public Mesh(ArrayList<MeshEntry> submeshes, Vec3D position) {
        this(submeshes);
        this.position = position;
    }

    public Mesh(ArrayList<MeshEntry> submeshes,
                float scale,
                Vec3D position) {

        this(submeshes, scale);

        this.position = position;
    }

    public Mesh(ArrayList<MeshEntry> submeshes,
                float scale,
                Vec3D position,
                Vec3D orientation) {

        this(submeshes, scale, position);

        setOrientation(orientation);
    }

    public Mat4 getModelMatrix(double currentTime, double deltaTime) {

        Mat4 model = new Mat4Identity();

        model = model.mul(new Mat4Scale(scale));

        model = model.mul(rotation.toRotationMatrix());

        for (TimeTransform transform : transforms) {
            model = model.mul(transform.get(currentTime));
        }

        model = model.mul(new Mat4Transl(position));

        return model;
    }

    public List<MeshEntry> getEntries() {
        return submeshes;
    }

    public boolean isVisible() {
        return visible;
    }

    public Vec3D getPosition() {
        return position;
    }

    public void setPosition(Vec3D position) {
        this.position = position;
    }

    public void setScale(float scale) {
        this.scale = scale;
    }

    // =========================================================
    // ORIENTATION
    // =========================================================

    public Quaternion getRotation() {
        return rotation;
    }

    public void setRotation(Quaternion rotation) {
        this.rotation = rotation;
    }

    public Vec3D getOrientation() {

        return transformDirection(
                rotation.toRotationMatrix(),
                new Vec3D(0, 1, 0)
        );
    }

    public void setOrientation(Vec3D direction) {

        Vec3D normalized =
                direction.normalized()
                        .orElse(new Vec3D(0, 1, 0));

        rotation = Quaternion.fromTo(
                new Vec3D(0, 1, 0),
                normalized
        );
    }

    // =========================================================

    public void addTransform(TimeTransform transform) {
        transforms.add(transform);
    }

    // =========================================================
    // HELPERS
    // =========================================================

    public Vec3D transformDirection(Mat4 m, Vec3D v) {

        return new Vec3D(
                m.get(0,0) * v.getX() +
                        m.get(1,0) * v.getY() +
                        m.get(2,0) * v.getZ(),

                m.get(0,1) * v.getX() +
                        m.get(1,1) * v.getY() +
                        m.get(2,1) * v.getZ(),

                m.get(0,2) * v.getX() +
                        m.get(1,2) * v.getY() +
                        m.get(2,2) * v.getZ()
        );
    }
}