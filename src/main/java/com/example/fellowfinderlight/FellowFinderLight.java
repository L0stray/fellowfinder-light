package com.example.fellowfinderlight;

import com.example.fellowfinderlight.hud.CompassLightRenderer;
import com.example.fellowfinderlight.network.PlayerLocationPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class FellowFinderLight implements ModInitializer, ClientModInitializer {
    public static final String MOD_ID = "fellowfinderlight";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private int tickCounter = 0;
    private static final int BROADCAST_INTERVAL = 20;

    // 所有能隐藏玩家的头部装备（遵循原版定位栏规则）
    private static final Set<Item> HIDING_HEADWEAR = Set.of(
            Items.CARVED_PUMPKIN,
            Items.PLAYER_HEAD,
            Items.SKELETON_SKULL,
            Items.WITHER_SKELETON_SKULL,
            Items.ZOMBIE_HEAD,
            Items.CREEPER_HEAD,
            Items.PIGLIN_HEAD,
            Items.DRAGON_HEAD
    );

    @Override
    public void onInitialize() {
        LOGGER.info("[FellowFinderLight] 服务端已加载，将自动为局域网主机开启广播。");
        PayloadTypeRegistry.playS2C().register(PlayerLocationPayload.ID, PlayerLocationPayload.CODEC);
        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);
    }

    private void onServerTick(MinecraftServer server) {
        tickCounter++;
        if (tickCounter >= BROADCAST_INTERVAL) {
            tickCounter = 0;

            Map<UUID, PlayerLocationPayload.PlayerData> playerDataMap = new HashMap<>();
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                String dimension = player.getWorld().getRegistryKey().getValue().toString();

                byte flags = 0;
                if (player.isSneaking()) flags |= 1;
                if (player.isInvisible() || player.hasStatusEffect(StatusEffects.INVISIBILITY)) flags |= 2;
                if (player.isSpectator()) flags |= 4;
                ItemStack helmet = player.getEquippedStack(EquipmentSlot.HEAD);
                if (HIDING_HEADWEAR.contains(helmet.getItem())) flags |= 8;

                playerDataMap.put(
                        player.getUuid(),
                        new PlayerLocationPayload.PlayerData(
                                player.getUuid(),
                                player.getName().getString(),
                                player.getX(),
                                player.getY(),
                                player.getZ(),
                                dimension,
                                flags
                        )
                );
            }

            if (playerDataMap.isEmpty()) return;

            PlayerLocationPayload payload = new PlayerLocationPayload(playerDataMap);
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }

    @Override
    public void onInitializeClient() {
        LOGGER.info("[FellowFinderLight] 客户端已加载，开始渲染罗盘。");
        HudRenderCallback.EVENT.register(new CompassLightRenderer());
        ClientPlayNetworking.registerGlobalReceiver(PlayerLocationPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                CompassLightRenderer.updateRemotePlayers(payload.players());
            });
        });
    }
}