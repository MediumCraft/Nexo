package com.nexomc.nexo.mechanics.misc.custom.listeners

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent
import com.nexomc.nexo.NexoPlugin
import com.nexomc.nexo.api.NexoItems
import com.nexomc.nexo.mechanics.misc.custom.fields.CustomEvent
import com.nexomc.nexo.utils.actions.ClickAction
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler

class UnequipListener(itemID: String?, cooldown: Long, event: CustomEvent, clickAction: ClickAction) : CustomListener(itemID, cooldown, event, clickAction) {

    override fun register() {
        Bukkit.getPluginManager().registerEvents(this, NexoPlugin.instance())
    }

    @EventHandler
    fun PlayerArmorChangeEvent.onUnEquipArmor() {
        if (itemID != NexoItems.idFromItem(oldItem)) return
        perform(player, oldItem)
    }
}
