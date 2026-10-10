package com.bigpickle.graverift.client;

import com.bigpickle.graverift.entity.GraveRiftEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Draws the Grave Rift.
 *
 * <p>The visuals are made of three camera facing discs that all share a single custom shader, very
 * much like the vanilla End Portal but with a colder, sicklier palette. Because the shader uses
 * projected screen coordinates, the classic swirling portal parallax appears automatically; we only
 * have to decide the shape, size, colour and layering of the discs.</p>
 *
 * <p>Layering, from back to front:</p>
 * <ol>
 *     <li>a large, dim, slowly rotating halo – the "light" bleeding into the world,</li>
 *     <li>the main swirling body of the tear,</li>
 *     <li>a small near black core that reads as a hole straight through reality.</li>
 * </ol>
 */
public class GraveRiftRenderer extends EntityRenderer<GraveRiftEntity> {
    private static final ResourceLocation END_PORTAL_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/end_portal.png");

    public GraveRiftRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
        this.shadowStrength = 0.0F;
    }

    @Override
    public void render(GraveRiftEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        if (!GraveRiftRenderTypes.isReady()) {
            return;
        }

        float age = entity.tickCount + partialTick;
        float remaining = entity.getLifetime() + partialTick;

        // Fade in over the first 12 ticks and out over the last 40 so the rift never pops.
        float fade = Mth.clamp(age / 12.0F, 0.0F, 1.0F) * Mth.clamp(remaining / 40.0F, 0.0F, 1.0F);
        if (fade <= 0.001F) {
            return;
        }

        float pulse = 1.0F + 0.06F * Mth.sin(age * 0.22F) + 0.03F * Mth.sin(age * 0.71F);

        poseStack.pushPose();
        poseStack.translate(0.0D, entity.getBbHeight() * 0.5D, 0.0D);
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());

        VertexConsumer consumer = bufferSource.getBuffer(GraveRiftRenderTypes.GRAVE_RIFT);

        // Dim halo.
        poseStack.pushPose();
        poseStack.mulPose(Axis.ZP.rotationDegrees(age * 5.5F));
        drawDisc(consumer, poseStack.last().pose(),
                1.55F * pulse, 1.75F * pulse,
                0.30F, 0.62F, 0.48F, 0.30F * fade, 48);
        poseStack.popPose();

        // Main body.
        poseStack.pushPose();
        poseStack.mulPose(Axis.ZP.rotationDegrees(-age * 3.1F));
        drawDisc(consumer, poseStack.last().pose(),
                1.10F * pulse, 1.40F * pulse,
                0.55F, 0.86F, 0.66F, 0.95F * fade, 64);
        poseStack.popPose();

        // Inky core.
        poseStack.pushPose();
        poseStack.mulPose(Axis.ZP.rotationDegrees(age * 8.0F));
        drawDisc(consumer, poseStack.last().pose(),
                0.34F, 0.52F,
                0.02F, 0.03F, 0.035F, 0.92F * fade, 32);
        poseStack.popPose();

        poseStack.popPose();

        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    private static void drawDisc(VertexConsumer consumer, Matrix4f matrix,
                                 float radiusX, float radiusY,
                                 float red, float green, float blue, float alpha,
                                 int segments) {
        // Triangle fan: centre vertex first, then the perimeter (repeating the first point to close).
        consumer.addVertex(matrix, 0.0F, 0.0F, 0.0F).setColor(red, green, blue, alpha);
        for (int i = 0; i <= segments; i++) {
            double angle = (Math.PI * 2.0D * i) / segments;
            float x = (float) (Math.cos(angle) * radiusX);
            float y = (float) (Math.sin(angle) * radiusY);
            consumer.addVertex(matrix, x, y, 0.0F).setColor(red, green, blue, alpha);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(GraveRiftEntity entity) {
        return END_PORTAL_TEXTURE;
    }
}
