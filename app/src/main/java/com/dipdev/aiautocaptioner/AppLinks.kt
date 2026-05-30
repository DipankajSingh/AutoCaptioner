package com.dipdev.aiautocaptioner

import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.remoteConfig

/**
 * Central place for all external URLs and app-wide constants.
 * Update these before publishing a release.
 */
object AppLinks {

    // ── Legal ────────────────────────────────────────────────────────────────
    val PRIVACY_POLICY: String
        get() = Firebase.remoteConfig.getString("privacy_policy_url")
            .takeIf { it.isNotBlank() } ?: "https://dipdevlabs.example.com/privacy"

    val TERMS_OF_SERVICE: String
        get() = Firebase.remoteConfig.getString("terms_of_service_url")
            .takeIf { it.isNotBlank() } ?: "https://dipdevlabs.example.com/terms"

    // ── Developer / Store ────────────────────────────────────────────────────
    val DEVELOPER_WEBSITE: String
        get() = Firebase.remoteConfig.getString("developer_website_url")
            .takeIf { it.isNotBlank() } ?: "https://dipdevlabs.example.com"

    const val PLAY_STORE = "https://play.google.com/store/apps/details?id=com.dipdev.aiautocaptioner"
    const val GITHUB = "https://github.com/dipdevlabs/autocaptioner"

    // ── Support ──────────────────────────────────────────────────────────────
    const val SUPPORT_EMAIL = "support@dipdevlabs.example.com"
    const val BUG_REPORT = "https://github.com/dipdevlabs/autocaptioner/issues"
}
