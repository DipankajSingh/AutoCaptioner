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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PremiumManager @Inject constructor() {

    companion object {
        const val ENTITLEMENT_PRO = "AutoCaptioner Pro"
    }

    private val _customerInfoFlow = MutableSharedFlow<CustomerInfo>(replay = 1)
    val customerInfoFlow: Flow<CustomerInfo> = _customerInfoFlow.asSharedFlow()

    init {
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

    val isPremiumFlow: Flow<Boolean> = customerInfoFlow.map { customerInfo ->
        customerInfo.entitlements[ENTITLEMENT_PRO]?.isActive == true
    }.distinctUntilChanged()

    suspend fun purchaseLifetime(activity: Activity): StoreTransaction? {
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

