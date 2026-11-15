package com.valor.weapons.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.valor.weapons.ValorWeapons;
import com.valor.weapons.entity.SunBoltProjectile;
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
 * Draws the sun bolt with models/item/sun_bolt.json (long axis = Z, thin side fins at the low-Z end).
 * If the bolt flies backwards in game, set FACING_OFFSET_DEGREES to 180.
 */
public class SunBoltRenderer extends EntityRenderer<SunBoltProjectile> {
    /** Model file: assets/valor_weapons/models/item/sun_bolt.json (registered in ClientEvents). */
    public static final ResourceLocation MODEL = new ResourceLocation(ValorWeapons.MODID, "item/sun_bolt");

    /** 0 = the model's +Z end is the front. 180 = the -Z end is the front. */
    private static final float FACING_OFFSET_DEGREES = 180.0F;
    private static final float SCALE = 1.0F;
    /** Rotation pivot inside the model, in model pixels (0-16 per block): roughly the middle of the bolt. */
    private static final float PIVOT_X = 7.0F;
    private static final float PIVOT_Y = 1.5F;
    private static final float PIVOT_Z = 12.0F;

    public SunBoltRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(SunBoltProjectile bolt, float entityYaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int packedLight) {
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(MODEL);

        pose.pushPose();
        pose.translate(0.0D, bolt.getBbHeight() / 2.0F, 0.0D);
        // Orientation straight from the velocity (the entity rotation is still 0 on the first rendered frame).
        Vec3 motion = bolt.getDeltaMovement();
        float yaw = bolt.getYRot();
        float pitch = bolt.getXRot();
        if (motion.lengthSqr() > 1.0E-7D) {
            yaw = (float) (Mth.atan2(motion.x, motion.z) * (180.0D / Math.PI));
            pitch = (float) (-Mth.atan2(motion.y, motion.horizontalDistance()) * (180.0D / Math.PI));
        }
        boolean flipped = FACING_OFFSET_DEGREES >= 90.0F;
        pose.mulPose(Axis.YP.rotationDegrees(yaw + FACING_OFFSET_DEGREES));
        pose.mulPose(Axis.XP.rotationDegrees(flipped ? -pitch : pitch));
        pose.scale(SCALE, SCALE, SCALE);
        pose.translate(-PIVOT_X / 16.0F, -PIVOT_Y / 16.0F, -PIVOT_Z / 16.0F);

        // Emissive render type: fullbright and no directional shading.
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucentEmissive(InventoryMenu.BLOCK_ATLAS));
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(
                pose.last(), consumer, null, model,
                1.0F, 1.0F, 1.0F,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                ModelData.EMPTY, null);
        pose.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(SunBoltProjectile bolt) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
