package com.streaming.platform.ioskit

import com.streaming.platform.security.SecureTokenStore
import com.streaming.platform.security.TokenPair
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.serialization.json.Json
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * Keychain-backed implementation of the shared [SecureTokenStore] contract — Keychain items are
 * encrypted at rest by the OS (Secure Enclave-backed on real devices), the iOS equivalent of
 * Android's EncryptedSharedPreferences. Built on plain CoreFoundation calls (CFDictionary, not
 * NSDictionary) — SecItem* takes CFDictionaryRef directly, so this avoids any NS<->CF bridging cast.
 */
@OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
class IosSecureTokenStore : SecureTokenStore {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun read(): TokenPair? {
        val data = readData() ?: return null
        val text = NSString.create(data, NSUTF8StringEncoding) as? String ?: return null
        return runCatching { json.decodeFromString(TokenPair.serializer(), text) }.getOrNull()
    }

    override suspend fun write(tokens: TokenPair) {
        val text = json.encodeToString(TokenPair.serializer(), tokens)
        writeData(text.toNSData())
    }

    override suspend fun clear() {
        val query = baseQuery()
        SecItemDelete(query)
        CFRelease(query)
    }

    private fun readData(): NSData? {
        val query = baseQuery()
        CFDictionaryAddValue(query, kSecReturnData, kCFBooleanTrue)
        CFDictionaryAddValue(query, kSecMatchLimit, kSecMatchLimitOne)
        return memScoped {
            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query, result.ptr)
            CFRelease(query)
            if (status != errSecSuccess) null else result.value as? NSData
        }
    }

    private fun writeData(data: NSData) {
        val existsQuery = baseQuery()
        CFDictionaryAddValue(existsQuery, kSecReturnData, kCFBooleanTrue)
        val exists = memScoped {
            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(existsQuery, result.ptr)
            status == errSecSuccess
        }
        CFRelease(existsQuery)
        if (exists) {
            val update = CFDictionaryCreateMutable(kCFAllocatorDefault, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
            CFDictionaryAddValue(update, kSecValueData, CFBridgingRetain(data))
            val matchQuery = baseQuery()
            SecItemUpdate(matchQuery, update)
            CFRelease(update)
            CFRelease(matchQuery)
        } else {
            val insert = baseQuery()
            CFDictionaryAddValue(insert, kSecValueData, CFBridgingRetain(data))
            SecItemAdd(insert, null)
            CFRelease(insert)
        }
    }

    private fun baseQuery(): CFMutableDictionaryRef? {
        val dict = CFDictionaryCreateMutable(kCFAllocatorDefault, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        CFDictionaryAddValue(dict, kSecClass, kSecClassGenericPassword)
        CFDictionaryAddValue(dict, kSecAttrService, CFBridgingRetain(SERVICE as NSString))
        CFDictionaryAddValue(dict, kSecAttrAccount, CFBridgingRetain(ACCOUNT as NSString))
        return dict
    }

    private companion object {
        const val SERVICE = "com.streaming.platform.ios.tokens"
        const val ACCOUNT = "tokens"
    }
}

@OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
private fun String.toNSData(): NSData {
    val bytes = encodeToByteArray()
    return if (bytes.isEmpty()) {
        NSData()
    } else {
        bytes.usePinned { pinned -> NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong()) }
    }
}
