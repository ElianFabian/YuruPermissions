package com.elianfabian.yuru_permissions

import android.Manifest
import android.widget.Toast
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class YuruInstrumentedTest {


	// TODO: add library to set the permission state in the test app and add more tests

	@get:Rule
	val activityRule = ActivityScenarioRule(TestActivity::class.java)

	fun showDebugToast(message: String) {
		activityRule.scenario.onActivity { activity ->
			Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
		}
	}

	private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

	@Before
	fun setup() {

	}

	@Test
	fun testCameraPermissionGrant() = runBlocking {
		val yuru = Yuru()
		val controller = yuru.getOrCreateSinglePermissionController(Manifest.permission.CAMERA)

		// 1. Initial State should be NotDetermined (assuming fresh install)
		// Note: On real device it might be Denied if previously rejected.
		assertEquals(YuruPermissionState.NotDetermined, controller.state.value)

		val resultDeferred = async {
			controller.request()
		}
		yield()

		// 2. Wait for system dialog and click "Allow" (or "While using the app")
		// The button text varies by Android version.
		val allowButton = device.findObject(
			UiSelector().textMatches("(?i)Allow|While using the app|Only this time")
		)

		if (allowButton.waitForExists(1000)) {
			allowButton.click()
		}

		val result = resultDeferred.await()

		assertEquals(YuruPermissionState.Granted, result)
		assertEquals(YuruPermissionState.Granted, controller.state.value)
	}

	@Test
	fun testCameraPermissionDeny() = runBlocking {
		val yuru = Yuru()
		val controller = yuru.getOrCreateSinglePermissionController(Manifest.permission.CAMERA)

		// 1. Initial State should be NotDetermined (assuming fresh install)
		// Note: On real device it might be Denied if previously rejected.
		assertEquals(YuruPermissionState.NotDetermined, controller.state.value)

		val resultDeferred = async {
			controller.request()
		}
		yield()

		// 2. Wait for system dialog and click "Deny"
		val denyButton = device.findObject(
			UiSelector().textMatches("(?i)Don't allow|Deny")
		)

		if (denyButton.waitForExists(5000)) {
			denyButton.click()
		}

		val result = resultDeferred.await()

		assertEquals(YuruPermissionState.Denied, result)
		assertEquals(YuruPermissionState.Denied, controller.state.value)
	}
}
