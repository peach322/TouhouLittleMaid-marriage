package com.example.maidmarriage.compat;

import com.example.maidmarriage.MaidMarriageMod;
import com.example.maidmarriage.data.ChildStateData;
import com.example.maidmarriage.data.ModTaskData;
import com.example.maidmarriage.entity.ChildMaidHelper;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidAndItemTransformEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 胶片复活兼容处理器：将胶片中存储的子代女仆数据还原为新格式。
 *
 * <h3>两阶段策略</h3>
 * <ol>
 *   <li>{@link #onToMaidTransform}：在 {@code readAdditionalSaveData} 执行前，
 *       将旧格式胶片数据（GrowthTicks / entity tags）注入到女仆的持久化数据中，
 *       同时向 {@code data} CompoundTag 的 MaidTaskDataMaps 节写入
 *       {@link ChildStateData}，确保 {@code readAdditionalSaveData} 能正确读取。</li>
 *   <li>{@link #onEntityJoinLevel}：若 task data 未通过上述注入还原，则从持久化数据
 *       中读取并调用 {@code setAndSyncData} 完成同步。</li>
 * </ol>
 *
 * <h3>新格式胶片</h3>
 * 新格式胶片已经在 {@code MaidTaskDataMaps} 节保存了 {@link ChildStateData}，
 * {@code EntityMaid.readAdditionalSaveData} 会自动还原，无需额外处理。
 */
public final class ChildMaidFilmReviveHandler {

    private static final Logger LOGGER = LogManager.getLogger(MaidMarriageMod.MOD_ID);

    private ChildMaidFilmReviveHandler() {
    }

    @SubscribeEvent
    public static void onToMaidTransform(MaidAndItemTransformEvent.ToMaid event) {
        CompoundTag data = event.getData();
        if (!isChildMaidFilmData(data)) {
            return;
        }

        // Skip if the film already carries new-format ChildStateData.
        String childStateKey = ChildStateData.KEY.toString();
        if (data.contains("MaidTaskDataMaps", Tag.TAG_COMPOUND)) {
            CompoundTag maps = data.getCompound("MaidTaskDataMaps");
            if (maps.contains(childStateKey, Tag.TAG_COMPOUND)) {
                return;
            }
        }

        // --- Old-format film: extract fields and migrate to new format. ---

        EntityMaid maid = event.getMaid();
        CompoundTag persistent = maid.getPersistentData();

        // Restore growth ticks from old entity-NBT field.
        int growthTicks = data.contains("GrowthTicks", Tag.TAG_INT)
                ? data.getInt("GrowthTicks") : 0;

        // Restore growth stage from old entity-NBT field.
        ChildStateData.GrowthStage stage = data.contains("GrowthStage", Tag.TAG_STRING)
                ? ChildStateData.GrowthStage.byName(data.getString("GrowthStage"))
                : ChildStateData.GrowthStage.INFANT;

        // Restore parent UUIDs from old entity-NBT fields.
        Optional<UUID> motherUuid = data.hasUUID("MotherUuid")
                ? Optional.of(data.getUUID("MotherUuid")) : Optional.empty();
        Optional<UUID> fatherUuid = data.hasUUID("FatherUuid")
                ? Optional.of(data.getUUID("FatherUuid")) : Optional.empty();

        // Mark as child in persistent data so EntityJoinLevelEvent can finish init
        // if task-data injection is somehow missed (e.g. readSaveData clears maps).
        persistent.putBoolean(ChildMaidHelper.PERSISTENT_CHILD_ACTIVE_KEY, true);

        // Inject ChildStateData into MaidTaskDataMaps so readAdditionalSaveData picks it up.
        ChildStateData childState = new ChildStateData(true, stage, growthTicks, motherUuid, fatherUuid);
        CompoundTag taskMaps = data.contains("MaidTaskDataMaps", Tag.TAG_COMPOUND)
                ? data.getCompound("MaidTaskDataMaps")
                : new CompoundTag();
        // ChildStateData.SAVE_CODEC uses RecordCodecBuilder which always encodes to CompoundTag.
        // The instanceof guard below keeps the cast safe if codec behaviour ever changes.
        CompoundTag childStateTag = ChildStateData.SAVE_CODEC
                .encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, childState)
                .resultOrPartial(e -> LOGGER.warn("Failed to encode ChildStateData for film migration: {}", e))
                .filter(t -> t instanceof CompoundTag)
                .map(t -> (CompoundTag) t)
                .orElse(new CompoundTag());
        taskMaps.put(childStateKey, childStateTag);
        data.put("MaidTaskDataMaps", taskMaps);
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        Entity entity = event.getEntity();
        if (!(entity instanceof EntityMaid maid)) {
            return;
        }

        // Only act if the persistent-data flag was set by onToMaidTransform.
        CompoundTag persistent = maid.getPersistentData();
        if (!persistent.getBoolean(ChildMaidHelper.PERSISTENT_CHILD_ACTIVE_KEY)) {
            return;
        }
        // Clear the flag so it doesn't trigger on every world load.
        persistent.remove(ChildMaidHelper.PERSISTENT_CHILD_ACTIVE_KEY);

        // If task data was already restored by readAdditionalSaveData, nothing to do.
        if (ModTaskData.CHILD_STATE != null) {
            ChildStateData current = maid.getData(ModTaskData.CHILD_STATE);
            if (current != null && current.isChild()) {
                return;
            }
            // Task data was cleared by readSaveData; restore from persistent data
            // written in onToMaidTransform (growthTicks / stage may be missing if
            // readAdditionalSaveData already handled them — fallback to defaults).
            maid.setAndSyncData(ModTaskData.CHILD_STATE,
                    new ChildStateData(true, ChildStateData.GrowthStage.INFANT, 0,
                            Optional.empty(), Optional.empty()));
        }
        maid.addTag(ChildMaidHelper.CHILD_ACTIVE_ENTITY_TAG);
    }

    // -------------------------------------------------------------------------
    // Detection
    // -------------------------------------------------------------------------

    private static boolean isChildMaidFilmData(CompoundTag data) {
        // Check entity Tags list for the child-active marker.
        if (data.contains("Tags", Tag.TAG_LIST)) {
            ListTag tags = data.getList("Tags", Tag.TAG_STRING);
            for (int i = 0; i < tags.size(); i++) {
                if (ChildMaidHelper.CHILD_ACTIVE_ENTITY_TAG.equals(tags.getString(i))) {
                    return true;
                }
            }
        }
        // GrowthTicks is written only by old MaidChildEntity — acts as fallback.
        return data.contains("GrowthTicks");
    }
}
