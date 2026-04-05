package com.example.maidmarriage.client;

import com.example.maidmarriage.MaidMarriageMod;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class RhythmKeyMappings {
    public static final KeyMapping RHYTHM_HIT = new KeyMapping(
            "key.maidmarriage.rhythm_hit",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            "key.categories.maidmarriage"
    );

    private RhythmKeyMappings() {
    }
}

