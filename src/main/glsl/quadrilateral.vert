#version 330

layout (location = 0) in vec3 position;

uniform mat4 mvpMatrix;

out float cameraDistance;

void main() {
    vec4 result = mvpMatrix * vec4(position, 1.0f);
    cameraDistance = result.z;
    gl_Position = result;
}
