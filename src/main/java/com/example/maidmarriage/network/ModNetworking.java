package com.example.maidmarriage.network;

import com.example.maidmarriage.MaidMarriageMod;
import com.example.maidmarriage.compat.RomanceSleepManager;
import com.example.maidmarriage.network.payload.StartRomanceRhythmPayload;
import com.example.maidmarriage.network.payload.SubmitRomanceRhythmPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = MaidMarriageMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class ModNetworking {
    private ModNetworking() {
    }

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToClient(
                StartRomanceRhythmPayload.TYPE,
                StartRomanceRhythmPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        com.example.maidmarriage.client.RomanceRhythmHud.start(payload.maidUuid()))
        );

        registrar.playToServer(
                SubmitRomanceRhythmPayload.TYPE,
                SubmitRomanceRhythmPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer serverPlayer) {
                        RomanceSleepManager.onRhythmPanelResult(serverPlayer, payload.maidUuid(), payload.conceptionSuccess());
                    }
                })
        );
    }
}
