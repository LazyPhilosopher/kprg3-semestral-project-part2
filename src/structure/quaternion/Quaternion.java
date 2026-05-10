package structure.quaternion;

import transforms.Mat4;
import transforms.Mat4Identity;
import transforms.Vec3D;

import java.lang.reflect.Field;
import java.util.Optional;

public class Quaternion {

    private static final double NORMALIZED_EPSILON = 1e-9;
    private static final double ZERO_EPSILON = 1e-12;

    private double w, i, j, k;
    private boolean isNormalized;

    public Quaternion() {
        this(1.0, 0.0, 0.0, 0.0);
    }

    public Quaternion(double w, double i, double j, double k) {
        this.w = w;
        this.i = i;
        this.j = j;
        this.k = k;
        this.isNormalized = this.isNormalized();
    }

    // === Lin-Alg Operations ===
    public boolean isNormalized(){
        this.isNormalized = Math.abs(magnitudeSquared() - 1.0) < NORMALIZED_EPSILON;
        return this.isNormalized;
    }

    public double magnitudeSquared(){
        return w * w + i * i + j * j + k * k;
    }

    public double magnitude(){
        return Math.pow(this.magnitudeSquared(), 0.5);
    }

    public Mat4 toRotationMatrix() {
        Quaternion normalized = normalized();
        double w = normalized.w;
        double i = normalized.i;
        double j = normalized.j;
        double k = normalized.k;

        // https://en.wikipedia.org/wiki/Quaternions_and_spatial_rotation
        Mat4 result = new Mat4Identity();
        result = result.withElement(0, 0, 1.0 - 2.0 * (j * j + k * k));
        result = result.withElement(1, 0, 2.0 * (i * j - w * k));
        result = result.withElement(2, 0, 2.0 * (w * j + i * k));

        result = result.withElement(0, 1, 2.0 * (i * j + w * k));
        result = result.withElement(1, 1, 1.0 - 2.0 * (i * i + k * k));
        result = result.withElement(2, 1, 2.0 * (j * k - i * w));

        result = result.withElement(0, 2, 2.0 * (i * k - w * j));
        result = result.withElement(1, 2, 2.0 * (j * k + i * w));
        result = result.withElement(2, 2, 1.0 - 2.0 * (i * i + j * j));
        return result;
    }

    public double dot(Quaternion other) {
        return w * other.w + i * other.i + j * other.j + k * other.k;
    }

    // === getting new Quaternion instance ===
    public Quaternion normalized(){
        double mag = this.magnitude();
        return new Quaternion(w / mag, i / mag, j / mag, k / mag);
    }

    public Quaternion conjugated(){
        return new Quaternion(w, -i, -j, -k);
    }

    public static Quaternion identity() {
        return new Quaternion();
    }

    public static Quaternion fromAxisAngle(double angle, Vec3D vector){
        Optional<Vec3D> normalizedAxis = vector.normalized();
        if (normalizedAxis.isEmpty()) {
            return identity();
        }

        double halfAngle = angle * 0.5;
        double sinHalfAngle = Math.sin(halfAngle);
        Vec3D axis = normalizedAxis.get();
        return new Quaternion(
                Math.cos(halfAngle),
                axis.getX() * sinHalfAngle,
                axis.getY() * sinHalfAngle,
                axis.getZ() * sinHalfAngle
        );
    }

    public Quaternion pow(double t) {

        Quaternion q = normalized();

        double angle = 2.0 * Math.acos(q.w);

        double sinHalf = Math.sqrt(1.0 - q.w * q.w);

        if (sinHalf < 1e-8) {
            return Quaternion.identity();
        }

        Vec3D axis = new Vec3D(
                q.i / sinHalf,
                q.j / sinHalf,
                q.k / sinHalf
        );

        return Quaternion.fromAxisAngle(angle * t, axis);
    }

    public static Quaternion slerp(Quaternion a, Quaternion b, double t) {
        Quaternion qa = a.normalized();
        Quaternion qb = b.normalized();

        if (qa.dot(qb) < 0.0) {
            qb = new Quaternion(-qb.w, -qb.i, -qb.j, -qb.k);
        }

        Quaternion relative = qa.conjugated().mul(qb);
        return qa.mul(relative.pow(t)).normalized();
    }

    public static Quaternion fromTo(Vec3D a, Vec3D b) {
        Vec3D from = a.normalized().orElse(new Vec3D(1,0,0));
        Vec3D to = b.normalized().orElse(new Vec3D(1,0,0));

        double dot = from.dot(to);

        // same direction
        if (dot > 0.999999) {
            return Quaternion.identity();
        }

        // opposite direction
        if (dot < -0.999999) {

            Vec3D axis =
                    new Vec3D(1,0,0).cross(from);

            if (axis.length() < 1e-6) {
                axis = new Vec3D(0,1,0).cross(from);
            }

            axis = axis.normalized().get();

            return Quaternion.fromAxisAngle(
                    Math.PI,
                    axis
            );
        }

        Vec3D axis = from.cross(to);

        return new Quaternion(
                1.0 + dot,
                axis.getX(),
                axis.getY(),
                axis.getZ()
        ).normalized();
    }
    // === modifying current Quaternion instance ===

    public void normalize(){
        double mag = this.magnitude();
        w /= mag;
        i /= mag;
        j /= mag;
        k /= mag;
    }

    public void conjugate(){
        i = -i;
        j = -j;
        k = -k;
    }
    public Quaternion mul(Quaternion other) {
        return new Quaternion(
                w * other.w - i * other.i - j * other.j - k * other.k,
                w * other.i + i * other.w + j * other.k - k * other.j,
                w * other.j - i * other.k + j * other.w + k * other.i,
                w * other.k + i * other.j - j * other.i + k * other.w
        );
    }

    // === private methods ===
    private void _copyFields(Object source) throws IllegalAccessException {
        if (!getClass().isAssignableFrom(source.getClass())) {
            throw new IllegalArgumentException("Incompatible types");
        }

        Class<?> cls = source.getClass();

        while (cls != null) {
            for (Field field : cls.getDeclaredFields()) {
                field.setAccessible(true);

                if (field.getName().equals("id")) {
                    continue;
                }

                field.set(this, field.get(source));
            }

            cls = cls.getSuperclass();
        }
    }
}
