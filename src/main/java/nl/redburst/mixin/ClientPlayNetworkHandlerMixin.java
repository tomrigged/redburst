package nl.redburst.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.world.World;
import nl.redburst.RedBurst;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {

    // Deeltjes van de server vlak bij een dood
    @Inject(method = "onParticle", at = @At("HEAD"), cancellable = true)
    private void redburst$onParticle(ParticleS2CPacket packet, CallbackInfo ci) {
        if (RedBurst.config.hideParticles && RedBurst.inZone(packet.getX(), packet.getY(), packet.getZ())) {
            ci.cancel();
        }
    }

    // Geluiden van de server vlak bij een dood
    @Inject(method = "onPlaySound", at = @At("HEAD"), cancellable = true)
    private void redburst$onPlaySound(PlaySoundS2CPacket packet, CallbackInfo ci) {
        if (RedBurst.config.hideSounds && RedBurst.inZone(packet.getX(), packet.getY(), packet.getZ())) {
            ci.cancel();
        }
    }

    // Bliksem en vuurwerkraketten van de server
    @Inject(method = "onEntitySpawn", at = @At("HEAD"), cancellable = true)
    private void redburst$onEntitySpawn(EntitySpawnS2CPacket packet, CallbackInfo ci) {
        EntityType<?> type = packet.getEntityType();
        if (RedBurst.config.hideLightning && type == EntityType.LIGHTNING_BOLT) {
            ci.cancel();
        } else if (RedBurst.config.hideFireworks && type == EntityType.FIREWORK_ROCKET) {
            ci.cancel();
        }
    }

    // Dood-melding (status 3) = nieuwe zone; vuurwerk-explosie (status 17) verbergen
    @Inject(method = "onEntityStatus", at = @At("HEAD"), cancellable = true)
    private void redburst$onEntityStatus(EntityStatusS2CPacket packet, CallbackInfo ci) {
        World world = MinecraftClient.getInstance().world;
        if (world == null) return;
        Entity entity = packet.getEntity(world);
        if (entity == null) return;
        if (packet.getStatus() == 3) {
            RedBurst.addZone(entity.getX(), entity.getY() + entity.getHeight() / 2.0, entity.getZ());
        } else if (packet.getStatus() == 17 && RedBurst.config.hideFireworks
                && entity instanceof FireworkRocketEntity) {
            ci.cancel();
        }
    }
}
