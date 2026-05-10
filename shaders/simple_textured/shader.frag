#version 330 core

in vec3 vNormal;
in vec3 vWorldPos;
in vec2 vTexCoord;

uniform sampler2D uTexture;

out vec4 FragColor;

void main()
{
    vec3 normal = normalize(vNormal);

    // Static light direction
    vec3 lightDir = normalize(vec3(1.0, 1.0, 1.0));

    float diffuse = max(dot(normal, lightDir), 0.0);

    // Small ambient term
    float ambient = 0.25;

    vec3 texColor = texture(uTexture, vTexCoord).rgb;

    vec3 finalColor = texColor * (ambient + diffuse);

    FragColor = vec4(finalColor, 1.0);
}