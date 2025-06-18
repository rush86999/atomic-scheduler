package org.acme.kotlin.atomic.meeting.assist.rest

import org.acme.kotlin.atomic.meeting.assist.domain.*
import java.util.*

fun Timeslot.toDto(): TimeslotDto {
    return TimeslotDto(
        id = this.id?.let { UUID.nameUUIDFromBytes(it.toString().toByteArray()) },
        hostId = this.hostId?.toString(),
        dayOfWeek = this.dayOfWeek.toString(),
        startTime = this.startTime.toString(),
        endTime = this.endTime.toString(),
        monthDay = this.monthDay.toString()
    )
}

fun WorkTime.toDto(): WorkTimeDto {
    return WorkTimeDto(
        id = this.id?.let { UUID.nameUUIDFromBytes(it.toString().toByteArray()) },
        userId = this.userId.toString(),
        hostId = this.hostId.toString(),
        startTime = this.startTime.toString(),
        endTime = this.endTime.toString()
    )
}

fun User.toDto(): UserDto {
    return UserDto(
        id = this.id.toString(),
        hostId = this.hostId.toString(),
        maxWorkLoadPercent = this.maxWorkLoadPercent,
        backToBackMeetings = this.backToBackMeetings,
        maxNumberOfMeetings = this.maxNumberOfMeetings,
        workTimes = this.workTimes.map { it.toDto() }
    )
}

fun PreferredTimeRange.toDto(): PreferredTimeRangeDto {
    return PreferredTimeRangeDto(
        id = this.id?.let { UUID.nameUUIDFromBytes(it.toString().toByteArray()) },
        eventId = UUID.fromString(this.eventId),
        userId = this.userId.toString(),
        hostId = this.hostId.toString(),
        dayOfWeek = this.dayOfWeek?.toString(),
        startTime = this.startTime.toString(),
        endTime = this.endTime.toString()
    )
}

fun Event.toDto(): EventDto {
    return EventDto(
        id = UUID.fromString(this.id),
        userId = this.userId.toString(),
        hostId = this.hostId.toString(),
        preferredTimeRanges = this.preferredTimeRanges?.map { it.toDto() }
    )
}

fun EventPart.toDto(userDtoMap: Map<String, UserDto>, eventDtoMap: Map<String, EventDto>): EventPartDto {
    val mappedUserDto = userDtoMap[this.userId.toString()]
    val mappedEventDto = eventDtoMap[this.eventId]

    return EventPartDto(
        id = this.id?.toString(),
        groupId = UUID.fromString(this.groupId),
        eventId = UUID.fromString(this.eventId),
        part = this.part,
        lastPart = this.part == this.lastPart,
        startDate = this.startDate,
        endDate = this.endDate,
        taskId = this.taskId?.let { UUID.fromString(it) },
        softDeadline = this.softDeadline,
        hardDeadline = this.hardDeadline,
        meetingId = this.meetingId?.let { UUID.fromString(it) },
        userId = this.userId.toString(),
        hostId = this.hostId.toString(),
        user = mappedUserDto,
        priority = this.priority,
        isPreEvent = this.isPreEvent,
        isPostEvent = this.isPostEvent,
        forEventId = this.forEventId?.let { UUID.fromString(it) },
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
        dailyTaskList = this.dailyTaskList,
        weeklyTaskList = this.weeklyTaskList,
        gap = if (this.gap) 1L else 0L,
        preferredStartTimeRange = this.preferredStartTimeRange?.toString(),
        preferredEndTimeRange = this.preferredEndTimeRange?.toString(),
        totalWorkingHours = this.totalWorkingHours,
        event = mappedEventDto,
        timeslot = this.timeslot?.toDto()
    )
}
