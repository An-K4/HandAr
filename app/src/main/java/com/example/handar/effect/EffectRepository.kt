package com.example.handar.effect

import com.example.handar.effect.catalog.blackHoleEffect
import com.example.handar.effect.catalog.canvasDrawEffect
import com.example.handar.effect.catalog.dragonBallEffect
import com.example.handar.effect.catalog.earthEffect
import com.example.handar.effect.catalog.fireBallEffect
import com.example.handar.effect.catalog.gojoEffect
import com.example.handar.effect.catalog.lightningEffect
import com.example.handar.effect.catalog.magicShieldEffect
import com.example.handar.effect.catalog.monsterEffect
import com.example.handar.effect.catalog.roomTeleportEffect
import com.example.handar.effect.model.EffectDefinition

object EffectRepository {
    val all = listOf(
        blackHoleEffect(),
        fireBallEffect(),
        magicShieldEffect(),
        lightningEffect(),
        dragonBallEffect(),
        gojoEffect(),
        monsterEffect(),
        roomTeleportEffect(),
        canvasDrawEffect(),
        earthEffect()
    )

    fun findById(id: String): EffectDefinition = all.first { it.id == id }

    fun findByIdOrNull(id: String): EffectDefinition? = all.firstOrNull { it.id == id }

    fun findByName(query: String): List<EffectDefinition> =
        if (query.isBlank()) all else all.filter { it.displayName.contains(query, ignoreCase = true) }
}
