package com.valor.weapons.client;

import com.valor.weapons.ValorWeapons;
import com.valor.weapons.registry.ModEntities;
import com.valor.weapons.registry.ModParticles;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ValorWeapons.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.THUNDER_BOLT.get(), ThunderBoltRenderer::new);
        event.registerEntityRenderer(ModEntities.LIGHTNING_STRIKE.get(), LightningStrikeRenderer::new);
    }

    /** The projectile model is not attached to any item, so ask the game to load and bake it anyway. */
    @SubscribeEvent
    public static void onRegisterAdditionalModels(ModelEvent.RegisterAdditional event) {
        event.register(ThunderBoltRenderer.MODEL);
    }

    @SubscribeEvent
    public static void onRegisterParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.LIGHTNING_HIT.get(), LightningHitParticle.Provider::new);
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ModKeys.THUNDERSTEP);
        event.register(ModKeys.THUNDERSTEP_UPGRADE);
    }

    @SubscribeEvent
    public static void onRegisterGuiOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("thunderstep", SkillHud.OVERLAY);
    }
}
