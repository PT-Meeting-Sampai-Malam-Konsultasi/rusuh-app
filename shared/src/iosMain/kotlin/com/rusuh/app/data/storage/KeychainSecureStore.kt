package com.rusuh.app.data.storage

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
import platform.Foundation.NSBundle
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
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * Keychain-backed [SecureStore] (kSecClassGenericPassword). Items are scoped by [service] (the app bundle id)
 * and are readable after the first unlock, never synced or restored to another device.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class KeychainSecureStore(
    private val service: String = NSBundle.mainBundle.bundleIdentifier ?: FALLBACK_SERVICE,
) : SecureStore {
    override suspend fun getString(key: String): String? =
        memScoped {
            val result = alloc<CFTypeRefVar>()
            val status =
                withQuery(key, extra = { d ->
                    CFDictionaryAddValue(d, kSecReturnData, kCFBooleanTrue)
                    CFDictionaryAddValue(d, kSecMatchLimit, kSecMatchLimitOne)
                }) { SecItemCopyMatching(it, result.ptr) }
            if (status != errSecSuccess) return@memScoped null
            val data = CFBridgingRelease(result.value) as? NSData ?: return@memScoped null
            NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()
        }

    override suspend fun putString(
        key: String,
        value: String,
    ) {
        remove(key)
        val data = (value as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return
        val dataRef = CFBridgingRetain(data)
        try {
            withQuery(key, extra = { d ->
                CFDictionaryAddValue(d, kSecValueData, dataRef)
                CFDictionaryAddValue(d, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
            }) { SecItemAdd(it, null) }
        } finally {
            CFRelease(dataRef)
        }
    }

    override suspend fun remove(key: String) {
        withQuery(key) { SecItemDelete(it) }
    }

    override suspend fun clear() {
        withQuery(account = null) { SecItemDelete(it) }
    }

    private fun <T> withQuery(
        account: String?,
        extra: (CFMutableDictionaryRef?) -> Unit = {},
        block: (CFMutableDictionaryRef?) -> T,
    ): T {
        val dict =
            CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        val serviceRef = CFBridgingRetain(service)
        val accountRef = account?.let { CFBridgingRetain(it) }
        try {
            CFDictionaryAddValue(dict, kSecClass, kSecClassGenericPassword)
            CFDictionaryAddValue(dict, kSecAttrService, serviceRef)
            if (accountRef != null) CFDictionaryAddValue(dict, kSecAttrAccount, accountRef)
            extra(dict)
            return block(dict)
        } finally {
            CFRelease(serviceRef)
            accountRef?.let { CFRelease(it) }
            CFRelease(dict)
        }
    }

    private companion object {
        const val FALLBACK_SERVICE = "com.rusuh.app"
    }
}
