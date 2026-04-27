package com.example.fellowfinderlight.util;

import java.awt.Color;
import java.util.UUID;

public class ColorUtils {
    /**
     * 根据 UUID 生成一个固定的、足够明亮的颜色（ARGB 格式，Alpha 固定为 255）。
     * 算法：取 UUID 的 hashCode，映射到色相环 0~360°，然后检查感知亮度。
     * 如果颜色太暗（人眼感知亮度低于 128），自动混合白色提亮，确保在深色背景上清晰可辨。
     */
    public static int getColorFromUuid(UUID uuid) {
        int hash = uuid.hashCode();
        float hue = (Math.abs(hash) % 360) / 360.0f;
        // 饱和度 0.8，亮度 0.9，让颜色鲜艳但不刺眼
        int rgb = Color.HSBtoRGB(hue, 0.8f, 0.9f);

        // 提取 RGB 分量，计算感知亮度
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        // 感知亮度公式：人眼对绿色最敏感，红色次之，蓝色最不敏感
        double luminance = 0.299 * r + 0.587 * g + 0.114 * b;

        // 如果感知亮度低于 128（中灰），就把整个颜色提亮
        if (luminance < 128) {
            // 混合白色，使颜色变亮但保留色相和饱和度
            r = Math.min(255, r + 128);
            g = Math.min(255, g + 128);
            b = Math.min(255, b + 128);
            rgb = (r << 16) | (g << 8) | b;
        }

        return 0xFF000000 | rgb; // 添加完全不透明的 Alpha 通道
    }
}