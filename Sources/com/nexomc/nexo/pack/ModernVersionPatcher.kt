package com.nexomc.nexo.pack

import com.nexomc.nexo.pack.PackGenerator.Companion.externalPacks
import com.nexomc.nexo.pack.creative.NexoPackReader
import com.nexomc.nexo.utils.*
import com.nexomc.nexo.utils.JsonBuilder.array
import com.nexomc.nexo.utils.JsonBuilder.`object`
import com.nexomc.nexo.utils.JsonBuilder.plus
import com.nexomc.nexo.utils.JsonBuilder.toJsonArray
import com.nexomc.nexo.utils.logs.Logs
import team.unnamed.creative.ResourcePack
import team.unnamed.creative.base.Writable
import team.unnamed.creative.model.ItemOverride
import team.unnamed.creative.model.ItemPredicate

// Patch for handling CustomModelData for 1.21.4+ until creative updated
class ModernVersionPatcher(val resourcePack: ResourcePack) {
    private val overlayItemModelRegex = ".+/assets/minecraft/items/.*\\.json".toRegex()

    private fun mergeItemModels(firstModel: Writable, secondModel: Writable): Writable {
        // Parse JSON objects
        val firstJson = firstModel.toJsonObject() ?: return secondModel
        val secondJson = secondModel.toJsonObject() ?: return firstModel
        if (firstJson == secondJson) return secondModel

        // Extract "entries" array from the first model
        val firstEntries = firstJson.`object`("model")?.array("entries")

        // If "entries" exist in the base model, merge with overlay
        if (firstEntries != null) {
            val secondModelJson = secondJson.`object`("model") ?: return firstModel
            val updatedEntries = secondModelJson.array("entries")?.plus(firstEntries) ?: firstEntries

            // Add merged entries back into the second model JSON
            secondModelJson.add("entries", updatedEntries)
            return Writable.stringUtf8(secondJson.toString())
        }

        // No "entries" in the base model, replace with overlay
        return secondModel
    }

    fun patchPack() {
        resourcePack.models().filterFast { DefaultResourcePackExtractor.vanillaResourcePack.model(it.key()) != null }
            .associateFast { it.key().value().removePrefix("item/").appendIfMissing(".json") to it.overrides() }
            .forEach { (model, overrides) ->
                val existingItemModel = standardItemModels["assets/minecraft/items/$model"]?.toJsonObject()
                // If not standard (shield etc.) we need to traverse the tree
                val finalNewItemModel = existingItemModel?.let { existingItemModel ->
                    // More complex item-models, like shield etc
                    val baseItemModel =
                        existingItemModel.`object`("model")?.takeUnless { it.isSimpleItemModel } ?: return@let null

                    runCatching {
                        val keys = baseItemModel.keySet()
                        if (model.endsWith("bow.json")) handleBowEntries(existingItemModel, baseItemModel, overrides)
                        else {
                            if ("on_false" in keys) handleOnBoolean(false, baseItemModel, overrides)
                            if ("on_true" in keys) handleOnBoolean(true, baseItemModel, overrides)
                            if ("tints" in keys) handleTints(existingItemModel, baseItemModel, overrides)
                            if ("cases" in keys) handleCases(existingItemModel, baseItemModel, overrides)
                        }
                    }.onFailure {
                        it.printStackTrace()
                        Logs.logError(model)
                        Logs.logWarn(overrides.joinToString("\n") { s -> s.toString() })
                    }

                    existingItemModel
                } ?: JsonBuilder.jsonObject.plus(
                    "model",
                    modelObject(existingItemModel?.`object`("model"), overrides, model)
                )
                resourcePack.unknownFile(
                    "assets/minecraft/items/$model",
                    Writable.stringUtf8(finalNewItemModel.toString())
                )
            }

        // Merge any ItemModel in an overlay into the base one
        resourcePack.unknownFiles()
            .filterKeys { it.matches(overlayItemModelRegex) }
            .mapKeys { it.key.substringAfter("/").prependIfMissing("assets/") }
            .forEach { (key, overlayWritable) ->
                resourcePack.unknownFile(key)?.toJsonObject()
                    ?.takeUnless { !isStandardItemModel(key, it) }
                    ?.also { resourcePack.unknownFile(key, overlayWritable) }
                    ?: mergeItemModels(resourcePack.unknownFile(key) ?: overlayWritable, overlayWritable).also {
                        resourcePack.unknownFile(key, it)
                    }
            }

        // Remove all overlay ItemModels
        resourcePack.unknownFiles()
            .filterKeys { it.matches(overlayItemModelRegex) }
            .keys.forEach(resourcePack::removeUnknownFile)
    }

    private fun handleCases(
        existingItemModel: JsonObject,
        baseItemModel: JsonObject,
        overrides: MutableList<ItemOverride>,
    ) {
        JsonBuilder.jsonObject.plus("type", "minecraft:range_dispatch")
            .plus("property", "minecraft:custom_model_data")
            .plus(
                "entries", JsonBuilder.jsonArray
                    .plus(JsonBuilder.jsonObject.plus("model", baseItemModel).plus("threshold", 0f))
                    .plus(overrides.mapNotNull {
                        val cmd = it.predicate().customModelData ?: return@mapNotNull null
                        JsonBuilder.jsonObject.plus("threshold", cmd).plus(
                            "model", JsonBuilder.jsonObject
                                .plus("model", it.model().asString())
                                .plus("type", "minecraft:model")
                        )
                    }.toJsonArray())
            ).let { existingItemModel.plus("model", it) }
    }

    private fun handleTints(
        existingItemModel: JsonObject,
        baseItemModel: JsonObject,
        overrides: MutableList<ItemOverride>,
    ) {
        val defaultTints = baseItemModel.array("tints") ?: return

        JsonBuilder.jsonObject.plus("type", "minecraft:range_dispatch")
            .plus("property", "minecraft:custom_model_data")
            .plus(
                "entries", JsonBuilder.jsonArray
                    .plus(JsonBuilder.jsonObject.plus("model", baseItemModel).plus("threshold", 0f))
                    .plus(overrides.mapNotNull {
                        val cmd = it.predicate().customModelData ?: return@mapNotNull null
                        JsonBuilder.jsonObject.plus("threshold", cmd).plus(
                            "model", JsonBuilder.jsonObject
                                .plus("model", it.model().asString())
                                .plus("type", "minecraft:model")
                                .plus("tints", defaultTints)
                        )
                    }.toJsonArray())
            ).let { existingItemModel.plus("model", it) }
    }

    private fun handleOnBoolean(
        boolean: Boolean,
        baseItemModel: JsonObject,
        overrides: List<ItemOverride>,
    ) {
        val defaultObject = baseItemModel.`object`("on_$boolean")?.deepCopy() ?: return
        val wantedOverrides = overrides.groupBy { it.predicate().customModelData }
            .let {
                it.values.map { e -> if (boolean) e.last() else e.first() }
            }.filter { (it.predicate().customModelData ?: 0) != 0 }

        JsonBuilder.jsonObject.plus("type", "minecraft:range_dispatch")
            .plus("property", "minecraft:custom_model_data")
            .plus(
                "entries", JsonBuilder.jsonArray
                    .plus(JsonBuilder.jsonObject.plus("model", defaultObject).plus("threshold", 0f))
                    .plus(wantedOverrides.mapNotNull {
                        val cmd = it.predicate().customModelData ?: return@mapNotNull null
                        JsonBuilder.jsonObject.plus("threshold", cmd).plus(
                            "model", JsonBuilder.jsonObject
                                .plus("model", it.model().asString())
                                .plus("type", "minecraft:model")
                        )
                    }.toJsonArray())
            ).let { baseItemModel.plus("on_$boolean", it) }
    }

    private fun handleBowEntries(
        existingItemModel: JsonObject,
        baseItemModel: JsonObject,
        overrides: List<ItemOverride>,
    ) {
        val defaultObject = baseItemModel.deepCopy() ?: return
        val pullingOverrides = overrides.groupBy { it.predicate().customModelData }

        JsonBuilder.jsonObject
            .plus("type", "minecraft:range_dispatch")
            .plus("property", "minecraft:custom_model_data")
            .plus("entries", pullingOverrides.entries.mapNotNull { (cmd, overrides) ->
                val pullOverrides = overrides.filter { it.predicate().pull != null }.sortedBy { it.predicate().pull }
                val fallback = pullOverrides.firstOrNull()?.model()?.asString() ?: return@mapNotNull null
                val onFalse = overrides.firstOrNull()?.model()?.asString() ?: return@mapNotNull null

                JsonBuilder.jsonObject
                    .plus("threshold", cmd ?: return@mapNotNull null)
                    .plus(
                        "model",
                        JsonBuilder.jsonObject
                            .plus("type", "minecraft:condition")
                            .plus("property", "minecraft:using_item")
                            .plus("on_false", JsonBuilder.jsonObject.plus("model", onFalse).plus("type", "minecraft:model"))
                            .plus(
                                "on_true",
                                JsonBuilder.jsonObject
                                    .plus("type", "minecraft:range_dispatch")
                                    .plus("property", "minecraft:use_duration")
                                    .plus("scale", 0.05)
                                    .plus("fallback", JsonBuilder.jsonObject.plus("type", "minecraft:model").plus("model", fallback))
                                    .plus("entries", pullOverrides.mapNotNull pull@{ pull ->
                                        val model = pull.model().asString()
                                        val pull = pull.predicate().pull?.takeIf { it > 0 } ?: return@pull null
                                        JsonBuilder.jsonObject
                                            .plus("threshold", pull)
                                            .plus("model", JsonBuilder.jsonObject.plus("type", "minecraft:model").plus("model", model))
                                    }.toJsonArray())
                            )
                    )
            }.toJsonArray()).plus("fallback", defaultObject).also { existingItemModel.plus("model", it) }

    }

    private val JsonObject.isSimpleItemModel: Boolean
        get() = keySet().size == 2 && get("type").asString.equals("minecraft:model")
    private val standardItemModels by lazy {
        runCatching {
            NexoPackReader().readFile(externalPacks.listFiles()!!.first { it.name.startsWith("RequiredPack_") })
        }.getOrDefault(DefaultResourcePackExtractor.vanillaResourcePack).unknownFiles()
            .filterKeys { it.startsWith("assets/minecraft/items") }
    }

    private fun isStandardItemModel(key: String, itemModel: JsonObject): Boolean {
        return (standardItemModels[key]?.toJsonObject()?.equals(itemModel) ?: false)
    }

    private fun modelObject(
        baseItemModel: JsonObject?,
        overrides: List<ItemOverride>,
        model: String? = null,
    ): JsonObject = JsonBuilder.jsonObject
        .plus("type", "minecraft:range_dispatch")
        .plus("property", "minecraft:custom_model_data")
        .plus("entries", modelEntries(overrides))
        .plus("scale", 1f)
        .apply {
            if (model != null) plus(
                "fallback", baseItemModel ?: JsonBuilder.jsonObject
                    .plus("type", "minecraft:model")
                    .plus("model", "item/${model.removeSuffix(".json")}")
            )
        }

    private fun modelEntries(overrides: List<ItemOverride>) = overrides.mapNotNull {
        JsonBuilder.jsonObject.plus("threshold", it.predicate().customModelData ?: return@mapNotNull null).plus(
            "model", JsonBuilder.jsonObject
                .plus("model", it.model().asString())
                .plus("type", "minecraft:model")
        )
    }.toJsonArray()

    private val List<ItemPredicate>.customModelData: Float?
        get() = firstOrNull { it.name() == "custom_model_data" }?.value().toString().toFloatOrNull()
    private val List<ItemPredicate>.pull: Float?
        get() = firstOrNull { it.name() == "pull" }?.value().toString().toFloatOrNull()
}