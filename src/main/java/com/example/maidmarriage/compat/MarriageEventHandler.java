package com.example.maidmarriage.compat;

import com.example.maidmarriage.advancement.ModAdvancements;
import com.example.maidmarriage.config.ModConfigs;
import com.example.maidmarriage.data.ChildStateData;
import com.example.maidmarriage.data.MarriageData;
import com.example.maidmarriage.data.ModTaskData;
import com.example.maidmarriage.data.PregnancyData;
import com.example.maidmarriage.entity.ChildMaidHelper;
import com.example.maidmarriage.init.ModItems;
import com.example.maidmarriage.item.MarriageApplicationItem;
import com.github.tartaricacid.touhoulittlemaid.api.event.InteractMaidEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * 缂佹挸顭锋稉搴濇唉娴滄帗鐗宠箛鍐偓鏄忕帆閿涙艾顦╅悶鍡樼湴婵犳哎鈧焦鍨濋幐鍥у煝鐎涙ぜ鈧焦绁寸拠鏇壕閸忛鐡戦妴?
 * 鐠囥儳琚惃鍕徔娴ｆ捇鈧槒绶崣顖氬棘鐟欎椒绗呴弬瑙勬煙濞夋洑绗岀€涙顔岀€规矮绠熼妴?
 */
public final class MarriageEventHandler {
    private static final String TAG_PLAYER_PRIMARY_MAID = "maidmarriage_primary_maid";
    private static final String TAG_RING_USED = "maidmarriage_ring_used";
    private static final String TAG_RING_PLAYER = "maidmarriage_ring_player";
    private static final String TAG_RING_MAID = "maidmarriage_ring_maid";
    private static final String TAG_FLOWER_GIFT_MASK = "maidmarriage_flower_gift_mask";
    private static final int NORMAL_FLOWER_FAVORABILITY_GAIN = 10;
    private static final int RAINBOW_BOUQUET_FAVORABILITY_GAIN = 10;
    private static final int PROPOSAL_PUNISHMENT_LIGHTNING_COUNT = 5;
    private static final int PROPOSAL_PUNISHMENT_CONFUSION_DURATION_TICKS = 200;

    private MarriageEventHandler() {
    }

    @SubscribeEvent
    public static void onInteractMaid(InteractMaidEvent event) {
        if (event.getWorld().isClientSide()) {
            return;
        }
        if (ModTaskData.MARRIAGE_DATA == null) {
            return;
        }

        ItemStack stack = event.getStack();
        if (stack.isEmpty()) {
            if (tryHandleAffectionInteraction(event.getPlayer(), event.getMaid())) {
                event.setCanceled(true);
            }
            return;
        }

        if (stack.is(ModItems.PROPOSAL_RING.get())) {
            handleProposal(event.getPlayer(), event.getMaid(), stack);
            event.setCanceled(true);
            return;
        }
        if (stack.is(ModItems.YES_PILLOW.get())) {
            handleBreedingTest(event.getPlayer(), event.getMaid());
            event.setCanceled(true);
            return;
        }
        if (stack.is(ModItems.LONGING_TESTER.get())) {
            handleLongingTest(event.getPlayer(), event.getMaid());
            event.setCanceled(true);
            return;
        }
        if (stack.is(ModItems.MARRIAGE_APPLICATION.get())) {
            handleMarriageApplicationMaidSelection(event.getPlayer(), event.getMaid(), stack);
            event.setCanceled(true);
            return;
        }
        if (stack.is(ModItems.GENEALOGY_DISPLAY_BOOK.get())) {
            handleGenealogyDisplay(event.getPlayer(), event.getMaid());
            event.setCanceled(true);
            return;
        }
        if (tryHandleFlowerGift(event.getPlayer(), event.getMaid(), stack)) {
            event.setCanceled(true);
            return;
        }
        if (MaidWorkManager.tryHandleFavorRecovery(event.getPlayer(), event.getMaid(), stack)) {
            event.setCanceled(true);
        }
    }

    private static void handleProposal(net.minecraft.world.entity.player.Player player, EntityMaid maid, ItemStack stack) {
        if (!maid.isOwnedBy(player)) {
            player.sendSystemMessage(Component.translatable("message.maidmarriage.proposal.need_owner"));
            return;
        }

        if (maid.getTags().contains(ChildMaidHelper.BORN_MAID_TAG) && ChildMaidHelper.shouldStayChild(maid)) {
            if (maid.level() instanceof ServerLevel level) {
                summonProposalPunishLightning(level, player);
            }
            player.sendSystemMessage(Component.translatable("message.maidmarriage.proposal.own_child_cannot_marry"));
            return;
        }

        int requiredFavorability = ModConfigs.requiredFavorability();
        if (maid.getFavorability() < requiredFavorability) {
            player.sendSystemMessage(Component.translatable(
                    "message.maidmarriage.proposal.need_favorability", requiredFavorability));
            return;
        }
        if (!ModConfigs.haremMode() && hasOtherMarriage(player, maid)) {
            player.sendSystemMessage(Component.translatable("message.maidmarriage.proposal.harem_disabled"));
            return;
        }

        MarriageData currentData = maid.getOrCreateData(ModTaskData.MARRIAGE_DATA, MarriageData.EMPTY);
        if (currentData.isMarriedWith(player.getUUID())) {
            player.sendSystemMessage(Component.translatable("message.maidmarriage.proposal.already_married_with_you"));
            return;
        }
        if (currentData.married()) {
            player.sendSystemMessage(Component.translatable("message.maidmarriage.proposal.already_married"));
            return;
        }

        ItemStack offhandRing = player.getOffhandItem();
        if (!offhandRing.is(ModItems.PROPOSAL_RING.get())) {
            player.sendSystemMessage(Component.translatable("message.maidmarriage.proposal.need_offhand_ring"));
            return;
        }
        if (isRingUsed(stack) || isRingUsed(offhandRing)) {
            player.sendSystemMessage(Component.translatable("message.maidmarriage.proposal.ring_used"));
            return;
        }

        ItemStack maidRing = stack.copyWithCount(1);

        maid.setAndSyncData(ModTaskData.MARRIAGE_DATA, currentData.marry(player.getUUID(), maid.level().getGameTime()));
        engraveRing(offhandRing, player, maid);
        engraveRing(maidRing, player, maid);
        giveRingToMaid(maid, maidRing);

        consumeMainHandProposalRing(player, stack);
        giveMarriagePillows(player, maid);
        if (!ModConfigs.haremMode()) {
            player.getPersistentData().putUUID(TAG_PLAYER_PRIMARY_MAID, maid.getUUID());
        }

        if (maid.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.HEART, maid.getX(), maid.getY(1), maid.getZ(),
                    10, 0.25, 0.25, 0.25, 0.01);
        }
        maid.level().playSound(null, maid.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.3F);
        player.sendSystemMessage(Component.translatable("message.maidmarriage.proposal.success"));
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            ModAdvancements.grantMarriage(serverPlayer);
            RomanceSleepManager.startProposalDialogue(serverPlayer, maid);
        }
    }

    private static void handleBreedingTest(net.minecraft.world.entity.player.Player player, EntityMaid maid) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
            return;
        }
        if (!maid.isOwnedBy(player)) {
            player.sendSystemMessage(Component.translatable("message.maidmarriage.breed.need_owner"));
            return;
        }
        if (!isMarriedWithPlayer(maid, player)) {
            player.sendSystemMessage(Component.translatable("message.maidmarriage.breed.not_married"));
            return;
        }
        if (player.level().isDay()) {
            player.sendSystemMessage(Component.translatable("message.maidmarriage.breed.not_time_yet"));
            RomanceSleepManager.speakSingleLine(maid, "dialogue.maidmarriage.daytime");
            return;
        }

        RomanceSleepManager.tryStartRomanceRhythmThenSleep(serverPlayer, maid);
    }

    private static void handleLongingTest(net.minecraft.world.entity.player.Player player, EntityMaid maid) {
        if (!ModConfigs.clingyMaidEnabled()) {
            player.sendSystemMessage(Component.translatable("message.maidmarriage.debug.longing_disabled"));
            return;
        }
        if (!maid.isOwnedBy(player)) {
            player.sendSystemMessage(Component.translatable("message.maidmarriage.breed.need_owner"));
            return;
        }
        PregnancyData current = maid.getOrCreateData(ModTaskData.PREGNANCY_DATA, PregnancyData.EMPTY);
        maid.setAndSyncData(ModTaskData.PREGNANCY_DATA, current.forceLonging(maid.level().getGameTime()));
        player.sendSystemMessage(Component.translatable("message.maidmarriage.debug.longing_applied"));
    }

    private static boolean tryHandleFlowerGift(net.minecraft.world.entity.player.Player player, EntityMaid maid, ItemStack stack) {
        if (!maid.isOwnedBy(player)) {
            return false;
        }
        if (stack.is(ModItems.RAINBOW_BOUQUET.get())) {
            applyFlowerGiftResult(player, maid, stack, "message.maidmarriage.flower.color.rainbow",
                    "dialogue.maidmarriage.flower.rainbow", RAINBOW_BOUQUET_FAVORABILITY_GAIN);
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                ModAdvancements.grantRainbowBouquet(serverPlayer);
            }
            return true;
        }

        FlowerGift gift = FlowerGift.from(stack);
        if (gift == null) {
            return false;
        }

        if (hasGiftedFlowerColor(maid, gift)) {
            player.sendSystemMessage(Component.translatable(
                    "message.maidmarriage.flower.already_gifted",
                    Component.translatable(gift.colorNameKey)));
            return true;
        }
        setGiftedFlowerColor(maid, gift);

        applyFlowerGiftResult(player, maid, stack, gift.colorNameKey, gift.dialogueKey, NORMAL_FLOWER_FAVORABILITY_GAIN);

        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            ModAdvancements.grantFlowerGift(serverPlayer, gift.advancementSuffix);
        }
        return true;
    }

    private static boolean hasGiftedFlowerColor(EntityMaid maid, FlowerGift gift) {
        int giftedMask = maid.getPersistentData().getInt(TAG_FLOWER_GIFT_MASK);
        return (giftedMask & gift.bit) != 0;
    }

    private static void setGiftedFlowerColor(EntityMaid maid, FlowerGift gift) {
        int giftedMask = maid.getPersistentData().getInt(TAG_FLOWER_GIFT_MASK);
        maid.getPersistentData().putInt(TAG_FLOWER_GIFT_MASK, giftedMask | gift.bit);
    }

    private static void applyFlowerGiftResult(net.minecraft.world.entity.player.Player player, EntityMaid maid, ItemStack stack,
            String colorNameKey, String dialogueKey, int favorabilityGain) {
        maid.getFavorabilityManager().add(favorabilityGain);
        RomanceSleepManager.speakSingleLine(maid, dialogueKey);
        player.sendSystemMessage(Component.translatable(
                "message.maidmarriage.flower.gift",
                Component.translatable(colorNameKey),
                favorabilityGain,
                maid.getFavorability()));

        if (!player.getAbilities().instabuild) {
            ItemStack mainHandRing = player.getMainHandItem();
            if (mainHandRing.is(ModItems.PROPOSAL_RING.get())) {
                mainHandRing.shrink(1);
            } else {
                stack.shrink(1);
            }
        }
        if (maid.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.HEART, maid.getX(), maid.getY(1.1), maid.getZ(),
                    6, 0.25, 0.2, 0.25, 0.02);
        }
        maid.level().playSound(null, maid.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.45F, 1.45F);
    }

    private static boolean isMarriedWithPlayer(EntityMaid maid, net.minecraft.world.entity.player.Player player) {
        MarriageData data = maid.getData(ModTaskData.MARRIAGE_DATA);
        return data != null && data.isMarriedWith(player.getUUID());
    }

    private static boolean hasOtherMarriage(net.minecraft.world.entity.player.Player player, EntityMaid currentMaid) {
        CompoundTag tag = player.getPersistentData();
        if (!tag.hasUUID(TAG_PLAYER_PRIMARY_MAID)) {
            return false;
        }
        return !tag.getUUID(TAG_PLAYER_PRIMARY_MAID).equals(currentMaid.getUUID());
    }

    private static boolean isRingUsed(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBoolean(TAG_RING_USED);
    }

    private static void engraveRing(ItemStack ring, net.minecraft.world.entity.player.Player player, EntityMaid maid) {
        CustomData.update(DataComponents.CUSTOM_DATA, ring, tag -> {
            tag.putBoolean(TAG_RING_USED, true);
            tag.putUUID(TAG_RING_PLAYER, player.getUUID());
            tag.putUUID(TAG_RING_MAID, maid.getUUID());
        });
        ring.set(DataComponents.CUSTOM_NAME, Component.translatable("item.maidmarriage.vow_ring"));
        ring.set(DataComponents.LORE, new ItemLore(List.of(
                Component.translatable("item.maidmarriage.vow_ring.pair", player.getName(), maid.getName()),
                Component.translatable("item.maidmarriage.vow_ring.desc"))));
    }

    private static void giveRingToMaid(EntityMaid maid, ItemStack ring) {
        if (ring.isEmpty()) {
            return;
        }
        if (maid.getMainHandItem().isEmpty()) {
            maid.setItemInHand(InteractionHand.MAIN_HAND, ring);
            return;
        }
        ItemStack remaining = ItemHandlerHelper.insertItemStacked(maid.getAvailableInv(false), ring, false);
        if (!remaining.isEmpty()) {
            ItemEntity drop = new ItemEntity(maid.level(), maid.getX(), maid.getY() + 0.5, maid.getZ(), remaining);
            maid.level().addFreshEntity(drop);
        }
    }

    private static void giveMarriagePillows(net.minecraft.world.entity.player.Player player, EntityMaid maid) {
        ItemStack pillowForPlayer = new ItemStack(ModItems.YES_PILLOW.get());
        if (!player.getInventory().add(pillowForPlayer)) {
            player.drop(pillowForPlayer, false);
        }

        ItemStack pillowForMaid = new ItemStack(ModItems.YES_PILLOW.get());
        ItemStack remaining = ItemHandlerHelper.insertItemStacked(maid.getAvailableInv(false), pillowForMaid, false);
        if (!remaining.isEmpty()) {
            ItemEntity drop = new ItemEntity(maid.level(), maid.getX(), maid.getY() + 0.5, maid.getZ(), remaining);
            maid.level().addFreshEntity(drop);
        }
    }

    private static void consumeMainHandProposalRing(net.minecraft.world.entity.player.Player player, ItemStack fallbackStack) {
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.is(ModItems.PROPOSAL_RING.get())) {
            mainHand.shrink(1);
        } else if (!fallbackStack.isEmpty() && fallbackStack.is(ModItems.PROPOSAL_RING.get())) {
            fallbackStack.shrink(1);
        } else {
            var inventory = player.getInventory();
            for (int i = 0; i < inventory.items.size(); i++) {
                ItemStack invStack = inventory.items.get(i);
                if (invStack.is(ModItems.PROPOSAL_RING.get())) {
                    invStack.shrink(1);
                    break;
                }
            }
        }
        player.getInventory().setChanged();
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            serverPlayer.containerMenu.broadcastChanges();
        }
    }

    private static void summonProposalPunishLightning(ServerLevel level, net.minecraft.world.entity.player.Player player) {
        for (int i = 0; i < PROPOSAL_PUNISHMENT_LIGHTNING_COUNT; i++) {
            LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(level);
            if (lightning == null) {
                continue;
            }
            lightning.moveTo(player.getX(), player.getY(), player.getZ());
            lightning.setVisualOnly(true);
            if (player instanceof ServerPlayer serverPlayer) {
                lightning.setCause(serverPlayer);
            }
            level.addFreshEntity(lightning);
        }
        if (!player.getAbilities().instabuild) {
            player.setHealth(1.0F);
        }
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, PROPOSAL_PUNISHMENT_CONFUSION_DURATION_TICKS, 0));
    }

    private static void handleGenealogyDisplay(net.minecraft.world.entity.player.Player player, EntityMaid maid) {
        ChildStateData childState = maid.getData(ModTaskData.CHILD_STATE);
        if (childState == null || (!childState.motherUuid().isPresent() && !childState.fatherUuid().isPresent())) {
            player.sendSystemMessage(Component.translatable("message.maidmarriage.genealogy.none"));
            return;
        }
        String motherInfo = childState.motherUuid().map(UUID::toString).orElse("-");
        String fatherInfo = childState.fatherUuid().map(UUID::toString).orElse("-");
        player.sendSystemMessage(Component.translatable("message.maidmarriage.genealogy.header", maid.getDisplayName()));
        player.sendSystemMessage(Component.translatable("message.maidmarriage.genealogy.mother", motherInfo));
        player.sendSystemMessage(Component.translatable("message.maidmarriage.genealogy.father", fatherInfo));
    }

    private static void handleMarriageApplicationMaidSelection(net.minecraft.world.entity.player.Player player, EntityMaid maid, ItemStack stack) {
        if (!maid.isOwnedBy(player)) {
            player.sendSystemMessage(Component.translatable("message.maidmarriage.application.need_owner"));
            return;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putUUID(MarriageApplicationItem.TAG_PENDING_MAID, maid.getUUID());
            tag.putUUID(MarriageApplicationItem.TAG_PENDING_OWNER, player.getUUID());
        });
        player.sendSystemMessage(Component.translatable("message.maidmarriage.application.maid_selected", maid.getDisplayName()));
    }

    private static boolean tryHandleAffectionInteraction(net.minecraft.world.entity.player.Player player, EntityMaid maid) {
        if (!maid.isOwnedBy(player)) {
            return false;
        }
        if (ChildMaidHelper.shouldStayChild(maid)) {
            return false;
        }
        if (!isMarriedWithPlayer(maid, player)) {
            return false;
        }
        if (maid.isInSittingPose()) {
            playAffectionEffect(maid, "dialogue.maidmarriage.interaction.pet");
            player.sendSystemMessage(Component.translatable("message.maidmarriage.interaction.pet", maid.getDisplayName()));
            return true;
        }
        if (player.isShiftKeyDown()) {
            playAffectionEffect(maid, "dialogue.maidmarriage.interaction.kiss");
            player.sendSystemMessage(Component.translatable("message.maidmarriage.interaction.kiss", maid.getDisplayName()));
            return true;
        }
        if (!maid.isPassenger()) {
            maid.startRiding(player, true);
        }
        playAffectionEffect(maid, "dialogue.maidmarriage.interaction.hug");
        player.sendSystemMessage(Component.translatable("message.maidmarriage.interaction.hug", maid.getDisplayName()));
        return true;
    }

    private static void playAffectionEffect(EntityMaid maid, String dialogueKey) {
        if (maid.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.HEART, maid.getX(), maid.getY(1.0), maid.getZ(),
                    6, 0.25, 0.2, 0.25, 0.01);
        }
        RomanceSleepManager.speakSingleLine(maid, dialogueKey);
        maid.level().playSound(null, maid.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.4F, 1.5F);
    }

    private enum FlowerGift {
        RED("red", 1 << 0, "message.maidmarriage.flower.color.red", "dialogue.maidmarriage.flower.red",
                Items.POPPY, Items.RED_TULIP, Items.ROSE_BUSH),
        YELLOW("yellow", 1 << 1, "message.maidmarriage.flower.color.yellow", "dialogue.maidmarriage.flower.yellow",
                Items.DANDELION, Items.SUNFLOWER),
        BLUE("blue", 1 << 2, "message.maidmarriage.flower.color.blue", "dialogue.maidmarriage.flower.blue",
                Items.BLUE_ORCHID, Items.CORNFLOWER),
        WHITE("white", 1 << 3, "message.maidmarriage.flower.color.white", "dialogue.maidmarriage.flower.white",
                Items.AZURE_BLUET, Items.WHITE_TULIP, Items.OXEYE_DAISY, Items.LILY_OF_THE_VALLEY),
        ORANGE("orange", 1 << 4, "message.maidmarriage.flower.color.orange", "dialogue.maidmarriage.flower.orange",
                Items.ORANGE_TULIP, Items.TORCHFLOWER),
        PINK("pink", 1 << 5, "message.maidmarriage.flower.color.pink", "dialogue.maidmarriage.flower.pink",
                Items.PINK_TULIP, Items.PEONY),
        PURPLE("purple", 1 << 6, "message.maidmarriage.flower.color.purple", "dialogue.maidmarriage.flower.purple",
                Items.ALLIUM, Items.LILAC),
        BLACK("black", 1 << 7, "message.maidmarriage.flower.color.black", "dialogue.maidmarriage.flower.black",
                Items.WITHER_ROSE);

        private final String advancementSuffix;
        private final int bit;
        private final String colorNameKey;
        private final String dialogueKey;
        private final List<Item> flowers;

        FlowerGift(String advancementSuffix, int bit, String colorNameKey, String dialogueKey, Item... flowers) {
            this.advancementSuffix = advancementSuffix;
            this.bit = bit;
            this.colorNameKey = colorNameKey;
            this.dialogueKey = dialogueKey;
            this.flowers = Arrays.asList(flowers);
        }

        private static FlowerGift from(ItemStack stack) {
            for (FlowerGift gift : values()) {
                if (gift.flowers.stream().anyMatch(stack::is)) {
                    return gift;
                }
            }
            return null;
        }
    }
}
