package com.nexomc.nexo.pack.server

typealias PackServerFactory = () -> NexoPackServer

object PackServerRegistry {
    private val registry = mutableMapOf<String, PackServerFactory>()

    fun register(type: String, factory: PackServerFactory) {
        registry[type.uppercase()] = factory
    }

    fun create(type: String): NexoPackServer? = registry[type.uppercase()]?.invoke()

    val allRegisteredTypes: Set<String> = registry.keys
}
