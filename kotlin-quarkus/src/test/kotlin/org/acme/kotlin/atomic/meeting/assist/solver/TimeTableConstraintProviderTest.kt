package org.acme.kotlin.atomic.meeting.assist.solver

import org.acme.kotlin.atomic.meeting.assist.domain.Event
import org.acme.kotlin.atomic.meeting.assist.domain.EventPart
import org.acme.kotlin.atomic.meeting.assist.domain.TimeTable
import org.acme.kotlin.atomic.meeting.assist.domain.Timeslot
import org.acme.kotlin.atomic.meeting.assist.domain.User
import org.acme.kotlin.atomic.meeting.assist.domain.WorkTime
// import org.acme.kotlin.atomic.meeting.assist.domain.MonthDayDescriptor // Not used

import org.optaplanner.test.api.score.stream.ConstraintVerifier
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.MonthDay
import java.util.UUID

class TimeTableConstraintProviderTest {

    private val constraintVerifier = ConstraintVerifier.build(
        TimeTableConstraintProvider(),
        TimeTable::class.java,
        EventPart::class.java
        // If User or Timeslot are involved directly in constraints as PlanningEntities or Facts not through EventPart, add them.
        // From the constraints, User and Timeslot are accessed via EventPart.
    )

    private val DEMO_HOST_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")

    // Helper function to create a Timeslot
    private fun createTimeslot(
        dayOfWeek: DayOfWeek,
        startTime: LocalTime,
        endTime: LocalTime,
        monthDay: MonthDay? = MonthDay.of(1, 15) // Default to Jan 15
    ): Timeslot {
        val ts = Timeslot()
        ts.id = UUID.randomUUID()
        ts.hostId = DEMO_HOST_ID
        ts.dayOfWeek = dayOfWeek
        ts.startTime = startTime
        ts.endTime = endTime
        ts.monthDay = monthDay
        return ts
    }

    // Helper function to create a User
    private fun createUser(
        id: UUID = UUID.randomUUID(),
        name: String = "Test User $id",
        workTimes: MutableList<WorkTime> = mutableListOf(),
        maxWorkLoadPercent: Int = 80,
        backToBackMeetings: Boolean = false,
        maxNumberOfMeetings: Int = 5,
        minNumberOfBreaks: Int = 2
    ): User {
        val user = User()
        user.id = id
        user.hostId = DEMO_HOST_ID
        user.name = name
        user.workTimes = workTimes
        user.maxWorkLoadPercent = maxWorkLoadPercent
        user.backToBackMeetings = backToBackMeetings
        user.maxNumberOfMeetings = maxNumberOfMeetings
        user.minNumberOfBreaks = minNumberOfBreaks
        return user
    }

    // Helper function to create a WorkTime and associate it with a user
    private fun createWorkTime(
        user: User,
        dayOfWeek: DayOfWeek,
        startTime: LocalTime,
        endTime: LocalTime
    ): WorkTime {
        val wt = WorkTime(hostId = DEMO_HOST_ID, dayOfWeek = dayOfWeek, startTime = startTime, endTime = endTime)
        wt.id = UUID.randomUUID()
        wt.userId = user.id
        user.workTimes.add(wt) // Add to user's list
        return wt
    }

    // Helper function to create an Event
    private fun createEvent(userId: UUID, eventId: UUID = UUID.randomUUID()): Event {
        val event = Event()
        event.id = eventId
        event.userId = userId
        event.hostId = DEMO_HOST_ID
        event.name = "Test Event $eventId"
        return event
    }

    // Helper function to create an EventPart
    private fun createEventPart(
        user: User,
        event: Event,
        timeslot: Timeslot?,
        partNumber: Int = 1,
        totalPartsInGroup: Int = 1,
        groupId: UUID = event.id!!, // Default groupId to eventId for single-event groups
        meetingId: UUID? = null,
        meetingPart: Int? = null,
        priority: Int = 1,
        startDate: String = "2024-01-15T09:00:00Z", // Default, can be overridden
        endDate: String = "2024-01-15T10:00:00Z",   // Default, can be overridden
        totalWorkingHours: Int = 8 * 60 // Default total working hours for maxWorkload test
    ): EventPart {
        val ep = EventPart()
        ep.id = UUID.randomUUID()
        ep.groupId = groupId
        ep.eventId = event.id
        ep.part = partNumber
        ep.lastPart = totalPartsInGroup
        ep.startDate = startDate
        ep.endDate = endDate
        ep.userId = user.id
        ep.hostId = DEMO_HOST_ID
        ep.user = user
        ep.event = event
        ep.timeslot = timeslot
        ep.priority = priority
        ep.modifiable = true
        ep.meetingId = meetingId
        ep.meetingPart = meetingPart
        ep.totalWorkingHours = totalWorkingHours // For maxWorkload test
        return ep
    }

    @Test
    fun userTimeSlotHardConflict_conflict() {
        val user1 = createUser()
        val timeslot1 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0))
        val event1 = createEvent(user1.id!!)
        val event2 = createEvent(user1.id!!)

        val eventPart1 = createEventPart(user1, event1, timeslot1)
        val eventPart2 = createEventPart(user1, event2, timeslot1)

        constraintVerifier.verifyThat(TimeTableConstraintProvider::userTimeSlotHardConflict)
            .given(eventPart1, eventPart2)
            .penalizesBy(1)
    }

    @Test
    fun userTimeSlotHardConflict_noConflictDifferentTimeslot() {
        val user1 = createUser()
        val timeslot1 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0))
        val timeslot2 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(11, 0))
        val event1 = createEvent(user1.id!!)
        val event2 = createEvent(user1.id!!)

        val eventPart1 = createEventPart(user1, event1, timeslot1)
        val eventPart2 = createEventPart(user1, event2, timeslot2)

        constraintVerifier.verifyThat(TimeTableConstraintProvider::userTimeSlotHardConflict)
            .given(eventPart1, eventPart2)
            .rewardsWith(0) // or .penalizesBy(0)
    }

    @Test
    fun userTimeSlotHardConflict_noConflictDifferentUser() {
        val user1 = createUser()
        val user2 = createUser()
        val timeslot1 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0))
        val event1 = createEvent(user1.id!!)
        val event2 = createEvent(user2.id!!)


        val eventPart1 = createEventPart(user1, event1, timeslot1)
        val eventPart2 = createEventPart(user2, event2, timeslot1)

        constraintVerifier.verifyThat(TimeTableConstraintProvider::userTimeSlotHardConflict)
            .given(eventPart1, eventPart2)
            .rewardsWith(0)
    }

    @Test
    fun outOfWorkTimesBoundaryFromStartTimeHardPenalize_conflict() {
        val workTime = WorkTime(DEMO_HOST_ID, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(17, 0))
        val user1 = createUser(workTimes = mutableListOf(workTime))
        workTime.userId = user1.id // Assign userId after user is created or pass user to WorkTime constructor if possible

        val timeslotConflict = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(9, 0))
        val event1 = createEvent(user1.id!!)
        val eventPartConflict = createEventPart(user1, event1, timeslotConflict)

        constraintVerifier.verifyThat(TimeTableConstraintProvider::outOfWorkTimesBoundaryFromStartTimeHardPenalize)
            .given(eventPartConflict)
            .penalizesBy(1)
    }

    @Test
    fun outOfWorkTimesBoundaryFromStartTimeHardPenalize_noConflict() {
        val workTime = WorkTime(DEMO_HOST_ID, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(17, 0))
        val user1 = createUser(workTimes = mutableListOf(workTime))
        workTime.userId = user1.id

        val timeslotNoConflict = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0))
        val event1 = createEvent(user1.id!!)
        val eventPartNoConflict = createEventPart(user1, event1, timeslotNoConflict)

        constraintVerifier.verifyThat(TimeTableConstraintProvider::outOfWorkTimesBoundaryFromStartTimeHardPenalize)
            .given(eventPartNoConflict)
            .rewardsWith(0)
    }

    @Test
    fun eventPartsDisconnectedByMonthDayHardPenalize_conflict() {
        val user1 = createUser()
        val event1 = createEvent(user1.id!!)
        val commonGroupId = event1.id!!

        val timeslot1 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0), MonthDay.of(1, 15))
        val timeslot2 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(11, 0), MonthDay.of(1, 16)) // Different MonthDay

        val eventPart1 = createEventPart(user1, event1, timeslot1, 1, 2, commonGroupId)
        val eventPart2 = createEventPart(user1, event1, timeslot2, 2, 2, commonGroupId)

        constraintVerifier.verifyThat(TimeTableConstraintProvider::eventPartsDisconnectedByMonthDayHardPenalize)
            .given(eventPart1, eventPart2)
            .penalizesBy(1)
    }

    @Test
    fun eventPartsDisconnectedByMonthDayHardPenalize_noConflict() {
        val user1 = createUser()
        val event1 = createEvent(user1.id!!)
        val commonGroupId = event1.id!!

        val timeslot1 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0), MonthDay.of(1, 15))
        val timeslot2 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(11, 0), MonthDay.of(1, 15)) // Same MonthDay

        val eventPart1 = createEventPart(user1, event1, timeslot1, 1, 2, commonGroupId)
        val eventPart2 = createEventPart(user1, event1, timeslot2, 2, 2, commonGroupId)

        constraintVerifier.verifyThat(TimeTableConstraintProvider::eventPartsDisconnectedByMonthDayHardPenalize)
            .given(eventPart1, eventPart2)
            .rewardsWith(0)
    }

    // --- Tests for meetingNotSameTimeSlotHardConflict ---
    @Test
    fun meetingNotSameTimeSlotHardConflict_conflictDifferentDay() {
        val user1 = createUser()
        val meetingUuid = UUID.randomUUID()
        val event1 = createEvent(user1.id!!) // Main event for eventPart1
        val event2 = createEvent(user1.id!!) // Main event for eventPart2 (can be same or different actual event)

        val timeslot1 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(9,0), LocalTime.of(10,0))
        val timeslot2 = createTimeslot(DayOfWeek.TUESDAY, LocalTime.of(9,0), LocalTime.of(10,0)) // Different Day

        val ep1 = createEventPart(user1, event1, timeslot1, meetingId = meetingUuid, meetingPart = 1)
        val ep2 = createEventPart(user1, event2, timeslot2, meetingId = meetingUuid, meetingPart = 1)

        constraintVerifier.verifyThat(TimeTableConstraintProvider::meetingNotSameTimeSlotHardConflict)
            .given(ep1, ep2)
            .penalizesBy(1) // Expect penalty because timeslots are different
    }

    @Test
    fun meetingNotSameTimeSlotHardConflict_noConflictSameTimeslot() {
        val user1 = createUser()
        val meetingUuid = UUID.randomUUID()
        val event1 = createEvent(user1.id!!)
        val event2 = createEvent(user1.id!!)

        val timeslot1 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(9,0), LocalTime.of(10,0))

        val ep1 = createEventPart(user1, event1, timeslot1, meetingId = meetingUuid, meetingPart = 1)
        val ep2 = createEventPart(user1, event2, timeslot1, meetingId = meetingUuid, meetingPart = 1) // Same timeslot

        constraintVerifier.verifyThat(TimeTableConstraintProvider::meetingNotSameTimeSlotHardConflict)
            .given(ep1, ep2)
            .rewardsWith(0)
    }

    // --- Tests for sequentialEventPartsDisconnectedByTimeHardPenalize ---
    @Test
    fun sequentialEventPartsDisconnectedByTime_conflictGap() {
        val user1 = createUser()
        val event1 = createEvent(user1.id!!)
        val commonGroupId = event1.id!!

        val ts1 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(9,0), LocalTime.of(9,15))
        val ts2 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(9,30), LocalTime.of(9,45)) // Gap of 15 mins

        val ep1 = createEventPart(user1, event1, ts1, partNumber = 1, totalPartsInGroup = 2, groupId = commonGroupId)
        val ep2 = createEventPart(user1, event1, ts2, partNumber = 2, totalPartsInGroup = 2, groupId = commonGroupId)

        constraintVerifier.verifyThat(TimeTableConstraintProvider::sequentialEventPartsDisconnectedByTimeHardPenalize)
            .given(ep1, ep2)
            .penalizesBy(1)
    }

    @Test
    fun sequentialEventPartsDisconnectedByTime_conflictOverlap() {
        val user1 = createUser()
        val event1 = createEvent(user1.id!!)
        val commonGroupId = event1.id!!

        val ts1 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(9,0), LocalTime.of(9,15))
        val ts2 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(9,0), LocalTime.of(9,15)) // Overlap (same start)

        val ep1 = createEventPart(user1, event1, ts1, partNumber = 1, totalPartsInGroup = 2, groupId = commonGroupId)
        val ep2 = createEventPart(user1, event1, ts2, partNumber = 2, totalPartsInGroup = 2, groupId = commonGroupId)

        constraintVerifier.verifyThat(TimeTableConstraintProvider::sequentialEventPartsDisconnectedByTimeHardPenalize)
            .given(ep1, ep2)
            .penalizesBy(1) // actualBetween will be 0, but it should be > 0 for non-sequential if start times are used, this constraint expects endTime to startTime
    }

    @Test
    fun sequentialEventPartsDisconnectedByTime_noConflict() {
        val user1 = createUser()
        val event1 = createEvent(user1.id!!)
        val commonGroupId = event1.id!!

        val ts1 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(9,0), LocalTime.of(9,15))
        val ts2 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(9,15), LocalTime.of(9,30)) // Sequential

        val ep1 = createEventPart(user1, event1, ts1, partNumber = 1, totalPartsInGroup = 2, groupId = commonGroupId)
        val ep2 = createEventPart(user1, event1, ts2, partNumber = 2, totalPartsInGroup = 2, groupId = commonGroupId)

        constraintVerifier.verifyThat(TimeTableConstraintProvider::sequentialEventPartsDisconnectedByTimeHardPenalize)
            .given(ep1, ep2)
            .rewardsWith(0)
    }

    // --- Tests for firstPartPushesLastPartOutHardPenalize ---
    @Test
    fun firstPartPushesLastPartOut_conflict() {
        val user1 = createUser()
        createWorkTime(user1, DayOfWeek.MONDAY, LocalTime.of(9,0), LocalTime.of(10,0)) // Work: 9-10

        val event1 = createEvent(user1.id!!)
        // 4 parts, each 15 mins = 1 hour total.
        // If part1 starts at 9:30, it should end at 10:30. Work ends at 10:00.
        val ts1 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(9,30), LocalTime.of(9,45))
        val ep1 = createEventPart(user1, event1, ts1, partNumber = 1, totalPartsInGroup = 4)

        constraintVerifier.verifyThat(TimeTableConstraintProvider::firstPartPushesLastPartOutHardPenalize)
            .given(ep1)
            .penalizesBy(1)
    }

    @Test
    fun firstPartPushesLastPartOut_noConflict() {
        val user1 = createUser()
        createWorkTime(user1, DayOfWeek.MONDAY, LocalTime.of(9,0), LocalTime.of(10,0)) // Work: 9-10

        val event1 = createEvent(user1.id!!)
        // 4 parts, each 15 mins = 1 hour total.
        // If part1 starts at 9:00, it should end at 10:00. Work ends at 10:00.
        val ts1 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(9,0), LocalTime.of(9,15))
        val ep1 = createEventPart(user1, event1, ts1, partNumber = 1, totalPartsInGroup = 4)

        constraintVerifier.verifyThat(TimeTableConstraintProvider::firstPartPushesLastPartOutHardPenalize)
            .given(ep1)
            .rewardsWith(0)
    }

    // --- Tests for higherPriorityEventsSoonerForTimeOfDayMediumPenalize ---
    @Test
    fun higherPriorityEventsSooner_conflict() {
        val user1 = createUser()
        val eventA = createEvent(user1.id!!, UUID.randomUUID())
        val eventB = createEvent(user1.id!!, UUID.randomUUID())

        val tsA = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(10,0), LocalTime.of(11,0)) // High prio, later
        val tsB = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(9,0), LocalTime.of(10,0))  // Low prio, earlier

        val epA = createEventPart(user1, eventA, tsA, priority = 1) // Higher priority (lower number)
        val epB = createEventPart(user1, eventB, tsB, priority = 5) // Lower priority

        constraintVerifier.verifyThat(TimeTableConstraintProvider::higherPriorityEventsSoonerForTimeOfDayMediumPenalize)
            .given(epA, epB) // Order matters for forEachUniquePair if specific roles assumed by joiners
            .penalizesBy(1)
    }

    @Test
    fun higherPriorityEventsSooner_noConflict() {
        val user1 = createUser()
        val eventA = createEvent(user1.id!!, UUID.randomUUID())
        val eventB = createEvent(user1.id!!, UUID.randomUUID())

        val tsA = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(9,0), LocalTime.of(10,0))  // High prio, earlier
        val tsB = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(10,0), LocalTime.of(11,0)) // Low prio, later

        val epA = createEventPart(user1, eventA, tsA, priority = 1)
        val epB = createEventPart(user1, eventB, tsB, priority = 5)

        constraintVerifier.verifyThat(TimeTableConstraintProvider::higherPriorityEventsSoonerForTimeOfDayMediumPenalize)
            .given(epA, epB)
            .rewardsWith(0)
    }

    // --- Tests for maxWorkloadConflictSoftPenalize ---
    @Test
    fun maxWorkloadConflict_conflict() {
        val monthDay = MonthDay.of(1, 15)
        // User works 1 hour (60 mins). Max workload 50% => 30 mins allowed.
        val user1 = createUser(maxWorkLoadPercent = 50)
        // The constraint uses EventPart.totalWorkingHours, so we must set it on EventPart.
        // Let's simulate this user having 1 hour total work time for this day for the constraint's groupBy.
        val dailyWorkMinutesForUser = 60

        createWorkTime(user1, DayOfWeek.MONDAY, LocalTime.of(9,0), LocalTime.of(10,0)) // WorkTime for user context

        val event1 = createEvent(user1.id!!)
        val ts1 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(9,0), LocalTime.of(9,20), monthDay) // 20 mins
        val ep1 = createEventPart(user1, event1, ts1,
                                 startDate = "2024-01-15T09:00:00Z", endDate = "2024-01-15T09:20:00Z",
                                 totalWorkingHours = dailyWorkMinutesForUser / 60) // Pass hours

        val event2 = createEvent(user1.id!!)
        val ts2 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(9,30), LocalTime.of(9,50), monthDay) // 20 mins
        val ep2 = createEventPart(user1, event2, ts2,
                                 startDate = "2024-01-15T09:30:00Z", endDate = "2024-01-15T09:50:00Z",
                                 totalWorkingHours = dailyWorkMinutesForUser / 60)
        // Total: 40 mins. Limit: 30 mins. Should penalize.

        constraintVerifier.verifyThat(TimeTableConstraintProvider::maxWorkloadConflictSoftPenalize)
            .given(user1, ep1, ep2) // User needs to be a fact if its properties are accessed in groupBy/filter
            .penalizesBy(1)
    }

    @Test
    fun maxWorkloadConflict_noConflict() {
        val monthDay = MonthDay.of(1, 15)
        val user1 = createUser(maxWorkLoadPercent = 50)
        val dailyWorkMinutesForUser = 60
        createWorkTime(user1, DayOfWeek.MONDAY, LocalTime.of(9,0), LocalTime.of(10,0))

        val event1 = createEvent(user1.id!!)
        val ts1 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(9,0), LocalTime.of(9,10), monthDay) // 10 mins
        val ep1 = createEventPart(user1, event1, ts1,
                                 startDate = "2024-01-15T09:00:00Z", endDate = "2024-01-15T09:10:00Z",
                                 totalWorkingHours = dailyWorkMinutesForUser / 60)

        val event2 = createEvent(user1.id!!)
        val ts2 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(9,30), LocalTime.of(9,40), monthDay) // 10 mins
        val ep2 = createEventPart(user1, event2, ts2,
                                 startDate = "2024-01-15T09:30:00Z", endDate = "2024-01-15T09:40:00Z",
                                 totalWorkingHours = dailyWorkMinutesForUser / 60)
        // Total: 20 mins. Limit: 30 mins. Should not penalize.

        constraintVerifier.verifyThat(TimeTableConstraintProvider::maxWorkloadConflictSoftPenalize)
            .given(user1, ep1, ep2)
            .rewardsWith(0)
    }
}
