package nl.tomaxmc.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.world.World;
import nl.tomaxmc.TomaxMCKillEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {

    // Deeltjes van de server vlak bij een dood
    @Inject(method = "onParticle", at = @At("HEAD"), cancellable = true)
    private void tomaxmckilleffect$onParticle(ParticleS2CPacket packet, CallbackInfo ci) {
        if (TomaxMCKillEffect.config.hideParticles && TomaxMCKillEffect.inZone(packet.getX(), packet.getY(), packet.getZ())) {
            ci.cancel();
        }
    }

    // Geluiden van de server vlak bij een dood
    @Inject(method = "onPlaySound", at = @At("HEAD"), cancellable = true)
    private void tomaxmckilleffect$onPlaySound(PlaySoundS2CPacket packet, CallbackInfo ci) {
        if (TomaxMCKillEffect.config.hideSounds && TomaxMCKillEffect.inZone(packet.getX(), packet.getY(), packet.getZ())) {
            ci.cancel();
        }
    }

    // Bliksem en vuurwerkraketten van de server
    @Inject(method = "onEntitySpawn", at = @At("HEAD"), cancellable = true)
    private void tomaxmckilleffect$onEntitySpawn(EntitySpawnS2CPacket packet, CallbackInfo ci) {
        EntityType<?> type = packet.getEntityType();
        if (TomaxMCKillEffect.config.hideLightning && type == EntityType.LIGHTNING_BOLT) {
            ci.cancel();
        } else if (TomaxMCKillEffect.config.hideFireworks && type == EntityType.FIREWORK_ROCKET) {
            ci.cancel();
        }
    }

    // Dood-melding (status 3) = nieuwe zone; vuurwerk-explosie (status 17) verbergen
    @Inject(method = "onEntityStatus", at = @At("HEAD"), cancellable = true)
    private void tomaxmckilleffect$onEntityStatus(EntityStatusS2CPacket packet, CallbackInfo ci) {
        World world = MinecraftClient.getInstance().world;
        if (world == null) return;
        Entity entity = packet.getEntity(world);
        if (entity == null) return;
        if (packet.getStatus() == 3 && entity instanceof PlayerEntity) {
            TomaxMCKillEffect.addZone(entity.getX(), entity.getY() + entity.getHeight() / 2.0, entity.getZ());
        }
        // Dood-pakket: werkt ook als de server je geen gezondheid van anderen stuurt
        if (packet.getStatus() == 3 && entity instanceof LivingEntity living
                && MinecraftClient.getInstance().isOnThread()) {
            TomaxMCKillEffect.handleDeath(MinecraftClient.getInstance(), living);
        }
        if (packet.getStatus() == 17 && TomaxMCKillEffect.config.hideFireworks
                && entity instanceof FireworkRocketEntity) {
            ci.cancel();
        }
    }
}
