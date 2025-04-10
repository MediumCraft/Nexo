package com.nexomc.nexo.utils.breaker

import com.nexomc.nexo.mechanics.breakable.BreakableMechanic
import com.nexomc.nexo.mechanics.custom_block.CustomBlockMechanic
import com.nexomc.nexo.mechanics.furniture.FurnitureMechanic
import com.nexomc.nexo.utils.wrappers.AttributeWrapper
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.bukkit.GameMode
import org.bukkit.NamespacedKey
import org.bukkit.attribute.AttributeModifier
import org.bukkit.block.Block
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.bukkit.inventory.EquipmentSlotGroup

class ModernBreakerManager(private val modifierMap: ConcurrentHashMap<UUID, AttributeModifier>) : BreakerManager {

    companion object {
        private val KEY = NamespacedKey.fromString("nexo:custom_breaking_speed")!!
    }

    override fun startFurnitureBreak(
        player: Player,
        baseEntity: ItemDisplay,
        mechanic: FurnitureMechanic,
        block: Block
    ) {
        //TODO See if this can be handled even with packet-barriers
    }

    override fun startBlockBreak(player: Player, block: Block, mechanic: CustomBlockMechanic) {
        removeTransientModifier(player)
        if (player.gameMode == GameMode.CREATIVE) return

        addTransientModifier(player, createBreakingModifier(player, mechanic.breakable))
    }

    override fun stopBlockBreak(player: Player) {
        removeTransientModifier(player)
    }

    private fun createBreakingModifier(player: Player, breakable: BreakableMechanic): AttributeModifier {
        return AttributeModifier(KEY, (0.24 / breakable.hardness * breakable.speedMultiplier(player)) - 1, AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.HAND)
    }

    private fun addTransientModifier(player: Player, modifier: AttributeModifier) {
        removeTransientModifier(player)
        modifierMap[player.uniqueId] = modifier
        player.getAttribute(AttributeWrapper.BLOCK_BREAK_SPEED!!)?.addTransientModifier(modifier)
    }

    private fun removeTransientModifier(player: Player) {
        val modifier = modifierMap[player.uniqueId] ?: return
        val instance = player.getAttribute(AttributeWrapper.BLOCK_BREAK_SPEED!!) ?: return
        instance.removeModifier(modifier)
    }
}
