package com.valor.weapons.network;

import com.valor.weapons.client.ClientSkillData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: skill level and cooldown, for the HUD. */
public class SkillSyncMessage {
    private final int level;
    private final int cooldownRemaining;
    private final int cooldownTotal;

    public SkillSyncMessage(int level, int cooldownRemaining, int cooldownTotal) {
        this.level = level;
        this.cooldownRemaining = cooldownRemaining;
        this.cooldownTotal = cooldownTotal;
    }

    public static void encode(SkillSyncMessage message, FriendlyByteBuf buffer) {
        buffer.writeVarInt(message.level);
        buffer.writeVarInt(message.cooldownRemaining);
        buffer.writeVarInt(message.cooldownTotal);
    }

    public static SkillSyncMessage decode(FriendlyByteBuf buffer) {
        return new SkillSyncMessage(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
    }

    public static void handle(SkillSyncMessage message, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> ClientSkillData.update(message.level, message.cooldownRemaining, message.cooldownTotal));
        ctx.setPacketHandled(true);
    }
}
