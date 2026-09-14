package com.volp.travelbudget.util

import android.content.Context
import android.content.pm.PackageManager
import java.security.MessageDigest

/**
 * 지금 돌고 있는 앱이 어떤 키로 서명됐는지.
 *
 * 지도 키 제한과 Google 로그인은 모두 이 지문을 보고 앱을 확인한다. 지문이 다르면 아무
 * 설명 없이 조용히 실패하므로, 폰에서 바로 확인할 수 있게 설정 화면에 띄운다.
 */
object BuildIdentity {

    fun signingSha1(context: Context): String? = runCatching {
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_SIGNING_CERTIFICATES,
        )
        val signer = info.signingInfo?.apkContentsSigners?.firstOrNull() ?: return null

        MessageDigest.getInstance("SHA-1")
            .digest(signer.toByteArray())
            .joinToString(":") { "%02X".format(it) }
    }.getOrNull()

    /** 디버그 키로 서명된 빌드인지. 디버그 키는 업데이트로 덮어쓸 수 없다. */
    fun isDebugSigned(sha1: String?): Boolean = sha1 == ANDROID_DEBUG_KEY_SHA1

    /** 안드로이드 SDK가 기본으로 만드는 디버그 키의 지문. 모든 기기에서 같다. */
    private const val ANDROID_DEBUG_KEY_SHA1 =
        "A4:0D:A8:0A:59:D1:70:CA:A9:50:CF:15:C1:8C:45:4D:47:A3:9B:26"
}
