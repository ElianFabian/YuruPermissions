package com.elianfabian.yuru_permissions

import android.Manifest
import com.elianfabian.yuru_permissions.testing.FakeYuru
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the [FakeYuru] environment.
 *
 * These tests demonstrate how to use the simulated environment to test
 * permission-related logic without Android dependencies.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FakeYuruTest {

	private val testDispatcher = UnconfinedTestDispatcher()

	@Test
	fun `test camera permission grant flow`() = runTest(testDispatcher) {
		val mockYuru = Yuru.createSimulatedYuruEnvironment()
		val controller = mockYuru.singlePermissionController(Manifest.permission.CAMERA)

		// 1. Initial State
		assertEquals(YuruPermissionState.NotDetermined, controller.state.value)

		// 2. Start request
		var result: YuruPermissionState? = null
		launch {
			result = controller.request()
		}

		// 3. Simulate user clicking "Allow"
		controller.accept()

		// Verify final state
		assertEquals(YuruPermissionState.Granted, result)
		assertEquals(YuruPermissionState.Granted, controller.state.value)
	}

	@Test
	fun `test clear storage resets all controllers`() = runTest(testDispatcher) {
		val mockYuru = Yuru.createSimulatedYuruEnvironment()
		val camera = mockYuru.singlePermissionController(Manifest.permission.CAMERA)

		// Grant camera
		launch { camera.request() }
		camera.accept()
		assertEquals(YuruPermissionState.Granted, camera.state.value)

		// Clear storage (simulates fresh app install)
		mockYuru.clearStorage()

		assertEquals(YuruPermissionState.NotDetermined, camera.state.value)
	}

	@Test
	fun `test multiple permissions reject all`() = runTest(testDispatcher) {
		val mockYuru = Yuru.createSimulatedYuruEnvironment()
		val permissions = listOf("p1", "p2")
		val controller = mockYuru.multiplePermissionController(permissions)

		var results: Map<String, YuruPermissionState>? = null
		launch {
			results = controller.request()
		}

		controller.rejectAll()

		assertEquals(YuruPermissionState.Denied, results!!["p1"])
		assertEquals(YuruPermissionState.Denied, results!!["p2"])
	}

	@Test
	fun `test two rejects change state to permanently denied`() = runTest(testDispatcher) {
		val mockYuru = Yuru.createSimulatedYuruEnvironment()
		val camera = mockYuru.singlePermissionController(Manifest.permission.CAMERA)

		// First reject
		launch { camera.request() }
		camera.reject()
		assertEquals(YuruPermissionState.Denied, camera.state.value)

		// Second reject triggers Permanent Denial logic in the fake backend
		launch { camera.request() }
		camera.reject()
		assertEquals(YuruPermissionState.PermanentlyDenied, camera.state.value)
	}

	@Test
	fun `test independent controllers for same permission share state`() = runTest(testDispatcher) {
		val mockYuru = Yuru.createSimulatedYuruEnvironment()
		val camera1 = mockYuru.singlePermissionController(Manifest.permission.CAMERA)
		val camera2 = mockYuru.singlePermissionController(Manifest.permission.CAMERA)

		assertEquals(YuruPermissionState.NotDetermined, camera1.state.value)
		assertEquals(YuruPermissionState.NotDetermined, camera2.state.value)

		launch { camera1.request() }
		camera1.accept()

		// Both controllers should be updated
		assertEquals(YuruPermissionState.Granted, camera1.state.value)
		assertEquals(YuruPermissionState.Granted, camera2.state.value)
	}

	@Test
	fun `test interleaved requests for different permissions`() = runTest(testDispatcher) {
		val mockYuru = Yuru.createSimulatedYuruEnvironment()
		val camera = mockYuru.singlePermissionController("p1")
		val contacts = mockYuru.singlePermissionController("p2")

		var cameraResult: YuruPermissionState? = null
		var contactsResult: YuruPermissionState? = null

		launch { cameraResult = camera.request() }
		launch { contactsResult = contacts.request() }

		// Resumes only the camera request
		camera.accept()
		assertEquals(YuruPermissionState.Granted, cameraResult)
		assertEquals(null, contactsResult)

		// Resumes the contacts request
		contacts.reject()
		assertEquals(YuruPermissionState.Denied, contactsResult)
	}

	@Test
	fun `test multiple permission controller partial grant`() = runTest(testDispatcher) {
		val mockYuru = Yuru.createSimulatedYuruEnvironment()
		val permissions = listOf("p1", "p2")
		val controller = mockYuru.multiplePermissionController(permissions)

		var results: Map<String, YuruPermissionState>? = null
		launch { results = controller.request() }

		// Manually update states for mixed results
		controller.updateStates(mapOf(
			"p1" to YuruPermissionState.Granted,
			"p2" to YuruPermissionState.Denied
		))

		val finalState = controller.state.value
		assertEquals(YuruPermissionState.Granted, finalState["p1"])
		assertEquals(YuruPermissionState.Denied, finalState["p2"])
		assertEquals(YuruPermissionState.Granted, results!!["p1"])
		assertEquals(YuruPermissionState.Denied, results!!["p2"])
	}

	@Test
	fun `test single controller and multiple controller interaction`() = runTest(testDispatcher) {
		val mockYuru = Yuru.createSimulatedYuruEnvironment()
		val single = mockYuru.singlePermissionController("p1")
		val multiple = mockYuru.multiplePermissionController(listOf("p1", "p2"))

		// Grant p1 via multiple controller
		launch { multiple.request() }
		multiple.acceptAll()

		// Single controller for p1 should be updated automatically
		assertEquals(YuruPermissionState.Granted, single.state.value)
		assertEquals(YuruPermissionState.Granted, multiple.state.value["p1"])
		assertEquals(YuruPermissionState.Granted, multiple.state.value["p2"])
	}

	@Test
	fun `test pre-emptive action resolves immediate request`() = runTest(testDispatcher) {
		val mockYuru = Yuru.createSimulatedYuruEnvironment()
		val controller = mockYuru.singlePermissionController(Manifest.permission.CAMERA)

		// 1. Accept BEFORE request (thanks to Result Queuing)
		controller.accept()

		// 2. Request should resolve immediately without suspending
		var result: YuruPermissionState? = null
		launch { result = controller.request() }

		assertEquals(YuruPermissionState.Granted, result)
	}

	@Test
	fun `test concurrent requests for same permission should both resolve`() = runTest(testDispatcher) {
		val mockYuru = Yuru.createSimulatedYuruEnvironment()
		val camera = mockYuru.singlePermissionController(Manifest.permission.CAMERA)

		var result1: YuruPermissionState? = null
		var result2: YuruPermissionState? = null

		launch { result1 = camera.request() }
		launch { result2 = camera.request() }

		camera.accept()

		assertEquals(YuruPermissionState.Granted, result1)
		assertEquals(YuruPermissionState.Granted, result2)
	}

	@Test(expected = IllegalArgumentException::class)
	fun `test multiple permission controller with only one permission should fail`() {
		val mockYuru = Yuru.createSimulatedYuruEnvironment()
		mockYuru.multiplePermissionController(listOf("p1"))
	}
}
