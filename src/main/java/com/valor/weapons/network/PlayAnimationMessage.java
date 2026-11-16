package com.valor.weapons.network;

import com.valor.weapons.ValorWeapons;
import com.valor.weapons.client.SkillAnimations;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> clients tracking a player: play a (Better Combat / PlayerAnimator) animation on that player. */
public class PlayAnimationMessage {
    /** Pseudo animation id: "stop whatever the skill animation layer is playing". */
    public static final ResourceLocation STOP = new ResourceLocation(ValorWeapons.MODID, "stop");

    private final int entityId;
    private final ResourceLocation animation;
    /** Playback speed: 1.0 = the speed the animation file was made for. */
    private final float speed;

    public PlayAnimationMessage(int entityId, ResourceLocation animation) {
        this(entityId, animation, 1.0F);
    }

    public PlayAnimationMessage(int entityId, ResourceLocation animation, float speed) {
        this.entityId = entityId;
        this.animation = animation;
        this.speed = speed;
    }

    public static void encode(PlayAnimationMessage message, FriendlyByteBuf buffer) {
        buffer.writeVarInt(message.entityId);
        buffer.writeResourceLocation(message.animation);
        buffer.writeFloat(message.speed);
    }

    public static PlayAnimationMessage decode(FriendlyByteBuf buffer) {
        return new PlayAnimationMessage(buffer.readVarInt(), buffer.readResourceLocation(), buffer.readFloat());
    }

    public static void handle(PlayAnimationMessage message, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> SkillAnimations.play(message.entityId, message.animation, message.speed)));
        ctx.setPacketHandled(true);
    }
}
