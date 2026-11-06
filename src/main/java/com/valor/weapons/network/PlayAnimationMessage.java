package com.valor.weapons.network;

import com.valor.weapons.client.SkillAnimations;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> clients tracking a player: play a (Better Combat / PlayerAnimator) animation on that player. */
public class PlayAnimationMessage {
    private final int entityId;
    private final ResourceLocation animation;

    public PlayAnimationMessage(int entityId, ResourceLocation animation) {
        this.entityId = entityId;
        this.animation = animation;
    }

    public static void encode(PlayAnimationMessage message, FriendlyByteBuf buffer) {
        buffer.writeVarInt(message.entityId);
        buffer.writeResourceLocation(message.animation);
    }

    public static PlayAnimationMessage decode(FriendlyByteBuf buffer) {
        return new PlayAnimationMessage(buffer.readVarInt(), buffer.readResourceLocation());
    }

    public static void handle(PlayAnimationMessage message, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> SkillAnimations.play(message.entityId, message.animation)));
        ctx.setPacketHandled(true);
    }
}
