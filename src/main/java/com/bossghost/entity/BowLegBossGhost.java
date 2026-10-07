package com.bossghost.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * ผีพี่บอสขาโก่ง
 * - กลางคืน: วิ่งเร็ว ล่าผู้เล่น
 * - กระโดดไม่ได้ => ขึ้นที่สูง (บล็อกเต็ม) ไม่ได้
 * - กลางวัน: เดินปกติ ไม่ไล่ใคร (ถ้าเสกด้วยยันต์จะอยู่ต่อ ถ้าเกิดเองจะหายไปตอนเช้า)
 */
public class BowLegBossGhost extends Monster {
    private static final double NIGHT_SPEED = 0.46D;
    private static final double DAY_SPEED = 0.23D;

    private boolean summoned = false;
    private int dayTicks = 0;

    public BowLegBossGhost(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.MOVEMENT_SPEED, NIGHT_SPEED)
                .add(Attributes.ATTACK_DAMAGE, 6.0D)
                .add(Attributes.FOLLOW_RANGE, 40.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.3D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0D, false));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, false, false,
                e -> this.isNightMode()));
    }

    public boolean isNightMode() {
        return !this.level().isDay();
    }

    public void setSummoned(boolean summoned) {
        this.summoned = summoned;
    }

    /** ขาโก่ง ขึ้นที่สูงไม่ได้: ปิดการกระโดดทั้งหมด */
    @Override
    protected void jumpFromGround() {
        // no-op
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide) return;

        boolean night = isNightMode();
        double wanted = night ? NIGHT_SPEED : DAY_SPEED;
        AttributeInstance speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getBaseValue() != wanted) {
            speed.setBaseValue(wanted);
        }

        if (!night) {
            LivingEntity target = this.getTarget();
            if (target != null && target != this.getLastHurtByMob()) {
                this.setTarget(null);
            }
            // ผีที่เกิดเองตามธรรมชาติ หายไปตอนเช้า
            if (!summoned && !this.isPersistenceRequired()) {
                if (++dayTicks > 200) {
                    if (this.level() instanceof ServerLevel sl) {
                        sl.sendParticles(ParticleTypes.SOUL, getX(), getY() + 1.0, getZ(), 20, 0.3, 0.6, 0.3, 0.02);
                    }
                    this.discard();
                }
            }
        } else {
            dayTicks = 0;
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Summoned", summoned);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        summoned = tag.getBoolean("Summoned");
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SOUL_ESCAPE;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource src) {
        return SoundEvents.PHANTOM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PHANTOM_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.ZOMBIE_STEP, 0.15F, 1.0F);
    }
}
