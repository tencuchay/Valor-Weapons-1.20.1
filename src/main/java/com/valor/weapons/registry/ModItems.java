package com.valor.weapons.registry;

import com.valor.weapons.ValorWeapons;
import com.valor.weapons.item.SunBowItem;
import com.valor.weapons.item.ThunderLordFistItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ValorWeapons.MODID);

    // id: valor_weapons:thunder_lord_fist
    public static final RegistryObject<Item> THUNDER_LORD_FIST = ITEMS.register("thunder_lord_fist",
            () -> new ThunderLordFistItem(new Item.Properties().durability(1800).rarity(Rarity.EPIC)));

    // id: valor_weapons:sun_bow
    public static final RegistryObject<Item> SUN_BOW = ITEMS.register("sun_bow",
            () -> new SunBowItem(new Item.Properties().durability(1800).rarity(Rarity.EPIC)));

    private ModItems() {}
}
