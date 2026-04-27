package com.example.fellowfinderlight.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.*;

public record PlayerLocationPayload(Map<UUID, PlayerData> players) implements CustomPayload {

    public static final CustomPayload.Id<PlayerLocationPayload> ID =
            new CustomPayload.Id<>(Identifier.of("fellowfinderlight", "player_locations"));

    public static final PacketCodec<PacketByteBuf, PlayerLocationPayload> CODEC =
            PacketCodec.of(PlayerLocationPayload::write, PlayerLocationPayload::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    private void write(PacketByteBuf buf) {
        buf.writeMap(
                players,
                (b, uuid) -> b.writeUuid(uuid),
                (b, data) -> data.write(b)
        );
    }

    private static PlayerLocationPayload read(PacketByteBuf buf) {
        Map<UUID, PlayerData> map = buf.readMap(
                b -> b.readUuid(),
                PlayerData::new
        );
        return new PlayerLocationPayload(map);
    }

    public record PlayerData(UUID uuid, String name, double x, double y, double z, String dimension, byte flags) {
        // 状态标志位定义
        private static final byte FLAG_SNEAKING   = 1 << 0; // 1
        private static final byte FLAG_INVISIBLE  = 1 << 1; // 2
        private static final byte FLAG_SPECTATOR  = 1 << 2; // 4
        private static final byte FLAG_MASKED     = 1 << 3; // 8 (戴南瓜或玩家头)

        public PlayerData(PacketByteBuf buf) {
            this(
                    buf.readUuid(),
                    buf.readString(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readString(),
                    buf.readByte() // 读取状态字节
            );
        }

        public void write(PacketByteBuf buf) {
            buf.writeUuid(uuid);
            buf.writeString(name);
            buf.writeDouble(x);
            buf.writeDouble(y);
            buf.writeDouble(z);
            buf.writeString(dimension);
            buf.writeByte(flags); // 写入状态字节
        }

        // 便捷判断方法
        public boolean isSneaking()  { return (flags & FLAG_SNEAKING) != 0; }
        public boolean isInvisible() { return (flags & FLAG_INVISIBLE) != 0; }
        public boolean isSpectator() { return (flags & FLAG_SPECTATOR) != 0; }
        public boolean isMasked()    { return (flags & FLAG_MASKED) != 0; }
    }
}