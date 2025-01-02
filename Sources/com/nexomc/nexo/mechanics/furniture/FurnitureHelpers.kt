package com.nexomc.nexo.mechanics.furniture

import com.jeff_media.morepersistentdatatypes.DataType
import com.nexomc.nexo.api.NexoFurniture
import org.bukkit.Color
import org.bukkit.Rotation
import org.bukkit.entity.ItemDisplay
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

object FurnitureHelpers {
    fun correctedYaw(mechanic: FurnitureMechanic, yaw: Float): Float {
        return when {
            mechanic.limitedPlacing?.isRoof == false -> yaw
            mechanic.properties.isFixedTransform -> yaw - 180
            else -> yaw
        }
    }

    @JvmStatic
    fun furnitureYaw(baseEntity: ItemDisplay, yaw: Float) {
        if (!NexoFurniture.isFurniture(baseEntity)) return

        baseEntity.setRotation(yaw, baseEntity.location.pitch)
    }

    @JvmStatic
    fun rotationToYaw(rotation: Rotation) = (Rotation.entries.indexOf(rotation) * 360f) / 8f

    @JvmStatic
    fun furnitureItem(baseEntity: ItemDisplay): ItemStack? {
        return IFurniturePacketManager.furnitureBaseFromBaseEntity(baseEntity)?.itemStack
    }

    @JvmStatic
    fun furnitureItem(baseEntity: ItemDisplay, itemStack: ItemStack) {
        IFurniturePacketManager.furnitureBaseFromBaseEntity(baseEntity)?.also { furnitureBase ->
            furnitureBase.itemStack = itemStack
        }
        val mechanic = NexoFurniture.furnitureMechanic(baseEntity) ?: return
        FurnitureFactory.instance()?.packetManager()?.sendFurnitureMetadataPacket(baseEntity, mechanic)
    }

    @JvmStatic
    fun furnitureDye(baseEntity: ItemDisplay) =
        baseEntity.persistentDataContainer.get(FurnitureMechanic.FURNITURE_DYE_KEY, PersistentDataType.INTEGER)?.let(Color::fromRGB)

    @JvmStatic
    fun furnitureDye(baseEntity: ItemDisplay, dyeColor: Color?) {
        if (dyeColor == null) baseEntity.persistentDataContainer.remove(FurnitureMechanic.FURNITURE_DYE_KEY)
        else baseEntity.persistentDataContainer.set(FurnitureMechanic.FURNITURE_DYE_KEY, PersistentDataType.INTEGER, dyeColor.asRGB())
        val mechanic = NexoFurniture.furnitureMechanic(baseEntity) ?: return
        IFurniturePacketManager.furnitureBaseFromBaseEntity(baseEntity)?.also { furnitureBase ->
            furnitureBase.itemStack = furnitureBase.itemStack
        }
        FurnitureFactory.instance()?.packetManager()?.sendFurnitureMetadataPacket(baseEntity, mechanic)
    }

    @JvmStatic
    fun lightState(baseEntity: ItemDisplay): Boolean {
        NexoFurniture.furnitureMechanic(baseEntity)?.takeUnless { it.light.isEmpty } ?: return false
        return baseEntity.persistentDataContainer.getOrDefault(FurnitureMechanic.FURNITURE_LIGHT_KEY, DataType.BOOLEAN, true)
    }

    @JvmOverloads
    @JvmStatic
    fun toggleLight(baseEntity: ItemDisplay, state: Boolean? = null): Boolean {
        val mechanic = NexoFurniture.furnitureMechanic(baseEntity)?.takeUnless { it.light.isEmpty } ?: return false
        val newState = state ?: !baseEntity.persistentDataContainer.getOrDefault(FurnitureMechanic.FURNITURE_LIGHT_KEY, DataType.BOOLEAN, true)
        if (!mechanic.lightIsToggleable) return true
        baseEntity.persistentDataContainer.set(FurnitureMechanic.FURNITURE_LIGHT_KEY, DataType.BOOLEAN, state ?: newState)
        return newState
    }
}
