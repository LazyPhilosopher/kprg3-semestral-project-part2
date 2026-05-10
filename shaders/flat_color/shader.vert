#version 330 core

in vec3 inPosition;
in vec3 inColor;
uniform mat4 uModel;
uniform mat4 uView;
uniform mat4 uProjection;
out vec3 vColor;

void main() {
    vColor = inColor;
    gl_Position = uProjection * uView * uModel * vec4(inPosition, 1.0);
}
