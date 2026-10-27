package nl.tomaxmc;

import com.mojang.brigadier.arguments.StringArgumentType;
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
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import java.util.Set;

public class TomaxMCKillEffect implements ClientModInitializer {

    // ===== Instellingen (worden opgeslagen in config/tomaxmckilleffect.json) =====
    public static class Config {
        public boolean players = true;
        public boolean mobs = false;
        public int color = 0;
        public boolean sound = true;
        public boolean everyone = false; // ook doden die niet door jou komen (creeper, valschade, ...)
        public int delay = 2; // vertraging van het vuurwerk t.o.v. het geluid, in ticks (20 ticks = 1 seconde)
        // Effecten die de SERVER bij een kill stuurt verbergen
        public boolean hideFireworks = true;
        public boolean hideLightning = true;
        public boolean hideParticles = true;
        public boolean hideSounds = true;
        public int hideSeconds = 25; // hoe lang na een dood de server-effecten verborgen blijven
        // Vrienden: kills van deze spelers zie je ook
        public boolean friendsEnabled = true;
        public List<String> friends = new ArrayList<>();
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
        return FabricLoader.getInstance().getConfigDir().resolve("tomaxmckilleffect.json");
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
        if (config.friends == null) config.friends = new ArrayList<>();
    }

    public static boolean isFriend(String name) {
        for (String f : config.friends) if (f.equalsIgnoreCase(name)) return true;
        return false;
    }

    public static boolean addFriend(String name) {
        if (name == null || name.isBlank() || isFriend(name)) return false;
        config.friends.add(name.trim());
        save();
        return true;
    }

    public static boolean removeFriend(String name) {
        boolean removed = config.friends.removeIf(f -> f.equalsIgnoreCase(name));
        if (removed) save();
        return removed;
    }

    public static void save() {
        try {
            Files.writeString(configPath(), GSON.toJson(config));
        } catch (Exception ignored) {
        }
    }

    // ===== Kill-detectie =====
    private static final int WINDOW_TICKS = 100; // 5 seconden na je laatste klap

    private static final SoundEvent KILL_SOUND = SoundEvent.of(Identifier.of("tomaxmckilleffect", "kill"));
    private static final Set<Integer> handled = new HashSet<>();
    public static final int[] DELAYS = {0, 1, 2, 3, 4, 6, 8, 10};

    // Plekken waar net iemand doodging: server-deeltjes/geluiden daar worden verborgen
    public static final int[] DURATIONS = {5, 10, 20, 25, 30, 45, 60}; // seconden
    private static final double ZONE_RADIUS = 12.0; // blokken
    private static final List<double[]> zones = new CopyOnWriteArrayList<>(); // {tick, x, y, z}

    public static void addZone(double x, double y, double z) {
        zones.add(new double[]{tick, x, y, z});
    }

    public static boolean inZone(double x, double y, double z) {
        int now = tick;
        int maxAge = Math.max(1, config.hideSeconds) * 20;
        zones.removeIf(zn -> now - zn[0] > maxAge);
        for (double[] zn : zones) {
            double dx = zn[1] - x, dy = zn[2] - y, dz = zn[3] - z;
            if (dx * dx + dy * dy + dz * dz <= ZONE_RADIUS * ZONE_RADIUS) return true;
        }
        return false;
    }

    // Vuurwerk dat nog op zijn moment wacht: {ticksOver, x, y, z}
    private static final List<double[]> pending = new ArrayList<>();

    // Wanneer (tick) elke speler voor het laatst zijn arm zwaaide
    private static final Map<String, Integer> lastSwing = new HashMap<>();
    private record Death(int tick, String name, double x, double y, double z) { }
    private static final List<Death> recentDeaths = new CopyOnWriteArrayList<>();
    private static final double FRIEND_RANGE = 10.0; // blokken tussen vriend en slachtoffer

    private static LivingEntity target;
    private static int hitTick;
    public static volatile int tick;
    private static double lx, ly, lz;
    private static boolean openScreen = false;

    @Override
    public void onInitializeClient() {
        load();

        // Commando: /tomaxmc (instellingen) en /tomaxmc test (proefvuurwerk)
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("tomaxmc")
                        .executes(ctx -> {
                            openScreen = true;
                            return 1;
                        })
                        .then(ClientCommandManager.literal("add")
                                .then(ClientCommandManager.argument("naam", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String n = StringArgumentType.getString(ctx, "naam");
                                            ctx.getSource().sendFeedback(Text.literal(addFriend(n)
                                                    ? n + " toegevoegd aan je vrienden."
                                                    : n + " staat er al in."));
                                            return 1;
                                        })))
                        .then(ClientCommandManager.literal("remove")
                                .then(ClientCommandManager.argument("naam", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String n = StringArgumentType.getString(ctx, "naam");
                                            ctx.getSource().sendFeedback(Text.literal(removeFriend(n)
                                                    ? n + " verwijderd."
                                                    : n + " staat niet in je lijst."));
                                            return 1;
                                        })))
                        .then(ClientCommandManager.literal("list").executes(ctx -> {
                            ctx.getSource().sendFeedback(Text.literal(config.friends.isEmpty()
                                    ? "Je hebt nog geen vrienden toegevoegd."
                                    : "Vrienden: " + String.join(", ", config.friends)));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("test").executes(ctx -> {
                            MinecraftClient mc = MinecraftClient.getInstance();
                            if (mc.player != null) {
                                celebrate(mc.player.getX(), mc.player.getY() + 1.5, mc.player.getZ());
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

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            tick++;
            if (openScreen) {
                openScreen = false;
                client.setScreen(new TomaxMCKillEffectScreen(null));
            }
            if (client.world == null || client.player == null) {
                handled.clear();
                pending.clear();
                zones.clear();
                lastSwing.clear();
                recentDeaths.clear();
                target = null;
                return;
            }

            // Onthoud wanneer spelers zwaaien (om vrienden-kills te herkennen)
            if (config.friendsEnabled && !config.friends.isEmpty()) {
                for (PlayerEntity p : client.world.getPlayers()) {
                    if (p.handSwinging || p.handSwingProgress > 0.0f) {
                        lastSwing.put(p.getName().getString().toLowerCase(), tick);
                    }
                }
            }
            recentDeaths.removeIf(d -> tick - d.tick() > 100);

            // Vuurwerk dat op zijn vertraging wacht
            Iterator<double[]> it = pending.iterator();
            while (it.hasNext()) {
                double[] b = it.next();
                b[0] -= 1;
                if (b[0] <= 0) {
                    explode(b[1], b[2], b[3]);
                    it.remove();
                }
            }

            if (target != null && tick - hitTick > WINDOW_TICKS) target = null;
            if (target != null && target.getHealth() > 0.0f) {
                lx = target.getX();
                ly = target.getY() + target.getHeight() / 2.0;
                lz = target.getZ();
            }

            // Vergeet entities die niet meer bestaan
            handled.removeIf(id -> client.world.getEntityById(id) == null);

            // Zoek alles wat nu doodgaat
            for (Entity e : client.world.getEntities()) {
                if (!(e instanceof LivingEntity living)) continue;
                if (living == client.player) continue;
                if (living.getHealth() > 0.0f) continue;
                if (!handled.add(living.getId())) continue; // al afgehandeld
                if (living instanceof PlayerEntity) {
                    addZone(living.getX(), living.getY() + living.getHeight() / 2.0, living.getZ());
                }

                boolean mine = (living == target);
                boolean isPlayer = living instanceof PlayerEntity;
                boolean typeOn = (isPlayer && config.players) || (!isPlayer && config.mobs);
                if (mine) target = null;
                if (!typeOn) continue;
                boolean friendKill = !mine && !config.everyone && killedByFriend(client, living);
                if (!mine && !config.everyone && !friendKill) {
                    // Bewaar voor de chat-reserve (bijv. vriend schiet met een boog)
                    recentDeaths.add(new Death(tick, living.getName().getString(),
                            living.getX(), living.getY() + living.getHeight() / 2.0, living.getZ()));
                    continue;
                }

                celebrate(living.getX(), living.getY() + living.getHeight() / 2.0, living.getZ());
            }
        });

        // Reserve: sommige servers geven geen gezondheid door, dan lezen we de kill-melding
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (overlay) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null) return;
            String text = message.getString();

            // Vrienden: kill-melding bevat een vriend en een recent slachtoffer
            if (config.friendsEnabled && !config.friends.isEmpty() && !recentDeaths.isEmpty()) {
                String lower = text.toLowerCase();
                for (Death d : recentDeaths) {
                    if (tick - d.tick() > 80) continue;
                    String victim = d.name().toLowerCase();
                    if (!lower.contains(victim)) continue;
                    for (String f : config.friends) {
                        String fl = f.toLowerCase();
                        if (fl.equals(victim) || !lower.contains(fl)) continue;
                        recentDeaths.remove(d);
                        celebrate(d.x(), d.y(), d.z());
                        return;
                    }
                }
            }

            if (target == null) return;
            if (tick - hitTick > 60) return;
            String me = mc.player.getName().getString();
            String them = target.getName().getString();
            if (text.contains(me) && text.contains(them)) {
                handled.add(target.getId());
                celebrate(lx, ly, lz);
                target = null;
            }
        });
    }

    private static boolean killedByFriend(MinecraftClient client, LivingEntity victim) {
        if (!config.friendsEnabled || config.friends.isEmpty() || client.world == null) return false;
        for (PlayerEntity p : client.world.getPlayers()) {
            if (p == victim || p == client.player) continue;
            String name = p.getName().getString();
            if (!isFriend(name)) continue;
            Integer swung = lastSwing.get(name.toLowerCase());
            if (swung == null || tick - swung > 10) continue;
            if (p.squaredDistanceTo(victim) <= FRIEND_RANGE * FRIEND_RANGE) return true;
        }
        return false;
    }

    private static void celebrate(double x, double y, double z) {
        // Geluid start meteen; het vuurwerk volgt na de ingestelde vertraging,
        // zodat de klap en het geluid samen te horen zijn.
        if (config.sound) {
            MinecraftClient mc = MinecraftClient.getInstance();
            mc.getSoundManager().play(PositionedSoundInstance.ui(KILL_SOUND, 1.0f, 1.0f));
        }
        if (config.delay <= 0) {
            explode(x, y, z);
        } else {
            pending.add(new double[]{config.delay, x, y, z});
        }
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
