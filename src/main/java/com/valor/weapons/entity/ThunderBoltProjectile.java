package com.valor.weapons.entity;

import com.valor.weapons.Config;
import com.valor.weapons.registry.ModEntities;
import com.valor.weapons.registry.ModParticles;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Thunder bolt fired by Thunder Lord's Fist.
 *
 * - Flies straight along the player's look direction, no gravity, passes through blocks.
 * - Near the end of its range (Config.HOMING_START_FRACTION of Config.PROJECTILE_RANGE) it picks the NEAREST valid
 *   target around itself and locks on; from then on it chases that target (even beyond the range).
 * - No target there: it disappears when the range is used up.
 * - Only the locked target is hit. Deals magic damage (bypasses armor).
 */
public class ThunderBoltProjectile extends Projectile {
    @Nullable
    private UUID targetId;
    private float damage;
    private int age;
    private double traveled;

    public ThunderBoltProjectile(EntityType<? extends ThunderBoltProjectile> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.noPhysics = true; // never collides with blocks
    }

    public ThunderBoltProjectile(Level level, LivingEntity owner, float damage) {
        this(ModEntities.THUNDER_BOLT.get(), level);
        this.setOwner(owner);
        this.damage = damage;
    }

    @Override
    protected void defineSynchedData() {
    }

    /** Set the initial velocity and face the bolt along it. */
    public void launch(Vec3 velocity) {
        this.setDeltaMovement(velocity);
        this.faceMotion();
        this.xRotO = this.getXRot();
        this.yRotO = this.getYRot();
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide) {
            // The bolt is drawn by ThunderBoltRenderer; position/velocity come from the server.
            Vec3 m = this.getDeltaMovement();
            this.setPos(this.getX() + m.x, this.getY() + m.y, this.getZ() + m.z);
            this.faceMotion();
            return;
        }

        if (++this.age > Config.PROJECTILE_MAX_AGE.get()) {
            this.fizzle();
            return;
        }

        double range = Config.PROJECTILE_RANGE.get();
        LivingEntity target = this.currentTarget();
        if (target == null && this.traveled >= range * Config.HOMING_START_FRACTION.get()) {
            target = this.acquireTarget(); // only near the end of the flight
            this.targetId = target == null ? null : target.getUUID();
        }
        if (target == null && this.traveled >= range) {
            this.fizzle();
            return;
        }
        if (target != null) {
            this.steerTowards(target);
        }

        Vec3 start = this.position();
        Vec3 motion = this.getDeltaMovement();
        Vec3 end = start.add(motion);

        EntityHitResult hit = ProjectileUtil.getEntityHitResult(this.level(), this, start, end,
                this.getBoundingBox().expandTowards(motion).inflate(1.0D), this::canHitEntity);
        if (hit != null && !ForgeEventFactory.onProjectileImpact(this, hit)) {
            this.onHit(hit);
            if (this.isRemoved()) {
                return;
            }
        }

        this.setPos(end.x, end.y, end.z);
        this.traveled += motion.length();
        this.faceMotion();
    }

    /** The locked target if it is still valid, otherwise null (and the lock is dropped). */
    @Nullable
    private LivingEntity currentTarget() {
        if (this.targetId != null && this.level() instanceof ServerLevel serverLevel) {
            Entity entity = serverLevel.getEntity(this.targetId);
            if (entity instanceof LivingEntity living && this.isValidTarget(living)) {
                return living;
            }
        }
        this.targetId = null;
        return null;
    }

    /** The valid target nearest to the bolt, within Config.HOMING_RADIUS. */
    @Nullable
    private LivingEntity acquireTarget() {
        Vec3 pos = this.position();
        double radius = Config.HOMING_RADIUS.get();
        AABB area = new AABB(pos, pos).inflate(radius);

        LivingEntity best = null;
        double bestDistSqr = radius * radius;
        for (LivingEntity candidate : this.level().getEntitiesOfClass(LivingEntity.class, area, this::isValidTarget)) {
            double distSqr = candidate.getBoundingBox().getCenter().distanceToSqr(pos);
            if (distSqr <= bestDistSqr) {
                bestDistSqr = distSqr;
                best = candidate;
            }
        }
        return best;
    }

    /** Edit this to change who the bolt is allowed to chase. */
    private boolean isValidTarget(LivingEntity entity) {
        Entity owner = this.getOwner();
        if (!entity.isAlive() || entity.isSpectator() || !entity.isAttackable() || entity == owner) {
            return false;
        }
        if (entity instanceof ArmorStand) {
            return false;
        }
        if (owner instanceof Player attacker && entity instanceof Player other && !attacker.canHarmPlayer(other)) {
            return false;
        }
        return !(owner != null && entity instanceof OwnableEntity pet && owner.getUUID().equals(pet.getOwnerUUID()));
    }

    private void steerTowards(LivingEntity target) {
        double speed = Config.PROJECTILE_SPEED.get();
        Vec3 desired = target.getBoundingBox().getCenter().subtract(this.position()).normalize().scale(speed);
        // Blend old heading with the desired one: strong homing but still a visible curve.
        Vec3 blended = this.getDeltaMovement().scale(0.5D).add(desired.scale(0.5D));
        if (blended.lengthSqr() < 1.0E-6D) {
            blended = desired;
        }
        this.setDeltaMovement(blended.normalize().scale(speed));
    }

    /** Only the locked target can be hit; everything else (and every block) is ignored. */
    @Override
    protected boolean canHitEntity(Entity entity) {
        return this.targetId != null && this.targetId.equals(entity.getUUID()) && entity.isAlive();
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        Entity victim = result.getEntity();
        DamageSource source = this.level().damageSources().indirectMagic(this, this.getOwner());
        victim.hurt(source, this.damage);
        this.hitEffects(victim);
        this.impactSound(result.getLocation());
        this.discard();
    }

    /** Lightning flash particles on the enemy that got hit. */
    private void hitEffects(Entity victim) {
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ModParticles.LIGHTNING_HIT.get(),
                    victim.getX(), victim.getY(0.5D), victim.getZ(),
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    private void fizzle() {
        this.impactSound(this.position());
        this.discard();
    }

    private void impactSound(Vec3 pos) {
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, pos.x, pos.y, pos.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.4F, 1.6F);
        }
    }

    /** Entity convention: yaw 0 = +Z, positive pitch = looking down. */
    private void faceMotion() {
        Vec3 m = this.getDeltaMovement();
        if (m.lengthSqr() < 1.0E-7D) {
            return;
        }
        this.setYRot((float) (Mth.atan2(m.x, m.z) * (180.0D / Math.PI)));
        this.setXRot((float) (-Mth.atan2(m.y, m.horizontalDistance()) * (180.0D / Math.PI)));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.targetId != null) {
            tag.putUUID("Target", this.targetId);
        }
        tag.putFloat("Damage", this.damage);
        tag.putInt("Age", this.age);
        tag.putDouble("Traveled", this.traveled);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Target")) {
            this.targetId = tag.getUUID("Target");
        }
        this.damage = tag.getFloat("Damage");
        this.age = tag.getInt("Age");
        this.traveled = tag.getDouble("Traveled");
    }
}
