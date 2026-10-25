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

    private static Text colorText() {
        return Text.literal("Kleur: " + RedBurst.COLOR_NAMES[RedBurst.config.color]);
    }

    @Override
    protected void init() {
        int w = 220;
        int h = 20;
        int x = this.width / 2 - w / 2;
        int y = this.height / 2 - 62;

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

        addDrawableChild(ButtonWidget.builder(Text.literal("Klaar"), b -> close())
                .dimensions(x, y + 115, w, h).build());
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }
}
