package com.valor.weapons.registry;

import com.valor.weapons.ValorWeapons;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ValorWeapons.MODID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("valor_weapons",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + ValorWeapons.MODID))
                    .icon(() -> new ItemStack(ModItems.THUNDER_LORD_FIST.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.THUNDER_LORD_FIST.get());
                        // Add future weapons here.
                    })
                    .build());

    private ModCreativeTabs() {}
}
