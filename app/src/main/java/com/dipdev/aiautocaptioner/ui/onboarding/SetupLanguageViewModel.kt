package com.dipdev.aiautocaptioner.ui.onboarding

import android.content.Context
import android.telephony.TelephonyManager
import androidx.lifecycle.ViewModel
import com.dipdev.aiautocaptioner.core.whisper.WhisperLanguages
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class SetupLanguageViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _selectedLanguage = MutableStateFlow<String?>(null)
    val selectedLanguage: StateFlow<String?> = _selectedLanguage.asStateFlow()

    val orderedLanguages: List<String> = WhisperLanguages.orderedCodes(
        getCountryCode(context),
        Locale.getDefault().language
    )

    fun selectLanguage(code: String) {
        _selectedLanguage.value = code
    }

    private fun getCountryCode(context: Context): String? {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val networkCountry = tm?.networkCountryIso
        if (!networkCountry.isNullOrEmpty()) {
            return networkCountry
        }
        val simCountry = tm?.simCountryIso
        if (!simCountry.isNullOrEmpty()) {
            return simCountry
        }
        val localeCountry = Locale.getDefault().country
        if (!localeCountry.isNullOrEmpty()) {
            return localeCountry
        }
        return null
    }
}
