package com.dipdev.aiautocaptioner.data.billing

import android.app.Activity
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesException
import com.revenuecat.purchases.awaitCustomerInfo
import com.revenuecat.purchases.awaitOfferings
import com.revenuecat.purchases.awaitPurchase
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import com.revenuecat.purchases.models.StoreTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.remoteConfig
import javax.inject.Inject
import javax.inject.Singleton
import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart

@Singleton
class PremiumManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        const val ENTITLEMENT_PRO = "AutoCaptioner Pro"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences("billing_prefs", Context.MODE_PRIVATE)
    private val _simulatedPremiumFlow = MutableStateFlow(prefs.getBoolean("simulated_premium", false))

    private val _customerInfoFlow = MutableSharedFlow<CustomerInfo>(replay = 1)
    val customerInfoFlow: Flow<CustomerInfo> = _customerInfoFlow.asSharedFlow()

    // formattedPrice logic removed as it's hardcoded in UI

    init {
        if (Purchases.isConfigured) {
            Purchases.sharedInstance.updatedCustomerInfoListener = UpdatedCustomerInfoListener { customerInfo ->
                _customerInfoFlow.tryEmit(customerInfo)
            }

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val initialInfo = Purchases.sharedInstance.awaitCustomerInfo()
                    _customerInfoFlow.tryEmit(initialInfo)
                } catch (e: Exception) {
                    // Ignore initial error
                }
            }
        }
    }

    val isPremiumFlow: Flow<Boolean> = combine(
        customerInfoFlow.map { it.entitlements[ENTITLEMENT_PRO]?.isActive == true }.onStart { emit(false) },
        _simulatedPremiumFlow
    ) { isRealPremium, isSimulatedPremium ->
        isRealPremium || isSimulatedPremium
    }.distinctUntilChanged()


    suspend fun purchaseLifetime(activity: Activity): StoreTransaction? {
        val isUnlocked = Firebase.remoteConfig.getBoolean("is_premium_unlocked_for_testing")
        if (isUnlocked) {
            // Simulate a successful purchase to test the UX flow
            prefs.edit().putBoolean("simulated_premium", true).apply()
            _simulatedPremiumFlow.value = true
            return null
        }

        if (!Purchases.isConfigured) return null

        return try {
            val offerings = Purchases.sharedInstance.awaitOfferings()
            val packageToBuy = offerings.current?.lifetime
                ?: offerings.current?.availablePackages?.firstOrNull()

            if (packageToBuy != null) {
                val params = PurchaseParams.Builder(activity, packageToBuy).build()
                val purchaseResult = Purchases.sharedInstance.awaitPurchase(params)
                purchaseResult.storeTransaction
            } else {
                null
            }
        } catch (e: PurchasesException) {
            e.printStackTrace()
            null
        }
    }
}
