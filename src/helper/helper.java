package helper;

import transforms.Mat4;
import transforms.Vec3D;

public class helper {

    public static double angleBetween(Vec3D a, Vec3D b) {
        double lengths = a.length() * b.length();

        if (lengths == 0.0) {
            throw new IllegalArgumentException("Zero-length vector");
        }

        double cos = a.dot(b) / lengths;

        // Clamp because of floating-point precision errors
        cos = Math.max(-1.0, Math.min(1.0, cos));

        return Math.acos(cos); // radians
    }
}
