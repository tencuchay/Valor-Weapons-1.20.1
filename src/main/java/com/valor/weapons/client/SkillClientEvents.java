package com.valor.weapons.client;

import com.valor.weapons.ValorWeapons;
import com.valor.weapons.item.ThunderLordFistItem;
import com.valor.weapons.network.ModNetwork;
import com.valor.weapons.network.SkillActionMessage;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
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
}
