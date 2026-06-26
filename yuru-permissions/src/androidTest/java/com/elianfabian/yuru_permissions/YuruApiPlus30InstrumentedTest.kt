package com.elianfabian.yuru_permissions

import android.Manifest
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 30)
class YuruApiPlus30InstrumentedTest {

	@get:Rule
	val activityRule = ActivityScenarioRule(TestActivity::class.java)

	private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

	@Test
	fun testCameraPermissionTwoStrikesPermanentDenial() = runTest(timeout = 15.seconds) {
		val yuru = Yuru
		val controller = yuru.getOrCreateSinglePermissionController(Manifest.permission.CAMERA)

		// On API 30+, denying twice in a row results in Permanent Denial.
		repeat(2) {
			val resultDeferred = async {
				controller.request()
			}
			yield()

			val denyButton = device.findDenyButton()
			if (denyButton.waitForExists(5000)) {
				denyButton.click()
			}
			resultDeferred.await()
		}

		assertEquals(YuruPermissionState.PermanentlyDenied, controller.state.value)

		// Third request should return immediately without dialog
		val finalResult = controller.request()
		assertEquals(YuruPermissionState.PermanentlyDenied, finalResult)
	}

	@Test
	fun testMultiplePermissionsTwoStrikes() = runTest(timeout = 15.seconds) {
		val yuru = Yuru
		// Using CAMERA and READ_CONTACTS which are both in debug manifest
		val controller = yuru.getOrCreateMultiplePermissionController(
			Manifest.permission.CAMERA,
			Manifest.permission.READ_CONTACTS
		)

		repeat(2) {
			val resultDeferred = async {
				controller.request()
			}
			yield()

			val denyButton = device.findDenyButton()
			if (denyButton.waitForExists(5000)) {
				denyButton.click()
			}
			if (denyButton.waitForExists(5000)) {
				denyButton.click()
			}
			resultDeferred.await()
		}

		val state = controller.state.value
		assertEquals(YuruPermissionState.PermanentlyDenied, state[Manifest.permission.CAMERA])
		assertEquals(YuruPermissionState.PermanentlyDenied, state[Manifest.permission.READ_CONTACTS])
	}
}
