package com.nexomc.nexo.mechanics.misc.misc

import com.nexomc.nexo.mechanics.Mechanic
import com.nexomc.nexo.mechanics.MechanicFactory
import com.nexomc.nexo.utils.VersionUtil
import com.nexomc.nexo.utils.logs.Logs
import org.bukkit.configuration.ConfigurationSection

class MiscMechanic(mechanicFactory: MechanicFactory?, section: ConfigurationSection) :
    Mechanic(mechanicFactory, section) {
    val cactusBreaks = section.getBoolean("breaks_from_cactus", true)
    val burnsInFire = section.getBoolean("burns_in_fire", true)
    val burnsInLava = section.getBoolean("burns_in_lava", true)
    val isVanillaInteractionDisabled = section.getBoolean("disable_vanilla_interactions", false)
    val canStripLogs = section.getBoolean("can_strip_logs", false)
    val piglinsIgnoreWhenEquipped = section.getBoolean("piglins_ignore_when_equipped", false)
    val isCompostable = section.getBoolean("compostable", false)

    val isAllowedInVanillaRecipes = section.getBoolean("allow_in_vanilla_recipes", false)

    init {
        if (VersionUtil.atleast("1.20.5") && (section.getBoolean("burns_in_fire") || section.getBoolean("burns_in_lava"))) {
            Logs.logWarn("$itemID seems to be using ${if (burnsInFire) "burns_in_fire" else "burns_in_lava"} which is deprecated....")
            Logs.logWarn("It is heavily advised to swap to the new ${
                if (VersionUtil.atleast("1.21.2")) "Components.damage_resistant: is_fire on all 1.21.2+ servers"
                else "Components.fire_resistant on all 1.20.5+ servers"
            }")
        }
    }
}
