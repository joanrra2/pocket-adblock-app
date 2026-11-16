@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package io.github.joanrra2.pocketadblock.platform

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleWhenUnlockedThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * Passwords in the iOS Keychain (generic passwords, readable only while the phone is
 * unlocked, never synced to iCloud or restored to another device).
 */
class KeychainStore(private val service: String = "io.github.joanrra2.pocketadblock") : KeyValueStore {

    override fun get(key: String): String? = withQuery(key) { query ->
        CFDictionaryAddValue(query, kSecReturnData, kCFBooleanTrue)
        CFDictionaryAddValue(query, kSecMatchLimit, kSecMatchLimitOne)
        memScoped {
            val result = alloc<CFTypeRefVar>()
            if (SecItemCopyMatching(query, result.ptr) != errSecSuccess) return@withQuery null
            val data = CFBridgingRelease(result.value) as? NSData ?: return@withQuery null
            NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()
        }
    }

    override fun put(key: String, value: String?) {
        withQuery(key) { query -> SecItemDelete(query) }
        if (value == null) return
        @Suppress("CAST_NEVER_SUCCEEDS")
        val data = (value as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return
        withQuery(key) { query ->
            val bridged = CFBridgingRetain(data)
            try {
                CFDictionaryAddValue(query, kSecValueData, bridged)
                CFDictionaryAddValue(query, kSecAttrAccessible, kSecAttrAccessibleWhenUnlockedThisDeviceOnly)
                SecItemAdd(query, null)
            } finally {
                CFRelease(bridged)
            }
        }
    }

    /** Runs [block] with a mutable query for (generic password, [service], [account]). */
    private fun <T> withQuery(account: String, block: (CFMutableDictionaryRef?) -> T): T {
        val query = CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        val serviceRef = CFBridgingRetain(service)
        val accountRef = CFBridgingRetain(account)
        try {
            CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
            CFDictionaryAddValue(query, kSecAttrService, serviceRef)
            CFDictionaryAddValue(query, kSecAttrAccount, accountRef)
            return block(query)
        } finally {
            CFRelease(serviceRef)
            CFRelease(accountRef)
            CFRelease(query)
        }
    }
}
