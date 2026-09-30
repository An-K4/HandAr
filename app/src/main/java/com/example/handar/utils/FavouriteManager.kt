package com.example.handar.utils

import android.content.Context
import androidx.core.content.edit

class FavouriteManager(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isFavourite(effectId: String): Boolean {
        return prefs.getStringSet(KEY_IDS, emptySet())?.contains(effectId) == true
    }

    fun toggle(effectId: String): Boolean {
        val current = HashSet(prefs.getStringSet(KEY_IDS, emptySet()) ?: emptySet())
        val nowFavourite = if (current.contains(effectId)) {
            current.remove(effectId)
            false
        } else {
            current.add(effectId)
            true
        }
        prefs.edit { putStringSet(KEY_IDS, current) }
        return nowFavourite
    }

    private companion object {
        const val PREFS_NAME = "favourite_effects"
        const val KEY_IDS = "ids"
    }
}
