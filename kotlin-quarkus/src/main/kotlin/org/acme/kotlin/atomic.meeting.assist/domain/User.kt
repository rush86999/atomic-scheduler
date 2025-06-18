package org.acme.kotlin.atomic.meeting.assist.domain

import org.optaplanner.core.api.domain.lookup.PlanningId
import java.util.*
import javax.persistence.*
import javax.validation.Valid
import javax.validation.constraints.Max
import javax.validation.constraints.Min
import javax.validation.constraints.NotEmpty
import javax.validation.constraints.NotNull


@Entity
@Table(name="user_optaplanner", indexes = [
    Index(name = "sk_hostId_user_optaplanner", columnList = "hostId"),
])
class User {
    @PlanningId
    @Id
    @field:NotNull(message = "User id must not be null")
    lateinit var id: UUID

    @field:NotNull(message = "User hostId must not be null")
    lateinit var hostId: UUID

    @field:Min(value = 0, message = "maxWorkLoadPercent must be non-negative")
    @field:Max(value = 100, message = "maxWorkLoadPercent must be at most 100")
    var maxWorkLoadPercent: Int = 85
    var backToBackMeetings: Boolean = false // boolean defaults are fine
    @field:Min(value = 0, message = "maxNumberOfMeetings must be non-negative")
    var maxNumberOfMeetings: Int = 8
    @field:Min(value = 0, message = "minNumberOfBreaks must be non-negative")
    var minNumberOfBreaks: Int = 0

    @OneToMany(fetch = FetchType.LAZY) // Changed to LAZY
    @JoinColumn(name = "userId", referencedColumnName = "id", insertable = false, updatable = false)
    @field:NotEmpty(message = "User workTimes must not be empty")
    @field:Valid
    lateinit var workTimes: MutableList<WorkTime>


    // No-arg constructor required for Hibernate and OptaPlanner
    constructor()

    constructor(id: UUID,
                hostId: UUID,
                maxWorkLoadPercent: Int,
                backToBackMeetings: Boolean,
                maxNumberOfMeetings: Int,
                minNumberOfBreaks: Int,
                workTimes: MutableList<WorkTime>,
    ) {
        this.id = id
        this.hostId = hostId
        this.maxWorkLoadPercent = maxWorkLoadPercent
        this.backToBackMeetings = backToBackMeetings
        this.maxNumberOfMeetings = maxNumberOfMeetings
        this.minNumberOfBreaks = minNumberOfBreaks
        this.workTimes = workTimes
    }
}