package com.streaming.platform.common

import kotlin.jvm.JvmInline

typealias UserId = String
typealias ProfileId = String
typealias DeviceId = String
typealias ContentId = String

@JvmInline
value class CorrelationId(val value: String)
