package osu.mps.app

import osu.mps.osu.jobs.MatchesSyncScheduler
import osu.mps.app.di.appModule
import osu.mps.app.plugins.configureDatabases
import osu.mps.app.plugins.configureHTTP
import osu.mps.app.plugins.configureRouting
import osu.mps.app.plugins.configureSerialization
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.netty.EngineMain
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.request.path
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import org.slf4j.event.Level

fun main(args: Array<String>): Unit = EngineMain.main(args)

fun Application.module() {
    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/") }
    }

    configureSerialization()
    configureHTTP()
    val parserVersion = environment.config.property("parser.version").getString().toShortOrNull()
        ?.takeIf { it > 0 }
        ?: error("parser.version must be a positive smallint")
    configureDatabases()
    install(Koin) {
        slf4jLogger()
        modules(appModule(parserVersion))
    }
    val matchesSyncScheduler by inject<MatchesSyncScheduler>()
    matchesSyncScheduler.start()
    environment.monitor.subscribe(ApplicationStopped) {
        matchesSyncScheduler.stop()
    }
    configureRouting()
}
