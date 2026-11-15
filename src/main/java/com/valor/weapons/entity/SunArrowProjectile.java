package com.valor.weapons.entity;

import com.valor.weapons.Config;
import com.valor.weapons.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;

/**
 * Sun arrow: one of the BURST_ARROWS arrows of Sun's Bow's Infinite Arrow burst. It is a VISUAL effect only: it flies
 * straight (no gravity, Config.SUN_BOW_RANGE blocks) and disappears on a block, but it never hits or damages entities.
 * The damage of the burst is dealt along the line of the arrows by SunBowCombat, using {@link #damage(float, LivingEntity)}:
 *
 *   FLAT_DAMAGE + target max health x (BASE_HEALTH_PERCENT + attack / ATTACK_PER_STEP x PERCENT_PER_STEP)
 *
 * where "attack" is the shooter's minecraft:generic.attack_damage.
 */
public class SunArrowProjectile extends Projectile {
    public static final float FLAT_DAMAGE = 10.0F;
    /** 1.5% of the target's max health. */
    public static final double BASE_HEALTH_PERCENT = 0.015D;
    /** Every 25 attack adds 1% of the target's max health. */
    public static final double ATTACK_PER_STEP = 25.0D;
    public static final double PERCENT_PER_STEP = 0.01D;

    /** Every arrow is fired in one of these colors, picked at random (RGB). */
    public static final int[] COLORS = {0xFF8943, 0xFFC844, 0xFFEB84, 0xFFA952};

    private static final EntityDataAccessor<Integer> DATA_COLOR =
            SynchedEntityData.defineId(SunArrowProjectile.class, EntityDataSerializers.INT);

    private double traveled;

    public SunArrowProjectile(EntityType<? extends SunArrowProjectile> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        if (!level.isClientSide) {
            this.setColor(COLORS[this.random.nextInt(COLORS.length)]);
        }
    }

    public SunArrowProjectile(Level level, LivingEntity owner) {
        this(ModEntities.SUN_ARROW.get(), level);
        this.setOwner(owner);
    }

    /** Damage of ONE hit of the burst against a target (the burst hits BURST_HITS times, see SunBowCombat). */
    public static float damage(float attack, LivingEntity target) {
        double percent = BASE_HEALTH_PERCENT + attack / ATTACK_PER_STEP * PERCENT_PER_STEP;
        return (float) (FLAT_DAMAGE + target.getMaxHealth() * percent);
    }

    /** RGB color of this arrow (synced to the client, used by SunArrowRenderer). */
    public int getColor() {
        return this.entityData.get(DATA_COLOR);
    }

    public void setColor(int rgb) {
        this.entityData.set(DATA_COLOR, rgb & 0xFFFFFF);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_COLOR, COLORS[0]);
    }

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

        double range = Config.SUN_BOW_RANGE.get();
        if (this.traveled >= range) {
            this.discard();
            return;
        }
        Vec3 motion = this.getDeltaMovement();
        double remaining = range - this.traveled;
        if (motion.length() > remaining) {
            motion = motion.normalize().scale(remaining);
            this.setDeltaMovement(motion);
        }

        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() == HitResult.Type.BLOCK && !ForgeEventFactory.onProjectileImpact(this, hit)) {
            this.onHit(hit);
            if (this.isRemoved()) {
                return;
            }
        }

        this.setPos(this.getX() + motion.x, this.getY() + motion.y, this.getZ() + motion.z);
        this.traveled += motion.length();
        this.faceMotion();
    }

    /** Visual only: never hits entities. */
    @Override
    protected boolean canHitEntity(Entity entity) {
        return false;
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        this.discard();
    }

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
        tag.putDouble("Traveled", this.traveled);
        tag.putInt("Color", this.getColor());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.traveled = tag.getDouble("Traveled");
        if (tag.contains("Color")) {
            this.setColor(tag.getInt("Color"));
        }
    }
}
