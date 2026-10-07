package com.bossghost.entity;

import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
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
import net.minecraft.world.phys.Vec3;

/**
 * ผีพี่บอสขาโก่ง
 * - กลางคืน: วิ่งเร็ว ล่าผู้เล่น / กลางวัน: เดินปกติ ไม่ไล่ใคร
 * - กระโดดไม่ได้ => ขึ้นที่สูงไม่ได้
 * - โหมด watcher: ยืนนิ่งๆ จ้องผู้เล่นไกลๆ (บนเขา / หลังต้นไม้) แล้วหายไป
 * - โหมด scripted: ถูกควบคุมโดย HorrorEvents (เคาะประตู, จับกด ฯลฯ)
 */
public class BowLegBossGhost extends Monster {
    private static final double NIGHT_SPEED = 0.46D;
    private static final double DAY_SPEED = 0.23D;

    private boolean summoned = false;
    private int dayTicks = 0;

    private boolean watcher = false;
    private int watcherAge = 0;
    private int stare = 0;
    private boolean pendingDiscard = false;
    private Consumer<ServerPlayer> hitHook;

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

    // ---------- โหมดพิเศษ ----------

    /** ผีถูกควบคุมด้วยสคริปต์ (ไม่มี AI ปกติ) */
    public void makeScripted() {
        this.setNoAi(true);
    }

    /** ผียืนนิ่งๆ จ้องผู้เล่นจากที่ไกล */
    public void makeWatcher() {
        this.setNoAi(true);
        this.watcher = true;
    }

    /** กลับไปเป็นผีล่าปกติ */
    public void releaseToHunt() {
        this.setNoAi(false);
        this.noPhysics = false;
        this.setNoGravity(false);
        this.hitHook = null;
        this.watcher = false;
    }

    public void setHitHook(Consumer<ServerPlayer> hook) {
        this.hitHook = hook;
    }

    public void faceToward(Vec3 target) {
        double dx = target.x - this.getX();
        double dz = target.z - this.getZ();
        float yaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        this.setYRot(yaw);
        this.setYHeadRot(yaw);
        this.setYBodyRot(yaw);
    }

    public void moveAndFace(Vec3 pos, Vec3 lookAt) {
        double dx = lookAt.x - pos.x;
        double dz = lookAt.z - pos.z;
        float yaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        this.moveTo(pos.x, pos.y, pos.z, yaw, 0.0F);
        this.setYHeadRot(yaw);
        this.setYBodyRot(yaw);
    }

    public void vanish() {
        if (this.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.SOUL, getX(), getY() + 1.0, getZ(), 25, 0.3, 0.8, 0.3, 0.03);
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1.0, getZ(), 15, 0.3, 0.8, 0.3, 0.02);
            sl.playSound(null, blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0F, 0.6F);
        }
        this.discard();
    }

    /** ผีที่ถูกควบคุมจะไม่รับดาเมจ แต่แจ้งเมื่อผู้เล่นตี */
    @Override
    public boolean hurt(DamageSource src, float amount) {
        if (this.isNoAi() && !src.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            if (!this.level().isClientSide && src.getEntity() instanceof ServerPlayer sp && hitHook != null) {
                hitHook.accept(sp);
            }
            return false;
        }
        return super.hurt(src, amount);
    }

    /** ขาโก่ง ขึ้นที่สูงไม่ได้: ปิดการกระโดดทั้งหมด */
    @Override
    protected void jumpFromGround() {
        // no-op
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide && pendingDiscard) {
            this.discard();
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide) return;

        if (watcher) {
            tickWatcher();
            return;
        }
        if (this.isNoAi()) return; // scripted

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

    private void tickWatcher() {
        Player p = this.level().getNearestPlayer(getX(), getY(), getZ(), 160.0D, false);
        if (p == null || isNightMode()) {
            vanish();
            return;
        }
        watcherAge++;
        faceToward(p.position());

        Vec3 look = p.getViewVector(1.0F).normalize();
        Vec3 to = this.position().add(0, 1.5, 0).subtract(p.getEyePosition()).normalize();
        if (look.dot(to) > 0.985D && p.hasLineOfSight(this)) {
            stare++;
        } else {
            stare = Math.max(0, stare - 1);
        }
        // หายไปเมื่อผู้เล่นเดินเข้าใกล้ / จ้องนานพอ / อยู่นานเกินไป
        if (this.distanceTo(p) < 10.0F || stare > 70 || watcherAge > 1800) {
            vanish();
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
        // ผีที่ถูกสคริปต์ควบคุม (NoAI) ไม่ควรค้างอยู่หลังโหลดเซฟใหม่
        if (this.isNoAi()) pendingDiscard = true;
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
