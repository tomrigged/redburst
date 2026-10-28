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
        public int fadeMode = 0; // hoe het vuurwerk wegfade (zie FADE_NAMES)
        public int color = 0; // oud (alleen voor oude configs)
        public Map<Integer, Integer> weights = null; // verhouding per kleur in procenten (palet-index -> %)
        public List<Integer> mix = null; // gekozen kleuren (index in PALETTE)
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

    // Kleurenpalet: naam + RGB. Je kunt er meerdere tegelijk aanzetten om te mixen.
    public static final String[] PALETTE_NAMES = {
            "Rood", "Lichtrood", "Oranje", "Perzik", "Geel", "Lichtgeel",
            "Limoen", "Groen", "Mint", "Turquoise", "Cyaan", "Lichtblauw",
            "Blauw", "Donkerblauw", "Paars", "Lila", "Roze", "Babyroze",
            "Wit", "Zilver"
    };
    public static final int[] PALETTE = {
            0xFF0000, 0xFF7777, 0xFF8800, 0xFFBB88, 0xFFFF00, 0xFFFF99,
            0x88FF00, 0x00DD00, 0x99FFAA, 0x00DDBB, 0x00FFFF, 0x88CCFF,
            0x0044FF, 0x1111AA, 0xAA00FF, 0xCC99FF, 0xFF55CC, 0xFFAADD,
            0xFFFFFF, 0xBBBBBB
    };
    public static final String[] FADE_NAMES = {"Zelfde kleur", "Lichter", "Donkerder", "Wit", "Geen"};
    public static final int[] RAINBOW = {0, 2, 4, 7, 11, 14};
    // Oude kleurkeuze (0-8) omzetten naar het nieuwe palet
    private static final int[][] OLD_TO_NEW = {
            {0}, {12, 11}, {7, 6}, {4, 5}, {2, 3}, {14, 15}, {16, 17}, {18, 19}, RAINBOW
    };

    public static boolean hasColor(int idx) {
        return config.mix != null && config.mix.contains(idx);
    }

    public static void toggleColor(int idx) {
        if (config.mix == null) config.mix = new ArrayList<>();
        if (!config.mix.remove(Integer.valueOf(idx))) config.mix.add(idx);
        config.weights = null; // verhouding opnieuw gelijk verdelen
        save();
    }

    public static void setColors(int... idx) {
        config.mix = new ArrayList<>();
        for (int i : idx) config.mix.add(i);
        config.weights = null;
        save();
    }

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
        if (config.mix == null) {
            int old = Math.max(0, Math.min(config.color, OLD_TO_NEW.length - 1));
            config.mix = new ArrayList<>();
            for (int i : OLD_TO_NEW[old]) config.mix.add(i);
        }
        if (config.fadeMode < 0 || config.fadeMode >= FADE_NAMES.length) config.fadeMode = 0;
        config.mix.removeIf(i -> i == null || i < 0 || i >= PALETTE.length);
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

    // Gevechtsgeluiden (hits, schade, pijlen) worden nooit verborgen, ook niet vlak bij een dood
    public static boolean isCombatSound(String id) {
        if (id == null) return false;
        String s = id.toLowerCase();
        return s.contains(".attack") || s.contains(".hurt") || s.contains(".hit")
                || s.contains(".damage") || s.contains("knockback") || s.contains(".crit")
                || s.contains(".sweep") || s.contains("shield") || s.contains("bow.shoot");
    }

    // Vuurwerk dat nog op zijn moment wacht: {ticksOver, x, y, z}
    private static final List<double[]> pending = new ArrayList<>();

    // Wanneer (tick) elke speler voor het laatst zijn arm zwaaide
    private static final Map<String, Integer> lastSwing = new HashMap<>();
    private record Death(int tick, String name, double x, double y, double z) { }
    private static final List<Death> recentDeaths = new CopyOnWriteArrayList<>();
    private static final double FRIEND_RANGE = 14.0; // blokken tussen vriend en slachtoffer

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
                        .then(ClientCommandManager.literal("debug").executes(ctx -> {
                            debug = !debug;
                            ctx.getSource().sendFeedback(Text.literal("Debug: " + (debug ? "AAN" : "UIT")));
                            return 1;
                        }))
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
                recentChats.clear();
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
                if (living.getHealth() > 0.0f) continue;
                handleDeath(client, living);
            }
        });

        // Reserve: sommige servers geven geen gezondheid door, dan lezen we de kill-melding
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (overlay) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null) return;
            String text = message.getString();
            recentChats.add(new Chat(tick, text.toLowerCase()));
            recentChats.removeIf(c -> tick - c.tick() > 120);

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

    public static boolean debug = false;
    private record Chat(int tick, String lower) { }
    private static final List<Chat> recentChats = new CopyOnWriteArrayList<>();

    private static void dbg(MinecraftClient client, String msg) {
        if (debug && client.inGameHud != null) {
            client.inGameHud.getChatHud().addMessage(Text.literal("[TomaxMC] " + msg));
        }
    }

    /** Wordt aangeroepen als er iets doodgaat (via gezondheid of via het dood-pakket van de server). */
    public static void handleDeath(MinecraftClient client, LivingEntity living) {
        if (client.world == null || client.player == null) return;
        if (living == client.player) return;
        if (!handled.add(living.getId())) return; // al afgehandeld
        if (living instanceof PlayerEntity) {
            addZone(living.getX(), living.getY() + living.getHeight() / 2.0, living.getZ());
        }

        boolean mine = (living == target);
        boolean isPlayer = living instanceof PlayerEntity;
        boolean typeOn = (isPlayer && config.players) || (!isPlayer && config.mobs);
        if (mine) target = null;
        if (!typeOn) {
            dbg(client, "Dood: " + living.getName().getString() + " (type staat uit)");
            return;
        }
        boolean friendKill = !mine && !config.everyone && killedByFriend(client, living);
        dbg(client, "Dood: " + living.getName().getString() + " | van mij: " + mine + " | vriend: " + friendKill);
        if (!mine && !config.everyone && !friendKill) {
            // Bewaar voor de chat-reserve (bijv. vriend schiet met een boog)
            recentDeaths.add(new Death(tick, living.getName().getString(),
                    living.getX(), living.getY() + living.getHeight() / 2.0, living.getZ()));
            return;
        }
        celebrate(living.getX(), living.getY() + living.getHeight() / 2.0, living.getZ());
    }

    private static boolean killedByFriend(MinecraftClient client, LivingEntity victim) {
        if (!config.friendsEnabled || config.friends.isEmpty() || client.world == null) return false;
        String victimName = victim.getName().getString().toLowerCase();

        // 1) Een vriend was in de buurt en zwaaide net
        for (PlayerEntity p : client.world.getPlayers()) {
            if (p == victim || p == client.player) continue;
            String name = p.getName().getString();
            if (!isFriend(name)) continue;
            Integer swung = lastSwing.get(name.toLowerCase());
            boolean swingingNow = p.handSwinging || p.handSwingProgress > 0.0f;
            boolean recent = swingingNow || (swung != null && tick - swung <= 25);
            if (recent && p.squaredDistanceTo(victim) <= FRIEND_RANGE * FRIEND_RANGE) return true;
        }

        // 2) Er kwam net een chat-melding met een vriend en dit slachtoffer
        for (Chat c : recentChats) {
            if (tick - c.tick() > 100) continue;
            if (!c.lower().contains(victimName)) continue;
            for (String f : config.friends) {
                String fl = f.toLowerCase();
                if (!fl.equals(victimName) && c.lower().contains(fl)) return true;
            }
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

        IntList colors = new IntArrayList();
        IntList fade = new IntArrayList();
        List<Integer> chosen = (config.mix == null || config.mix.isEmpty()) ? List.of(0) : config.mix;

        // Verhouding: een kleur met meer % komt vaker voor in de lijst (de server kiest willekeurig uit die lijst)
        final int SLOTS = 40;
        int n = chosen.size();
        int[] slots = new int[n];
        double sum = 0;
        boolean useWeights = config.weights != null && n > 1;
        if (useWeights) {
            for (int i = 0; i < n; i++) {
                Integer v = config.weights.get(chosen.get(i));
                if (v == null) { useWeights = false; break; }
                sum += Math.max(0, v);
            }
            if (sum <= 0) useWeights = false;
        }
        int total = 0;
        for (int i = 0; i < n; i++) {
            if (useWeights) {
                int v = Math.max(0, config.weights.get(chosen.get(i)));
                slots[i] = v == 0 ? 0 : Math.max(1, (int) Math.round(SLOTS * v / sum));
            } else {
                slots[i] = 1;
            }
            total += slots[i];
        }
        if (total == 0) java.util.Arrays.fill(slots, 1);

        for (int i = 0; i < n; i++) {
            int c = PALETTE[chosen.get(i)];
            int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, bl = c & 0xFF;
            for (int k = 0; k < slots[i]; k++) {
                colors.add(c);
                switch (config.fadeMode) {
                    case 0 -> fade.add(c); // zelfde kleur
                    case 1 -> fade.add((((r + 255) / 2) << 16) | (((g + 255) / 2) << 8) | ((bl + 255) / 2)); // lichter
                    case 2 -> fade.add(((int) (r * 0.4) << 16) | ((int) (g * 0.4) << 8) | (int) (bl * 0.4)); // donkerder
                    case 3 -> { if (!fade.contains(0xFFFFFF)) fade.add(0xFFFFFF); } // wit
                    default -> { } // geen: alleen uitdoven
                }
            }
        }

        FireworkExplosionComponent bol = new FireworkExplosionComponent(
                FireworkExplosionComponent.Type.LARGE_BALL, colors, fade, true, true);
        FireworkExplosionComponent burst = new FireworkExplosionComponent(
                FireworkExplosionComponent.Type.BURST, colors, fade, true, false);
        FireworkExplosionComponent ster = new FireworkExplosionComponent(
                FireworkExplosionComponent.Type.STAR, colors, fade, false, true);

        mc.world.addFireworkParticle(x, y, z, 0, 0, 0, List.of(bol, burst, ster));
    }
}
