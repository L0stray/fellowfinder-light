package com.example.fellowfinderlight.hud;

import com.example.fellowfinderlight.network.PlayerLocationPayload;
import com.example.fellowfinderlight.util.ColorUtils;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.MathHelper;

import java.util.*;

public class CompassLightRenderer implements HudRenderCallback {

    private static final Map<UUID, PlayerLocationPayload.PlayerData> remotePlayers = new HashMap<>();

    static {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> remotePlayers.clear());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> remotePlayers.clear());
    }

    public static void updateRemotePlayers(Map<UUID, PlayerLocationPayload.PlayerData> players) {
        remotePlayers.clear();
        remotePlayers.putAll(players);
    }

    @Override
    public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden) return;

        int screenWidth = client.getWindow().getScaledWidth();
        int compassY = 10;
        int compassWidth = 240;
        int compassCenterX = screenWidth / 2;
        int compassLeftX = compassCenterX - compassWidth / 2;

        // 罗盘背景
        context.fill(compassLeftX, compassY, compassLeftX + compassWidth, compassY + 10, 0x80000000);
        // 中心标记（1像素宽，与箭头尖对齐）
        context.fill(compassCenterX, compassY, compassCenterX + 1, compassY + 10, 0xFFFFFFFF);

        float playerYaw = client.player.getYaw();
        float maxAngle = 120;
        String currentDim = client.player.getWorld().getRegistryKey().getValue().toString();

        Map<UUID, double[]> visiblePlayers = new HashMap<>();
        Map<UUID, String> playerNames = new HashMap<>();

        // 无条件收集本地坐标
        if (client.world != null) {
            for (PlayerEntity player : client.world.getPlayers()) {
                if (player == client.player) continue;
                double dx = player.getX() - client.player.getX();
                double dy = player.getY() - client.player.getY();
                double dz = player.getZ() - client.player.getZ();
                visiblePlayers.put(player.getUuid(), new double[]{dx, dy, dz});
                playerNames.put(player.getUuid(), player.getName().getString());
            }
        }

        UUID selfUUID = client.player.getUuid();
        for (PlayerLocationPayload.PlayerData data : remotePlayers.values()) {
            if (data.uuid().equals(selfUUID)) continue;
            if (!data.dimension().equals(currentDim)) continue;
            if (visiblePlayers.containsKey(data.uuid())) continue;
            double dx = data.x() - client.player.getX();
            double dy = data.y() - client.player.getY();
            double dz = data.z() - client.player.getZ();
            visiblePlayers.put(data.uuid(), new double[]{dx, dy, dz});
            playerNames.put(data.uuid(), data.name());
        }

        for (Map.Entry<UUID, double[]> entry : visiblePlayers.entrySet()) {
            UUID uuid = entry.getKey();
            double[] coords = entry.getValue();
            double dx = coords[0];
            double dy = coords[1];
            double dz = coords[2];

            // 可见性过滤
            PlayerEntity targetPlayer = client.world != null ? client.world.getPlayerByUuid(uuid) : null;
            if (targetPlayer != null) {
                if (targetPlayer.isSneaking()) continue;
                if (targetPlayer.isInvisible() || targetPlayer.hasStatusEffect(StatusEffects.INVISIBILITY)) continue;
                if (targetPlayer.isSpectator()) continue;
                ItemStack helmet = targetPlayer.getEquippedStack(EquipmentSlot.HEAD);
                Item headItem = helmet.getItem();
                if (headItem == Items.CARVED_PUMPKIN ||
                        headItem == Items.PLAYER_HEAD ||
                        headItem == Items.SKELETON_SKULL ||
                        headItem == Items.WITHER_SKELETON_SKULL ||
                        headItem == Items.ZOMBIE_HEAD ||
                        headItem == Items.CREEPER_HEAD ||
                        headItem == Items.PIGLIN_HEAD ||
                        headItem == Items.DRAGON_HEAD) {
                    continue;
                }
            } else {
                PlayerLocationPayload.PlayerData data = remotePlayers.get(uuid);
                if (data != null) {
                    if (data.isSneaking()) continue;
                    if (data.isInvisible()) continue;
                    if (data.isSpectator()) continue;
                    if (data.isMasked()) continue;
                }
            }

            float angleToTarget = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float relativeYaw = angleToTarget - playerYaw;
            relativeYaw = ((relativeYaw + 180) % 360 + 360) % 360 - 180;

            if (Math.abs(relativeYaw) > maxAngle) continue;

            float percent = relativeYaw / maxAngle;
            int markerX = (int) (compassCenterX + percent * (compassWidth / 2.0));
            markerX = MathHelper.clamp(markerX, compassLeftX, compassLeftX + compassWidth - 1);

            int color = ColorUtils.getColorFromUuid(uuid);
            String name = playerNames.getOrDefault(uuid, "?");

            // 玩家ID
            int idY = compassY + (-8);
            int idWidth = client.textRenderer.getWidth(name);
            int idX = markerX - idWidth / 2;
            renderOutlinedText(context, client, name, idX, idY, color);

            // 高度箭头
            String arrow;
            if (dy > 1.0) arrow = "↑";
            else if (dy < -1.0) arrow = "↓";
            else arrow = "I";
            int arrowY = compassY + 2;
            int arrowWidth = client.textRenderer.getWidth(arrow);
            int arrowX = markerX - arrowWidth / 2 + 1;
            renderOutlinedText(context, client, arrow, arrowX, arrowY, color);

            // 水平距离
            double horizontalDist = Math.sqrt(dx*dx + dz*dz);
            String distanceText = formatDistance(horizontalDist);
            int distY = compassY + 11;
            renderScaledText(context, client, distanceText, markerX, distY, color, 0.7f);
        }
    }

    private String formatDistance(double distance) {
        if (distance >= 1000) return String.format("%.1fkm", distance / 1000.0);
        else return String.format("%.1fm", distance);
    }

    private void renderScaledText(DrawContext context, MinecraftClient client, String text,
                                  int centerX, int baseY, int color, float scale) {
        int textWidth = client.textRenderer.getWidth(text);
        int x = centerX - textWidth / 2;
        int y = baseY;
        context.drawText(client.textRenderer, text, x, y, color, false);
    }

    private void renderOutlinedText(DrawContext context, MinecraftClient client, String text,
                                    int x, int y, int color) {
        int outlineColor = 0xFF000000;
        context.drawText(client.textRenderer, text, x - 1, y, outlineColor, false);
        context.drawText(client.textRenderer, text, x + 1, y, outlineColor, false);
        context.drawText(client.textRenderer, text, x, y - 1, outlineColor, false);
        context.drawText(client.textRenderer, text, x, y + 1, outlineColor, false);
        context.drawText(client.textRenderer, text, x, y, color, false);
    }
}