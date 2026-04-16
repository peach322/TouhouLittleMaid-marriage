package com.example.maidmarriage.compat;

import com.example.maidmarriage.entity.ChildMaidHelper;
import com.github.tartaricacid.touhoulittlemaid.api.event.InteractMaidEvent;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidTickEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * 子代女仆跟随抱持处理器：不使用 Minecraft 的骑乘（passenger）系统，
 * 以避免与 TLM 内置的鞍形骑乘机制冲突，以及避免默认骑乘偏移导致的"被抱起"外观。
 *
 * <h3>操作说明</h3>
 * <ul>
 *   <li><b>空手右键（非潜行）</b>：将子代女仆扛在肩上；在女仆的持久数据中写入携带者 UUID，
 *       禁用 AI 与重力，并每 tick 通过 {@code setPos} 将她定位在玩家头顶。</li>
 *   <li><b>携带时潜行（Shift）</b>：放下女仆，恢复 AI 与重力。</li>
 * </ul>
 *
 * <p>若子代女仆成长为成人（见 {@link ChildMaidGrowthHandler}），会在 {@code promoteToAdult}
 * 中自动调用 {@link #clearCarryFlags(EntityMaid)}，不需要在此处重复处理。
 */
public final class ChildMaidRideHandler {

    /** Persistent-data key that stores the UUID of the player currently carrying this maid. */
    static final String TAG_CARRIED_BY = "maidmarriage_carried_by";

    private ChildMaidRideHandler() {
    }

    /**
     * Empty-hand, non-sneak right-click starts carrying the child maid.
     *
     * <p>The event is canceled on <em>both</em> sides so that TLM's {@code openMaidGui} does
     * not open the GUI on either side.  The carry state itself is only applied server-side.
     */
    @SubscribeEvent
    public static void onInteractMaid(InteractMaidEvent event) {
        EntityMaid maid = event.getMaid();
        if (!ChildMaidHelper.shouldStayChild(maid)) {
            return;
        }

        Player player = event.getPlayer();

        // Only trigger on empty-hand, non-sneak right-click.
        if (!event.getStack().isEmpty() || player.isShiftKeyDown()) {
            return;
        }

        // If already being carried, let the interaction fall through (GUI, etc.).
        if (maid.getPersistentData().hasUUID(TAG_CARRIED_BY)) {
            return;
        }

        // Cancel on both sides to prevent TLM's openMaidGui from running.
        event.setCanceled(true);

        // Only start the carry on the server; client state is managed purely by position syncs.
        if (!event.getWorld().isClientSide()) {
            startCarry(player, maid);
        }
    }

    /**
     * Each maid tick (server-side): follow the carrier or stop if the carrier sneaks / goes away.
     *
     * <p>By handling everything here we avoid the unreliable {@code PlayerTickEvent} approach and
     * keep all carry logic in one place that fires every time the maid entity ticks.
     */
    @SubscribeEvent
    public static void onMaidTick(MaidTickEvent event) {
        EntityMaid maid = event.getMaid();
        if (maid.level().isClientSide()) {
            return;
        }

        CompoundTag pd = maid.getPersistentData();
        if (!pd.hasUUID(TAG_CARRIED_BY)) {
            return;
        }

        Player carrier = maid.level().getPlayerByUUID(pd.getUUID(TAG_CARRIED_BY));

        // Carrier offline, in another dimension, or dead → silently clear carry.
        if (carrier == null || !carrier.isAlive()) {
            clearCarryFlags(maid);
            return;
        }

        // Carrier pressed sneak → put the maid down.
        if (carrier.isShiftKeyDown()) {
            clearCarryFlags(maid);
            carrier.level().playSound(null, carrier.blockPosition(),
                    SoundEvents.CHICKEN_EGG, SoundSource.NEUTRAL,
                    0.5F, 0.9F + carrier.getRandom().nextFloat() * 0.2F);
            carrier.sendSystemMessage(Component.translatable(
                    "message.maidmarriage.child.ride.dismount", maid.getDisplayName()));
            return;
        }

        // Position the maid on top of the carrier's head every tick.
        // Her feet sit at carrier.getY() + carrier.getBbHeight() so there is no bounding-box
        // overlap with the carrier, which prevents the vanilla entity-push mechanic from
        // shoving the carrier around.
        maid.setPos(
                carrier.getX(),
                carrier.getY() + carrier.getBbHeight(),
                carrier.getZ());
        maid.setDeltaMovement(Vec3.ZERO);
        maid.setYRot(carrier.getYRot());
        maid.setYHeadRot(carrier.getYHeadRot());
    }

    /**
     * Puts the child maid in carry state: writes the carrier UUID, disables AI and gravity.
     * Must be called server-side.
     */
    static void startCarry(Player player, EntityMaid maid) {
        maid.getPersistentData().putUUID(TAG_CARRIED_BY, player.getUUID());
        maid.setNoAi(true);
        maid.setNoGravity(true);
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.FOX_SNIFF, SoundSource.NEUTRAL,
                0.6F, 1.2F + player.getRandom().nextFloat() * 0.15F);
        player.sendSystemMessage(Component.translatable(
                "message.maidmarriage.child.ride.mount", maid.getDisplayName()));
    }

    /**
     * Removes carry state: clears the persistent-data key and restores AI and gravity.
     * Does NOT emit any sound or message; callers handle that where needed.
     * Must be called server-side.
     */
    static void clearCarryFlags(EntityMaid maid) {
        maid.getPersistentData().remove(TAG_CARRIED_BY);
        maid.setNoAi(false);
        maid.setNoGravity(false);
    }
}
