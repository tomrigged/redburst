package nl.redburst;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.type.FireworkExplosionComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class RedBurst implements ClientModInitializer {

    // ===== Instellingen (worden opgeslagen in config/redburst.json) =====
    public static class Config {
        public boolean players = true;
        public boolean mobs = false;
        public int color = 0;
    }

    public static Config config = new Config();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final String[] COLOR_NAMES = {
            "Rood", "Blauw", "Groen", "Geel", "Oranje", "Paars", "Roze", "Wit", "Regenboog"
    };
    private static final int[][] COLORS = {
            {0xFF0000, 0xCC0000, 0xFF3300},
            {0x0044FF, 0x00AAFF, 0x2222CC},
            {0x00FF00, 0x22CC22, 0x88FF00},
            {0xFFFF00, 0xFFCC00, 0xFFEE66},
            {0xFF8800, 0xFF5500, 0xFFAA33},
            {0xAA00FF, 0x7700CC, 0xDD66FF},
            {0xFF55CC, 0xFF88DD, 0xFF2299},
            {0xFFFFFF, 0xDDDDDD, 0xAAAAFF},
            {0xFF0000, 0xFFAA00, 0xFFFF00, 0x00FF00, 0x00AAFF, 0xAA00FF}
    };
    private static final int[] FADES = {
            0x550000, 0x000055, 0x003300, 0x665500, 0x552200, 0x330055, 0x550033, 0x555555, 0xFFFFFF
    };

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("redburst.json");
    }

    public static void load() {
        try {
            Path p = configPath();
            if (Files.exists(p)) {
                Config c = GSON.fromJson(Files.readString(p), Config.class);
                if (c != null) config = c;
            }
        } catch (Exception ignored) {
        }
        if (config.color < 0 || config.color >= COLOR_NAMES.length) config.color = 0;
    }

    public static void save() {
        try {
            Files.writeString(configPath(), GSON.toJson(config));
        } catch (Exception ignored) {
        }
    }

    // ===== Kill-detectie =====
    private static final int WINDOW_TICKS = 100; // 5 seconden na je laatste klap

    private static LivingEntity target;
    private static int hitTick;
    private static int tick;
    private static double lx, ly, lz;
    private static boolean openScreen = false;

    @Override
    public void onInitializeClient() {
        load();

        // Commando: /redburst (instellingen) en /redburst test (proefvuurwerk)
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("redburst")
                        .executes(ctx -> {
                            openScreen = true;
                            return 1;
                        })
                        .then(ClientCommandManager.literal("test").executes(ctx -> {
                            MinecraftClient mc = MinecraftClient.getInstance();
                            if (mc.player != null) {
                                explode(mc.player.getX(), mc.player.getY() + 1.5, mc.player.getZ());
                            }
                            return 1;
                        }))));

        // Onthoud wie jij het laatst hebt geslagen
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient() && entity instanceof LivingEntity living) {
                boolean isPlayer = living instanceof PlayerEntity;
                if ((isPlayer && config.players) || (!isPlayer && config.mobs)) {
                    target = living;
                    hitTick = tick;
                    lx = living.getX();
                    ly = living.getY() + living.getHeight() / 2.0;
                    lz = living.getZ();
                }
            }
            return ActionResult.PASS;
        });

        // Kijk elke tick of je doelwit dood is
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            tick++;
            if (openScreen) {
                openScreen = false;
                client.setScreen(new RedBurstScreen(null));
            }
            if (target == null || client.world == null) return;
            if (tick - hitTick > WINDOW_TICKS) {
                target = null;
                return;
            }
            if (target.getHealth() <= 0.0f) {
                explode(lx, ly, lz);
                target = null;
                return;
            }
            lx = target.getX();
            ly = target.getY() + target.getHeight() / 2.0;
            lz = target.getZ();
        });

        // Reserve: sommige servers geven geen gezondheid door, dan lezen we de kill-melding
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (overlay || target == null) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null) return;
            if (tick - hitTick > 60) return;
            String text = message.getString();
            String me = mc.player.getName().getString();
            String them = target.getName().getString();
            if (text.contains(me) && text.contains(them)) {
                explode(lx, ly, lz);
                target = null;
            }
        });
    }

    // ===== Het vuurwerk =====
    private static void explode(double x, double y, double z) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        int i = Math.max(0, Math.min(config.color, COLORS.length - 1));
        IntList colors = new IntArrayList(COLORS[i]);
        IntList fade = new IntArrayList(new int[]{FADES[i]});

        FireworkExplosionComponent bol = new FireworkExplosionComponent(
                FireworkExplosionComponent.Type.LARGE_BALL, colors, fade, true, true);
        FireworkExplosionComponent burst = new FireworkExplosionComponent(
                FireworkExplosionComponent.Type.BURST, colors, fade, true, false);
        FireworkExplosionComponent ster = new FireworkExplosionComponent(
                FireworkExplosionComponent.Type.STAR, colors, fade, false, true);

        mc.world.addFireworkParticle(x, y, z, 0, 0, 0, List.of(bol, burst, ster));
    }
}
