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
                    hostId = DEMO_HOST_ID,
                    dayOfWeek = day,
                    startTime = startTimes[i],
                    endTime = endTimes[i]
                    // monthDay can be null or set if specific dates are needed
                ))
            }
        }
        timeslotRepository.persist(timeslotList)
    }

    private fun generateUsersAndWorkTimes() {
        val users = mutableListOf<User>()

        val user1 = User(
            id = UUID.randomUUID(), hostId = DEMO_HOST_ID, name = "User One",
            maxWorkLoadPercent = 80, backToBackMeetings = true, maxNumberOfMeetings = 5, minNumberOfBreaks = 2
        )
        users.add(user1)

        val user2 = User(
            id = UUID.randomUUID(), hostId = DEMO_HOST_ID, name = "User Two",
            maxWorkLoadPercent = 70, backToBackMeetings = false, maxNumberOfMeetings = 4, minNumberOfBreaks = 3
        )
        users.add(user2)

        userRepository.persist(users) // Persist users first

        val workTimes = mutableListOf<WorkTime>()
        val workDays = listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)

        for (user in users) {
            for (day in workDays) {
                workTimes.add(WorkTime(
                    userId = user.id!!, hostId = DEMO_HOST_ID, dayOfWeek = day,
                    startTime = LocalTime.of(9, 0), endTime = LocalTime.of(17, 0)
                ))
            }
        }
        workTimeRepository.persist(workTimes) // Persist worktimes after users

        // Note: In a real setup with bidirectional relationships and cascading,
        // you might add workTimes to user.workTimes and persist only the user.
        // Here, direct repository usage is shown for clarity.

        if (demoData == DemoData.LARGE) {
            val user3 = User(
                id = UUID.randomUUID(), hostId = DEMO_HOST_ID, name = "User Three",
                maxWorkLoadPercent = 90, backToBackMeetings = true, maxNumberOfMeetings = 6, minNumberOfBreaks = 1
            )
            users.add(user3)
            userRepository.persist(user3) // Persist additional user

            for (day in workDays) { // Add work times for User Three
                workTimes.add(WorkTime(
                    userId = user3.id!!, hostId = DEMO_HOST_ID, dayOfWeek = day,
                    startTime = LocalTime.of(8, 0), endTime = LocalTime.of(18, 0) // Longer hours for User Three
                ))
            }
            workTimeRepository.persist(workTimes.filter { it.userId == user3.id }) // Persist only new worktimes
        }
    }

    private fun generateEventsAndEventParts() {
        val users = userRepository.list("hostId", DEMO_HOST_ID)
        if (users.isEmpty()) return // Need users to assign events

        val events = mutableListOf<Event>()
        val eventParts = mutableListOf<EventPart>()
        val preferredRanges = mutableListOf<PreferredTimeRange>()
        val now = OffsetDateTime.now(ZoneOffset.UTC)

        // Event 1 for User 1 (multi-part meeting)
        val user1 = users.firstOrNull { it.name == "User One" }
        if (user1 != null) {
            val event1Id = UUID.randomUUID()
            val event1 = Event(id = event1Id, userId = user1.id!!, hostId = DEMO_HOST_ID, name = "Project Alpha Meeting")
            events.add(event1)

            eventParts.add(EventPart(
                id = UUID.randomUUID(), groupId = event1Id, eventId = event1Id, part = 1, lastPart = 2,
                startDate = now.plusDays(1).withHour(10).withMinute(0).toString(),
                endDate = now.plusDays(1).withHour(10).withMinute(30).toString(),
                userId = user1.id!!, hostId = DEMO_HOST_ID, event = event1, priority = 1, modifiable = true, isMeeting = true
            ))
            eventParts.add(EventPart(
                id = UUID.randomUUID(), groupId = event1Id, eventId = event1Id, part = 2, lastPart = 2,
                startDate = now.plusDays(1).withHour(10).withMinute(30).toString(),
                endDate = now.plusDays(1).withHour(11).withMinute(0).toString(),
                userId = user1.id!!, hostId = DEMO_HOST_ID, event = event1, priority = 1, modifiable = true, isMeeting = true
            ))
            preferredRanges.add(PreferredTimeRange(
                eventId = event1Id, userId = user1.id!!, hostId = DEMO_HOST_ID,
                dayOfWeek = DayOfWeek.MONDAY, startTime = LocalTime.of(10,0), endTime = LocalTime.of(12,0)
            ))
        }

        // Event 2 for User 2 (single part task)
        val user2 = users.firstOrNull { it.name == "User Two" }
        if (user2 != null) {
            val event2Id = UUID.randomUUID()
            val event2 = Event(id = event2Id, userId = user2.id!!, hostId = DEMO_HOST_ID, name = "Focus Work - Report")
            events.add(event2)
            eventParts.add(EventPart(
                id = UUID.randomUUID(), groupId = event2Id, eventId = event2Id, part = 1, lastPart = 1,
                startDate = now.plusDays(2).withHour(14).withMinute(0).toString(),
                endDate = now.plusDays(2).withHour(15).withMinute(0).toString(),
                userId = user2.id!!, hostId = DEMO_HOST_ID, event = event2, priority = 2, modifiable = true, isMeeting = false
            ))
        }

        if (demoData == DemoData.LARGE) {
            // Add more events for LARGE dataset
            // Event 3 for User 1 (task)
            if (user1 != null) {
                val event3Id = UUID.randomUUID()
                val event3 = Event(id = event3Id, userId = user1.id!!, hostId = DEMO_HOST_ID, name = "Prepare Presentation")
                events.add(event3)
                eventParts.add(EventPart(
                    id = UUID.randomUUID(), groupId = event3Id, eventId = event3Id, part = 1, lastPart = 1,
                    startDate = now.plusDays(3).withHour(9).withMinute(0).toString(),
                    endDate = now.plusDays(3).withHour(11).withMinute(0).toString(),
                    userId = user1.id!!, hostId = DEMO_HOST_ID, event = event3, priority = 3, modifiable = true
                ))
            }

            // Event 4 for User 2 (meeting, 3 parts)
            if (user2 != null) {
                val event4Id = UUID.randomUUID()
                val event4 = Event(id = event4Id, userId = user2.id!!, hostId = DEMO_HOST_ID, name = "Team Sync")
                events.add(event4)
                eventParts.add(EventPart(
                    id = UUID.randomUUID(), groupId = event4Id, eventId = event4Id, part = 1, lastPart = 3,
                    startDate = now.plusDays(1).withHour(15).withMinute(0).toString(),
                    endDate = now.plusDays(1).withHour(15).withMinute(20).toString(),
                    userId = user2.id!!, hostId = DEMO_HOST_ID, event = event4, priority = 1, modifiable = true, isMeeting = true
                ))
                eventParts.add(EventPart(
                    id = UUID.randomUUID(), groupId = event4Id, eventId = event4Id, part = 2, lastPart = 3,
                    startDate = now.plusDays(1).withHour(15).withMinute(20).toString(),
                    endDate = now.plusDays(1).withHour(15).withMinute(40).toString(),
                    userId = user2.id!!, hostId = DEMO_HOST_ID, event = event4, priority = 1, modifiable = true, isMeeting = true
                ))
                eventParts.add(EventPart(
                    id = UUID.randomUUID(), groupId = event4Id, eventId = event4Id, part = 3, lastPart = 3,
                    startDate = now.plusDays(1).withHour(15).withMinute(40).toString(),
                    endDate = now.plusDays(1).withHour(16).withMinute(0).toString(),
                    userId = user2.id!!, hostId = DEMO_HOST_ID, event = event4, priority = 1, modifiable = true, isMeeting = true
                ))
            }

            // Event 5 for User 3 (if LARGE)
            val user3 = users.firstOrNull { it.name == "User Three" }
            if (user3 != null) {
                 val event5Id = UUID.randomUUID()
                 val event5 = Event(id = event5Id, userId = user3.id!!, hostId = DEMO_HOST_ID, name = "Client Call")
                 events.add(event5)
                 eventParts.add(EventPart(
                    id = UUID.randomUUID(), groupId = event5Id, eventId = event5Id, part = 1, lastPart = 1,
                    startDate = now.plusDays(2).withHour(11).withMinute(0).toString(),
                    endDate = now.plusDays(2).withHour(12).withMinute(0).toString(),
                    userId = user3.id!!, hostId = DEMO_HOST_ID, event = event5, priority = 1, modifiable = false, isMeeting = true
                 ))
                 preferredRanges.add(PreferredTimeRange(
                    eventId = event5Id, userId = user3.id!!, hostId = DEMO_HOST_ID,
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
