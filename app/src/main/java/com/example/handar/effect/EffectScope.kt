package com.example.handar.effect

class EffectScope {
    private val models = HashMap<String, Any>()

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> shared(key: String, create: () -> T): T = models.getOrPut(key, create) as T
}