package com.example.maidmarriage.network.payload;

import com.example.maidmarriage.MaidMarriageMod;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record StartRomanceRhythmPayload(UUID maidUuid) implements CustomPacketPayload {
    public static final Type<StartRomanceRhythmPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MaidMarriageMod.MOD_ID, "start_romance_rhythm"));

    public static final StreamCodec<ByteBuf, StartRomanceRhythmPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            payload -> payload.maidUuid().toString(),
            maidIdStr -> new StartRomanceRhythmPayload(UUID.fromString(maidIdStr))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
