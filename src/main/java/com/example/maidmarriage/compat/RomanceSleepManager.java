package com.example.maidmarriage.compat;

import com.example.maidmarriage.advancement.ModAdvancements;
import com.example.maidmarriage.config.ModConfigs;
import com.example.maidmarriage.data.MarriageData;
import com.example.maidmarriage.data.ModTaskData;
import com.example.maidmarriage.data.PregnancyData;
import com.example.maidmarriage.entity.ChildMaidHelper;
import com.example.maidmarriage.rhythm.RomanceRhythmSync;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidTickEvent;
import com.github.tartaricacid.touhoulittlemaid.block.BlockMaidBed;
import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.IChatBubbleData;
import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.implement.TextChatBubbleData;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 闁告艾鐬煎﹢銏＄▔鎼达絾鏅搁柤鎻掑级缁侊妇绮欑€ｎ剦鍚€闁荤偛妫寸槐鐗堝緞閸曨厽鍊為柛鎾楀嫬鍓伴柕鍡曠缁洪箖骞嗛崨顓熷剷闁绘粠鍨埀顑跨劍閳ь兘鍋撻悗娑欐磻缁楀矂宕氶崱妞栴偊濡?
 * 閻犲洢鍎崇悮顐︽儍閸曨偄寰斿ù锝嗘崌閳ь剚妲掔欢顐﹀矗椤栨艾妫橀悷娆庢缁楀懘寮憴鍕厵婵炲娲戠粭宀€鈧稒顨嗛宀€鈧鐭粻鐔煎Υ?
 */
public final class RomanceSleepManager {
    private static final int WAKE_DIALOGUE_DELAY_TICKS = 40;
    private static final int SCENE_INTERVAL_TICKS = 24;
    private static final int SINGLE_LINE_BUBBLE_TICKS = 100;
    private static final int MULTI_LINE_BUBBLE_TICKS = 60;
    private static final int MULTI_LINE_GAP_TICKS = 30;
    private static final int MULTI_LINE_STEP_TICKS = MULTI_LINE_BUBBLE_TICKS + MULTI_LINE_GAP_TICKS;
    private static final long LONGING_SADDLE_COOLDOWN_TICKS = 200L;
    private static final double LONGING_HEART_RANGE_SQR = 25.0D;
    private static final String TAG_ROMANCE_COUNT = "maidmarriage_romance_count";
    private static final String TAG_LONGING_NEXT_TALK_TICK = "maidmarriage_longing_next_talk_tick";
    private static final String TAG_LONGING_LINE_INDEX = "maidmarriage_longing_line_index";
    private static final String TAG_LONGING_SADDLE_NEXT_TALK_TICK = "maidmarriage_longing_saddle_next_talk_tick";
    private static final String TAG_PENDING_WAKE_DIALOGUE = "maidmarriage_pending_wake_dialogue";
    private static final String TAG_PENDING_WAKE_DIALOGUE_DUE_TICK = "maidmarriage_pending_wake_dialogue_due_tick";
    /**
     * 缂佹鍏涚粩鏉戔枎閳ヨ櫕鍊遍悗鍨箑缁楁挾浠﹂悙瀛樼€俊妤€鐗炵槐婵嗩嚕妤﹁法娈堕柍銉︾矊閸ㄩ潧鈻庨垾鑼Ъ濡ょ姴澶囬埀顒佺箘濞堟垶绂掗鍕闁规壆鍠嗛埀?
     */
    private static final List<String> FIRST_SCENE_LINES = List.of(
            "scene.maidmarriage.sleep.1",
            "scene.maidmarriage.sleep.2",
            "scene.maidmarriage.sleep.3",
            "scene.maidmarriage.sleep.4"
    );
    /**
     * 闂傚牏鍋ら々璇测枎閳╁啯顦ч梻鍛箲濠р偓闁规儼妫勮ぐ鍥儍閸曨偄鈷旈柟顖氭噽缁秹鏁嶅顓濆垔闁?闁绘埈鍘鹃崕?闁哄啨鍎遍悥鍫曟偨濠婂棙绡€濞戞挸顦遍～鎺擃槹鎼淬垻澹愰柕?
     */
    private static final List<List<String>> RANDOM_SCENE_VARIANTS = List.of(
            List.of(
                    "scene.maidmarriage.sleep.gentle.1",
                    "scene.maidmarriage.sleep.gentle.2",
                    "scene.maidmarriage.sleep.gentle.3",
                    "scene.maidmarriage.sleep.gentle.4"
            ),
            List.of(
                    "scene.maidmarriage.sleep.passion.1",
                    "scene.maidmarriage.sleep.passion.2",
                    "scene.maidmarriage.sleep.passion.3",
                    "scene.maidmarriage.sleep.passion.4"
            ),
            List.of(
                    "scene.maidmarriage.sleep.sweet.1",
                    "scene.maidmarriage.sleep.sweet.2",
                    "scene.maidmarriage.sleep.sweet.3",
                    "scene.maidmarriage.sleep.sweet.4"
            )
    );
    private static final List<String> PROPOSAL_LINES = List.of(
            "dialogue.maidmarriage.proposal.1",
            "dialogue.maidmarriage.proposal.2",
            "dialogue.maidmarriage.proposal.3"
    );
    private static final List<String> LONGING_LOOP_LINES = List.of(
            "dialogue.maidmarriage.longing.loop1",
            "dialogue.maidmarriage.longing.loop2",
            "dialogue.maidmarriage.longing.loop3"
    );
    private static final Map<UUID, RomanceSession> SESSIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, ProposalDialogueSession> PROPOSAL_DIALOGUES = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> RHYTHM_CONCEPTION_DECISIONS = new ConcurrentHashMap<>();

    private RomanceSleepManager() {
    }

    public static boolean tryStartRomanceRhythmThenSleep(ServerPlayer player, EntityMaid maid) {
        RomancePrerequisite prerequisite = checkRomancePrerequisite(player, maid);
        if (prerequisite == null) {
            return false;
        }
        RomanceRhythmSync.requestDecision(player, maid, player.level().getGameTime(), prerequisite.playerBedPos());
        return true;
    }

    public static boolean tryStartRomanceSleep(ServerPlayer player, EntityMaid maid) {
        RomancePrerequisite prerequisite = checkRomancePrerequisite(player, maid);
        if (prerequisite == null) {
            return false;
        }
        return beginSleepSession(player, maid, prerequisite.playerBedPos(), prerequisite.pregnancy());
    }

    private static RomancePrerequisite checkRomancePrerequisite(ServerPlayer player, EntityMaid maid) {
        if (!maid.isSleeping()) {
            player.sendSystemMessage(Component.translatable("message.maidmarriage.breed.need_sleeping_maid"));
            return null;
        }

        Optional<BlockPos> maidBedPos = maid.getSleepingPos();
        if (maidBedPos.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.maidmarriage.breed.cannot_find_maid_bed"));
            return null;
        }

        Optional<BlockPos> playerBedPos = findAdjacentPlayerBed(player.serverLevel(), player.blockPosition(), maidBedPos.get());
        if (playerBedPos.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.maidmarriage.breed.need_adjacent_bed"));
            return null;
        }

        PregnancyData pregnancy = maid.getOrCreateData(ModTaskData.PREGNANCY_DATA, PregnancyData.EMPTY);
        if (pregnancy.pregnant() && !pregnancy.isPregnantWith(player.getUUID())) {
            player.sendSystemMessage(Component.translatable("message.maidmarriage.breed.pregnant_with_other"));
            return null;
        }
        return new RomancePrerequisite(playerBedPos.get(), pregnancy);
    }

    public static void startProposalDialogue(ServerPlayer player, EntityMaid maid) {
        ProposalDialogueSession session = new ProposalDialogueSession(
                maid.getUUID(),
                player.serverLevel().getGameTime());
        PROPOSAL_DIALOGUES.put(player.getUUID(), session);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide()) {
            return;
        }
        tickProposalDialogue(player);

        RomanceSession session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }

        if (!player.isSleeping()) {
            finishSession(player, session);
            SESSIONS.remove(player.getUUID());
            return;
        }

        session.sleepTicks++;
        if (session.sleepTicks % SCENE_INTERVAL_TICKS == 0) {
            sendNextSceneLine(player, session);
        }
    }

    @SubscribeEvent
    public static void onMaidTick(MaidTickEvent event) {
        EntityMaid maid = event.getMaid();
        if (maid.level().isClientSide()) {
            return;
        }
        if (maid.tickCount % 20 != 0) {
            return;
        }
        flushPendingWakeDialogue(maid);
        tryAutoBirthOnTick(maid);
        if (!ModConfigs.clingyMaidEnabled()) {
            resetLongingLoopDialogue(maid);
            return;
        }
        MarriageData marriage = maid.getData(ModTaskData.MARRIAGE_DATA);
        if (marriage == null || !marriage.married()) {
            return;
        }
        PregnancyData pregnancy = maid.getData(ModTaskData.PREGNANCY_DATA);
        if (pregnancy == null
                || pregnancy.currentMood(maid.level().getGameTime()).orElse(null) != PregnancyData.MoodState.LONGING) {
            return;
        }
        if (!(maid.level() instanceof ServerLevel level)) {
            return;
        }
        if (!(maid.getOwner() instanceof ServerPlayer owner) || owner.level() != maid.level()) {
            return;
        }
        double distanceSqr = maid.distanceToSqr(owner);
        boolean saddleTriggered = tryTriggerSaddleDialogue(level, maid, owner);

        if (distanceSqr <= LONGING_HEART_RANGE_SQR) {
            level.sendParticles(ParticleTypes.HEART, maid.getX(), maid.getY(1.0D), maid.getZ(),
                    2, 0.25D, 0.15D, 0.25D, 0.01D);
            if (!saddleTriggered) {
                tryTriggerLongingLoopDialogue(level, maid);
            }
        } else {
            resetLongingLoopDialogue(maid);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        SESSIONS.remove(player.getUUID());
        PROPOSAL_DIALOGUES.remove(player.getUUID());
        RomanceRhythmSync.clear(player.getUUID());
        RHYTHM_CONCEPTION_DECISIONS.remove(player.getUUID());
    }

    private static void finishSession(ServerPlayer player, RomanceSession session) {
        Entity maidEntity = player.serverLevel().getEntity(session.maidUuid);
        EntityMaid maid = maidEntity instanceof EntityMaid entityMaid && entityMaid.isAlive() ? entityMaid : null;
        if (!session.sceneFinished) {
            player.sendSystemMessage(Component.translatable("message.maidmarriage.breed.scene_interrupted"));
            return;
        }

        if (maid == null) {
            player.sendSystemMessage(Component.translatable("message.maidmarriage.breed.mother_missing"));
            return;
        }
        if (!maid.isOwnedBy(player)) {
            player.sendSystemMessage(Component.translatable("message.maidmarriage.breed.need_owner"));
            return;
        }
        int romanceCount = increaseRomanceCount(player);
        if (romanceCount == 1) {
            ModAdvancements.grantFirstRomance(player);
        }
        if (romanceCount >= 10) {
            ModAdvancements.grantRomanceTen(player);
        }

        PregnancyData current = maid.getOrCreateData(ModTaskData.PREGNANCY_DATA, PregnancyData.EMPTY);
        long gameTime = maid.level().getGameTime();
        PregnancyData updated = current.markRomance(gameTime);
        maid.setAndSyncData(ModTaskData.PREGNANCY_DATA, updated);

        // If this session will directly lead to childbirth, reserve dialogue channel for birth lines only.
        boolean willGiveBirthNow = updated.pregnant()
                && updated.isPregnantWith(player.getUUID())
                && isBirthDue(updated, gameTime);
        if (!willGiveBirthNow) {
            speakSingleLineAfterWake(maid, "dialogue.maidmarriage.after_romance");
        }

        if (!updated.pregnant()) {
            Boolean rhythmDecision = RHYTHM_CONCEPTION_DECISIONS.remove(player.getUUID());
            boolean willConceive = rhythmDecision != null
                    ? rhythmDecision
                    : maid.getRandom().nextDouble() < ModConfigs.pregnancyChance();
            if (!willConceive) {
                player.sendSystemMessage(Component.translatable("message.maidmarriage.breed.no_conception"));
                return;
            }
            maid.setAndSyncData(ModTaskData.PREGNANCY_DATA, updated.conceive(player.getUUID(), gameTime));
            maid.level().playSound(null, maid.blockPosition(), net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.75F, 1.2F);
            if (maid.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.HEART, maid.getX(), maid.getY(1), maid.getZ(),
                        10, 0.3, 0.2, 0.3, 0.02);
            }
            speakSingleLineAfterWake(maid, "dialogue.maidmarriage.pregnant");
            player.sendSystemMessage(Component.translatable("message.maidmarriage.breed.pregnant_success"));
            return;
        }

        if (!updated.isPregnantWith(player.getUUID())) {
            player.sendSystemMessage(Component.translatable("message.maidmarriage.breed.pregnant_with_other"));
            return;
        }
        if (!isBirthDue(updated, gameTime)) {
            long needTicks = Math.max(1L, ModConfigs.pregnancyBirthDays()) * 24000L;
            long passedTicks = Math.max(0L, gameTime - updated.conceivedGameTime());
            long leftTicks = Math.max(0L, needTicks - passedTicks);
            long leftDays = (long) Math.ceil(leftTicks / 24000.0D);
            player.sendSystemMessage(Component.literal("分娩尚未到期，预计还需 " + leftDays + " 天。"));
            return;
        }

        if (!spawnChild(player, maid)) {
            player.sendSystemMessage(Component.translatable("message.maidmarriage.breed.spawn_fail"));
            return;
        }
        maid.setAndSyncData(ModTaskData.PREGNANCY_DATA, updated.completeBirth());
        speakSingleLineAfterWake(maid, "dialogue.maidmarriage.after_birth");
        player.sendSystemMessage(Component.translatable("message.maidmarriage.breed.birth_success"));
        ModAdvancements.grantChildbirth(player);
    }

    public static void onRhythmPanelResult(ServerPlayer player, UUID maidUuid, boolean conceptionSuccess) {
        RomanceRhythmSync.PendingDecision pending = RomanceRhythmSync.consume(player.getUUID());
        if (pending == null || !pending.maidUuid().equals(maidUuid)) {
            return;
        }

        Entity entity = player.serverLevel().getEntity(maidUuid);
        if (!(entity instanceof EntityMaid maid) || !maid.isAlive()) {
            player.sendSystemMessage(Component.translatable("message.maidmarriage.breed.mother_missing"));
            return;
        }
        if (!maid.isOwnedBy(player)) {
            player.sendSystemMessage(Component.translatable("message.maidmarriage.breed.need_owner"));
            return;
        }

        PregnancyData current = maid.getOrCreateData(ModTaskData.PREGNANCY_DATA, PregnancyData.EMPTY);
        // Even if already pregnant, the rhythm result should still continue into sleep session.
        // Only conception decision needs to be skipped in that case.
        if (!current.pregnant()) {
            RHYTHM_CONCEPTION_DECISIONS.put(player.getUUID(), conceptionSuccess);
        }
        beginSleepSession(player, maid, pending.playerBedPos(), current);
    }

    private static boolean spawnChild(ServerPlayer player, EntityMaid mother) {
        if (!(mother.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        // Ensure mother keeps adult state if save data had stale child flags.
        ChildMaidHelper.markAsAdult(mother);
        EntityMaid child = new EntityMaid(serverLevel);
        child.setPersistenceRequired();

        double spawnX = mother.getX() + mother.getLookAngle().x * 0.35D;
        double spawnY = mother.getY();
        double spawnZ = mother.getZ() + mother.getLookAngle().z * 0.35D;

        child.moveTo(spawnX, spawnY, spawnZ, player.getYRot(), 0);
        tameChildWithOwner(child, player);
        ChildMaidHelper.applyBornMaidTraits(child);
        ChildMaidHelper.initChildState(child, mother.getUUID(), player.getUUID());
        ChildMaidHelper.inheritModelFromMother(child, mother);
        child.setCustomName(readPlannedChildName(player));

        boolean success = serverLevel.addFreshEntity(child);
        if (success) {
            serverLevel.sendParticles(ParticleTypes.HEART, spawnX, spawnY + 0.75, spawnZ, 16, 0.4, 0.25, 0.4, 0.02);
            serverLevel.playSound(null, mother.blockPosition(), net.minecraft.sounds.SoundEvents.CHICKEN_EGG,
                    net.minecraft.sounds.SoundSource.NEUTRAL, 0.7F, 1.1F);
        }
        return success;
    }

    private static void tryAutoBirthOnTick(EntityMaid maid) {
        if (!(maid.level() instanceof ServerLevel level)) {
            return;
        }
        PregnancyData pregnancy = maid.getData(ModTaskData.PREGNANCY_DATA);
        if (pregnancy == null || !pregnancy.pregnant()) {
            return;
        }
        if (!isBirthDue(pregnancy, level.getGameTime())) {
            return;
        }

        ServerPlayer father = pregnancy.father()
                .map(uuid -> level.getServer().getPlayerList().getPlayer(uuid))
                .orElse(null);
        if (father == null && maid.getOwner() instanceof ServerPlayer owner && owner.level() == maid.level()) {
            father = owner;
        }
        if (father == null) {
            return;
        }

        if (!spawnChild(father, maid)) {
            return;
        }
        maid.setAndSyncData(ModTaskData.PREGNANCY_DATA, pregnancy.completeBirth());
        speakSingleLineAfterWake(maid, "dialogue.maidmarriage.after_birth");
        father.sendSystemMessage(Component.translatable("message.maidmarriage.breed.birth_success"));
        ModAdvancements.grantChildbirth(father);
    }

    private static boolean beginSleepSession(ServerPlayer player, EntityMaid maid, BlockPos playerBedPos, PregnancyData pregnancy) {
        player.lookAt(EntityAnchorArgument.Anchor.EYES, maid.position().add(0, 0.3, 0));
        var sleepResult = player.startSleepInBed(playerBedPos);
        if (sleepResult.left().isPresent()) {
            player.sendSystemMessage(Component.translatable("message.maidmarriage.breed.sleep_failed"));
            return false;
        }

        boolean childbirthSession = pregnancy.isPregnantWith(player.getUUID());
        RomanceSession session = new RomanceSession(maid.getUUID(), pickSceneLines(player, maid, childbirthSession));
        SESSIONS.put(player.getUUID(), session);
        sendNextSceneLine(player, session);
        return true;
    }

    private static void tameChildWithOwner(EntityMaid child, ServerPlayer owner) {
        // Use full tame flow so child maid gets the same equipment/hand behavior as normal maids.
        child.tame(owner);
    }

    private static Component readPlannedChildName(ServerPlayer player) {
        ItemStack offhand = player.getOffhandItem();
        if (offhand.is(Items.NAME_TAG) && offhand.has(DataComponents.CUSTOM_NAME)) {
            Component name = offhand.getHoverName().copy();
            if (!player.getAbilities().instabuild) {
                offhand.shrink(1);
            }
            player.sendSystemMessage(Component.translatable("message.maidmarriage.breed.use_name_tag", name));
            return name;
        }
        return Component.translatable("entity.maidmarriage.maid_child");
    }

    private static int increaseRomanceCount(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        int count = data.getInt(TAG_ROMANCE_COUNT) + 1;
        data.putInt(TAG_ROMANCE_COUNT, count);
        return count;
    }

    private static boolean isBirthDue(PregnancyData data, long gameTime) {
        if (!data.pregnant()) {
            return false;
        }
        long needTicks = Math.max(1L, ModConfigs.pregnancyBirthDays()) * 24000L;
        long passedTicks = Math.max(0L, gameTime - data.conceivedGameTime());
        return passedTicks >= needTicks;
    }
    private static void tryTriggerLongingLoopDialogue(ServerLevel level, EntityMaid maid) {
        CompoundTag data = maid.getPersistentData();
        long now = level.getGameTime();
        long nextTalkTick = data.getLong(TAG_LONGING_NEXT_TALK_TICK);
        if (nextTalkTick > now) {
            return;
        }
        int index = data.getInt(TAG_LONGING_LINE_INDEX);
        speakMultiLine(maid, LONGING_LOOP_LINES.get(index));
        data.putInt(TAG_LONGING_LINE_INDEX, (index + 1) % LONGING_LOOP_LINES.size());
        data.putLong(TAG_LONGING_NEXT_TALK_TICK, now + MULTI_LINE_STEP_TICKS);
    }

    private static void resetLongingLoopDialogue(EntityMaid maid) {
        CompoundTag data = maid.getPersistentData();
        data.remove(TAG_LONGING_NEXT_TALK_TICK);
        data.remove(TAG_LONGING_LINE_INDEX);
    }

    private static void speakSingleLineAfterWake(EntityMaid maid, String langKey) {
        CompoundTag data = maid.getPersistentData();
        data.putString(TAG_PENDING_WAKE_DIALOGUE, langKey);
        data.putLong(TAG_PENDING_WAKE_DIALOGUE_DUE_TICK, maid.level().getGameTime() + WAKE_DIALOGUE_DELAY_TICKS);
    }

    private static void flushPendingWakeDialogue(EntityMaid maid) {
        if (maid.isSleeping()) {
            return;
        }
        CompoundTag data = maid.getPersistentData();
        if (!data.contains(TAG_PENDING_WAKE_DIALOGUE, Tag.TAG_STRING)) {
            return;
        }
        long now = maid.level().getGameTime();
        long dueTick = data.getLong(TAG_PENDING_WAKE_DIALOGUE_DUE_TICK);
        if (dueTick > now) {
            return;
        }
        String langKey = data.getString(TAG_PENDING_WAKE_DIALOGUE);
        data.remove(TAG_PENDING_WAKE_DIALOGUE);
        data.remove(TAG_PENDING_WAKE_DIALOGUE_DUE_TICK);
        if (!langKey.isBlank()) {
            speakSingleLine(maid, langKey);
        }
    }

    private static boolean tryTriggerSaddleDialogue(ServerLevel level, EntityMaid maid, ServerPlayer owner) {
        if (!maid.isPassenger() || maid.getVehicle() != owner) {
            return false;
        }
        long now = level.getGameTime();
        CompoundTag data = maid.getPersistentData();
        if (data.getLong(TAG_LONGING_SADDLE_NEXT_TALK_TICK) > now) {
            return false;
        }
        speakSingleLine(maid, "dialogue.maidmarriage.longing_saddle");
        level.sendParticles(ParticleTypes.HEART, maid.getX(), maid.getY(1.0D), maid.getZ(),
                6, 0.3D, 0.15D, 0.3D, 0.01D);
        data.putLong(TAG_LONGING_SADDLE_NEXT_TALK_TICK, now + LONGING_SADDLE_COOLDOWN_TICKS);
        return true;
    }

    private static void tickProposalDialogue(ServerPlayer player) {
        ProposalDialogueSession session = PROPOSAL_DIALOGUES.get(player.getUUID());
        if (session == null) {
            return;
        }
        if (player.serverLevel().getGameTime() < session.nextSpeakTick) {
            return;
        }
        Entity maidEntity = player.serverLevel().getEntity(session.maidUuid);
        if (!(maidEntity instanceof EntityMaid maid) || !maid.isAlive()) {
            PROPOSAL_DIALOGUES.remove(player.getUUID());
            return;
        }
        if (session.lineIndex >= PROPOSAL_LINES.size()) {
            PROPOSAL_DIALOGUES.remove(player.getUUID());
            return;
        }

        speakMultiLine(maid, PROPOSAL_LINES.get(session.lineIndex));
        session.lineIndex++;
        session.nextSpeakTick = player.serverLevel().getGameTime() + MULTI_LINE_STEP_TICKS;

        if (session.lineIndex >= PROPOSAL_LINES.size()) {
            PROPOSAL_DIALOGUES.remove(player.getUUID());
        }
    }

    private static void sendNextSceneLine(ServerPlayer player, RomanceSession session) {
        if (session.lineIndex >= session.sceneLines.size()) {
            session.sceneFinished = true;
            return;
        }
        player.displayClientMessage(Component.translatable(session.sceneLines.get(session.lineIndex)), true);
        session.lineIndex++;
        if (session.lineIndex >= session.sceneLines.size()) {
            session.sceneFinished = true;
        }
    }

    /**
     * 闁哄秷顫夊畵渚€鎮抽埡渚囧晙闁哄嫷鍨伴幆渚€鍨惧鍫禃婵炲棌鈧櫕鍊遍悗鍨妇閳ь剚绻堥埀顒€顦扮€氥劑宕滆閸庡繘鏁?
     * - 濡絾鐗楅濂告晬濮橆剚绁奸悗瑙勭閹搁亶寮ㄦィ鍐炬禃婵炲棌鈧啿鈷旈柟顖氭嫅缁?
     * - 闂傚牏鍋ら々璇测枎閳藉懐绐楅柛锔哄妺缁椾胶绱掗崟顖涱吂闁哄牆鎼晶浠嬪箚閸涱剝鍘柟鑸靛灊缁斿绱掗崟鈹惧亾?
     */
    private static List<String> pickSceneLines(ServerPlayer player, EntityMaid maid, boolean childbirthSession) {
        if (player.getPersistentData().getInt(TAG_ROMANCE_COUNT) <= 0) {
            return FIRST_SCENE_LINES;
        }
        if (!childbirthSession) {
            return RANDOM_SCENE_VARIANTS.get(maid.getRandom().nextInt(RANDOM_SCENE_VARIANTS.size()));
        }

        // Childbirth session: avoid the "passion" line pack, keep tone consistent with birth dialogue.
        List<List<String>> safeVariants = new ArrayList<>();
        for (List<String> variant : RANDOM_SCENE_VARIANTS) {
            boolean hasPassionLine = variant.stream().anyMatch(line -> line.contains(".sleep.passion."));
            if (!hasPassionLine) {
                safeVariants.add(variant);
            }
        }
        if (safeVariants.isEmpty()) {
            return RANDOM_SCENE_VARIANTS.get(0);
        }
        return safeVariants.get(maid.getRandom().nextInt(safeVariants.size()));
    }

    private static Optional<BlockPos> findAdjacentPlayerBed(ServerLevel level, BlockPos playerPos, BlockPos maidBedPos) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos bedPos = maidBedPos.relative(direction);
            BlockState state = level.getBlockState(bedPos);
            if (isPlayerBed(state) && playerPos.distManhattan(bedPos) <= 3) {
                return Optional.of(bedPos);
            }
        }
        return Optional.empty();
    }

    private static boolean isPlayerBed(BlockState state) {
        return state.getBlock() instanceof BedBlock && !(state.getBlock() instanceof BlockMaidBed);
    }

    public static void speakSingleLine(EntityMaid maid, String langKey) {
        maid.getChatBubbleManager().addChatBubble(TextChatBubbleData.create(
                SINGLE_LINE_BUBBLE_TICKS,
                Component.translatable(langKey),
                IChatBubbleData.TYPE_2,
                IChatBubbleData.DEFAULT_PRIORITY));
    }

    private static void speakMultiLine(EntityMaid maid, String langKey) {
        maid.getChatBubbleManager().addChatBubble(TextChatBubbleData.create(
                MULTI_LINE_BUBBLE_TICKS,
                Component.translatable(langKey),
                IChatBubbleData.TYPE_2,
                IChatBubbleData.DEFAULT_PRIORITY));
    }

    private static final class RomanceSession {
        private final UUID maidUuid;
        private final List<String> sceneLines;
        private int sleepTicks = 0;
        private int lineIndex = 0;
        private boolean sceneFinished = false;

        private RomanceSession(UUID maidUuid, List<String> sceneLines) {
            this.maidUuid = maidUuid;
            this.sceneLines = sceneLines;
        }
    }

    private record RomancePrerequisite(BlockPos playerBedPos, PregnancyData pregnancy) {
    }

    private static final class ProposalDialogueSession {
        private final UUID maidUuid;
        private long nextSpeakTick;
        private int lineIndex = 0;

        private ProposalDialogueSession(UUID maidUuid, long nextSpeakTick) {
            this.maidUuid = maidUuid;
            this.nextSpeakTick = nextSpeakTick;
        }
    }
}
