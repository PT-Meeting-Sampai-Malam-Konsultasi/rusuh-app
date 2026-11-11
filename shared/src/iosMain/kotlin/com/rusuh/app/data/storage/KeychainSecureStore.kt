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
import platform.CoreFoundation.CFRetain
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSDictionary
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDefaults
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitAll
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnAttributes
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/** A Keychain call failed with a status other than the expected ones. Carries the OSStatus only, never data. */
class KeychainException(
    val status: Int,
    operation: String,
) : IllegalStateException("Keychain $operation failed with status $status")

private fun check(
    status: Int,
    operation: String,
) {
    if (status != errSecSuccess) throw KeychainException(status, operation)
}

/**
 * Keychain-backed [SecureStore] (kSecClassGenericPassword). Items are scoped by [service] (the app bundle id)
 * and are readable after the first unlock, never synced or restored to another device.
 *
 * The Keychain survives an app uninstall while NSUserDefaults does not, so a missing first-launch flag in
 * [defaults] means a fresh install: stale items are wiped once, before the first operation.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class KeychainSecureStore(
    private val service: String = NSBundle.mainBundle.bundleIdentifier ?: FALLBACK_SERVICE,
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : SecureStore {
    private var initialized = false

    override suspend fun getString(key: String): String? {
        ensureFreshInstallCleared()
        return memScoped {
            val result = alloc<CFTypeRefVar>()
            val status =
                withQuery(key, extra = { d ->
                    CFDictionaryAddValue(d, kSecReturnData, kCFBooleanTrue)
                    CFDictionaryAddValue(d, kSecMatchLimit, kSecMatchLimitOne)
                }) { SecItemCopyMatching(it, result.ptr) }
            if (status == errSecItemNotFound) return@memScoped null
            check(status, "read")
            val data = CFBridgingRelease(result.value) as? NSData ?: return@memScoped null
            NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()
        }
    }

    override suspend fun putString(
        key: String,
        value: String,
    ) {
        ensureFreshInstallCleared()
        val data = (value as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: error("Value is not UTF-8")
        val dataRef = CFBridgingRetain(data)
        try {
            val updated =
                withQuery(key) { query ->
                    withDict { attrs ->
                        CFDictionaryAddValue(attrs, kSecValueData, dataRef)
                        SecItemUpdate(query, attrs)
                    }
                }
            if (updated == errSecItemNotFound) {
                val added =
                    withQuery(key, extra = { d ->
                        CFDictionaryAddValue(d, kSecValueData, dataRef)
                        CFDictionaryAddValue(d, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
                    }) { SecItemAdd(it, null) }
                check(added, "add")
            } else {
                check(updated, "update")
            }
        } finally {
            CFRelease(dataRef)
        }
    }

    override suspend fun remove(key: String) {
        ensureFreshInstallCleared()
        deleteMatching(key)
    }

    override suspend fun removeWhere(prefix: String) {
        ensureFreshInstallCleared()
        var failure: Throwable? = null
        for (account in accounts().filter { it.startsWith(prefix) }) {
            try {
                deleteMatching(account)
            } catch (
                @Suppress("TooGenericExceptionCaught") e: IllegalStateException,
            ) {
                failure = failure ?: e
            }
        }
        failure?.let { throw it }
    }

    override suspend fun clear() {
        ensureFreshInstallCleared()
        deleteMatching(account = null)
    }

    private fun ensureFreshInstallCleared() {
        if (initialized) return
        if (!defaults.boolForKey(FLAG_KEY)) {
            deleteMatching(account = null)
            defaults.setBool(true, forKey = FLAG_KEY)
        }
        initialized = true
    }

    private fun deleteMatching(account: String?) {
        val status = withQuery(account) { SecItemDelete(it) }
        if (status != errSecItemNotFound) check(status, "delete")
    }

    /** Account names of every item of this service. */
    private fun accounts(): List<String> =
        memScoped {
            val result = alloc<CFTypeRefVar>()
            val status =
                withQuery(account = null, extra = { d ->
                    CFDictionaryAddValue(d, kSecReturnAttributes, kCFBooleanTrue)
                    CFDictionaryAddValue(d, kSecMatchLimit, kSecMatchLimitAll)
                }) { SecItemCopyMatching(it, result.ptr) }
            if (status == errSecItemNotFound) return@memScoped emptyList()
            check(status, "list")
            val accountKey = CFBridgingRelease(CFRetain(kSecAttrAccount)) as? String ?: return@memScoped emptyList()
            val items = CFBridgingRelease(result.value) as? List<*> ?: return@memScoped emptyList()
            items.mapNotNull { (it as? NSDictionary)?.objectForKey(accountKey) as? String }
        }

    private fun <T> withDict(block: (CFMutableDictionaryRef?) -> T): T {
        val dict =
            CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        try {
            return block(dict)
        } finally {
            CFRelease(dict)
        }
    }

    private fun <T> withQuery(
        account: String?,
        extra: (CFMutableDictionaryRef?) -> Unit = {},
        block: (CFMutableDictionaryRef?) -> T,
    ): T {
        val serviceRef = CFBridgingRetain(service)
        val accountRef = account?.let { CFBridgingRetain(it) }
        try {
            return withDict { dict ->
                CFDictionaryAddValue(dict, kSecClass, kSecClassGenericPassword)
                CFDictionaryAddValue(dict, kSecAttrService, serviceRef)
                if (accountRef != null) CFDictionaryAddValue(dict, kSecAttrAccount, accountRef)
                extra(dict)
                block(dict)
            }
        } finally {
            CFRelease(serviceRef)
            accountRef?.let { CFRelease(it) }
        }
    }

    private companion object {
        const val FALLBACK_SERVICE = "com.rusuh.app"
        const val FLAG_KEY = "rusuh_keychain_initialized"
    }
}
