package com.valor.weapons.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.valor.weapons.ValorWeapons;
import com.valor.weapons.entity.ThunderBoltProjectile;
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
 * Draws the bolt with models/item/lightning_projectile.json.
 * The model is turned 180 degrees around Y so its front matches the direction of travel.
 */
public class ThunderBoltRenderer extends EntityRenderer<ThunderBoltProjectile> {
    /** Model file: assets/valor_weapons/models/item/lightning_projectile.json (registered in ClientEvents). */
    public static final ResourceLocation MODEL = new ResourceLocation(ValorWeapons.MODID, "item/lightning_projectile");

    /** Size multiplier of the model. */
    private static final float SCALE = 1.0F;
    /** Rotation pivot inside the model, in model pixels (0-16 per block). Roughly the middle of the bolt. */
    private static final float PIVOT_X = 8.0F;
    private static final float PIVOT_Y = 3.0F;
    private static final float PIVOT_Z = 13.0F;

    public ThunderBoltRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(ThunderBoltProjectile bolt, float entityYaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int packedLight) {
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(MODEL);

        pose.pushPose();
        pose.translate(0.0D, bolt.getBbHeight() / 2.0F, 0.0D);
        // Orientation comes straight from the velocity. The entity's rotation fields (and their "old" values used for
        // interpolation) are still 0 on the first rendered frame after spawning, which made the bolt stand upright.
        Vec3 motion = bolt.getDeltaMovement();
        float yaw = bolt.getYRot();
        float pitch = bolt.getXRot();
        if (motion.lengthSqr() > 1.0E-7D) {
            yaw = (float) (Mth.atan2(motion.x, motion.z) * (180.0D / Math.PI));
            pitch = (float) (-Mth.atan2(motion.y, motion.horizontalDistance()) * (180.0D / Math.PI));
        }
        pose.mulPose(Axis.YP.rotationDegrees(yaw + 180.0F));
        pose.mulPose(Axis.XP.rotationDegrees(-pitch));
        pose.scale(SCALE, SCALE, SCALE);
        pose.translate(-PIVOT_X / 16.0F, -PIVOT_Y / 16.0F, -PIVOT_Z / 16.0F);

        // "Emissive" render type: ignores world light AND the directional shading that entity shaders add.
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucentEmissive(InventoryMenu.BLOCK_ATLAS));
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(
                pose.last(), consumer, null, model,
                1.0F, 1.0F, 1.0F,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                ModelData.EMPTY, null);
        pose.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(ThunderBoltProjectile bolt) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
