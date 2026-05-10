package structure.mesh;

public enum MeshType {
    CARTESIAN(0),
    SPHERICAL(1),
    CYLINDRICAL(2);

    public final int shaderValue;

    MeshType(int shaderValue) {
        this.shaderValue = shaderValue;
    }
}