package com.example.maidmarriage.client;

import com.example.maidmarriage.entity.ChildMaidHelper;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

/**
 * 子代女仆渲染钩子：通过 {@link RenderLivingEvent} 对普通女仆实体进行缩放，
 * 替代原 MaidChildRenderer 中的缩放逻辑。
 *
 * <p>仅在客户端通过 task data 中的 {@code isChild} 标志判断是否需要缩放，
 * 该字段由服务端通过 TLM 的 TASK_DATA_SYNC EntityDataAccessor 同步。
 *
 * <p>此类由 {@link com.example.maidmarriage.compat.LittleMaidCompat} 手动注册，
 * 仅在客户端运行时注册，避免服务端加载客户端 API 类。
 */
public final class ChildMaidRenderHandler {

    private static final float CHILD_RENDER_SCALE = 0.72F;

    private ChildMaidRenderHandler() {
    }

    @SubscribeEvent
    public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        if (!isChildMaid(event.getEntity())) {
            return;
        }
        event.getPoseStack().pushPose();
        event.getPoseStack().scale(CHILD_RENDER_SCALE, CHILD_RENDER_SCALE, CHILD_RENDER_SCALE);
    }

    @SubscribeEvent
    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?> event) {
        if (!isChildMaid(event.getEntity())) {
            return;
        }
        event.getPoseStack().popPose();
    }

    private static boolean isChildMaid(Object entity) {
        if (!(entity instanceof EntityMaid maid)) {
            return false;
        }
        return ChildMaidHelper.shouldStayChild(maid);
    }
}
