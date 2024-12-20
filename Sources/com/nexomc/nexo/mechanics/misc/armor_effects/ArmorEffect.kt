package com.nexomc.nexo.mechanics.misc.armor_effects

import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

class ArmorEffect(val effect: PotionEffect, val requiresFullSet: Boolean) : PotionEffect(
    effect.type, effect.duration, effect.amplifier, effect.isAmbient, effect.hasParticles(), effect.hasIcon()
)