package com.valor.weapons.network;

import com.valor.weapons.combat.ThunderstepSkill;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: the player pressed the Thunderstep cast key or the upgrade key. */
public class SkillActionMessage {
    public static final int CAST = 0;
    public static final int UPGRADE = 1;

    private final int action;
    /** Movement keys at the moment of the press: forward (+1 W / -1 S) and strafe (+1 A / -1 D). */
    private final float forward;
    private final float strafe;

    public SkillActionMessage(int action) {
        this(action, 0.0F, 0.0F);
    }

    public SkillActionMessage(int action, float forward, float strafe) {
        this.action = action;
        this.forward = forward;
        this.strafe = strafe;
    }

    public static void encode(SkillActionMessage message, FriendlyByteBuf buffer) {
        buffer.writeVarInt(message.action);
        buffer.writeFloat(message.forward);
        buffer.writeFloat(message.strafe);
    }

    public static SkillActionMessage decode(FriendlyByteBuf buffer) {
        return new SkillActionMessage(buffer.readVarInt(), buffer.readFloat(), buffer.readFloat());
    }

    public static void handle(SkillActionMessage message, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) {
                return;
            }
            if (message.action == CAST) {
                ThunderstepSkill.tryCast(player, message.forward, message.strafe);
            } else if (message.action == UPGRADE) {
                ThunderstepSkill.tryUpgrade(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
