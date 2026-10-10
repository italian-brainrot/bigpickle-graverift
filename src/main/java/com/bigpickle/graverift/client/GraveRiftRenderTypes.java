package com.bigpickle.graverift.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Holds the custom {@link RenderType} and shader used by the Grave Rift.
 *
 * <p>Two vanilla textures are reused (the End sky and the End portal swatch) so the mod ships no
 * binary texture for the effect, but the shader itself is our own, which lets the rift use its own
 * palette instead of the teal/green of the End.</p>
 */
public final class GraveRiftRenderTypes {
    private static final ResourceLocation END_SKY =
            ResourceLocation.withDefaultNamespace("textures/environment/end_sky.png");
    private static final ResourceLocation END_PORTAL =
            ResourceLocation.withDefaultNamespace("textures/entity/end_portal.png");

    @Nullable
    private static ShaderInstance riftShader;

    private static final RenderStateShard.ShaderStateShard RIFT_SHADER =
            new RenderStateShard.ShaderStateShard(() -> riftShader);

    public static final RenderType GRAVE_RIFT = RenderType.create(
            "grave_rift",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.TRIANGLE_FAN,
            1536,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(RIFT_SHADER)
                    .setTextureState(RenderStateShard.MultiTextureStateShard.builder()
                            .add(END_SKY, false, false)
                            .add(END_PORTAL, false, false)
                            .build())
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
                    .createCompositeState(false));

    public static void setRiftShader(ShaderInstance shader) {
        riftShader = shader;
    }

    public static boolean isReady() {
        return riftShader != null;
    }

    private GraveRiftRenderTypes() {
    }
}
