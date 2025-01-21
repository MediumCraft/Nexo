package com.nexomc.nexo.api

import com.nexomc.nexo.NexoPlugin
import com.nexomc.nexo.mechanics.furniture.*
import com.nexomc.nexo.mechanics.furniture.IFurniturePacketManager.Companion.furnitureBaseMap
import com.nexomc.nexo.mechanics.furniture.seats.FurnitureSeat
import com.nexomc.nexo.utils.BlockHelpers
import com.nexomc.nexo.utils.BlockHelpers.isLoaded
import com.nexomc.nexo.utils.BlockHelpers.toCenterBlockLocation
import com.nexomc.nexo.utils.ItemUtils.dyeColor
import com.nexomc.nexo.utils.VersionUtil
import com.nexomc.nexo.utils.drops.Drop
import io.lumine.mythiccrucible.items.furniture.Furniture
import org.bukkit.*
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.entity.*
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.util.BoundingBox
import java.util.*

object NexoFurniture {
    /**
     * Get all NexoItem IDs that have a FurnitureMechanic
     *
     * @return a Set of all NexoItem IDs that have a FurnitureMechanic
     */
    @JvmStatic
    fun furnitureIDs() = NexoItems.itemNames().filter(::isFurniture).toSet()
    /**
     * Check if a location contains a Furniture
     *
     * @param location The location to check
     * @return true if the location contains a Furniture, otherwise false
     */
    @JvmStatic
    fun isFurniture(location: Location): Boolean {
        val blockBox = BoundingBox.of(BlockHelpers.toCenterLocation(location), 0.5, 0.5, 0.5)
        return (furnitureMechanic(location) != null) || location.getWorld().getNearbyEntities(blockBox).any(::isFurniture)
    }

    /**
     * Check if an itemID has a FurnitureMechanic
     *
     * @param itemID The itemID to check
     * @return true if the itemID has a FurnitureMechanic, otherwise false
     */
    @JvmStatic
    fun isFurniture(itemID: String?) = FurnitureFactory.instance()?.isNotImplementedIn(itemID) == false

    @JvmStatic
    fun isFurniture(itemStack: ItemStack?) = isFurniture(NexoItems.idFromItem(itemStack))

    @JvmStatic
    fun isFurniture(entity: Entity?) = entity?.type == EntityType.ITEM_DISPLAY && furnitureMechanic(entity) != null

    @JvmStatic
    fun baseEntity(block: Block?): ItemDisplay? = FurnitureMechanic.baseEntity(block)

    @JvmStatic
    fun baseEntity(location: Location?): ItemDisplay? = FurnitureMechanic.baseEntity(location)

    @JvmStatic
    fun baseEntity(interactionId: Int) = FurnitureMechanic.baseEntity(interactionId)

    /**
     * Places Furniture at a given location
     * @param location The location to place the Furniture
     * @param itemID The itemID of the Furniture to place
     * @param rotation The rotation of the Furniture
     * @param blockFace The blockFace of the Furniture
     * @return The Furniture entity that was placed, or null if the Furniture could not be placed
     */
    @JvmStatic
    fun place(itemID: String?, location: Location, rotation: Rotation, blockFace: BlockFace): ItemDisplay? {
        return place(itemID, location, FurnitureHelpers.rotationToYaw(rotation), blockFace)
    }

    /**
     * Places Furniture at a given location
     * @param location The location to place the Furniture
     * @param itemID The itemID of the Furniture to place
     * @param yaw The yaw of the Furniture
     * @param blockFace The blockFace of the Furniture
     * @return The Furniture entity that was placed, or null if the Furniture could not be placed
     */
    @JvmStatic
    fun place(itemID: String?, location: Location, yaw: Float, blockFace: BlockFace): ItemDisplay? {
        val mechanic = furnitureMechanic(itemID) ?: return null
        return mechanic.place(location, yaw, blockFace)
    }

    /**
     * Removes Furniture at a given location, optionally by a player
     *
     * @param location The location to remove the Furniture
     * @param player   The player who removed the Furniture, can be null
     * @param drop     The drop of the furniture, if null the default drop will be used
     * @return true if the Furniture was removed, false otherwise
     */
    /**
     * Removes Furniture at a given location, optionally by a player
     *
     * @param location The location to remove the Furniture
     * @param player   The player who removed the Furniture, can be null
     * @return true if the Furniture was removed, false otherwise
     */
    @JvmOverloads
    @JvmStatic
    fun remove(location: Location, player: Player? = null, drop: Drop? = null): Boolean {
        if (!FurnitureFactory.isEnabled) return false
        if (!location.isWorldLoaded) return false
        checkNotNull(location.world)

        val entity = location.world.getNearbyEntities(location, 0.5, 0.5, 0.5).firstOrNull(::isFurniture)
        val mechanic = furnitureMechanic(location) ?: furnitureMechanic(entity) ?: return false
        val itemStack = player?.inventory?.itemInMainHand ?: ItemStack(Material.AIR)
        checkNotNull(entity)

        val baseEntity = FurnitureMechanic.baseEntity(location) ?: return false

        if (player != null) {
            if (player.gameMode != GameMode.CREATIVE) (drop ?: mechanic.breakable.drop).furnitureSpawns(baseEntity, itemStack)
            mechanic.storage?.takeIf { it.isStorage || it.isShulker }?.dropStorageContent(mechanic, baseEntity)
            if (VersionUtil.isPaperServer) baseEntity.world.sendGameEvent(player, GameEvent.BLOCK_DESTROY, baseEntity.location.toVector())
        }

        mechanic.removeBaseEntity(baseEntity)
        return true
    }

    /**
     * Removes Furniture at a given Entity, optionally by a player and with an altered Drop
     *
     * @param baseEntity The entity at which the Furniture should be removed
     * @param player     The player who removed the Furniture, can be null
     * @param drop       The drop of the furniture, if null the default drop will be used
     * @return true if the Furniture was removed, false otherwise
     */
    /**
     * Removes Furniture at a given Entity, optionally by a player
     *
     * @param baseEntity The entity at which the Furniture should be removed
     * @param player     The player who removed the Furniture, can be null
     * @return true if the Furniture was removed, false otherwise
     */
    @JvmOverloads
    @JvmStatic
    fun remove(baseEntity: Entity, player: Player? = null, drop: Drop? = null): Boolean {
        if (!FurnitureFactory.isEnabled) return false
        if (baseEntity !is ItemDisplay) return false
        val mechanic = furnitureMechanic(baseEntity) ?: return false

        // Allows for changing the FurnitureType in config and still remove old entities
        if (player != null) {
            val itemStack = player.inventory.itemInMainHand
            if (player.gameMode != GameMode.CREATIVE) (drop ?: mechanic.breakable.drop).furnitureSpawns(baseEntity, itemStack)
            mechanic.storage?.takeIf { it.isStorage || it.isShulker }?.dropStorageContent(mechanic, baseEntity)
            if (VersionUtil.isPaperServer) baseEntity.getWorld().sendGameEvent(player, GameEvent.BLOCK_DESTROY, baseEntity.location.toVector())
        }

        // Check if the mechanic or the baseEntity has barriers tied to it
        mechanic.removeBaseEntity(baseEntity)
        return true
    }

    @JvmStatic
    fun furnitureMechanic(block: Block?): FurnitureMechanic? {
        if (!FurnitureFactory.isEnabled || block == null) return null
        return furnitureMechanic(block.location)
    }

    /**
     * Get the FurnitureMechanic from a given location.
     * This will only return non-null for furniture with a barrier-hitbox
     *
     * @param location The location to get the FurnitureMechanic from
     * @return Instance of this block's FurnitureMechanic, or null if the location is not tied to a Furniture
     */
    @JvmStatic
    fun furnitureMechanic(location: Location?): FurnitureMechanic? {
        if (!FurnitureFactory.isEnabled || location == null) return null
        val baseEntity = IFurniturePacketManager.baseEntityFromHitbox(BlockLocation(location))
        return baseEntity?.let(::furnitureMechanic) ?: let {
            val centerLoc = toCenterBlockLocation(location)
            val boundingBox = BoundingBox.of(centerLoc, 0.5, 1.0, 0.5)
            centerLoc.world.getNearbyEntities(centerLoc, 2.0, 2.0, 2.0) { it is ItemDisplay}
                .sortedBy { it.location.distanceSquared(centerLoc) }
                .associateWith { furnitureMechanic(it) }
                .entries.firstOrNull { it.value?.hitbox?.interactionBoundingBoxes(centerLoc, centerLoc.yaw)?.any { it.overlaps(boundingBox) } == true }
                ?.key?.let(::furnitureMechanic)
        }
    }

    /**
     * Get the FurnitureMechanic from a given entity.
     *
     * @param baseEntity The entity to get the FurnitureMechanic from
     * @return Returns this entity's FurnitureMechanic, or null if the entity is not tied to a Furniture
     */
    @JvmStatic
    fun furnitureMechanic(baseEntity: Entity?): FurnitureMechanic? {
        if (!FurnitureFactory.isEnabled || baseEntity == null || baseEntity.type != EntityType.ITEM_DISPLAY) return null
        val itemID = baseEntity.persistentDataContainer.get(FurnitureMechanic.FURNITURE_KEY, PersistentDataType.STRING)
        if (!NexoItems.exists(itemID) || FurnitureSeat.isSeat(baseEntity)) return null
        // Ignore legacy hitbox entities as they should be removed in FurnitureConverter
        if (baseEntity is Interaction) return null
        return FurnitureFactory.instance()?.getMechanic(itemID)
    }

    /**
     * Get the FurnitureMechanic from a given block.
     * This will only return non-null for furniture with a barrier-hitbox
     *
     * @param itemID The itemID tied to this FurnitureMechanic
     * @return Returns the FurnitureMechanic tied to this itemID, or null if the itemID is not tied to a Furniture
     */
    @JvmStatic
    fun furnitureMechanic(itemID: String?): FurnitureMechanic? {
        if (!FurnitureFactory.isEnabled || !NexoItems.exists(itemID)) return null
        return FurnitureFactory.instance()?.getMechanic(itemID)
    }

    /**
     * Ensures that the given entity is a Furniture, and updates it if it is
     *
     * @param baseEntity The furniture baseEntity to update
     */
    @JvmStatic
    fun updateFurniture(baseEntity: ItemDisplay) {
        if (!FurnitureFactory.isEnabled || !isLoaded(baseEntity.location)) return
        val mechanic = furnitureMechanic(baseEntity)?.takeUnless { FurnitureSeat.isSeat(baseEntity) } ?: return

        FurnitureSeat.updateSeats(baseEntity, mechanic)

        val packetManager = FurnitureFactory.instance()?.packetManager() ?: return
        furnitureBaseMap.removeIf { it.baseUuid == baseEntity.uniqueId }
        packetManager.removeLightMechanicPacket(baseEntity, mechanic)
        packetManager.removeInteractionHitboxPacket(baseEntity, mechanic)
        packetManager.removeBarrierHitboxPacket(baseEntity, mechanic)

        Bukkit.getScheduler().runTaskLater(NexoPlugin.instance(), Runnable {
            packetManager.sendFurnitureMetadataPacket(baseEntity, mechanic)
            packetManager.sendInteractionEntityPacket(baseEntity, mechanic)
            packetManager.sendBarrierHitboxPacket(baseEntity, mechanic)
            packetManager.sendLightMechanicPacket(baseEntity, mechanic)
        }, 2L)
    }

    @JvmStatic
    fun findTargetFurniture(player: Player): ItemDisplay? {
        return FurnitureFactory.instance()?.packetManager()?.findTargetFurnitureHitbox(player)
    }
}
