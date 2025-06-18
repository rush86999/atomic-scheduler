package org.acme.kotlin.atomic.meeting.assist.domain

import org.hibernate.annotations.Type
import org.optaplanner.core.api.domain.lookup.PlanningId
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.*
import javax.persistence.*
import javax.validation.constraints.NotNull
import java.time.MonthDay


@Entity
@Table(name="timeslot_optaplanner", indexes = [
    Index(name = "sk_timeslot_hostId_optaplanner", columnList = "hostId"),
])
class Timeslot {
    @PlanningId
    @Id
    @GeneratedValue
    var id: Long? = null

    var hostId: UUID? = null // Nullable, so no @NotNull unless business rule changes

    @field:NotNull(message = "Timeslot dayOfWeek must not be null")
    lateinit var dayOfWeek: DayOfWeek
    @field:NotNull(message = "Timeslot startTime must not be null")
    lateinit var startTime: LocalTime
    @field:NotNull(message = "Timeslot endTime must not be null")
    lateinit var endTime: LocalTime
    @field:NotNull(message = "Timeslot monthDay must not be null")
    @Type(type = "org.acme.kotlin.atomic.meeting.assist.domain.MonthDay")
    lateinit var monthDay: MonthDay
    @field:NotNull(message = "Timeslot date must not be null")
    lateinit var date: LocalDate

    // No-arg constructor required for Hibernate
    constructor()

    constructor(dayOfWeek: DayOfWeek, startTime: LocalTime, endTime: LocalTime, monthDay: MonthDay, userId: UUID?, date: LocalDate) {
        this.dayOfWeek = dayOfWeek
        this.startTime = startTime
        this.endTime = endTime
        this.hostId = userId
        this.monthDay = monthDay
        this.date = date
    }

    constructor(id: Long?, dayOfWeek: DayOfWeek, startTime: LocalTime, endTime: LocalTime, monthDay: MonthDay, userId: UUID?, date: LocalDate)
            : this(dayOfWeek, startTime, endTime, monthDay, userId, date) {
        this.id = id
    }

    override fun toString(): String = "$date $dayOfWeek $startTime"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Timeslot
        if (dayOfWeek != other.dayOfWeek) return false
        if (startTime != other.startTime) return false
        if (endTime != other.endTime) return false
        if (monthDay != other.monthDay) return false
        if (hostId != other.hostId) return false
        if (date != other.date) return false

        return true
    }

    override fun hashCode(): Int {
        var result = dayOfWeek.hashCode()
        result = 31 * result + monthDay.hashCode()
        result = 31 * result + endTime.hashCode()
        result = 31 * result + startTime.hashCode()
        result = 31 * result + hostId.hashCode()
        result = 31 * result + date.hashCode()
        return result
    }

}
