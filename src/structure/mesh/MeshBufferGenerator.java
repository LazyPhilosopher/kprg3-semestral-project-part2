package structure.mesh;

import lwjglutils.OGLBuffers;
import org.lwjgl.BufferUtils;
import transforms.Vec3D;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL11.GL_LINES;
import static org.lwjgl.opengl.GL11.GL_LINEAR;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glGenTextures;
import static org.lwjgl.opengl.GL11.glTexImage2D;
import static org.lwjgl.opengl.GL11.glTexParameteri;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.stb.STBImage.stbi_failure_reason;
import static org.lwjgl.stb.STBImage.stbi_image_free;
import static org.lwjgl.stb.STBImage.stbi_load_from_memory;

public class MeshBufferGenerator {

    private static class FaceVertex {
        private final int positionIndex;
        private final int texCoordIndex;
        private final int normalIndex;

        private FaceVertex(int positionIndex, int texCoordIndex, int normalIndex) {
            this.positionIndex = positionIndex;
            this.texCoordIndex = texCoordIndex;
            this.normalIndex = normalIndex;
        }
    }

    private static class TexturedVertexKey {
        private final int positionIndex;
        private final int texCoordIndex;
        private final int normalIndex;

        private TexturedVertexKey(int positionIndex, int texCoordIndex, int normalIndex) {
            this.positionIndex = positionIndex;
            this.texCoordIndex = texCoordIndex;
            this.normalIndex = normalIndex;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof TexturedVertexKey)) {
                return false;
            }
            TexturedVertexKey other = (TexturedVertexKey) obj;
            return positionIndex == other.positionIndex
                    && texCoordIndex == other.texCoordIndex
                    && normalIndex == other.normalIndex;
        }

        @Override
        public int hashCode() {
            int result = positionIndex;
            result = 31 * result + texCoordIndex;
            result = 31 * result + normalIndex;
            return result;
        }
    }

    private static class MaterialMeshData {
        private final List<Float> vertexData = new ArrayList<>();
        private final List<Integer> indices = new ArrayList<>();
        private final Map<TexturedVertexKey, Integer> vertexMap = new HashMap<>();
    }

    public static ArrayList<MeshEntry> GetObjBuffers(Path objPath, int shaderProgram) {

        List<Vec3D> positions = new ArrayList<>();
        List<Vec3D> normals = new ArrayList<>();

        List<Float> vertexData = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();

        // Maps "vIndex/vtIndex/vnIndex" -> generated vertex index
        Map<String, Integer> vertexMap = new HashMap<>();

        try {
            List<String> lines = Files.readAllLines(objPath);

            for (String rawLine : lines) {

                String line = rawLine.trim();

                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                String[] parts = line.split("\\s+");

                switch (parts[0]) {

                    // Vertex position
                    case "v" -> {
                        float x = Float.parseFloat(parts[1]);
                        float y = Float.parseFloat(parts[2]);
                        float z = Float.parseFloat(parts[3]);

                        positions.add(new Vec3D(x, y, z));
                    }

                    // Vertex normal
                    case "vn" -> {
                        float x = Float.parseFloat(parts[1]);
                        float y = Float.parseFloat(parts[2]);
                        float z = Float.parseFloat(parts[3]);

                        normals.add(new Vec3D(x, y, z));
                    }

                    // Face
                    case "f" -> {

                        // Supports:
                        // f v1 v2 v3
                        // f v1//n1 v2//n2 v3//n3
                        // f v1/t1/n1 ...

                        List<Integer> faceIndices = new ArrayList<>();

                        for (int i = 1; i < parts.length; i++) {

                            String token = parts[i];

                            Integer existingIndex = vertexMap.get(token);

                            if (existingIndex != null) {
                                faceIndices.add(existingIndex);
                                continue;
                            }

                            String[] refs = token.split("/");

                            int positionIndex = Integer.parseInt(refs[0]) - 1;

                            int normalIndex = -1;

                            if (refs.length >= 3 && !refs[2].isEmpty()) {
                                normalIndex = Integer.parseInt(refs[2]) - 1;
                            }

                            Vec3D pos = positions.get(positionIndex);

                            Vec3D normal;

                            if (normalIndex >= 0 && normalIndex < normals.size()) {
                                normal = normals.get(normalIndex);
                            } else {
                                normal = new Vec3D(0.0, 0.0, 1.0);
                            }

                            int newVertexIndex = vertexData.size() / 6;

                            // Position
                            vertexData.add((float) pos.getX());
                            vertexData.add((float) pos.getY());
                            vertexData.add((float) pos.getZ());

                            // Normal
                            vertexData.add((float) normal.getX());
                            vertexData.add((float) normal.getY());
                            vertexData.add((float) normal.getZ());

                            vertexMap.put(token, newVertexIndex);
                            faceIndices.add(newVertexIndex);
                        }

                        // Triangulate polygon using fan triangulation
                        for (int i = 1; i < faceIndices.size() - 1; i++) {
                            indices.add(faceIndices.get(0));
                            indices.add(faceIndices.get(i));
                            indices.add(faceIndices.get(i + 1));
                        }
                    }
                }
            }

        } catch (IOException e) {
            throw new RuntimeException("Failed to load OBJ file: " + objPath, e);
        }

        float[] vertices = toFloatArray(vertexData);
        int[] indexArray = toIntArray(indices);

        ArrayList<MeshEntry> output = new ArrayList<>();

        output.add(new MeshEntry(
                new OGLBuffers(
                        vertices,
                        6,
                        new OGLBuffers.Attrib[]{
                                new OGLBuffers.Attrib("inPosition", 3, 0),
                                new OGLBuffers.Attrib("inNormal", 3, 3)
                        },
                        indexArray
                ),
                GL_TRIANGLES,
                shaderProgram
        ));

        return output;
    }

    public static ArrayList<MeshEntry> GetTexturedObjBuffers(Path objPath, int shaderProgram) {
        List<Vec3D> positions = new ArrayList<>();
        List<Vec3D> normals = new ArrayList<>();
        List<float[]> texCoords = new ArrayList<>();

        Map<String, String> materialTexturePaths = new HashMap<>();
        Map<String, Integer> textureIds = new HashMap<>();
        Map<String, MaterialMeshData> materialMeshes = new LinkedHashMap<>();

        String currentMaterial = "__default__";
        materialMeshes.put(currentMaterial, new MaterialMeshData());

        try {
            for (String rawLine : Files.readAllLines(objPath)) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                String[] parts = line.split("\\s+");
                switch (parts[0]) {
                    case "mtllib" -> {
                        Path materialPath = objPath.getParent().resolve(parts[1]).normalize();
                        materialTexturePaths.putAll(loadMaterialTextures(materialPath));
                    }
                    case "usemtl" -> {
                        currentMaterial = parts[1];
                        materialMeshes.computeIfAbsent(currentMaterial, ignored -> new MaterialMeshData());
                    }
                    case "v" -> positions.add(new Vec3D(
                            Double.parseDouble(parts[1]),
                            Double.parseDouble(parts[2]),
                            Double.parseDouble(parts[3])
                    ));
                    case "vn" -> normals.add(new Vec3D(
                            Double.parseDouble(parts[1]),
                            Double.parseDouble(parts[2]),
                            Double.parseDouble(parts[3])
                    ));
                    case "vt" -> {
                        float u = Float.parseFloat(parts[1]);
                        float v = Float.parseFloat(parts[2]);
                        texCoords.add(new float[]{u, 1.0f - v});
                    }
                    case "f" -> {
                        List<FaceVertex> faceVertices = new ArrayList<>();
                        for (int i = 1; i < parts.length; i++) {
                            faceVertices.add(parseFaceVertex(parts[i], positions.size(), texCoords.size(), normals.size()));
                        }
                        appendFace(materialMeshes.get(currentMaterial), positions, texCoords, normals, faceVertices);
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load textured OBJ file: " + objPath, e);
        }

        ArrayList<MeshEntry> output = new ArrayList<>();
        for (Map.Entry<String, MaterialMeshData> entry : materialMeshes.entrySet()) {
            MaterialMeshData meshData = entry.getValue();
            if (meshData.indices.isEmpty()) {
                continue;
            }

            int textureId = 0;
            String texturePath = materialTexturePaths.get(entry.getKey());
            if (texturePath != null) {
                textureId = textureIds.computeIfAbsent(texturePath, MeshBufferGenerator::loadTextureFromFile);
            }

            output.add(new MeshEntry(
                    new OGLBuffers(
                            toFloatArray(meshData.vertexData),
                            8,
                            new OGLBuffers.Attrib[]{
                                    new OGLBuffers.Attrib("inPosition", 3, 0),
                                    new OGLBuffers.Attrib("inNormal", 3, 3),
                                    new OGLBuffers.Attrib("inTexCoord", 2, 6)
                            },
                            toIntArray(meshData.indices)
                    ),
                    GL_TRIANGLES,
                    shaderProgram,
                    textureId
            ));
        }

        return output;
    }

    public static ArrayList<MeshEntry> GetCubeBuffers(int shaderProgram) {

        float[] CUBE_VERTICES = {
                // front
                -1f, -1f,  1f,   0f,  0f,  1f,
                1f, -1f,  1f,   0f,  0f,  1f,
                1f,  1f,  1f,   0f,  0f,  1f,
                -1f,  1f,  1f,   0f,  0f,  1f,
                // back
                1f, -1f, -1f,   0f,  0f, -1f,
                -1f, -1f, -1f,   0f,  0f, -1f,
                -1f,  1f, -1f,   0f,  0f, -1f,
                1f,  1f, -1f,   0f,  0f, -1f,
                // left
                -1f, -1f, -1f,  -1f,  0f,  0f,
                -1f, -1f,  1f,  -1f,  0f,  0f,
                -1f,  1f,  1f,  -1f,  0f,  0f,
                -1f,  1f, -1f,  -1f,  0f,  0f,
                // right
                1f, -1f,  1f,   1f,  0f,  0f,
                1f, -1f, -1f,   1f,  0f,  0f,
                1f,  1f, -1f,   1f,  0f,  0f,
                1f,  1f,  1f,   1f,  0f,  0f,
                // top
                -1f,  1f,  1f,   0f,  1f,  0f,
                1f,  1f,  1f,   0f,  1f,  0f,
                1f,  1f, -1f,   0f,  1f,  0f,
                -1f,  1f, -1f,   0f,  1f,  0f,
                // bottom
                -1f, -1f, -1f,   0f, -1f,  0f,
                1f, -1f, -1f,   0f, -1f,  0f,
                1f, -1f,  1f,   0f, -1f,  0f,
                -1f, -1f,  1f,   0f, -1f,  0f
        };

        int[] CUBE_INDICES = {
                0, 1, 2, 0, 2, 3,
                4, 5, 6, 4, 6, 7,
                8, 9, 10, 8, 10, 11,
                12, 13, 14, 12, 14, 15,
                16, 17, 18, 16, 18, 19,
                20, 21, 22, 20, 22, 23
        };

        ArrayList<MeshEntry> output = new ArrayList<>();
        output.add(new MeshEntry(
                new OGLBuffers(
                        CUBE_VERTICES,
                        6,
                        new OGLBuffers.Attrib[]{
                                new OGLBuffers.Attrib("inPosition", 3, 0),
                                new OGLBuffers.Attrib("inNormal", 3, 3)
                        },
                        CUBE_INDICES
                ),
                GL_TRIANGLES,
                shaderProgram
        ));
        return output;
    }

    public static ArrayList<MeshEntry> GetAxisArrowBuffers(int flatColorShaderProgram){
        return GetAxisArrowBuffers(flatColorShaderProgram, 1.0f);
    }

    public static ArrayList<MeshEntry> GetAxisArrowBuffers(int flatColorShaderProgram, float size) {
        ArrayList<MeshEntry> output = new ArrayList<>();
        output.addAll(getAxisEntries(new Vec3D(1.0, 0.0, 0.0).mul(size), new Vec3D(1.0, 0.0, 0.0), flatColorShaderProgram));
        output.addAll(getAxisEntries(new Vec3D(0.0, 1.0, 0.0).mul(size), new Vec3D(0.0, 1.0, 0.0), flatColorShaderProgram));
        output.addAll(getAxisEntries(new Vec3D(0.0, 0.0, 1.0).mul(size), new Vec3D(0.0, 0.0, 1.0), flatColorShaderProgram));
        return output;
    }

    public static ArrayList<MeshEntry> getAxisEntries(Vec3D axisEnd, Vec3D color, int shaderProgram) {
        ArrayList<MeshEntry> output = new ArrayList<>();
        output.add(createAxisLineEntry(axisEnd, color, shaderProgram));
        output.addAll(getAxisConeEntry(shaderProgram, axisEnd, color));
        return output;
    }
    public static ArrayList<MeshEntry> getAxisEntries(Vec3D color, int shaderProgram) {
        Vec3D axisEnd = new Vec3D(0,1,0);
        return getAxisEntries(axisEnd, color, shaderProgram);
    }

    private static MeshEntry createAxisLineEntry(Vec3D axisEnd, Vec3D color, int shaderProgram) {
        Vec3D start = new Vec3D(0.0, 0.0, 0.0);
        double shaftLength = axisEnd.length() + 1;
        Vec3D direction = axisEnd.normalized().orElse(new Vec3D(1.0, 0.0, 0.0));
        Vec3D shaftEnd = start.add(direction.mul(shaftLength));

        float[] lineVertices = {
                (float) start.getX(), (float) start.getY(), (float) start.getZ(), (float) color.getX(), (float) color.getY(), (float) color.getZ(),
                (float) shaftEnd.getX(), (float) shaftEnd.getY(), (float) shaftEnd.getZ(), (float) color.getX(), (float) color.getY(), (float) color.getZ()
        };

        return new MeshEntry(
                new OGLBuffers(
                        lineVertices,
                        6,
                        new OGLBuffers.Attrib[]{
                                new OGLBuffers.Attrib("inPosition", 3, 0),
                                new OGLBuffers.Attrib("inColor", 3, 3)
                        },
                        null
                ),
                GL_LINES,
                shaderProgram
        );
    }

    public static ArrayList<MeshEntry> getAxisConeEntry(int shaderProgram, Vec3D axisEnd, Vec3D color) {
        Vec3D start = new Vec3D(0.0, 0.0, 0.0);
        Vec3D direction = axisEnd.normalized().orElse(new Vec3D(1.0, 0.0, 0.0));
        Vec3D baseCenter = start.add(direction.mul(axisEnd.length()));
        Vec3D tip = baseCenter.add(direction);

        List<Float> vertices = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();
        appendFlatColorCone(vertices, indices, baseCenter, tip, 0.16*direction.length(), 16, color);

        ArrayList<MeshEntry> output = new ArrayList<>();
        output.add(new MeshEntry(
                new OGLBuffers(
                        toFloatArray(vertices),
                        6,
                        new OGLBuffers.Attrib[]{
                                new OGLBuffers.Attrib("inPosition", 3, 0),
                                new OGLBuffers.Attrib("inColor", 3, 3)
                        },
                        toIntArray(indices)
                ),
                GL_TRIANGLES,
                shaderProgram
        ));
        return output;
    }

    private static void appendFlatColorCone(
            List<Float> vertices,
            List<Integer> indices,
            Vec3D baseCenter,
            Vec3D tip,
            double radius,
            int segments,
            Vec3D color
    ) {
        Vec3D[] basis = createPerpendicularBasis(tip.sub(baseCenter));
        Vec3D tangent = basis[0];
        Vec3D bitangent = basis[1];

        for (int i = 0; i < segments; i++) {
            double angle0 = 2.0 * Math.PI * i / segments;
            double angle1 = 2.0 * Math.PI * (i + 1) / segments;

            Vec3D radial0 = tangent.mul(Math.cos(angle0)).add(bitangent.mul(Math.sin(angle0)));
            Vec3D radial1 = tangent.mul(Math.cos(angle1)).add(bitangent.mul(Math.sin(angle1)));
            Vec3D rim0 = baseCenter.add(radial0.mul(radius));
            Vec3D rim1 = baseCenter.add(radial1.mul(radius));

            int sideBase = vertices.size() / 6;
            addFlatColorVertex(vertices, rim0, color);
            addFlatColorVertex(vertices, rim1, color);
            addFlatColorVertex(vertices, tip, color);
            addTriangle(indices, sideBase, sideBase + 1, sideBase + 2);

            int capBase = vertices.size() / 6;
            addFlatColorVertex(vertices, baseCenter, color);
            addFlatColorVertex(vertices, rim1, color);
            addFlatColorVertex(vertices, rim0, color);
            addTriangle(indices, capBase, capBase + 1, capBase + 2);
        }
    }

    private static Vec3D[] createPerpendicularBasis(Vec3D direction) {
        Vec3D helper = Math.abs(direction.getX()) < 0.9
                ? new Vec3D(1.0, 0.0, 0.0)
                : new Vec3D(0.0, 1.0, 0.0);

        Vec3D tangent = direction.cross(helper).normalized().orElse(new Vec3D(0.0, 0.0, 1.0));
        Vec3D bitangent = direction.cross(tangent).normalized().orElse(new Vec3D(0.0, 1.0, 0.0));
        return new Vec3D[]{tangent, bitangent};
    }

    private static void addFlatColorVertex(List<Float> vertices, Vec3D position, Vec3D color) {
        vertices.add((float) position.getX());
        vertices.add((float) position.getY());
        vertices.add((float) position.getZ());
        vertices.add((float) color.getX());
        vertices.add((float) color.getY());
        vertices.add((float) color.getZ());
    }

    private static void addTriangle(List<Integer> indices, int a, int b, int c) {
        indices.add(a);
        indices.add(b);
        indices.add(c);
    }

    private static float[] toFloatArray(List<Float> values) {
        float[] array = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            array[i] = values.get(i);
        }
        return array;
    }

    private static int[] toIntArray(List<Integer> values) {
        int[] array = new int[values.size()];
        for (int i = 0; i < values.size(); i++) {
            array[i] = values.get(i);
        }
        return array;
    }

    private static Map<String, String> loadMaterialTextures(Path materialPath) throws IOException {
        Map<String, String> textures = new HashMap<>();
        String currentMaterial = null;

        try (BufferedReader reader = Files.newBufferedReader(materialPath)) {
            String rawLine;
            while ((rawLine = reader.readLine()) != null) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                String[] parts = line.split("\\s+");
                switch (parts[0]) {
                    case "newmtl" -> currentMaterial = parts[1];
                    case "map_Kd" -> {
                        if (currentMaterial != null) {
                            Path texturePath = materialPath.getParent().resolve(parts[1]).normalize();
                            textures.put(currentMaterial, texturePath.toString());
                        }
                    }
                }
            }
        }

        return textures;
    }

    private static FaceVertex parseFaceVertex(String token, int positionCount, int texCoordCount, int normalCount) {
        String[] refs = token.split("/", -1);
        return new FaceVertex(
                parseObjIndex(refs[0], positionCount),
                refs.length > 1 && !refs[1].isEmpty() ? parseObjIndex(refs[1], texCoordCount) : -1,
                refs.length > 2 && !refs[2].isEmpty() ? parseObjIndex(refs[2], normalCount) : -1
        );
    }

    private static int parseObjIndex(String rawIndex, int count) {
        int parsedIndex = Integer.parseInt(rawIndex);
        return parsedIndex > 0 ? parsedIndex - 1 : count + parsedIndex;
    }

    private static void appendFace(
            MaterialMeshData meshData,
            List<Vec3D> positions,
            List<float[]> texCoords,
            List<Vec3D> normals,
            List<FaceVertex> faceVertices
    ) {
        List<Integer> faceIndices = new ArrayList<>(faceVertices.size());
        for (FaceVertex faceVertex : faceVertices) {
            TexturedVertexKey key = new TexturedVertexKey(
                    faceVertex.positionIndex,
                    faceVertex.texCoordIndex,
                    faceVertex.normalIndex
            );

            Integer existingVertexIndex = meshData.vertexMap.get(key);
            if (existingVertexIndex != null) {
                faceIndices.add(existingVertexIndex);
                continue;
            }

            Vec3D position = positions.get(faceVertex.positionIndex);
            Vec3D normal = faceVertex.normalIndex >= 0 && faceVertex.normalIndex < normals.size()
                    ? normals.get(faceVertex.normalIndex)
                    : new Vec3D(0.0, 0.0, 1.0);
            float[] texCoord = faceVertex.texCoordIndex >= 0 && faceVertex.texCoordIndex < texCoords.size()
                    ? texCoords.get(faceVertex.texCoordIndex)
                    : new float[]{0.0f, 0.0f};

            int vertexIndex = meshData.vertexData.size() / 8;
            meshData.vertexData.add((float) position.getX());
            meshData.vertexData.add((float) position.getY());
            meshData.vertexData.add((float) position.getZ());
            meshData.vertexData.add((float) normal.getX());
            meshData.vertexData.add((float) normal.getY());
            meshData.vertexData.add((float) normal.getZ());
            meshData.vertexData.add(texCoord[0]);
            meshData.vertexData.add(texCoord[1]);

            meshData.vertexMap.put(key, vertexIndex);
            faceIndices.add(vertexIndex);
        }

        for (int i = 1; i < faceIndices.size() - 1; i++) {
            meshData.indices.add(faceIndices.get(0));
            meshData.indices.add(faceIndices.get(i));
            meshData.indices.add(faceIndices.get(i + 1));
        }
    }

    private static int loadTextureFromFile(String texturePath) {
        try {
            byte[] textureBytes = Files.readAllBytes(Path.of(texturePath));
            ByteBuffer imageBuffer = BufferUtils.createByteBuffer(textureBytes.length);
            imageBuffer.put(textureBytes).flip();

            IntBuffer width = BufferUtils.createIntBuffer(1);
            IntBuffer height = BufferUtils.createIntBuffer(1);
            IntBuffer components = BufferUtils.createIntBuffer(1);

            ByteBuffer pixelData = stbi_load_from_memory(imageBuffer, width, height, components, 4);
            if (pixelData == null) {
                throw new IOException("Failed to load image: " + stbi_failure_reason());
            }

            int textureId = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, textureId);
            glTexImage2D(
                    GL_TEXTURE_2D,
                    0,
                    GL_RGBA,
                    width.get(0),
                    height.get(0),
                    0,
                    GL_RGBA,
                    GL_UNSIGNED_BYTE,
                    pixelData
            );
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

            stbi_image_free(pixelData);
            return textureId;
        } catch (IOException e) {
            throw new RuntimeException("Failed to create texture from file: " + texturePath, e);
        }
    }
}
