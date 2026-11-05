package com.valor.weapons.network;

import com.valor.weapons.combat.FistCombat;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: "the attack key is held while Thunder Lord's Fist is in the main hand". No payload. */
public class FistSwingMessage {
    public static void encode(FistSwingMessage message, FriendlyByteBuf buffer) {
    }

    public static FistSwingMessage decode(FriendlyByteBuf buffer) {
        return new FistSwingMessage();
    }

    public static void handle(FistSwingMessage message, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                FistCombat.requestBolt(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
