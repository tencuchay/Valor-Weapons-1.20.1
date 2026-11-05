package com.valor.weapons.network;

import com.valor.weapons.ValorWeapons;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {
    private static final String PROTOCOL = "3";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ValorWeapons.MODID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private ModNetwork() {}

    public static void register() {
        CHANNEL.registerMessage(0, FistSwingMessage.class,
                FistSwingMessage::encode, FistSwingMessage::decode, FistSwingMessage::handle);
        CHANNEL.registerMessage(1, SkillActionMessage.class,
                SkillActionMessage::encode, SkillActionMessage::decode, SkillActionMessage::handle);
        CHANNEL.registerMessage(2, SkillSyncMessage.class,
                SkillSyncMessage::encode, SkillSyncMessage::decode, SkillSyncMessage::handle);
    }
}
