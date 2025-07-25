@file:Suppress("UnusedReceiverParameter", "UnusedReceiverParameter")

package com.nexomc.nexo.mechanics.furniture.listeners

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent
import com.destroystokyo.paper.event.player.PlayerUseUnknownEntityEvent
import com.jeff_media.morepersistentdatatypes.DataType
import com.nexomc.nexo.api.NexoFurniture
import com.nexomc.nexo.api.events.NexoItemsLoadedEvent
import com.nexomc.nexo.api.events.furniture.NexoFurnitureBreakEvent
import com.nexomc.nexo.api.events.furniture.NexoFurnitureInteractEvent
import com.nexomc.nexo.mechanics.custom_block.CustomBlockHelpers
import com.nexomc.nexo.mechanics.furniture.FurnitureFactory
import com.nexomc.nexo.mechanics.furniture.FurnitureMechanic
import com.nexomc.nexo.mechanics.furniture.IFurniturePacketManager
import com.nexomc.nexo.mechanics.furniture.IFurniturePacketManager.Companion.furnitureBaseMap
import com.nexomc.nexo.mechanics.furniture.bed.FurnitureBed
import com.nexomc.nexo.mechanics.furniture.seats.FurnitureSeat
import com.nexomc.nexo.utils.BlockHelpers.isLoaded
import com.nexomc.nexo.utils.EventUtils.call
import com.nexomc.nexo.utils.SchedulerUtils
import com.nexomc.protectionlib.ProtectionLib
import io.papermc.paper.event.player.PlayerTrackEntityEvent
import io.papermc.paper.event.player.PlayerUntrackEntityEvent
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.entity.Entity
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDismountEvent
import org.bukkit.event.entity.EntityMountEvent
import org.bukkit.event.entity.EntityTeleportEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.world.WorldUnloadEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.util.Vector

class FurniturePacketListener : Listener {

    @EventHandler
    fun PlayerTrackEntityEvent.onPlayerTrackFurniture() {
        val itemDisplay = entity.takeIf(Entity::isValid) as? ItemDisplay ?: return
        val mechanic = NexoFurniture.furnitureMechanic(itemDisplay) ?: return
        val packetManager = FurnitureFactory.instance()?.packetManager() ?: return

        SchedulerUtils.foliaScheduler.runAtEntityLater(itemDisplay, Runnable {
            packetManager.sendFurnitureMetadataPacket(itemDisplay, mechanic, player)
            packetManager.sendHitboxEntityPacket(itemDisplay, mechanic, player)
            packetManager.sendBarrierHitboxPacket(itemDisplay, mechanic, player)
            packetManager.sendLightMechanicPacket(itemDisplay, mechanic, player)
        }, 4L)
    }

    @EventHandler
    fun PlayerUntrackEntityEvent.onPlayerUntrackFurniture() {
        val itemDisplay = entity.takeIf(Entity::isValid) as? ItemDisplay ?: return
        val mechanic = NexoFurniture.furnitureMechanic(itemDisplay) ?: return
        val packetManager = FurnitureFactory.instance()?.packetManager() ?: return

        packetManager.removeHitboxEntityPacket(itemDisplay, mechanic, player)
        packetManager.removeBarrierHitboxPacket(itemDisplay, mechanic, player)
        packetManager.removeLightMechanicPacket(itemDisplay, mechanic, player)
    }

    @EventHandler
    fun EntityAddToWorldEvent.onLoad() {
        val itemDisplay = entity as? ItemDisplay ?: return

        furnitureBaseMap.remove(itemDisplay.uniqueId, furnitureBaseMap.get(itemDisplay.uniqueId)?.takeIf { it.baseId != itemDisplay.entityId })

        SchedulerUtils.foliaScheduler.runAtEntityLater(itemDisplay, Runnable {
            val packetManager = FurnitureFactory.instance()?.packetManager() ?: return@Runnable
            val mechanic = NexoFurniture.furnitureMechanic(itemDisplay) ?: return@Runnable

            packetManager.sendFurnitureMetadataPacket(itemDisplay, mechanic)
            packetManager.sendHitboxEntityPacket(itemDisplay, mechanic)
            packetManager.sendBarrierHitboxPacket(itemDisplay, mechanic)
            packetManager.sendLightMechanicPacket(itemDisplay, mechanic)
            if (mechanic.hasBeds) FurnitureBed.spawnBeds(itemDisplay, mechanic)
        }, 2L)
    }

    @EventHandler
    fun EntityRemoveFromWorldEvent.onUnload() {
        val itemDisplay = entity.takeIf { it.location.isLoaded } as? ItemDisplay ?: return
        val mechanic = NexoFurniture.furnitureMechanic(itemDisplay) ?: return
        val packetManager = FurnitureFactory.instance()?.packetManager() ?: return

        SchedulerUtils.foliaScheduler.runAtEntityLater(itemDisplay, Runnable {
            FurnitureBed.removeBeds(itemDisplay)
        }, 1L)

        furnitureBaseMap.remove(itemDisplay.uniqueId)
        packetManager.removeHitboxEntityPacket(itemDisplay, mechanic)
        packetManager.removeBarrierHitboxPacket(itemDisplay, mechanic)
        packetManager.removeLightMechanicPacket(itemDisplay, mechanic)
    }

    @EventHandler
    fun WorldUnloadEvent.onUnload() {
        world.entities.filterIsInstance<ItemDisplay>().forEach { itemDisplay ->
            val uuid = itemDisplay.uniqueId

            furnitureBaseMap.remove(uuid)
            IFurniturePacketManager.interactionHitboxPacketMap.remove(uuid)
            IFurniturePacketManager.shulkerHitboxPacketMap.remove(uuid)
            IFurniturePacketManager.barrierHitboxPositionMap.remove(uuid)
            IFurniturePacketManager.barrierHitboxLocationMap.remove(uuid)
            IFurniturePacketManager.lightPositionMap.remove(uuid)
            IFurniturePacketManager.lightLocationMap.remove(uuid)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun EntityTeleportEvent.onTeleportFurniture() {
        val baseEntity = entity as? ItemDisplay ?: return
        val mechanic = NexoFurniture.furnitureMechanic(baseEntity) ?: return

        mechanic.hitbox.refreshHitboxes(baseEntity, mechanic)
        mechanic.light.refreshLights(baseEntity, mechanic)
        FurnitureSeat.updateSeats(baseEntity, mechanic)
        FurnitureBed.updateBeds(baseEntity, mechanic)
    }

    @EventHandler
    fun NexoItemsLoadedEvent.onFurnitureFactory() {
        val packetManager = FurnitureFactory.instance()?.packetManager() ?: return
        SchedulerUtils.runAtWorldEntities<ItemDisplay> { entity ->
            val mechanic = NexoFurniture.furnitureMechanic(entity) ?: return@runAtWorldEntities
            if (FurnitureSeat.isSeat(entity)|| FurnitureBed.isBed(entity)) return@runAtWorldEntities

            packetManager.sendFurnitureMetadataPacket(entity, mechanic)
            packetManager.sendHitboxEntityPacket(entity, mechanic)
            packetManager.sendBarrierHitboxPacket(entity, mechanic)
            packetManager.sendLightMechanicPacket(entity, mechanic)
        }
    }

    @EventHandler
    fun PlayerUseUnknownEntityEvent.onUseUnknownEntity() {
        val itemStack = when (hand) {
            EquipmentSlot.HAND -> player.inventory.itemInMainHand
            else -> player.inventory.itemInOffHand
        }
        val baseEntity = IFurniturePacketManager.baseEntityFromHitbox(entityId) ?: return
        val relativePos = (clickedRelativePosition ?: Vector()).takeIf { isAttack || clickedRelativePosition != null} ?: return
        val interactionPoint = IFurniturePacketManager.hitboxLocFromId(entityId, baseEntity.world)?.add(relativePos) ?: return
        val mechanic = NexoFurniture.furnitureMechanic(baseEntity) ?: return

        when {
            isAttack && player.gameMode != GameMode.ADVENTURE && ProtectionLib.canBreak(player, baseEntity.location) -> {
                BlockBreakEvent(baseEntity.location.block, player).call {
                    NexoFurnitureBreakEvent(mechanic, baseEntity, player).call {
                        NexoFurniture.remove(baseEntity, player)
                    }
                }
            }

            !isAttack && ProtectionLib.canInteract(player, baseEntity.location) && clickedRelativePosition != null ->
                NexoFurnitureInteractEvent(mechanic, baseEntity, player, itemStack, hand, interactionPoint).call()
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun PlayerInteractEvent.onPlayerInteractBarrierHitbox() {
        if (!IFurniturePacketManager.blockIsHitbox((clickedBlock ?: interactionPoint?.block)?.takeIf { it.isEmpty } ?: return)) return
        val mechanic = NexoFurniture.furnitureMechanic(clickedBlock) ?: NexoFurniture.furnitureMechanic(interactionPoint) ?: return
        val baseEntity = FurnitureMechanic.baseEntity(clickedBlock) ?: FurnitureMechanic.baseEntity(interactionPoint) ?: return
        val interactionPoint = interactionPoint ?: clickedBlock?.location?.toCenterLocation()

         when (action) {
            Action.RIGHT_CLICK_BLOCK -> {
                if (!ProtectionLib.canBuild(player, baseEntity.location)) setUseItemInHand(Event.Result.DENY)
                val validBlockItem = item != null && !NexoFurniture.isFurniture(item) && item!!.type.let { it.isBlock && it != Material.LILY_PAD && it != Material.FROGSPAWN }
                if (useItemInHand() != Event.Result.DENY && validBlockItem && (!mechanic.isInteractable(player) || player.isSneaking)) {
                    setUseItemInHand(Event.Result.DENY)
                    setUseInteractedBlock(Event.Result.DENY)
                    clickedBlock?.type = Material.BARRIER
                    CustomBlockHelpers.makePlayerPlaceBlock(player, hand!!, item!!, clickedBlock!!, blockFace, null, null)
                    clickedBlock?.type = Material.AIR
                }

                if (!ProtectionLib.canInteract(player, baseEntity.location)) setUseInteractedBlock(Event.Result.DENY)
                NexoFurnitureInteractEvent(mechanic, baseEntity, player, item, hand!!, interactionPoint, useInteractedBlock(), useItemInHand(), blockFace).call {
                    setUseInteractedBlock(useFurniture)
                    setUseItemInHand(useItemInHand)
                }
            }
            Action.LEFT_CLICK_BLOCK if ProtectionLib.canBreak(player, baseEntity.location) -> {
                if (mechanic.breakable.hardness == 0.0 || player.gameMode == GameMode.CREATIVE) BlockBreakEvent(baseEntity.location.block, player).call {
                    NexoFurnitureBreakEvent(mechanic, baseEntity, player).call {
                        NexoFurniture.remove(baseEntity, player)
                    }
                }
            }
             else -> {}
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    fun EntityMountEvent.onSitSeat() {
        val player = entity as? Player ?: return
        val baseEntity = mount.persistentDataContainer.get(FurnitureSeat.SEAT_KEY, DataType.UUID)?.let(Bukkit::getEntity) as? ItemDisplay ?: return
        val mechanic = NexoFurniture.furnitureMechanic(baseEntity) ?: return

        SchedulerUtils.runTaskLater(4L) {
            FurnitureFactory.instance()?.packetManager()?.removeBarrierHitboxPacket(baseEntity, mechanic, player)
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    fun EntityDismountEvent.onLeaveSeat() {
        val player = entity as? Player ?: return
        val baseEntity = dismounted.persistentDataContainer.get(FurnitureSeat.SEAT_KEY, DataType.UUID)?.let(Bukkit::getEntity) as? ItemDisplay ?: return
        val mechanic = NexoFurniture.furnitureMechanic(baseEntity) ?: return

        FurnitureFactory.instance()?.packetManager()?.sendBarrierHitboxPacket(baseEntity, mechanic, player)
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    fun BlockPlaceEvent.onPlaceInBarrier() {
        if (IFurniturePacketManager.blockIsHitbox(blockPlaced)) isCancelled = true
    }
}
