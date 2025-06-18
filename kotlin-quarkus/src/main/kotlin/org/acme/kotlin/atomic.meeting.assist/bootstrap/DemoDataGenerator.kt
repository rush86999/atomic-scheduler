package org.acme.kotlin.atomic.meeting.assist.bootstrap

import io.quarkus.runtime.StartupEvent
import org.acme.kotlin.atomic.meeting.assist.domain.Timeslot
import org.acme.kotlin.atomic.meeting.assist.persistence.TimeslotRepository
// New imports for current domain
import org.acme.kotlin.atomic.meeting.assist.domain.User
import org.acme.kotlin.atomic.meeting.assist.persistence.UserRepository
import org.acme.kotlin.atomic.meeting.assist.domain.WorkTime
import org.acme.kotlin.atomic.meeting.assist.persistence.WorkTimeRepository
import org.acme.kotlin.atomic.meeting.assist.domain.Event
import org.acme.kotlin.atomic.meeting.assist.persistence.EventRepository
import org.acme.kotlin.atomic.meeting.assist.domain.EventPart
import org.acme.kotlin.atomic.meeting.assist.persistence.EventPartRepository
import org.acme.kotlin.atomic.meeting.assist.domain.PreferredTimeRange
import org.acme.kotlin.atomic.meeting.assist.persistence.PreferredTimeRangeRepository

import org.eclipse.microprofile.config.inject.ConfigProperty
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.MonthDay // Added import for MonthDay
import java.util.UUID // For generating IDs and hostId
import java.time.OffsetDateTime // For EventPart startDate/endDate
import java.time.ZoneOffset // For EventPart startDate/endDate


import javax.enterprise.context.ApplicationScoped
import javax.enterprise.event.Observes
import javax.inject.Inject
import javax.transaction.Transactional


@ApplicationScoped
class DemoDataGenerator {

    @ConfigProperty(name = "timeTable.demoData", defaultValue = "SMALL")
    lateinit var demoData: DemoData

    @Inject
    lateinit var timeslotRepository: TimeslotRepository
    // Inject new repositories
    @Inject
    lateinit var userRepository: UserRepository
    @Inject
    lateinit var workTimeRepository: WorkTimeRepository
    @Inject
    lateinit var eventRepository: EventRepository
    @Inject
    lateinit var eventPartRepository: EventPartRepository
    @Inject
    lateinit var preferredTimeRangeRepository: PreferredTimeRangeRepository

    // Define a fixed hostId for demo data
    private val DEMO_HOST_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")


    @Transactional
    fun generateDemoData(@Observes startupEvent: StartupEvent) {
        if (demoData == DemoData.NONE) {
            return
        }

        // Clear existing data for this host to avoid duplicates on restart
        clearDemoData()

        generateTimeslots()
        generateUsersAndWorkTimes()
        generateEventsAndEventParts()

        // Example of how to link an event part to a timeslot manually if needed,
        // but typically the solver does this.
        // For demo purposes, we might pre-assign a few for visual confirmation if desired,
        // but it's not strictly necessary for the solver to start.
    }

    private fun clearDemoData() {
        // Order of deletion matters due to potential foreign key constraints
        // EventPart and PreferredTimeRange depend on Event and User
        // WorkTime depends on User
        // Event depends on User
        // Timeslot is independent in this context but good to clear
        eventPartRepository.delete("hostId", DEMO_HOST_ID)
        preferredTimeRangeRepository.delete("hostId", DEMO_HOST_ID)
        eventRepository.delete("hostId", DEMO_HOST_ID)
        workTimeRepository.delete("hostId", DEMO_HOST_ID)
        userRepository.delete("hostId", DEMO_HOST_ID)
        timeslotRepository.delete("hostId", DEMO_HOST_ID)
    }

    private fun generateTimeslots() {
        val timeslotList: MutableList<Timeslot> = mutableListOf()
        val days = if (demoData == DemoData.LARGE) {
            listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
        } else {
            listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY)
        }

        val startTimes = listOf(
            LocalTime.of(8, 30), LocalTime.of(9, 0), LocalTime.of(9, 30), LocalTime.of(10, 0),
            LocalTime.of(10, 30), LocalTime.of(11, 0), LocalTime.of(11, 30),
            LocalTime.of(13, 0), LocalTime.of(13, 30), LocalTime.of(14, 0),
            LocalTime.of(14, 30), LocalTime.of(15, 0), LocalTime.of(15, 30),
            LocalTime.of(16, 0), LocalTime.of(16, 30)
        )
        val endTimes = listOf(
            LocalTime.of(9, 0), LocalTime.of(9, 30), LocalTime.of(10, 0), LocalTime.of(10, 30),
            LocalTime.of(11, 0), LocalTime.of(11, 30), LocalTime.of(12, 0),
            LocalTime.of(13, 30), LocalTime.of(14, 0), LocalTime.of(14, 30),
            LocalTime.of(15, 0), LocalTime.of(15, 30), LocalTime.of(16, 0),
            LocalTime.of(16, 30), LocalTime.of(17, 0)
        )

        for (day in days) {
            for (i in startTimes.indices) {
                timeslotList.add(Timeslot(
                    day, // Positional argument for dayOfWeek
                    startTimes[i], // Positional argument for startTime
                    endTimes[i], // Positional argument for endTime
                    MonthDay.now(), // Positional argument for monthDay
                    DEMO_HOST_ID // Positional argument for hostId
                ))
            }
        }
        timeslotRepository.persist(timeslotList)
    }

    private fun generateUsersAndWorkTimes() {
        val users = mutableListOf<User>()

        val user1 = User(
            id = UUID.randomUUID(), name = "User One", hostId = DEMO_HOST_ID,
            maxWorkLoadPercent = 80, backToBackMeetings = true, maxNumberOfMeetings = 5, minNumberOfBreaks = 2,
            workTimes = mutableListOf()
        )
        users.add(user1)

        val user2 = User(
            id = UUID.randomUUID(), name = "User Two", hostId = DEMO_HOST_ID,
            maxWorkLoadPercent = 70, backToBackMeetings = false, maxNumberOfMeetings = 4, minNumberOfBreaks = 3,
            workTimes = mutableListOf()
        )
        users.add(user2)

        userRepository.persist(users)

        val workTimes = mutableListOf<WorkTime>()
        val workDays = listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)

        for (user in users) {
            for (day in workDays) {
                workTimes.add(WorkTime(
                    userId = user.id, hostId = DEMO_HOST_ID, dayOfWeek = day, // Removed !! from user.id
                    startTime = LocalTime.of(9, 0), endTime = LocalTime.of(17, 0)
                ))
            }
        }
        workTimeRepository.persist(workTimes)

        if (demoData == DemoData.LARGE) {
            val user3 = User(
                id = UUID.randomUUID(), name = "User Three", hostId = DEMO_HOST_ID,
                maxWorkLoadPercent = 90, backToBackMeetings = true, maxNumberOfMeetings = 6, minNumberOfBreaks = 1,
                workTimes = mutableListOf()
            )
            users.add(user3)
            userRepository.persist(user3)

            val user3WorkTimes = mutableListOf<WorkTime>()
            for (day in workDays) {
                user3WorkTimes.add(WorkTime(
                    userId = user3.id, hostId = DEMO_HOST_ID, dayOfWeek = day, // Removed !! from user3.id
                    startTime = LocalTime.of(8, 0), endTime = LocalTime.of(18, 0)
                ))
            }
            workTimeRepository.persist(user3WorkTimes)
        }
    }

    private fun generateEventsAndEventParts() {
        val users = userRepository.list("hostId", DEMO_HOST_ID)
        if (users.isEmpty()) return

        val events = mutableListOf<Event>()
        val eventParts = mutableListOf<EventPart>()
        val preferredRanges = mutableListOf<PreferredTimeRange>()
        val now = OffsetDateTime.now(ZoneOffset.UTC)

        // Event 1 for User 1 (multi-part meeting)
        val user1 = users.firstOrNull { it.hostId == DEMO_HOST_ID && it.name == "User One" }
        if (user1 != null) {
            val event1IdUUID = UUID.randomUUID()
            val event1IdString = event1IdUUID.toString()
            val event1 = Event(id = event1IdString, name = "Project Alpha Meeting", preferredTimeRanges = mutableListOf(), userId = user1.id, hostId = DEMO_HOST_ID) // Removed !!
            events.add(event1)

            eventParts.add(EventPart(
                groupId = event1IdString,
                part = 1,
                lastPart = 2,
                startDate = now.plusDays(1).withHour(10).withMinute(0).toString(),
                endDate = now.plusDays(1).withHour(10).withMinute(30).toString(),
                taskId = null,
                softDeadline = null,
                hardDeadline = null,
                userId = user1.id, // Removed !!
                user = user1,
                priority = 1,
                isPreEvent = false,
                isPostEvent = false,
                forEventId = null,
                positiveImpactScore = 0,
                negativeImpactScore = 0,
                positiveImpactDayOfWeek = null,
                positiveImpactTime = null,
                negativeImpactDayOfWeek = null,
                negativeImpactTime = null,
                modifiable = true,
                preferredDayOfWeek = null,
                preferredTime = null,
                isMeeting = true,
                isExternalMeeting = false,
                isExternalMeetingModifiable = true,
                isMeetingModifiable = true,
                dailyTaskList = false,
                weeklyTaskList = false,
                gap = false,
                preferredStartTimeRange = null,
                preferredEndTimeRange = null,
                totalWorkingHours = 8,
                eventId = event1IdString,
                event = event1,
                hostId = DEMO_HOST_ID,
                meetingId = null,
                meetingPart = 1,
                meetingLastPart = 2
            ))
            eventParts.add(EventPart(
                groupId = event1IdString,
                part = 2,
                lastPart = 2,
                startDate = now.plusDays(1).withHour(10).withMinute(30).toString(),
                endDate = now.plusDays(1).withHour(11).withMinute(0).toString(),
                taskId = null,
                softDeadline = null,
                hardDeadline = null,
                userId = user1.id, // Removed !!
                user = user1,
                priority = 1,
                isPreEvent = false,
                isPostEvent = false,
                forEventId = null,
                positiveImpactScore = 0,
                negativeImpactScore = 0,
                positiveImpactDayOfWeek = null,
                positiveImpactTime = null,
                negativeImpactDayOfWeek = null,
                negativeImpactTime = null,
                modifiable = true,
                preferredDayOfWeek = null,
                preferredTime = null,
                isMeeting = true,
                isExternalMeeting = false,
                isExternalMeetingModifiable = true,
                isMeetingModifiable = true,
                dailyTaskList = false,
                weeklyTaskList = false,
                gap = false,
                preferredStartTimeRange = null,
                preferredEndTimeRange = null,
                totalWorkingHours = 8,
                eventId = event1IdString,
                event = event1,
                hostId = DEMO_HOST_ID,
                meetingId = null,
                meetingPart = 2,
                meetingLastPart = 2
            ))
            preferredRanges.add(PreferredTimeRange(
                eventId = event1IdString, userId = user1.id, hostId = DEMO_HOST_ID, // Removed !!
                dayOfWeek = DayOfWeek.MONDAY, startTime = LocalTime.of(10,0), endTime = LocalTime.of(12,0)
            ))
        }

        // Event 2 for User 2 (single part task)
        val user2 = users.firstOrNull { it.hostId == DEMO_HOST_ID && it.name == "User Two" }
        if (user2 != null) {
            val event2IdUUID = UUID.randomUUID()
            val event2IdString = event2IdUUID.toString()
            val event2 = Event(id = event2IdString, name = "Focus Work - Report", preferredTimeRanges = mutableListOf(), userId = user2.id, hostId = DEMO_HOST_ID) // Removed !!
            events.add(event2)
            eventParts.add(EventPart(
                groupId = event2IdString,
                part = 1,
                lastPart = 1,
                startDate = now.plusDays(2).withHour(14).withMinute(0).toString(),
                endDate = now.plusDays(2).withHour(15).withMinute(0).toString(),
                taskId = null,
                softDeadline = null,
                hardDeadline = null,
                userId = user2.id, // Removed !!
                user = user2,
                priority = 2,
                isPreEvent = false,
                isPostEvent = false,
                forEventId = null,
                positiveImpactScore = 0,
                negativeImpactScore = 0,
                positiveImpactDayOfWeek = null,
                positiveImpactTime = null,
                negativeImpactDayOfWeek = null,
                negativeImpactTime = null,
                modifiable = true,
                preferredDayOfWeek = null,
                preferredTime = null,
                isMeeting = false,
                isExternalMeeting = false,
                isExternalMeetingModifiable = true,
                isMeetingModifiable = true,
                dailyTaskList = false,
                weeklyTaskList = false,
                gap = false,
                preferredStartTimeRange = null,
                preferredEndTimeRange = null,
                totalWorkingHours = 8,
                eventId = event2IdString,
                event = event2,
                hostId = DEMO_HOST_ID,
                meetingId = null,
                meetingPart = -1,
                meetingLastPart = -1
            ))
        }

        if (demoData == DemoData.LARGE) {
            // Add more events for LARGE dataset
            // Event 3 for User 1 (task)
            if (user1 != null) {
                val event3IdUUID = UUID.randomUUID()
                val event3IdString = event3IdUUID.toString()
                val event3 = Event(id = event3IdString, name = "Prepare Presentation", preferredTimeRanges = mutableListOf(), userId = user1.id, hostId = DEMO_HOST_ID) // Removed !!
                events.add(event3)
                eventParts.add(EventPart(
                    groupId = event3IdString, eventId = event3IdString, part = 1, lastPart = 1,
                    startDate = now.plusDays(3).withHour(9).withMinute(0).toString(),
                    endDate = now.plusDays(3).withHour(11).withMinute(0).toString(),
                    taskId = null, softDeadline = null, hardDeadline = null,
                    userId = user1.id, user = user1, priority = 3, // Removed !!
                    isPreEvent = false, isPostEvent = false, forEventId = null,
                    positiveImpactScore = 0, negativeImpactScore = 0, positiveImpactDayOfWeek = null, positiveImpactTime = null, negativeImpactDayOfWeek = null, negativeImpactTime = null,
                    modifiable = true, preferredDayOfWeek = null, preferredTime = null,
                    isMeeting = false, isExternalMeeting = false, isExternalMeetingModifiable = true, isMeetingModifiable = true,
                    dailyTaskList = false, weeklyTaskList = false, gap = false, preferredStartTimeRange = null, preferredEndTimeRange = null, totalWorkingHours = 8,
                    event = event3, hostId = DEMO_HOST_ID, meetingId = null, meetingPart = -1, meetingLastPart = -1
                ))
            }

            // Event 4 for User 2 (meeting, 3 parts)
            if (user2 != null) {
                val event4IdUUID = UUID.randomUUID()
                val event4IdString = event4IdUUID.toString()
                val event4 = Event(id = event4IdString, name = "Team Sync", preferredTimeRanges = mutableListOf(), userId = user2.id, hostId = DEMO_HOST_ID) // Removed !!
                events.add(event4)
                eventParts.add(EventPart(
                    groupId = event4IdString, eventId = event4IdString, part = 1, lastPart = 3,
                    startDate = now.plusDays(1).withHour(15).withMinute(0).toString(),
                    endDate = now.plusDays(1).withHour(15).withMinute(20).toString(),
                    taskId = null, softDeadline = null, hardDeadline = null,
                    userId = user2.id, user = user2, priority = 1, // Removed !!
                    isPreEvent = false, isPostEvent = false, forEventId = null,
                    positiveImpactScore = 0, negativeImpactScore = 0, positiveImpactDayOfWeek = null, positiveImpactTime = null, negativeImpactDayOfWeek = null, negativeImpactTime = null,
                    modifiable = true, preferredDayOfWeek = null, preferredTime = null,
                    isMeeting = true, isExternalMeeting = false, isExternalMeetingModifiable = true, isMeetingModifiable = true,
                    dailyTaskList = false, weeklyTaskList = false, gap = false, preferredStartTimeRange = null, preferredEndTimeRange = null, totalWorkingHours = 8,
                    event = event4, hostId = DEMO_HOST_ID, meetingId = null, meetingPart = 1, meetingLastPart = 3
                ))
                eventParts.add(EventPart(
                    groupId = event4IdString, eventId = event4IdString, part = 2, lastPart = 3,
                    startDate = now.plusDays(1).withHour(15).withMinute(20).toString(),
                    endDate = now.plusDays(1).withHour(15).withMinute(40).toString(),
                    taskId = null, softDeadline = null, hardDeadline = null,
                    userId = user2.id, user = user2, priority = 1, // Removed !!
                    isPreEvent = false, isPostEvent = false, forEventId = null,
                    positiveImpactScore = 0, negativeImpactScore = 0, positiveImpactDayOfWeek = null, positiveImpactTime = null, negativeImpactDayOfWeek = null, negativeImpactTime = null,
                    modifiable = true, preferredDayOfWeek = null, preferredTime = null,
                    isMeeting = true, isExternalMeeting = false, isExternalMeetingModifiable = true, isMeetingModifiable = true,
                    dailyTaskList = false, weeklyTaskList = false, gap = false, preferredStartTimeRange = null, preferredEndTimeRange = null, totalWorkingHours = 8,
                    event = event4, hostId = DEMO_HOST_ID, meetingId = null, meetingPart = 2, meetingLastPart = 3
                ))
                eventParts.add(EventPart(
                    groupId = event4IdString, eventId = event4IdString, part = 3, lastPart = 3,
                    startDate = now.plusDays(1).withHour(15).withMinute(40).toString(),
                    endDate = now.plusDays(1).withHour(16).withMinute(0).toString(),
                    taskId = null, softDeadline = null, hardDeadline = null,
                    userId = user2.id, user = user2, priority = 1, // Removed !!
                    isPreEvent = false, isPostEvent = false, forEventId = null,
                    positiveImpactScore = 0, negativeImpactScore = 0, positiveImpactDayOfWeek = null, positiveImpactTime = null, negativeImpactDayOfWeek = null, negativeImpactTime = null,
                    modifiable = true, preferredDayOfWeek = null, preferredTime = null,
                    isMeeting = true, isExternalMeeting = false, isExternalMeetingModifiable = true, isMeetingModifiable = true,
                    dailyTaskList = false, weeklyTaskList = false, gap = false, preferredStartTimeRange = null, preferredEndTimeRange = null, totalWorkingHours = 8,
                    event = event4, hostId = DEMO_HOST_ID, meetingId = null, meetingPart = 3, meetingLastPart = 3
                ))
            }

            // Event 5 for User 3 (if LARGE)
            val user3 = users.firstOrNull { it.hostId == DEMO_HOST_ID && it.name == "User Three" }
            if (user3 != null) {
                 val event5IdUUID = UUID.randomUUID()
                 val event5IdString = event5IdUUID.toString()
                 val event5 = Event(id = event5IdString, name = "Client Call", preferredTimeRanges = mutableListOf(), userId = user3.id, hostId = DEMO_HOST_ID) // Removed !!
                 events.add(event5)
                 eventParts.add(EventPart(
                    groupId = event5IdString, eventId = event5IdString, part = 1, lastPart = 1,
                    startDate = now.plusDays(2).withHour(11).withMinute(0).toString(),
                    endDate = now.plusDays(2).withHour(12).withMinute(0).toString(),
                    taskId = null, softDeadline = null, hardDeadline = null,
                    userId = user3.id, user = user3, priority = 1, // Removed !!
                    isPreEvent = false, isPostEvent = false, forEventId = null,
                    positiveImpactScore = 0, negativeImpactScore = 0, positiveImpactDayOfWeek = null, positiveImpactTime = null, negativeImpactDayOfWeek = null, negativeImpactTime = null,
                    modifiable = false, preferredDayOfWeek = null, preferredTime = null,
                    isMeeting = true, isExternalMeeting = false, isExternalMeetingModifiable = true, isMeetingModifiable = true,
                    dailyTaskList = false, weeklyTaskList = false, gap = false, preferredStartTimeRange = null, preferredEndTimeRange = null, totalWorkingHours = 8,
                    event = event5, hostId = DEMO_HOST_ID, meetingId = null, meetingPart = 1, meetingLastPart = 1
                 ))
                 preferredRanges.add(PreferredTimeRange(
                    eventId = event5IdString, userId = user3.id, hostId = DEMO_HOST_ID, // Removed !!
                    dayOfWeek = DayOfWeek.WEDNESDAY, startTime = LocalTime.of(10,0), endTime = LocalTime.of(12,0)
                ))
            }
        }

        eventRepository.persist(events)
        preferredTimeRangeRepository.persist(preferredRanges)
        eventPartRepository.persist(eventParts)
    }


    enum class DemoData {
        NONE, SMALL, LARGE
    }

}
