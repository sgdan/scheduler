package org.sgdan.scheduler

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor
import mu.KotlinLogging
import java.lang.System.currentTimeMillis
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private val log = KotlinLogging.logger {}
private val formatter =
        DateTimeFormatter.ofPattern("h:mma z")
private val weekdayFormatter =
        DateTimeFormatter.ofPattern("ha z")

sealed class Manager() {
    object Update : Manager()
    class GetStatus(val job: CompletableDeferred<Status>) : Manager()
    class Extend(val job: CompletableDeferred<Status>) : Manager()
}

fun CoroutineScope.managerActor(aws: Aws,
                                zoneId: ZoneId,
                                weekdayStart: Int?,
                                window: Int) = actor<Manager> {
    var lastStarted = aws.getLastStarted()
    var resourceList: List<Resource> = emptyList()

    fun resources() = resourceList.sortedWith(
            compareBy(Resource::type, Resource::name))

    fun weekdayStartMsg(): String = weekdayStart?.let {
        val hour = ZonedDateTime.now(zoneId).withHour(it).format(weekdayFormatter)
        "Auto start: Mon-Fri, $hour"
    } ?: "Auto start disabled"

    fun clock(now: Long) = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), zoneId).format(formatter)
    fun status(now: Long) = Status(
            clock = clock(now),
            canExtend = canExtend(lastStarted, now, window),
            remaining = remaining(lastStarted, now, window),
            weekdayStartMessage = weekdayStartMsg(),
            resources = resources())

    fun checkInstance(running: Boolean, r: Resource) {
        when {
            running && r.state == "stopped" -> aws.startInstance(r)
            !running && r.state == "running" -> aws.stopInstance(r)
        }
    }

    fun checkAsg(running: Boolean, r: Resource) {
        when {
            running && (r.size != r.max) -> aws.startAsg(r)
            !running && (r.size > 0) -> aws.stopAsg(r)
        }
    }

    fun checkCluster(running: Boolean, r: Resource) {
        when {
            running && r.state == "stopped" -> aws.startCluster(r)
            !running && r.isAvailable -> aws.stopCluster(r)
        }
    }

    fun checkRds(running: Boolean, r: Resource) {
        when {
            running && !r.isAvailable -> aws.startRds(r)
            !running && (r.state != "stopped") -> aws.stopRds(r)
        }
    }

    fun updateResources() {
        // check if lastStarted needs to be updated due to the schedule
        val now = currentTimeMillis()
        val lastScheduled = lastScheduledMillis(weekdayStart, now, zoneId)
        if (lastScheduled > lastStarted) {
            lastStarted = lastScheduled
            aws.setLastStarted(lastStarted)
            log.info { "Scheduled start: $weekdayStart" }
        }

        // check if anything should be started or stopped
        val running = remaining(lastStarted, now, window).isNotEmpty()
        resourceList.forEach {
            when (it.type) {
                "EC2" -> checkInstance(running, it)
                "ASG" -> checkAsg(running, it)
                "RDS" -> checkRds(running, it)
                "DocDB" -> checkCluster(running, it)
                else -> log.error { "Invalid resource type: ${it.type}" }
            }
        }

        // load the latest config
        resourceList = aws.instances() + aws.databases() + aws.clusters() + aws.asgs()
    }

    for (msg in channel) when (msg) {
        is Manager.GetStatus -> msg.job.complete(status(currentTimeMillis()))

        is Manager.Update -> try {
            updateResources()
        } catch (e: Exception) {
            log.error { "Unable to update nodes: ${e.message}" }
        }

        is Manager.Extend -> {
            resourceList = resourceList.map { it.copy(state = "...") }
            val now = currentTimeMillis()
            lastStarted = extend(lastStarted, now, window)
            aws.setLastStarted(lastStarted)
            msg.job.complete(status(now))
            log.info { "Extending by 1 hour" }
        }
    }
}.also {
    tick(30, it, Manager.Update)
}

/**
 * Trigger specified message at regular intervals to an actor
 */
fun <T> tick(seconds: Long, channel: SendChannel<T>, msg: T) {
    GlobalScope.launch {
        while (!channel.isClosedForSend) {
            channel.send(msg)
            delay(seconds * 1000)
        }
    }
}
