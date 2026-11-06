package com.valor.weapons.client;

import com.valor.weapons.ValorWeapons;
import com.valor.weapons.item.ThunderLordFistItem;
import com.valor.weapons.network.ModNetwork;
import com.valor.weapons.network.SkillActionMessage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ValorWeapons.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class SkillClientEvents {
    private SkillClientEvents() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        ClientSkillData.clientTicks++;

        Minecraft mc = Minecraft.getInstance();
        boolean canUse = mc.player != null && mc.screen == null
                && mc.player.getMainHandItem().getItem() instanceof ThunderLordFistItem;

        // Always drain the clicks, even when they are not usable, so they don't pile up.
        while (ModKeys.THUNDERSTEP.consumeClick()) {
            if (canUse) {
                // Dash direction follows the movement keys (W/A/S/D); none held = forward.
                ModNetwork.CHANNEL.sendToServer(new SkillActionMessage(SkillActionMessage.CAST,
                        mc.player.input.forwardImpulse, mc.player.input.leftImpulse));
            }
        }
        while (ModKeys.THUNDERSTEP_UPGRADE.consumeClick()) {
            if (canUse) {
                mc.setScreen(new SkillUpgradeScreen()); // the Upgrade button inside sends the request
            }
        }
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (event.getHand() != InteractionHand.OFF_HAND) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        ItemStack mainStack = mc.player.getMainHandItem();
        if (!(mainStack.getItem() instanceof ThunderLordFistItem) || !mc.player.getOffhandItem().isEmpty()) {
            return;
        }

        event.setCanceled(true);

        HumanoidArm arm = mc.player.getMainArm().getOpposite();
        int side = (arm == HumanoidArm.RIGHT) ? 1 : -1;
        float swingProgress = event.getSwingProgress();
        float equipProgress = event.getEquipProgress();
        PoseStack poseStack = event.getPoseStack();

        poseStack.pushPose();

        float f4 = -0.4F * Mth.sin(Mth.sqrt(swingProgress) * (float) Math.PI);
        float f5 = 0.2F * Mth.sin(Mth.sqrt(swingProgress) * ((float) Math.PI * 2.0F));
        float f6 = -0.2F * Mth.sin(swingProgress * (float) Math.PI);
        poseStack.translate(side * f4, f5, f6);
        poseStack.translate(side * 0.56F, -0.52F + equipProgress * -0.6F, -0.72F);

        float f = Mth.sin(swingProgress * swingProgress * (float) Math.PI);
        poseStack.mulPose(Axis.YP.rotationDegrees(side * (45.0F + f * -20.0F)));
        float f1 = Mth.sin(Mth.sqrt(swingProgress) * (float) Math.PI);
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * f1 * -20.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(f1 * -80.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(side * -45.0F));

        ItemDisplayContext displayContext = (arm == HumanoidArm.RIGHT)
                ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                : ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        boolean isLeftHand = (arm != HumanoidArm.RIGHT);

        mc.getEntityRenderDispatcher().getItemInHandRenderer().renderItem(
                mc.player, mainStack, displayContext, isLeftHand,
                poseStack, event.getMultiBufferSource(), event.getPackedLight()
        );

        poseStack.popPose();
    }
}
