package com.dipdev.aiautocaptioner.engine.effects

import android.content.Context
import androidx.camera.core.CameraEffect
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CameraEffectManagerTest {

    private lateinit var effectManager: CameraEffectManager
    private lateinit var context: Context

    @Before
    fun setup() {
        effectManager = CameraEffectManager()
        context = mockk<Context>().also {
            every { it.applicationContext } returns it
        }
    }

    @Test
    fun `default effect manager initializes with default studio settings`() {
        assertEquals(0.35f, effectManager.getSmoothnessIntensity(), 0.001f)
        assertEquals(CreatorFilter.NATURAL, effectManager.getActiveFilter())
    }

    @Test
    fun `setSmoothnessIntensity clamps values between 0 and 1 without pipeline interrupts`() {
        effectManager.setSmoothnessIntensity(0.85f)
        assertEquals(0.85f, effectManager.getSmoothnessIntensity(), 0.001f)

        // Negative under-clamp check
        effectManager.setSmoothnessIntensity(-0.25f)
        assertEquals(0.0f, effectManager.getSmoothnessIntensity(), 0.001f)

        // Excessive over-clamp check
        effectManager.setSmoothnessIntensity(2.5f)
        assertEquals(1.0f, effectManager.getSmoothnessIntensity(), 0.001f)
    }

    @Test
    fun `setActiveFilter updates atomic filter reference and verifies unique shader index routing`() {
        val testedFilters = CreatorFilter.values()
        val observedIndices = mutableSetOf<Int>()

        testedFilters.forEach { filter ->
            effectManager.setActiveFilter(filter)
            assertEquals(filter, effectManager.getActiveFilter())
            observedIndices.add(filter.shaderIndex)
        }

        // Ensure every CreatorFilter maps to a unique integer GLSL uniform register
        assertEquals(testedFilters.size, observedIndices.size)
    }

    @Test
    fun `buildCameraEffects defaults to preview and video capture targets`() {
        val effects = effectManager.buildCameraEffects(context)
        assertEquals(2, effects.size)
        val targets = effects.fold(0) { acc, effect -> acc or effect.targets }
        assertTrue(targets and CameraEffect.PREVIEW != 0)
        assertTrue(targets and CameraEffect.VIDEO_CAPTURE != 0)
    }

    @Test
    fun `buildCameraEffects targets only the requested use cases`() {
        val previewEffects = effectManager.buildCameraEffects(context, CameraEffect.PREVIEW)
        assertEquals(1, previewEffects.size)
        assertTrue(previewEffects.single().targets == CameraEffect.PREVIEW)

        val videoEffects = effectManager.buildCameraEffects(context, CameraEffect.VIDEO_CAPTURE)
        assertEquals(1, videoEffects.size)
        assertTrue(videoEffects.single().targets == CameraEffect.VIDEO_CAPTURE)
    }

    @Test
    fun `release safely executes when GPU processor is uninitialized or already released`() {
        // Must complete cleanly without NullPointerException or open EGL leak throws
        effectManager.release()
        
        // Multiple successive calls should remain idempotent
        effectManager.release()
        assertNotNull(effectManager)
    }
}
