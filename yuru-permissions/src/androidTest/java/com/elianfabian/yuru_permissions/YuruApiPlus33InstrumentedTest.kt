package com.elianfabian.yuru_permissions

import android.Manifest
import android.widget.Toast
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 33)
class YuruApiPlus33InstrumentedTest {

	@get:Rule
	val activityRule = ActivityScenarioRule(TestActivity::class.java)

	//private val context = InstrumentationRegistry.getInstrumentation().targetContext
	//private val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation

	fun showDebugToast(message: String) {
		activityRule.scenario.onActivity { activity ->
			Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
		}
	}

	private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

	@Before
	fun setup() {
		//uiAutomation.revokeRuntimePermission(context.packageName, Manifest.permission.CAMERA)
	}

	@Test
	fun testCameraPermissionGrant() = runTest(timeout = 15.seconds) {
		val yuru = Yuru
		val controller = yuru.singlePermissionController(Manifest.permission.CAMERA)

		// 1. Initial State should be NotDetermined (assuming fresh installation)
		assertEquals(YuruPermissionState.NotDetermined, controller.state.value)

		val resultDeferred = async {
			controller.request()
		}
		yield()

		// 2. Wait for system dialog and click "Allow" (or "While using the app")
		// Increased timeout and improved selector
		val allowButton = device.findAllowButton()

		if (allowButton.waitForExists(5000)) {
			allowButton.click()
		}

		val result = resultDeferred.await()

		assertEquals(YuruPermissionState.Granted, result)
		assertEquals(YuruPermissionState.Granted, controller.state.value)
	}

	@Test
	fun testCameraPermissionDeny() = runTest(timeout = 15.seconds) {
		val yuru = Yuru
		val controller = yuru.singlePermissionController(Manifest.permission.CAMERA)

		assertEquals(controller.state.value, YuruPermissionState.NotDetermined)

		val resultDeferred = async {
			controller.request()
		}
		yield()

		// 2. Wait for system dialog and click "Deny"
		val denyButton = device.findDenyButton()

		if (denyButton.waitForExists(5000)) {
			denyButton.click()
		}

		val result = resultDeferred.await()

		assertEquals(YuruPermissionState.Denied, result)
		assertEquals(YuruPermissionState.Denied, controller.state.value)
	}

	@Test(expected = IllegalArgumentException::class)
	fun testWrongPermissionString() {
		val yuru = Yuru
		// This should throw IllegalArgumentException immediately
		yuru.singlePermissionController("INVALID_PERMISSION_NAME")
	}

	@Test
	fun testPermissionMissingInManifest() = runTest(timeout = 15.seconds) {
		val yuru = Yuru
		// READ_SMS is not in TestActivity's manifest
		val controller = yuru.singlePermissionController(Manifest.permission.READ_SMS)

		val result = controller.request()

		// If not in manifest, Android usually returns PERMISSION_DENIED immediately.
		// Since it was never asked and not in manifest, it stays NotDetermined or maps to Denied?
		// According to our Util.kt logic, PERMISSION_DENIED + no rationale + no prefs = NotDetermined.
		assertEquals(YuruPermissionState.NotDetermined, result)
	}

	@Test
	fun testMultiplePermissionsGrant() = runTest(timeout = 15.seconds) {
		val yuru = Yuru
		// Only CAMERA is in manifest for TestActivity. 
		// To test multiple, we'd need them in the manifest.
		// For now, let's use a subset or add them to debug manifest.
		// I'll assume only CAMERA is there for now, so I'll add CAMERA and another one.

		// Actually, I'll test with CAMERA and something else not in manifest to see partial behavior.
		val controller = yuru.multiplePermissionController(
			Manifest.permission.CAMERA,
			Manifest.permission.READ_SMS // Not in manifest
		)

		val resultDeferred = async {
			controller.request()
		}
		yield()

		val allowButton = device.findAllowButton()
		if (allowButton.waitForExists(5000)) {
			allowButton.click()
		}

		val result = resultDeferred.await()

		// CAMERA should be Granted, READ_SMS should be NotDetermined
		assertEquals(YuruPermissionState.Granted, result[Manifest.permission.CAMERA])
		assertEquals(YuruPermissionState.NotDetermined, result[Manifest.permission.READ_SMS])
	}

	@Test
	fun testCameraPermissionPermanentlyDenied() = runTest(timeout = 15.seconds) {
		val yuru = Yuru
		val controller = yuru.singlePermissionController(Manifest.permission.CAMERA)

		// This test assumes we can deny twice. 
		// Note: On newer Android, "Don't allow" then "Don't allow" makes it permanent.

		repeat(2) {
			val resultDeferred = async {
				controller.request()
			}
			yield()

			val denyButton = device.findDenyButton()
			if (denyButton.waitForExists(2000)) {
				denyButton.click()
			}
			resultDeferred.await()
		}

		assertEquals(YuruPermissionState.PermanentlyDenied, controller.state.value)

		// One more request should return immediately without dialog
		val finalResult = controller.request()
		assertEquals(YuruPermissionState.PermanentlyDenied, finalResult)
	}
}
