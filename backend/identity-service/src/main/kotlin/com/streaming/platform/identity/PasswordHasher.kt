package com.streaming.platform.identity

import de.mkammerer.argon2.Argon2Factory

interface PasswordHasher {
    fun hash(password: String): String
    fun verify(encodedHash: String, password: String): Boolean
}

class Argon2PasswordHasher : PasswordHasher {
    private val argon2 = Argon2Factory.create()

    override fun hash(password: String): String {
        val chars = password.toCharArray()
        return try {
            argon2.hash(3, 65_536, 1, chars)
        } finally {
            argon2.wipeArray(chars)
        }
    }

    override fun verify(encodedHash: String, password: String): Boolean {
        val chars = password.toCharArray()
        return try {
            argon2.verify(encodedHash, chars)
        } finally {
            argon2.wipeArray(chars)
        }
    }
}
