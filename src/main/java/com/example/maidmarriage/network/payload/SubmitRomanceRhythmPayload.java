package com.example.maidmarriage.network.payload;

import com.example.maidmarriage.MaidMarriageMod;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SubmitRomanceRhythmPayload(UUID maidUuid, boolean conceptionSuccess) implements CustomPacketPayload {
    public static final Type<SubmitRomanceRhythmPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MaidMarriageMod.MOD_ID, "submit_romance_rhythm"));

    public static final StreamCodec<ByteBuf, SubmitRomanceRhythmPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            payload -> payload.maidUuid().toString(),
            ByteBufCodecs.BOOL,
            SubmitRomanceRhythmPayload::conceptionSuccess,
            (maidIdStr, success) -> new SubmitRomanceRhythmPayload(UUID.fromString(maidIdStr), success)
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
