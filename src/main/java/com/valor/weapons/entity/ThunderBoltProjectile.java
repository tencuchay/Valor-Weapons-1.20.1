package com.valor.weapons.entity;

import com.valor.weapons.Config;
import com.valor.weapons.combat.ThunderstepSkill;
import com.valor.weapons.registry.ModEntities;
import com.valor.weapons.registry.ModParticles;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;

/**
 * Thunder bolt fired by Thunder Lord's Fist.
 *
 * - Flies straight along the player's look direction, no gravity.
 * - Hits the first valid enemy or solid block along its path.
 * - Deals magic damage (bypasses armor).
 */
public class ThunderBoltProjectile extends Projectile {
    private float damage;
    private int age;
    private double traveled;

    public ThunderBoltProjectile(EntityType<? extends ThunderBoltProjectile> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
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
        if (this.traveled >= range) {
            this.fizzle();
            return;
        }

        Vec3 start = this.position();
        Vec3 motion = this.getDeltaMovement();
        Vec3 end = start.add(motion);

        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() != HitResult.Type.MISS && !ForgeEventFactory.onProjectileImpact(this, hit)) {
            this.onHit(hit);
            if (this.isRemoved()) {
                return;
            }
        }

        this.setPos(end.x, end.y, end.z);
        this.traveled += motion.length();
        this.faceMotion();
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        if (!super.canHitEntity(entity)) {
            return false;
        }
        return entity instanceof LivingEntity living && this.isValidTarget(living);
    }

    /** Edit this to change who the bolt is allowed to hit. */
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

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        Entity victim = result.getEntity();
        DamageSource source = this.level().damageSources().indirectMagic(this, this.getOwner());
        victim.hurt(source, this.damage);
        if (this.getOwner() instanceof ServerPlayer serverPlayer) {
            ThunderstepSkill.addStack(serverPlayer);
        }
        this.hitEffects(victim);
        this.impactSound(result.getLocation());
        this.discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
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
        tag.putFloat("Damage", this.damage);
        tag.putInt("Age", this.age);
        tag.putDouble("Traveled", this.traveled);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.damage = tag.getFloat("Damage");
        this.age = tag.getInt("Age");
        this.traveled = tag.getDouble("Traveled");
    }
}
