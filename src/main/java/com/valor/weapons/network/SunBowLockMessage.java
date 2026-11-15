package com.valor.weapons.network;

import com.valor.weapons.client.ClientSunBowData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> client: the Infinite Arrow burst started (locked = true, with the view direction to freeze the camera at)
 * or ended (locked = false).
 */
public class SunBowLockMessage {
    private final boolean locked;
    private final float yaw;
    private final float pitch;

    public SunBowLockMessage(boolean locked, float yaw, float pitch) {
        this.locked = locked;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public static void encode(SunBowLockMessage message, FriendlyByteBuf buffer) {
        buffer.writeBoolean(message.locked);
        buffer.writeFloat(message.yaw);
        buffer.writeFloat(message.pitch);
    }

    public static SunBowLockMessage decode(FriendlyByteBuf buffer) {
        return new SunBowLockMessage(buffer.readBoolean(), buffer.readFloat(), buffer.readFloat());
    }

    public static void handle(SunBowLockMessage message, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            ClientSunBowData.lockYaw = message.yaw;
            ClientSunBowData.lockPitch = message.pitch;
            ClientSunBowData.lockActive = message.locked;
        });
        ctx.setPacketHandled(true);
    }
}
