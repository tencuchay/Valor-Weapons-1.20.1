package com.valor.weapons.combat;

import com.valor.weapons.ValorWeapons;
import com.valor.weapons.item.ThunderLordFistItem;
import com.valor.weapons.network.ModNetwork;
import com.valor.weapons.network.SkillSyncMessage;
import com.valor.weapons.registry.ModParticles;
import com.valor.weapons.util.Targeting;
import com.valor.weapons.util.ValorDamage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Thunderstep (Thien Loi Bo) - active skill of Thunder Lord's Fist.
 *
 * - Dash 4 blocks horizontally in the direction of the movement keys (W/A/S/D, none = forward). Smooth: the server sets the player's velocity every tick
 *   for a fixed time and the client moves itself (so walls, steps and slopes behave normally).
 * - Hits every valid enemy along the way ONCE: flat damage (by level) + 30% of valor_items:magic_damage, magic damage.
 * - Shield for 2 seconds: flat amount (by level) + 4% of max health, as a temporary modifier on valor_items:shield
 *   (falls back to vanilla absorption hearts if that attribute does not exist).
 * - When the dash ends the player's NEXT attack (within 5 s) is a Lightning Strike (see FistCombat).
 * - 9 second cooldown. Levels 1..6 (start at 1), each upgrade costs 10 experience levels; upgrade screen opened with the upgrade key.
 */
@Mod.EventBusSubscriber(modid = ValorWeapons.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ThunderstepSkill {
    /** Every player starts at level 1. */
    public static final int STARTING_LEVEL = 1;
    public static final int MAX_LEVEL = 6;
    public static final int COOLDOWN_TICKS = 9 * 20;
    public static final int UPGRADE_COST_LEVELS = 10;

    /** Flat part of the damage / shield for level 1..6. */
    private static final double[] DAMAGE = {2.5D, 3.0D, 3.5D, 4.0D, 4.5D, 5.0D};
    private static final double[] SHIELD = {2.0D, 3.0D, 3.5D, 4.0D, 4.5D, 5.0D};
    public static final double MAGIC_DAMAGE_SCALE = 0.30D;
    public static final double SHIELD_MAX_HEALTH_FRACTION = 0.04D;

    public static final double DASH_DISTANCE = 4.0D;
    private static final int DASH_TICKS = 5;
    /** Blocks per tick while dashing. */
    private static final double DASH_SPEED = DASH_DISTANCE / DASH_TICKS;
    public static final int SHIELD_TICKS = 2 * 20;

    public static final ResourceLocation SHIELD_ATTRIBUTE_ID = new ResourceLocation("valor_items", "shield");
    private static final UUID SHIELD_MODIFIER_UUID = UUID.fromString("c6b2f1a4-8d3e-4e57-b9a1-3f0d7a52e8c4");
    private static final String NBT_LEVEL = "valor_weapons_thunderstep_level";

    private static final class Dash {
        final Vec3 dir;
        final int level;
        final Set<Integer> hit = new HashSet<>();
        int ticksLeft = DASH_TICKS;

        Dash(Vec3 dir, int level) {
            this.dir = dir;
            this.level = level;
        }
    }

    private static final class ShieldState {
        final long expireTick;
        final double amount;
        final boolean viaAttribute;

        ShieldState(long expireTick, double amount, boolean viaAttribute) {
            this.expireTick = expireTick;
            this.amount = amount;
            this.viaAttribute = viaAttribute;
        }
    }

    private static final class PassiveState {
        int stacks = 0;
        boolean hasFreeCast = false;
        long savedCooldownRemaining = 0;
        long lastStackTick = -1000;
    }

    private static final Map<UUID, Dash> DASHES = new HashMap<>();
    private static final Map<UUID, Long> COOLDOWN_END = new HashMap<>();
    private static final Map<UUID, ShieldState> SHIELDS = new HashMap<>();
    private static final Map<UUID, PassiveState> PASSIVES = new HashMap<>();

    public static final int MAX_PASSIVE_STACKS = 6;

    private static PassiveState passive(ServerPlayer player) {
        return PASSIVES.computeIfAbsent(player.getUUID(), id -> new PassiveState());
    }

    /** Called when an attack hits an enemy with the weapon. Grants 1 passive mark, up to 6 for a free cast. */
    public static void addStack(ServerPlayer player) {
        if (!holdingFist(player) || !player.isAlive()) {
            return;
        }
        long now = player.level().getGameTime();
        PassiveState p = passive(player);
        if (p.lastStackTick == now) {
            return; // 1 stack per attack action tick
        }
        p.lastStackTick = now;

        if (p.hasFreeCast) {
            return; // Already ready
        }

        p.stacks++;
        if (p.stacks >= MAX_PASSIVE_STACKS) {
            p.stacks = MAX_PASSIVE_STACKS;
            p.hasFreeCast = true;

            long cdEnd = COOLDOWN_END.getOrDefault(player.getUUID(), 0L);
            if (cdEnd > now) {
                p.savedCooldownRemaining = cdEnd - now;
            } else {
                p.savedCooldownRemaining = 0;
            }

            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5F, 1.8F);
            player.displayClientMessage(Component.translatable("message.valor_weapons.thunderstep.free_ready"), true);
        }
        sync(player);
    }

    /** Resets passive marks back to 0 (e.g. after landing an empowered attack). */
    public static void resetStacks(ServerPlayer player) {
        PassiveState p = passive(player);
        p.stacks = 0;
        p.hasFreeCast = false;
        p.savedCooldownRemaining = 0;
        sync(player);
    }

    private ThunderstepSkill() {}

    // ---------------------------------------------------------------- level

    /** Flat damage part at the given level (1..MAX_LEVEL). Used by the upgrade screen. */
    public static double damageAtLevel(int level) {
        return DAMAGE[Mth.clamp(level, 1, MAX_LEVEL) - 1];
    }

    /** Flat shield part at the given level (1..MAX_LEVEL). Used by the upgrade screen. */
    public static double shieldAtLevel(int level) {
        return SHIELD[Mth.clamp(level, 1, MAX_LEVEL) - 1];
    }

    public static int getLevel(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        return data.contains(NBT_LEVEL) ? Mth.clamp(data.getInt(NBT_LEVEL), 1, MAX_LEVEL) : STARTING_LEVEL;
    }

    private static void setLevel(ServerPlayer player, int level) {
        player.getPersistentData().putInt(NBT_LEVEL, Mth.clamp(level, 1, MAX_LEVEL));
    }

    private static boolean holdingFist(ServerPlayer player) {
        return player.getMainHandItem().getItem() instanceof ThunderLordFistItem;
    }

    /** Key press: spend experience levels to raise the skill level. */
    public static void tryUpgrade(ServerPlayer player) {
        if (!holdingFist(player)) {
            return;
        }
        int level = getLevel(player);
        if (level >= MAX_LEVEL) {
            player.displayClientMessage(Component.translatable("message.valor_weapons.thunderstep.max"), true);
            return;
        }
        if (!player.isCreative() && player.experienceLevel < UPGRADE_COST_LEVELS) {
            player.displayClientMessage(Component.translatable("message.valor_weapons.thunderstep.need_xp", UPGRADE_COST_LEVELS), true);
            return;
        }
        if (!player.isCreative()) {
            player.giveExperienceLevels(-UPGRADE_COST_LEVELS);
        }
        setLevel(player, level + 1);
        player.displayClientMessage(Component.translatable("message.valor_weapons.thunderstep.upgraded", level + 1), true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.4F);
        sync(player);
    }

    // ---------------------------------------------------------------- cast

    /**
     * Key press: dash.
     *
     * @param forward movement keys at the time of the press: +1 = W, -1 = S
     * @param strafe  +1 = A (left), -1 = D (right). Both 0 -> dash forward.
     */
    public static void tryCast(ServerPlayer player, float forward, float strafe) {
        if (!holdingFist(player) || player.isSpectator() || player.isPassenger() || !player.isAlive()) {
            return;
        }
        if (DASHES.containsKey(player.getUUID())) {
            return;
        }
        int level = getLevel(player);
        long now = player.level().getGameTime();
        PassiveState p = passive(player);

        boolean isFree = p.hasFreeCast;
        if (!isFree) {
            if (COOLDOWN_END.getOrDefault(player.getUUID(), 0L) > now) {
                return;
            }
            COOLDOWN_END.put(player.getUUID(), now + COOLDOWN_TICKS);
        } else {
            p.hasFreeCast = false;
            p.stacks = 0;
            if (p.savedCooldownRemaining > 0) {
                COOLDOWN_END.put(player.getUUID(), now + p.savedCooldownRemaining);
                p.savedCooldownRemaining = 0;
            } else {
                COOLDOWN_END.put(player.getUUID(), 0L);
            }
        }

        double shield = SHIELD[level - 1] + player.getMaxHealth() * SHIELD_MAX_HEALTH_FRACTION;
        applyShield(player, shield, now);
        sync(player);

        Vec3 dir = dashDirection(player, forward, strafe);
        Dash dash = new Dash(dir, level);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.5F, 1.8F);

        DASHES.put(player.getUUID(), dash); // the first dash tick runs at the end of this server tick
    }

    // ---------------------------------------------------------------- dash

    /** Horizontal unit vector: the movement keys, relative to where the player is facing (no keys = forward). */
    private static Vec3 dashDirection(ServerPlayer player, float forward, float strafe) {
        float f = Mth.clamp(forward, -1.0F, 1.0F);
        float s = Mth.clamp(strafe, -1.0F, 1.0F);
        if (Math.abs(f) < 1.0E-3F && Math.abs(s) < 1.0E-3F) {
            f = 1.0F;
            s = 0.0F;
        }
        double yaw = Math.toRadians(player.getYRot());
        double fx = -Math.sin(yaw); // facing
        double fz = Math.cos(yaw);
        double lx = fz;             // left of facing
        double lz = -fx;
        double x = fx * f + lx * s;
        double z = fz * f + lz * s;
        double length = Math.sqrt(x * x + z * z);
        return new Vec3(x / length, 0.0D, z / length);
    }

    /**
     * One dash tick: push the player along the dash direction and damage enemies touched.
     * The velocity is re-sent every tick (like knockback), so the client simulates the movement itself, which is
     * smooth and respects collisions. Gravity is cancelled while dashing.
     *
     * @return true when the dash is over
     */
    private static boolean step(ServerPlayer player, Dash dash) {
        player.setDeltaMovement(dash.dir.x * DASH_SPEED, 0.0D, dash.dir.z * DASH_SPEED);
        player.hurtMarked = true;
        player.fallDistance = 0.0F;

        damageAlongPath(player, dash, player.serverLevel());
        return --dash.ticksLeft <= 0;
    }

    private static void damageAlongPath(ServerPlayer player, Dash dash, ServerLevel level) {
        AABB area = player.getBoundingBox().inflate(1.0D, 0.3D, 1.0D).expandTowards(dash.dir.scale(DASH_SPEED * 2.0D));
        float damage = (float) (DAMAGE[dash.level - 1] + ValorDamage.getMagicDamage(player) * MAGIC_DAMAGE_SCALE);
        DamageSource source = level.damageSources().indirectMagic(player, player);

        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area,
                e -> Targeting.isValidTarget(player, e) && !dash.hit.contains(e.getId()))) {
            dash.hit.add(target.getId());
            target.invulnerableTime = 0;
            if (target.hurt(source, damage)) {
                level.sendParticles(ModParticles.LIGHTNING_HIT.get(),
                        target.getX(), target.getY(0.5D), target.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
                level.playSound(null, target.getX(), target.getY(), target.getZ(),
                        SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.4F, 1.6F);
            }
        }
    }

    private static void finish(ServerPlayer player) {
        DASHES.remove(player.getUUID());
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
        FistCombat.grantLightningStrike(player); // next attack = Lightning Strike
        player.displayClientMessage(Component.translatable("message.valor_weapons.thunderstep.empowered"), true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.4F, 2.0F);
    }

    // ---------------------------------------------------------------- shield

    private static Attribute shieldAttribute() {
        return ForgeRegistries.ATTRIBUTES.containsKey(SHIELD_ATTRIBUTE_ID)
                ? ForgeRegistries.ATTRIBUTES.getValue(SHIELD_ATTRIBUTE_ID) : null;
    }

    private static void applyShield(ServerPlayer player, double amount, long now) {
        clearShield(player);
        Attribute attribute = shieldAttribute();
        AttributeInstance instance = attribute == null ? null : player.getAttribute(attribute);
        boolean viaAttribute = instance != null;
        if (viaAttribute) {
            instance.addTransientModifier(new AttributeModifier(SHIELD_MODIFIER_UUID, "Thunderstep shield",
                    amount, AttributeModifier.Operation.ADDITION));
        } else {
            player.setAbsorptionAmount(player.getAbsorptionAmount() + (float) amount); // fallback: vanilla hearts
        }
        SHIELDS.put(player.getUUID(), new ShieldState(now + SHIELD_TICKS, amount, viaAttribute));
    }

    private static void clearShield(ServerPlayer player) {
        ShieldState state = SHIELDS.remove(player.getUUID());
        if (state == null) {
            return;
        }
        if (state.viaAttribute) {
            removeShieldModifier(player);
        } else {
            player.setAbsorptionAmount(Math.max(0.0F, player.getAbsorptionAmount() - (float) state.amount));
        }
    }

    private static void removeShieldModifier(ServerPlayer player) {
        Attribute attribute = shieldAttribute();
        if (attribute != null) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance != null) {
                instance.removeModifier(SHIELD_MODIFIER_UUID);
            }
        }
    }

    // ---------------------------------------------------------------- sync + events

    public static void sync(ServerPlayer player) {
        long now = player.level().getGameTime();
        PassiveState p = passive(player);
        int remaining;
        if (p.hasFreeCast) {
            remaining = 0; // ready on client
        } else {
            remaining = (int) Math.max(0L, COOLDOWN_END.getOrDefault(player.getUUID(), 0L) - now);
        }
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new SkillSyncMessage(getLevel(player), remaining, COOLDOWN_TICKS, p.stacks, p.hasFreeCast));
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        UUID id = player.getUUID();

        ShieldState shield = SHIELDS.get(id);
        if (shield != null && player.level().getGameTime() >= shield.expireTick) {
            clearShield(player);
        }

        Dash dash = DASHES.get(id);
        if (dash != null) {
            if (!player.isAlive()) {
                DASHES.remove(id);
            } else if (step(player, dash)) {
                finish(player);
            }
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        CompoundTag old = event.getOriginal().getPersistentData();
        if (old.contains(NBT_LEVEL)) {
            player.getPersistentData().putInt(NBT_LEVEL, old.getInt(NBT_LEVEL));
        }
        // A shield modifier copied over from the old body must not outlive its timer.
        removeShieldModifier(player);
        SHIELDS.remove(player.getUUID());
        DASHES.remove(player.getUUID());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
        }
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            clearShield(player);
            PASSIVES.remove(player.getUUID());
            COOLDOWN_END.remove(player.getUUID());
        }
        DASHES.remove(event.getEntity().getUUID());
    }
}
