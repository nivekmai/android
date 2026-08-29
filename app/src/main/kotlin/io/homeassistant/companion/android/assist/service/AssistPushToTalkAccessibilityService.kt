package io.homeassistant.companion.android.assist.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import io.homeassistant.companion.android.common.assist.AssistPushToTalkDiagnostics

/**
 * Opt-in diagnostic service that observes global key events without consuming them.
 *
 * Android may withhold reserved system keys such as Power even from an accessibility service;
 * logging each observed Power transition lets us test that boundary on the target device.
 */
class AssistPushToTalkAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        AssistPushToTalkDiagnostics.log(
            "accessibility.onServiceConnected flags=${serviceInfo.flags} " +
                "canFilter=${serviceInfo.capabilities and AccessibilityServiceInfo.CAPABILITY_CAN_REQUEST_FILTER_KEY_EVENTS != 0}",
        )
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_POWER) {
            AssistPushToTalkDiagnostics.log(
                "accessibility.onKeyEvent POWER action=${event.action} repeat=${event.repeatCount} " +
                    "flags=${event.flags} canceled=${event.isCanceled}",
            )
            if (event.action == KeyEvent.ACTION_UP) {
                AssistPushToTalkController.releaseActive("accessibility-power-up")
            }
        }
        return false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) = Unit

    override fun onInterrupt() {
        AssistPushToTalkDiagnostics.log("accessibility.onInterrupt")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        AssistPushToTalkDiagnostics.log("accessibility.onUnbind")
        return super.onUnbind(intent)
    }
}
