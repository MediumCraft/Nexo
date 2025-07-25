package com.nexomc.nexo.mechanics.light

import com.nexomc.nexo.mechanics.furniture.BlockLocation
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.data.type.Light

class LightBlock : BlockLocation {
    val lightLevel: Int
    val lightData: Light

    fun from(hitboxObject: Any?): LightBlock {
        return when (hitboxObject) {
            is String -> LightBlock(hitboxObject)
            else -> LightBlock("0,0,0 15")
        }
    }

    constructor(hitboxString: String) : super(hitboxString.substringBefore(" ")) {
        this.lightLevel = hitboxString.substringAfter(" ").toIntOrNull()?.coerceIn(0..15) ?: 15
        this.lightData = Material.LIGHT.createBlockData { (it as Light).level = lightLevel } as Light
    }

    constructor(location: Location, lightData: Light) : super(location) {
        this.lightLevel = lightData.level
        this.lightData = lightData.apply { isWaterlogged = location.block.type == Material.WATER }
    }

    constructor(location: Location, lightData: Light, waterlogged: Boolean) : super(location) {
        this.lightLevel = lightData.level
        this.lightData = lightData.apply { isWaterlogged = waterlogged }
    }

    constructor(x: Int, y: Int, z: Int, lightLevel: Int) : super(x, y, z) {
        this.lightLevel = lightLevel
        this.lightData = Material.LIGHT.createBlockData { (it as Light).level = lightLevel } as Light
    }
}
