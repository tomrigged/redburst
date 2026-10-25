package nl.redburst;

import it.unimi.dsi.fastutil.ints.IntList;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FireworkExplosionComponent;
import net.minecraft.component.type.FireworksComponent;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class RedBurst implements ModInitializer {
    // false = ook bij mobs (om te testen), true = alleen als je een speler killt
    private static final boolean ONLY_PLAYERS = false;

    private static class Pending {
        final ServerWorld world;
        final FireworkRocketEntity rocket;
        int ticks = 3;
        Pending(ServerWorld world, FireworkRocketEntity rocket) {
            this.world = world;
            this.rocket = rocket;
        }
    }

    private static final List<Pending> PENDING = new ArrayList<>();

    @Override
    public void onInitialize() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(source.getAttacker() instanceof ServerPlayerEntity)) return;
            if (ONLY_PLAYERS && !(entity instanceof ServerPlayerEntity)) return;
            if (!(entity.getEntityWorld() instanceof ServerWorld world)) return;

            double x = entity.getX();
            double y = entity.getY() + entity.getHeight() / 2.0;
            double z = entity.getZ();

            // Drie rode explosies tegelijk
            FireworkExplosionComponent bol = new FireworkExplosionComponent(
                    FireworkExplosionComponent.Type.LARGE_BALL,
                    IntList.of(0xFF0000, 0xCC0000), IntList.of(0x550000), true, true);
            FireworkExplosionComponent burst = new FireworkExplosionComponent(
                    FireworkExplosionComponent.Type.BURST,
                    IntList.of(0xFF0000, 0xFF3300), IntList.of(0x880000), true, false);
            FireworkExplosionComponent ster = new FireworkExplosionComponent(
                    FireworkExplosionComponent.Type.STAR,
                    IntList.of(0xFF1111), IntList.of(0x330000), false, true);

            ItemStack stack = new ItemStack(Items.FIREWORK_ROCKET);
            // Lange vliegtijd, zodat de raket zelf nooit ontploft (dat zou schade doen)
            stack.set(DataComponentTypes.FIREWORKS,
                    new FireworksComponent(100, List.of(bol, burst, ster)));

            FireworkRocketEntity rocket = new FireworkRocketEntity(world, x, y, z, stack);
            world.spawnEntity(rocket);
            PENDING.add(new Pending(world, rocket));
        });

        // Na een paar ticks sturen we alleen het explosie-effect en halen we de raket weg
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            Iterator<Pending> it = PENDING.iterator();
            while (it.hasNext()) {
                Pending p = it.next();
                if (--p.ticks > 0) continue;
                if (!p.rocket.isRemoved()) {
                    p.world.sendEntityStatus(p.rocket, (byte) 17);
                    p.rocket.discard();
                }
                it.remove();
            }
        });
    }
}
