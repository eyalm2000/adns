package com.eyalm.adns

import androidx.annotation.Keep

@Keep
class PrivilegedService : IPrivilegedService.Stub() {
    override fun grantWriteSecureSettings(packageName: String): Boolean {
        val proc = ProcessBuilder(
            "pm", "grant",
            packageName,
            "android.permission.WRITE_SECURE_SETTINGS"
        ).redirectErrorStream(true).start()

        val output = proc.inputStream.bufferedReader().use { it.readText() }
        val code = proc.waitFor()

        if (code != 0) {
            throw RuntimeException("pm grant failed: exit=$code, output=$output")
        }
        return true
    }
}