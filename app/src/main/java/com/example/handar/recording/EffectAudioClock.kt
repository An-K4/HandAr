package com.example.handar.recording

class EffectAudioClock(private val sampleRate: Int) {
    @Volatile private var running = false
    private var  thread: Thread? = null

    fun start(chunkMs: Int = 20, onChunk: (pcmChunk: ShortArray, len: Int) -> Unit) {
        running = true
        val chunkSample = sampleRate * chunkMs / 1000
        val silence = ShortArray(chunkSample)

        thread = Thread {
            val startNs = System.nanoTime()
            var  producedSamples = 0L

            while (running) {
                val elapsedNs = System.nanoTime() - startNs
                val expectedSamples = elapsedNs * sampleRate / 1_000_000_000L
                val pending = (expectedSamples - producedSamples).toInt()

                if (pending >= chunkSample) {
                    onChunk(silence, chunkSample)
                    producedSamples += chunkSample
                } else {
                    Thread.sleep(2)
                }
            }
        }.apply { start() }
    }

    fun stop() {
        running = false
        thread?.join(200)
        thread = null
    }
}