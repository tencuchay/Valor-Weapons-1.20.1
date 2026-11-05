package com.valor.weapons.entity;

import com.valor.weapons.registry.ModEntities;
import com.valor.weapons.registry.ModParticles;
import com.valor.weapons.util.Targeting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * One lightning strike of the Thunder Lord's Fist skill.
 *
 * It waits `delay` ticks (so the three strikes of the skill go off one after another), then deals magic damage to every
 * valid target inside a 4x4 square (oriented along the cast direction) and heals the owner. The 9-frame animation
 * (textures/entity/thunder_strike_0..8.png) is drawn by LightningStrikeRenderer, one frame per tick.
 */
public class LightningStrikeEntity extends Entity {
    private static final EntityDataAccessor<Integer> DATA_DELAY =
            SynchedEntityData.defineId(LightningStrikeEntity.class, EntityDataSerializers.INT);

    /** Side length of the damage square, in blocks. */
    public static final double AREA_SIZE = 4.0D;
    /** Frames in the animation. */
    public static final int ANIMATION_TICKS = 9;
    /** Visual height of the bolt in blocks. */
    public static final float VISUAL_HEIGHT = 14.0F;

    /** Shared by the strikes of one volley, so the heal happens only once per volley (server side only). */
    public static final class HealToken {
        private boolean used;

        boolean take() {
            if (this.used) {
                return false;
            }
            this.used = true;
            return true;
        }
    }

    @Nullable
    private HealToken healToken;
    @Nullable
    private UUID ownerId;
    private float damage;
    private float heal;
    private double dirX;
    private double dirZ;
    private boolean struck;

    public LightningStrikeEntity(EntityType<? extends LightningStrikeEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public LightningStrikeEntity(Level level, LivingEntity owner, double x, double y, double z,
                                 double dirX, double dirZ, float damage, float heal, int delay,
                                 @Nullable HealToken healToken) {
        this(ModEntities.LIGHTNING_STRIKE.get(), level);
        this.ownerId = owner.getUUID();
        this.setPos(x, y, z);
        this.dirX = dirX;
        this.dirZ = dirZ;
        this.damage = damage;
        this.heal = heal;
        this.healToken = healToken;
        this.entityData.set(DATA_DELAY, delay);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_DELAY, 0);
    }

    public int getDelay() {
        return this.entityData.get(DATA_DELAY);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return; // animation is purely visual, see LightningStrikeRenderer
        }
        int delay = this.getDelay();
        if (!this.struck && this.tickCount > delay) {
            this.struck = true;
            this.strike();
        }
        if (this.tickCount > delay + ANIMATION_TICKS) {
            this.discard();
        }
    }

    private void strike() {
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        Entity owner = this.ownerId == null ? null : level.getEntity(this.ownerId);
        DamageSource source = level.damageSources().indirectMagic(this, owner);

        double half = AREA_SIZE / 2.0D;
        AABB search = new AABB(this.getX() - 3.5D, this.getY() - 2.0D, this.getZ() - 3.5D,
                this.getX() + 3.5D, this.getY() + 5.0D, this.getZ() + 3.5D);

        int hits = 0;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, search, e -> Targeting.isValidTarget(owner, e))) {
            double dx = target.getX() - this.getX();
            double dz = target.getZ() - this.getZ();
            double along = dx * this.dirX + dz * this.dirZ;
            double side = -dx * this.dirZ + dz * this.dirX;
            double margin = target.getBbWidth() / 2.0D;
            if (Math.abs(along) > half + margin || Math.abs(side) > half + margin) {
                continue;
            }
            if (target.getBoundingBox().maxY < this.getY() - 1.0D || target.getBoundingBox().minY > this.getY() + 4.0D) {
                continue;
            }
            target.invulnerableTime = 0; // every strike of the skill must connect
            if (target.hurt(source, this.damage)) {
                hits++;
                level.sendParticles(ModParticles.LIGHTNING_HIT.get(),
                        target.getX(), target.getY(0.5D), target.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }

        if (hits > 0 && this.heal > 0.0F && owner instanceof LivingEntity livingOwner && livingOwner.isAlive()
                && (this.healToken == null || this.healToken.take())) {
            livingOwner.heal(this.heal);
        }

        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.6F, 1.5F);
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.6F, 1.2F);
    }

    /**
     * By default an entity is only rendered within ~64 x its hitbox size of the camera. This entity's hitbox is tiny
     * (0.1), which meant strikes farther than ~6 blocks were never drawn. The bolt is tall, so show it from far away.
     */
    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSqr) {
        return distanceSqr < 128.0D * 128.0D;
    }

    /** The bolt is tall, so the culling box must be too (otherwise it disappears when its base is off screen). */
    @Override
    public AABB getBoundingBoxForCulling() {
        return new AABB(this.getX() - 2.0D, this.getY(), this.getZ() - 2.0D,
                this.getX() + 2.0D, this.getY() + VISUAL_HEIGHT, this.getZ() + 2.0D);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
