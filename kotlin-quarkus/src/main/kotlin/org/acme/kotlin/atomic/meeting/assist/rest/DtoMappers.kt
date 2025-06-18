package org.acme.kotlin.atomic.meeting.assist.rest

import org.acme.kotlin.atomic.meeting.assist.domain.*
import org.acme.kotlin.atomic.meeting.assist.rest.dto.*
import java.util.UUID
import java.time.DayOfWeek
import java.time.LocalTime
// import java.time.MonthDay // Not strictly needed for mappers if only using toString()

// Timeslot to TimeslotDto
fun Timeslot.toDto(): TimeslotDto {
    return TimeslotDto(
        // Assuming Timeslot.id is Long?, TimeslotDto.id is UUID?
        id = this.id?.let { UUID.nameUUIDFromBytes(it.toString().toByteArray()) },
        hostId = this.hostId?.toString(), // Assuming Timeslot.hostId is UUID?
        dayOfWeek = this.dayOfWeek.toString(), // Assuming Timeslot.dayOfWeek is DayOfWeek
        startTime = this.startTime.toString(), // Assuming Timeslot.startTime is LocalTime
        endTime = this.endTime.toString(), // Assuming Timeslot.endTime is LocalTime
        monthDay = this.monthDay.toString() // Assuming Timeslot.monthDay is MonthDay or similar
    )
}

// WorkTime to WorkTimeDto
fun WorkTime.toDto(): WorkTimeDto {
    return WorkTimeDto(
        // Assuming WorkTime.id is Long?, WorkTimeDto.id is UUID?
        id = this.id?.let { UUID.nameUUIDFromBytes(it.toString().toByteArray()) },
        userId = this.userId.toString(), // Assuming WorkTime.userId is UUID
        hostId = this.hostId.toString(), // Assuming WorkTime.hostId is UUID
        startTime = this.startTime.toString(), // Assuming WorkTime.startTime is LocalTime
        endTime = this.endTime.toString() // Assuming WorkTime.endTime is LocalTime
        // Domain WorkTime.dayOfWeek is not mapped as per user instructions for DTO.
    )
}

// User to UserDto
fun User.toDto(): UserDto {
    return UserDto(
        id = this.id.toString(), // User.id is UUID (domain), UserDto.id is String?
        hostId = this.hostId.toString(), // User.hostId is UUID (domain)
        maxWorkLoadPercent = this.maxWorkLoadPercent,
        backToBackMeetings = this.backToBackMeetings,
        maxNumberOfMeetings = this.maxNumberOfMeetings,
        workTimes = this.workTimes.map { it.toDto() } // User.workTimes is List<WorkTime>
    )
}

// PreferredTimeRange to PreferredTimeRangeDto
fun PreferredTimeRange.toDto(): PreferredTimeRangeDto {
    return PreferredTimeRangeDto(
        // Assuming PreferredTimeRange.id is Long?, PreferredTimeRangeDto.id is UUID?
        id = this.id?.let { UUID.nameUUIDFromBytes(it.toString().toByteArray()) },
        // Domain PreferredTimeRange.eventId is String (must be UUID format), DTO.eventId is UUID?
        eventId = this.eventId?.let { UUID.fromString(it) },
        userId = this.userId.toString(), // Domain userId is UUID
        hostId = this.hostId.toString(), // Domain hostId is UUID
        dayOfWeek = this.dayOfWeek?.toString(), // Domain dayOfWeek is DayOfWeek?
        startTime = this.startTime.toString(), // Domain startTime is LocalTime
        endTime = this.endTime.toString() // Domain endTime is LocalTime
    )
}

// Event to EventDto
fun Event.toDto(): EventDto {
    return EventDto(
        id = UUID.fromString(this.id), // Domain Event.id is String (must be UUID format)
        userId = this.userId.toString(), // Domain userId is UUID
        hostId = this.hostId.toString(), // Domain hostId is UUID
        preferredTimeRanges = this.preferredTimeRanges?.map { it.toDto() }
    )
}

// EventPart.toDto now takes maps of pre-converted UserDto and EventDto for efficiency
fun EventPart.toDto(userDtoMap: Map<String, UserDto>, eventDtoMap: Map<String, EventDto>): EventPartDto {
    val mappedUserDto = userDtoMap[this.userId.toString()] // this.userId from domain is UUID
    val mappedEventDto = eventDtoMap[this.eventId]       // this.eventId from domain is String (used as key)

    return EventPartDto(
        id = this.id?.toString(), // Domain EventPart.id is Long?, DTO EventPartDto.id is String?
        groupId = UUID.fromString(this.groupId), // Domain groupId is String (non-null, UUID format)
        eventId = UUID.fromString(this.eventId), // Domain eventId is String (non-null, UUID format)
        part = this.part,
        lastPart = this.part == this.lastPart, // Domain part & lastPart are Int
        startDate = this.startDate, // Assuming type matches (e.g. ZonedDateTime)
        endDate = this.endDate,     // Assuming type matches
        taskId = this.taskId?.let { UUID.fromString(it) }, // Domain taskId is String? (UUID format)
        softDeadline = this.softDeadline, // Assuming type matches
        hardDeadline = this.hardDeadline, // Assuming type matches
        meetingId = this.meetingId?.let { UUID.fromString(it) }, // Domain meetingId is String? (UUID format)
        userId = this.userId.toString(), // Domain userId is UUID
        hostId = this.hostId.toString(), // Domain hostId is UUID
        user = mappedUserDto,
        priority = this.priority,
        isPreEvent = this.isPreEvent,
        isPostEvent = this.isPostEvent,
        forEventId = this.forEventId?.let { UUID.fromString(it) }, // Domain forEventId is String? (UUID format)
        positiveImpactScore = this.positiveImpactScore,
        negativeImpactScore = this.negativeImpactScore,
        positiveImpactDayOfWeek = this.positiveImpactDayOfWeek?.toString(),
        positiveImpactTime = this.positiveImpactTime?.toString(),
        negativeImpactDayOfWeek = this.negativeImpactDayOfWeek?.toString(),
        negativeImpactTime = this.negativeImpactTime?.toString(),
        modifiable = this.modifiable,
        preferredDayOfWeek = this.preferredDayOfWeek?.toString(),
        preferredTime = this.preferredTime?.toString(),
        isExternalMeeting = this.isExternalMeeting,
        isExternalMeetingModifiable = this.isExternalMeetingModifiable,
        isMeetingModifiable = this.isMeetingModifiable,
        isMeeting = this.isMeeting,
        dailyTaskList = this.dailyTaskList, // Assuming type matches (e.g. List<String>)
        weeklyTaskList = this.weeklyTaskList, // Assuming type matches
        gap = if (this.gap) 1L else 0L, // Domain gap is Boolean, DTO gap is Long?
        preferredStartTimeRange = this.preferredStartTimeRange?.toString(),
        preferredEndTimeRange = this.preferredEndTimeRange?.toString(),
        totalWorkingHours = this.totalWorkingHours,
        event = mappedEventDto,
        timeslot = this.timeslot?.toDto() // Direct mapping for nested Timeslot
    )
}

// TimeTable to TimeTableSolutionDto
// This version assumes the DTOs required by constraints (UnaryConstraintDto, BinaryConstraintDto)
// are defined and that the constraint domain objects have compatible 'id', 'name', 'eventPart', etc. fields.
fun TimeTable.toDto(
    userDtoMap: Map<String, UserDto>,
    eventDtoMap: Map<String, EventDto>
): TimeTableSolutionDto {
    val mappedEventParts = this.eventParts?.map { ep ->
        ep.toDto(userDtoMap, eventDtoMap)
    }
    val eventPartDtoMapById = mappedEventParts?.associateBy { it.id } // Assuming EventPartDto has a String id

    return TimeTableSolutionDto(
        id = this.id.toString(),
        name = this.name,
        users = this.users?.map { it.toDto() },
        events = this.events?.map { it.toDto() },
        eventParts = mappedEventParts,
        timeslots = this.timeslots?.map { it.toDto() },
        constraints = this.constraints?.mapNotNull { constraint ->
            when (constraint) {
                is UnaryConstraint -> UnaryConstraintDto(
                    id = constraint.id?.toString(), // Or .toUUIDPlaceholderOrNull() if DTO id is UUID
                    name = constraint.name,
                    eventPart = eventPartDtoMapById?.get(constraint.eventPart?.id?.toString()),
                    possibleTimeslots = constraint.possibleTimeslots?.map { it.toDto() }
                )
                is BinaryConstraint -> BinaryConstraintDto(
                    id = constraint.id?.toString(), // Or .toUUIDPlaceholderOrNull() if DTO id is UUID
                    name = constraint.name,
                    eventPartA = eventPartDtoMapById?.get(constraint.eventPartA?.id?.toString()),
                    eventPartB = eventPartDtoMapById?.get(constraint.eventPartB?.id?.toString()),
                    type = constraint.type.name
                )
                else -> null
            }
        },
        score = this.score.toString()
    )
}
