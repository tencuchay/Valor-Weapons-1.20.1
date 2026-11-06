package com.valor.weapons.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.valor.weapons.item.ThunderLordFistItem;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Renders a mirrored Thunder Lord's Fist on the off-hand arm in 3rd person
 * when the fist is held in the main hand and the offhand is empty.
 */
public final class OffhandFistLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private final ItemInHandRenderer itemInHandRenderer;

    public OffhandFistLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> renderer,
                            ItemInHandRenderer itemInHandRenderer) {
        super(renderer);
        this.itemInHandRenderer = itemInHandRenderer;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                       AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        ItemStack mainStack = player.getMainHandItem();
        if (!(mainStack.getItem() instanceof ThunderLordFistItem)) {
            return;
        }
        if (!player.getOffhandItem().isEmpty()) {
            return;
        }

        HumanoidArm offArm = player.getMainArm().getOpposite();
        boolean isLeft = (offArm == HumanoidArm.LEFT);
        ItemDisplayContext displayContext = isLeft
                ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;

        poseStack.pushPose();
        this.getParentModel().translateToHand(offArm, poseStack);
        poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.translate((float) (isLeft ? -1 : 1) / 16.0F, 0.125F, -0.625F);

        this.itemInHandRenderer.renderItem(player, mainStack, displayContext, isLeft, poseStack, buffer, packedLight);
        poseStack.popPose();
    }
}
