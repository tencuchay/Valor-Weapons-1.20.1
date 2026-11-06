package com.valor.weapons.network;

import com.valor.weapons.client.ClientSkillData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: skill level, cooldown, passive stacks and free cast state for the HUD. */
public class SkillSyncMessage {
    private final int level;
    private final int cooldownRemaining;
    private final int cooldownTotal;
    private final int stacks;
    private final boolean hasFreeCast;

    public SkillSyncMessage(int level, int cooldownRemaining, int cooldownTotal, int stacks, boolean hasFreeCast) {
        this.level = level;
        this.cooldownRemaining = cooldownRemaining;
        this.cooldownTotal = cooldownTotal;
        this.stacks = stacks;
        this.hasFreeCast = hasFreeCast;
    }

    public static void encode(SkillSyncMessage message, FriendlyByteBuf buffer) {
        buffer.writeVarInt(message.level);
        buffer.writeVarInt(message.cooldownRemaining);
        buffer.writeVarInt(message.cooldownTotal);
        buffer.writeVarInt(message.stacks);
        buffer.writeBoolean(message.hasFreeCast);
    }

    public static SkillSyncMessage decode(FriendlyByteBuf buffer) {
        return new SkillSyncMessage(
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readBoolean()
        );
    }

    public static void handle(SkillSyncMessage message, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> ClientSkillData.update(message.level, message.cooldownRemaining, message.cooldownTotal, message.stacks, message.hasFreeCast));
        ctx.setPacketHandled(true);
    }
}
