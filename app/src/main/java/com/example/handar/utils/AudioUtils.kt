package com.example.handar.utils

import android.content.Context
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

fun loadWavPcm(context: Context, resId: Int): ShortArray {
    val bytes = context.resources.openRawResource(resId).readBytes()
    val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

    var offset = 12
    var dataOffset = -1
    var dataSize = -1

    while (offset + 8 <= bytes.size) {
        val chunkIdInt = buf.getInt(offset)
        val chunkSize = buf.getInt(offset + 4)

        // 0x61746164 là chữ "data" viết theo dạng Little Endian Int
        if (chunkIdInt == 0x61746164) {
            dataOffset = offset + 8
            dataSize = chunkSize
            break
        }

        if (chunkSize < 0 || offset + 8 + chunkSize > bytes.size) {
            break
        }

        offset += 8 + chunkSize + (chunkSize % 2)
    }

    val start = if (dataOffset != -1) dataOffset else 44
    val end = if (dataOffset != -1 && dataSize > 0) {
        (start + dataSize).coerceAtMost(bytes.size)
    } else {
        bytes.size
    }

    val pcmBytes = bytes.copyOfRange(start, end)

    val shorts = ShortArray(pcmBytes.size / 2)
    ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
    return shorts
}
