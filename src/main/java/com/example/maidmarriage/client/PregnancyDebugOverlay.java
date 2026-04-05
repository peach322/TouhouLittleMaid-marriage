package com.example.maidmarriage.client;

import com.example.maidmarriage.MaidMarriageMod;
import com.example.maidmarriage.config.ModConfigs;
import com.example.maidmarriage.data.ModTaskData;
import com.example.maidmarriage.data.PregnancyData;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;

@EventBusSubscriber(modid = MaidMarriageMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class PregnancyDebugOverlay {
    private PregnancyDebugOverlay() {
    }

    @SubscribeEvent
    public static void onRender(RenderGuiLayerEvent.Post event) {
        if (!ModConfigs.showPregnancyDebugCountdown()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }

        AABB search = mc.player.getBoundingBox().inflate(64.0D);
        List<EntityMaid> maids = mc.level.getEntitiesOfClass(
                EntityMaid.class,
                search,
                maid -> maid.isOwnedBy(mc.player));
        if (maids.isEmpty()) {
            return;
        }

        long minLeftTicks = Long.MAX_VALUE;
        EntityMaid target = null;
        for (EntityMaid maid : maids) {
            PregnancyData data = maid.getData(ModTaskData.PREGNANCY_DATA);
            if (data == null || !data.pregnant()) {
                continue;
            }
            long needTicks = (long) ModConfigs.pregnancyBirthDays() * 24000L;
            long passedTicks = Math.max(0L, maid.level().getGameTime() - data.conceivedGameTime());
            long leftTicks = Math.max(0L, needTicks - passedTicks);
            if (leftTicks < minLeftTicks) {
                minLeftTicks = leftTicks;
                target = maid;
            }
        }
        if (target == null) {
            return;
        }

        long leftSeconds = (long) Math.ceil(minLeftTicks / 20.0D);
        String timeText = formatSeconds(leftSeconds);
        Component text = Component.translatable("overlay.maidmarriage.debug.birth_countdown", target.getName(), timeText);
        GuiGraphics g = event.getGuiGraphics();
        g.drawString(mc.font, text, 8, 8, 0xFF8BFF98, true);
    }

    private static String formatSeconds(long totalSeconds) {
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0) {
            return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.ROOT, "%02d:%02d", minutes, seconds);
    }
}

