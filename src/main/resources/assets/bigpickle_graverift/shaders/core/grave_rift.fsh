#version 150

#moj_import <matrix.glsl>

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

uniform float GameTime;
uniform int EndPortalLayers;

in vec4 texProj0;
in vec4 vertexColor;

// A colder, sicklier palette than the End portal: ash grey, dead violet, grave
// moss and bone. The last entries are the brightest so the innermost layers the
// player sees up close stay detailed instead of turning into a flat blur.
const vec3[] COLORS = vec3[](
    vec3(0.050, 0.058, 0.074),
    vec3(0.090, 0.064, 0.112),
    vec3(0.048, 0.095, 0.086),
    vec3(0.120, 0.088, 0.150),
    vec3(0.066, 0.060, 0.090),
    vec3(0.140, 0.108, 0.180),
    vec3(0.058, 0.130, 0.112),
    vec3(0.180, 0.140, 0.212),
    vec3(0.100, 0.092, 0.132),
    vec3(0.216, 0.180, 0.244),
    vec3(0.080, 0.150, 0.138),
    vec3(0.164, 0.132, 0.202),
    vec3(0.262, 0.220, 0.300),
    vec3(0.120, 0.188, 0.168),
    vec3(0.320, 0.260, 0.360),
    vec3(0.440, 0.380, 0.470)
);

const mat4 SCALE_TRANSLATE = mat4(
    0.5, 0.0, 0.0, 0.25,
    0.0, 0.5, 0.0, 0.25,
    0.0, 0.0, 1.0, 0.0,
    0.0, 0.0, 0.0, 1.0
);

mat4 grave_rift_layer(float layer) {
    mat4 translate = mat4(
        1.0, 0.0, 0.0, 17.0 / layer,
        0.0, 1.0, 0.0, (2.0 + layer / 1.5) * (GameTime * 1.5),
        0.0, 0.0, 1.0, 0.0,
        0.0, 0.0, 0.0, 1.0
    );

    // The odd powers of the layer index make each successive swirl rotate at a
    // different rate, which is what gives the End portal its restless churn.
    mat2 rotate = mat2_rotate_z(radians((layer * layer * 4321.0 + layer * 9.0) * 2.0));

    mat2 scale = mat2((4.5 - layer / 4.0) * 2.0);

    return mat4(scale * rotate) * translate * SCALE_TRANSLATE;
}

out vec4 fragColor;

void main() {
    vec3 color = textureProj(Sampler0, texProj0).rgb * COLORS[0];
    for (int i = 0; i < EndPortalLayers; i++) {
        color += textureProj(Sampler1, texProj0 * grave_rift_layer(float(i + 1))).rgb * COLORS[i];
    }

    // The renderer hands us a per-vertex tint (hue plus the layer's opacity), so
    // the discs can share one shader and still read as halo / body / core.
    vec3 tinted = color * vertexColor.rgb * 2.4;

    // Feather along the brightness of the swirl so the discs melt into the world
    // rather than reading as flat coins, while a floor keeps the near-black core
    // opaque enough to look like a hole punched straight through.
    float lum = clamp(max(max(color.r, color.g), color.b) * 14.0, 0.0, 1.0);
    float alpha = vertexColor.a * mix(0.6, 1.0, lum);

    fragColor = vec4(tinted, alpha);
}
