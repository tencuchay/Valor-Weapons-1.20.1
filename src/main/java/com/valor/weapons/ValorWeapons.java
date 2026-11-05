package com.valor.weapons;

import com.valor.weapons.network.ModNetwork;
import com.valor.weapons.registry.ModCreativeTabs;
import com.valor.weapons.registry.ModEntities;
import com.valor.weapons.registry.ModItems;
import com.valor.weapons.registry.ModParticles;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(ValorWeapons.MODID)
public class ValorWeapons {
    public static final String MODID = "valor_weapons";

    public ValorWeapons() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModItems.ITEMS.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        ModParticles.PARTICLES.register(modBus);
        ModCreativeTabs.TABS.register(modBus);

        ModNetwork.register();

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }
}
