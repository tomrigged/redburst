package nl.redburst;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.random.Random;

public class RedBurst implements ModInitializer {
    @Override
    public void onInitialize() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(source.getAttacker() instanceof ServerPlayerEntity)) return;
            if (!(entity instanceof ServerPlayerEntity)) return;
            if (!(entity.getEntityWorld() instanceof ServerWorld world)) return;

            double x = entity.getX();
            double y = entity.getY() + entity.getHeight() / 2.0;
            double z = entity.getZ();
            Random r = world.getRandom();

            DustParticleEffect red = new DustParticleEffect(0xFF0000, 1.6f);

            for (int i = 0; i < 250; i++) {
                double dx = r.nextGaussian() * 0.9;
                double dy = r.nextGaussian() * 0.9;
                double dz = r.nextGaussian() * 0.9;
                world.spawnParticles(red, x + dx, y + dy, z + dz, 1, 0, 0, 0, 0);
            }

            world.spawnParticles(ParticleTypes.FIREWORK, x, y, z, 80, 0.1, 0.1, 0.1, 0.35);
        });
    }
}
