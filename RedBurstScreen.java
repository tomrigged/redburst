package nl.redburst;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class RedBurstScreen extends Screen {
    private final Screen parent;

    public RedBurstScreen(Screen parent) {
        super(Text.literal("Red Burst"));
        this.parent = parent;
    }

    private static Text playersText() {
        return Text.literal("Bij spelers: " + (RedBurst.config.players ? "AAN" : "UIT"));
    }

    private static Text mobsText() {
        return Text.literal("Bij mobs: " + (RedBurst.config.mobs ? "AAN" : "UIT"));
    }

    private static Text soundText() {
        return Text.literal("Kill-geluid: " + (RedBurst.config.sound ? "AAN" : "UIT"));
    }

    private static Text everyoneText() {
        return Text.literal("Ook doden door anderen/creeper/val: " + (RedBurst.config.everyone ? "AAN" : "UIT"));
    }

    private static Text delayText() {
        return Text.literal("Vuurwerk-vertraging: " + RedBurst.config.delay + " ticks");
    }

    private static Text colorText() {
        return Text.literal("Kleur: " + RedBurst.COLOR_NAMES[RedBurst.config.color]);
    }

    @Override
    protected void init() {
        int w = 260;
        int h = 20;
        int x = this.width / 2 - w / 2;
        int y = this.height / 2 - 90;

        ButtonWidget header = ButtonWidget.builder(Text.literal("Red Burst instellingen"), b -> { })
                .dimensions(x, y, w, h).build();
        header.active = false;
        addDrawableChild(header);

        addDrawableChild(ButtonWidget.builder(playersText(), b -> {
            RedBurst.config.players = !RedBurst.config.players;
            RedBurst.save();
            b.setMessage(playersText());
        }).dimensions(x, y + 30, w, h).build());

        addDrawableChild(ButtonWidget.builder(mobsText(), b -> {
            RedBurst.config.mobs = !RedBurst.config.mobs;
            RedBurst.save();
            b.setMessage(mobsText());
        }).dimensions(x, y + 55, w, h).build());

        addDrawableChild(ButtonWidget.builder(colorText(), b -> {
            RedBurst.config.color = (RedBurst.config.color + 1) % RedBurst.COLOR_NAMES.length;
            RedBurst.save();
            b.setMessage(colorText());
        }).dimensions(x, y + 80, w, h).build());

        addDrawableChild(ButtonWidget.builder(soundText(), b -> {
            RedBurst.config.sound = !RedBurst.config.sound;
            RedBurst.save();
            b.setMessage(soundText());
        }).dimensions(x, y + 105, w, h).build());

        addDrawableChild(ButtonWidget.builder(everyoneText(), b -> {
            RedBurst.config.everyone = !RedBurst.config.everyone;
            RedBurst.save();
            b.setMessage(everyoneText());
        }).dimensions(x, y + 130, w, h).build());

        addDrawableChild(ButtonWidget.builder(delayText(), b -> {
            int[] d = RedBurst.DELAYS;
            int next = d[0];
            for (int i = 0; i < d.length; i++) {
                if (d[i] > RedBurst.config.delay) {
                    next = d[i];
                    break;
                }
            }
            RedBurst.config.delay = next;
            RedBurst.save();
            b.setMessage(delayText());
        }).dimensions(x, y + 155, w, h).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Klaar"), b -> close())
                .dimensions(x, y + 190, w, h).build());
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }
}
