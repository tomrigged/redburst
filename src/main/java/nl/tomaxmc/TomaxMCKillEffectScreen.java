package nl.tomaxmc;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.function.Supplier;

public class TomaxMCKillEffectScreen extends Screen {
    private final Screen parent;

    public TomaxMCKillEffectScreen(Screen parent) {
        super(Text.literal("TomaxMC Kill Effect"));
        this.parent = parent;
    }

    private static String onOff(boolean b) {
        return b ? "AAN" : "UIT";
    }

    private void toggle(int x, int y, int w, Supplier<Text> label, Runnable action) {
        addDrawableChild(ButtonWidget.builder(label.get(), b -> {
            action.run();
            TomaxMCKillEffect.save();
            b.setMessage(label.get());
        }).dimensions(x, y, w, 20).build());
    }

    @Override
    protected void init() {
        int colW = 190;
        int gap = 10;
        int left = this.width / 2 - colW - gap / 2;
        int right = this.width / 2 + gap / 2;
        int top = this.height / 2 - 95;
        int step = 25;

        ButtonWidget headerL = ButtonWidget.builder(Text.literal("Mijn effect"), b -> { })
                .dimensions(left, top, colW, 20).build();
        headerL.active = false;
        addDrawableChild(headerL);

        ButtonWidget headerR = ButtonWidget.builder(Text.literal("Server-effecten verbergen"), b -> { })
                .dimensions(right, top, colW, 20).build();
        headerR.active = false;
        addDrawableChild(headerR);

        // Linkerkolom: mijn eigen effect
        toggle(left, top + step, colW, () -> Text.literal("Bij spelers: " + onOff(TomaxMCKillEffect.config.players)),
                () -> TomaxMCKillEffect.config.players = !TomaxMCKillEffect.config.players);
        toggle(left, top + step * 2, colW, () -> Text.literal("Bij mobs: " + onOff(TomaxMCKillEffect.config.mobs)),
                () -> TomaxMCKillEffect.config.mobs = !TomaxMCKillEffect.config.mobs);
        addDrawableChild(ButtonWidget.builder(
                Text.literal("Kleuren (" + TomaxMCKillEffect.config.mix.size() + ")..."),
                b -> { if (this.client != null) this.client.setScreen(new ColorScreen(this)); })
                .dimensions(left, top + step * 3, colW, 20).build());
        toggle(left, top + step * 4, colW, () -> Text.literal("Kill-geluid: " + onOff(TomaxMCKillEffect.config.sound)),
                () -> TomaxMCKillEffect.config.sound = !TomaxMCKillEffect.config.sound);
        toggle(left, top + step * 5, colW, () -> Text.literal("Ook door anderen: " + onOff(TomaxMCKillEffect.config.everyone)),
                () -> TomaxMCKillEffect.config.everyone = !TomaxMCKillEffect.config.everyone);
        toggle(left, top + step * 6, colW, () -> Text.literal("Vuurwerk-vertraging: " + TomaxMCKillEffect.config.delay),
                () -> {
                    int[] d = TomaxMCKillEffect.DELAYS;
                    int next = d[0];
                    for (int v : d) {
                        if (v > TomaxMCKillEffect.config.delay) {
                            next = v;
                            break;
                        }
                    }
                    TomaxMCKillEffect.config.delay = next;
                });

        // Rechterkolom: server-effecten verbergen
        toggle(right, top + step, colW, () -> Text.literal("Vuurwerk: " + onOff(TomaxMCKillEffect.config.hideFireworks)),
                () -> TomaxMCKillEffect.config.hideFireworks = !TomaxMCKillEffect.config.hideFireworks);
        toggle(right, top + step * 2, colW, () -> Text.literal("Bliksem: " + onOff(TomaxMCKillEffect.config.hideLightning)),
                () -> TomaxMCKillEffect.config.hideLightning = !TomaxMCKillEffect.config.hideLightning);
        toggle(right, top + step * 3, colW, () -> Text.literal("Deeltjes: " + onOff(TomaxMCKillEffect.config.hideParticles)),
                () -> TomaxMCKillEffect.config.hideParticles = !TomaxMCKillEffect.config.hideParticles);
        toggle(right, top + step * 4, colW, () -> Text.literal("Geluid: " + onOff(TomaxMCKillEffect.config.hideSounds)),
                () -> TomaxMCKillEffect.config.hideSounds = !TomaxMCKillEffect.config.hideSounds);

        toggle(right, top + step * 5, colW, () -> Text.literal("Verberg-duur: " + TomaxMCKillEffect.config.hideSeconds + " sec"),
                () -> {
                    int[] d = TomaxMCKillEffect.DURATIONS;
                    int next = d[0];
                    for (int v : d) {
                        if (v > TomaxMCKillEffect.config.hideSeconds) {
                            next = v;
                            break;
                        }
                    }
                    TomaxMCKillEffect.config.hideSeconds = next;
                });

        addDrawableChild(ButtonWidget.builder(
                Text.literal("Vrienden (" + TomaxMCKillEffect.config.friends.size() + ")..."),
                b -> { if (this.client != null) this.client.setScreen(new FriendsScreen(this)); })
                .dimensions(right, top + step * 6, colW, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Klaar"), b -> close())
                .dimensions(this.width / 2 - 100, top + step * 7 + 5, 200, 20).build());
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }
}
