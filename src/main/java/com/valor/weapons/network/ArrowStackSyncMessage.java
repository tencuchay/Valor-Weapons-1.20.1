package com.valor.weapons.network;

import com.valor.weapons.client.ClientSunBowData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: how many Infinite Arrow stacks the player has (for the HUD). */
public class ArrowStackSyncMessage {
    private final int stacks;

    public ArrowStackSyncMessage(int stacks) {
        this.stacks = stacks;
    }

    public static void encode(ArrowStackSyncMessage message, FriendlyByteBuf buffer) {
        buffer.writeVarInt(message.stacks);
    }

    public static ArrowStackSyncMessage decode(FriendlyByteBuf buffer) {
        return new ArrowStackSyncMessage(buffer.readVarInt());
    }

    public static void handle(ArrowStackSyncMessage message, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> ClientSunBowData.stacks = message.stacks);
        ctx.setPacketHandled(true);
    }
}
