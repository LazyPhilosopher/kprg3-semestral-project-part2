#version 330 core

in vec3 vWorldPosition;
in vec3 vWorldNormal;
in vec3 vColor;
uniform vec3 uCameraPosition;
out vec4 fragColor;

void main() {
    vec3 viewDirection = normalize(uCameraPosition - vWorldPosition);
    float facing = max(dot(normalize(vWorldNormal), viewDirection), 0.0);
    vec3 normalColor = normalize(vWorldNormal) * 0.5 + 0.5;
    vec3 vertexColor = clamp(vColor, 0.0, 1.0);
    float hasVertexColor = step(0.001, dot(vertexColor, vertexColor));
    vec3 litColor = mix(normalColor, vertexColor, hasVertexColor);
//    vec3 baseColor = litColor * (0.2 + 0.8 * facing);
    vec3 baseColor = normalColor * (0.2 + 0.8 * facing);
    fragColor = vec4(baseColor, 1.0);
}
