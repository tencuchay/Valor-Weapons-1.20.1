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
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;

/**
 * Sun bolt fired by Sun's Bow: flies straight along the look direction, no gravity, disappears after
 * Config.SUN_BOW_RANGE blocks. It hits the first valid enemy on its way (damage = the value the bow computed when
 * firing) and is stopped by blocks.
 */
public class SunBoltProjectile extends Projectile {
    private float damage;
    private double traveled;

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

        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() != HitResult.Type.MISS && !ForgeEventFactory.onProjectileImpact(this, hit)) {
            this.onHit(hit);
            if (this.isRemoved()) {
                return;
            }
        }

        this.setPos(this.getX() + motion.x, this.getY() + motion.y, this.getZ() + motion.z);
        this.traveled += motion.length();
        this.faceMotion();
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
