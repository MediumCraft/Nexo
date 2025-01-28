package com.nexomc.nexo.mechanics.repair

import com.mineinabyss.idofront.items.editItemMeta
import com.nexomc.nexo.api.NexoItems
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.meta.Damageable

class RepairMechanicListener(val factory: RepairMechanicFactory) : Listener {

    @EventHandler
    fun InventoryClickEvent.onRepairItem() {
        val repairMechanic = factory.getMechanic(cursor)?.takeUnless { factory.isOraxenDurabilityOnly } ?: return
        val toRepair = currentItem ?: return
        val toRepairMeta = (toRepair.itemMeta as? Damageable)?.takeUnless { it.damage == 0 } ?: return

        toRepairMeta.damage = repairMechanic.finalDamage(toRepair.type.maxDurability.toInt(), toRepairMeta.damage)

        toRepair.setItemMeta(toRepairMeta)
        isCancelled = true
        cursor.amount -= 1
    }
}
