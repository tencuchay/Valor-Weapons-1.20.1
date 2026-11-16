package com.valor.weapons.client;

import com.mojang.logging.LogUtils;
import com.valor.weapons.ValorWeapons;
import com.valor.weapons.network.PlayAnimationMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

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

    /** Ticks the animation fades out over when it is stopped. */
    private static final int STOP_FADE_TICKS = 6;

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

    /** Plays the animation at its normal speed. */
    public static void play(int entityId, ResourceLocation animationId) {
        play(entityId, animationId, 1.0F);
    }

    /**
     * Plays the animation on the player with this entity id (called on the client thread). speed 1.0 is the speed the
     * file was made for, 2.0 plays it twice as fast.
     */
    public static void play(int entityId, ResourceLocation animationId, float speed) {
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
        if (PlayAnimationMessage.STOP.equals(animationId)) {
            stop(player);
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
            applySpeed(layer, speed);
            method(layer.getClass(), "setAnimation", 1).invoke(layer, animation);
        } catch (Throwable t) {
            warnOnce("play", "Could not play " + animationId + ": " + t);
        }
    }

    /** One PlayerAnimator SpeedModifier per layer, created the first time a non-default speed is needed. */
    private static final Map<Object, Object> SPEED_MODIFIERS = new WeakHashMap<>();

    /**
     * Sets the playback speed of whatever the layer plays next. Uses PlayerAnimator's SpeedModifier (through
     * reflection, like everything here); when it is not available the animation just plays at its normal speed.
     * Every play call sets the speed, so an animation that follows a sped-up one is back to normal.
     */
    private static void applySpeed(Object layer, float speed) {
        try {
            Object modifier = SPEED_MODIFIERS.get(layer);
            if (modifier == null) {
                if (Math.abs(speed - 1.0F) < 1.0E-3F) {
                    return; // normal speed and no modifier yet: nothing to do
                }
                Class<?> type = Class.forName(PKG + "api.layered.modifier.SpeedModifier");
                java.lang.reflect.Field field = type.getField("speed"); // checked first: the speed must be changeable later
                if (field.getType() != float.class || java.lang.reflect.Modifier.isFinal(field.getModifiers())) {
                    throw new NoSuchFieldException("speed is not a writable float");
                }
                modifier = type.getConstructor(float.class).newInstance(speed);
                method(layer.getClass(), "addModifierLast", 1).invoke(layer, modifier);
                SPEED_MODIFIERS.put(layer, modifier);
                return;
            }
            modifier.getClass().getField("speed").setFloat(modifier, speed);
        } catch (Throwable t) {
            warnOnce("speed", "Animation speed is not available, playing at normal speed: " + t);
        }
    }

    /** Stops the animation of our layer, fading out; a plain cut if the fade API is not there. */
    private static void stop(AbstractClientPlayer player) {
        try {
            Object data = method(Class.forName(PKG + "minecraftApi.PlayerAnimationAccess"), "getPlayerAssociatedData", 1)
                    .invoke(null, player);
            Object layer = method(data.getClass(), "get", 1).invoke(data, LAYER_ID);
            if (layer == null) {
                return;
            }
            try {
                Class<?> easeClass = Class.forName(PKG + "core.util.Ease");
                @SuppressWarnings({"unchecked", "rawtypes"})
                Object ease = Enum.valueOf((Class<? extends Enum>) easeClass.asSubclass(Enum.class), "INOUTSINE");
                Object fade = Class.forName(PKG + "api.layered.modifier.AbstractFadeModifier")
                        .getMethod("standardFadeIn", int.class, easeClass).invoke(null, STOP_FADE_TICKS, ease);
                method(layer.getClass(), "replaceAnimationWithFade", 2).invoke(layer, fade, null);
            } catch (Throwable fadeFailed) {
                warnOnce("fade", "Fade-out is not available, cutting the animation instead: " + fadeFailed);
                method(layer.getClass(), "setAnimation", 1).invoke(layer, new Object[]{null});
            }
        } catch (Throwable t) {
            warnOnce("stop", "Could not stop the animation: " + t);
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
