package com.example.maidmarriage.data;

import com.example.maidmarriage.MaidMarriageMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/**
 * 子代女仆状态数据：记录是否为子代、成长阶段、成长刻与亲代 UUID。
 *
 * <p>注册为 TLM 任务数据（TaskDataKey），利用 {@code SAVE_CODEC} 将完整状态
 * 写入实体 NBT（含胶片），利用 {@code SYNC_CODEC} 仅向客户端同步渲染所需的
 * {@code isChild} 与 {@code stage} 字段，避免每刻带宽浪费。
 */
public record ChildStateData(
        boolean isChild,
        GrowthStage stage,
        int growthTicks,
        Optional<UUID> motherUuid,
        Optional<UUID> fatherUuid) {

    private static final Codec<UUID> UUID_CODEC =
            Codec.STRING.xmap(UUID::fromString, UUID::toString);

    /** Full codec — persisted to entity NBT and films. */
    public static final Codec<ChildStateData> SAVE_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.BOOL.optionalFieldOf("is_child", false).forGetter(ChildStateData::isChild),
                    Codec.STRING.optionalFieldOf("stage", GrowthStage.INFANT.name())
                            .xmap(GrowthStage::byName, GrowthStage::name)
                            .forGetter(ChildStateData::stage),
                    Codec.INT.optionalFieldOf("growth_ticks", 0).forGetter(ChildStateData::growthTicks),
                    UUID_CODEC.optionalFieldOf("mother_uuid").forGetter(ChildStateData::motherUuid),
                    UUID_CODEC.optionalFieldOf("father_uuid").forGetter(ChildStateData::fatherUuid)
            ).apply(instance, ChildStateData::new));

    /** Minimal codec — only what the client needs for child-scale rendering. */
    public static final Codec<ChildStateData> SYNC_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.BOOL.optionalFieldOf("is_child", false).forGetter(ChildStateData::isChild),
                    Codec.STRING.optionalFieldOf("stage", GrowthStage.INFANT.name())
                            .xmap(GrowthStage::byName, GrowthStage::name)
                            .forGetter(ChildStateData::stage)
            ).apply(instance, (isChild, stage) ->
                    new ChildStateData(isChild, stage, 0, Optional.empty(), Optional.empty())));

    public static final ResourceLocation KEY =
            ResourceLocation.fromNamespaceAndPath(MaidMarriageMod.MOD_ID, "child_state");

    public static final ChildStateData EMPTY =
            new ChildStateData(false, GrowthStage.INFANT, 0, Optional.empty(), Optional.empty());

    public enum GrowthStage {
        INFANT,
        MIDDLE,
        ADULT;

        public static GrowthStage byName(String name) {
            for (GrowthStage stage : values()) {
                if (stage.name().equals(name)) {
                    return stage;
                }
            }
            return INFANT;
        }
    }
}
