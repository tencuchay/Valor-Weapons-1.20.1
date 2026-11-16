package com.valor.weapons.client;

import com.valor.weapons.ValorWeapons;
import com.valor.weapons.item.SunBowItem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;

/**
 * Sun's Bow is always held in the LEFT hand, in first and third person, whatever the player's main hand setting is.
 *
 * Vanilla draws the main-hand item on the main arm. So while a player holding the bow is being drawn, their main arm
 * is switched to LEFT, and switched back as soon as the drawing is over:
 *  - third person (and other players): RenderPlayerEvent.Pre -> Post;
 *  - first person: RenderHandEvent (before the arm is drawn) -> end of the frame.
 * The main arm is only read by rendering code, so nothing else sees the change. Players whose main hand already is
 * the left one need no change at all.
 *
 * The item model has the same display transforms for both hands (see sun_bow_model.json), so the bow looks the same.
 */
@Mod.EventBusSubscriber(modid = ValorWeapons.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class SunBowLeftHand {
    /** Players whose main arm is switched to LEFT right now (they all are RIGHT-handed in reality). */
    private static final Set<Player> FLIPPED = new HashSet<>();
    /** Players flipped by RenderPlayerEvent.Pre, to be restored by Post. */
    private static final Set<Player> FLIPPED_BY_PLAYER_RENDER = new HashSet<>();
    private static Player flippedByHandRender = null;

    private SunBowLeftHand() {}

    private static boolean holdsBow(Player player) {
        return player.getMainHandItem().getItem() instanceof SunBowItem;
    }

    /** Switches the main arm to LEFT; false when there was nothing to do (left-handed, or already switched). */
    private static boolean flip(Player player) {
        if (player.getMainArm() != HumanoidArm.RIGHT || !FLIPPED.add(player)) {
            return false;
        }
        player.setMainArm(HumanoidArm.LEFT);
        return true;
    }

    private static void restore(Player player) {
        if (FLIPPED.remove(player)) {
            player.setMainArm(HumanoidArm.RIGHT);
        }
    }

    // ---- third person (and every other player) ----

    /** Last, so that a render another mod cancelled is left alone. */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onPlayerRenderPre(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        if (!event.isCanceled() && holdsBow(player) && flip(player)) {
            FLIPPED_BY_PLAYER_RENDER.add(player);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onPlayerRenderPost(RenderPlayerEvent.Post event) {
        Player player = event.getEntity();
        if (FLIPPED_BY_PLAYER_RENDER.remove(player)) {
            restore(player);
        }
    }

    // ---- first person ----

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onHandRender(RenderHandEvent event) {
        if (event.isCanceled() || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        Player player = Minecraft.getInstance().player;
        if (player != null && holdsBow(player) && flip(player)) {
            flippedByHandRender = player;
        }
    }

    /** The hands are drawn once per frame: switch back when the frame is finished. */
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.END && flippedByHandRender != null) {
            restore(flippedByHandRender);
            flippedByHandRender = null;
        }
    }

    /** Never leave a main arm switched when the world goes away. */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        for (Player player : new HashSet<>(FLIPPED)) {
            restore(player);
        }
        FLIPPED_BY_PLAYER_RENDER.clear();
        flippedByHandRender = null;
    }
}
