package com.valor.weapons.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

import javax.annotation.Nullable;

/** Short animated lightning flash (5 frames, 1 tick each), fullbright, billboard, no rotation. */
public class LightningHitParticle extends TextureSheetParticle {
    /** Frames lightning_hit_0..4, one per tick. */
    private static final int LAST_FRAME = 4;
    /** Particle#tick removes the particle once age >= lifetime, so this shows frames 0..4 for one tick each. */
    private static final int LIFETIME_TICKS = LAST_FRAME;
    private static final float SIZE = 0.7F;

    private final SpriteSet sprites;

    protected LightningHitParticle(ClientLevel level, double x, double y, double z,
                                   double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z, xd, yd, zd);
        this.sprites = sprites;
        this.lifetime = LIFETIME_TICKS;
        this.quadSize = SIZE * (0.8F + this.random.nextFloat() * 0.4F);
        this.gravity = 0.0F;
        this.hasPhysics = false;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.updateFrame();
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.removed) {
            this.updateFrame();
        }
    }

    /** Frame index = age (SpriteSet#get(age, max) maps age 0..max onto all frames). */
    private void updateFrame() {
        this.setSprite(this.sprites.get(Math.min(this.age, LAST_FRAME), LAST_FRAME));
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 15728880; // fullbright
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Nullable
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double xd, double yd, double zd) {
            return new LightningHitParticle(level, x, y, z, xd, yd, zd, this.sprites);
        }
    }
}
