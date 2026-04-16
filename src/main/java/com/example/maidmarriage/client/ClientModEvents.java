package com.example.maidmarriage.client;

import com.example.maidmarriage.MaidMarriageMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

@EventBusSubscriber(modid = MaidMarriageMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
/**
 * 客户端事件注册：绑定渲染器与客户端显示逻辑。
 * 该类的具体逻辑可参见下方方法与字段定义。
 */
public final class ClientModEvents {
    private ClientModEvents() {
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(RhythmKeyMappings.RHYTHM_HIT);
    }
}
