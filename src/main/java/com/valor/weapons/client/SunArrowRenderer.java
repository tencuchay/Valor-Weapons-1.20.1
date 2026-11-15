package com.valor.weapons.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.valor.weapons.ValorWeapons;
import com.valor.weapons.entity.SunArrowProjectile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.model.data.ModelData;

/**
 * Draws the sun arrow with models/item/sun_arrow.json (long axis = Z).
 * Same facing convention as SunBoltRenderer. If the arrow flies backwards, set FACING_OFFSET_DEGREES to 0.
 */
public class SunArrowRenderer extends EntityRenderer<SunArrowProjectile> {
    public static final ResourceLocation MODEL = new ResourceLocation(ValorWeapons.MODID, "item/sun_arrow");

    private static final float FACING_OFFSET_DEGREES = 180.0F;
    private static final float SCALE = 1.0F;
    /** Rotation pivot inside the model, in model pixels: the middle of the arrow. */
    private static final float PIVOT_X = 8.0F;
    private static final float PIVOT_Y = 0.0F;
    private static final float PIVOT_Z = 12.0F;

    public SunArrowRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(SunArrowProjectile arrow, float entityYaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int packedLight) {
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(MODEL);

        pose.pushPose();
        pose.translate(0.0D, arrow.getBbHeight() / 2.0F, 0.0D);
        Vec3 motion = arrow.getDeltaMovement();
        float yaw = arrow.getYRot();
        float pitch = arrow.getXRot();
        if (motion.lengthSqr() > 1.0E-7D) {
            yaw = (float) (Mth.atan2(motion.x, motion.z) * (180.0D / Math.PI));
            pitch = (float) (-Mth.atan2(motion.y, motion.horizontalDistance()) * (180.0D / Math.PI));
        }
        boolean flipped = FACING_OFFSET_DEGREES >= 90.0F;
        pose.mulPose(Axis.YP.rotationDegrees(yaw + FACING_OFFSET_DEGREES));
        pose.mulPose(Axis.XP.rotationDegrees(flipped ? -pitch : pitch));
        pose.scale(SCALE, SCALE, SCALE);
        pose.translate(-PIVOT_X / 16.0F, -PIVOT_Y / 16.0F, -PIVOT_Z / 16.0F);

        // The model faces have tintindex 0 and a white texture, so this is the arrow's color.
        int color = arrow.getColor();
        float red = ((color >> 16) & 0xFF) / 255.0F;
        float green = ((color >> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;

        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucentEmissive(InventoryMenu.BLOCK_ATLAS));
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(
                pose.last(), consumer, null, model,
                red, green, blue,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                ModelData.EMPTY, null);
        pose.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(SunArrowProjectile arrow) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
