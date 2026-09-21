package com.example.handar.utils

import android.content.Context
import androidx.core.content.edit

object FavouriteManager {
    private const val PREFS_NAME = "favourite_effects"
    private const val KEY_IDS = "ids"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isFavourite(context: Context, effectId: String): Boolean {
        return prefs(context).getStringSet(KEY_IDS, emptySet())?.contains(effectId) == true
    }

    fun toggle(context: Context, effectId: String): Boolean {
        val p = prefs(context)
        val current = HashSet(p.getStringSet(KEY_IDS, emptySet()) ?: emptySet())
        val nowFavourite = if (current.contains(effectId)) {
            current.remove(effectId)
            false
        } else {
            current.add(effectId)
            true
        }
        p.edit { putStringSet(KEY_IDS, current) }
        return nowFavourite
    }
}
