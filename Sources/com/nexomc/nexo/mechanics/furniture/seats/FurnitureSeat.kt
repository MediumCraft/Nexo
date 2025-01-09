package com.nexomc.nexo.mechanics.furniture.seats

import com.nexomc.nexo.NexoPlugin
import com.nexomc.nexo.mechanics.furniture.FurnitureMechanic
import com.nexomc.nexo.utils.VectorUtils.vectorFromString
import com.nexomc.nexo.utils.safeCast
import com.jeff_media.morepersistentdatatypes.DataType
import com.mineinabyss.idofront.operators.plus
import com.nexomc.nexo.api.NexoFurniture
import com.nexomc.nexo.mechanics.furniture.FurnitureFactory
import com.nexomc.nexo.utils.BlockHelpers
import com.nexomc.nexo.utils.toFastMap
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.entity.Entity
import org.bukkit.entity.Interaction
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType
import org.bukkit.util.Vector
import org.joml.Math
import java.util.*

data class FurnitureSeat(val offset: Vector) {

    constructor(offset: Map<String?, Any?>) : this(Vector.deserialize(offset))

    constructor(offset: String) : this(vectorFromString(offset, 0f))

    fun offset(): Vector {
        return offset
    }

    /**
     * Offset rotated around the baseEntity's yaw
     *
     * @param yaw Yaw of baseEntity
     * @return Rotated offset vector
     */
    fun offset(yaw: Float): Vector {
        return rotateOffset(yaw)
    }

    private fun rotateOffset(angle: Float): Vector {
        var angle = angle
        if (angle < 0) angle += 360f // Ensure yaw is positive

        val radians = Math.toRadians(angle).toDouble()

        // Get the coordinates relative to the local y-axis
        val x = offset.x * Math.cos(radians) - (-offset.z) * Math.sin(radians)
        val z = offset.x * Math.sin(radians) + (-offset.z) * Math.cos(radians)
        val y = offset.y

        return Vector(x, y, z)
    }

    companion object {
        val SEAT_KEY = NamespacedKey(NexoPlugin.instance(), "seat")

        fun getSeat(offset: Any?) = when (offset) {
            is Map<*, *> -> FurnitureSeat(offset.safeCast<Map<String?, Any?>>()!!)
            is Vector -> FurnitureSeat(offset)
            is String -> FurnitureSeat(offset)
            is Double -> FurnitureSeat(offset.toString())
            is Int -> FurnitureSeat(offset.toString())
            else -> null
        }

        fun isSeat(entity: Entity?) = entity?.persistentDataContainer?.has(SEAT_KEY, DataType.UUID) == true

        fun sitOnSeat(baseEntity: ItemDisplay, player: Player, interactionPoint: Location?) {
            val centeredLoc = BlockHelpers.toCenterLocation(interactionPoint ?: baseEntity.location)
            baseEntity.persistentDataContainer.get(SEAT_KEY, DataType.asList(DataType.UUID))
                ?.mapNotNull { Bukkit.getEntity(it).takeIf { i -> i is Interaction && i.passengers.isEmpty() } }
                ?.minWithOrNull(Comparator.comparingDouble { centeredLoc.distanceSquared(it.location) })
                ?.addPassenger(player)
        }

        fun spawnSeats(baseEntity: ItemDisplay, mechanic: FurnitureMechanic) {
            val translation = Vector.fromJOML(baseEntity.transformation.translation)
            val yaw = baseEntity.location.yaw
            val uuid = baseEntity.uniqueId
            val seatUUIDs = mechanic.seats.map { seat: FurnitureSeat ->
                val location = baseEntity.location.plus(translation).add(seat.offset(yaw))
                baseEntity.world.spawn(location, Interaction::class.java) { i ->
                    i.interactionHeight = 0.1f
                    i.interactionWidth = 0.1f
                    i.isPersistent = true
                    i.persistentDataContainer.set(FurnitureMechanic.FURNITURE_KEY, PersistentDataType.STRING, mechanic.itemID)
                    i.persistentDataContainer.set(SEAT_KEY, DataType.UUID, uuid)
                }.uniqueId
            }
            baseEntity.persistentDataContainer.set(SEAT_KEY, DataType.asList(DataType.UUID), seatUUIDs)
        }

        fun updateSeats(baseEntity: ItemDisplay, mechanic: FurnitureMechanic) {
            val seats: Map<FurnitureSeat, Interaction> = baseEntity.persistentDataContainer
                .get(SEAT_KEY, DataType.asList(DataType.UUID))
                ?.mapIndexedNotNull { i, uuid ->
                    val furnitureSeat = mechanic.seats.elementAtOrNull(i) ?: return@mapIndexedNotNull null
                    val interactionEntity = Bukkit.getEntity(uuid) as? Interaction ?: return@mapIndexedNotNull null
                    furnitureSeat to interactionEntity
                }?.toFastMap() ?: return


            if (mechanic.seats.isEmpty) seats.values.onEach(Entity::remove)
            else seats.forEach { (seat, entity) ->
                val passengers = entity.passengers.toList().onEach(entity::removePassenger)
                val translation = Vector.fromJOML(baseEntity.transformation.translation)
                entity.teleport(baseEntity.location.plus(translation).plus(seat.offset(baseEntity.location.yaw)))
                passengers.onEach(entity::addPassenger)
            }
        }

        fun removeSeats(baseEntity: ItemDisplay) {
            baseEntity.persistentDataContainer.getOrDefault(SEAT_KEY, DataType.asList(DataType.UUID), listOf())
                .mapNotNull { Bukkit.getEntity(it) as? Interaction }.forEach { seat ->
                    seat.passengers.forEach(seat::removePassenger)
                    if (!seat.isDead) seat.remove()
                }
        }
    }
}
