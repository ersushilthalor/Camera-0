package com.example

import com.example.camera.model.CameraResolution
import com.example.camera.model.LensInfo
import com.example.camera.model.LensType
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleUnitTest {
    @Test
    fun testMainActivityLaunch() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        controller.setup()
        assertNotNull(controller.get())
    }

    @Test
    fun testMainActivityLaunchWithPermissionsGranted() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        val shadowApp = org.robolectric.Shadows.shadowOf(app)
        shadowApp.grantPermissions(android.Manifest.permission.CAMERA, android.Manifest.permission.RECORD_AUDIO)

        val controller = Robolectric.buildActivity(MainActivity::class.java)
        controller.setup()
        assertNotNull(controller.get())
    }

    @Test
    fun testCameraResolutionCalculations() {
        val res43 = CameraResolution(4000, 3000)
        assertEquals(12.0f, res43.megapixels, 0.01f)
        assertEquals("4:3", res43.aspectRatioLabel)

        val res169 = CameraResolution(3840, 2160)
        assertEquals(8.29f, res169.megapixels, 0.02f)
        assertEquals("16:9", res169.aspectRatioLabel)

        val res209 = CameraResolution(2400, 1080)
        assertEquals("20:9", res209.aspectRatioLabel)
    }

    @Test
    fun testLensInfoTypes() {
        val ultraWide = LensInfo(
            cameraId = "2",
            facing = android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK,
            lensType = LensType.ULTRAWIDE,
            displayName = "0.5x Ultra Wide",
            focalLengthMm = 1.94f,
            maxAperture = 2.2f,
            isPhysical = true,
            isHiddenAux = true,
            fovDegrees = 118f
        )
        assertEquals("0.5x", ultraWide.lensType.shortLabel)
        assertEquals("Ultra Wide", ultraWide.lensType.fullLabel)
        assertTrue(ultraWide.isPhysical)
        assertTrue(ultraWide.isHiddenAux)
    }

    @Test
    fun testMirrorSelfiePreferenceDefault() {
        val prefs = com.example.camera.data.CameraPreferences(
            androidx.test.core.app.ApplicationProvider.getApplicationContext()
        )
        // Default is true for "Save selfie as previewed (without flipping)"
        assertTrue(prefs.saveSelfieAsPreviewed)
        prefs.saveSelfieAsPreviewed = false
        assertFalse(prefs.saveSelfieAsPreviewed)
        prefs.saveSelfieAsPreviewed = true
        assertTrue(prefs.saveSelfieAsPreviewed)
    }

    @Test
    fun testVideoHdrModeAndPreferences() {
        val prefs = com.example.camera.data.CameraPreferences(
            androidx.test.core.app.ApplicationProvider.getApplicationContext()
        )
        // Default HDR is AUTO
        assertEquals(com.example.camera.model.VideoHdrMode.AUTO, prefs.videoHdrMode)
        assertEquals(50, prefs.videoHdrManualIntensity)

        prefs.videoHdrMode = com.example.camera.model.VideoHdrMode.MANUAL
        assertEquals(com.example.camera.model.VideoHdrMode.MANUAL, prefs.videoHdrMode)

        prefs.videoHdrManualIntensity = 80
        assertEquals(80, prefs.videoHdrManualIntensity)
    }

    @Test
    fun testVideoHdrEngineCalculations() {
        val engine = com.example.camera.engine.VideoHdrEngine()

        // 1. Initial mode is AUTO
        assertEquals(com.example.camera.model.VideoHdrMode.AUTO, engine.currentState.mode)

        // 2. Simulate frames in low-light / high-ISO scene
        // Simulating ISO 3200, 1/30s exposure (33_333_333 ns), aperture 1.8
        for (i in 0 until 10) {
            engine.processFrameValues(
                iso = 3200,
                exposureNs = 33_333_333L,
                aperture = 1.8f,
                focusDist = 1.5f
            )
        }

        val stateHighIso = engine.currentState
        assertTrue(stateHighIso.isHdrActive)
        // Shadows lifted for low-light scene
        assertTrue(stateHighIso.shadowLift > 0.2f)
        // Aggressive adaptive spatial + temporal noise reduction triggered for high ISO
        assertTrue(stateHighIso.noiseReductionStrength > 0.5f)
        assertTrue(stateHighIso.statusDescription.contains("Low-Light"))

        // 3. Test Manual Mode
        engine.mode = com.example.camera.model.VideoHdrMode.MANUAL
        engine.manualIntensity = 80
        for (i in 0 until 5) {
            engine.processFrameValues(
                iso = 400,
                exposureNs = 16_666_666L,
                aperture = 1.8f,
                focusDist = 2.0f
            )
        }
        val stateManual = engine.currentState
        assertEquals(80, stateManual.manualIntensity)
        assertTrue(stateManual.shadowLift > 0.25f)
        assertTrue(stateManual.highlightProtection > 0.25f)

        // 4. Test OFF Mode
        engine.mode = com.example.camera.model.VideoHdrMode.OFF
        engine.processFrameValues(
            iso = 400,
            exposureNs = 16_666_666L,
            aperture = 1.8f,
            focusDist = 2.0f
        )
        val stateOff = engine.currentState
        assertFalse(stateOff.isHdrActive)
        assertEquals(0f, stateOff.shadowLift, 0.001f)
        assertEquals(0f, stateOff.highlightProtection, 0.001f)
    }
}
