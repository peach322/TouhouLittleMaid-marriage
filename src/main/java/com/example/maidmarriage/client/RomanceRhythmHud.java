package com.example.maidmarriage.client;

import com.example.maidmarriage.MaidMarriageMod;
import com.example.maidmarriage.config.ModConfigs;
import com.example.maidmarriage.network.payload.SubmitRomanceRhythmPayload;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = MaidMarriageMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class RomanceRhythmHud {
    private static final int FAIL_STREAK_LIMIT = 8;
    private static final int PEAK_LIMIT = 3;
    private static final float NOTE_SPEED = 260f;
    private static final long STEP_MS = 520L;
    private static final float TYPEWRITER_CPS = 20f;

    private static final ResourceLocation PORTRAIT_SMILE =
            ResourceLocation.fromNamespaceAndPath(MaidMarriageMod.MOD_ID, "textures/gui/smile.png");
    private static final ResourceLocation PORTRAIT_HOT_SMILE =
            ResourceLocation.fromNamespaceAndPath(MaidMarriageMod.MOD_ID, "textures/gui/hotsmile.png");

    private static boolean active = false;
    private static UUID maidId = null;
    private static boolean sent = false;
    private static boolean lastConfigKeyDown = false;

    private static final List<Note> notes = new ArrayList<>();
    private static int missStreak = 0;
    private static int combo = 0;
    private static String judge = "-";
    private static String fullLine = "女仆：跟上节奏，别急。";
    private static String shownLine = "";
    private static float typeProgress = 0f;

    private static float player = 10f;
    private static float maid = 15f;
    private static int playerPeak = 0;
    private static int maidPeak = 0;
    private static int hits = 0;
    private static int misses = 0;
    private static int maxCombo = 0;

    private static long lastMs = 0L;
    private static long stepTimer = 0L;

    private RomanceRhythmHud() {
    }

    public static void start(UUID targetMaid) {
        maidId = targetMaid;
        sent = false;
        active = true;
        lastConfigKeyDown = false;
        notes.clear();
        missStreak = 0;
        combo = 0;
        judge = "-";
        player = 10f;
        maid = 15f;
        playerPeak = 0;
        maidPeak = 0;
        hits = 0;
        misses = 0;
        maxCombo = 0;
        lastMs = Util.getMillis();
        stepTimer = 0L;
        setLine("女仆：跟上节奏，别急哦主人~");
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!active) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (mc.screen != null) {
            return;
        }

        long now = Util.getMillis();
        long dt = Math.max(0L, now - lastMs);
        lastMs = now;

        updateTypewriter(dt);

        if (consumeHitInput(mc)) {
            hit();
        }

        stepTimer += dt;
        while (stepTimer >= STEP_MS) {
            stepTimer -= STEP_MS;
            spawnStep();
        }

        float dx = NOTE_SPEED * (dt / 1000f);
        float missX = 360f + 24f;
        Iterator<Note> it = notes.iterator();
        while (it.hasNext()) {
            Note n = it.next();
            n.x += dx;
            if (n.x > missX) {
                it.remove();
                onMiss();
                if (!active) {
                    return;
                }
            }
        }

        player = clamp(player - 0.9f * (dt / 1000f), 0f, 100f);
        maid = clamp(maid - 0.6f * (dt / 1000f), 0f, 100f);
        checkPeakOrEnd();
    }

    @SubscribeEvent
    public static void render(RenderGuiLayerEvent.Post event) {
        if (!active) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) {
            return;
        }

        GuiGraphics g = event.getGuiGraphics();
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();

        int panelW = 480;
        int panelH = 270;
        int x = w / 2 - panelW / 2;
        int y = Math.max(8, h / 2 - panelH / 2);

        g.fill(x, y, x + panelW, y + panelH, 0xEE12101A);
        g.fill(x, y, x + panelW, y + 22, 0xFF000000);
        g.drawString(mc.font, "Maid Marriage Rhythm", x + 8, y + 7, 0xFFFFFFFF, false);

        String meta = String.format("%d/%d  %d/%d", playerPeak, PEAK_LIMIT, maidPeak, PEAK_LIMIT);
        g.drawString(mc.font, meta, x + 8, y + 28, 0xFFECE5FF, false);

        int barTop = y + 50;
        int barBottom = y + 206;
        drawBar(g, x + 20, barTop, barBottom, player, 0xFF4EA0FF);
        drawBar(g, x + panelW - 34, barTop, barBottom, maid, 0xFFFF69C5);

        int tx = x + 60;
        int ty = y + 88;
        int tw = panelW - 120;
        int th = 54;
        g.fill(tx, ty, tx + tw, ty + th, 0x99262333);
        g.fill(tx, ty + th / 2, tx + tw, ty + th / 2 + 1, 0x66FFFFFF);
        int judgeX = tx + tw - 92;
        g.fill(judgeX, ty + 6, judgeX + 2, ty + th - 6, 0xFFFBD36B);
        for (Note n : notes) {
            int nx = (int) (tx + n.x);
            int ny = ty + th / 2 - 6;
            int col = n.eighth ? 0xFFFF9A4A : 0xFFE9DEFF;
            g.fill(nx, ny, nx + 12, ny + 12, col);
        }

        String mini = "x" + combo + "  " + judge;
        int miniW = mc.font.width(mini) + 12;
        g.fill(x + panelW - miniW - 10, y + 50, x + panelW - 10, y + 64, 0xCC1A1A1A);
        g.drawString(mc.font, mini, x + panelW - miniW - 4, y + 53, 0xFFEFE7FF, false);

        int dialogX = tx;
        int dialogY = y + panelH - 94;
        int dialogW = tw;
        int dialogH = 76;
        g.fill(dialogX, dialogY, dialogX + dialogW, dialogY + dialogH, 0xEE181722);
        g.fill(dialogX, dialogY, dialogX + dialogW, dialogY + 1, 0xFF5A4D77);
        g.fill(dialogX + 10, dialogY - 10, dialogX + 66, dialogY + 4, 0xFF2B253C);
        g.drawString(mc.font, "女仆", dialogX + 28, dialogY - 8, 0xFFFF8BCF, false);

        ResourceLocation portrait = (playerPeak >= 1 || maidPeak >= 1) ? PORTRAIT_HOT_SMILE : PORTRAIT_SMILE;
        g.blit(portrait, dialogX + 8, dialogY + 8, 0, 0, 48, 48, 48, 48);
        g.drawString(mc.font, shownLine, dialogX + 64, dialogY + 28, 0xFFF3E9FF, false);

        g.drawString(mc.font, "判定键可在 模组设置 -> 节奏游戏设置 中修改", x + 12, y + panelH - 14, 0xFFD3CAE9, false);
    }

    private static void spawnStep() {
        boolean eighth = ThreadLocalRandom.current().nextFloat() < 0.58f;
        float chance = eighth ? 0.78f : 0.92f;
        if (ThreadLocalRandom.current().nextFloat() < chance) {
            notes.add(new Note(-24f, eighth));
        }
    }

    private static void hit() {
        if (notes.isEmpty()) {
            onMiss();
            return;
        }
        float judgeX = 360f - 92f;
        Note nearest = null;
        float distMin = Float.MAX_VALUE;
        for (Note n : notes) {
            float d = Math.abs(n.x - judgeX);
            if (d < distMin) {
                distMin = d;
                nearest = n;
            }
        }
        if (nearest == null) {
            onMiss();
            return;
        }
        if (distMin <= 28f) {
            notes.remove(nearest);
            onPerfect();
            hits++;
        } else if (distMin <= 68f) {
            notes.remove(nearest);
            onGood();
            hits++;
        } else {
            onMiss();
        }
        checkPeakOrEnd();
    }

    private static void onPerfect() {
        player = clamp(player + 10f + combo * 0.15f, 0f, 100f);
        maid = clamp(maid + (12f + combo * 0.18f) * maidMul(combo), 0f, 100f);
        combo++;
        maxCombo = Math.max(maxCombo, combo);
        missStreak = 0;
        judge = "Perfect";
        setLine("女仆：好棒呀主人，继续保持~");
    }

    private static void onGood() {
        player = clamp(player + 5f + combo * 0.10f, 0f, 100f);
        maid = clamp(maid + (6f + combo * 0.12f) * maidMul(combo), 0f, 100f);
        combo++;
        maxCombo = Math.max(maxCombo, combo);
        missStreak = 0;
        judge = "Good";
        setLine("女仆：嗯嗯，这个节奏很舒服呢~");
    }

    private static void onMiss() {
        float missMul = 1f + Math.min(missStreak * 0.08f, 0.64f);
        player = clamp(player + 4.5f * missMul, 0f, 100f);
        combo = 0;
        missStreak++;
        misses++;
        judge = "Miss";
        if (missStreak >= FAIL_STREAK_LIMIT) {
            player = 100f;
            playerPeak = Math.min(PEAK_LIMIT, playerPeak + 1);
            setLine("女仆：唔...失误太多啦，这次先到这里哦。");
            finishAndSend(false);
            return;
        }
        setLine("女仆：慢一点，跟着节拍来就好呀~");
    }

    private static void checkPeakOrEnd() {
        if (!active) {
            return;
        }
        if (player >= 100f) {
            playerPeak++;
            player = 25f;
            combo = 0;
            setLine("女仆：主人这边到 1/3 啦~");
        }
        if (maid >= 100f) {
            maidPeak++;
            maid = 25f;
            combo = 0;
            setLine("女仆：我这边也到 1/3 了呢~");
        }
        if (playerPeak >= PEAK_LIMIT || maidPeak >= PEAK_LIMIT) {
            boolean success = calcConception();
            finishAndSend(success);
        }
    }

    private static boolean calcConception() {
        if (missStreak >= FAIL_STREAK_LIMIT) {
            return false;
        }
        int judged = hits + misses;
        boolean fullCombo = judged > 0 && misses == 0;
        if (fullCombo) {
            return true;
        }
        float accuracy = hits / (float) Math.max(1, judged);
        float comboFactor = clamp(maxCombo / Math.max(20f, (hits + misses) * 0.55f), 0f, 1f);
        float peakFactor = clamp((playerPeak + maidPeak) / 6f, 0f, 1f);
        float staminaPenalty = clamp(misses / Math.max(8f, (hits + misses) * 0.5f), 0f, 1f) * 0.22f;
        float chance = clamp(0.08f + accuracy * 0.38f + comboFactor * 0.34f + peakFactor * 0.20f - staminaPenalty, 0.02f, 0.96f);
        return ThreadLocalRandom.current().nextFloat() < chance;
    }

    private static void finishAndSend(boolean conceptionSuccess) {
        if (!active || sent || maidId == null) {
            active = false;
            return;
        }
        sent = true;
        PacketDistributor.sendToServer(new SubmitRomanceRhythmPayload(maidId, conceptionSuccess));
        active = false;
    }

    private static float maidMul(int c) {
        if (c >= 30) return 2.0f;
        if (c >= 20) return 1.7f;
        if (c >= 12) return 1.45f;
        if (c >= 6) return 1.2f;
        return 1.0f;
    }

    private static void drawBar(GuiGraphics g, int x, int top, int bottom, float value, int color) {
        g.fill(x, top, x + 14, bottom, 0x66000000);
        int h = Math.max(0, Math.min(bottom - top, Math.round((bottom - top) * value / 100f)));
        g.fill(x, bottom - h, x + 14, bottom, color);
    }

    private static void setLine(String newLine) {
        fullLine = newLine == null ? "" : newLine;
        shownLine = "";
        typeProgress = 0f;
    }

    private static void updateTypewriter(long dtMs) {
        if (shownLine.length() >= fullLine.length()) {
            return;
        }
        typeProgress += (dtMs / 1000f) * TYPEWRITER_CPS;
        int len = Math.min(fullLine.length(), Math.max(0, (int) typeProgress));
        shownLine = fullLine.substring(0, len);
    }

    private static boolean consumeHitInput(Minecraft mc) {
        if (RhythmKeyMappings.RHYTHM_HIT.consumeClick()) {
            return true;
        }

        int key = switch (ModConfigs.rhythmHitKey()) {
            case J -> GLFW.GLFW_KEY_J;
            case K -> GLFW.GLFW_KEY_K;
            case L -> GLFW.GLFW_KEY_L;
            case SEMICOLON -> GLFW.GLFW_KEY_SEMICOLON;
            case SPACE -> GLFW.GLFW_KEY_SPACE;
        };

        boolean down = GLFW.glfwGetKey(mc.getWindow().getWindow(), key) == GLFW.GLFW_PRESS;
        boolean pressed = down && !lastConfigKeyDown;
        lastConfigKeyDown = down;
        return pressed;
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private static final class Note {
        private float x;
        private final boolean eighth;

        private Note(float x, boolean eighth) {
            this.x = x;
            this.eighth = eighth;
        }
    }
}
