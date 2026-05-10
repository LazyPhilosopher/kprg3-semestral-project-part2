#version 330 core

in vec3 inPosition;
in vec3 inNormal;
in vec2 inTexCoord;

uniform mat4 uModel;
uniform mat4 uView;
uniform mat4 uProjection;

out vec3 vNormal;
out vec3 vWorldPos;
out vec2 vTexCoord;

void main()
{
    vec4 worldPos = uModel * vec4(inPosition, 1.0);

    vWorldPos = worldPos.xyz;
    vNormal = mat3(transpose(inverse(uModel))) * inNormal;
    vTexCoord = inTexCoord;

    gl_Position = uProjection * uView * worldPos;
}
