package nl.redburst;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.function.Supplier;

public class RedBurstScreen extends Screen {
    private final Screen parent;

    public RedBurstScreen(Screen parent) {
        super(Text.literal("Red Burst"));
        this.parent = parent;
    }

    private static String onOff(boolean b) {
        return b ? "AAN" : "UIT";
    }

    private void toggle(int x, int y, int w, Supplier<Text> label, Runnable action) {
        addDrawableChild(ButtonWidget.builder(label.get(), b -> {
            action.run();
            RedBurst.save();
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
        toggle(left, top + step, colW, () -> Text.literal("Bij spelers: " + onOff(RedBurst.config.players)),
                () -> RedBurst.config.players = !RedBurst.config.players);
        toggle(left, top + step * 2, colW, () -> Text.literal("Bij mobs: " + onOff(RedBurst.config.mobs)),
                () -> RedBurst.config.mobs = !RedBurst.config.mobs);
        toggle(left, top + step * 3, colW, () -> Text.literal("Kleur: " + RedBurst.COLOR_NAMES[RedBurst.config.color]),
                () -> RedBurst.config.color = (RedBurst.config.color + 1) % RedBurst.COLOR_NAMES.length);
        toggle(left, top + step * 4, colW, () -> Text.literal("Kill-geluid: " + onOff(RedBurst.config.sound)),
                () -> RedBurst.config.sound = !RedBurst.config.sound);
        toggle(left, top + step * 5, colW, () -> Text.literal("Ook door anderen: " + onOff(RedBurst.config.everyone)),
                () -> RedBurst.config.everyone = !RedBurst.config.everyone);
        toggle(left, top + step * 6, colW, () -> Text.literal("Vuurwerk-vertraging: " + RedBurst.config.delay),
                () -> {
                    int[] d = RedBurst.DELAYS;
                    int next = d[0];
                    for (int v : d) {
                        if (v > RedBurst.config.delay) {
                            next = v;
                            break;
                        }
                    }
                    RedBurst.config.delay = next;
                });

        // Rechterkolom: server-effecten verbergen
        toggle(right, top + step, colW, () -> Text.literal("Vuurwerk: " + onOff(RedBurst.config.hideFireworks)),
                () -> RedBurst.config.hideFireworks = !RedBurst.config.hideFireworks);
        toggle(right, top + step * 2, colW, () -> Text.literal("Bliksem: " + onOff(RedBurst.config.hideLightning)),
                () -> RedBurst.config.hideLightning = !RedBurst.config.hideLightning);
        toggle(right, top + step * 3, colW, () -> Text.literal("Deeltjes: " + onOff(RedBurst.config.hideParticles)),
                () -> RedBurst.config.hideParticles = !RedBurst.config.hideParticles);
        toggle(right, top + step * 4, colW, () -> Text.literal("Geluid: " + onOff(RedBurst.config.hideSounds)),
                () -> RedBurst.config.hideSounds = !RedBurst.config.hideSounds);

        toggle(right, top + step * 5, colW, () -> Text.literal("Verberg-duur: " + RedBurst.config.hideSeconds + " sec"),
                () -> {
                    int[] d = RedBurst.DURATIONS;
                    int next = d[0];
                    for (int v : d) {
                        if (v > RedBurst.config.hideSeconds) {
                            next = v;
                            break;
                        }
                    }
                    RedBurst.config.hideSeconds = next;
                });

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
