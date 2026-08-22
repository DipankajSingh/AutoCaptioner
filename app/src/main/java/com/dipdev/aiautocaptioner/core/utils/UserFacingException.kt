package com.dipdev.aiautocaptioner.core.utils

/**
 * An exception used for expected user-facing validation errors (e.g., "Battery too low", "Model not found").
 * 
 * Throwing this exception will display the error message in the UI, but it will intentionally BYPASS 
 * Firebase Crashlytics to prevent polluting error reports with normal application control flow and 
 * avoid triggering fatal debug crashes.
 */
class UserFacingException(message: String) : Exception(message)
