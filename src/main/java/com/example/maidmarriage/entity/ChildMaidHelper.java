package com.example.maidmarriage.entity;

import com.example.maidmarriage.config.ModConfigs;
import com.example.maidmarriage.data.ChildStateData;
import com.example.maidmarriage.data.ModTaskData;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * 子代女仆辅助类：替代原 MaidChildEntity，以纯静态工具方法形式
 * 管理子代女仆的常量、状态读写与成长阈值计算。
 */
public final class ChildMaidHelper {

    public static final int DAY_TICKS = 24000;
    private static final double HEALTH_MULTIPLIER = 1.3D;

    /** Entity tag set on all born maids (persists after growing up). */
    public static final String BORN_MAID_TAG = "maidmarriage_born_maid";

    /**
     * Entity tag present only while the maid is still in child state.
     * Written/refreshed every tick so it survives world saves.
     */
    public static final String CHILD_ACTIVE_ENTITY_TAG = "maidmarriage_child_entity";

    /** Persistent-data key: whether tame() has been called after birth. */
    public static final String PERSISTENT_TAME_INITIALIZED_KEY = "maidmarriage_child_tame_initialized";

    // Legacy persistent-data keys kept for backward-compat film detection only.
    public static final String PERSISTENT_CHILD_ACTIVE_KEY = "maidmarriage_child_active";

    private ChildMaidHelper() {
    }

    // -------------------------------------------------------------------------
    // State queries
    // -------------------------------------------------------------------------

    private static boolean isChildStateRegistered() {
        return ModTaskData.CHILD_STATE != null;
    }

    /**
     * Returns {@code true} when {@code maid} is currently in child state
     * according to the synced task data.  Works on both logical sides.
     */
    public static boolean shouldStayChild(EntityMaid maid) {
        if (!isChildStateRegistered()) {
            return false;
        }
        ChildStateData data = maid.getData(ModTaskData.CHILD_STATE);
        return data != null && data.isChild();
    }

    /**
     * Returns {@code true} when {@code playerUuid} is recorded as the
     * father/owner-parent of {@code maid}.
     */
    public static boolean isChildOfPlayer(EntityMaid maid, UUID playerUuid) {
        if (!isChildStateRegistered()) {
            return false;
        }
        ChildStateData data = maid.getData(ModTaskData.CHILD_STATE);
        return data != null && data.fatherUuid().filter(playerUuid::equals).isPresent();
    }

    // -------------------------------------------------------------------------
    // State mutations
    // -------------------------------------------------------------------------

    /**
     * Clears all child-state flags from the maid, promoting it to full adult.
     * Must be called server-side.
     */
    public static void markAsAdult(EntityMaid maid) {
        if (isChildStateRegistered()) {
            maid.setAndSyncData(ModTaskData.CHILD_STATE, ChildStateData.EMPTY);
        }
        maid.removeTag(CHILD_ACTIVE_ENTITY_TAG);
    }

    /**
     * Applies the traits common to all born child maids (born tag, base health
     * boost, favorability seed).  Call this immediately after taming the child.
     */
    public static void applyBornMaidTraits(EntityMaid maid) {
        maid.addTag(BORN_MAID_TAG);
        maid.setFavorability(64);
        AttributeInstance maxHealth = maid.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(maxHealth.getBaseValue() * HEALTH_MULTIPLIER);
            maid.setHealth(maid.getMaxHealth());
        }
        // Mark tame-init state so the growth handler can call tame() again if
        // the entity is loaded from a film (which does not restore ForgeData).
        boolean alreadyTamed = maid.isTame() && maid.getOwnerUUID() != null;
        maid.getPersistentData().putBoolean(PERSISTENT_TAME_INITIALIZED_KEY, alreadyTamed);
        maid.addTag(CHILD_ACTIVE_ENTITY_TAG);
    }

    /**
     * Records parent UUIDs and writes the initial child task-data so the
     * maid is recognised as a child by the growth handler and by the client
     * renderer.  Call this after {@link #applyBornMaidTraits}.
     */
    public static void initChildState(EntityMaid maid, UUID motherUuid, UUID fatherUuid) {
        if (!isChildStateRegistered()) {
            return;
        }
        ChildStateData state = new ChildStateData(
                true,
                ChildStateData.GrowthStage.INFANT,
                0,
                Optional.ofNullable(motherUuid),
                Optional.ofNullable(fatherUuid));
        maid.setAndSyncData(ModTaskData.CHILD_STATE, state);
    }

    /**
     * Copies the mother's model (YSM or standard) onto the child maid.
     */
    public static void inheritModelFromMother(EntityMaid child, EntityMaid mother) {
        if (mother.isYsmModel()) {
            child.setIsYsmModel(true);
            child.setYsmModel(mother.getYsmModelId(), mother.getYsmModelTexture(), mother.getYsmModelName());
        } else {
            child.setIsYsmModel(false);
            child.setModelId(mother.getModelId());
        }
    }

    // -------------------------------------------------------------------------
    // Growth thresholds
    // -------------------------------------------------------------------------

    public static int getAdultAfterTicks() {
        return Math.max(1, ModConfigs.childGrowthDays()) * DAY_TICKS;
    }

    public static int getMiddleStageTicks() {
        return Math.max(1, getAdultAfterTicks() / 2);
    }

    /**
     * Default child custom name, shown when no name-tag is held by the father.
     */
    public static Component defaultChildName() {
        return Component.translatable("entity.maidmarriage.maid_child");
    }
}
