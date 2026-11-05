package com.valor.weapons.client;

import com.valor.weapons.ValorWeapons;
import com.valor.weapons.item.ThunderLordFistItem;
import com.valor.weapons.network.FistSwingMessage;
import com.valor.weapons.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Tells the server when the player is swinging the fist (attack key down), independent of Better Combat.
 * Better Combat only reports swings to the server when they hit something, so air swings need this.
 */
@Mod.EventBusSubscriber(modid = ValorWeapons.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientInputHandler {
    private ClientInputHandler() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        boolean down = player != null
                && mc.screen == null
                && mc.mouseHandler.isMouseGrabbed()
                && mc.options.keyAttack.isDown()
                && player.getMainHandItem().getItem() instanceof ThunderLordFistItem;

        // Every tick while held, so a swing is picked up on the exact tick it becomes possible.
        // The server decides whether it counts (attack-speed cooldown), so this cannot be used to fire faster.
        if (down) {
            ModNetwork.CHANNEL.sendToServer(new FistSwingMessage());
        }
    }
}
