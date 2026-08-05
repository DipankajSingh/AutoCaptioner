package com.dipdev.aiautocaptioner.ui.recorder

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dipdev.aiautocaptioner.engine.effects.CreatorFilter
import com.dipdev.aiautocaptioner.ui.recorder.components.FilterCarousel
import com.dipdev.aiautocaptioner.ui.recorder.components.FloatingFilterBadge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmartRecorderUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun recordButton_idleState_clickTriggersCallback() {
        var clicked = false
        composeTestRule.setContent {
            RecordButton(isRecording = false) {
                clicked = true
            }
        }

        composeTestRule.onNodeWithTag("RecordButton")
            .assertIsDisplayed()
            .performClick()

        assertTrue("RecordButton onClick callback should be invoked", clicked)
    }

    @Test
    fun recordButton_activeRecordingState_rendersWithoutError() {
        var clicked = false
        composeTestRule.setContent {
            RecordButton(isRecording = true) {
                clicked = true
            }
        }

        composeTestRule.onNodeWithTag("RecordButton")
            .assertIsDisplayed()
            .performClick()

        assertTrue(clicked)
    }

    @Test
    fun filterCarousel_selectsFilter_andTriggersCallback() {
        var selectedFilter: CreatorFilter? = null
        composeTestRule.setContent {
            FilterCarousel(
                activeFilter = CreatorFilter.NATURAL,
                onFilterSelected = { selectedFilter = it }
            )
        }

        composeTestRule.onNodeWithText(CreatorFilter.VIBRANT.displayName)
            .assertIsDisplayed()
            .performClick()

        assertEquals(CreatorFilter.VIBRANT, selectedFilter)
    }

    @Test
    fun floatingFilterBadge_displaysFilterName_whenFilterChanges() {
        composeTestRule.setContent {
            FloatingFilterBadge(activeFilter = CreatorFilter.WARM_GLOW)
        }

        composeTestRule.onNodeWithText(CreatorFilter.WARM_GLOW.displayName, useUnmergedTree = true)
            .assertExists()
    }
}
