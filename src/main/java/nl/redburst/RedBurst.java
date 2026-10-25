package nl.redburst;

import it.unimi.dsi.fastutil.ints.IntList;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.type.FireworkExplosionComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;

import java.util.List;

public class RedBurst implements ClientModInitializer {
    // false = ook bij mobs (om te testen), true = alleen als je een speler killt
    private static final boolean ONLY_PLAYERS = false;
    // Hoe lang (in ticks) na je laatste klap een dood nog als jouw kill telt (100 = 5 seconden)
    private static final int WINDOW_TICKS = 100;

    private static LivingEntity target;
    private static int hitTick;
    private static int tick;
    private static double lx, ly, lz;

    @Override
    public void onInitializeClient() {
        // Onthoud wie jij het laatst hebt geslagen
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient() && entity instanceof LivingEntity living) {
                if (!ONLY_PLAYERS || living instanceof PlayerEntity) {
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

    private static void explode(double x, double y, double z) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        FireworkExplosionComponent bol = new FireworkExplosionComponent(
                FireworkExplosionComponent.Type.LARGE_BALL,
                IntList.of(0xFF0000, 0xCC0000), IntList.of(0x550000), true, true);
        FireworkExplosionComponent burst = new FireworkExplosionComponent(
                FireworkExplosionComponent.Type.BURST,
                IntList.of(0xFF0000, 0xFF3300), IntList.of(0x880000), true, false);
        FireworkExplosionComponent ster = new FireworkExplosionComponent(
                FireworkExplosionComponent.Type.STAR,
                IntList.of(0xFF1111), IntList.of(0x330000), false, true);

        mc.world.addFireworkParticle(x, y, z, 0, 0, 0, List.of(bol, burst, ster));
    }
}
