package com.example

import com.example.module.ReplacementParser
import com.example.module.ScheduleParser
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import com.example.plugins.*
import io.ktor.serialization.gson.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import kotlin.concurrent.thread

fun main() {
    thread(isDaemon = true) {
        runBlocking {
            ReplacementParser.run()
        }
    }
//    thread(isDaemon = true) {
//        runBlocking {
//            ScheduleParser.run()
//        }
//    }
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        install(CORS) {
            anyHost()
        }
        install(ContentNegotiation) {
            gson()
        }
        configureRouting()
    }.start(wait = true)
}
