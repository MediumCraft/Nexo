package com.nexomc.nexo.items

import com.nexomc.nexo.utils.KeyUtils
import com.nexomc.nexo.utils.KeyUtils.dropExtension
import com.nexomc.nexo.utils.VersionUtil
import com.nexomc.nexo.utils.customarmor.CustomArmorType
import net.kyori.adventure.key.Key
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.inventory.EquipmentSlot
import team.unnamed.creative.model.ModelTexture
import team.unnamed.creative.model.ModelTextures

data class NexoMeta(
    var customModelData: Int? = null,
    var modelKey: Key? = null,
    var blockingModel: Key? = null,
    var pullingModels: List<Key> = listOf(),
    var chargedModel: Key? = null,
    var fireworkModel: Key? = null,
    var castModel: Key? = null,
    var damagedModels: List<Key> = listOf(),
    var textureLayers: List<ModelTexture> = listOf(),
    var textureVariables: Map<String, ModelTexture> = mapOf(),
    var modelTextures: ModelTextures? = null,
    var containsPackInfo: Boolean = false,
    var excludedFromInventory: Boolean = false,
    var excludedFromCommands: Boolean = false,
    var noUpdate: Boolean = false,
    var disableEnchanting: Boolean = false,
    var generateModel: Boolean = false,
    var customArmorTextures: CustomArmorTextures? = null,
) {

    data class CustomArmorTextures(val layer1: Key, val layer2: Key) {
        var armorPrefix: String = ""

        constructor(section: ConfigurationSection) : this(KeyUtils.parseKey(section.getString("layer_1", "")!!), KeyUtils.parseKey(section.getString("layer_2", "")!!))
    }

    lateinit var parentModel: Key

    fun packInfo(packSection: ConfigurationSection) {
        this.containsPackInfo = true
        this.modelKey = readModelName(packSection, "model")
        this.blockingModel = readModelName(packSection, "blocking_model")
        this.castModel = readModelName(packSection, "cast_model")
        this.chargedModel = readModelName(packSection, "charged_model")
        this.fireworkModel = readModelName(packSection, "firework_model")
        this.pullingModels = packSection.getStringList("pulling_models").map(KeyUtils::dropExtension)
        this.damagedModels = packSection.getStringList("damaged_models").map(KeyUtils::dropExtension)

        // By adding the textures to pullingModels aswell,
        // we can use the same code for both pullingModels
        // and pullingTextures to add to the base-bow file predicates
        if (pullingModels.isEmpty()) pullingModels =
            packSection.getStringList("pulling_textures").map(KeyUtils::dropExtension)
        if (damagedModels.isEmpty()) damagedModels =
            packSection.getStringList("damaged_textures").map(KeyUtils::dropExtension)

        if (chargedModel == null) chargedModel = dropExtension(packSection.getString("charged_texture", "")!!)
        if (fireworkModel == null) fireworkModel = dropExtension(packSection.getString("firework_texture", "")!!)
        if (castModel == null) castModel = dropExtension(packSection.getString("cast_texture", "")!!)
        if (blockingModel == null) blockingModel = dropExtension(packSection.getString("blocking_texture", "")!!)

        val textureSection = packSection.getConfigurationSection("textures")
        when {
            textureSection != null -> {
                val texturesSection = checkNotNull(packSection.getConfigurationSection("textures"))
                val variables = HashMap<String, ModelTexture>()
                texturesSection.getKeys(false).forEach { key: String ->
                    variables[key] = ModelTexture.ofKey(dropExtension(texturesSection.getString(key)!!))
                }
                this.textureVariables = variables
            }

            packSection.isList("textures") -> this.textureLayers =
                packSection.getStringList("textures").map(KeyUtils::dropExtension).map(ModelTexture::ofKey)

            packSection.isString("textures") -> this.textureLayers =
                listOf(ModelTexture.ofKey(dropExtension(packSection.getString("textures", "")!!)))

            packSection.isString("texture") -> this.textureLayers =
                listOf(ModelTexture.ofKey(dropExtension(packSection.getString("texture", "")!!)))
        }

        this.modelTextures = ModelTextures.builder()
            .particle(textureVariables["particle"])
            .variables(textureVariables)
            .layers(textureLayers)
            .build()

        val customArmorSection = packSection.getConfigurationSection("CustomArmor")
        when {
            customArmorSection != null -> {
                this.customArmorTextures = CustomArmorTextures(customArmorSection)
            }
            else -> apply {
                val itemId = packSection.parent!!.name
                val armorPrefix = itemId.substringBeforeLast("_").takeUnless { it == itemId || it.isBlank() } ?: return@apply
                itemId.substringAfterLast("_").takeIf { itemId.matches(CustomArmorType.itemIdRegex) } ?: return@apply

                this.customArmorTextures = CustomArmorTextures(Key.key(armorPrefix.plus("_armor_layer_1.png")), Key.key(armorPrefix.plus("_armor_layer_2.png")))
            }
        }

        this.parentModel = Key.key(packSection.getString("parent_model", "item/generated")!!)
        this.generateModel = packSection.getString("model") == null

        this.customModelData = packSection.getInt("custom_model_data").takeUnless { it == 0 }
    }

    private fun readModelName(configSection: ConfigurationSection, configString: String): Key? {
        val modelName = configSection.getString(configString)
        val parent = configSection.parent!!.name.lowercase().replace(" ", "_")

        return when {
            modelName == null && configString == "model" && Key.parseable(parent) -> Key.key(parent)
            Key.parseable(modelName) -> dropExtension(modelName!!)
            //else -> KeyUtils.MALFORMED_KEY_PLACEHOLDER
            else -> null
        }
    }

    fun hasBlockingModel() = blockingModel?.value()?.isNotEmpty() == true

    fun hasCastModel() = castModel?.value()?.isNotEmpty() == true

    fun hasChargedModel() = chargedModel?.value()?.isNotEmpty() == true

    fun hasFireworkModel() = fireworkModel?.value()?.isNotEmpty() == true
}

