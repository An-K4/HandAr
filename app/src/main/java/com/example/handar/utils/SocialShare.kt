package com.example.handar.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File

// mở app mạng xã hội kèm sẵn video (nếu app đó có cài) bằng Intent.ACTION_SEND thường để chia sẻ
enum class SocialTarget(val packageCandidates: List<String>, val playStorePackage: String) {
    FACEBOOK(listOf("com.facebook.katana"), "com.facebook.katana"),
    INSTAGRAM(listOf("com.instagram.android"), "com.instagram.android"),
    TIKTOK(
        listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"),
        "com.zhiliaoapp.musically"
    ),
    YOUTUBE(listOf("com.google.android.youtube"), "com.google.android.youtube")
}

/**
 * app đích có cài thì mở app đó kèm sẵn [videoFile] (qua FileProvider, dùng lại đúng authority
 * đã cấu hình cho màn share); không có app nào trong [SocialTarget.packageCandidates] được
 * cài, hoặc app từ chối nhận intent (ActivityNotFoundException — ví dụ bản rút gọn không có màn
 * nhận share), đều rơi về mở trang app đó trên chplay.
 */
fun shareVideoToSocialApp(
    context: Context,
    target: SocialTarget,
    videoFile: File,
    caption: String
) {
    val installedPackage = target.packageCandidates.firstOrNull { isAppInstalled(context, it) }
    if (installedPackage == null) {
        openPlayStore(context, target.playStorePackage)
        return
    }

    val videoUri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", videoFile)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, videoUri)
        putExtra(Intent.EXTRA_TEXT, caption)
        setPackage(installedPackage)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        openPlayStore(context, target.playStorePackage)
    }
}

private fun isAppInstalled(context: Context, packageName: String): Boolean =
    try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

private fun openPlayStore(context: Context, packageId: String) {
    val marketIntent = Intent(Intent.ACTION_VIEW, "market://details?id=$packageId".toUri())
    try {
        context.startActivity(marketIntent)
    } catch (e: ActivityNotFoundException) {
        // máy không có app play store (hiếm) — mở bằng trình duyệt thay thế.
        context.startActivity(
            Intent(
                Intent.ACTION_VIEW,
                "https://play.google.com/store/apps/details?id=$packageId".toUri()
            )
        )
    }
}
