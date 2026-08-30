package com.dipdev.aiautocaptioner.data.billing

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PremiumManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val prefs: SharedPreferences = context.getSharedPreferences("billing_prefs", Context.MODE_PRIVATE)
    private val _simulatedPremiumFlow = MutableStateFlow(prefs.getBoolean("simulated_premium", false))

    val isPremiumFlow: Flow<Boolean> = _simulatedPremiumFlow
}
