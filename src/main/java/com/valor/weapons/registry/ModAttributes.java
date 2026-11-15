package com.valor.weapons.registry;

import com.valor.weapons.ValorWeapons;
import com.valor.weapons.combat.ThunderstepSkill;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Player attributes of Valor Weapons.
 *
 * valor_weapons:thunder_stacks - the Thunder Lord's Fist passive stacks (0..6) of the player. Written by
 * ThunderstepSkill.sync(); syncable, so other mods can read it on the client with player.getAttributeValue(...).
 */
@Mod.EventBusSubscriber(modid = ValorWeapons.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModAttributes {
    public static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(ForgeRegistries.ATTRIBUTES, ValorWeapons.MODID);

    public static final RegistryObject<Attribute> THUNDER_STACKS = ATTRIBUTES.register("thunder_stacks",
            () -> new RangedAttribute("attribute.name.valor_weapons.thunder_stacks",
                    0.0D, 0.0D, ThunderstepSkill.MAX_PASSIVE_STACKS).setSyncable(true));

    private ModAttributes() {}

    @SubscribeEvent
    public static void onAttributeModification(EntityAttributeModificationEvent event) {
        event.add(EntityType.PLAYER, THUNDER_STACKS.get());
    }
}
