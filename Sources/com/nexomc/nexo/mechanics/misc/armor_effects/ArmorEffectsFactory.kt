package com.nexomc.nexo.mechanics.misc.armor_effects

import com.nexomc.nexo.NexoPlugin
import com.nexomc.nexo.mechanics.MechanicFactory
import com.nexomc.nexo.mechanics.MechanicsManager
import com.nexomc.nexo.utils.VersionUtil
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.inventory.ItemStack

class ArmorEffectsFactory(section: ConfigurationSection) : MechanicFactory(section) {
    private var armorEffectTask: ArmorEffectsTask? = null
    val delay = section.getInt("delay_in_ticks", 20)

    init {
        instance = this
        if (VersionUtil.isPaperServer) MechanicsManager.registerListeners(NexoPlugin.instance(), mechanicID, ArmorEffectsListener())
    }

    override fun parse(section: ConfigurationSection): ArmorEffectsMechanic {
        val mechanic = ArmorEffectsMechanic(this, section)
        addToImplemented(mechanic)
        armorEffectTask?.cancel()
        armorEffectTask = ArmorEffectsTask()
        MechanicsManager.registerTask(
            instance.mechanicID,
            armorEffectTask!!.runTaskTimer(NexoPlugin.instance(), 0, delay.toLong())
        )
        return mechanic
    }

    override fun getMechanic(itemID: String?) = super.getMechanic(itemID) as? ArmorEffectsMechanic

    override fun getMechanic(itemStack: ItemStack?) = super.getMechanic(itemStack) as? ArmorEffectsMechanic

    companion object {
        private lateinit var instance: ArmorEffectsFactory
        fun instance(): ArmorEffectsFactory {
            return instance
        }
    }
}