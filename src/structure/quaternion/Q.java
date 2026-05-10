package structure.quaternion;

import transforms.Mat4;
import transforms.Mat4Identity;
import transforms.Point3D;
import transforms.Vec3D;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public class Q {

    private static final double NORMALIZED_EPSILON = 1e-9;
    private static final double ZERO_EPSILON = 1e-12;

    private double w;
    private double i;
    private double j;
    private double k;
    private boolean isNormalized;

    public Q() {
        this(1.0, 0.0, 0.0, 0.0);
        this.isNormalized = true;
    }

    public Q(double w, double i, double j, double k) {
        this.w = w;
        this.i = i;
        this.j = j;
        this.k = k;
        this.isNormalized = Math.abs(magnitudeSquared() - 1.0) < NORMALIZED_EPSILON;
    }

    public Q(Q other) {
        this(other.w, other.i, other.j, other.k);
        this.isNormalized = other.isNormalized;
    }

    public static Q identity() {
        return new Q();
    }

    public static Q fromAxisAngle(double angle, double x, double y, double z) {
        Optional<Vec3D> normalizedAxis = new Vec3D(x, y, z).normalized();
        if (!normalizedAxis.isPresent()) {
            return identity();
        }

        double halfAngle = angle * 0.5;
        double sinHalfAngle = Math.sin(halfAngle);
        Vec3D axis = normalizedAxis.get();
        return new Q(
                Math.cos(halfAngle),
                axis.getX() * sinHalfAngle,
                axis.getY() * sinHalfAngle,
                axis.getZ() * sinHalfAngle
        );
    }

    public static Q fromAxisAngle(double angle, Vec3D axis) {
        return fromAxisAngle(angle, axis.getX(), axis.getY(), axis.getZ());
    }

    public static Q fromEulerAngle(double angle, double x, double y, double z) {
        return fromAxisAngle(angle, x, y, z);
    }

    public static Q fromEulerAngle(double angle, Vec3D axis) {
        return fromAxisAngle(angle, axis);
    }

    public static Q fromEulerAnglesXYZ(double xAngle, double yAngle, double zAngle) {
        Q qx = fromAxisAngle(xAngle, 1.0, 0.0, 0.0);
        Q qy = fromAxisAngle(yAngle, 0.0, 1.0, 0.0);
        Q qz = fromAxisAngle(zAngle, 0.0, 0.0, 1.0);
        return qz.mul(qy).mul(qx).normalized();
    }

    public static Q fromEulerAngles(double xAngle, double yAngle, double zAngle) {
        return fromEulerAnglesXYZ(xAngle, yAngle, zAngle);
    }

    public static Q fromRotationMatrix(Mat4 matrix) {
        double trace = matrix.get(0, 0) + matrix.get(1, 1) + matrix.get(2, 2);
        double w;
        double i;
        double j;
        double k;

        if (trace > 0.0) {
            double s = Math.sqrt(trace + 1.0) * 2.0;
            w = 0.25 * s;
            i = (matrix.get(1, 2) - matrix.get(2, 1)) / s;
            j = (matrix.get(2, 0) - matrix.get(0, 2)) / s;
            k = (matrix.get(0, 1) - matrix.get(1, 0)) / s;
        } else if (matrix.get(0, 0) > matrix.get(1, 1) && matrix.get(0, 0) > matrix.get(2, 2)) {
            double s = Math.sqrt(1.0 + matrix.get(0, 0) - matrix.get(1, 1) - matrix.get(2, 2)) * 2.0;
            w = (matrix.get(1, 2) - matrix.get(2, 1)) / s;
            i = 0.25 * s;
            j = (matrix.get(1, 0) + matrix.get(0, 1)) / s;
            k = (matrix.get(2, 0) + matrix.get(0, 2)) / s;
        } else if (matrix.get(1, 1) > matrix.get(2, 2)) {
            double s = Math.sqrt(1.0 + matrix.get(1, 1) - matrix.get(0, 0) - matrix.get(2, 2)) * 2.0;
            w = (matrix.get(2, 0) - matrix.get(0, 2)) / s;
            i = (matrix.get(1, 0) + matrix.get(0, 1)) / s;
            j = 0.25 * s;
            k = (matrix.get(2, 1) + matrix.get(1, 2)) / s;
        } else {
            double s = Math.sqrt(1.0 + matrix.get(2, 2) - matrix.get(0, 0) - matrix.get(1, 1)) * 2.0;
            w = (matrix.get(0, 1) - matrix.get(1, 0)) / s;
            i = (matrix.get(2, 0) + matrix.get(0, 2)) / s;
            j = (matrix.get(2, 1) + matrix.get(1, 2)) / s;
            k = 0.25 * s;
        }

        return new Q(w, i, j, k).normalized();
    }

    public double getW() {
        return w;
    }

    public double getI() {
        return i;
    }

    public double getJ() {
        return j;
    }

    public double getK() {
        return k;
    }

    public Vec3D getVectorPart() {
        return new Vec3D(i, j, k);
    }

    public boolean isNormalized() {
        return isNormalized;
    }

    public double magnitudeSquared() {
        return w * w + i * i + j * j + k * k;
    }

    public double magnitude() {
        return Math.sqrt(magnitudeSquared());
    }

    public double get_magnitude() {
        return magnitude();
    }

    public double norm() {
        return magnitude();
    }

    public Q copy() {
        return new Q(this);
    }

    public void normalize() {
        double magnitude = magnitude();
        if (magnitude < ZERO_EPSILON) {
            throw new IllegalStateException("Cannot normalize a zero quaternion.");
        }

        w /= magnitude;
        i /= magnitude;
        j /= magnitude;
        k /= magnitude;
        isNormalized = true;
    }

    public Q normalized() {
        Q result = copy();
        result.normalize();
        return result;
    }

    public Q conjugate() {
        Q result = new Q(w, -i, -j, -k);
        result.isNormalized = isNormalized;
        return result;
    }

    public Q inverse() {
        double magnitudeSquared = magnitudeSquared();
        if (magnitudeSquared < ZERO_EPSILON) {
            throw new IllegalStateException("Cannot invert a zero quaternion.");
        }

        return new Q(
                w / magnitudeSquared,
                -i / magnitudeSquared,
                -j / magnitudeSquared,
                -k / magnitudeSquared
        );
    }

    public Q add(Q other) {
        return new Q(w + other.w, i + other.i, j + other.j, k + other.k);
    }

    public Q sub(Q other) {
        return new Q(w - other.w, i - other.i, j - other.j, k - other.k);
    }

    public Q opposite() {
        return new Q(-w, -i, -j, -k);
    }

    public Q mul(double scalar) {
        return new Q(w * scalar, i * scalar, j * scalar, k * scalar);
    }

    public Q mul(Q other) {
        return new Q(
                w * other.w - i * other.i - j * other.j - k * other.k,
                w * other.i + i * other.w + j * other.k - k * other.j,
                w * other.j - i * other.k + j * other.w + k * other.i,
                w * other.k + i * other.j - j * other.i + k * other.w
        );
    }

    public Q mulR(Q other) {
        return mul(other);
    }

    public Q mulL(Q other) {
        return other.mul(this);
    }

    public double dot(Q other) {
        return w * other.w + i * other.i + j * other.j + k * other.k;
    }

    public Vec3D rotate(Vec3D vector) {
        Q normalized = normalized();
        Vec3D qVector = normalized.getVectorPart();
        Vec3D t = qVector.mul(2.0).cross(vector);
        return vector.add(t.mul(normalized.w)).add(qVector.cross(t));
    }

    public Point3D toAxisAngle() {
        Q normalized = normalized();
        double angle = 2.0 * Math.acos(normalized.w);
        double sinHalfAngle = Math.sqrt(Math.max(0.0, 1.0 - normalized.w * normalized.w));

        if (sinHalfAngle < ZERO_EPSILON) {
            return new Point3D(0.0, 1.0, 0.0, 0.0);
        }

        return new Point3D(
                angle,
                normalized.i / sinHalfAngle,
                normalized.j / sinHalfAngle,
                normalized.k / sinHalfAngle
        );
    }

    public Point3D toEulerAngle() {
        return toAxisAngle();
    }

    public Mat4 toRotationMatrix() {
        Q normalized = normalized();
        double w = normalized.w;
        double i = normalized.i;
        double j = normalized.j;
        double k = normalized.k;

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

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Q)) {
            return false;
        }
        Q other = (Q) obj;
        return Double.compare(other.w, w) == 0
                && Double.compare(other.i, i) == 0
                && Double.compare(other.j, j) == 0
                && Double.compare(other.k, k) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(w, i, j, k);
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "(%f, %f, %f, %f)", w, i, j, k);
    }
}
