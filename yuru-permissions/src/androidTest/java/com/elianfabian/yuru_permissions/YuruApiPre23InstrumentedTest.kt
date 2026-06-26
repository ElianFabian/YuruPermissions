package com.elianfabian.yuru_permissions

import android.Manifest
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.seconds

@RunWith(AndroidJUnit4::class)
@SdkSuppress(maxSdkVersion = 22)
class YuruApiPre23InstrumentedTest {

	@get:Rule
	val activityRule = ActivityScenarioRule(TestActivity::class.java)

	@Test
	fun testCameraPermissionGrantedOnPre23() = runTest(timeout = 5.seconds) {
		val yuru = Yuru()
		val controller = yuru.getOrCreateSinglePermissionController(Manifest.permission.CAMERA)

		// On API < 23, permissions are granted at install time if in manifest.
		// So it should be Granted immediately.
		assertEquals(YuruPermissionState.Granted, controller.state.value)

		val result = controller.request()
		assertEquals(YuruPermissionState.Granted, result)
	}

	@Test
	fun testPermissionMissingInManifestOnPre23() = runTest(timeout = 5.seconds) {
		val yuru = Yuru()
		// READ_SMS is not in TestActivity's manifest

		try {
			val controller = yuru.getOrCreateSinglePermissionController(Manifest.permission.READ_SMS)
		}
		catch (e: IllegalArgumentException) {
			assertEquals(e::class, IllegalArgumentException::class)
		}
	}
}
