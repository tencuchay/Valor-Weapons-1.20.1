package com.valor.weapons.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.valor.weapons.ValorWeapons;
import com.valor.weapons.entity.LightningStrikeEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Draws the strike as a tall, camera-facing (around the vertical axis) quad, fullbright, one frame per tick. */
public class LightningStrikeRenderer extends EntityRenderer<LightningStrikeEntity> {
    private static final ResourceLocation[] FRAMES = new ResourceLocation[LightningStrikeEntity.ANIMATION_TICKS];

    static {
        for (int i = 0; i < FRAMES.length; i++) {
            FRAMES[i] = new ResourceLocation(ValorWeapons.MODID, "textures/entity/thunder_strike_" + i + ".png");
        }
    }

    /** Frames are 64x231 pixels. */
    private static final float HEIGHT = LightningStrikeEntity.VISUAL_HEIGHT;
    private static final float WIDTH = HEIGHT * 64.0F / 231.0F;

    public LightningStrikeRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(LightningStrikeEntity strike, float entityYaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int packedLight) {
        // tickCount is 1 on the first tick after spawning; the strike "goes off" when tickCount exceeds the delay.
        float age = strike.tickCount + partialTick - 1.0F - strike.getDelay();
        int frame = (int) Math.floor(age);
        if (frame < 0 || frame >= FRAMES.length) {
            return;
        }

        pose.pushPose();
        // Face the camera around the Y axis only, so the bolt stays vertical.
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - this.entityRenderDispatcher.camera.getYRot()));

        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucentEmissive(FRAMES[frame]));
        PoseStack.Pose last = pose.last();
        Matrix4f matrix = last.pose();
        Matrix3f normal = last.normal();
        float half = WIDTH / 2.0F;
        vertex(consumer, matrix, normal, -half, 0.0F, 0.0F, 0.0F, 1.0F);
        vertex(consumer, matrix, normal, half, 0.0F, 0.0F, 1.0F, 1.0F);
        vertex(consumer, matrix, normal, half, HEIGHT, 0.0F, 1.0F, 0.0F);
        vertex(consumer, matrix, normal, -half, HEIGHT, 0.0F, 0.0F, 0.0F);
        pose.popPose();
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, Matrix3f normal,
                               float x, float y, float z, float u, float v) {
        consumer.vertex(matrix, x, y, z)
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(normal, 0.0F, 1.0F, 0.0F)
                .endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(LightningStrikeEntity strike) {
        return FRAMES[0];
    }
}
