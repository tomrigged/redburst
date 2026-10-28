package nl.tomaxmc;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

public class ColorScreen extends Screen {
    private static final int COLS = 4;
    private final Screen parent;

    public ColorScreen(Screen parent) {
        super(Text.literal("Kleuren"));
        this.parent = parent;
    }

    private Text label(int i) {
        boolean on = TomaxMCKillEffect.hasColor(i);
        return Text.literal((on ? "[x] " : "[ ] ") + TomaxMCKillEffect.PALETTE_NAMES[i])
                .setStyle(Style.EMPTY.withColor(TomaxMCKillEffect.PALETTE[i]));
    }

    @Override
    protected void init() {
        int btnW = 98;
        int gap = 4;
        int totalW = COLS * btnW + (COLS - 1) * gap;
        int left = this.width / 2 - totalW / 2;
        int top = this.height / 2 - 95;
        int step = 22;

        int n = TomaxMCKillEffect.PALETTE.length;
        for (int i = 0; i < n; i++) {
            final int idx = i;
            int col = i % COLS;
            int row = i / COLS;
            addDrawableChild(ButtonWidget.builder(label(i), b -> {
                TomaxMCKillEffect.toggleColor(idx);
                b.setMessage(label(idx));
            }).dimensions(left + col * (btnW + gap), top + 14 + row * step, btnW, 20).build());
        }

        int rows = (n + COLS - 1) / COLS;
        int by = top + 14 + rows * step + 6;
        int w3 = (totalW - 2 * gap) / 3;
        addDrawableChild(ButtonWidget.builder(Text.literal("Regenboog"), b -> {
            TomaxMCKillEffect.setColors(TomaxMCKillEffect.RAINBOW);
            clearAndInit();
        }).dimensions(left, by, w3, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Alles"), b -> {
            int[] all = new int[n];
            for (int i = 0; i < n; i++) all[i] = i;
            TomaxMCKillEffect.setColors(all);
            clearAndInit();
        }).dimensions(left + w3 + gap, by, w3, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Alleen rood"), b -> {
            TomaxMCKillEffect.setColors(0);
            clearAndInit();
        }).dimensions(left + 2 * (w3 + gap), by, w3, 20).build());

        addDrawableChild(ButtonWidget.builder(
                Text.literal("Wegfaden: " + TomaxMCKillEffect.FADE_NAMES[TomaxMCKillEffect.config.fadeMode]), b -> {
                    TomaxMCKillEffect.config.fadeMode =
                            (TomaxMCKillEffect.config.fadeMode + 1) % TomaxMCKillEffect.FADE_NAMES.length;
                    TomaxMCKillEffect.save();
                    b.setMessage(Text.literal("Wegfaden: "
                            + TomaxMCKillEffect.FADE_NAMES[TomaxMCKillEffect.config.fadeMode]));
                }).dimensions(this.width / 2 - 100, by + 26, 200, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Klaar"), b -> close())
                .dimensions(this.width / 2 - 100, by + 52, 200, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int top = this.height / 2 - 95;
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Zet meerdere kleuren aan om ze te mixen"),
                this.width / 2, top, 0xFFFFFF);
    }

    @Override
    public void close() {
        if (this.client != null) this.client.setScreen(parent);
    }
}
