package com.dipdev.aiautocaptioner.core.diagnostics

import android.content.Context
import com.dipdev.aiautocaptioner.data.repository.SettingsRepository
import io.mockk.mockk
import org.junit.Assert.assertTrue
import org.junit.Test

class TxAnalyticsTest {

    private val context: Context = mockk(relaxed = true)
    private val settingsRepository: SettingsRepository = mockk(relaxed = true)
    private val txAnalytics = TxAnalytics(context, settingsRepository)

    @Test
    fun `logRun with mock record does not throw`() {
        val record = TranscriptionRunRecord(
            runId = "test_run",
            modelId = "tiny.en",
            languageCode = "en",
            clipDurationMs = 30000L,
            whisperWallDurationMs = 5000L,
            totalDurationMs = 7000L
        )

        var exceptionThrown = false
        try {
            txAnalytics.logRun(record)
        } catch (_: Throwable) {
            exceptionThrown = true
        }

        assertTrue(!exceptionThrown)
    }

    @Test
    fun `logModelDownload with null or valid input does not throw`() {
        var exceptionThrown = false
        try {
            txAnalytics.logModelDownload("tiny.en", 77L, 1000L, "WIFI", "SUCCESS")
        } catch (_: Throwable) {
            exceptionThrown = true
        }

        assertTrue(!exceptionThrown)
    }
}
