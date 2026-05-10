package structure.mesh;

import transforms.Mat4;

public interface TimeTransform {
    Mat4 get(double time);
}