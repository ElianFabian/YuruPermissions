package com.elianfabian.yuru_permissions.internal

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import com.elianfabian.activity_provider.ActivityProviderInitializer
import com.elianfabian.yuru_permissions.YuruPermissionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 35])
class PermissionBridgeIntegrationTest {
    private lateinit var application: Application
    private lateinit var owner: ActivityController<ComponentActivity>
    private val controllers = mutableListOf<ActivityController<ComponentActivity>>()

    @Before fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        ActivityProviderInitializer().create(application)
        shadowOf(application).denyPermissions(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        owner = activity()
    }

    @After fun tearDown() {
        controllers.filter { !it.get().isDestroyed }.forEach { it.pause().stop().destroy() }
    }

    private fun activity(savedState: Bundle? = null): ActivityController<ComponentActivity> =
        Robolectric.buildActivity(ComponentActivity::class.java).also { controllers.add(it) }
            .create(savedState).start().resume()

    // Use the framework entry point so Android also clears its outstanding-dialog flag.
    private fun permissionResult(code: Int, permissions: Array<String>, results: IntArray) {
        val data = Intent()
            .putExtra("android.content.pm.extra.REQUEST_PERMISSIONS_NAMES", permissions)
            .putExtra("android.content.pm.extra.REQUEST_PERMISSIONS_RESULTS", results)
        Activity::class.java.getDeclaredMethod("dispatchRequestPermissionsResult", Int::class.javaPrimitiveType, Intent::class.java)
            .apply { isAccessible = true }.invoke(owner.get(), code, data)
    }

    @Test fun `single controller receives granted result through real bridge`() = runTest(timeout = 5.seconds) {
        val controller = RealYuruBackend().createSingleController(Manifest.permission.CAMERA)
        val result = async { controller.request() }
        runCurrent()
        val request = shadowOf(owner.get()).lastRequestedPermission
        shadowOf(application).grantPermissions(Manifest.permission.CAMERA)
        permissionResult(request.requestCode, arrayOf(Manifest.permission.CAMERA), intArrayOf(PackageManager.PERMISSION_GRANTED))
        assertEquals(YuruPermissionState.Granted, result.await())
        assertEquals(YuruPermissionState.Granted, controller.state.value)
    }

    @Test fun `single controller retains denied state when rationale is available`() = runTest(timeout = 5.seconds) {
        val controller = RealYuruBackend().createSingleController(Manifest.permission.CAMERA)
        val result = async { controller.request() }
        runCurrent()
        val request = shadowOf(owner.get()).lastRequestedPermission
        shadowOf(application.packageManager).setShouldShowRequestPermissionRationale(Manifest.permission.CAMERA, true)
        permissionResult(request.requestCode, arrayOf(Manifest.permission.CAMERA), intArrayOf(PackageManager.PERMISSION_DENIED))
        assertEquals(YuruPermissionState.Denied, result.await())
        assertEquals(YuruPermissionState.Denied, controller.state.value)
    }

    @Test fun `multiple permissions update independently through real bridge`() = runTest(timeout = 5.seconds) {
        val controller = RealYuruBackend().createMultipleController(listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        val result = async { controller.request() }
        runCurrent()
        val request = shadowOf(owner.get()).lastRequestedPermission
        shadowOf(application).grantPermissions(Manifest.permission.CAMERA)
        shadowOf(application.packageManager).setShouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO, true)
        permissionResult(request.requestCode, arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO), intArrayOf(PackageManager.PERMISSION_GRANTED, PackageManager.PERMISSION_DENIED))
        val states = result.await()
        assertEquals(YuruPermissionState.Granted, states[Manifest.permission.CAMERA])
        assertEquals(YuruPermissionState.Denied, states[Manifest.permission.RECORD_AUDIO])
        assertEquals(states, controller.state.value)
    }

    @Test fun `permission request survives owner recreation`() = runTest(timeout = 5.seconds) {
        val controller = RealYuruBackend().createSingleController(Manifest.permission.CAMERA)
        val result = async { controller.request() }
        runCurrent()
        val code = shadowOf(owner.get()).lastRequestedPermission.requestCode
        val savedState = Bundle()
        owner.pause().saveInstanceState(savedState).stop().destroy()
        owner = activity(savedState)
        shadowOf(application).grantPermissions(Manifest.permission.CAMERA)
        permissionResult(code, arrayOf(Manifest.permission.CAMERA), intArrayOf(PackageManager.PERMISSION_GRANTED))
        assertEquals(YuruPermissionState.Granted, result.await())
        assertEquals(YuruPermissionState.Granted, controller.state.value)
    }

    @Test fun `cancelled permission request does not affect the next request`() = runTest(timeout = 5.seconds) {
        val controller = RealYuruBackend().createSingleController(Manifest.permission.CAMERA)
        val cancelled = async { controller.request() }
        runCurrent()
        val previousCode = shadowOf(owner.get()).lastRequestedPermission.requestCode
        cancelled.cancelAndJoin()
        // Cancelling a coroutine does not dismiss the native permission dialog.
        permissionResult(previousCode, arrayOf(Manifest.permission.CAMERA), intArrayOf(PackageManager.PERMISSION_DENIED))
        runCurrent()
        val next = async { controller.request() }
        runCurrent()
        val nextCode = shadowOf(owner.get()).lastRequestedPermission.requestCode
        assertNotEquals(previousCode, nextCode)
        assertFalse(next.isCompleted)
        shadowOf(application).grantPermissions(Manifest.permission.CAMERA)
        permissionResult(nextCode, arrayOf(Manifest.permission.CAMERA), intArrayOf(PackageManager.PERMISSION_GRANTED))
        assertEquals(YuruPermissionState.Granted, next.await())
    }
}
