package structure.mesh;

import transforms.Mat4;

@FunctionalInterface
public interface MotionTransform {
    Mat4 get(double deltaTime);
}
