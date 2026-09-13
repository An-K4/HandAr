package com.example.handar.recording

class AudioMixer {
    @Volatile private var effectPcm: ShortArray? = null
    @Volatile private var effectPos = 0
    private val lock = Any()

    fun triggerEffect(pcm: ShortArray?, startPos: Int = 0) {
        synchronized(lock) {
            effectPcm = pcm
            effectPos = if (pcm != null && pcm.isNotEmpty()) startPos % pcm.size else 0
        }
    }

    fun mix(micChunk: ShortArray, len: Int): ShortArray {
        val result = micChunk.copyOf(len)
        synchronized(lock) {
            val pcm = effectPcm ?: return result
            if (pcm.isEmpty()) return result
            for (i in 0 until len) {
                val sum = result[i] + pcm[effectPos]
                result[i] = sum.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                effectPos++
                if (effectPos >= pcm.size) effectPos = 0
            }
        }
        return result
    }
}