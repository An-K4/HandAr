package com.example.handar.utils

import android.content.Context
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

fun loadWavPcm(context: Context, resId: Int): ShortArray {
    val bytes = context.resources.openRawResource(resId).readBytes()
    val pcmBytes = bytes.copyOfRange(44, bytes.size) // header wav chuẩn = 44 byte
    val shorts = ShortArray(pcmBytes.size / 2)
    ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
    return shorts
}