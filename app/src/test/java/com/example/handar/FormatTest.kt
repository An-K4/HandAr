package com.example.handar

import com.example.handar.utils.formatDuration

import org.junit.Test
import org.junit.Assert.*

class FormatTest {

    @Test
    fun `duration duoi 1 phut`() = assertEquals("00:07", formatDuration(7_000))

    @Test
    fun `duration co phut`() = assertEquals("01:23", formatDuration(83_000))

    @Test
    fun `duration bang 0`() = assertEquals("--:--", formatDuration(0))
}