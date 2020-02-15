package org.sgdan.scheduler

import io.micronaut.context.annotation.Parallel
import io.micronaut.context.annotation.Value
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.GlobalScope
import java.time.ZoneId
import javax.inject.Singleton

class Config() {
    @Value("\${backend.tag.name:scheduler-enabled}")
    lateinit var tagName: String

    @Value("\${backend.tag.value:true}")
    lateinit var tagValue: String

    @Value("\${backend.zoneId:UTC}")
    lateinit var zoneId: String

    @Value("\${backend.weekdayStart:}")
    lateinit var weekdayStart: String

    @Value("\${backend.ssmPath:/scheduler/lastStarted}")
    lateinit var ssmPath: String

    @Value("\${backend.useMultiAz:false}")
    lateinit var useMultiAz: String

    @Value("\${backend.uptimeWindow:10}")
    lateinit var uptimeWindow: String
}

@Singleton
@Parallel // Don't wait until the first request before starting up!
class Backend(private val cfg: Config) {
    private val useMultiAz = cfg.useMultiAz.toBoolean()

    private val uptimeWindow: Int = cfg.uptimeWindow.toInt()

    private val weekdayStart: Int? = when (val x = cfg.weekdayStart.toIntOrNull()) {
        in 0..23 -> x
        else -> null
    }

    private val zoneId = ZoneId.of(cfg.zoneId)

    private val aws = Aws(cfg.tagName, cfg.tagValue, cfg.ssmPath, useMultiAz)

    private val manager =
            GlobalScope.run { managerActor(aws, zoneId, weekdayStart, uptimeWindow) }

    suspend fun getStatus(): Status =
            CompletableDeferred<Status>()
                    .also { manager.send(Manager.GetStatus(it)) }
                    .await()

    suspend fun extend(): Status =
            CompletableDeferred<Status>()
                    .also { manager.send(Manager.Extend(it)) }
                    .await()
}
