package com.example.maidmarriage.client;

import com.example.maidmarriage.MaidMarriageMod;
import com.example.maidmarriage.config.ModConfigs;
import com.example.maidmarriage.data.MarriageData;
import com.example.maidmarriage.data.ModTaskData;
import com.example.maidmarriage.data.PregnancyData;
import com.github.tartaricacid.touhoulittlemaid.client.gui.entity.maid.AbstractMaidContainerGui;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

@EventBusSubscriber(modid = MaidMarriageMod.MOD_ID, value = Dist.CLIENT)
/**
 * 女仆面板状态扩展：在原面板额外显示婚姻与生理信息。
 * 该类的具体逻辑可参见下方方法与字段定义。
 */
public final class MaidPanelStatusOverlay {
    private MaidPanelStatusOverlay() {
    }

    @SubscribeEvent
    public static void onRenderMaidPanel(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof AbstractMaidContainerGui<?> maidGui)) {
            return;
        }
        EntityMaid maid = maidGui.getMaid();
        Font font = Minecraft.getInstance().font;

        MarriageData marriage = maid.getData(ModTaskData.MARRIAGE_DATA);
        PregnancyData pregnancy = maid.getData(ModTaskData.PREGNANCY_DATA);

        Component marriageText = (marriage != null && marriage.married())
                ? Component.translatable("panel.maidmarriage.marriage.married")
                : Component.translatable("panel.maidmarriage.marriage.single");

        Component physiologyText = (pregnancy != null && pregnancy.firstExperience())
                ? (pregnancy.pregnant()
                ? buildPregnancyCountdownText(maid, pregnancy)
                : Component.translatable("panel.maidmarriage.physiology.normal"))
                : Component.translatable("panel.maidmarriage.physiology.untried");

        Optional<PregnancyData.MoodState> mood = pregnancy == null
                ? Optional.empty()
                : pregnancy.currentMood(maid.level().getGameTime());
        if (!ModConfigs.clingyMaidEnabled()) {
            mood = Optional.empty();
        }

        int x = maidGui.getGuiLeft() + 8;
        int y = maidGui.getGuiTop() + 72;
        event.getGuiGraphics().drawString(font, marriageText, x, y, 0xFFF6C782, false);
        event.getGuiGraphics().drawString(font, physiologyText, x, y + 10, 0xFFF19FB6, false);
        mood.ifPresent(moodState -> {
            Component moodText = Component.translatable("panel.maidmarriage.mood." + moodState.key());
            event.getGuiGraphics().drawString(font, moodText, x, y + 20, 0xFFFFAEC8, false);
        });
    }

    private static Component buildPregnancyCountdownText(EntityMaid maid, PregnancyData pregnancy) {
        long needDays = ModConfigs.pregnancyBirthDays();
        long conceivedDay = pregnancy.conceivedGameTime() / 24000L;
        long nowDay = maid.level().getGameTime() / 24000L;
        long passed = Math.max(0L, nowDay - conceivedDay);
        long left = Math.max(0L, needDays - passed);
        return Component.literal("分娩倒计时: " + left + "天");
    }
}
