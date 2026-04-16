package com.example.maidmarriage.item;

import com.example.maidmarriage.data.MarriageData;
import com.example.maidmarriage.data.ModTaskData;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public class MarriageApplicationItem extends DescriptionItem {
    public static final String TAG_PENDING_MAID = "maidmarriage_application_pending_maid";
    public static final String TAG_PENDING_OWNER = "maidmarriage_application_pending_owner";

    public MarriageApplicationItem(Properties properties, String tooltipKey) {
        super(properties, tooltipKey);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player user, LivingEntity target, InteractionHand hand) {
        if (user.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(user instanceof ServerPlayer serverUser)) {
            return InteractionResult.PASS;
        }

        if (target instanceof EntityMaid maid) {
            if (!maid.isOwnedBy(user)) {
                user.sendSystemMessage(Component.translatable("message.maidmarriage.application.need_owner"));
                return InteractionResult.SUCCESS;
            }
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
                tag.putUUID(TAG_PENDING_MAID, maid.getUUID());
                tag.putUUID(TAG_PENDING_OWNER, user.getUUID());
            });
            user.sendSystemMessage(Component.translatable("message.maidmarriage.application.maid_selected", maid.getDisplayName()));
            return InteractionResult.SUCCESS;
        }

        if (!(target instanceof ServerPlayer targetPlayer)) {
            return InteractionResult.PASS;
        }

        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            user.sendSystemMessage(Component.translatable("message.maidmarriage.application.need_select_maid"));
            return InteractionResult.SUCCESS;
        }
        var tag = customData.copyTag();
        if (!tag.hasUUID(TAG_PENDING_MAID) || !tag.hasUUID(TAG_PENDING_OWNER)) {
            user.sendSystemMessage(Component.translatable("message.maidmarriage.application.need_select_maid"));
            return InteractionResult.SUCCESS;
        }
        UUID ownerUuid = tag.getUUID(TAG_PENDING_OWNER);
        if (!ownerUuid.equals(user.getUUID())) {
            user.sendSystemMessage(Component.translatable("message.maidmarriage.application.need_original_owner"));
            return InteractionResult.SUCCESS;
        }
        UUID maidUuid = tag.getUUID(TAG_PENDING_MAID);
        if (!(serverUser.serverLevel().getEntity(maidUuid) instanceof EntityMaid maid) || !maid.isAlive()) {
            user.sendSystemMessage(Component.translatable("message.maidmarriage.application.maid_missing"));
            clearPending(stack);
            return InteractionResult.SUCCESS;
        }
        if (!maid.isOwnedBy(user)) {
            user.sendSystemMessage(Component.translatable("message.maidmarriage.application.need_owner"));
            clearPending(stack);
            return InteractionResult.SUCCESS;
        }
        MarriageData currentData = maid.getOrCreateData(ModTaskData.MARRIAGE_DATA, MarriageData.EMPTY);
        maid.setAndSyncData(ModTaskData.MARRIAGE_DATA, currentData.marry(targetPlayer.getUUID(), maid.level().getGameTime()));
        maid.tame(targetPlayer);
        clearPending(stack);
        if (!user.getAbilities().instabuild) {
            stack.shrink(1);
        }
        user.sendSystemMessage(Component.translatable("message.maidmarriage.application.success.owner", targetPlayer.getDisplayName()));
        targetPlayer.sendSystemMessage(Component.translatable("message.maidmarriage.application.success.target", maid.getDisplayName()));
        return InteractionResult.SUCCESS;
    }

    private static void clearPending(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.remove(TAG_PENDING_MAID);
            tag.remove(TAG_PENDING_OWNER);
        });
    }
}
