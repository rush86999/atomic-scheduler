package org.acme.kotlin.atomic.meeting.assist.domain

import org.optaplanner.core.api.domain.entity.PlanningEntity
import org.optaplanner.core.api.domain.lookup.PlanningId
import org.optaplanner.core.api.domain.variable.PlanningVariable
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.*
import javax.persistence.*
import javax.validation.Valid
import javax.validation.constraints.Min
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull

@PlanningEntity
@Entity
@Table(name="event_part_optaplanner", indexes = [
    Index(name = "sk_userId_eventPart_optaplanner", columnList = "userId"),
    Index(name = "sk_groupId_eventPart_optaplanner", columnList = "groupId"),
    Index(name = "sk_eventId_eventPart_optaplanner", columnList = "eventId"),
    Index(name = "sk_hostId_eventPart_optaplanner", columnList = "hostId"),
])
class EventPart {
    @PlanningId
    @Id
    @GeneratedValue
    var id: Long? = null

    @field:NotBlank(message = "EventPart groupId must not be blank")
    lateinit var groupId: String
    @field:NotBlank(message = "EventPart eventId must not be blank")
    lateinit var eventId: String

    @field:Min(value = 1, message = "part number must be at least 1")
    var part: Int = 1
    @field:Min(value = 1, message = "lastPart number must be at least 1")
    var lastPart: Int = 1
    // meetingPart, meetingLastPart can be -1, so no Min(1)

    @field:NotNull(message = "EventPart startDate must not be null")
    lateinit var startDate: LocalDateTime // Parsed from String in constructor, validation of String format can be done at DTO level if it comes as String
    @field:NotNull(message = "EventPart endDate must not be null")
    lateinit var endDate: LocalDateTime   // Similar to startDate

    var taskId: String? = null
    // softDeadline and hardDeadline are LocalDateTime?, validated if present (e.g. @Future if they must be in future)
    // No specific annotations here unless there are universal rules for them.
    var softDeadline: LocalDateTime? = null
    var hardDeadline: LocalDateTime? = null

    @field:NotNull(message = "EventPart userId must not be null")
    lateinit var userId: UUID
    @field:NotNull(message = "EventPart hostId must not be null")
    lateinit var hostId: UUID
    var meetingId: String? = null

    @ManyToOne
    @JoinColumn(name = "userId", referencedColumnName = "id", insertable = false, updatable = false)
    @field:NotNull(message = "EventPart user object must not be null")
    @field:Valid // Validate the User object
    lateinit var user: User

    @field:Min(value = 1, message = "priority must be at least 1")
    var isPreEvent: Boolean = false
    var isPostEvent: Boolean = false
    var forEventId: String? = null
    var positiveImpactScore: Int = 0
    var negativeImpactScore: Int = 0
    var positiveImpactDayOfWeek: DayOfWeek? = null
    var positiveImpactTime: LocalTime? = null
    var negativeImpactDayOfWeek: DayOfWeek? = null
    var negativeImpactTime: LocalTime? = null
    var modifiable: Boolean = true
    var preferredDayOfWeek: DayOfWeek? = null
    var preferredTime: LocalTime? = null
    var isExternalMeeting: Boolean = false
    var isExternalMeetingModifiable: Boolean = true
    var isMeetingModifiable: Boolean = true
    var isMeeting: Boolean = false
    var dailyTaskList: Boolean = false
    var weeklyTaskList: Boolean = false
    var gap: Boolean = false
    var preferredStartTimeRange: LocalTime? = null
    var preferredEndTimeRange: LocalTime? = null
    var totalWorkingHours: Int = 8 // Consider @Min(0) if applicable

    @ManyToOne
    @JoinColumn(name = "eventId", referencedColumnName = "id", insertable = false, updatable = false)
    @field:NotNull(message = "EventPart event object must not be null")
    @field:Valid // Validate the Event object
    lateinit var event: Event

    @PlanningVariable(valueRangeProviderRefs = ["timeslotRange"])
    @ManyToOne
    var timeslot: Timeslot? = null

    // No-arg constructor required for Hibernate and Opta Planner
    constructor()

    constructor(groupId: String, part: Int, lastPart: Int, startDate: String, endDate: String, taskId: String?, softDeadline: String?, hardDeadline: String?, userId: UUID, user: User, priority: Int, isPreEvent: Boolean, isPostEvent: Boolean, forEventId: String?, positiveImpactScore: Int, negativeImpactScore: Int, positiveImpactDayOfWeek: DayOfWeek?, positiveImpactTime: LocalTime?, negativeImpactDayOfWeek: DayOfWeek?, negativeImpactTime: LocalTime?, modifiable: Boolean, preferredDayOfWeek: DayOfWeek?, preferredTime: LocalTime?, isMeeting: Boolean, isExternalMeeting: Boolean, isExternalMeetingModifiable: Boolean, isMeetingModifiable: Boolean, dailyTaskList: Boolean, weeklyTaskList: Boolean,
                gap: Boolean, preferredStartTimeRange: LocalTime?, preferredEndTimeRange: LocalTime?, totalWorkingHours: Int,
                eventId: String, event: Event, hostId: UUID,
                meetingId: String?, meetingPart: Int, meetingLastPart: Int
    ) {
        this.groupId = groupId
        this.part = part
        this.lastPart = lastPart
        this.startDate = LocalDateTime.parse(startDate.trim(), DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        this.endDate = LocalDateTime.parse(endDate.trim(), DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        this.taskId = taskId?.trim()
        this.softDeadline = softDeadline?.trim()?.let { LocalDateTime.parse(it, DateTimeFormatter.ISO_LOCAL_DATE_TIME) }
        this.hardDeadline = hardDeadline?.trim()?.let { LocalDateTime.parse(it, DateTimeFormatter.ISO_LOCAL_DATE_TIME) }
        this.userId = userId
        this.user = user
        this.priority = priority
        this.isPreEvent = isPreEvent
        this.isPostEvent = isPostEvent
        this.forEventId = forEventId
        this.positiveImpactScore = positiveImpactScore
        this.negativeImpactScore = negativeImpactScore
        this.positiveImpactDayOfWeek = positiveImpactDayOfWeek
        this.positiveImpactTime = positiveImpactTime
        this.negativeImpactDayOfWeek = negativeImpactDayOfWeek
        this.negativeImpactTime = negativeImpactTime
        this.modifiable = modifiable
        this.preferredDayOfWeek = preferredDayOfWeek
        this.preferredTime = preferredTime
        this.isMeeting = isMeeting
        this.isExternalMeeting = isExternalMeeting
        this.isExternalMeetingModifiable = isExternalMeetingModifiable
        this.isMeetingModifiable = isMeetingModifiable
        this.dailyTaskList = dailyTaskList
        this.weeklyTaskList = weeklyTaskList
        this.gap = gap
        this.preferredStartTimeRange = preferredStartTimeRange
        this.preferredEndTimeRange = preferredEndTimeRange
        this.totalWorkingHours = totalWorkingHours
        this.eventId = eventId
        this.event = event
        this.hostId = hostId
        this.meetingId = meetingId
        this.meetingPart = meetingPart
        this.meetingLastPart = meetingLastPart
    }

    constructor(id: Long?, groupId: String, part: Int, lastPart: Int, startDate: String,
                endDate: String, taskId: String?, softDeadline: String?,
                hardDeadline: String?, userId: UUID, user: User,
                priority: Int, isPreEvent: Boolean, isPostEvent: Boolean,
                forEventId: String?, positiveImpactScore: Int,
                negativeImpactScore: Int, positiveImpactDayOfWeek: DayOfWeek?,
                positiveImpactTime: LocalTime?, negativeImpactDayOfWeek: DayOfWeek?,
                negativeImpactTime: LocalTime?, modifiable: Boolean,
                preferredDayOfWeek: DayOfWeek?, preferredTime: LocalTime?,
                isMeeting: Boolean, isExternalMeeting: Boolean,
                isExternalMeetingModifiable: Boolean, isMeetingModifiable: Boolean,
                dailyTaskList: Boolean, weeklyTaskList: Boolean, timeslot: Timeslot?, gap: Boolean,
                preferredStartTimeRange: LocalTime?, preferredEndTimeRange: LocalTime?, totalWorkingHours: Int,
                eventId: String, event: Event, hostId: UUID,
                meetingId: String?, meetingPart: Int, meetingLastPart: Int
    )
            : this(groupId, part, lastPart, startDate, endDate, taskId, softDeadline, hardDeadline, userId, user,
        priority, isPreEvent, isPostEvent, forEventId,
        positiveImpactScore, negativeImpactScore, positiveImpactDayOfWeek,
        positiveImpactTime, negativeImpactDayOfWeek, negativeImpactTime,
        modifiable, preferredDayOfWeek, preferredTime, isMeeting,
        isExternalMeeting, isExternalMeetingModifiable, isMeetingModifiable,
        dailyTaskList, weeklyTaskList, gap, preferredStartTimeRange, preferredEndTimeRange, totalWorkingHours,
        eventId, event, hostId, meetingId, meetingPart, meetingLastPart
    ) {
        this.id = id
        this.timeslot = timeslot
    }

}