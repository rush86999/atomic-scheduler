package org.acme.kotlin.atomic.meeting.assist.domain

import org.optaplanner.core.api.domain.lookup.PlanningId
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.*
import javax.persistence.*
import javax.validation.constraints.NotNull

@Entity
@Table(name="preferredTimeRange_optaplanner", indexes = [
    Index(name = "sk_eventId_preferredTimeRange_optaplanner", columnList = "eventId"),
    Index(name = "sk_userId_preferredTimeRange_optaplanner", columnList = "userId"),
    Index(name = "sk_hostId_preferredTimeRange_optaplanner", columnList = "hostId"),
])
class PreferredTimeRange {
    @PlanningId
    @Id
    @GeneratedValue
    var id: Long? = null

    @field:NotNull(message = "PreferredTimeRange eventId must not be null")
    lateinit var eventId: String
    @field:NotNull(message = "PreferredTimeRange userId must not be null")
    lateinit var userId: UUID
    @field:NotNull(message = "PreferredTimeRange hostId must not be null")
    lateinit var hostId: UUID

    var dayOfWeek: DayOfWeek? = null // Optional, so no @NotNull

    @field:NotNull(message = "PreferredTimeRange startTime must not be null")
    lateinit var startTime: LocalTime

    @field:NotNull(message = "PreferredTimeRange endTime must not be null")
    lateinit var endTime: LocalTime

    // No-arg constructor required for Hibernate
    constructor()

    constructor(dayOfWeek: DayOfWeek?, startTime: LocalTime, endTime: LocalTime, eventId: String, userId: UUID,
    hostId: UUID) {
        this.dayOfWeek = dayOfWeek
        this.startTime = startTime
        this.endTime = endTime
        this.eventId = eventId
        this.userId = userId
        this.hostId = hostId
    }

    constructor(id: Long?, dayOfWeek: DayOfWeek?, startTime: LocalTime, endTime: LocalTime, eventId: String, userId: UUID,
    hostId: UUID)
            : this(dayOfWeek, startTime, endTime, eventId, userId, hostId) {
        this.id = id
    }

}