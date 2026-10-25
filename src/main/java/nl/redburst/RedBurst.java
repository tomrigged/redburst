package nl.redburst;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.random.Random;

public class RedBurst implements ModInitializer {
    // false = ook bij mobs (om te testen), true = alleen als je een speler killt
    private static final boolean ONLY_PLAYERS = false;

    @Override
    public void onInitialize() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(source.getAttacker() instanceof ServerPlayerEntity)) return;
            if (ONLY_PLAYERS && !(entity instanceof ServerPlayerEntity)) return;
            if (!(entity.getEntityWorld() instanceof ServerWorld world)) return;

            double x = entity.getX();
            double y = entity.getY() + entity.getHeight() / 2.0;
            double z = entity.getZ();
            Random r = world.getRandom();

            DustParticleEffect red = new DustParticleEffect(0xFF0000, 2.0f);
            DustParticleEffect darkRed = new DustParticleEffect(0xAA0000, 1.4f);
            DustParticleEffect orange = new DustParticleEffect(0xFF4000, 1.2f);

            // Grote, volle rode wolk
            for (int i = 0; i < 600; i++) {
                double dx = r.nextGaussian() * 1.3;
                double dy = r.nextGaussian() * 1.3;
                double dz = r.nextGaussian() * 1.3;
                DustParticleEffect type = (i % 3 == 0) ? darkRed : (i % 3 == 1) ? red : orange;
                world.spawnParticles(type, x + dx, y + dy, z + dz, 1, 0, 0, 0, 0);
            }

            // Vonkjes die in alle richtingen naar buiten schieten
            for (int i = 0; i < 150; i++) {
                double dx = r.nextGaussian();
                double dy = r.nextGaussian();
                double dz = r.nextGaussian();
                double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (len < 0.001) continue;
                double speed = 0.4 + r.nextDouble() * 0.4;
                world.spawnParticles(ParticleTypes.FIREWORK, x, y, z, 0,
                        dx / len, dy / len, dz / len, speed);
            }

            // Extra losse vonkjes in het midden
            world.spawnParticles(ParticleTypes.FIREWORK, x, y, z, 120, 0.2, 0.2, 0.2, 0.45);
        });
    }
}
