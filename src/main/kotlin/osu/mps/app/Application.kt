package osu.mps.app

import osu.mps.osu.jobs.MatchesSyncScheduler
import osu.mps.app.plugins.configureDatabases
import osu.mps.app.plugins.configureHTTP
import osu.mps.app.plugins.configureRouting
import osu.mps.app.plugins.configureSerialization
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.netty.EngineMain
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.request.path
import org.slf4j.event.Level

fun main(args: Array<String>): Unit = EngineMain.main(args)

fun Application.module() {
    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/") }
    }

    configureSerialization()
    configureHTTP()
    val repositories = configureDatabases()
    val matchesSyncScheduler = MatchesSyncScheduler(repositories)
    matchesSyncScheduler.start()
    environment.monitor.subscribe(ApplicationStopped) {
        matchesSyncScheduler.stop()
    }
    configureRouting(repositories)
}
