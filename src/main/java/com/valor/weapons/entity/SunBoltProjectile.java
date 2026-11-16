package com.valor.weapons.entity;

import com.valor.weapons.Config;
import com.valor.weapons.registry.ModEntities;
import com.valor.weapons.util.Targeting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;

/**
 * Sun bolt fired by Sun's Bow: flies straight along the look direction, no gravity, disappears after
 * Config.SUN_BOW_RANGE blocks. It hits the first valid enemy on its way (damage = the value the bow computed when
 * firing) and is stopped by blocks.
 *
 * Hit detection of the FIRST step starts at the shooter's eye (setHitOrigin), not at the muzzle point 0.8 blocks in
 * front of it: a ray that starts inside a hitbox never hits it, and one that starts behind a wall flies through it, so
 * targets (and walls) right in front of the shooter used to be skipped.
 */
public class SunBoltProjectile extends Projectile {
    /** The bolt is this much bigger than its path for hit detection (same margin vanilla projectiles use). */
    private static final float HIT_MARGIN = 0.3F;

    private float damage;
    private double traveled;
    /** Where the first step of the flight starts from for hit detection (the shooter's eye); null once used. */
    private Vec3 hitOrigin;

    public SunBoltProjectile(EntityType<? extends SunBoltProjectile> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    public SunBoltProjectile(Level level, LivingEntity owner, float damage) {
        this(ModEntities.SUN_BOLT.get(), level);
        this.setOwner(owner);
        this.damage = damage;
    }

    @Override
    protected void defineSynchedData() {
    }

    /** The first step of the flight is checked for hits starting from this point (normally the shooter's eye). */
    public void setHitOrigin(Vec3 origin) {
        this.hitOrigin = origin;
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
            // Drawn by SunBoltRenderer; position/velocity come from the server.
            Vec3 m = this.getDeltaMovement();
            this.setPos(this.getX() + m.x, this.getY() + m.y, this.getZ() + m.z);
            this.faceMotion();
            return;
        }

        double range = Config.SUN_BOW_RANGE.get();
        if (this.traveled >= range) {
            this.discard();
            return;
        }
        // Do not fly past the end of the range with the last step.
        Vec3 motion = this.getDeltaMovement();
        double remaining = range - this.traveled;
        if (motion.length() > remaining) {
            motion = motion.normalize().scale(remaining);
            this.setDeltaMovement(motion);
        }

        Vec3 from = this.position();
        Vec3 to = from.add(motion);
        if (this.hitOrigin != null) {
            from = this.hitOrigin; // first step: from the eye, so nothing between the eye and the muzzle is skipped
            this.hitOrigin = null;
        }
        HitResult hit = this.findHit(from, to);
        if (hit != null && !ForgeEventFactory.onProjectileImpact(this, hit)) {
            this.onHit(hit);
            if (this.isRemoved()) {
                return;
            }
        }

        this.setPos(this.getX() + motion.x, this.getY() + motion.y, this.getZ() + motion.z);
        this.traveled += motion.length();
        this.faceMotion();
    }

    /** The first thing on the way from `from` to `to`: a valid target or a block; null when the way is clear. */
    private HitResult findHit(Vec3 from, Vec3 to) {
        BlockHitResult block = this.level().clip(new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 reach = block.getType() == HitResult.Type.MISS ? to : block.getLocation();

        EntityHitResult entity = this.findEntity(from, reach);
        if (entity != null) {
            return entity; // never farther than the block: the search stops at it
        }
        return block.getType() == HitResult.Type.MISS ? null : block;
    }

    /**
     * The closest valid target on the way. A target whose hitbox already contains `from` (right in front of the
     * shooter) counts as hit right there; a plain ray would pass through it.
     */
    private EntityHitResult findEntity(Vec3 from, Vec3 to) {
        Entity best = null;
        Vec3 bestPos = null;
        double bestDistance = Double.MAX_VALUE;
        AABB sweep = new AABB(from, to).inflate(1.0D);
        for (Entity candidate : this.level().getEntities(this, sweep, this::canHitEntity)) {
            AABB box = candidate.getBoundingBox().inflate(HIT_MARGIN);
            Vec3 pos = box.contains(from) ? from : box.clip(from, to).orElse(null);
            if (pos == null) {
                continue;
            }
            double distance = from.distanceToSqr(pos);
            if (distance < bestDistance) {
                best = candidate;
                bestPos = pos;
                bestDistance = distance;
            }
        }
        return best == null ? null : new EntityHitResult(best, bestPos);
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity)
                && entity instanceof LivingEntity living
                && Targeting.isValidTarget(this.getOwner(), living);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        Entity victim = result.getEntity();
        DamageSource source = this.level().damageSources().thrown(this, this.getOwner());
        // Like the other skills of the mod: the hit-immunity frames of an earlier hit (e.g. the arrow burst) must not
        // swallow or shrink this bolt, which would hit close targets (right after the other hit) but not far ones.
        victim.invulnerableTime = 0;
        victim.hurt(source, this.damage);
        this.impactSound(result.getLocation());
        this.discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        this.impactSound(result.getLocation());
        this.discard();
    }

    private void impactSound(Vec3 pos) {
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, pos.x, pos.y, pos.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.4F, 1.4F);
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
        tag.putFloat("Damage", this.damage);
        tag.putDouble("Traveled", this.traveled);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.damage = tag.getFloat("Damage");
        this.traveled = tag.getDouble("Traveled");
    }
}
