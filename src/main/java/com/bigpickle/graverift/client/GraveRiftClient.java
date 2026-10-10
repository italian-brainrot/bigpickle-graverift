package com.bigpickle.graverift.client;

import java.io.IOException;

import com.bigpickle.graverift.GraveRiftMod;
import com.bigpickle.graverift.registry.ModEntities;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

/**
 * Client-only wiring.
 *
 * <p>Everything here is registered through NeoForge's mod event bus while the client is starting up,
 * so no mixins or access transformers are needed to add a shader or an entity renderer.</p>
 */
@EventBusSubscriber(modid = GraveRiftMod.MODID, value = Dist.CLIENT)
public final class GraveRiftClient {

    private GraveRiftClient() {
    }

    /**
     * Builds our custom shader from {@code assets/bigpickle_graverift/shaders/core/grave_rift.*}.
     *
     * <p>The loaded instance is handed to {@link GraveRiftRenderTypes}; the {@link RenderType} uses a
     * supplier so it picks up the shader as soon as the resource reload finishes.</p>
     */
    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
        ShaderInstance shader = new ShaderInstance(
                event.getResourceProvider(),
                GraveRiftMod.id("grave_rift"),
                DefaultVertexFormat.POSITION_COLOR);
        event.registerShader(shader, GraveRiftRenderTypes::setRiftShader);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.GRAVE_RIFT.get(), GraveRiftRenderer::new);
    }
}
