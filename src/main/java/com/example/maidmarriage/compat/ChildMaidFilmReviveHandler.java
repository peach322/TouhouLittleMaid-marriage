package com.example.maidmarriage.compat;

import com.example.maidmarriage.entity.MaidChildEntity;
import com.example.maidmarriage.init.ModEntities;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidAndItemTransformEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Intercepts TLM's film resurrection so that a MaidChildEntity stored in a film
 * is restored as a proper MaidChildEntity (not a base EntityMaid).
 *
 * <h3>Why the interception must happen in two stages</h3>
 * {@code ItemFilm.filmToMaid()} always hard-codes the entity type as
 * {@code touhou_little_maid:maid} and then calls
 * {@code maid.readAdditionalSaveData(data)} — <em>not</em> {@code maid.load(data)}.
 * Because of this, <strong>neither entity-tags nor NeoForge ForgeData from the film
 * NBT are ever loaded into the EntityMaid object</strong>; they are only loaded by
 * {@code Entity.load()}.  Any detection that relies on those fields being present on
 * the entity inside {@code EntityJoinLevelEvent} therefore silently fails.
 *
 * <h3>Fix</h3>
 * <ol>
 *   <li>{@link #onToMaidTransform} fires <em>before</em> {@code readAdditionalSaveData}.
 *       The raw film {@code data} CompoundTag is still available.  We inspect it for
 *       child-maid markers, stash a copy of the full data in a {@link ThreadLocal}, and
 *       set a ForgeData flag directly on the entity object (bypassing
 *       {@code readAdditionalSaveData}).</li>
 *   <li>{@link #onEntityJoinLevel} detects the ForgeData flag, retrieves the stashed
 *       data, and replaces the EntityMaid with a fully initialised MaidChildEntity.</li>
 * </ol>
 */
public final class ChildMaidFilmReviveHandler {
    /**
     * Holds the original film data between the {@code ToMaid} event and
     * {@code EntityJoinLevelEvent}.  The Minecraft server tick loop is single-threaded,
     * so a {@link ThreadLocal} is sufficient to prevent cross-action contamination while
     * remaining safe if mods run actions from worker threads.
     */
    private static final ThreadLocal<CompoundTag> PENDING_CHILD_DATA = new ThreadLocal<>();

    private ChildMaidFilmReviveHandler() {
    }

    /**
     * Fired by TLM <em>before</em> {@code readAdditionalSaveData} is called on the
     * freshly-created EntityMaid.  At this point {@code event.getData()} is the
     * unmodified (but inventory-stripped) film NBT and still contains every field
     * written by {@link MaidChildEntity#addAdditionalSaveData}, including
     * {@code GrowthTicks}, {@code MotherUuid}, {@code FatherUuid}, and the entity
     * {@code Tags} list.
     */
    @SubscribeEvent
    public static void onToMaidTransform(MaidAndItemTransformEvent.ToMaid event) {
        CompoundTag data = event.getData();
        if (!isChildMaidFilmData(data)) {
            return;
        }
        // Stash the full data so onEntityJoinLevel can pass it to child.load().
        // We cannot rely on maid.saveWithoutId() later because the base EntityMaid
        // does not know about MaidChildEntity-specific fields (GrowthTicks, etc.) and
        // would silently drop them.
        PENDING_CHILD_DATA.set(data.copy());
        // Directly set a ForgeData flag on the in-memory entity object.
        // readAdditionalSaveData() (called next by filmToMaid) does not touch
        // getPersistentData(), so this flag survives until EntityJoinLevelEvent fires.
        event.getMaid().getPersistentData().putBoolean(MaidChildEntity.PERSISTENT_CHILD_ACTIVE_KEY, true);
    }

    /**
     * Fired when {@code filmToMaid} calls {@code worldIn.addFreshEntity(maid)}.
     * If the entity carries our ForgeData flag (set by {@link #onToMaidTransform}),
     * the spawn is cancelled and a properly initialised {@link MaidChildEntity} is
     * added instead, using the stashed original film data.
     */
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        Entity entity = event.getEntity();
        // Only intercept exact base EntityMaid instances; MaidChildEntity itself must pass through.
        if (entity.getClass() != EntityMaid.class) {
            return;
        }
        EntityMaid maid = (EntityMaid) entity;
        if (!maid.getPersistentData().getBoolean(MaidChildEntity.PERSISTENT_CHILD_ACTIVE_KEY)) {
            return;
        }

        // Consume stashed data (may be null if loaded from a world save rather than a film).
        CompoundTag childData = PENDING_CHILD_DATA.get();
        PENDING_CHILD_DATA.remove();

        Level level = event.getLevel();
        MaidChildEntity child = ModEntities.MAID_CHILD.get().create(level);
        if (child == null) {
            return;
        }

        // Prevent the base EntityMaid from being added to the world.
        event.setCanceled(true);

        if (childData != null) {
            // Load from the stashed original film data so that child-specific fields
            // (GrowthTicks, MotherUuid, FatherUuid, GrowthStage) are correctly restored.
            child.load(childData);
        } else {
            // Fallback for edge-cases (e.g. flag was set by some other code path):
            // load what the base EntityMaid managed to deserialise.
            CompoundTag nbt = new CompoundTag();
            maid.saveWithoutId(nbt);
            child.load(nbt);
        }
        // filmToMaid sets the position on the EntityMaid after the ToMaid event but
        // before addFreshEntity; mirror that position onto the child.
        child.moveTo(maid.getX(), maid.getY(), maid.getZ(), maid.getYRot(), maid.getXRot());
        level.addFreshEntity(child);
    }

    /**
     * Returns {@code true} if {@code data} was saved by a living {@link MaidChildEntity}.
     *
     * <p>Two independent markers are checked so that films created before the entity-tag
     * fix (which added {@code CHILD_ACTIVE_ENTITY_TAG}) are still handled correctly:
     * <ul>
     *   <li>The entity {@code Tags} list contains {@link MaidChildEntity#CHILD_ACTIVE_ENTITY_TAG}.
     *       This is always written by {@code Entity.saveWithoutId()} and is never removed
     *       by {@code ItemFilm.removeMaidSomeData()}.</li>
     *   <li>The {@code GrowthTicks} key is present — written by
     *       {@link MaidChildEntity#addAdditionalSaveData} and likewise not stripped by
     *       {@code removeMaidSomeData}.</li>
     * </ul>
     */
    private static boolean isChildMaidFilmData(CompoundTag data) {
        if (data.contains("Tags", Tag.TAG_LIST)) {
            ListTag tags = data.getList("Tags", Tag.TAG_STRING);
            for (int i = 0; i < tags.size(); i++) {
                if (MaidChildEntity.CHILD_ACTIVE_ENTITY_TAG.equals(tags.getString(i))) {
                    return true;
                }
            }
        }
        // Fallback: MaidChildEntity always writes GrowthTicks; base EntityMaid never does.
        return data.contains("GrowthTicks");
    }
}
