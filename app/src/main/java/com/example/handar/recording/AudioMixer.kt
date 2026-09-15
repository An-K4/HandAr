package com.example.handar.recording

class AudioMixer {
    @Volatile
    private var effectPcm: ShortArray? = null
    @Volatile
    private var effectPos = 0
    private val lock = Any()

    @Volatile
    private var bgmPcm: ShortArray? = null
    private var bgmPos = 0
    private var bgmGain = 0.5f

    fun triggerEffect(pcm: ShortArray?, startPos: Int = 0) {
        synchronized(lock) {
            effectPcm = pcm
            effectPos = if (pcm != null && pcm.isNotEmpty()) startPos % pcm.size else 0
        }
    }

    fun setBgm(pcm: ShortArray?, gainPercent: Int) {
        synchronized(lock) {
            bgmPcm = pcm
            bgmGain = gainPercent / 100f
            bgmPos = 0
        }
    }

    fun resetBgmPos() {
        synchronized(lock) { bgmPos = 0 }
    }

    fun mix(micChunk: ShortArray, len: Int): ShortArray {
        val result = micChunk.copyOf(len)
        synchronized(lock) {
            val bgm = bgmPcm
            if (bgm != null && bgm.isNotEmpty()) {
                for (i in 0 until len) {
                    val sum = result[i] + (bgm[bgmPos] * bgmGain).toInt()
                    result[i] = sum.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    bgmPos++
                    if (bgmPos >= bgm.size) bgmPos = 0
                }
            }

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