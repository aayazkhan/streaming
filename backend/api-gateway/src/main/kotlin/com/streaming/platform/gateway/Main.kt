package com.streaming.platform.gateway

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun main() {
    val config = GatewayConfig.fromEnvironment()
    embeddedServer(Netty, port = config.port, module = { module(config) }).start(wait = true)
}
