package structure.mesh;

import lwjglutils.OGLBuffers;

import static org.lwjgl.opengl.GL20.glGetUniformLocation;
import static org.lwjgl.opengl.GL33.glUniform1i;

public class MeshBuffers {
    private OGLBuffers buffers;
    private MeshType meshType;
    private boolean useCustomTexCoord;
    private boolean useCustomNormal;

    public MeshBuffers(OGLBuffers buffers) {
        this.buffers = buffers;
        this.meshType = MeshType.CARTESIAN;
        this.useCustomTexCoord = false;
        this.useCustomNormal = false;
    }

    public MeshBuffers(OGLBuffers buffers, MeshType meshType) {
        this(buffers);
        this.meshType = meshType;
    }

    public MeshBuffers(OGLBuffers buffers, MeshType meshType, boolean useCustomTexCoord, boolean useCustomNormal) {
        this(buffers, meshType);
        this.useCustomTexCoord = useCustomTexCoord;
        this.useCustomNormal = useCustomNormal;
    }

    public void draw(int topology, int shaderProgram) {
        int locMeshType = glGetUniformLocation(shaderProgram, "meshType");
        int locUseCustomNormal = glGetUniformLocation(shaderProgram, "useCustomNormal");
        int locUseCustomTexCoord = glGetUniformLocation(shaderProgram, "useCustomTexCoord");

        glUniform1i(locMeshType, meshType.shaderValue);
        glUniform1i(locUseCustomTexCoord, useCustomTexCoord ? 1 : 0);
        glUniform1i(locUseCustomNormal, useCustomNormal ? 1 : 0);
        buffers.draw(topology, shaderProgram);
    }
}
