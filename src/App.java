import lwjglutils.OGLUtils;
import lwjglutils.ShaderUtils;
import lwjglutils.ToFloatArray;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;
import structure.FlightState;
import structure.PlaneDirection;
import structure.mesh.Mesh;
import structure.mesh.MeshEntry;
import structure.quaternion.Quaternion;
import transforms.*;

import java.io.IOException;
import java.nio.DoubleBuffer;
import java.nio.IntBuffer;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL33.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL33.GL_CULL_FACE;
import static org.lwjgl.opengl.GL33.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL33.glClear;
import static org.lwjgl.opengl.GL33.glDisable;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;
import static structure.mesh.MeshBufferGenerator.*;


public class App {


    private long window;
    private int width = 1280;
    private int height = 860;
    private double ox;
    private double oy;
    private boolean mouseButton1 = false;
    private double lastFrameTime;
    private int generalShaderProgram, flatColorShaderProgram, simpleTexturedShaderProgram;
    private Vec3D cameraTarget = new Vec3D(0.0, 0.0, 0.0);
    private Quaternion cameraRotation = quaternionFromViewDirection(directionFromAngles(
            Math.toRadians(45.0),
            Math.toRadians(-20.0)
    ));
    private double cameraRadius = 6.0;

    private ArrayList<Mesh> scene_meshes = new ArrayList<>();
    private Mesh cube, axes, cone, plane, terrain;
    private List<PlaneDirection> planeDirections = new ArrayList<>();
    final Vec3D forward = new Vec3D(0, 1, 0);
    private static final Vec3D WORLD_UP = new Vec3D(0, 0, 1);
    private static final Vec3D CAMERA_FORWARD_BASE = new Vec3D(1, 0, 0);
    private static final Vec3D CAMERA_RIGHT_BASE = new Vec3D(0, -1, 0);

    final double maxT = 2.0;
    final double reachThreshold = 0.1;


    FlightState state = new FlightState();

    Runnable loadNextTarget = () -> {

        PlaneDirection current = planeDirections.get(state.dirIdx);
        state.posA = plane.getPosition();
        state.posB = current.getPosition();

        /*
         * Build rotations
         */
        Vec3D dirA = plane.getOrientation()
                .normalized()
                .orElse(forward);

        Vec3D dirB = current.getOrientation()
                .normalized()
                .orElse(forward);

        state.rotA = Quaternion.fromTo(forward, dirA);
        state.rotB = Quaternion.fromTo(forward, dirB);

        Vec3D flightDir = state.posB.sub(state.posA)
                .normalized()
                .orElse(dirA);

        /*
         * Blend current direction, target direction,
         * and physical climb/descent direction.
         * Add extra pitch influence from altitude difference.
         */
        double dz = state.posB.getZ() - state.posA.getZ();
        Vec3D midDir = dirA
                .add(dirB)
                .add(flightDir.mul(2.0))
                .add(new Vec3D(0.0,0.0,dz).mul(0.5));

        midDir = midDir.normalized()
                .orElse(dirA);

        /*
         * Create middle position
         */
        Vec3D posStart = state.posA.add(dirA.mul(3));
        Vec3D posEnd = state.posB.sub(dirB.mul(3));
        state.posMid = posStart.add(posEnd).mul(0.5);

        state.rotMid = Quaternion.fromTo(forward, midDir);
        state.deltaTime = 0.0;
        state.dirIdx = (state.dirIdx + 1) % planeDirections.size();
    };

    private void init() throws IOException {
        GLFWErrorCallback.createPrint(System.err).set();

        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        window = glfwCreateWindow(width, height, "Quaternion Demo Scene", NULL, NULL);
        if (window == NULL) {
            throw new RuntimeException("Failed to create the GLFW window");
        }

        glfwSetKeyCallback(window, (window, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE) {
                glfwSetWindowShouldClose(window, true);
            }
        });

        glfwSetCursorPosCallback(window, new GLFWCursorPosCallback() {
            @Override
            public void invoke(long window, double x, double y) {
                if (mouseButton1) {
                    double deltaYaw = Math.PI * (ox - x) / width;
                    double deltaPitch = Math.PI * (oy - y) / height;
                    cameraRotation = rotateCamera(cameraRotation, deltaYaw, deltaPitch);
                    ox = x;
                    oy = y;
                }
            }
        });

        glfwSetMouseButtonCallback(window, new GLFWMouseButtonCallback() {
            @Override
            public void invoke(long window, int button, int action, int mods) {
                mouseButton1 = glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_1) == GLFW_PRESS;

                if (button == GLFW_MOUSE_BUTTON_1 && action == GLFW_PRESS) {
                    mouseButton1 = true;
                    DoubleBuffer xBuffer = BufferUtils.createDoubleBuffer(1);
                    DoubleBuffer yBuffer = BufferUtils.createDoubleBuffer(1);
                    glfwGetCursorPos(window, xBuffer, yBuffer);
                    ox = xBuffer.get(0);
                    oy = yBuffer.get(0);
                }

                if (button == GLFW_MOUSE_BUTTON_1 && action == GLFW_RELEASE) {
                    mouseButton1 = false;
                }
            }
        });

        glfwSetFramebufferSizeCallback(window, new GLFWFramebufferSizeCallback() {
            @Override
            public void invoke(long window, int w, int h) {
                if (w > 0 && h > 0) {
                    width = w;
                    height = h;
                }
            }
        });

        try (MemoryStack stack = stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1);
            IntBuffer pHeight = stack.mallocInt(1);
            glfwGetWindowSize(window, pWidth, pHeight);
            GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());

            glfwSetWindowPos(
                    window,
                    (vidmode.width() - pWidth.get(0)) / 2,
                    (vidmode.height() - pHeight.get(0)) / 2
            );
        }

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        glfwShowWindow(window);

        GL.createCapabilities();
        OGLUtils.printOGLparameters();

        initScene();

        glClearColor(0.05f, 0.06f, 0.09f, 1.0f);
        glDisable(GL_CULL_FACE);
        glEnable(GL_DEPTH_TEST);
        glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);

        lastFrameTime = glfwGetTime();
    }

    private void initScene() {
        generalShaderProgram = ShaderUtils.loadProgram("/debug_normal/shader.vert", "/debug_normal/shader.frag", null, null, null, null);
        flatColorShaderProgram = ShaderUtils.loadProgram("/flat_color/shader.vert", "/flat_color/shader.frag", null, null, null, null);
        simpleTexturedShaderProgram = ShaderUtils.loadProgram("/simple_textured/shader.vert", "/simple_textured/shader.frag", null, null, null, null);
        if (generalShaderProgram <= 0 || flatColorShaderProgram <= 0 || simpleTexturedShaderProgram <= 0) {
            throw new IllegalStateException("Failed to create shader program.");
        }

        // === unused meshes ===
//        cube = new Mesh(
//                GetCubeBuffers(generalShaderProgram),
//                new Vec3D(3, 3, 3)
//        );
//        cone = new Mesh(
//                getAxisConeEntry(flatColorShaderProgram, new Vec3D(1.0, 1.0, 0.0f), new Vec3D(1.0f, 1.0f, 1.0f)),
//                new Vec3D(5, 2.5, 1)
//        );
//        terrain = new Mesh(
//                GetObjBuffers(Paths.get("res/obj/terrain/SnowTerrain.obj"), generalShaderProgram),
//                1.0f,
//                new Vec3D(0.0, 0.0, -50)
//        );

        // ===
        axes = new Mesh(
                GetAxisArrowBuffers(flatColorShaderProgram, 5.0f)
        );
        plane = new Mesh(
                GetTexturedObjBuffers(Paths.get("res/obj/plane/11805_airplane_v2_L2.obj"), simpleTexturedShaderProgram),
                0.001f,
                new Vec3D(8, 5, 2)
        );

        planeDirections = List.of(
                new PlaneDirection(
                        new Vec3D(8.0, 5.0, 2.0),   // position
                        new Vec3D(0.0, 1.0, 0.0),   // orientation
                        new Vec3D(0.0, 1.0, 0.0)    // color
                ),

                new PlaneDirection(
                        new Vec3D(5.0, 9.0, 3.0),
                        new Vec3D(-1.0, 0.0, 0.0),
                        new Vec3D(1.0, 1.0, 1.0)
                ),

                new PlaneDirection(
                        new Vec3D(1.0, 13.0, 4.0),
                        new Vec3D(0.0, 1.0, 0.0),
                        new Vec3D(1.0, 0.0, 0.0)
                ),

                new PlaneDirection(
                        new Vec3D(5.0, 16.0, 5.0),
                        new Vec3D(1.0, 0.0, 0.0),
                        new Vec3D(0.0, 1.0, 0.0)
                ),

                new PlaneDirection(
                        new Vec3D(8.0, 13.0, 4.0),
                        new Vec3D(0.0, -1.0, 0.0),
                        new Vec3D(0.0, 0.0, 1.0)
                ),

                new PlaneDirection(
                        new Vec3D(5.0, 9.0, 3.0),
                        new Vec3D(-1.0, 0.0, 0.0),
                        new Vec3D(1.0, 1.0, 1.0)
                ),

                new PlaneDirection(
                        new Vec3D(1.0, 5.0, 2.0),
                        new Vec3D(0.0, -1.0, 0.0),
                        new Vec3D(0.0, 0.0, 1.0)
                ),
                new PlaneDirection(
                        new Vec3D(5.0, 1.0, 1.0),
                        new Vec3D(1.0, 0.0, 0.0),
                        new Vec3D(1.0, 0.0, 0.0)
                )
        );

//        scene_meshes.add(terrain);
//        scene_meshes.add(cone);
//        scene_meshes.add(cube);

        scene_meshes.add(axes);
        scene_meshes.add(plane);

        for (PlaneDirection direction : planeDirections){
            scene_meshes.add(
                    new Mesh(
                            getAxisEntries(
                                    direction.getColor(),
                                    flatColorShaderProgram
                            ),
                            0.25f,
                            direction.getPosition(),
                            direction.getOrientation()
                    )
            );
        }

//        cube.addTransform(time ->
//                new Mat4RotX(time * 0.6));
//        cube.addTransform(time ->
//                new Mat4RotY(time));

        loadNextTarget.run();
    }

    private void handleKeyboard(double deltaTime) {
        double speed = (glfwGetKey(window, GLFW_KEY_LEFT_SHIFT) == GLFW_PRESS ? 4.0 : 2.0) * deltaTime;

        if (glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS) {
            cameraTarget = cameraTarget.add(getCameraForward().mul(speed));
        }
        if (glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS) {
            cameraTarget = cameraTarget.sub(getCameraForward().mul(speed));
        }
        if (glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS) {
            cameraTarget = cameraTarget.sub(getCameraRight().mul(speed));
        }
        if (glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS) {
            cameraTarget = cameraTarget.add(getCameraRight().mul(speed));
        }
        if (glfwGetKey(window, GLFW_KEY_Q) == GLFW_PRESS) {
            cameraTarget = cameraTarget.add(WORLD_UP.mul(speed));
        }
        if (glfwGetKey(window, GLFW_KEY_E) == GLFW_PRESS) {
            cameraTarget = cameraTarget.sub(WORLD_UP.mul(speed));
        }
    }

    private void uploadMatrix(int location, Mat4 matrix) {
        if (location >= 0) {
            glUniformMatrix4fv(location, false, ToFloatArray.convert(matrix));
        }
    }

    private static Vec3D directionFromAngles(double azimuth, double zenith) {
        return new Vec3D(
                Math.cos(azimuth) * Math.cos(zenith),
                Math.sin(azimuth) * Math.cos(zenith),
                Math.sin(zenith)
        );
    }

    private static Quaternion quaternionFromViewDirection(Vec3D direction) {
        return Quaternion.fromTo(CAMERA_FORWARD_BASE, direction.normalized().orElse(CAMERA_FORWARD_BASE));
    }

    private Vec3D rotateDirection(Quaternion rotation, Vec3D direction) {
        Mat4 matrix = rotation.toRotationMatrix();
        return new Vec3D(
                matrix.get(0, 0) * direction.getX() + matrix.get(1, 0) * direction.getY() + matrix.get(2, 0) * direction.getZ(),
                matrix.get(0, 1) * direction.getX() + matrix.get(1, 1) * direction.getY() + matrix.get(2, 1) * direction.getZ(),
                matrix.get(0, 2) * direction.getX() + matrix.get(1, 2) * direction.getY() + matrix.get(2, 2) * direction.getZ()
        );
    }

    private Vec3D getCameraForward() {
        return rotateDirection(cameraRotation, CAMERA_FORWARD_BASE)
                .normalized()
                .orElse(CAMERA_FORWARD_BASE);
    }

    private Vec3D getCameraRight() {
        Vec3D horizontalForward = new Vec3D(getCameraForward().getX(), getCameraForward().getY(), 0.0);
        if (horizontalForward.length() > 1e-8) {
            return horizontalForward.cross(WORLD_UP)
                    .normalized()
                    .orElse(CAMERA_RIGHT_BASE);
        }

        return rotateDirection(cameraRotation, CAMERA_RIGHT_BASE)
                .normalized()
                .orElse(CAMERA_RIGHT_BASE);
    }

    private Vec3D getCameraUp() {
        return getCameraRight().cross(getCameraForward())
                .normalized()
                .orElse(WORLD_UP);
    }

    private Vec3D getCameraEye() {
        return cameraTarget.sub(getCameraForward().mul(cameraRadius));
    }

    private Mat4 getCameraViewMatrix() {
        return new Mat4ViewRH(getCameraEye(), getCameraForward().mul(cameraRadius), getCameraUp());
    }

    private Quaternion rotateCamera(Quaternion currentRotation, double deltaYaw, double deltaPitch) {
        Quaternion yawRotation = Quaternion.fromAxisAngle(deltaYaw, WORLD_UP);
        Quaternion yawedRotation = yawRotation.mul(currentRotation).normalized();

        Vec3D pitchAxis = rotateDirection(yawedRotation, CAMERA_RIGHT_BASE)
                .normalized()
                .orElse(CAMERA_RIGHT_BASE);
        Quaternion pitchRotation = Quaternion.fromAxisAngle(deltaPitch, pitchAxis);
        Quaternion candidate = pitchRotation.mul(yawedRotation).normalized();

        double verticalLimit = 0.995;
        if (Math.abs(rotateDirection(candidate, CAMERA_FORWARD_BASE).getZ()) > verticalLimit) {
            return yawedRotation;
        }

        return candidate;
    }

    private void loop() {
        while (!glfwWindowShouldClose(window)) {
            double currentTime = glfwGetTime();
            double deltaTime = currentTime - lastFrameTime;
            lastFrameTime = currentTime;

            handleKeyboard(deltaTime);

            state.deltaTime += deltaTime;
            double t = Math.min(state.deltaTime / maxT, 1.0);

            // ----------------------------
            // QUADRATIC POSITION INTERPOLATION
            // ----------------------------

            // B(t) = (1-t)[(1-t)P0+t*P1] + t[(1-t)*P1+tP2], 0<=t<=1
            Vec3D left_part = (state.posA.mul(1-t)
                    .add(state.posMid.mul(t)))
                    .mul(1-t);
            Vec3D right_part = (state.posMid.mul(1-t)
                    .add(state.posB.mul(t)))
                    .mul(t);
            Vec3D newPosition = left_part.add(right_part);
            plane.setPosition(newPosition);

            // ----------------------------
            // ROTATION INTERPOLATION
            // ----------------------------
            Quaternion rotAB =
                    Quaternion.slerp(
                            state.rotA,
                            state.rotMid,
                            t
                    );

            Quaternion rotBC =
                    Quaternion.slerp(
                            state.rotMid,
                            state.rotB,
                            t
                    );

            Quaternion finalRotation =
                    Quaternion.slerp(
                            rotAB,
                            rotBC,
                            t
                    );

            plane.setRotation(finalRotation);

            // ----------------------------
            // TARGET REACHED
            // ----------------------------

            if (state.posB.sub(newPosition).length() < reachThreshold) {
                loadNextTarget.run();
                state.deltaTime = 0;
            }


            glViewport(0, 0, width, height);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            Mat4 projection = new Mat4PerspRH(Math.toRadians(60.0), (double) height / width, 0.1, 50.0);

            Vec3D cameraPosition = getCameraEye();
            Mat4 cameraViewMatrix = getCameraViewMatrix();

            int currentShaderProgram = -1;
            for (Mesh mesh : scene_meshes) {
                if (!mesh.isVisible()) {
                    continue;
                }

                Mat4 model = mesh.getModelMatrix(currentTime, deltaTime);
                for (MeshEntry entry : mesh.getEntries()) {
                    int program = entry.getShaderProgram();
                    if (currentShaderProgram != program) {
                        glUseProgram(program);
                        currentShaderProgram = program;
                    }


                    int locModel = glGetUniformLocation(program, "uModel");
                    int locView = glGetUniformLocation(program, "uView");
                    int locProjection = glGetUniformLocation(program, "uProjection");
                    int locCameraPosition = glGetUniformLocation(program, "uCameraPosition");

                    uploadMatrix(locModel, model);
                    uploadMatrix(locView, cameraViewMatrix);
                    uploadMatrix(locProjection, projection);
                    if (locCameraPosition >= 0) {
                        glUniform3f(locCameraPosition,
                                (float) cameraPosition.getX(),
                                (float) cameraPosition.getY(),
                                (float) cameraPosition.getZ());
                    }
                    int textureId = entry.getTextureId();
                    if (textureId != 0) {
                        glActiveTexture(GL_TEXTURE0);
                        glBindTexture(GL_TEXTURE_2D, textureId);
                        glUniform1i(glGetUniformLocation(program, "uTexture"), 0);
                    }

                    entry.getBuffers().draw(entry.getTopology(), program);
                }
            }
            glUseProgram(0);

            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    private void dispose() {
        if (generalShaderProgram != 0) {
            glDeleteProgram(generalShaderProgram);
        }
        if (flatColorShaderProgram != 0) {
            glDeleteProgram(flatColorShaderProgram);
        }
        if (simpleTexturedShaderProgram != 0) {
            glDeleteProgram(simpleTexturedShaderProgram);
        }

        Set<Integer> textureIds = new HashSet<>();
        for (Mesh mesh : scene_meshes) {
            for (MeshEntry entry : mesh.getEntries()) {
                if (entry.getTextureId() != 0) {
                    textureIds.add(entry.getTextureId());
                }
            }
        }
        for (int textureId : textureIds) {
            glDeleteTextures(textureId);
        }
    }

    public void run() {
        try {
            init();
            loop();
            dispose();
            glfwFreeCallbacks(window);
            org.lwjgl.glfw.GLFW.glfwDestroyWindow(window);
        } catch (Throwable t) {
            t.printStackTrace();
        } finally {
            glfwTerminate();
            glfwSetErrorCallback(null).free();
        }
    }

    public static void main(String[] args) {
        new App().run();
    }
}
