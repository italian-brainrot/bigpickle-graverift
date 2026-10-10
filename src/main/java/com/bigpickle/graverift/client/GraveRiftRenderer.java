package com.bigpickle.graverift.client;

import com.bigpickle.graverift.entity.GraveRiftEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Draws the Grave Rift as a single camera-facing diamond of open sky — the same shape Jules' mod
 * uses for its portal, but slightly larger and painted by our own grave-coloured shader.
 *
 * <p>The diamond is one fully opaque quad (much like the vanilla End portal), so it inherits the
 * classic swirling parallax with none of the flicker that stacked translucent discs produce. Because
 * the quad is opaque, the rift eases open by growing and eases closed by shrinking instead of using
 * a transparency fade.</p>
 */
public class GraveRiftRenderer extends EntityRenderer<GraveRiftEntity> {
    private static final ResourceLocation END_PORTAL_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/end_portal.png");

    /** Half width / height of the diamond in blocks (before the pulse is applied). */
    private static final float HALF_WIDTH = 0.8F;
    private static final float HALF_HEIGHT = 1.7F;

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

        // Grow the tear open over the first 10 ticks and shrink it closed over the last 20 so it
        // never pops, while staying fully opaque.
        float open = Mth.clamp(age / 10.0F, 0.0F, 1.0F);
        float close = Mth.clamp((remaining + 1.0F) / 20.0F, 0.0F, 1.0F);
        float scale = 0.3F + 0.7F * Math.min(open, close);

        float pulse = 1.0F + 0.05F * Mth.sin(age * 0.18F) + 0.025F * Mth.sin(age * 0.63F);

        float halfWidth = HALF_WIDTH * scale * pulse;
        float halfHeight = HALF_HEIGHT * scale * pulse;

        poseStack.pushPose();
        poseStack.translate(0.0D, entity.getBbHeight() * 0.5D, 0.0D);
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());

        VertexConsumer consumer = bufferSource.getBuffer(GraveRiftRenderTypes.GRAVE_RIFT);
        Matrix4f matrix = poseStack.last().pose();

        // Diamond: top, left, bottom, right. The shader tints by vertex colour and ignores UVs, so a
        // single white quad shows the palette at its full strength.
        consumer.addVertex(matrix, 0.0F, halfHeight, 0.0F).setColor(1.0F, 1.0F, 1.0F, 1.0F);
        consumer.addVertex(matrix, -halfWidth, 0.0F, 0.0F).setColor(1.0F, 1.0F, 1.0F, 1.0F);
        consumer.addVertex(matrix, 0.0F, -halfHeight, 0.0F).setColor(1.0F, 1.0F, 1.0F, 1.0F);
        consumer.addVertex(matrix, halfWidth, 0.0F, 0.0F).setColor(1.0F, 1.0F, 1.0F, 1.0F);

        poseStack.popPose();

        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(GraveRiftEntity entity) {
        return END_PORTAL_TEXTURE;
    }
}