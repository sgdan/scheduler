package org.sgdan.scheduler

import mu.KotlinLogging
import software.amazon.awssdk.services.autoscaling.AutoScalingClient
import software.amazon.awssdk.services.docdb.DocDbClient
import software.amazon.awssdk.services.ec2.Ec2Client
import software.amazon.awssdk.services.ec2.model.Filter
import software.amazon.awssdk.services.ec2.model.InstanceStateName
import software.amazon.awssdk.services.ec2.model.Tag
import software.amazon.awssdk.services.rds.RdsClient
import software.amazon.awssdk.services.ssm.SsmClient
import software.amazon.awssdk.services.ssm.model.ParameterType

private val log = KotlinLogging.logger {}

/**
 * Wraps all operations on AWS resources
 */
class Aws(private val tagName: String,
          private val tagValue: String,
          private val ssmPath: String,
          private val useMultiAz: Boolean) {
    private val ec2 = Ec2Client.create()
    private val rds: RdsClient = RdsClient.create()
    private val docdb: DocDbClient = DocDbClient.create()
    private val asg: AutoScalingClient = AutoScalingClient.create()
    private val ssm = SsmClient.create()

    private val ec2Filter = Filter.builder()
            .name("tag:$tagName").values(tagValue).build()

    /**
     * @return the timestamp of the last start from SSM Parameter Store
     */
    fun getLastStarted(): Long = try {
        ssm.getParameter { it.name(ssmPath) }.parameter().value()
                .toLongOrNull() ?: 0L
    } catch (e: Exception) {
        log.error { "Unable to get lastStarted: ${e.message}" }
        0L
    }

    fun setLastStarted(value: Long) {
        try {
            ssm.putParameter {
                it.name(ssmPath)
                        .value(value.toString())
                        .type(ParameterType.STRING)
                        .overwrite(true)
            }
        } catch (e: Exception) {
            log.error { "Unable to set lastStarted to $value: ${e.message}" }
        }
    }

    fun getTag(name: String, tags: List<Tag>) =
            tags.find { it.key() == name }?.value()

    fun instances(): List<Resource> = try {
        val res = ec2.describeInstances { it.filters(ec2Filter) }
        res.reservations().flatMap { reservation ->
            reservation.instances().map {
                Resource(id = it.instanceId(),
                        name = getTag("Name", it.tags()) ?: it.instanceId(),
                        type = "EC2",
                        state = it.state().nameAsString(),
                        isAvailable = it.state().name() == InstanceStateName.RUNNING)
            }
        }
    } catch (e: Exception) {
        log.error { "Unable to load ec2 instances: ${e.message}" }
        emptyList<Resource>()
    }

    fun databases(): List<Resource> = try {
        rds.describeDBInstances().dbInstances().filter { dbi ->
            rds.listTagsForResource {
                it.resourceName(dbi.dbInstanceArn())
            }.tagList().any {
                it.key() == tagName && it.value() == tagValue
            }
        }.map {
            Resource(id = it.dbInstanceIdentifier(),
                    name = it.dbInstanceIdentifier(),
                    type = "RDS",
                    state = "${it.dbInstanceStatus()}${if (it.multiAZ()) " (Multi-AZ)" else ""}",
                    isAvailable = it.dbInstanceStatus() == "available"
                            && (!useMultiAz || it.multiAZ()),
                    multiAz = it.multiAZ())
        }
    } catch (e: Exception) {
        log.error { "Unable to load rds instances: ${e.message}" }
        emptyList<Resource>()
    }

    fun clusters(): List<Resource> = try {
        docdb.describeDBClusters().dbClusters().filter { dbc ->
            docdb.listTagsForResource {
                it.resourceName(dbc.dbClusterArn())
            }.tagList().any {
                it.key() == tagName && it.value() == tagValue
            }
        }.map {
            Resource(id = it.dbClusterIdentifier(),
                    name = it.dbClusterIdentifier(),
                    type = "DocDB",
                    state = it.status(),
                    isAvailable = it.status() == "available")
        }
    } catch (e: Exception) {
        log.error { "Unable to load docdb clusters: ${e.message}" }
        emptyList<Resource>()
    }

    fun asgs(): List<Resource> = try {
        asg.describeAutoScalingGroups().autoScalingGroups().filter { grp ->
            grp.tags().any { it.key() == tagName && it.value() == tagValue }
        }.map {
            Resource(id = it.autoScalingGroupName(),
                    name = it.autoScalingGroupName(),
                    type = "ASG",
                    state = "${it.instances().size}/${it.maxSize()} instances",
                    isAvailable = it.instances().size == it.maxSize(),
                    size = it.instances().size,
                    max = it.maxSize())
        }
    } catch (e: Exception) {
        log.error { "Unable to load ASGs: ${e.message}" }
        emptyList<Resource>()
    }

    fun startInstance(r: Resource) = try {
        ec2.startInstances { it.instanceIds(r.id) }
        log.info { "Starting instance ${r.name}" }
    } catch (e: Exception) {
        log.error { "Unable to start instance ${r.name}: ${e.message}" }
    }

    fun stopInstance(r: Resource) = try {
        ec2.stopInstances { it.instanceIds(r.id) }
        log.info { "Stopping instance $${r.name}" }
    } catch (e: Exception) {
        log.error { "Unable to stop instance ${r.name}: ${e.message}" }
    }

    fun startCluster(r: Resource) = try {
        docdb.startDBCluster { it.dbClusterIdentifier(r.id) }
        log.info { "Starting cluster ${r.name}" }
    } catch (e: Exception) {
        log.error { "Unable to start cluster ${r.name}: ${e.message}" }
    }

    fun stopCluster(r: Resource) = try {
        docdb.stopDBCluster { it.dbClusterIdentifier(r.id) }
        log.info { "Stopping cluster ${r.name}" }
    } catch (e: Exception) {
        log.error { "Unable to stop cluster ${r.name}: ${e.message}" }
    }

    fun startAsg(r: Resource) = try {
        asg.updateAutoScalingGroup {
            it.autoScalingGroupName(r.id).desiredCapacity(r.max)
        }
        log.info { "Starting asg ${r.name}" }
    } catch (e: Exception) {
        log.error { "Unable to start asg ${r.name}: ${e.message}" }
    }

    fun stopAsg(r: Resource) = try {
        asg.updateAutoScalingGroup {
            it.autoScalingGroupName(r.id).desiredCapacity(0)
        }
        log.info { "Stopping asg ${r.name}" }
    } catch (e: Exception) {
        log.error { "Unable to stop asg ${r.name}: ${e.message}" }
    }

    fun startRds(r: Resource) {
        try {
            if (r.state == "stopped") {
                rds.startDBInstance { it.dbInstanceIdentifier(r.id) }
                log.info { "Starting rds ${r.name}" }
            } else if (r.isAvailable && useMultiAz && !r.multiAz) {
                rds.modifyDBInstance { it.dbInstanceIdentifier(r.id).multiAZ(true).applyImmediately(true) }
                log.info { "Enabling Multi-AZ for rds ${r.name}" }
            }
        } catch (e: Exception) {
            log.error { "Unable to start rds ${r.name}: ${e.message}" }
        }
    }

    fun stopRds(r: Resource) {
        try {
            if (r.isAvailable && r.multiAz) {
                rds.modifyDBInstance { it.dbInstanceIdentifier(r.id).multiAZ(false).applyImmediately(true) }
                log.info { "Disabling Multi-AZ for rds ${r.name}" }
            } else if (r.isAvailable) {
                rds.stopDBInstance { it.dbInstanceIdentifier(r.id) }
                log.info { "Stopping rds ${r.name}" }
            }
        } catch (e: Exception) {
            log.error { "Unable to stop rds ${r.name}: ${e.message}" }
        }
    }
}
