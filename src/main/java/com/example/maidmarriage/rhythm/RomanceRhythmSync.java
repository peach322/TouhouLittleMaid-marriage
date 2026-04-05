package com.example.maidmarriage.rhythm;

import com.example.maidmarriage.network.payload.StartRomanceRhythmPayload;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class RomanceRhythmSync {
    private static final Map<UUID, PendingDecision> PENDING = new ConcurrentHashMap<>();

    private RomanceRhythmSync() {
    }

    public static void requestDecision(ServerPlayer player, EntityMaid maid, long conceivedGameTime, BlockPos playerBedPos) {
        PENDING.put(player.getUUID(), new PendingDecision(maid.getUUID(), conceivedGameTime, playerBedPos.immutable()));
        PacketDistributor.sendToPlayer(player, new StartRomanceRhythmPayload(maid.getUUID()));
        player.sendSystemMessage(Component.translatable("message.maidmarriage.rhythm.enter_panel"));
    }

    public static PendingDecision consume(UUID playerId) {
        return PENDING.remove(playerId);
    }

    public static PendingDecision peek(UUID playerId) {
        return PENDING.get(playerId);
    }

    public static void clear(UUID playerId) {
        PENDING.remove(playerId);
    }

    public record PendingDecision(UUID maidUuid, long conceivedGameTime, BlockPos playerBedPos) {
    }
}
