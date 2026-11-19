package com.example.telecom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.ui.screens.IncomingCallPresentationMode

/**
 * Handles direct 1-tap Answer, Decline, Expand-to-Full-Screen, and Restore-Banner actions
 * from the incoming-call notification and system call controls.
 */
class CallActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_ANSWER_CALL = "com.example.telecom.ACTION_ANSWER_CALL"
        const val ACTION_DECLINE_CALL = "com.example.telecom.ACTION_DECLINE_CALL"
        const val ACTION_TOGGLE_MUTE_CALL = "com.example.telecom.ACTION_TOGGLE_MUTE_CALL"
        const val ACTION_TOGGLE_SPEAKER_CALL = "com.example.telecom.ACTION_TOGGLE_SPEAKER_CALL"
        const val ACTION_EXPAND_FULL_INCOMING = "com.example.telecom.ACTION_EXPAND_FULL_INCOMING"
        const val ACTION_RESTORE_INCOMING_BANNER = "com.example.telecom.ACTION_RESTORE_INCOMING_BANNER"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val appContext = context.applicationContext
        when (intent?.action) {
            ACTION_ANSWER_CALL -> {
                CallStateManager.answerRingingCall(appContext)
            }
            ACTION_DECLINE_CALL -> {
                CallStateManager.rejectOrDisconnectCall(appContext)
            }
            ACTION_TOGGLE_MUTE_CALL -> {
                CallStateManager.toggleMute()
            }
            ACTION_TOGGLE_SPEAKER_CALL -> {
                CallStateManager.toggleSpeakerphone()
            }
            ACTION_EXPAND_FULL_INCOMING,
            ACTION_RESTORE_INCOMING_BANNER -> {
                CallStateManager.expandIncomingCallToFullScreen()
                CallStateManager.launchFullScreenInCallActivityIntent(
                    context = appContext,
                    isRinging = true
                )
            }
        }
    }
}
