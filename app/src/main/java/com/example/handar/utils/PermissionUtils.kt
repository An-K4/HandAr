package com.example.handar.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Mở màn cài đặt quyền của chính app (App info) để người dùng bật quyền thủ công.
 * Một số máy không có màn này, khi đó fallback sang danh sách cài đặt app của hệ thống.
 */
fun Context.openAppSettings() {
    val detailsIntent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null)
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    try {
        startActivity(detailsIntent)
    } catch (_: ActivityNotFoundException) {
        try {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: ActivityNotFoundException) {
            // không có màn cài đặt nào mở được: bỏ qua, luồng gọi sẽ tự xử lý.
        }
    }
}
