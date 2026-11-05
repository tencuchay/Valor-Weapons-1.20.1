package com.valor.weapons.registry;

import com.valor.weapons.ValorWeapons;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, ValorWeapons.MODID);

    /** Shown where a thunder bolt hits an enemy. Frames: textures/particle/lightning_hit_0..4.png */
    public static final RegistryObject<SimpleParticleType> LIGHTNING_HIT = PARTICLES.register("lightning_hit",
            () -> new SimpleParticleType(false));

    private ModParticles() {}
}
