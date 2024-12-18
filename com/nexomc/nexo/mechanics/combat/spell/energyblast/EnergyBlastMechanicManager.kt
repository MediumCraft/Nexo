package com.nexomc.nexo.mechanics.combat.spell.energyblast

import com.nexomc.nexo.NexoPlugin
import com.nexomc.nexo.api.NexoItems
import com.nexomc.nexo.utils.BlockHelpers.isInteractable
import com.nexomc.nexo.utils.EventUtils.EntityDamageByEntityEvent
import com.nexomc.nexo.utils.EventUtils.call
import com.nexomc.nexo.utils.VectorUtils.rotateAroundAxisX
import com.nexomc.nexo.utils.VectorUtils.rotateAroundAxisY
import com.nexomc.nexo.utils.wrappers.ParticleWrapper
import io.th0rgal.protectionlib.ProtectionLib
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.damage.DamageType
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Vector
import kotlin.math.cos
import kotlin.math.sin

@Suppress("UnstableApiUsage")
class EnergyBlastMechanicManager(private val factory: EnergyBlastMechanicFactory) : Listener {
    @EventHandler(priority = EventPriority.NORMAL)
    fun PlayerInteractEvent.onPlayerUse() {
        val (block, item) = (clickedBlock) to (item ?: return)
        val mechanic = factory.getMechanic(item) ?: return
        val location = clickedBlock?.location ?: player.location

        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return
        if (useItemInHand() == Event.Result.DENY || !ProtectionLib.canUse(player, location)) return
        if (isInteractable(block) && useInteractedBlock() == Event.Result.ALLOW) return

        mechanic.timer(player).let { it.takeIf { it.isFinished }?.reset() ?: return it.sendToPlayer(player) }

        val origin = player.eyeLocation
        val direction = origin.getDirection()
        direction.normalize()
        direction.multiply(0.1)
        val destination = origin.clone().add(direction)
        for (i in 0..mechanic.length * 10) {
            val loc = destination.add(direction)
            spawnParticle(loc.getWorld(), loc, mechanic)
        }
        mechanic.removeCharge(item)
        playEffect(player, mechanic)
    }


    private fun playEffect(player: Player, mechanic: EnergyBlastMechanic) {
        object : BukkitRunnable() {
            val dir = player.location.getDirection().normalize()
            val circlePoints = 360
            var radius = 2.0
            val playerLoc = player.eyeLocation
            val pitch = ((playerLoc.pitch + 90.0f) * 0.017453292f).toDouble()
            val yaw = (-playerLoc.yaw * 0.017453292f).toDouble()
            val increment = (2 * Math.PI) / circlePoints
            var circlePointOffset = 0.0
            var beamLength = mechanic.length * 2
            val radiusShrinkage = radius / ((beamLength + 2) / 2.0)

            override fun run() {
                beamLength--
                if (beamLength < 1) {
                    this.cancel()
                    return
                }
                (0..<circlePoints).forEach { i ->
                    val angle = i * increment + circlePointOffset
                    val x = radius * cos(angle)
                    val z = radius * sin(angle)
                    val vec = Vector(x, 0.0, z)
                    rotateAroundAxisX(vec, pitch)
                    rotateAroundAxisY(vec, yaw)
                    playerLoc.add(vec)
                    spawnParticle(playerLoc.getWorld(), playerLoc, mechanic)
                    playerLoc.subtract(vec)
                }

                circlePointOffset += increment / 3
                if (circlePointOffset >= increment) {
                    circlePointOffset = 0.0
                }

                radius -= radiusShrinkage
                if (radius < 0) {
                    spawnParticle(playerLoc.getWorld(), playerLoc, mechanic, 1000, 0.3, 0.3, 0.3, 0.3)
                    for (entity: Entity in playerLoc.getWorld()
                        .getNearbyEntities(playerLoc, 0.5, 0.5, 0.5)) if (entity is LivingEntity && entity !== player) {
                        val event = EntityDamageByEntityEvent(
                            player,
                            entity,
                            EntityDamageEvent.DamageCause.MAGIC,
                            DamageType.MAGIC,
                            mechanic.damage * 3.0
                        )
                        if (entity.isDead() || event.call()) continue
                        entity.setLastDamageCause(event)
                        entity.damage(mechanic.damage * 3.0, player)
                    }
                    this.cancel()
                    return
                }

                playerLoc.add(dir)
                playerLoc.getWorld().getNearbyEntities(playerLoc, radius, radius, radius)
                    .filterIsInstance<LivingEntity>().forEach { entity ->
                    if (entity === player) return@forEach
                    val event = EntityDamageByEntityEvent(
                        player,
                        entity,
                        EntityDamageEvent.DamageCause.MAGIC,
                        DamageType.MAGIC,
                        mechanic.damage
                    )
                    if (entity.isDead || !event.call()) return@forEach
                        entity.lastDamageCause = event
                    entity.damage(mechanic.damage, player)
                }
            }
        }.runTaskTimer(NexoPlugin.instance(), 0, 1)
    }

    private fun spawnParticle(world: World, location: Location, mechanic: EnergyBlastMechanic) {
        if (mechanic.particle == ParticleWrapper.DUST) world.spawnParticle(
            ParticleWrapper.DUST,
            location,
            1,
            0.0,
            0.0,
            0.0,
            0.0,
            mechanic.particleColor
        )
        else world.spawnParticle(mechanic.particle, location, 1, 0.0, 0.0, 0.0, 0.0)
    }

    private fun spawnParticle(
        world: World, location: Location, mechanic: EnergyBlastMechanic, amount: Int, offsetX: Double,
        offsetY: Double, offsetZ: Double, extra: Double
    ) {
        if (mechanic.particle == ParticleWrapper.DUST) world.spawnParticle(
            ParticleWrapper.DUST, location.x, location.y, location.z,
            amount, offsetX, offsetY, offsetZ, extra,
            mechanic.particleColor!!
        )
        else world.spawnParticle(mechanic.particle, location, amount, offsetX, offsetY, offsetZ, extra)
    }
}
