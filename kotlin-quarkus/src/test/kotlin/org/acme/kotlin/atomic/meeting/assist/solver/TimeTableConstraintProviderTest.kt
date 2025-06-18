package org.acme.kotlin.atomic.meeting.assist.solver

import org.acme.kotlin.atomic.meeting.assist.domain.Event
import org.acme.kotlin.atomic.meeting.assist.domain.EventPart
import org.acme.kotlin.atomic.meeting.assist.domain.TimeTable
import org.acme.kotlin.atomic.meeting.assist.domain.Timeslot
import org.acme.kotlin.atomic.meeting.assist.domain.User
import org.acme.kotlin.atomic.meeting.assist.domain.WorkTime
import org.acme.kotlin.atomic.meeting.assist.domain.PreferredTimeRange
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
        date: LocalDate = LocalDate.of(2024, 1, 15) // Default to Jan 15, 2024
    ): Timeslot {
        val ts = Timeslot()
        // ts.id = Random.nextLong() // Timeslot.id is Long? and @GeneratedValue
        ts.hostId = DEMO_HOST_ID
        ts.dayOfWeek = dayOfWeek
        ts.startTime = startTime
        ts.endTime = endTime
        ts.date = date
        ts.monthDay = MonthDay.from(date) // Derive monthDay from date
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
        startDateString: String = "2024-01-15T09:00:00", // Changed to match constructor, no Z
        endDateString: String = "2024-01-15T10:00:00",   // Changed to match constructor, no Z
        hardDeadlineString: String? = null,
        softDeadlineString: String? = null,
        totalWorkingHours: Int = 8 * 60 // Default total working hours for maxWorkload test
    ): EventPart {
        // The EventPart constructor takes String for dates and parses them.
        // We pass them directly. Other properties are set on the instance.
        val ep = EventPart(
            groupId = groupId.toString(), // Assuming EventPart constructor wants String here
            part = partNumber,
            lastPart = totalPartsInGroup,
            startDate = startDateString,
            endDate = endDateString,
            taskId = null, // Assuming null for tests unless specified
            softDeadline = softDeadlineString,
            hardDeadline = hardDeadlineString,
            userId = user.id!!,
            user = user,
            priority = priority,
            isPreEvent = false, isPostEvent = false, forEventId = null,
            positiveImpactScore = 0, negativeImpactScore = 0,
            positiveImpactDayOfWeek = null, positiveImpactTime = null,
            negativeImpactDayOfWeek = null, negativeImpactTime = null,
            modifiable = true, preferredDayOfWeek = null, preferredTime = null,
            isMeeting = false, isExternalMeeting = false,
            isExternalMeetingModifiable = true, isMeetingModifiable = true,
            dailyTaskList = false, weeklyTaskList = false, gap = false,
            preferredStartTimeRange = null, preferredEndTimeRange = null,
            totalWorkingHours = totalWorkingHours,
            eventId = event.id!!.toString(), // Assuming EventPart constructor wants String here
            event = event,
            hostId = DEMO_HOST_ID,
            meetingId = meetingId?.toString(), // Assuming EventPart constructor wants String here
            meetingPart = meetingPart ?: -1, // Provide default if null
            meetingLastPart = -1 // Provide default if null, adjust if needed
        )
        // Assign ID and timeslot after construction as they are not part of this specific constructor
        // ep.id = Random.nextLong() // EventPart.id is Long? and @GeneratedValue
        ep.timeslot = timeslot
        // Note: The main EventPart constructor has many fields. This helper simplifies for common test cases.
        // Adjust defaults (e.g. for booleans, impact scores) as needed per test.
        return ep
    }

    // Helper function to create a PreferredTimeRange and associate it with an event
    private fun createPreferredTimeRange(
        event: Event,
        dayOfWeek: DayOfWeek?, // Nullable if applies to any day
        startTime: LocalTime,
        endTime: LocalTime
    ): PreferredTimeRange {
        val ptr = PreferredTimeRange()
        ptr.id = UUID.randomUUID()
        ptr.eventId = event.id
        ptr.userId = event.userId // Assuming preferred time range is user-specific via the event
        ptr.hostId = event.hostId
        ptr.dayOfWeek = dayOfWeek
        ptr.startTime = startTime
        ptr.endTime = endTime
        if (event.preferredTimeRanges == null) {
            event.preferredTimeRanges = mutableListOf()
        }
        event.preferredTimeRanges!!.add(ptr)
        return ptr
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
    fun maxWorkloadConflict_conflict_exceedsDailyCapacityPercentage() {
        val testDate = LocalDate.of(2024, 1, 15) // Monday
        // User works Mon 9-12 (3 hours = 180 mins). Max workload 50% => 90 mins allowed.
        val user1 = createUser(maxWorkLoadPercent = 50)
        createWorkTime(user1, DayOfWeek.MONDAY, LocalTime.of(9,0), LocalTime.of(12,0))

        val event1 = createEvent(user1.id!!)
        // EventPart 1: Mon 9:00-10:00 (60 mins)
        val ts1 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(9,0), LocalTime.of(10,0), testDate)
        val ep1 = createEventPart(user1, event1, ts1)

        val event2 = createEvent(user1.id!!)
        // EventPart 2: Mon 10:00-10:31 (31 mins) -> Total 60 + 31 = 91 mins. Allowed was 90.
        val ts2 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(10,0), LocalTime.of(10,31), testDate)
        val ep2 = createEventPart(user1, event2, ts2)

        constraintVerifier.verifyThat(TimeTableConstraintProvider::maxWorkloadConflictSoftPenalize)
            .given(user1, ep1, ep2) // Pass user as a problem fact
            .penalizesBy(1)
    }

    @Test
    fun maxWorkloadConflict_noConflict_withinDailyCapacityPercentage() {
        val testDate = LocalDate.of(2024, 1, 15) // Monday
        // User works Mon 9-12 (3 hours = 180 mins). Max workload 50% => 90 mins allowed.
        val user1 = createUser(maxWorkLoadPercent = 50)
        createWorkTime(user1, DayOfWeek.MONDAY, LocalTime.of(9,0), LocalTime.of(12,0))

        val event1 = createEvent(user1.id!!)
        // EventPart 1: Mon 9:00-10:00 (60 mins)
        val ts1 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(9,0), LocalTime.of(10,0), testDate)
        val ep1 = createEventPart(user1, event1, ts1)

        val event2 = createEvent(user1.id!!)
        // EventPart 2: Mon 10:00-10:30 (30 mins) -> Total 60 + 30 = 90 mins. Allowed is 90.
        val ts2 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(10,0), LocalTime.of(10,30), testDate)
        val ep2 = createEventPart(user1, event2, ts2)

        constraintVerifier.verifyThat(TimeTableConstraintProvider::maxWorkloadConflictSoftPenalize)
            .given(user1, ep1, ep2)
            .rewardsWith(0)
    }

    @Test
    fun maxWorkloadConflict_noConflict_differentDays() {
        // User works Mon 9-10 (60 mins), Tue 9-10 (60 mins). Max workload 50% => 30 mins/day.
        val user1 = createUser(maxWorkLoadPercent = 50)
        createWorkTime(user1, DayOfWeek.MONDAY, LocalTime.of(9,0), LocalTime.of(10,0))
        createWorkTime(user1, DayOfWeek.TUESDAY, LocalTime.of(9,0), LocalTime.of(10,0))

        val event1 = createEvent(user1.id!!)
        // EventPart 1: Mon 9:00-9:25 (25 mins) - OK for Monday
        val ts1 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(9,0), LocalTime.of(9,25), LocalDate.of(2024,1,15))
        val ep1 = createEventPart(user1, event1, ts1)

        val event2 = createEvent(user1.id!!)
        // EventPart 2: Tue 9:00-9:25 (25 mins) - OK for Tuesday
        val ts2 = createTimeslot(DayOfWeek.TUESDAY, LocalTime.of(9,0), LocalTime.of(9,25), LocalDate.of(2024,1,16))
        val ep2 = createEventPart(user1, event2, ts2)

        constraintVerifier.verifyThat(TimeTableConstraintProvider::maxWorkloadConflictSoftPenalize)
            .given(user1, ep1, ep2)
            .rewardsWith(0) // No penalty as daily limits are respected for each day
    }

    @Test
    fun maxWorkloadConflict_conflict_noWorkTimeForDay() {
        val testDate = LocalDate.of(2024, 1, 15) // Monday
        // User has NO work time for Monday. Max workload 50%.
        val user1 = createUser(maxWorkLoadPercent = 50)
        // createWorkTime(user1, DayOfWeek.TUESDAY, LocalTime.of(9,0), LocalTime.of(12,0)) // Work time for a different day

        val event1 = createEvent(user1.id!!)
        // EventPart 1: Mon 9:00-10:00 (60 mins). Scheduled on a day with 0 capacity.
        val ts1 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(9,0), LocalTime.of(10,0), testDate)
        val ep1 = createEventPart(user1, event1, ts1)

        constraintVerifier.verifyThat(TimeTableConstraintProvider::maxWorkloadConflictSoftPenalize)
            .given(user1, ep1)
            .penalizesBy(1) // Penalized because totalScheduledMinutes > 0 and no workTime for that day (implies 0 capacity)
    }


    // --- Tests for PreferredTimeRange (Start Time) constraints ---
    // Helper to set up a common scenario for preferred start time range tests
    private fun setupPreferredStartTimeRangeTest(
        preferredStartTime: LocalTime, preferredEndTime: LocalTime, // For the PreferredTimeRange
        actualStartTime: LocalTime, actualEndTime: LocalTime,       // For the EventPart's Timeslot
        preferredDay: DayOfWeek = DayOfWeek.MONDAY,
        actualDay: DayOfWeek = DayOfWeek.MONDAY
    ): EventPart {
        val user = createUser()
        // Ensure user is working during the actual scheduled time to isolate preference penalty
        createWorkTime(user, actualDay, actualStartTime.minusHours(1), actualEndTime.plusHours(1))

        val event = createEvent(user.id!!)
        createPreferredTimeRange(event, preferredDay, preferredStartTime, preferredEndTime)

        // EventPart is scheduled on actualDay at actualStartTime
        // Ensure the event part is the first part for this constraint to apply (part=1)
        val timeslotForEventPart = createTimeslot(actualDay, actualStartTime, actualEndTime, MonthDay.from(event.startDate.toLocalDate())) // Ensure timeslot date aligns with event start date for consistency
        return createEventPart(user, event, timeslotForEventPart, partNumber = 1, totalPartsInGroup = 1) // ensure part = 1
    }

    // Removed Hard and Soft tests for notPreferredStartTimeOfTimeRanges

    @Test
    fun notPreferredStartTimeOfTimeRanges_OutsidePreferred_MediumPenalize() {
        // Preferred: Mon 10-11. Actual: Mon 14-15. (Outside)
        val eventPart = setupPreferredTimeRangeTest(
            preferredStartTime = LocalTime.of(10,0), preferredEndTime = LocalTime.of(11,0),
            actualStartTime = LocalTime.of(14,0), actualEndTime = LocalTime.of(15,0)
        )
        constraintVerifier.verifyThat(TimeTableConstraintProvider::notPreferredStartTimeOfTimeRangesMediumPenalize)
            .given(eventPart)
            .penalizesBy(1)
    }

    @Test
    fun notPreferredStartTimeOfTimeRanges_InsidePreferred_MediumNoPenalty() {
        // Preferred: Mon 10-11. Actual: Mon 10-11. (Inside)
        val eventPart = setupPreferredTimeRangeTest(
            preferredStartTime = LocalTime.of(10,0), preferredEndTime = LocalTime.of(11,0),
            actualStartTime = LocalTime.of(10,0), actualEndTime = LocalTime.of(11,0)
        )
        constraintVerifier.verifyThat(TimeTableConstraintProvider::notPreferredStartTimeOfTimeRangesMediumPenalize)
            .given(eventPart)
            .rewardsWith(0)
    }

    // Removed Hard and Soft tests for notPreferredStartTimeOfTimeRanges

    // --- Tests for PreferredTimeRange (End Time) constraints ---
    // Helper to set up a common scenario for preferred end time range tests
    private fun setupPreferredEndTimeRangeTest(
        preferredStartTime: LocalTime, preferredEndTime: LocalTime, // For the PreferredTimeRange
        actualStartTime: LocalTime, actualEndTime: LocalTime,       // For the EventPart's Timeslot
        preferredDay: DayOfWeek = DayOfWeek.MONDAY,
        actualDay: DayOfWeek = DayOfWeek.MONDAY
    ): EventPart {
        val user = createUser()
        // Ensure user is working during the actual scheduled time
        createWorkTime(user, actualDay, actualStartTime.minusHours(1), actualEndTime.plusHours(1))

        val event = createEvent(user.id!!)
        createPreferredTimeRange(event, preferredDay, preferredStartTime, preferredEndTime)

        // Ensure the event part is the last part for this constraint to apply (partNumber = totalPartsInGroup)
        val timeslotForEventPart = createTimeslot(actualDay, actualStartTime, actualEndTime, MonthDay.from(event.startDate.toLocalDate()))
        return createEventPart(user, event, timeslotForEventPart, partNumber = 1, totalPartsInGroup = 1) // partNumber = lastPart ensures it's the last part
    }

    // Removed Hard and Soft tests for notPreferredEndTimeOfTimeRanges

    @Test
    fun notPreferredEndTimeOfTimeRanges_OutsidePreferred_MediumPenalize() {
        val eventPart = setupPreferredEndTimeRangeTest(
            preferredStartTime = LocalTime.of(9,0), preferredEndTime = LocalTime.of(11,0),
            actualStartTime = LocalTime.of(14,0), actualEndTime = LocalTime.of(15,0)
        )
        constraintVerifier.verifyThat(TimeTableConstraintProvider::notPreferredEndTimeOfTimeRangesMediumPenalize)
            .given(eventPart)
            .penalizesBy(1)
    }

    @Test
    fun notPreferredEndTimeOfTimeRanges_InsidePreferred_MediumNoPenalty() {
        val eventPart = setupPreferredEndTimeRangeTest(
            preferredStartTime = LocalTime.of(8,0), preferredEndTime = LocalTime.of(11,0),
            actualStartTime = LocalTime.of(9,0), actualEndTime = LocalTime.of(10,0)
        )
        constraintVerifier.verifyThat(TimeTableConstraintProvider::notPreferredEndTimeOfTimeRangesMediumPenalize)
            .given(eventPart)
            .rewardsWith(0)
    }

    // Removed Hard and Soft tests for notPreferredEndTimeOfTimeRanges

    // --- Tests for eventPartsDisconnectedByTimeHardPenalize (now named eventPartsOnDifferentDaysHardPenalize) ---
    @Test
    fun eventPartsOnDifferentDays_conflict() {
        val user1 = createUser()
        val event1 = createEvent(user1.id!!)
        val commonGroupId = event1.id!!
        val date1 = LocalDate.of(2024,1,15) // Monday
        val date2 = LocalDate.of(2024,1,16) // Tuesday

        val ts1 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(9,0), LocalTime.of(9,15), date1)
        val ts2 = createTimeslot(DayOfWeek.TUESDAY, LocalTime.of(9,15), LocalTime.of(9,45), date2)

        val ep1 = createEventPart(user1, event1, ts1, partNumber = 1, totalPartsInGroup = 2, groupId = commonGroupId)
        val ep2 = createEventPart(user1, event1, ts2, partNumber = 2, totalPartsInGroup = 2, groupId = commonGroupId)

        constraintVerifier.verifyThat(TimeTableConstraintProvider::eventPartsDisconnectedByTimeHardPenalize) // Original name
            .given(ep1, ep2)
            .penalizesBy(1)
    }

    @Test
    fun eventPartsOnDifferentDays_noConflict_sameDay() {
        val user1 = createUser()
        val event1 = createEvent(user1.id!!)
        val commonGroupId = event1.id!!
        val date1 = LocalDate.of(2024,1,15) // Monday

        val ts1 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(9,0), LocalTime.of(9,15), date1)
        // ts2 has a gap, but is on the same day. This constraint should not penalize.
        // sequentialEventPartsDisconnectedByTimeHardPenalize would penalize for the gap if they are sequential parts.
        val ts2 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(10,0), LocalTime.of(10,15), date1)

        val ep1 = createEventPart(user1, event1, ts1, partNumber = 1, totalPartsInGroup = 2, groupId = commonGroupId)
        val ep2 = createEventPart(user1, event1, ts2, partNumber = 2, totalPartsInGroup = 2, groupId = commonGroupId)

        constraintVerifier.verifyThat(TimeTableConstraintProvider::eventPartsDisconnectedByTimeHardPenalize) // Original name
            .given(ep1, ep2)
            .rewardsWith(0)
    }

    // --- Tests for firstAndLastPartDisconnectedByTimeHardPenalize (now named firstAndLastPartOnDifferentDaysHardPenalize) ---
    @Test
    fun firstAndLastPartOnDifferentDays_conflict() {
        val user1 = createUser()
        val event1 = createEvent(user1.id!!)
        val commonGroupId = event1.id!!
        val date1 = LocalDate.of(2024,1,15) // Monday
        val date2 = LocalDate.of(2024,1,16) // Tuesday

        val ts1 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(9,0), LocalTime.of(9,15), date1)
        val ts3 = createTimeslot(DayOfWeek.TUESDAY, LocalTime.of(9,45), LocalTime.of(10,0), date2) // Last part on different day

        val ep1 = createEventPart(user1, event1, ts1, partNumber = 1, totalPartsInGroup = 3, groupId = commonGroupId)
        val ep3 = createEventPart(user1, event1, ts3, partNumber = 3, totalPartsInGroup = 3, groupId = commonGroupId)

        constraintVerifier.verifyThat(TimeTableConstraintProvider::firstAndLastPartDisconnectedByTimeHardPenalize) // Original name
            .given(ep1, ep3)
            .penalizesBy(1)
    }

    @Test
    fun firstAndLastPartOnDifferentDays_noConflict_sameDay() {
        val user1 = createUser()
        val event1 = createEvent(user1.id!!)
        val commonGroupId = event1.id!!
        val date1 = LocalDate.of(2024,1,15) // Monday

        // All parts on the same day, even if there are gaps between them (handled by sequential constraint)
        val ts1 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(9,0), LocalTime.of(9,15), date1)
        val ts3 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(10,30), LocalTime.of(10,45), date1)

        val ep1 = createEventPart(user1, event1, ts1, partNumber = 1, totalPartsInGroup = 3, groupId = commonGroupId)
        val ep3 = createEventPart(user1, event1, ts3, partNumber = 3, totalPartsInGroup = 3, groupId = commonGroupId)

        constraintVerifier.verifyThat(TimeTableConstraintProvider::firstAndLastPartDisconnectedByTimeHardPenalize) // Original name
            .given(ep1, ep3)
            .rewardsWith(0)
    }

    // --- Tests for outOfWorkTimesBoundaryFromEndTimeHardPenalize ---
    @Test
    fun outOfWorkTimesBoundaryFromEndTimeHardPenalize_conflict() {
        val user1 = createUser()
        createWorkTime(user1, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(17, 0)) // Work ends at 17:00

        // Event part ends at 17:30, which is after work time
        val timeslotConflict = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(16, 30), LocalTime.of(17, 30))
        val event1 = createEvent(user1.id!!)
        val eventPartConflict = createEventPart(user1, event1, timeslotConflict)

        constraintVerifier.verifyThat(TimeTableConstraintProvider::outOfWorkTimesBoundaryFromEndTimeHardPenalize)
            .given(eventPartConflict)
            .penalizesBy(1)
    }

    @Test
    fun outOfWorkTimesBoundaryFromEndTimeHardPenalize_noConflict() {
        val user1 = createUser()
        createWorkTime(user1, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(17, 0)) // Work ends at 17:00

        // Event part ends at 17:00, which is exactly at end of work time (allowed)
        val timeslotNoConflict = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(16, 0), LocalTime.of(17, 0))
        val event1 = createEvent(user1.id!!)
        val eventPartNoConflict = createEventPart(user1, event1, timeslotNoConflict)

        constraintVerifier.verifyThat(TimeTableConstraintProvider::outOfWorkTimesBoundaryFromEndTimeHardPenalize)
            .given(eventPartNoConflict)
            .rewardsWith(0)
    }

    @Test
    fun outOfWorkTimesBoundaryFromEndTimeHardPenalize_noWorkTimeDefined() {
        val user1 = createUser() // No WorkTime defined for this user

        val timeslot = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(16, 0), LocalTime.of(17, 0))
        val event1 = createEvent(user1.id!!)
        val eventPart = createEventPart(user1, event1, timeslot)

        // Constraint should penalize if no work time is defined for the day, as it cannot determine if it's within bounds.
        constraintVerifier.verifyThat(TimeTableConstraintProvider::outOfWorkTimesBoundaryFromEndTimeHardPenalize)
            .given(eventPart)
            .penalizesBy(1)
    }

    // --- Tests for hardDeadlineConflictHardPenalize ---
    @Test
    fun hardDeadlineConflictHardPenalize_conflict() {
        val user1 = createUser()
        val event1 = createEvent(user1.id!!)
        // Deadline: Jan 15th, 10:00. EventPart ends Jan 15th, 11:00.
        val timeslot = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(10,0), LocalTime.of(11,0), MonthDay.of(1,15))
        val eventPart = createEventPart(user1, event1, timeslot,
                                      startDateString="2024-01-15T10:00:00",
                                      endDateString="2024-01-15T11:00:00",
                                      hardDeadlineString = "2024-01-15T10:00:00")

        constraintVerifier.verifyThat(TimeTableConstraintProvider::hardDeadlineConflictHardPenalize)
            .given(eventPart)
            .penalizesBy(1)
    }

    @Test
    fun hardDeadlineConflictHardPenalize_noConflict_endsAtDeadline() {
        val user1 = createUser()
        val event1 = createEvent(user1.id!!)
        // Deadline: Jan 15th, 11:00. EventPart ends Jan 15th, 11:00.
        val timeslot = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(10,0), LocalTime.of(11,0), MonthDay.of(1,15))
        val eventPart = createEventPart(user1, event1, timeslot,
                                      startDateString="2024-01-15T10:00:00",
                                      endDateString="2024-01-15T11:00:00",
                                      hardDeadlineString = "2024-01-15T11:00:00")

        constraintVerifier.verifyThat(TimeTableConstraintProvider::hardDeadlineConflictHardPenalize)
            .given(eventPart)
            .rewardsWith(0)
    }

    @Test
    fun hardDeadlineConflictHardPenalize_noConflict_endsBeforeDeadline() {
        val user1 = createUser()
        val event1 = createEvent(user1.id!!)
        // Deadline: Jan 15th, 12:00. EventPart ends Jan 15th, 11:00.
        val timeslot = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(10,0), LocalTime.of(11,0), MonthDay.of(1,15))
        val eventPart = createEventPart(user1, event1, timeslot,
                                      startDateString="2024-01-15T10:00:00",
                                      endDateString="2024-01-15T11:00:00",
                                      hardDeadlineString = "2024-01-15T12:00:00")

        constraintVerifier.verifyThat(TimeTableConstraintProvider::hardDeadlineConflictHardPenalize)
            .given(eventPart)
            .rewardsWith(0)
    }

    @Test
    fun hardDeadlineConflictHardPenalize_noDeadlineSet() {
        val user1 = createUser()
        val event1 = createEvent(user1.id!!)
        val timeslot = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(10,0), LocalTime.of(11,0))
        // Create event part without setting hardDeadlineString (it will be null)
        val eventPart = createEventPart(user1, event1, timeslot, hardDeadlineString = null)

        constraintVerifier.verifyThat(TimeTableConstraintProvider::hardDeadlineConflictHardPenalize)
            .given(eventPart)
            .rewardsWith(0)
    }

    // --- Tests for softDeadlineConflictSoftPenalize ---
    @Test
    fun softDeadlineConflictSoftPenalize_conflict() {
        val user1 = createUser()
        val event1 = createEvent(user1.id!!)
        // Deadline: Jan 15th, 10:00. EventPart ends Jan 15th, 11:00.
        val timeslot = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(10,0), LocalTime.of(11,0), MonthDay.of(1,15))
        val eventPart = createEventPart(user1, event1, timeslot,
                                      startDateString="2024-01-15T10:00:00",
                                      endDateString="2024-01-15T11:00:00",
                                      softDeadlineString = "2024-01-15T10:00:00")

        constraintVerifier.verifyThat(TimeTableConstraintProvider::softDeadlineConflictSoftPenalize)
            .given(eventPart)
            .penalizesBy(1)
    }

    @Test
    fun softDeadlineConflictSoftPenalize_noConflict_endsAtDeadline() {
        val user1 = createUser()
        val event1 = createEvent(user1.id!!)
        val timeslot = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(10,0), LocalTime.of(11,0), MonthDay.of(1,15))
        val eventPart = createEventPart(user1, event1, timeslot,
                                      startDateString="2024-01-15T10:00:00",
                                      endDateString="2024-01-15T11:00:00",
                                      softDeadlineString = "2024-01-15T11:00:00")

        constraintVerifier.verifyThat(TimeTableConstraintProvider::softDeadlineConflictSoftPenalize)
            .given(eventPart)
            .rewardsWith(0)
    }

    // --- Tests for partIsNotFirstForStartOfDayHardPenalize ---
    @Test
    fun partIsNotFirstForStartOfDayHardPenalize_conflict() {
        val user1 = createUser()
        // Work day starts at 9:00
        createWorkTime(user1, DayOfWeek.MONDAY, LocalTime.of(9,0), LocalTime.of(17,0))
        val event1 = createEvent(user1.id!!)

        // EventPart starts exactly at work time start (9:00) but is NOT part 1 (e.g., part 2)
        val timeslotAtStart = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(9,0), LocalTime.of(10,0))
        val eventPartNotFirst = createEventPart(user1, event1, timeslotAtStart, partNumber = 2, totalPartsInGroup = 2)

        constraintVerifier.verifyThat(TimeTableConstraintProvider::partIsNotFirstForStartOfDayHardPenalize)
            .given(eventPartNotFirst)
            .penalizesBy(1)
    }

    @Test
    fun partIsNotFirstForStartOfDayHardPenalize_noConflict_isFirstPart() {
        val user1 = createUser()
        createWorkTime(user1, DayOfWeek.MONDAY, LocalTime.of(9,0), LocalTime.of(17,0))
        val event1 = createEvent(user1.id!!)

        // EventPart starts at work time start (9:00) AND is part 1
        val timeslotAtStart = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(9,0), LocalTime.of(10,0))
        val eventPartIsFirst = createEventPart(user1, event1, timeslotAtStart, partNumber = 1, totalPartsInGroup = 2)

        constraintVerifier.verifyThat(TimeTableConstraintProvider::partIsNotFirstForStartOfDayHardPenalize)
            .given(eventPartIsFirst)
            .rewardsWith(0)
    }

    @Test
    fun partIsNotFirstForStartOfDayHardPenalize_noConflict_notAtStartOfDay() {
        val user1 = createUser()
        createWorkTime(user1, DayOfWeek.MONDAY, LocalTime.of(9,0), LocalTime.of(17,0))
        val event1 = createEvent(user1.id!!)

        // EventPart is NOT part 1, but also NOT at the start of the day
        val timeslotNotAtStart = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(10,0), LocalTime.of(11,0))
        val eventPartNotFirstNotAtStart = createEventPart(user1, event1, timeslotNotAtStart, partNumber = 2, totalPartsInGroup = 2)

        constraintVerifier.verifyThat(TimeTableConstraintProvider::partIsNotFirstForStartOfDayHardPenalize)
            .given(eventPartNotFirstNotAtStart)
            .rewardsWith(0)
    }

    @Test
    fun partIsNotFirstForStartOfDayHardPenalize_noWorkTime() {
        val user1 = createUser() // No work time defined
        val event1 = createEvent(user1.id!!)

        val timeslot = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(9,0), LocalTime.of(10,0))
        // EventPart is part 2, but without work time, the "start of day" condition isn't met
        val eventPart = createEventPart(user1, event1, timeslot, partNumber = 2, totalPartsInGroup = 2)

        constraintVerifier.verifyThat(TimeTableConstraintProvider::partIsNotFirstForStartOfDayHardPenalize)
            .given(eventPart)
            .rewardsWith(0) // Filter `workTime == null` should prevent penalty
    }

    // --- Tests for eventPartsReversedHardPenalize ---
    @Test
    fun eventPartsReversedHardPenalize_conflict() {
        val user1 = createUser()
        val event1 = createEvent(user1.id!!)
        val commonGroupId = event1.id!!

        // Part 1 (ep1) is 10:00-10:15
        // Part 2 (ep2) is 09:00-09:15 (scheduled before Part 1)
        val ts1 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(10,0), LocalTime.of(10,15))
        val ts2 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(9,0), LocalTime.of(9,15))

        val ep1 = createEventPart(user1, event1, ts1, partNumber = 1, totalPartsInGroup = 2, groupId = commonGroupId)
        val ep2 = createEventPart(user1, event1, ts2, partNumber = 2, totalPartsInGroup = 2, groupId = commonGroupId)

        // ep2 (part 2) starts before ep1 (part 1) ends. Duration.between(ts1.endTime, ts2.startTime) will be negative.
        constraintVerifier.verifyThat(TimeTableConstraintProvider::eventPartsReversedHardPenalize)
            .given(ep1, ep2) // ep1.part < ep2.part is true
            .penalizesBy(1)
    }

    @Test
    fun eventPartsReversedHardPenalize_noConflict_sequential() {
        val user1 = createUser()
        val event1 = createEvent(user1.id!!)
        val commonGroupId = event1.id!!

        val ts1 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(9,0), LocalTime.of(9,15))
        val ts2 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(9,15), LocalTime.of(9,30))

        val ep1 = createEventPart(user1, event1, ts1, partNumber = 1, totalPartsInGroup = 2, groupId = commonGroupId)
        val ep2 = createEventPart(user1, event1, ts2, partNumber = 2, totalPartsInGroup = 2, groupId = commonGroupId)

        constraintVerifier.verifyThat(TimeTableConstraintProvider::eventPartsReversedHardPenalize)
            .given(ep1, ep2)
            .rewardsWith(0)
    }

    @Test
    fun eventPartsReversedHardPenalize_noConflict_withGap() {
        val user1 = createUser()
        val event1 = createEvent(user1.id!!)
        val commonGroupId = event1.id!!

        val ts1 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(9,0), LocalTime.of(9,15))
        val ts2 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(10,0), LocalTime.of(10,15)) // Gap, but not reversed

        val ep1 = createEventPart(user1, event1, ts1, partNumber = 1, totalPartsInGroup = 2, groupId = commonGroupId)
        val ep2 = createEventPart(user1, event1, ts2, partNumber = 2, totalPartsInGroup = 2, groupId = commonGroupId)

        constraintVerifier.verifyThat(TimeTableConstraintProvider::eventPartsReversedHardPenalize)
            .given(ep1, ep2)
            .rewardsWith(0)
    }

    @Test
    fun eventPartsReversedHardPenalize_noConflict_partsInWrongOrderInGiven() {
        // This test ensures that the Joiners.lessThan(EventPart::part) is effective,
        // so if we pass (ep2, ep1) to .given(), they are not matched by the join.
        val user1 = createUser()
        val event1 = createEvent(user1.id!!)
        val commonGroupId = event1.id!!

        val ts1 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(10,0), LocalTime.of(10,15))
        val ts2 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(9,0), LocalTime.of(9,15))

        val ep1 = createEventPart(user1, event1, ts1, partNumber = 1, totalPartsInGroup = 2, groupId = commonGroupId)
        val ep2 = createEventPart(user1, event1, ts2, partNumber = 2, totalPartsInGroup = 2, groupId = commonGroupId)

        constraintVerifier.verifyThat(TimeTableConstraintProvider::eventPartsReversedHardPenalize)
            .given(ep2, ep1) // ep2.part > ep1.part, so join should not occur
            .rewardsWith(0)
    }

    // --- Tests for modifiableConflictHardPenalize ---
    @Test
    fun modifiableConflictHardPenalize_conflict_timeChanged() {
        val user1 = createUser()
        val event1 = createEvent(user1.id!!)

        // Original startDate: 2024-01-15T09:00:00. Event is not modifiable.
        // Scheduled timeslot: 2024-01-15T10:00:00 (different time)
        val originalStartDateStr = "2024-01-15T09:00:00"
        val timeslotChanged = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(10,0), LocalTime.of(11,0), MonthDay.of(1,15))

        val eventPart = createEventPart(user1, event1, timeslotChanged,
                                      startDateString = originalStartDateStr,
                                      endDateString = "2024-01-15T10:00:00") // endDateString to match original duration for simplicity
        eventPart.modifiable = false

        constraintVerifier.verifyThat(TimeTableConstraintProvider::modifiableConflictHardPenalize)
            .given(eventPart)
            .penalizesBy(1)
    }

    @Test
    fun modifiableConflictHardPenalize_conflict_dayChanged() {
        val user1 = createUser()
        val event1 = createEvent(user1.id!!)

        // Original startDate: 2024-01-15T09:00:00. Event is not modifiable.
        // Scheduled timeslot: 2024-01-16T09:00:00 (different day)
        val originalStartDateStr = "2024-01-15T09:00:00"
        val scheduledDate = LocalDate.of(2024,1,16) // Tuesday

        val timeslotChanged = createTimeslot(DayOfWeek.TUESDAY, LocalTime.of(9,0), LocalTime.of(10,0), scheduledDate)

        val eventPart = createEventPart(user1, event1, timeslotChanged,
                                      startDateString = originalStartDateStr, // Monday
                                      endDateString = "2024-01-15T10:00:00")
        eventPart.modifiable = false

        // eventPart.startDate is 2024-01-15T09:00:00
        // timeslotChanged.date is 2024-01-16, timeslotChanged.startTime is 09:00
        // Constraint will compare 2024-01-15T09:00:00 with 2024-01-16T09:00:00. They are different.

        constraintVerifier.verifyThat(TimeTableConstraintProvider::modifiableConflictHardPenalize)
            .given(eventPart)
            .penalizesBy(1)
    }

    @Test
    fun modifiableConflictHardPenalize_noConflict_notModifiable_timeAndDaySame() {
        val user1 = createUser()
        val event1 = createEvent(user1.id!!)

        val originalStartDateStr = "2024-01-15T09:00:00"
        val originalDate = LocalDate.of(2024,1,15)
        // Scheduled timeslot matches original start datetime
        val timeslotSame = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(9,0), LocalTime.of(10,0), originalDate)

        val eventPart = createEventPart(user1, event1, timeslotSame,
                                      startDateString = originalStartDateStr,
                                      endDateString = "2024-01-15T10:00:00")
        eventPart.modifiable = false

        constraintVerifier.verifyThat(TimeTableConstraintProvider::modifiableConflictHardPenalize)
            .given(eventPart)
            .rewardsWith(0)
    }

    @Test
    fun modifiableConflictHardPenalize_noConflict_isModifiable() {
        val user1 = createUser()
        val event1 = createEvent(user1.id!!)

        val originalStartDateStr = "2024-01-15T09:00:00"
        // Time changed, but event is modifiable
        val timeslotChanged = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(10,0), LocalTime.of(11,0), MonthDay.of(1,15))

        val eventPart = createEventPart(user1, event1, timeslotChanged,
                                      startDateString = originalStartDateStr,
                                      endDateString = "2024-01-15T10:00:00")
        eventPart.modifiable = true // Event IS modifiable

        constraintVerifier.verifyThat(TimeTableConstraintProvider::modifiableConflictHardPenalize)
            .given(eventPart)
            .rewardsWith(0)
    }

    @Test
    fun modifiableConflictHardPenalize_noConflict_notPart1() {
        val user1 = createUser()
        val event1 = createEvent(user1.id!!)

        val originalStartDateStr = "2024-01-15T09:00:00"
        val timeslotChanged = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(10,0), LocalTime.of(11,0), MonthDay.of(1,15))

        // Event part is not part 1, so constraint shouldn't apply
        val eventPart = createEventPart(user1, event1, timeslotChanged,
                                      partNumber = 2, totalPartsInGroup = 2,
                                      startDateString = originalStartDateStr,
                                      endDateString = "2024-01-15T10:00:00")
        eventPart.modifiable = false

        constraintVerifier.verifyThat(TimeTableConstraintProvider::modifiableConflictHardPenalize)
            .given(eventPart)
            .rewardsWith(0)
    }

    // --- Tests for meetingWithSameEventSlotHardConflict ---
    @Test
    fun meetingWithSameEventSlotHardConflict_conflict() {
        val user1 = createUser()
        val event1 = createEvent(user1.id!!) // Non-meeting event
        val event2 = createEvent(user1.id!!) // Event that will be a meeting

        val commonTimeslot = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(10,0), LocalTime.of(11,0))

        val nonMeetingEventPart = createEventPart(user1, event1, commonTimeslot)
        val meetingEventPart = createEventPart(user1, event2, commonTimeslot, meetingId = UUID.randomUUID())

        constraintVerifier.verifyThat(TimeTableConstraintProvider::meetingWithSameEventSlotHardConflict)
            .given(nonMeetingEventPart, meetingEventPart)
            .penalizesBy(1)
    }

    @Test
    fun meetingWithSameEventSlotHardConflict_noConflict_differentTimeslots() {
        val user1 = createUser()
        val event1 = createEvent(user1.id!!)
        val event2 = createEvent(user1.id!!)

        val timeslot1 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(10,0), LocalTime.of(11,0))
        val timeslot2 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(11,0), LocalTime.of(12,0))

        val nonMeetingEventPart = createEventPart(user1, event1, timeslot1)
        val meetingEventPart = createEventPart(user1, event2, timeslot2, meetingId = UUID.randomUUID())

        constraintVerifier.verifyThat(TimeTableConstraintProvider::meetingWithSameEventSlotHardConflict)
            .given(nonMeetingEventPart, meetingEventPart)
            .rewardsWith(0)
    }

    @Test
    fun meetingWithSameEventSlotHardConflict_noConflict_bothMeetings() {
        val user1 = createUser()
        val event1 = createEvent(user1.id!!)
        val event2 = createEvent(user1.id!!)
        val commonTimeslot = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(10,0), LocalTime.of(11,0))

        // Both are meetings, so this specific constraint shouldn't penalize.
        // Other constraints (like userTimeSlotHardConflict) would handle if same user.
        val meetingEventPart1 = createEventPart(user1, event1, commonTimeslot, meetingId = UUID.randomUUID())
        val meetingEventPart2 = createEventPart(user1, event2, commonTimeslot, meetingId = UUID.randomUUID())


        constraintVerifier.verifyThat(TimeTableConstraintProvider::meetingWithSameEventSlotHardConflict)
            .given(meetingEventPart1, meetingEventPart2)
            .rewardsWith(0)
    }

    @Test
    fun meetingWithSameEventSlotHardConflict_noConflict_noMeetings() {
        val user1 = createUser()
        val event1 = createEvent(user1.id!!)
        val event2 = createEvent(user1.id!!)
        val commonTimeslot = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(10,0), LocalTime.of(11,0))

        val nonMeetingEventPart1 = createEventPart(user1, event1, commonTimeslot)
        val nonMeetingEventPart2 = createEventPart(user1, event2, commonTimeslot) // Both non-meetings

        constraintVerifier.verifyThat(TimeTableConstraintProvider::meetingWithSameEventSlotHardConflict)
            .given(nonMeetingEventPart1, nonMeetingEventPart2)
            .rewardsWith(0)
    }

    // --- Tests for positiveImpactTimeScoreMediumPenalize ---
    @Test
    fun positiveImpactTimeScoreMediumPenalize_conflict_notAtPositiveTime() {
        val user1 = createUser()
        val event1 = createEvent(user1.id!!)
        val workDay = DayOfWeek.MONDAY
        val positiveTime = LocalTime.of(10,0)
        createWorkTime(user1, workDay, LocalTime.of(9,0), LocalTime.of(17,0))

        // EventPart scheduled at 11:00, but positive impact time is 10:00
        val scheduledTimeslot = createTimeslot(workDay, LocalTime.of(11,0), LocalTime.of(12,0))
        val eventPart = createEventPart(user1, event1, scheduledTimeslot, partNumber = 1)
        eventPart.positiveImpactScore = 10
        eventPart.positiveImpactDayOfWeek = workDay
        eventPart.positiveImpactTime = positiveTime

        constraintVerifier.verifyThat(TimeTableConstraintProvider::positiveImpactTimeScoreMediumPenalize)
            .given(eventPart)
            .penalizesBy(1)
    }

    @Test
    fun positiveImpactTimeScoreMediumPenalize_noConflict_atPositiveTime() {
        val user1 = createUser()
        val event1 = createEvent(user1.id!!)
        val workDay = DayOfWeek.MONDAY
        val positiveTime = LocalTime.of(10,0)
        createWorkTime(user1, workDay, LocalTime.of(9,0), LocalTime.of(17,0))

        val scheduledTimeslot = createTimeslot(workDay, positiveTime, positiveTime.plusHours(1))
        val eventPart = createEventPart(user1, event1, scheduledTimeslot, partNumber = 1)
        eventPart.positiveImpactScore = 10
        eventPart.positiveImpactDayOfWeek = workDay
        eventPart.positiveImpactTime = positiveTime

        constraintVerifier.verifyThat(TimeTableConstraintProvider::positiveImpactTimeScoreMediumPenalize)
            .given(eventPart)
            .rewardsWith(0)
    }

    @Test
    fun positiveImpactTimeScoreMediumPenalize_noConflict_noImpactScore() {
        val user1 = createUser()
        val event1 = createEvent(user1.id!!)
        val workDay = DayOfWeek.MONDAY
        createWorkTime(user1, workDay, LocalTime.of(9,0), LocalTime.of(17,0))

        val scheduledTimeslot = createTimeslot(workDay, LocalTime.of(11,0), LocalTime.of(12,0))
        val eventPart = createEventPart(user1, event1, scheduledTimeslot, partNumber = 1)
        eventPart.positiveImpactScore = 0 // No score
        eventPart.positiveImpactDayOfWeek = workDay
        eventPart.positiveImpactTime = LocalTime.of(10,0)

        constraintVerifier.verifyThat(TimeTableConstraintProvider::positiveImpactTimeScoreMediumPenalize)
            .given(eventPart)
            .rewardsWith(0)
    }

    @Test
    fun positiveImpactTimeScoreMediumPenalize_conflict_outsideWorkHours() {
        val user1 = createUser()
        val event1 = createEvent(user1.id!!)
        val workDay = DayOfWeek.MONDAY
        val positiveTime = LocalTime.of(8,0) // Positive time is outside work hours
        createWorkTime(user1, workDay, LocalTime.of(9,0), LocalTime.of(17,0)) // Work 9-17

        // Scheduled at the positive time, but this time itself makes the event end outside work hours.
        // Event is 1hr long by default in helper. Scheduled 8-9. Work starts at 9.
        // The constraint's internal filter for eventOverallEndDateTime > workEndDateTime will be true.
        // Correction: The filter is `eventOverallEndDateTime > workEndDateTime`. If it's scheduled at 8, but work starts at 9.
        // The current constraint logic checks if the event *as scheduled* fits within work hours.
        // If scheduled at positive time (8:00) and positive time is itself outside workhours (e.g. event ends 9:00 but work starts 9:00 is OK)
        // Let's make event 8:00-9:00. Work 9:00-17:00. This is tricky.
        // The constraint says: if (eventOverallEndDateTime > workEndDateTime) { return@filter true }
        // If an event is scheduled at a "positiveImpactTime" which results in it violating work hours, it should be penalized.

        val scheduledTimeslot = createTimeslot(workDay, positiveTime, positiveTime.plusHours(1)) // Scheduled 8-9
        val eventPart = createEventPart(user1, event1, scheduledTimeslot, partNumber = 1,
                                      startDateString = "2024-01-15T08:00:00",
                                      endDateString = "2024-01-15T09:00:00")
        eventPart.positiveImpactScore = 10
        eventPart.positiveImpactDayOfWeek = workDay
        eventPart.positiveImpactTime = positiveTime

        // The constraint's filter `eventOverallEndDateTime > workEndDateTime` would not be true here.
        // However, `outOfWorkTimesBoundaryFromStartTimeHardPenalize` would catch this.
        // The positiveImpact constraint itself would find it's AT positive time, so it wouldn't penalize based on time mismatch.
        // The penalty comes if it's NOT the positive time OR if it violates work hours.
        // This needs careful thought on how `positiveImpactTimeScoreMediumPenalize` interacts with working hours.
        // The constraint is: `(eventOverallEndDateTime > workEndDateTime) || ( (positiveImpactDayOfWeek != timeslot.dayOfWeek) || (positiveImpactTime != timeslot.startTime) )`
        // If scheduled at positive time (matches), but that positive time makes it exceed work hours, it should penalize.
        // Scenario: Work 9-10. Positive time 9:30. Event is 1hr. Scheduled 9:30-10:30. Exceeds work time.
        createWorkTime(user1, DayOfWeek.FRIDAY, LocalTime.of(9,0), LocalTime.of(10,0))
        val friday = DayOfWeek.FRIDAY
        val scheduledFridaySlot = createTimeslot(friday, LocalTime.of(9,30), LocalTime.of(10,30), date = LocalDate.of(2024,1,19))
        val eventPartFriday = createEventPart(user1, event1, scheduledFridaySlot, partNumber = 1,
                                           startDateString = "2024-01-19T09:30:00",
                                           endDateString = "2024-01-19T10:30:00")
        eventPartFriday.positiveImpactScore = 10
        eventPartFriday.positiveImpactDayOfWeek = friday
        eventPartFriday.positiveImpactTime = LocalTime.of(9,30)


        constraintVerifier.verifyThat(TimeTableConstraintProvider::positiveImpactTimeScoreMediumPenalize)
            .given(eventPartFriday)
            .penalizesBy(1) // Because it's scheduled at positive time, but this scheduling violates work hours.
    }


    // --- Tests for negativeImpactTimeScoreMediumPenalize ---
    @Test
    fun negativeImpactTimeScoreMediumPenalize_conflict_atNegativeTime() {
        val user1 = createUser()
        val event1 = createEvent(user1.id!!)
        val workDay = DayOfWeek.MONDAY
        val negativeTime = LocalTime.of(14,0)
        createWorkTime(user1, workDay, LocalTime.of(9,0), LocalTime.of(17,0))

        // EventPart scheduled at 14:00, which is the negative impact time
        val scheduledTimeslot = createTimeslot(workDay, negativeTime, negativeTime.plusHours(1))
        val eventPart = createEventPart(user1, event1, scheduledTimeslot, partNumber = 1)
        eventPart.negativeImpactScore = 10
        eventPart.negativeImpactDayOfWeek = workDay
        eventPart.negativeImpactTime = negativeTime

        constraintVerifier.verifyThat(TimeTableConstraintProvider::negativeImpactTimeScoreMediumPenalize)
            .given(eventPart)
            .penalizesBy(1)
    }

    @Test
    fun negativeImpactTimeScoreMediumPenalize_noConflict_notAtNegativeTime() {
        val user1 = createUser()
        val event1 = createEvent(user1.id!!)
        val workDay = DayOfWeek.MONDAY
        val negativeTime = LocalTime.of(14,0)
        createWorkTime(user1, workDay, LocalTime.of(9,0), LocalTime.of(17,0))

        // EventPart scheduled at 10:00, different from negative impact time
        val scheduledTimeslot = createTimeslot(workDay, LocalTime.of(10,0), LocalTime.of(11,0))
        val eventPart = createEventPart(user1, event1, scheduledTimeslot, partNumber = 1)
        eventPart.negativeImpactScore = 10
        eventPart.negativeImpactDayOfWeek = workDay
        eventPart.negativeImpactTime = negativeTime

        constraintVerifier.verifyThat(TimeTableConstraintProvider::negativeImpactTimeScoreMediumPenalize)
            .given(eventPart)
            .rewardsWith(0)
    }

    @Test
    fun negativeImpactTimeScoreMediumPenalize_noConflict_noImpactScore() {
        val user1 = createUser()
        val event1 = createEvent(user1.id!!)
        val workDay = DayOfWeek.MONDAY
        createWorkTime(user1, workDay, LocalTime.of(9,0), LocalTime.of(17,0))

        val scheduledTimeslot = createTimeslot(workDay, LocalTime.of(14,0), LocalTime.of(15,0))
        val eventPart = createEventPart(user1, event1, scheduledTimeslot, partNumber = 1)
        eventPart.negativeImpactScore = 0 // No score
        eventPart.negativeImpactDayOfWeek = workDay
        eventPart.negativeImpactTime = LocalTime.of(14,0)

        constraintVerifier.verifyThat(TimeTableConstraintProvider::negativeImpactTimeScoreMediumPenalize)
            .given(eventPart)
            .rewardsWith(0)
    }

    // --- Tests for notPreferredDayOfWeekMediumPenalize ---
    @Test
    fun notPreferredDayOfWeekMediumPenalize_conflict() {
        val user1 = createUser()
        val event1 = createEvent(user1.id!!)
        val preferredDay = DayOfWeek.MONDAY
        val scheduledDay = DayOfWeek.TUESDAY // Scheduled on a different day

        val scheduledTimeslot = createTimeslot(scheduledDay, LocalTime.of(10,0), LocalTime.of(11,0), date = LocalDate.of(2024,1,16))
        val eventPart = createEventPart(user1, event1, scheduledTimeslot)
        eventPart.preferredDayOfWeek = preferredDay

        constraintVerifier.verifyThat(TimeTableConstraintProvider::notPreferredDayOfWeekMediumPenalize)
            .given(eventPart)
            .penalizesBy(1)
    }

    @Test
    fun notPreferredDayOfWeekMediumPenalize_noConflict() {
        val user1 = createUser()
        val event1 = createEvent(user1.id!!)
        val preferredDay = DayOfWeek.MONDAY
        val scheduledDay = DayOfWeek.MONDAY // Scheduled on preferred day

        val scheduledTimeslot = createTimeslot(scheduledDay, LocalTime.of(10,0), LocalTime.of(11,0), date = LocalDate.of(2024,1,15))
        val eventPart = createEventPart(user1, event1, scheduledTimeslot)
        eventPart.preferredDayOfWeek = preferredDay

        constraintVerifier.verifyThat(TimeTableConstraintProvider::notPreferredDayOfWeekMediumPenalize)
            .given(eventPart)
            .rewardsWith(0)
    }

    @Test
    fun notPreferredDayOfWeekMediumPenalize_noConflict_noPreference() {
        val user1 = createUser()
        val event1 = createEvent(user1.id!!)
        // No preferredDayOfWeek set on eventPart

        val scheduledTimeslot = createTimeslot(DayOfWeek.TUESDAY, LocalTime.of(10,0), LocalTime.of(11,0))
        val eventPart = createEventPart(user1, event1, scheduledTimeslot)
        eventPart.preferredDayOfWeek = null // Explicitly no preference

        constraintVerifier.verifyThat(TimeTableConstraintProvider::notPreferredDayOfWeekMediumPenalize)
            .given(eventPart)
            .rewardsWith(0)
    }

    // --- Tests for notPreferredStartTimeMediumPenalize ---
    @Test
    fun notPreferredStartTimeMediumPenalize_conflict() {
        val user1 = createUser()
        val event1 = createEvent(user1.id!!)
        val workDay = DayOfWeek.MONDAY
        val preferredStartTime = LocalTime.of(10,0)
        val scheduledStartTime = LocalTime.of(11,0) // Scheduled at a different time
        createWorkTime(user1, workDay, LocalTime.of(9,0), LocalTime.of(17,0))


        val scheduledTimeslot = createTimeslot(workDay, scheduledStartTime, scheduledStartTime.plusHours(1))
        val eventPart = createEventPart(user1, event1, scheduledTimeslot, partNumber = 1)
        eventPart.preferredTime = preferredStartTime

        constraintVerifier.verifyThat(TimeTableConstraintProvider::notPreferredStartTimeMediumPenalize)
            .given(eventPart)
            .penalizesBy(1)
    }

    @Test
    fun notPreferredStartTimeMediumPenalize_noConflict_atPreferredTime() {
        val user1 = createUser()
        val event1 = createEvent(user1.id!!)
        val workDay = DayOfWeek.MONDAY
        val preferredStartTime = LocalTime.of(10,0)
        createWorkTime(user1, workDay, LocalTime.of(9,0), LocalTime.of(17,0))

        val scheduledTimeslot = createTimeslot(workDay, preferredStartTime, preferredStartTime.plusHours(1))
        val eventPart = createEventPart(user1, event1, scheduledTimeslot, partNumber = 1)
        eventPart.preferredTime = preferredStartTime

        constraintVerifier.verifyThat(TimeTableConstraintProvider::notPreferredStartTimeMediumPenalize)
            .given(eventPart)
            .rewardsWith(0)
    }

    @Test
    fun notPreferredStartTimeMediumPenalize_noConflict_noPreference() {
        val user1 = createUser()
        val event1 = createEvent(user1.id!!)
        val workDay = DayOfWeek.MONDAY
        createWorkTime(user1, workDay, LocalTime.of(9,0), LocalTime.of(17,0))

        val scheduledTimeslot = createTimeslot(workDay, LocalTime.of(11,0), LocalTime.of(12,0))
        val eventPart = createEventPart(user1, event1, scheduledTimeslot, partNumber = 1)
        eventPart.preferredTime = null // No preference

        constraintVerifier.verifyThat(TimeTableConstraintProvider::notPreferredStartTimeMediumPenalize)
            .given(eventPart)
            .rewardsWith(0)
    }

    @Test
    fun notPreferredStartTimeMediumPenalize_noConflict_notPart1() {
        val user1 = createUser()
        val event1 = createEvent(user1.id!!)
        val workDay = DayOfWeek.MONDAY
        val preferredStartTime = LocalTime.of(10,0)
        val scheduledStartTime = LocalTime.of(11,0)
        createWorkTime(user1, workDay, LocalTime.of(9,0), LocalTime.of(17,0))

        val scheduledTimeslot = createTimeslot(workDay, scheduledStartTime, scheduledStartTime.plusHours(1))
        // Event is part 2, constraint only applies to part 1
        val eventPart = createEventPart(user1, event1, scheduledTimeslot, partNumber = 2, totalPartsInGroup = 2)
        eventPart.preferredTime = preferredStartTime

        constraintVerifier.verifyThat(TimeTableConstraintProvider::notPreferredStartTimeMediumPenalize)
            .given(eventPart)
            .rewardsWith(0)
    }

    @Test
    fun notPreferredStartTimeMediumPenalize_noConflict_outsideWorkHours() {
        // This test verifies that if the event is outside working hours, this specific constraint does not fire,
        // as other constraints (outOfWorkTimesBoundary) should handle it.
        val user1 = createUser()
        val event1 = createEvent(user1.id!!)
        val workDay = DayOfWeek.MONDAY
        val preferredStartTime = LocalTime.of(10,0)
        // Work 13:00-17:00. Preferred time 10:00. Scheduled time 10:00 (matches pref, but outside work hours)
        createWorkTime(user1, workDay, LocalTime.of(13,0), LocalTime.of(17,0))

        val scheduledTimeslot = createTimeslot(workDay, preferredStartTime, preferredStartTime.plusHours(1))
        val eventPart = createEventPart(user1, event1, scheduledTimeslot, partNumber = 1)
        eventPart.preferredTime = preferredStartTime

        constraintVerifier.verifyThat(TimeTableConstraintProvider::notPreferredStartTimeMediumPenalize)
            .given(eventPart)
            .rewardsWith(0) // The constraint's filter for isWithinWorkTime should prevent penalty here
    }

    // --- Tests for notPreferredScheduleStartTimeRangeMediumPenalize ---
    @Test
    fun notPreferredScheduleStartTimeRangeMediumPenalize_conflict_startsBeforeRange() {
        val user1 = createUser()
        val event1 = createEvent(user1.id!!)
        val workDay = DayOfWeek.MONDAY
        // User works 9-17. Preferred schedule range for event is 10:00-12:00.
        createWorkTime(user1, workDay, LocalTime.of(9,0), LocalTime.of(17,0))

        // Event (1hr) scheduled at 9:00, which is before preferred 10:00 start.
        val scheduledTimeslot = createTimeslot(workDay, LocalTime.of(9,0), LocalTime.of(10,0))
        val eventPart = createEventPart(user1, event1, scheduledTimeslot, partNumber = 1)
        eventPart.preferredStartTimeRange = LocalTime.of(10,0)
        eventPart.preferredEndTimeRange = LocalTime.of(12,0) // End of range also needed for the constraint's first filter

        constraintVerifier.verifyThat(TimeTableConstraintProvider::notPreferredScheduleStartTimeRangeMediumPenalize)
            .given(eventPart)
            .penalizesBy(1)
    }

    @Test
    fun notPreferredScheduleStartTimeRangeMediumPenalize_conflict_endsAfterWorkHoursDueToRange() {
        val user1 = createUser()
        val event1 = createEvent(user1.id!!)
        val workDay = DayOfWeek.MONDAY
        // User works 9-10. Preferred schedule range for event is 9:30-10:30.
        createWorkTime(user1, workDay, LocalTime.of(9,0), LocalTime.of(10,0))

        // Event (1hr by default helper) scheduled at 9:30 (within preferred start).
        // Event would end 10:30. Work ends 10:00. This is caught by `possibleEndTime > workTime.endTime`.
        val scheduledTimeslot = createTimeslot(workDay, LocalTime.of(9,30), LocalTime.of(10,30))
        val eventPart = createEventPart(user1, event1, scheduledTimeslot, partNumber = 1, totalPartsInGroup = 1,
                                      startDateString="2024-01-15T09:30:00", endDateString="2024-01-15T10:30:00")
        eventPart.preferredStartTimeRange = LocalTime.of(9,30)
        eventPart.preferredEndTimeRange = LocalTime.of(10,30)

        constraintVerifier.verifyThat(TimeTableConstraintProvider::notPreferredScheduleStartTimeRangeMediumPenalize)
            .given(eventPart)
            .penalizesBy(1)
    }

    @Test
    fun notPreferredScheduleStartTimeRangeMediumPenalize_noConflict_startsInRangeAndFitsWorkTime() {
        val user1 = createUser()
        val event1 = createEvent(user1.id!!)
        val workDay = DayOfWeek.MONDAY
        createWorkTime(user1, workDay, LocalTime.of(9,0), LocalTime.of(17,0))

        // Event (1hr) scheduled at 10:00, within preferred 10:00-12:00 range and fits work hours.
        val scheduledTimeslot = createTimeslot(workDay, LocalTime.of(10,0), LocalTime.of(11,0))
        val eventPart = createEventPart(user1, event1, scheduledTimeslot, partNumber = 1)
        eventPart.preferredStartTimeRange = LocalTime.of(10,0)
        eventPart.preferredEndTimeRange = LocalTime.of(12,0)

        constraintVerifier.verifyThat(TimeTableConstraintProvider::notPreferredScheduleStartTimeRangeMediumPenalize)
            .given(eventPart)
            .rewardsWith(0)
    }

    // --- Tests for notPreferredScheduleEndTimeRangeMediumPenalize ---
    @Test
    fun notPreferredScheduleEndTimeRangeMediumPenalize_conflict_endsAfterRange() {
        val user1 = createUser()
        val event1 = createEvent(user1.id!!)
        val workDay = DayOfWeek.MONDAY
        createWorkTime(user1, workDay, LocalTime.of(9,0), LocalTime.of(17,0))

        // Event (1hr) scheduled 10:00-11:00. Preferred schedule range ends 10:30. Event ends at 11:00.
        val scheduledTimeslot = createTimeslot(workDay, LocalTime.of(10,0), LocalTime.of(11,0))
        val eventPart = createEventPart(user1, event1, scheduledTimeslot, partNumber = 1, totalPartsInGroup = 1) // lastPart = 1
        eventPart.preferredStartTimeRange = LocalTime.of(9,0) // Start of range also needed for constraint's first filter
        eventPart.preferredEndTimeRange = LocalTime.of(10,30)

        constraintVerifier.verifyThat(TimeTableConstraintProvider::notPreferredScheduleEndTimeRangeMediumPenalize)
            .given(eventPart)
            .penalizesBy(1)
    }

    @Test
    fun notPreferredScheduleEndTimeRangeMediumPenalize_noConflict_endsInRange() {
        val user1 = createUser()
        val event1 = createEvent(user1.id!!)
        val workDay = DayOfWeek.MONDAY
        createWorkTime(user1, workDay, LocalTime.of(9,0), LocalTime.of(17,0))

        // Event (1hr) scheduled 9:00-10:00. Preferred schedule range ends 10:30. Event ends at 10:00.
        val scheduledTimeslot = createTimeslot(workDay, LocalTime.of(9,0), LocalTime.of(10,0))
        val eventPart = createEventPart(user1, event1, scheduledTimeslot, partNumber = 1, totalPartsInGroup = 1)
        eventPart.preferredStartTimeRange = LocalTime.of(9,0)
        eventPart.preferredEndTimeRange = LocalTime.of(10,30)

        constraintVerifier.verifyThat(TimeTableConstraintProvider::notPreferredScheduleEndTimeRangeMediumPenalize)
            .given(eventPart)
            .rewardsWith(0)
    }

    // --- Tests for externalMeetingModifiableConflictMediumPenalize ---
    @Test
    fun externalMeetingModifiableConflict_conflict_timeChanged() {
        val user1 = createUser()
        val event1 = createEvent(user1.id!!)
        val originalStartDateStr = "2024-01-15T09:00:00"
        // Scheduled for 10:00, original was 09:00
        val timeslotChanged = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(10,0), LocalTime.of(11,0), date=LocalDate.of(2024,1,15))

        val eventPart = createEventPart(user1, event1, timeslotChanged,
                                      startDateString = originalStartDateStr,
                                      endDateString = "2024-01-15T10:00:00")
        eventPart.isExternalMeeting = true
        eventPart.isExternalMeetingModifiable = false

        constraintVerifier.verifyThat(TimeTableConstraintProvider::externalMeetingModifiableConflictMediumPenalize)
            .given(eventPart)
            .penalizesBy(1)
    }

    @Test
    fun externalMeetingModifiableConflict_noConflict_isModifiable() {
        val user1 = createUser()
        val event1 = createEvent(user1.id!!)
        val originalStartDateStr = "2024-01-15T09:00:00"
        val timeslotChanged = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(10,0), LocalTime.of(11,0), date=LocalDate.of(2024,1,15))

        val eventPart = createEventPart(user1, event1, timeslotChanged,
                                      startDateString = originalStartDateStr,
                                      endDateString = "2024-01-15T10:00:00")
        eventPart.isExternalMeeting = true
        eventPart.isExternalMeetingModifiable = true // IS Modifiable

        constraintVerifier.verifyThat(TimeTableConstraintProvider::externalMeetingModifiableConflictMediumPenalize)
            .given(eventPart)
            .rewardsWith(0)
    }

    @Test
    fun externalMeetingModifiableConflict_noConflict_notExternalMeeting() {
        val user1 = createUser()
        val event1 = createEvent(user1.id!!)
        val originalStartDateStr = "2024-01-15T09:00:00"
        val timeslotChanged = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(10,0), LocalTime.of(11,0), date=LocalDate.of(2024,1,15))

        val eventPart = createEventPart(user1, event1, timeslotChanged,
                                      startDateString = originalStartDateStr,
                                      endDateString = "2024-01-15T10:00:00")
        eventPart.isExternalMeeting = false // NOT an external meeting
        eventPart.isExternalMeetingModifiable = false

        constraintVerifier.verifyThat(TimeTableConstraintProvider::externalMeetingModifiableConflictMediumPenalize)
            .given(eventPart)
            .rewardsWith(0)
    }

    // --- Tests for meetingModifiableConflictMediumPenalize ---
    @Test
    fun meetingModifiableConflict_conflict_timeChanged() {
        val user1 = createUser()
        val event1 = createEvent(user1.id!!)
        val originalStartDateStr = "2024-01-15T09:00:00"
        val timeslotChanged = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(10,0), LocalTime.of(11,0), date=LocalDate.of(2024,1,15))

        val eventPart = createEventPart(user1, event1, timeslotChanged,
                                      startDateString = originalStartDateStr,
                                      endDateString = "2024-01-15T10:00:00")
        eventPart.isMeeting = true
        eventPart.isMeetingModifiable = false

        constraintVerifier.verifyThat(TimeTableConstraintProvider::meetingModifiableConflictMediumPenalize)
            .given(eventPart)
            .penalizesBy(1)
    }

    @Test
    fun meetingModifiableConflict_noConflict_isModifiable() {
        val user1 = createUser()
        val event1 = createEvent(user1.id!!)
        val originalStartDateStr = "2024-01-15T09:00:00"
        val timeslotChanged = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(10,0), LocalTime.of(11,0), date=LocalDate.of(2024,1,15))

        val eventPart = createEventPart(user1, event1, timeslotChanged,
                                      startDateString = originalStartDateStr,
                                      endDateString = "2024-01-15T10:00:00")
        eventPart.isMeeting = true
        eventPart.isMeetingModifiable = true // IS Modifiable

        constraintVerifier.verifyThat(TimeTableConstraintProvider::meetingModifiableConflictMediumPenalize)
            .given(eventPart)
            .rewardsWith(0)
    }

    // --- Tests for backToBackMeetingsPreferredMediumReward ---
    @Test
    fun backToBackMeetingsPreferredMediumReward_reward_closeMeetings() {
        val user1 = createUser(backToBackMeetings = true) // User prefers back-to-back
        val event1 = createEvent(user1.id!!)
        val event2 = createEvent(user1.id!!)
        val date = LocalDate.of(2024,1,15)

        // Meetings are 10 mins apart
        val ts1 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(9,0), LocalTime.of(10,0), date)
        val ts2 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(10,10), LocalTime.of(11,0), date)

        val ep1 = createEventPart(user1, event1, ts1, partNumber=1, totalPartsInGroup=1, isMeeting=true) // Ensure it's the last part
        val ep2 = createEventPart(user1, event2, ts2, partNumber=1, totalPartsInGroup=1, isMeeting=true) // Ensure it's the first part

        constraintVerifier.verifyThat(TimeTableConstraintProvider::backToBackMeetingsPreferredMediumReward)
            .given(ep1, ep2) // Order might matter depending on join conditions in constraint
            .rewardsWith(1)
    }

    @Test
    fun backToBackMeetingsPreferredMediumReward_noReward_gapTooLarge() {
        val user1 = createUser(backToBackMeetings = true)
        val event1 = createEvent(user1.id!!)
        val event2 = createEvent(user1.id!!)
        val date = LocalDate.of(2024,1,15)

        // Meetings are 30 mins apart (threshold is < 15 for reward)
        val ts1 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(9,0), LocalTime.of(10,0), date)
        val ts2 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(10,30), LocalTime.of(11,0), date)

        val ep1 = createEventPart(user1, event1, ts1, partNumber=1, totalPartsInGroup=1, isMeeting=true)
        val ep2 = createEventPart(user1, event2, ts2, partNumber=1, totalPartsInGroup=1, isMeeting=true)

        constraintVerifier.verifyThat(TimeTableConstraintProvider::backToBackMeetingsPreferredMediumReward)
            .given(ep1, ep2)
            .rewardsWith(0)
    }

    // --- Tests for backToBackMeetingsNotPreferredMediumPenalize ---
    @Test
    fun backToBackMeetingsNotPreferredMediumPenalize_penalize_closeMeetings() {
        val user1 = createUser(backToBackMeetings = false) // User does NOT prefer back-to-back
        val event1 = createEvent(user1.id!!)
        val event2 = createEvent(user1.id!!)
        val date = LocalDate.of(2024,1,15)

        // Meetings are 10 mins apart (threshold for penalty is < 30)
        val ts1 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(9,0), LocalTime.of(10,0), date)
        val ts2 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(10,10), LocalTime.of(11,0), date)

        val ep1 = createEventPart(user1, event1, ts1, partNumber=1, totalPartsInGroup=1, isMeeting=true)
        val ep2 = createEventPart(user1, event2, ts2, partNumber=1, totalPartsInGroup=1, isMeeting=true)

        constraintVerifier.verifyThat(TimeTableConstraintProvider::backToBackMeetingsNotPreferredMediumPenalize)
            .given(ep1, ep2)
            .penalizesBy(1)
    }

    @Test
    fun backToBackMeetingsNotPreferredMediumPenalize_noPenalty_gapLargeEnough() {
        val user1 = createUser(backToBackMeetings = false)
        val event1 = createEvent(user1.id!!)
        val event2 = createEvent(user1.id!!)
        val date = LocalDate.of(2024,1,15)

        // Meetings are 30 mins apart
        val ts1 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(9,0), LocalTime.of(10,0), date)
        val ts2 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(10,30), LocalTime.of(11,0), date)

        val ep1 = createEventPart(user1, event1, ts1, partNumber=1, totalPartsInGroup=1, isMeeting=true)
        val ep2 = createEventPart(user1, event2, ts2, partNumber=1, totalPartsInGroup=1, isMeeting=true)

        constraintVerifier.verifyThat(TimeTableConstraintProvider::backToBackMeetingsNotPreferredMediumPenalize)
            .given(ep1, ep2)
            .rewardsWith(0)
    }

    // --- Tests for backToBackBreakConflictMediumPenalize ---
    @Test
    fun backToBackBreakConflictMediumPenalize_penalize_breakTooCloseToEvent() {
        val user1 = createUser() // Preference for backToBackMeetings doesn't matter here
        val eventBreak = createEvent(user1.id!!)
        val eventTask = createEvent(user1.id!!)
        val date = LocalDate.of(2024,1,15)

        // Break is 10 mins before a task
        val tsBreak = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(9,0), LocalTime.of(9,30), date) // 30 min break
        val tsTask = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(9,40), LocalTime.of(10,40), date) // Task starts 10 min after break

        val epBreak = createEventPart(user1, eventBreak, tsBreak, partNumber=1, totalPartsInGroup=1)
        epBreak.gap = true // This is a break
        val epTask = createEventPart(user1, eventTask, tsTask, partNumber=1, totalPartsInGroup=1)

        constraintVerifier.verifyThat(TimeTableConstraintProvider::backToBackBreakConflictMediumPenalize)
            .given(epBreak, epTask) // Order: break then task
            .penalizesBy(1)
    }

    @Test
    fun backToBackBreakConflictMediumPenalize_noPenalty_gapLargeEnough() {
        val user1 = createUser()
        val eventBreak = createEvent(user1.id!!)
        val eventTask = createEvent(user1.id!!)
        val date = LocalDate.of(2024,1,15)

        // Break is 30 mins before a task
        val tsBreak = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(9,0), LocalTime.of(9,30), date)
        val tsTask = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(10,0), LocalTime.of(11,0), date) // Task starts 30 min after break

        val epBreak = createEventPart(user1, eventBreak, tsBreak, partNumber=1, totalPartsInGroup=1)
        epBreak.gap = true
        val epTask = createEventPart(user1, eventTask, tsTask, partNumber=1, totalPartsInGroup=1)

        constraintVerifier.verifyThat(TimeTableConstraintProvider::backToBackBreakConflictMediumPenalize)
            .given(epBreak, epTask)
            .rewardsWith(0)
    }

    @Test
    fun backToBackBreakConflictMediumPenalize_noPenalty_eventThenBreak() {
        val user1 = createUser()
        val eventTask = createEvent(user1.id!!)
        val eventBreak = createEvent(user1.id!!)
        val date = LocalDate.of(2024,1,15)

        // Task is 10 mins before a break. Constraint is for break THEN event.
        val tsTask = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(9,0), LocalTime.of(10,0), date)
        val tsBreak = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(10,10), LocalTime.of(10,40), date)

        val epTask = createEventPart(user1, eventTask, tsTask, partNumber=1, totalPartsInGroup=1)
        val epBreak = createEventPart(user1, eventBreak, tsBreak, partNumber=1, totalPartsInGroup=1)
        epBreak.gap = true

        constraintVerifier.verifyThat(TimeTableConstraintProvider::backToBackBreakConflictMediumPenalize)
            .given(epTask, epBreak) // Order: task then break
            .rewardsWith(0)
    }

    // --- Tests for maxNumberOfMeetingsConflictMediumPenalize ---
    @Test
    fun maxNumberOfMeetingsConflict_conflict() {
        val user1 = createUser(maxNumberOfMeetings = 2) // Max 2 meetings
        val date = LocalDate.of(2024,1,15)

        val event1 = createEvent(user1.id!!)
        val event2 = createEvent(user1.id!!)
        val event3 = createEvent(user1.id!!)

        // 3 meetings scheduled for the same user on the same day
        val ts1 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(9,0), LocalTime.of(10,0), date)
        val ts2 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(10,0), LocalTime.of(11,0), date)
        val ts3 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(11,0), LocalTime.of(12,0), date)

        val ep1 = createEventPart(user1, event1, ts1, partNumber=1, totalPartsInGroup=1, isMeeting=true, groupId=UUID.randomUUID())
        val ep2 = createEventPart(user1, event2, ts2, partNumber=1, totalPartsInGroup=1, isMeeting=true, groupId=UUID.randomUUID())
        val ep3 = createEventPart(user1, event3, ts3, partNumber=1, totalPartsInGroup=1, isMeeting=true, groupId=UUID.randomUUID())

        constraintVerifier.verifyThat(TimeTableConstraintProvider::maxNumberOfMeetingsConflictMediumPenalize)
            .given(user1, ep1, ep2, ep3) // User must be a fact for its properties to be accessed in groupBy
            .penalizesBy(1) // (3 meetings > 2 allowed)
    }

    @Test
    fun maxNumberOfMeetingsConflict_noConflict() {
        val user1 = createUser(maxNumberOfMeetings = 2)
        val date = LocalDate.of(2024,1,15)

        val event1 = createEvent(user1.id!!)
        val event2 = createEvent(user1.id!!)

        // 2 meetings scheduled
        val ts1 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(9,0), LocalTime.of(10,0), date)
        val ts2 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(10,0), LocalTime.of(11,0), date)

        val ep1 = createEventPart(user1, event1, ts1, partNumber=1, totalPartsInGroup=1, isMeeting=true, groupId=UUID.randomUUID())
        val ep2 = createEventPart(user1, event2, ts2, partNumber=1, totalPartsInGroup=1, isMeeting=true, groupId=UUID.randomUUID())

        constraintVerifier.verifyThat(TimeTableConstraintProvider::maxNumberOfMeetingsConflictMediumPenalize)
            .given(user1, ep1, ep2)
            .rewardsWith(0)
    }

    @Test
    fun maxNumberOfMeetingsConflict_noConflict_notAllMeetings() {
        val user1 = createUser(maxNumberOfMeetings = 1) // Max 1 meeting
        val date = LocalDate.of(2024,1,15)

        val event1 = createEvent(user1.id!!)
        val event2 = createEvent(user1.id!!)

        // 1 meeting, 1 non-meeting
        val ts1 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(9,0), LocalTime.of(10,0), date)
        val ts2 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(10,0), LocalTime.of(11,0), date)

        val ep1 = createEventPart(user1, event1, ts1, partNumber=1, totalPartsInGroup=1, isMeeting=true, groupId=UUID.randomUUID())
        val ep2 = createEventPart(user1, event2, ts2, partNumber=1, totalPartsInGroup=1, isMeeting=false, groupId=UUID.randomUUID()) // Not a meeting

        constraintVerifier.verifyThat(TimeTableConstraintProvider::maxNumberOfMeetingsConflictMediumPenalize)
            .given(user1, ep1, ep2)
            .rewardsWith(0)
    }

    // --- Tests for minNumberOfBreaksConflictSoftPenalize ---
    @Test
    fun minNumberOfBreaksConflict_conflict_tooFewBreaks() {
        val user1 = createUser(minNumberOfBreaks = 2) // Min 2 breaks
        val date = LocalDate.of(2024,1,15)

        val eventBreak1 = createEvent(user1.id!!)
        // Only 1 break scheduled
        val tsBreak1 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(10,0), LocalTime.of(10,30), date)
        val epBreak1 = createEventPart(user1, eventBreak1, tsBreak1, partNumber=1, totalPartsInGroup=1, groupId=UUID.randomUUID())
        epBreak1.gap = true

        constraintVerifier.verifyThat(TimeTableConstraintProvider::minNumberOfBreaksConflictSoftPenalize)
            .given(user1, epBreak1)
            .penalizesBy(1) // (1 break < 2 required)
    }

    @Test
    fun minNumberOfBreaksConflict_conflict_tooManyBreaks() {
        val user1 = createUser(minNumberOfBreaks = 1) // Min 1 break (so allowed range is 1-3)
        val date = LocalDate.of(2024,1,15)

        val eventBreak1 = createEvent(user1.id!!)
        val eventBreak2 = createEvent(user1.id!!)
        val eventBreak3 = createEvent(user1.id!!)
        val eventBreak4 = createEvent(user1.id!!) // 4 breaks scheduled

        val ts1 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(10,0), LocalTime.of(10,30), date)
        val ts2 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(11,0), LocalTime.of(11,30), date)
        val ts3 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(12,0), LocalTime.of(12,30), date)
        val ts4 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(13,0), LocalTime.of(13,30), date)

        val ep1 = createEventPart(user1, eventBreak1, ts1, partNumber=1, totalPartsInGroup=1, groupId=UUID.randomUUID()); ep1.gap = true
        val ep2 = createEventPart(user1, eventBreak2, ts2, partNumber=1, totalPartsInGroup=1, groupId=UUID.randomUUID()); ep2.gap = true
        val ep3 = createEventPart(user1, eventBreak3, ts3, partNumber=1, totalPartsInGroup=1, groupId=UUID.randomUUID()); ep3.gap = true
        val ep4 = createEventPart(user1, eventBreak4, ts4, partNumber=1, totalPartsInGroup=1, groupId=UUID.randomUUID()); ep4.gap = true

        constraintVerifier.verifyThat(TimeTableConstraintProvider::minNumberOfBreaksConflictSoftPenalize)
            .given(user1, ep1, ep2, ep3, ep4)
            .penalizesBy(1) // (4 breaks > 1+2 allowed)
    }

    @Test
    fun minNumberOfBreaksConflict_noConflict_exactMin() {
        val user1 = createUser(minNumberOfBreaks = 2) // Min 2 breaks
        val date = LocalDate.of(2024,1,15)

        val eventBreak1 = createEvent(user1.id!!)
        val eventBreak2 = createEvent(user1.id!!)

        val ts1 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(10,0), LocalTime.of(10,30), date)
        val ts2 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(11,0), LocalTime.of(11,30), date)

        val ep1 = createEventPart(user1, eventBreak1, ts1, partNumber=1, totalPartsInGroup=1, groupId=UUID.randomUUID()); ep1.gap = true
        val ep2 = createEventPart(user1, eventBreak2, ts2, partNumber=1, totalPartsInGroup=1, groupId=UUID.randomUUID()); ep2.gap = true

        constraintVerifier.verifyThat(TimeTableConstraintProvider::minNumberOfBreaksConflictSoftPenalize)
            .given(user1, ep1, ep2)
            .rewardsWith(0) // 2 breaks is exactly min
    }

    @Test
    fun minNumberOfBreaksConflict_noConflict_withinAllowedRange() {
        val user1 = createUser(minNumberOfBreaks = 1) // Min 1 break (allowed 1, 2, or 3)
        val date = LocalDate.of(2024,1,15)

        val eventBreak1 = createEvent(user1.id!!)
        val eventBreak2 = createEvent(user1.id!!)
        // 2 breaks scheduled
        val ts1 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(10,0), LocalTime.of(10,30), date)
        val ts2 = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(11,0), LocalTime.of(11,30), date)

        val ep1 = createEventPart(user1, eventBreak1, ts1, partNumber=1, totalPartsInGroup=1, groupId=UUID.randomUUID()); ep1.gap = true
        val ep2 = createEventPart(user1, eventBreak2, ts2, partNumber=1, totalPartsInGroup=1, groupId=UUID.randomUUID()); ep2.gap = true

        constraintVerifier.verifyThat(TimeTableConstraintProvider::minNumberOfBreaksConflictSoftPenalize)
            .given(user1, ep1, ep2)
            .rewardsWith(0) // 2 breaks is within 1 to 1+2 range
    }

    // --- Tests for differentMonthDayConflictSoftPenalize ---
    @Test
    fun differentMonthDayConflictSoftPenalize_conflict() {
        val user1 = createUser()
        val event1 = createEvent(user1.id!!)
        val originalEndDate = "2024-01-15T10:00:00" // Original end date is Jan 15th
        // Scheduled on Jan 16th
        val scheduledTimeslot = createTimeslot(DayOfWeek.TUESDAY, LocalTime.of(9,0), LocalTime.of(10,0), date=LocalDate.of(2024,1,16))

        val eventPart = createEventPart(user1, event1, scheduledTimeslot,
                                      endDateString = originalEndDate, // EventPart's original endDate
                                      priority = 1, dailyTaskList = true, weeklyTaskList = false) // Satisfy filter conditions
        // Explicitly nullify other preferences to meet filter criteria for the constraint
        eventPart.preferredDayOfWeek = null
        eventPart.preferredTime = null
        eventPart.preferredEndTimeRange = null
        eventPart.preferredStartTimeRange = null
        eventPart.positiveImpactTime = null
        eventPart.positiveImpactDayOfWeek = null
        eventPart.negativeImpactTime = null
        eventPart.negativeImpactDayOfWeek = null

        constraintVerifier.verifyThat(TimeTableConstraintProvider::differentMonthDayConflictSoftPenalize)
            .given(eventPart)
            .penalizesBy(1)
    }

    @Test
    fun differentMonthDayConflictSoftPenalize_noConflict_sameMonthDay() {
        val user1 = createUser()
        val event1 = createEvent(user1.id!!)
        val originalEndDate = "2024-01-15T10:00:00" // Original end date is Jan 15th
        // Scheduled on Jan 15th
        val scheduledTimeslot = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(9,0), LocalTime.of(10,0), date=LocalDate.of(2024,1,15))

        val eventPart = createEventPart(user1, event1, scheduledTimeslot,
                                      endDateString = originalEndDate,
                                      priority = 1, dailyTaskList = true, weeklyTaskList = false)
        eventPart.preferredDayOfWeek = null; eventPart.preferredTime = null; eventPart.preferredEndTimeRange = null; eventPart.preferredStartTimeRange = null;
        eventPart.positiveImpactTime = null; eventPart.positiveImpactDayOfWeek = null; eventPart.negativeImpactTime = null; eventPart.negativeImpactDayOfWeek = null;

        constraintVerifier.verifyThat(TimeTableConstraintProvider::differentMonthDayConflictSoftPenalize)
            .given(eventPart)
            .rewardsWith(0)
    }

    @Test
    fun differentMonthDayConflictSoftPenalize_noConflict_filterNotMet() {
        val user1 = createUser()
        val event1 = createEvent(user1.id!!)
        val originalEndDate = "2024-01-15T10:00:00"
        val scheduledTimeslot = createTimeslot(DayOfWeek.TUESDAY, LocalTime.of(9,0), LocalTime.of(10,0), date=LocalDate.of(2024,1,16))

        val eventPart = createEventPart(user1, event1, scheduledTimeslot,
                                      endDateString = originalEndDate,
                                      priority = 1, dailyTaskList = true, weeklyTaskList = false)
        // A preference is set, so it won't pass all the null-preference filters
        eventPart.preferredDayOfWeek = DayOfWeek.MONDAY

        constraintVerifier.verifyThat(TimeTableConstraintProvider::differentMonthDayConflictSoftPenalize)
            .given(eventPart)
            .rewardsWith(0)
    }

    // --- Tests for notEqualStartDateForNonTaskSoftPenalize ---
    @Test
    fun notEqualStartDateForNonTaskSoftPenalize_conflict() {
        val user1 = createUser()
        val event1 = createEvent(user1.id!!)
        val originalStartDate = "2024-01-15T09:00:00" // Original start is Jan 15th, 09:00
        // Scheduled on Jan 15th, but at 10:00
        val scheduledTimeslot = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(10,0), LocalTime.of(11,0), date=LocalDate.of(2024,1,15))

        val eventPart = createEventPart(user1, event1, scheduledTimeslot,
                                      startDateString = originalStartDate, partNumber = 1)
        // Ensure it's a "non-task" and no other preferences are set to meet filter criteria
        eventPart.dailyTaskList = false; eventPart.weeklyTaskList = false; eventPart.gap = false; eventPart.forEventId = null;
        eventPart.preferredDayOfWeek = null; eventPart.preferredTime = null; eventPart.preferredEndTimeRange = null; eventPart.preferredStartTimeRange = null;
        eventPart.positiveImpactTime = null; eventPart.positiveImpactDayOfWeek = null; eventPart.negativeImpactTime = null; eventPart.negativeImpactDayOfWeek = null;

        constraintVerifier.verifyThat(TimeTableConstraintProvider::notEqualStartDateForNonTaskSoftPenalize)
            .given(eventPart)
            .penalizesBy(1)
    }

    @Test
    fun notEqualStartDateForNonTaskSoftPenalize_noConflict_sameStartDate() {
        val user1 = createUser()
        val event1 = createEvent(user1.id!!)
        val originalStartDate = "2024-01-15T09:00:00"
        // Scheduled on Jan 15th, 09:00 (same as original)
        val scheduledTimeslot = createTimeslot(DayOfWeek.MONDAY, LocalTime.of(9,0), LocalTime.of(10,0), date=LocalDate.of(2024,1,15))

        val eventPart = createEventPart(user1, event1, scheduledTimeslot,
                                      startDateString = originalStartDate, partNumber = 1)
        eventPart.dailyTaskList = false; eventPart.weeklyTaskList = false; eventPart.gap = false; eventPart.forEventId = null;
        eventPart.preferredDayOfWeek = null; eventPart.preferredTime = null; eventPart.preferredEndTimeRange = null; eventPart.preferredStartTimeRange = null;
        eventPart.positiveImpactTime = null; eventPart.positiveImpactDayOfWeek = null; eventPart.negativeImpactTime = null; eventPart.negativeImpactDayOfWeek = null;

        constraintVerifier.verifyThat(TimeTableConstraintProvider::notEqualStartDateForNonTaskSoftPenalize)
            .given(eventPart)
            .rewardsWith(0)
    }
}
