package nl.tomaxmc;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class FriendsScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget field;

    public FriendsScreen(Screen parent) {
        super(Text.literal("Vrienden"));
        this.parent = parent;
    }

    private void addFromField() {
        if (field == null) return;
        if (TomaxMCKillEffect.addFriend(field.getText().trim())) {
            field.setText("");
            rebuild();
        }
    }

    private void rebuild() {
        clearAndInit();
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int top = this.height / 2 - 95;

        addDrawableChild(ButtonWidget.builder(
                Text.literal("Vrienden-kills: " + (TomaxMCKillEffect.config.friendsEnabled ? "AAN" : "UIT")),
                b -> {
                    TomaxMCKillEffect.config.friendsEnabled = !TomaxMCKillEffect.config.friendsEnabled;
                    TomaxMCKillEffect.save();
                    b.setMessage(Text.literal("Vrienden-kills: " + (TomaxMCKillEffect.config.friendsEnabled ? "AAN" : "UIT")));
                }).dimensions(cx - 100, top, 200, 20).build());

        field = new TextFieldWidget(this.textRenderer, cx - 100, top + 30, 130, 20, Text.literal("Gebruikersnaam"));
        field.setMaxLength(16);
        field.setPlaceholder(Text.literal("Gebruikersnaam"));
        addDrawableChild(field);
        setInitialFocus(field);

        addDrawableChild(ButtonWidget.builder(Text.literal("Toevoegen"), b -> addFromField())
                .dimensions(cx + 35, top + 30, 65, 20).build());

        // Lijst: klik op een naam om hem te verwijderen
        List<String> list = new ArrayList<>(TomaxMCKillEffect.config.friends);
        int max = Math.min(list.size(), 6);
        for (int i = 0; i < max; i++) {
            String name = list.get(i);
            addDrawableChild(ButtonWidget.builder(Text.literal("[x] " + name), b -> {
                TomaxMCKillEffect.removeFriend(name);
                rebuild();
            }).dimensions(cx - 100, top + 60 + i * 22, 200, 20).build());
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("Klaar"), b -> close())
                .dimensions(cx - 100, top + 60 + 6 * 22 + 5, 200, 20).build());
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            addFromField();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int top = this.height / 2 - 95;
        if (TomaxMCKillEffect.config.friends.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("Nog geen vrienden. Typ een naam en klik Toevoegen."),
                    this.width / 2, top + 66, 0xAAAAAA);
        } else if (TomaxMCKillEffect.config.friends.size() > 6) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("(eerste 6 getoond - /tomaxmc remove <naam> voor de rest)"),
                    this.width / 2, top + 60 + 6 * 22 + 30, 0xAAAAAA);
        }
    }

    @Override
    public void close() {
        if (this.client != null) this.client.setScreen(parent);
    }
}
