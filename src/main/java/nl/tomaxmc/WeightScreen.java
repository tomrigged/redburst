package nl.tomaxmc;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Stel in hoeveel % van elke gekozen kleur je ziet. De andere kleuren passen automatisch mee (totaal 100%). */
public class WeightScreen extends Screen {
    private final Screen parent;
    private final List<PctSlider> sliders = new ArrayList<>();

    public WeightScreen(Screen parent) {
        super(Text.literal("Verhouding"));
        this.parent = parent;
    }

    private static class PctSlider extends SliderWidget {
        final int idx;
        final Runnable onMove;

        PctSlider(int x, int y, int w, int h, int idx, double pct, Runnable onMove) {
            super(x, y, w, h, Text.empty(), Math.max(0, Math.min(1, pct / 100.0)));
            this.idx = idx;
            this.onMove = onMove;
            updateMessage();
        }

        double pct() {
            return this.value * 100.0;
        }

        void setPct(double p) {
            this.value = Math.max(0, Math.min(1, p / 100.0));
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal(TomaxMCKillEffect.PALETTE_NAMES[idx] + ": " + Math.round(pct()) + "%")
                    .setStyle(Style.EMPTY.withColor(TomaxMCKillEffect.PALETTE[idx])));
        }

        @Override
        protected void applyValue() {
            onMove.run();
        }
    }


    private void rebalance(PctSlider m) {
        int n = sliders.size();
        if (n < 2) return;
        double rest = 100.0 - m.pct();
        double sumOthers = 0;
        for (PctSlider s : sliders) if (s != m) sumOthers += s.pct();
        for (PctSlider s : sliders) {
            if (s == m) continue;
            s.setPct(sumOthers <= 0.0001 ? rest / (n - 1) : s.pct() * rest / sumOthers);
        }
    }

    @Override
    protected void init() {
        sliders.clear();
        List<Integer> mix = TomaxMCKillEffect.config.mix;
        int n = mix.size();

        // Beginwaarden: opgeslagen verhouding, anders gelijk verdeeld
        Map<Integer, Integer> saved = TomaxMCKillEffect.config.weights;
        boolean useSaved = saved != null;
        double sum = 0;
        if (useSaved) {
            for (int idx : mix) {
                Integer v = saved.get(idx);
                if (v == null) { useSaved = false; break; }
                sum += v;
            }
            if (sum <= 0) useSaved = false;
        }

        int perCol = 6;
        int cols = Math.max(1, (n + perCol - 1) / perCol);
        int gap = 4;
        int colW = Math.min(200, (this.width - 20 - (cols - 1) * gap) / cols);
        int totalW = cols * colW + (cols - 1) * gap;
        int left = this.width / 2 - totalW / 2;
        int top = this.height / 2 - 80;
        int step = 24;

        for (int i = 0; i < n; i++) {
            int idx = mix.get(i);
            double pct = useSaved ? saved.get(idx) * 100.0 / sum : 100.0 / n;
            int col = i / perCol;
            int row = i % perCol;
            PctSlider[] self = new PctSlider[1];
            self[0] = new PctSlider(left + col * (colW + gap), top + 16 + row * step, colW, 20, idx, pct,
                    () -> rebalance(self[0]));
            sliders.add(self[0]);
            addDrawableChild(self[0]);
        }

        int rows = Math.min(n, perCol);
        int by = top + 16 + rows * step + 8;
        addDrawableChild(ButtonWidget.builder(Text.literal("Gelijk verdelen"), b -> {
            for (PctSlider s : sliders) s.setPct(100.0 / sliders.size());
        }).dimensions(this.width / 2 - 100, by, 200, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Klaar"), b -> close())
                .dimensions(this.width / 2 - 100, by + 24, 200, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Sleep een kleur: de rest past automatisch aan (totaal 100%)"),
                this.width / 2, this.height / 2 - 80, 0xFFFFFF);
    }

    @Override
    public void removed() {
        // Opslaan bij sluiten
        Map<Integer, Integer> w = new HashMap<>();
        for (PctSlider s : sliders) w.put(s.idx, (int) Math.round(s.pct()));
        if (!w.isEmpty()) {
            TomaxMCKillEffect.config.weights = w;
            TomaxMCKillEffect.save();
        }
        super.removed();
    }

    @Override
    public void close() {
        if (this.client != null) this.client.setScreen(parent);
    }
}
