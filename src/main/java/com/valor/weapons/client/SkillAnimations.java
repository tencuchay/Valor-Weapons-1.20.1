package com.valor.weapons.client;

import com.mojang.logging.LogUtils;
import com.valor.weapons.ValorWeapons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;

/**
 * Plays a PlayerAnimator animation on a player (the library Better Combat is built on).
 *
 * Everything goes through reflection on purpose: this mod then needs no extra dependency to compile, and when
 * PlayerAnimator is missing (or its API differs) the animation is simply skipped with a one-time log line.
 *
 * The animation id must be known to PlayerAnimator's own registry, i.e. the file has to exist as
 * assets/valor_weapons/player_animation/<name>.json (Better Combat's attack_animations folder is a different registry).
 */
public final class SkillAnimations {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Our own animation layer, drawn above Better Combat's layers. */
    private static final ResourceLocation LAYER_ID = new ResourceLocation(ValorWeapons.MODID, "skill_animation");
    private static final int LAYER_PRIORITY = 5000;

    private static final String PKG = "dev.kosmx.playerAnim.";

    private static boolean registered = false;
    private static final Set<String> WARNED = new HashSet<>();

    private SkillAnimations() {}

    /** Client setup: registers our animation layer so every player gets one. */
    public static void register() {
        try {
            Class<?> factoryClass = Class.forName(PKG + "minecraftApi.PlayerAnimationFactory");
            Object holder = factoryClass.getField("ANIMATION_DATA_FACTORY").get(null);

            Method registerFactory = null;
            for (Method method : holder.getClass().getMethods()) {
                if (method.getName().equals("registerFactory") && method.getParameterCount() == 3) {
                    registerFactory = method;
                    break;
                }
            }
            if (registerFactory == null) {
                throw new NoSuchMethodException("registerFactory(ResourceLocation, int, factory)");
            }
            registerFactory.setAccessible(true);

            // The factory parameter is a functional interface; implement it with a proxy so we don't need its exact type.
            Class<?> factoryType = registerFactory.getParameterTypes()[2];
            Object factory = Proxy.newProxyInstance(factoryType.getClassLoader(), new Class<?>[]{factoryType},
                    (proxy, method, args) -> {
                        if (method.getDeclaringClass() == Object.class) {
                            return switch (method.getName()) {
                                case "equals" -> proxy == args[0];
                                case "hashCode" -> System.identityHashCode(proxy);
                                default -> "ValorWeaponsAnimationLayerFactory";
                            };
                        }
                        return Class.forName(PKG + "api.layered.ModifierLayer").getConstructor().newInstance();
                    });
            registerFactory.invoke(holder, LAYER_ID, LAYER_PRIORITY, factory);
            registered = true;
            LOGGER.info("[{}] PlayerAnimator found, skill animations enabled", ValorWeapons.MODID);
        } catch (ClassNotFoundException e) {
            LOGGER.info("[{}] PlayerAnimator not installed, skill animations disabled", ValorWeapons.MODID);
        } catch (Throwable t) {
            LOGGER.warn("[{}] Could not register the skill animation layer: {}", ValorWeapons.MODID, t.toString());
        }
    }

    /** Plays the animation on the player with this entity id (called on the client thread). */
    public static void play(int entityId, ResourceLocation animationId) {
        if (!registered) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Entity entity = mc.level.getEntity(entityId);
        if (!(entity instanceof AbstractClientPlayer player)) {
            return;
        }
        try {
            Object keyframes = method(Class.forName(PKG + "minecraftApi.PlayerAnimationRegistry"), "getAnimation", 1)
                    .invoke(null, animationId);
            if (keyframes == null) {
                warnOnce("missing:" + animationId, "Animation " + animationId + " is not in PlayerAnimator's registry. "
                        + "Copy it to assets/" + animationId.getNamespace() + "/player_animation/" + animationId.getPath() + ".json");
                return;
            }

            Object data = method(Class.forName(PKG + "minecraftApi.PlayerAnimationAccess"), "getPlayerAssociatedData", 1)
                    .invoke(null, player);
            Object layer = method(data.getClass(), "get", 1).invoke(data, LAYER_ID);
            if (layer == null) {
                warnOnce("layer", "The animation layer " + LAYER_ID + " is missing on the player");
                return;
            }

            Object animation = Class.forName(PKG + "api.layered.KeyframeAnimationPlayer")
                    .getConstructor(Class.forName(PKG + "core.data.KeyframeAnimation")).newInstance(keyframes);
            method(layer.getClass(), "setAnimation", 1).invoke(layer, animation);
        } catch (Throwable t) {
            warnOnce("play", "Could not play " + animationId + ": " + t);
        }
    }

    /** First public method with this name and parameter count. */
    private static Method method(Class<?> type, String name, int parameters) throws NoSuchMethodException {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == parameters) {
                method.setAccessible(true);
                return method;
            }
        }
        throw new NoSuchMethodException(type.getName() + "." + name);
    }

    private static void warnOnce(String key, String message) {
        if (WARNED.add(key)) {
            LOGGER.warn("[{}] {}", ValorWeapons.MODID, message);
        }
    }
}
