package com.valor.weapons.combat;

import com.valor.weapons.Config;
import com.valor.weapons.ValorWeapons;
import com.valor.weapons.entity.SunArrowProjectile;
import com.valor.weapons.entity.SunBoltProjectile;
import com.valor.weapons.item.SunBowItem;
import com.valor.weapons.network.ArrowStackSyncMessage;
import com.valor.weapons.network.ModNetwork;
import com.valor.weapons.network.SunBowLockMessage;
import com.valor.weapons.util.SunBowDamage;
import com.valor.weapons.util.Targeting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side swing handling for Sun's Bow. Same rhythm as Thunder Lord's Fist:
 *  - the client reports "attack key held with the bow" every tick (FistSwingMessage);
 *  - a swing can start once the attack cooldown (20 / attack speed ticks) has passed;
 *  - the shot is released after the wind-up (Config.SUN_BOW_SWING_DELAY_FRACTION of the cooldown),
 *    unless a Better Combat hit already released it for that swing (SunBowItem#onLeftClickEntity).
 *
 * Infinite Arrow stacks: every normal shot (target or not) gives 1 stack, up to STACKS_NEEDED. The shot AFTER that
 * consumes all the stacks and, instead of one sun bolt, starts the arrow burst: BURST_ARROWS sun arrows (visual only,
 * scattered off-centre along the line of fire) are released one every ARROW_INTERVAL_TICKS, and independently of them
 * the line is damaged exactly BURST_HITS times, spread over BURST_DURATION_TICKS. A higher attack speed does not add
 * arrows or hits: it makes the whole burst (hits, arrows and its end) play faster, relative to the bow's own attack
 * speed (SunBowItem.TOTAL_ATTACK_SPEED).
 * While the burst is firing the player cannot make normal attacks, the camera is frozen (SunBowCameraLock on the
 * client) and the player runs 70% slower; the line of fire is the look direction at the moment the burst started.
 */
@Mod.EventBusSubscriber(modid = ValorWeapons.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SunBowCombat {
    /** Normal shots needed to fill the stacks; the next shot is the arrow burst. */
    public static final int STACKS_NEEDED = 4;
    /** Sun arrows of one burst. They are only a visual effect and deal no damage; always this many. */
    public static final int BURST_ARROWS = 24;
    /** Times the burst damages every target on its line (one hit per step). Always exactly this many. */
    public static final int BURST_HITS = 7;
    /** The whole burst lasts this many ticks at the bow's base attack speed (shorter with a higher attack speed). */
    private static final int BURST_DURATION_TICKS = 28;
    /** Ticks between two visual arrows at the base attack speed: the stream is even (shorter with a higher attack speed). */
    private static final int ARROW_INTERVAL_TICKS = 1;
    /** Movement speed change while the burst is firing: -0.7 = 70% slower. */
    private static final double BURST_SPEED_MODIFIER = -0.7D;
    private static final UUID BURST_SLOW_UUID = UUID.fromString("5b3f1c9e-7a24-4d8b-9c61-2e0a8f4d7b13");
    /** Visual arrows start up to this far (blocks) from the muzzle, so they all leave from nearly one point... */
    private static final double START_SCATTER_RADIUS = 0.2D;
    /** ...and then fan out slightly: each arrow's direction is off the line of fire by up to this many degrees. */
    private static final double SPREAD_DEGREES = 4.0D;
    /** Targets within this distance (blocks) of the line are hit by the burst. */
    private static final double LINE_HIT_RADIUS = 1.0D;

    private static final Map<UUID, State> STATES = new HashMap<>();

    private static final class State {
        long pendingTick = -1;
        int pendingWait = 0;
        long lastAttackTick = -1000; // start tick of the previous swing
        long lastHitTick = -1000;
        long lastBlockClickTick = -1000;
        int stacks = 0;
        int burstRemaining = 0;
        long burstStartTick = 0;
        int burstTotalHits = BURST_HITS;
        /** Ticks the damage hits are spread over, and ticks between two arrows, for the running burst. */
        double burstDuration = BURST_DURATION_TICKS;
        double arrowInterval = ARROW_INTERVAL_TICKS;
        int arrowsRemaining = 0;
        /** Look direction when the burst started: the burst fires along it for its whole duration. */
        Vec3 lockedLook = Vec3.ZERO;
    }

    private SunBowCombat() {}

    private static State state(ServerPlayer player) {
        return STATES.computeIfAbsent(player.getUUID(), id -> new State());
    }

    /** True while the Infinite Arrow burst is firing (normal attacks are blocked until it is over). */
    private static boolean bursting(State s) {
        return s.burstRemaining > 0 || s.arrowsRemaining > 0;
    }

    private static double cooldownTicks(ServerPlayer player) {
        return 20.0D / Math.max(0.1D, player.getAttributeValue(Attributes.ATTACK_SPEED));
    }

    private static int swingWaitTicks(ServerPlayer player) {
        int upswing = (int) Math.round(cooldownTicks(player) * Config.SUN_BOW_SWING_DELAY_FRACTION.get());
        return Mth.clamp(upswing + 1, 1, 20);
    }

    /** Called every tick the client reports the attack key held with the bow. */
    public static void requestShot(ServerPlayer player) {
        if (!(player.getMainHandItem().getItem() instanceof SunBowItem) || player.isSpectator()) {
            return;
        }
        State s = state(player);
        if (bursting(s)) {
            return; // no normal attacks while the arrow burst is firing
        }
        long now = player.level().getGameTime();
        if (s.pendingTick >= 0 || now == s.lastBlockClickTick) {
            return;
        }
        if (now - s.lastAttackTick < cooldownTicks(player) - 1.0D) {
            return; // one tick of slack so rounding never skips a swing Better Combat performs
        }
        s.pendingTick = now;
        s.pendingWait = swingWaitTicks(player);
    }

    /** Called by the item for every target Better Combat sweeps; the swing is only handled once. */
    public static void onSwingHit(ServerPlayer player) {
        State s = state(player);
        if (bursting(s)) {
            return; // no normal attacks while the arrow burst is firing
        }
        long now = player.level().getGameTime();
        if (s.lastHitTick == now) {
            return; // another target of the same swing
        }
        s.lastHitTick = now;
        s.lastAttackTick = s.pendingTick >= 0 ? s.pendingTick : now - (swingWaitTicks(player) - 1);
        s.pendingTick = -1;
        performShot(player);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        State s = STATES.get(player.getUUID());
        if (s == null) {
            return;
        }
        long now = player.level().getGameTime();

        // Arrow burst in progress.
        if (bursting(s)) {
            if (!player.isAlive() || !(player.getMainHandItem().getItem() instanceof SunBowItem)) {
                endBurst(player, s); // put the bow away or died: the rest of the burst is lost
            } else {
                runBurst(player, s, now);
            }
        }

        if (s.pendingTick < 0) {
            return;
        }
        if (now - s.pendingTick < s.pendingWait) {
            return;
        }

        long swingTick = s.pendingTick;
        s.pendingTick = -1;
        if (s.lastHitTick >= swingTick - 1) {
            return; // Better Combat already released the shot for this swing
        }
        if (!(player.getMainHandItem().getItem() instanceof SunBowItem)) {
            return;
        }
        s.lastAttackTick = swingTick;
        performShot(player);
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            State s = state(player);
            s.pendingTick = -1;
            s.lastBlockClickTick = player.level().getGameTime();
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            removeSlow(player);
        }
        STATES.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncStacks(player, 0);
        }
    }

    /** A burst slow copied over from the old body must not outlive the burst. */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            removeSlow(player);
        }
    }

    /** Dying clears the stacks and any burst in progress. */
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            State s = state(player);
            s.stacks = 0;
            endBurst(player, s);
            syncStacks(player, 0);
        }
    }

    private static void syncStacks(ServerPlayer player, int stacks) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ArrowStackSyncMessage(stacks));
    }

    /** One normal shot has been released: either a sun bolt (+1 stack) or, with full stacks, the arrow burst. */
    private static void performShot(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof SunBowItem)) {
            return;
        }
        State s = state(player);
        long now = player.level().getGameTime();

        if (s.stacks >= STACKS_NEEDED) {
            s.stacks = 0;
            syncStacks(player, 0);
            // Always exactly BURST_HITS hits and BURST_ARROWS visual arrows; the attack speed (relative to the bow's
            // own base attack speed) only makes the burst play faster. Never slower than half speed.
            double speed = Math.max(0.5D, player.getAttributeValue(Attributes.ATTACK_SPEED) / SunBowItem.TOTAL_ATTACK_SPEED);
            s.burstTotalHits = BURST_HITS;
            s.burstDuration = BURST_DURATION_TICKS / speed;
            s.arrowInterval = ARROW_INTERVAL_TICKS / speed;
            s.burstRemaining = s.burstTotalHits;
            s.arrowsRemaining = BURST_ARROWS;
            s.burstStartTick = now;
            s.lockedLook = player.getLookAngle();
            s.pendingTick = -1;
            applySlow(player);
            sendLock(player, true);
            runBurst(player, s, now); // the first hit and the first arrow happen right away, the rest follow
            stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(EquipmentSlot.MAINHAND));
            return;
        }

        s.stacks = Math.min(STACKS_NEEDED, s.stacks + 1); // every normal shot counts, with or without a target
        syncStacks(player, s.stacks);

        float damage = SunBowDamage.projectileDamage(player);
        if (damage <= 0.0F) {
            return;
        }
        ServerLevel level = player.serverLevel();
        SunBoltProjectile bolt = new SunBoltProjectile(level, player, damage);
        Vec3 look = player.getLookAngle();
        Vec3 start = muzzle(player, look);
        bolt.setPos(start.x, start.y, start.z);
        bolt.launch(look.scale(Config.SUN_BOW_PROJECTILE_SPEED.get()));
        level.addFreshEntity(bolt);

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.5F, 1.5F);
        stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(EquipmentSlot.MAINHAND));
    }

    /**
     * Runs everything of the burst that is due at game tick `now`: the damage hits and the visual arrows. They follow
     * separate schedules: the hits are spread over burstDuration ticks, the arrows come one every arrowInterval ticks
     * (several arrows can be due in the same tick at a high attack speed).
     * Both fire along the look direction locked when the burst started; when everything is done the burst ends.
     */
    private static void runBurst(ServerPlayer player, State s, long now) {
        // Several hits can be due in one tick when the burst has many hits.
        while (s.burstRemaining > 0 && now >= burstStepTick(s, s.burstTotalHits - s.burstRemaining)) {
            hitStep(player, s);
        }
        while (s.arrowsRemaining > 0
                && now >= s.burstStartTick + (long) Math.floor((BURST_ARROWS - s.arrowsRemaining) * s.arrowInterval)) {
            s.arrowsRemaining--;
            Vec3 look = s.lockedLook;
            spawnVisualArrow(player, player.serverLevel(), muzzle(player, look), look);
        }
        if (!bursting(s)) {
            endBurst(player, s);
        }
    }

    /** Ends the burst (finished, or cancelled): normal attacks, camera and running speed go back to normal. */
    private static void endBurst(ServerPlayer player, State s) {
        s.burstRemaining = 0;
        s.arrowsRemaining = 0;
        removeSlow(player);
        sendLock(player, false);
    }

    private static void applySlow(ServerPlayer player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(BURST_SLOW_UUID) == null) {
            speed.addTransientModifier(new AttributeModifier(BURST_SLOW_UUID, "Sun arrow burst slow",
                    BURST_SPEED_MODIFIER, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    private static void removeSlow(ServerPlayer player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(BURST_SLOW_UUID);
        }
    }

    /** Tells the client to freeze (or release) the camera, at the player's current view direction. */
    private static void sendLock(ServerPlayer player, boolean locked) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new SunBowLockMessage(locked, player.getYRot(), player.getXRot()));
    }

    /** One damage hit of the burst: damages every valid target on the line of fire once. */
    private static void hitStep(ServerPlayer player, State s) {
        s.burstRemaining--;
        ServerLevel level = player.serverLevel();
        damageAlongLine(player, level, s.lockedLook);

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.4F, 1.9F);
    }

    /** Game tick at which damage hit number `step` (0-based) of the running burst is due. */
    private static long burstStepTick(State s, int step) {
        return s.burstStartTick + (long) Math.floor(step * s.burstDuration / s.burstTotalHits);
    }

    /**
     * A visual-only arrow: leaves from (almost) the muzzle point and fans out slightly along the line of fire, so
     * the arrows are bunched near the shooter and drift apart with distance.
     */
    private static void spawnVisualArrow(ServerPlayer player, ServerLevel level, Vec3 start, Vec3 look) {
        RandomSource random = player.getRandom();
        Vec3 side = look.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (side.lengthSqr() < 1.0E-4D) {
            side = new Vec3(1.0D, 0.0D, 0.0D); // looking straight up or down
        }
        side = side.normalize();
        Vec3 up = side.cross(look).normalize();

        Vec3 pos = start.add(randomPerpendicular(random, side, up).scale(START_SCATTER_RADIUS * Math.sqrt(random.nextDouble())));
        Vec3 dir = look.add(randomPerpendicular(random, side, up)
                .scale(Math.tan(Math.toRadians(SPREAD_DEGREES)) * Math.sqrt(random.nextDouble()))).normalize();
        double speed = Config.SUN_BOW_PROJECTILE_SPEED.get() * (0.85D + random.nextDouble() * 0.3D);

        SunArrowProjectile arrow = new SunArrowProjectile(level, player);
        arrow.setPos(pos.x, pos.y, pos.z);
        arrow.launch(dir.scale(speed));
        level.addFreshEntity(arrow);
    }

    /** A random unit vector perpendicular to the line of fire. */
    private static Vec3 randomPerpendicular(RandomSource random, Vec3 side, Vec3 up) {
        double angle = random.nextDouble() * Math.PI * 2.0D;
        return side.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
    }

    /** Damages every valid target within LINE_HIT_RADIUS of the line of fire (Config.SUN_BOW_RANGE, stopped by blocks) once. */
    private static void damageAlongLine(ServerPlayer player, ServerLevel level, Vec3 look) {
        float attack = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        Vec3 origin = player.getEyePosition();
        Vec3 end = origin.add(look.scale(Config.SUN_BOW_RANGE.get()));
        BlockHitResult block = level.clip(new ClipContext(origin, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (block.getType() != HitResult.Type.MISS) {
            end = block.getLocation();
        }

        DamageSource source = SunBowDamage.sunArrowSource(level, player);
        AABB area = new AABB(origin, end).inflate(LINE_HIT_RADIUS);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area,
                e -> Targeting.isValidTarget(player, e))) {
            AABB box = target.getBoundingBox().inflate(LINE_HIT_RADIUS);
            if (!box.contains(origin) && box.clip(origin, end).isEmpty()) {
                continue; // too far from the line
            }
            target.invulnerableTime = 0; // every step of the burst must connect
            target.hurt(source, SunArrowProjectile.damage(attack, target));
        }
    }

    private static Vec3 muzzle(ServerPlayer player, Vec3 look) {
        return player.getEyePosition().add(look.scale(0.8D)).subtract(0.0D, 0.25D, 0.0D);
    }
}
