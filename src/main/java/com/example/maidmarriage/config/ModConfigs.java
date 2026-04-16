package com.example.maidmarriage.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Common config values for gameplay tuning.
 */
public final class ModConfigs {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue HAREM_MODE;
    private static final ModConfigSpec.IntValue REQUIRED_FAVORABILITY;
    private static final ModConfigSpec.DoubleValue PREGNANCY_CHANCE;
    private static final ModConfigSpec.IntValue PREGNANCY_BIRTH_DAYS;
    private static final ModConfigSpec.IntValue CHILD_GROWTH_DAYS;
    private static final ModConfigSpec.IntValue LONGING_DAYS;
    private static final ModConfigSpec.IntValue ROMANCE_COOLDOWN_DAYS;
    private static final ModConfigSpec.BooleanValue CLINGY_MAID_ENABLED;
    private static final ModConfigSpec.EnumValue<RhythmHitKey> RHYTHM_HIT_KEY;
    private static final ModConfigSpec.BooleanValue SHOW_PREGNANCY_DEBUG_COUNTDOWN;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("General settings for Maid Marriage.")
                .translation("config.maidmarriage.general")
                .push("general");

        HAREM_MODE = builder
                .comment("Allow a player to marry multiple maids.")
                .translation("config.maidmarriage.harem_mode")
                .define("haremMode", false);

        REQUIRED_FAVORABILITY = builder
                .comment("Favorability threshold to marry.")
                .translation("config.maidmarriage.required_favorability")
                .defineInRange("requiredFavorability", 64, 0, 10000);

        PREGNANCY_CHANCE = builder
                .comment("Conception chance after each romance scene. Range: 0.0~1.0")
                .translation("config.maidmarriage.pregnancy_chance")
                .defineInRange("pregnancyChance", 0.6D, 0.0D, 1.0D);

        PREGNANCY_BIRTH_DAYS = builder
                .comment("Days required from conception to childbirth.")
                .translation("config.maidmarriage.pregnancy_birth_days")
                .defineInRange("pregnancyBirthDays", 3, 1, 120);

        CHILD_GROWTH_DAYS = builder
                .comment("Days required for child maid to grow into an adult maid.")
                .translation("config.maidmarriage.child_growth_days")
                .defineInRange("childGrowthDays", 5, 1, 120);

        LONGING_DAYS = builder
                .comment("Days without romance before entering longing mood.")
                .translation("config.maidmarriage.longing_days")
                .defineInRange("longingDays", 3, 1, 30);

        ROMANCE_COOLDOWN_DAYS = builder
                .comment("Days of weak period after romance; during this time romance cannot be triggered again.")
                .translation("config.maidmarriage.romance_cooldown_days")
                .defineInRange("romanceCooldownDays", 1, 0, 30);

        CLINGY_MAID_ENABLED = builder
                .comment("Enable clingy maid behavior and longing mood display.")
                .translation("config.maidmarriage.clingy_maid_enabled")
                .define("clingyMaidEnabled", true);

        builder.pop();

        builder.comment("Rhythm game settings.")
                .translation("config.maidmarriage.rhythm_game")
                .push("rhythm_game");

        RHYTHM_HIT_KEY = builder
                .comment("Rhythm game hit key.")
                .translation("config.maidmarriage.rhythm_hit_key")
                .defineEnum("rhythmHitKey", RhythmHitKey.J);

        builder.pop();

        builder.comment("Debug settings.")
                .translation("config.maidmarriage.debug")
                .push("debug");

        SHOW_PREGNANCY_DEBUG_COUNTDOWN = builder
                .comment("Show pregnancy countdown in top-left corner with seconds.")
                .translation("config.maidmarriage.show_pregnancy_debug_countdown")
                .define("showPregnancyDebugCountdown", false);

        builder.pop();
        SPEC = builder.build();
    }

    private ModConfigs() {
    }

    public static boolean haremMode() {
        return HAREM_MODE.get();
    }

    public static int requiredFavorability() {
        return REQUIRED_FAVORABILITY.get();
    }

    public static double pregnancyChance() {
        return PREGNANCY_CHANCE.get();
    }

    public static int childGrowthDays() {
        return CHILD_GROWTH_DAYS.get();
    }

    public static int pregnancyBirthDays() {
        return PREGNANCY_BIRTH_DAYS.get();
    }

    public static int longingDays() {
        return LONGING_DAYS.get();
    }

    public static int romanceCooldownDays() {
        return ROMANCE_COOLDOWN_DAYS.get();
    }

    public static boolean clingyMaidEnabled() {
        return CLINGY_MAID_ENABLED.get();
    }

    public static RhythmHitKey rhythmHitKey() {
        return RHYTHM_HIT_KEY.get();
    }

    public static boolean showPregnancyDebugCountdown() {
        return SHOW_PREGNANCY_DEBUG_COUNTDOWN.get();
    }

    public enum RhythmHitKey {
        J,
        K,
        L,
        SEMICOLON,
        SPACE
    }
}
