#version 330 core

in vec3 inPosition;
in vec3 inNormal;
in vec3 inColor;
uniform mat4 uModel;
uniform mat4 uView;
uniform mat4 uProjection;
out vec3 vWorldPosition;
out vec3 vWorldNormal;
out vec3 vColor;

void main() {
    vec4 worldPosition = uModel * vec4(inPosition, 1.0);
    vWorldPosition = worldPosition.xyz;
    vWorldNormal = normalize(mat3(uModel) * inNormal);
    vColor = inColor;
    gl_Position = uProjection * uView * worldPosition;
}
