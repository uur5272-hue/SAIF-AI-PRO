package com.example.bridge

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.webkit.JavascriptInterface
import androidx.core.content.ContextCompat
import com.example.model.ActionResult
import com.example.model.ContactItem
import org.json.JSONArray
import org.json.JSONObject

class AndroidAppActionBridge(private val context: Context) {

    fun openWhatsApp(): ActionResult {
        return try {
            val pm = context.packageManager
            val launchIntent = pm.getLaunchIntentForPackage("com.whatsapp")
                ?: pm.getLaunchIntentForPackage("com.whatsapp.w4b")

            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                ActionResult(
                    actionType = "openWhatsApp",
                    target = "WhatsApp",
                    success = true,
                    message = "WhatsApp opened successfully."
                )
            } else {
                // Try deep link intent
                val deepLinkIntent = Intent(Intent.ACTION_VIEW, Uri.parse("whatsapp://send"))
                deepLinkIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (deepLinkIntent.resolveActivity(pm) != null) {
                    context.startActivity(deepLinkIntent)
                    ActionResult(
                        actionType = "openWhatsApp",
                        target = "WhatsApp",
                        success = true,
                        message = "WhatsApp opened."
                    )
                } else {
                    // Try browser WhatsApp Web as safe fallback
                    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send"))
                    webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(webIntent)
                    ActionResult(
                        actionType = "openWhatsApp",
                        target = "WhatsApp (Web fallback)",
                        success = true,
                        message = "WhatsApp app is not installed. Opened WhatsApp web instead."
                    )
                }
            }
        } catch (e: Exception) {
            ActionResult(
                actionType = "openWhatsApp",
                target = "WhatsApp",
                success = false,
                message = "Could not open WhatsApp: ${e.localizedMessage ?: "Unknown error"}"
            )
        }
    }

    fun openApp(appName: String): ActionResult {
        val trimmed = appName.trim().lowercase()
        return try {
            when {
                trimmed.contains("whatsapp") -> openWhatsApp()
                trimmed.contains("setting") -> {
                    val intent = Intent(Settings.ACTION_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    ActionResult(actionType = "openApp", target = "Settings", success = true, message = "Settings opened.")
                }
                trimmed.contains("camera") -> {
                    val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    ActionResult(actionType = "openApp", target = "Camera", success = true, message = "Camera opened.")
                }
                trimmed.contains("youtube") -> launchPackageOrWeb(
                    packageName = "com.google.android.youtube",
                    fallbackUrl = "https://www.youtube.com",
                    appName = "YouTube"
                )
                trimmed.contains("instagram") -> launchPackageOrWeb(
                    packageName = "com.instagram.android",
                    fallbackUrl = "https://www.instagram.com",
                    appName = "Instagram"
                )
                trimmed.contains("chrome") || trimmed == "browser" -> launchPackageOrWeb(
                    packageName = "com.android.chrome",
                    fallbackUrl = "https://www.google.com",
                    appName = "Chrome"
                )
                trimmed.contains("maps") -> launchPackageOrWeb(
                    packageName = "com.google.android.apps.maps",
                    fallbackUrl = "https://maps.google.com",
                    appName = "Google Maps"
                )
                trimmed.contains("spotify") -> launchPackageOrWeb(
                    packageName = "com.spotify.music",
                    fallbackUrl = "https://open.spotify.com",
                    appName = "Spotify"
                )
                trimmed.contains("photo") || trimmed.contains("gallery") -> launchPackageOrWeb(
                    packageName = "com.google.android.apps.photos",
                    fallbackUrl = "https://photos.google.com",
                    appName = "Photos"
                )
                trimmed.contains("gmail") || trimmed == "mail" || trimmed == "email" -> launchPackageOrWeb(
                    packageName = "com.google.android.gm",
                    fallbackUrl = "https://mail.google.com",
                    appName = "Gmail"
                )
                trimmed.contains("calculator") -> launchPackageOrWeb(
                    packageName = "com.google.android.calculator",
                    fallbackUrl = null,
                    appName = "Calculator"
                )
                trimmed.contains("alarm") || trimmed.contains("clock") -> {
                    val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    ActionResult(actionType = "openApp", target = "Clock/Alarms", success = true, message = "Clock opened.")
                }
                else -> {
                    // Search installed packages
                    val pm = context.packageManager
                    val installedApps = pm.getInstalledApplications(0)
                    var matchedPackage: String? = null
                    var matchedLabel: String? = null

                    for (appInfo in installedApps) {
                        val label = pm.getApplicationLabel(appInfo).toString().lowercase()
                        if (label.contains(trimmed) || trimmed.contains(label)) {
                            matchedPackage = appInfo.packageName
                            matchedLabel = pm.getApplicationLabel(appInfo).toString()
                            break
                        }
                    }

                    if (matchedPackage != null) {
                        val launchIntent = pm.getLaunchIntentForPackage(matchedPackage)
                        if (launchIntent != null) {
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(launchIntent)
                            ActionResult(
                                actionType = "openApp",
                                target = matchedLabel ?: appName,
                                success = true,
                                message = "Opened $matchedLabel."
                            )
                        } else {
                            ActionResult(
                                actionType = "openApp",
                                target = appName,
                                success = false,
                                message = "Found $matchedLabel but could not launch it."
                            )
                        }
                    } else {
                        ActionResult(
                            actionType = "openApp",
                            target = appName,
                            success = false,
                            message = "App '$appName' was not found on this device."
                        )
                    }
                }
            }
        } catch (e: Exception) {
            ActionResult(
                actionType = "openApp",
                target = appName,
                success = false,
                message = "Failed to open $appName: ${e.localizedMessage ?: "Unknown error"}"
            )
        }
    }

    private fun launchPackageOrWeb(packageName: String, fallbackUrl: String?, appName: String): ActionResult {
        val pm = context.packageManager
        val intent = pm.getLaunchIntentForPackage(packageName)
        return if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            ActionResult(
                actionType = "openApp",
                target = appName,
                success = true,
                message = "$appName opened."
            )
        } else if (fallbackUrl != null) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
            ActionResult(
                actionType = "openApp",
                target = "$appName (Web)",
                success = true,
                message = "$appName app is not installed, opened web version."
            )
        } else {
            ActionResult(
                actionType = "openApp",
                target = appName,
                success = false,
                message = "$appName is not installed on this device."
            )
        }
    }

    fun openUrl(url: String): ActionResult {
        return try {
            val formatted = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                "https://$url"
            } else {
                url
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(formatted)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult(
                actionType = "openUrl",
                target = formatted,
                success = true,
                message = "Opened link: $formatted"
            )
        } catch (e: Exception) {
            ActionResult(
                actionType = "openUrl",
                target = url,
                success = false,
                message = "Could not open URL: ${e.localizedMessage ?: "Unknown error"}"
            )
        }
    }

    fun makeCall(phoneNumber: String): ActionResult {
        val clean = phoneNumber.filter { it.isDigit() || it == '+' }
        if (clean.isBlank()) {
            return ActionResult(
                actionType = "makeCall",
                target = phoneNumber,
                success = false,
                message = "Invalid phone number provided."
            )
        }

        val hasCallPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        return try {
            if (hasCallPermission) {
                val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$clean")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                ActionResult(
                    actionType = "makeCall",
                    target = clean,
                    success = true,
                    directExecution = true,
                    message = "Calling $clean directly."
                )
            } else {
                // Safe dialer fallback
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$clean")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                ActionResult(
                    actionType = "makeCall",
                    target = clean,
                    success = true,
                    directExecution = false,
                    message = "Opened dialer with $clean (Direct CALL_PHONE permission not granted)."
                )
            }
        } catch (e: Exception) {
            ActionResult(
                actionType = "makeCall",
                target = clean,
                success = false,
                message = "Failed to initiate call: ${e.localizedMessage ?: "Unknown error"}"
            )
        }
    }

    fun searchContacts(query: String): List<ContactItem> {
        val hasContactsPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasContactsPermission) {
            return emptyList()
        }

        val results = mutableListOf<ContactItem>()
        val contentResolver = context.contentResolver
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI
        )

        // Expand query aliases (e.g. Mom -> Mummy, Maa, Mother)
        val expandedQueries = getAliasQueries(query.trim().lowercase())

        try {
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val idIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val photoIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)

                val seenIds = mutableSetOf<String>()
                while (cursor.moveToNext()) {
                    val id = if (idIndex >= 0) cursor.getString(idIndex) ?: "" else ""
                    val name = if (nameIndex >= 0) cursor.getString(nameIndex) ?: "" else ""
                    val number = if (numberIndex >= 0) cursor.getString(numberIndex) ?: "" else ""
                    val photo = if (photoIndex >= 0) cursor.getString(photoIndex) else null

                    if (name.isNotBlank() && number.isNotBlank()) {
                        val nameLower = name.lowercase()
                        val matches = expandedQueries.any { alias ->
                            nameLower.contains(alias) || alias.contains(nameLower)
                        }
                        if (matches && seenIds.add("$name-$number")) {
                            results.add(ContactItem(id = id, name = name, phoneNumber = number, photoUri = photo))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Log or ignore query exception
        }

        return results
    }

    private fun getAliasQueries(query: String): List<String> {
        val list = mutableListOf(query)
        when (query) {
            "mom", "mummy", "mother", "maa" -> {
                list.addAll(listOf("mom", "mummy", "mother", "maa", "mum"))
            }
            "dad", "papa", "father", "pitaji" -> {
                list.addAll(listOf("dad", "papa", "father", "pitaji", "pop"))
            }
            "brother", "bhai", "bhaiya" -> {
                list.addAll(listOf("brother", "bhai", "bhaiya", "bro"))
            }
            "sister", "didi", "behen" -> {
                list.addAll(listOf("sister", "didi", "behen", "sis"))
            }
            "wife", "patni", "biwi" -> {
                list.addAll(listOf("wife", "patni", "biwi"))
            }
            "husband", "pati" -> {
                list.addAll(listOf("husband", "pati"))
            }
        }
        return list.distinct()
    }

    fun callContact(contactName: String): ActionResult {
        val hasContactsPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasContactsPermission) {
            return ActionResult(
                actionType = "callContact",
                target = contactName,
                success = false,
                message = "Contacts permission (READ_CONTACTS) is needed to look up '$contactName'. Please allow contacts access."
            )
        }

        val matches = searchContacts(contactName)

        return when {
            matches.isEmpty() -> {
                ActionResult(
                    actionType = "callContact",
                    target = contactName,
                    success = false,
                    message = "No contact found matching '$contactName' in your device contacts."
                )
            }
            matches.size == 1 -> {
                val contact = matches.first()
                val callResult = makeCall(contact.phoneNumber)
                ActionResult(
                    actionType = "callContact",
                    target = "${contact.name} (${contact.phoneNumber})",
                    success = callResult.success,
                    directExecution = callResult.directExecution,
                    message = if (callResult.directExecution) {
                        "Calling ${contact.name} (${contact.phoneNumber})."
                    } else {
                        "Opened dialer for ${contact.name} (${contact.phoneNumber})."
                    },
                    candidates = listOf(contact)
                )
            }
            else -> {
                val candidateNames = matches.take(3).joinToString(", ") { "${it.name} (${it.phoneNumber})" }
                ActionResult(
                    actionType = "callContact",
                    target = contactName,
                    success = false,
                    needsClarification = true,
                    candidates = matches,
                    message = "I found ${matches.size} contacts for '$contactName' ($candidateNames). Which one should I call?"
                )
            }
        }
    }

    fun getBridgeInterface(): ArushiAndroidBridge {
        return ArushiAndroidBridge(this)
    }
}

/**
 * JavaScript Bridge exposed to WebView/Capacitor/Web wrappers.
 */
class ArushiAndroidBridge(private val bridge: AndroidAppActionBridge) {

    @JavascriptInterface
    fun openApp(appName: String): String {
        val result = bridge.openApp(appName)
        return resultToJson(result)
    }

    @JavascriptInterface
    fun makeCall(phoneNumber: String): String {
        val result = bridge.makeCall(phoneNumber)
        return resultToJson(result)
    }

    @JavascriptInterface
    fun callContact(contactName: String): String {
        val result = bridge.callContact(contactName)
        return resultToJson(result)
    }

    @JavascriptInterface
    fun openWhatsApp(): String {
        val result = bridge.openWhatsApp()
        return resultToJson(result)
    }

    @JavascriptInterface
    fun openUrl(url: String): String {
        val result = bridge.openUrl(url)
        return resultToJson(result)
    }

    @JavascriptInterface
    fun isBridgeAvailable(): Boolean = true

    private fun resultToJson(result: ActionResult): String {
        val obj = JSONObject()
        obj.put("actionType", result.actionType)
        obj.put("target", result.target)
        obj.put("success", result.success)
        obj.put("message", result.message)
        obj.put("directExecution", result.directExecution)
        obj.put("needsClarification", result.needsClarification)
        val arr = JSONArray()
        result.candidates.forEach { c ->
            val cObj = JSONObject()
            cObj.put("id", c.id)
            cObj.put("name", c.name)
            cObj.put("phoneNumber", c.phoneNumber)
            arr.put(cObj)
        }
        obj.put("candidates", arr)
        return obj.toString()
    }
}
