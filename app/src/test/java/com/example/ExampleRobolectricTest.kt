package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.bridge.AndroidAppActionBridge
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read app name from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Arushi AI", appName)
  }

  @Test
  fun `test action bridge openWhatsApp fallback execution`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val bridge = AndroidAppActionBridge(context)
    val result = bridge.openWhatsApp()
    assertNotNull(result)
    assertEquals("openWhatsApp", result.actionType)
  }

  @Test
  fun `test action bridge makeCall safe fallback`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val bridge = AndroidAppActionBridge(context)
    val result = bridge.makeCall("+91 98765-43210")
    assertNotNull(result)
    assertEquals("makeCall", result.actionType)
    assertEquals("+919876543210", result.target)
    assertTrue(result.success)
  }

  @Test
  fun `test action bridge openUrl formatting`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val bridge = AndroidAppActionBridge(context)
    val result = bridge.openUrl("google.com")
    assertEquals("openUrl", result.actionType)
    assertEquals("https://google.com", result.target)
    assertTrue(result.success)
  }

  @Test
  fun `test js bridge interface returns valid json`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val bridge = AndroidAppActionBridge(context)
    val jsBridge = bridge.getBridgeInterface()
    assertTrue(jsBridge.isBridgeAvailable())

    val jsonOutput = jsBridge.makeCall("1234567890")
    val jsonObj = JSONObject(jsonOutput)
    assertEquals("makeCall", jsonObj.getString("actionType"))
    assertEquals("1234567890", jsonObj.getString("target"))
  }
}
