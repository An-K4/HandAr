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

/**
 * 10 hiệu ứng đúng theo Design_App_HandAr.md mục 4 — mỗi hàm factory nằm trong `effect/catalog/`.
 * 4 hiệu ứng test cũ (rock_on_ily, camera_shutter, absolute_cinema_two_hand, heart_or_cross) đã được
 * thay thế hoàn toàn ở đợt này; xem AGENTS.md mục 8 lịch sử thay thế và Test_Checklist.md mục I để
 * biết những cử chỉ nào (singleHandOkSign, singleHandThumbsUp, singleHandCall, singleHandRockOn,
 * singleHandILoveYou, bothHandsFist, twoHandsHeart, twoHandsCrossedFingers) tạm thời mất chỗ test
 * riêng — các gesture này vẫn còn nguyên trong `Gestures`, chỉ là chưa có EffectDefinition nào gán
 * chúng cho state nào nữa.
 */
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

    fun findByName(query: String): List<EffectDefinition> =
        if (query.isBlank()) all else all.filter { it.displayName.contains(query, ignoreCase = true) }
}
