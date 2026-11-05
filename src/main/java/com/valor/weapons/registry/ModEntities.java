package com.valor.weapons.registry;

import com.valor.weapons.ValorWeapons;
import com.valor.weapons.entity.LightningStrikeEntity;
import com.valor.weapons.entity.ThunderBoltProjectile;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ValorWeapons.MODID);

    public static final RegistryObject<EntityType<ThunderBoltProjectile>> THUNDER_BOLT = ENTITIES.register("thunder_bolt",
            () -> EntityType.Builder.<ThunderBoltProjectile>of(ThunderBoltProjectile::new, MobCategory.MISC)
                    .sized(0.4F, 0.4F)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .fireImmune()
                    .noSave()
                    .noSummon()
                    .build(ValorWeapons.MODID + ":thunder_bolt"));

    public static final RegistryObject<EntityType<LightningStrikeEntity>> LIGHTNING_STRIKE = ENTITIES.register("lightning_strike",
            () -> EntityType.Builder.<LightningStrikeEntity>of(LightningStrikeEntity::new, MobCategory.MISC)
                    .sized(0.1F, 0.1F)
                    .clientTrackingRange(10)
                    .updateInterval(20)
                    .fireImmune()
                    .noSave()
                    .noSummon()
                    .build(ValorWeapons.MODID + ":lightning_strike"));

    private ModEntities() {}
}
