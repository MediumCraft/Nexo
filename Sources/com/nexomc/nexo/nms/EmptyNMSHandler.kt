package com.nexomc.nexo.nms

import com.nexomc.nexo.nms.IPacketHandler.EmptyPacketHandler
import com.nexomc.nexo.utils.wrappers.PotionEffectTypeWrapper
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect

class EmptyNMSHandler(override val pluginConverter: IPluginConverter = IPluginConverter.EmptyPluginConverter()) : NMSHandler {
    override val packDispatchListener: Listener = object : Listener {}
    override fun packetHandler(): IPacketHandler {
        return EmptyPacketHandler()
    }

    override fun noteblockUpdatesDisabled(): Boolean = false
    override fun tripwireUpdatesDisabled(): Boolean = false
    override fun chorusplantUpdateDisabled(): Boolean = false

    override fun copyItemNBTTags(oldItem: ItemStack, newItem: ItemStack) = newItem

    override fun correctBlockStates(player: Player, slot: EquipmentSlot, itemStack: ItemStack, target: Block, blockFace: BlockFace) = null

    override fun applyMiningEffect(player: Player) {
        player.addPotionEffect(
            PotionEffect(PotionEffectTypeWrapper.MINING_FATIGUE, -1, Int.MAX_VALUE, false, false, false)
        )
    }

    override fun removeMiningEffect(player: Player) {
        player.removePotionEffect(PotionEffectTypeWrapper.MINING_FATIGUE)
    }

    override fun noteBlockInstrument(block: Block): String {
        return "block.note_block.harp"
    }

    override fun resourcepackFormat() = 55
    override fun datapackFormat() = 71
}
