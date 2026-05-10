package structure.mesh;

import lwjglutils.OGLBuffers;

public class MeshEntry {
    private final OGLBuffers buffers;
    private final int topology;
    private final int shaderProgram;
    private final int textureId;

    public MeshEntry(OGLBuffers buffers, int topology, int shaderProgram) {
        this(buffers, topology, shaderProgram, 0);
    }

    public MeshEntry(OGLBuffers buffers, int topology, int shaderProgram, int textureId) {
        this.buffers = buffers;
        this.topology = topology;
        this.shaderProgram = shaderProgram;
        this.textureId = textureId;
    }

    public OGLBuffers getBuffers() {
        return buffers;
    }

    public int getTopology() {
        return topology;
    }

    public int getShaderProgram() {
        return shaderProgram;
    }

    public int getTextureId() {
        return textureId;
    }
}
