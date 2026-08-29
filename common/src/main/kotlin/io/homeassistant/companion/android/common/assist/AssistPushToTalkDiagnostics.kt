package io.homeassistant.companion.android.common.assist

import android.os.SystemClock
import timber.log.Timber

/** Logcat markers for diagnosing Android system-assistant push-to-talk delivery. */
object AssistPushToTalkDiagnostics {
    private const val TAG = "AssistPTT"

    fun log(message: String) {
        Timber.tag(TAG).i("t=%d %s", SystemClock.elapsedRealtime(), message)
    }

    fun warn(message: String, throwable: Throwable? = null) {
        if (throwable == null) {
            Timber.tag(TAG).w("t=%d %s", SystemClock.elapsedRealtime(), message)
        } else {
            Timber.tag(TAG).w(throwable, "t=%d %s", SystemClock.elapsedRealtime(), message)
        }
    }
}
