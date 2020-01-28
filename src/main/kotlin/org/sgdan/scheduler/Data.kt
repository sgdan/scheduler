package org.sgdan.scheduler

/**
 * Read-only snapshot that will be sent to the UI
 */
data class Status(
        val clock: String = "",
        val canExtend: Boolean,
        val remaining: String,
        val weekdayStartMessage: String,
        val resources: List<Resource> = emptyList())

data class Resource(
        val id: String,
        val name: String,
        val type: String,
        val state: String,
        val isAvailable: Boolean,
        val multiAz: Boolean = false,   // RDS only
        val size: Int = 0,              // ASG only
        val max: Int = 0)               // ASG only
