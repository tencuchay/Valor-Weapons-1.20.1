package com.valor.weapons.client;

import com.valor.weapons.ValorWeapons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * While Sun's Bow fires the Infinite Arrow burst the camera stays frozen where the player was looking
 * (ClientSunBowData.lockActive, set by SunBowLockMessage), and normal attacks are blocked.
 *
 * The player's real rotation is put back every client tick and every frame, so mouse movement is thrown away and the
 * view is exactly where it was when the burst ended. ComputeCameraAngles then makes sure the frame that is being
 * drawn also uses the locked angles, even if the mouse moved after the last reset.
 */
@Mod.EventBusSubscriber(modid = ValorWeapons.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class SunBowCameraLock {
    private SunBowCameraLock() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            restoreRotation();
        }
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            restoreRotation();
        }
    }

    private static void restoreRotation() {
        if (!ClientSunBowData.lockActive) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !player.isAlive()) {
            ClientSunBowData.lockActive = false; // left the world or died: never stay locked
            return;
        }
        float yaw = ClientSunBowData.lockYaw;
        float pitch = ClientSunBowData.lockPitch;
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.yRotO = yaw;
        player.xRotO = pitch;
        player.yHeadRot = yaw;
        player.yHeadRotO = yaw;
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!ClientSunBowData.lockActive) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        float partial = (float) event.getPartialTick();
        // How far the player's view is from the locked one right now; take exactly that off the camera angles.
        // (Keeps the +180 / flipped-pitch of the front third-person view intact.)
        float yawDiff = Mth.wrapDegrees(player.getViewYRot(partial) - ClientSunBowData.lockYaw);
        float pitchDiff = player.getViewXRot(partial) - ClientSunBowData.lockPitch;
        event.setYaw(event.getYaw() - yawDiff);
        event.setPitch(event.getPitch() + (mc.options.getCameraType().isMirrored() ? pitchDiff : -pitchDiff));
    }

    /** No normal attacks while the burst is firing. */
    @SubscribeEvent
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (ClientSunBowData.lockActive && event.isAttack()) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }
}
