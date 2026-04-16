package com.example.maidmarriage.compat;

import com.example.maidmarriage.data.ChildStateData;
import com.example.maidmarriage.data.ModTaskData;
import com.example.maidmarriage.entity.ChildMaidHelper;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidTickEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * 子代女仆成长驱动：通过 {@link MaidTickEvent} 替代原 MaidChildEntity.tick()。
 *
 * <p>每刻逻辑：
 * <ol>
 *   <li>检查是否为子代（task data isChild=true）；否则跳过。</li>
 *   <li>刷新实体 tag 与驯服初始化状态。</li>
 *   <li>将 growthTicks+1 写回 task data（无网络同步，仅更新内部 dataMaps）。</li>
 *   <li>当成长阶段变化时，通过 setAndSyncData 同步到客户端。</li>
 *   <li>达到成人刻数后调用 markAsAdult，无需替换实体。</li>
 * </ol>
 */
public final class ChildMaidGrowthHandler {

    private ChildMaidGrowthHandler() {
    }

    @SubscribeEvent
    public static void onMaidTick(MaidTickEvent event) {
        EntityMaid maid = event.getMaid();
        if (maid.level().isClientSide()) {
            return;
        }

        // Fast path: skip maids that are not children.
        if (ModTaskData.CHILD_STATE == null) {
            return;
        }
        ChildStateData state = maid.getData(ModTaskData.CHILD_STATE);
        if (state == null || !state.isChild()) {
            return;
        }

        // Keep entity tag in sync for legacy detection and film compatibility.
        maid.addTag(ChildMaidHelper.CHILD_ACTIVE_ENTITY_TAG);

        // Ensure tame state is initialised (may be missing after film restoration).
        // Fast path returns immediately once PERSISTENT_TAME_INITIALIZED_KEY is set.
        ensureFullTameState(maid);

        // Increment growth ticks via setData (no network packet; dataMaps updated for save).
        int growthTicks = state.growthTicks() + 1;
        ChildStateData updated = new ChildStateData(
                true, state.stage(), growthTicks,
                state.motherUuid(), state.fatherUuid());
        maid.setData(ModTaskData.CHILD_STATE, updated);

        // Check for adult promotion first.
        if (growthTicks >= ChildMaidHelper.getAdultAfterTicks()) {
            promoteToAdult(maid);
            return;
        }

        // Update growth stage and sync to client only when it changes.
        ChildStateData.GrowthStage newStage = computeStage(growthTicks);
        if (newStage != state.stage()) {
            ChildStateData stageUpdated = new ChildStateData(
                    true, newStage, growthTicks,
                    state.motherUuid(), state.fatherUuid());
            maid.setAndSyncData(ModTaskData.CHILD_STATE, stageUpdated);

            if (newStage == ChildStateData.GrowthStage.MIDDLE) {
                if (maid.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                            maid.getX(), maid.getY(1.0D), maid.getZ(),
                            8, 0.25D, 0.2D, 0.25D, 0.02D);
                }
                if (maid.getOwner() instanceof Player owner) {
                    owner.sendSystemMessage(
                            Component.translatable("message.maidmarriage.child.growth.middle"));
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static ChildStateData.GrowthStage computeStage(int growthTicks) {
        if (growthTicks < ChildMaidHelper.getMiddleStageTicks()) {
            return ChildStateData.GrowthStage.INFANT;
        }
        if (growthTicks < ChildMaidHelper.getAdultAfterTicks()) {
            return ChildStateData.GrowthStage.MIDDLE;
        }
        return ChildStateData.GrowthStage.ADULT;
    }

    private static void promoteToAdult(EntityMaid maid) {
        if (!(maid.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        // Auto-clear carry state if the maid is currently being carried by a player.
        if (maid.getPersistentData().hasUUID(ChildMaidRideHandler.TAG_CARRIED_BY)) {
            Player carrier = maid.level().getPlayerByUUID(
                    maid.getPersistentData().getUUID(ChildMaidRideHandler.TAG_CARRIED_BY));
            ChildMaidRideHandler.clearCarryFlags(maid);
            if (carrier != null) {
                carrier.sendSystemMessage(
                        Component.translatable("message.maidmarriage.child.ride.grown_up_dismount",
                                maid.getDisplayName()));
            }
        }
        // In the new design the maid IS already an EntityMaid — just clear flags.
        ChildMaidHelper.markAsAdult(maid);
        serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                maid.getX(), maid.getY(1.0D), maid.getZ(),
                10, 0.3D, 0.2D, 0.3D, 0.02D);
    }

    private static void ensureFullTameState(EntityMaid maid) {
        if (maid.getPersistentData().getBoolean(ChildMaidHelper.PERSISTENT_TAME_INITIALIZED_KEY)) {
            return;
        }
        if (maid.getOwner() instanceof Player owner) {
            maid.tame(owner);
            maid.getPersistentData().putBoolean(ChildMaidHelper.PERSISTENT_TAME_INITIALIZED_KEY, true);
        }
    }
}
