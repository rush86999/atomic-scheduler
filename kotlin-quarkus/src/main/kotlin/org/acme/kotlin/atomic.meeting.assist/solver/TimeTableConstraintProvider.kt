package org.acme.kotlin.atomic.meeting.assist.solver

import org.acme.kotlin.atomic.meeting.assist.domain.EventPart
import org.acme.kotlin.atomic.meeting.assist.domain.User
import org.optaplanner.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore
import org.optaplanner.core.api.score.stream.Constraint
import org.optaplanner.core.api.score.stream.ConstraintCollectors.countDistinct
import org.optaplanner.core.api.score.stream.ConstraintCollectors.sum
import org.optaplanner.core.api.score.stream.ConstraintFactory
import org.optaplanner.core.api.score.stream.ConstraintProvider
import org.optaplanner.core.api.score.stream.Joiners
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.MonthDay

class TimeTableConstraintProvider : ConstraintProvider {

    override fun defineConstraints(constraintFactory: ConstraintFactory): Array<Constraint> {
        return arrayOf(
                // Hard constraints
                userTimeSlotHardConflict(constraintFactory),
                meetingNotSameTimeSlotHardConflict(constraintFactory),
                outOfWorkTimesBoundaryFromStartTimeHardPenalize(constraintFactory),
                outOfWorkTimesBoundaryFromEndTimeHardPenalize(constraintFactory),
                sequentialEventPartsDisconnectedByTimeHardPenalize(constraintFactory),
                eventPartsDisconnectedByTimeHardPenalize(constraintFactory),
                firstAndLastPartDisconnectedByTimeHardPenalize(constraintFactory),
                eventPartsDisconnectedByMonthDayHardPenalize(constraintFactory),
                firstPartPushesLastPartOutHardPenalize(constraintFactory),
                partIsNotFirstForStartOfDayHardPenalize(constraintFactory),
                eventPartsReversedHardPenalize(constraintFactory),
                hardDeadlineConflictHardPenalize(constraintFactory),
                modifiableConflictHardPenalize(constraintFactory),
                notPreferredStartTimeOfTimeRangesHardPenalize(constraintFactory),
                notPreferredEndTimeOfTimeRangesHardPenalize(constraintFactory),
                meetingWithSameEventSlotHardConflict(constraintFactory),

                // Medium constraints
                higherPriorityEventsSoonerForTimeOfDayMediumPenalize(constraintFactory),
                positiveImpactTimeScoreMediumPenalize(constraintFactory),
                negativeImpactTimeScoreMediumPenalize(constraintFactory),
                notPreferredDayOfWeekMediumPenalize(constraintFactory),
                notPreferredStartTimeMediumPenalize(constraintFactory),
                notPreferredStartTimeOfTimeRangesMediumPenalize(constraintFactory),
                notPreferredEndTimeOfTimeRangesMediumPenalize(constraintFactory),
                notPreferredScheduleStartTimeRangeMediumPenalize(constraintFactory),
                notPreferredScheduleEndTimeRangeMediumPenalize(constraintFactory),
                externalMeetingModifiableConflictMediumPenalize(constraintFactory),
                meetingModifiableConflictMediumPenalize(constraintFactory),
                backToBackMeetingsPreferredMediumReward(constraintFactory),
                backToBackMeetingsNotPreferredMediumPenalize(constraintFactory),
                backToBackBreakConflictMediumPenalize(constraintFactory),
                maxNumberOfMeetingsConflictMediumPenalize(constraintFactory),

                // Soft constraints
                differentMonthDayConflictSoftPenalize(constraintFactory),
                notEqualStartDateForNonTaskSoftPenalize(constraintFactory),
                softDeadlineConflictSoftPenalize(constraintFactory),
                notPreferredStartTimeOfTimeRangesSoftPenalize(constraintFactory),
                notPreferredEndTimeOfTimeRangesSoftPenalize(constraintFactory),
                maxWorkloadConflictSoftPenalize(constraintFactory),
                minNumberOfBreaksConflictSoftPenalize(constraintFactory),
        )
    }

    // Helper for preferred start times
    private fun isOutsidePreferredStartTimeRanges(eventPart: EventPart): Boolean {
        // Ensure necessary fields are present to perform the check
        if (eventPart.event.preferredTimeRanges.isNullOrEmpty() ||
            eventPart.timeslot?.date == null || // Added null check for date
            eventPart.timeslot?.startTime == null ||
            eventPart.timeslot?.endTime == null || // endTime also needed for duration
            eventPart.timeslot?.dayOfWeek == null) {
            return false // Not enough info or no preferences, so not "outside"
        }
        // This constraint applies only to the first part of a multi-part event
        if (eventPart.part != 1) {
            return false
        }

        val timeslot = eventPart.timeslot!!
        val workTime = eventPart.user.workTimes.find { wt -> wt.dayOfWeek == timeslot.dayOfWeek }
            ?: return true // No work time for this day, so any timeslot is outside working hours


        // workTime.endTime is non-nullable (lateinit var endTime: LocalTime)
        // if (workTime.endTime == null) return true // This check is now redundant based on warnings

        val totalParts = eventPart.lastPart

        val partEndTimeForDurationCalc = timeslot.endTime ?: timeslot.startTime.plusMinutes(15)
        val partDuration = Duration.between(timeslot.startTime, partEndTimeForDurationCalc)
        val totalDuration = Duration.ofMinutes(totalParts * partDuration.toMinutes())

        val actualEventEndTime = timeslot.startTime.plus(totalDuration)
        if (actualEventEndTime > workTime.endTime) { // workTime.endTime is non-nullable
            return true // Event exceeds working hours
        }

        // Check against preferred time ranges
        val preferredTimeRanges = eventPart.event.preferredTimeRanges!!
        var countMatchingAtOrAfter = 0
        for (timeRange in preferredTimeRanges) {
            if (timeRange.startTime == null) continue

            if (timeRange.dayOfWeek == null || timeRange.dayOfWeek == timeslot.dayOfWeek) {
                if (timeslot.startTime >= timeRange.startTime) {
                    countMatchingAtOrAfter++
                }
            }
        }

        return countMatchingAtOrAfter == 0
    }

    // Helper for preferred end times
    private fun isOutsidePreferredEndTimeRanges(eventPart: EventPart): Boolean {
        if (eventPart.event.preferredTimeRanges.isNullOrEmpty() || eventPart.timeslot == null || eventPart.timeslot?.endTime == null || eventPart.timeslot?.dayOfWeek == null) {
            return false
        }
         if (eventPart.part != eventPart.lastPart) {
            return false
        }

        val timeslot = eventPart.timeslot!!


        // Calculate the actual end LocalDateTime of this event part (which is the last part)
        // val eventPartActualEndDateTime = LocalDateTime.of(timeslot.date!!, timeslot.endTime!!) // Not directly needed for comparison logic below

            if (timeRange.dayOfWeek == null || timeRange.dayOfWeek == timeslot.dayOfWeek) {
                if (timeslot.endTime <= timeRange.endTime) { // Removed !!
                    countMatchingAtOrBefore++
                }
            }
        }

        return countMatchingAtOrBefore == 0
    }


    fun userTimeSlotHardConflict(constraintFactory: ConstraintFactory): Constraint {

        return constraintFactory

                .forEachUniquePair(
                    EventPart::class.java,

                        Joiners.equal(EventPart::timeslot),

                        Joiners.equal(EventPart::userId))

                .penalize("User timeslot conflict hard penalize", HardMediumSoftScore.ONE_HARD)
    }

    fun meetingWithSameEventSlotHardConflict(constraintFactory: ConstraintFactory): Constraint {
        return constraintFactory
            .forEachUniquePair(
                EventPart::class.java,
                Joiners.equal(EventPart::timeslot),
            )
            .filter { eventPart1: EventPart, eventPart2: EventPart ->
                (((eventPart1.meetingId != null) && (eventPart2.meetingId == null))
                        || ((eventPart2.meetingId != null) && (eventPart1.meetingId == null)))
            }
            .penalize("Meetings should not be same timeslot as another event penalize hard", HardMediumSoftScore.ONE_HARD)
    }

    fun meetingNotSameTimeSlotHardConflict(constraintFactory: ConstraintFactory): Constraint {
        return constraintFactory
            .forEach(EventPart::class.java)
            .filter { eventPart: EventPart -> eventPart.meetingId != null }
            .join(
                EventPart::class.java,
                Joiners.equal(EventPart::meetingId),
                Joiners.equal(EventPart::meetingPart),
            )
            .filter { eventPart1: EventPart, eventPart2: EventPart ->
                (eventPart1.timeslot?.dayOfWeek != eventPart2.timeslot?.dayOfWeek)
                        || (eventPart1.timeslot?.endTime != eventPart2.timeslot?.endTime)
                        || (eventPart1.timeslot?.startTime != eventPart2.timeslot?.startTime)
                        || (eventPart1.timeslot?.monthDay != eventPart2.timeslot?.monthDay)
            }
            .penalize("Meetings should be same timeslot for each user penalize hard", HardMediumSoftScore.ONE_HARD)
    }

    fun outOfWorkTimesBoundaryFromStartTimeHardPenalize(constraintFactory: ConstraintFactory): Constraint {
        return constraintFactory
            .forEach(EventPart::class.java)
            .filter { eventPart: EventPart ->
                eventPart.timeslot?.dayOfWeek != null && eventPart.timeslot?.startTime != null
            }
            .filter { eventPart: EventPart ->
                val timeslot = eventPart.timeslot!!
                val workTime = eventPart.user.workTimes.find { it.dayOfWeek == timeslot.dayOfWeek }
                if (workTime == null || workTime.startTime == null) { // workTime.startTime is non-nullable
                    return@filter true
                }
                timeslot.startTime < workTime.startTime
            }
            .penalize("eventPart is out of bounds from start time hard penalize", HardMediumSoftScore.ONE_HARD)
    }

    fun outOfWorkTimesBoundaryFromEndTimeHardPenalize(constraintFactory: ConstraintFactory): Constraint {
        return constraintFactory
            .forEach(EventPart::class.java)
            .filter { eventPart: EventPart ->
                eventPart.timeslot?.dayOfWeek != null && eventPart.timeslot?.endTime != null
            }
            .filter { eventPart: EventPart ->
                val timeslot = eventPart.timeslot!!
                val workTime = eventPart.user.workTimes.find { it.dayOfWeek == timeslot.dayOfWeek }
                if (workTime == null || workTime.endTime == null) { // workTime.endTime is non-nullable
                    return@filter true
                }
                timeslot.endTime > workTime.endTime
            }
            .penalize("eventPart is out of bounds from end time hard penalize", HardMediumSoftScore.ONE_HARD)
    }

    fun sequentialEventPartsDisconnectedByTimeHardPenalize(constraintFactory: ConstraintFactory): Constraint {

        return constraintFactory
            .forEach(EventPart::class.java)
            .join(EventPart::class.java,
                Joiners.equal(EventPart::groupId),
                Joiners.lessThan(EventPart::part),
            )
            .filter { eventPart1: EventPart, eventPart2: EventPart ->
                val partDiff = eventPart2.part - eventPart1.part
                partDiff == 1
            }
            .filter { eventPart1: EventPart, eventPart2: EventPart ->

                val ts1 = eventPart1.timeslot
                val ts2 = eventPart2.timeslot
                if (ts1?.endTime == null || ts2?.startTime == null || ts1.dayOfWeek == null || ts2.dayOfWeek == null || ts1.monthDay == null || ts2.monthDay == null) {
                    return@filter true
                }

                val actualBetween = Duration.between(ts1.endTime, ts2.startTime)

                (actualBetween.isNegative ||
                        (ts1.dayOfWeek != ts2.dayOfWeek) ||
                        (ts1.monthDay != ts2.monthDay) ||
                        (actualBetween.toMinutes() > 0))
            }
            .penalize("Sequential Parts disconnected by time hard penalize", HardMediumSoftScore.ONE_HARD)

    }

    fun eventPartsDisconnectedByTimeHardPenalize(constraintFactory: ConstraintFactory): Constraint {

        return constraintFactory
            .forEach(EventPart::class.java)
            .join(EventPart::class.java,
                Joiners.equal(EventPart::groupId),
                Joiners.lessThan(EventPart::part),
            )
            .filter { eventPart1: EventPart, eventPart2: EventPart ->

                val ts1 = eventPart1.timeslot
                val ts2 = eventPart2.timeslot
                if (ts1?.startTime == null || ts1.endTime == null || ts2?.startTime == null ||
                    ts1.dayOfWeek == null || ts2.dayOfWeek == null || ts1.monthDay == null || ts2.monthDay == null) {
                    return@filter true
                }

                val actualBetween = Duration.between(ts1.startTime, ts2.startTime)
                val partDuration = Duration.between(ts1.startTime, ts1.endTime)
                val partDiff = eventPart2.part - eventPart1.part
                val expectedMinutes = partDiff * partDuration.toMinutes()

                (actualBetween.isNegative ||
                        (ts1.dayOfWeek != ts2.dayOfWeek) ||
                        (ts1.monthDay != ts2.monthDay) ||
                        (actualBetween.toMinutes() != expectedMinutes))
            }
            .penalize("Event parts disconnected by time hard penalize", HardMediumSoftScore.ONE_HARD)

    }

    fun firstAndLastPartDisconnectedByTimeHardPenalize(constraintFactory: ConstraintFactory): Constraint {

        return constraintFactory
            .forEach(EventPart::class.java)
            .join(EventPart::class.java,
                Joiners.equal(EventPart::groupId),
                Joiners.lessThan(EventPart::part),
            )
            .filter { eventPart1: EventPart, eventPart2: EventPart ->
                (eventPart1.part == 1) && (eventPart2.part == eventPart2.lastPart)
            }
            .filter { eventPart1: EventPart, eventPart2: EventPart ->

                val ts1 = eventPart1.timeslot
                val ts2 = eventPart2.timeslot
                if (ts1?.startTime == null || ts1.endTime == null || ts2?.endTime == null ||
                    ts1.dayOfWeek == null || ts2.dayOfWeek == null || ts1.monthDay == null || ts2.monthDay == null) {
                    return@filter true
                }

                val between = Duration.between(ts1.startTime, ts2.endTime)
                val partDuration = Duration.between(ts1.startTime, ts1.endTime)

                val totalNumberOfPartsInGroup = eventPart1.lastPart
                val expectedTotalDurationMinutes = totalNumberOfPartsInGroup * partDuration.toMinutes()


                (between.isNegative ||
                        (ts1.dayOfWeek != ts2.dayOfWeek) ||
                        (ts1.monthDay != ts2.monthDay) ||
                        (kotlin.math.abs(between.toMinutes() - expectedTotalDurationMinutes) > 0))
            }
            .penalize("First and last part disconnected by time for more than 0 min hard penalize", HardMediumSoftScore.ONE_HARD)

    }

    fun eventPartsDisconnectedByMonthDayHardPenalize(constraintFactory: ConstraintFactory): Constraint {

        return constraintFactory
            .forEach(EventPart::class.java)
            .join(EventPart::class.java,
                Joiners.equal(EventPart::groupId),
                Joiners.lessThan(EventPart::part),
            )
            .filter { eventPart1: EventPart, eventPart2: EventPart ->

                val ts1 = eventPart1.timeslot
                val ts2 = eventPart2.timeslot
                if (ts1?.monthDay == null || ts2?.monthDay == null) {

                    return@filter ts1?.monthDay != ts2?.monthDay
                }
                ts1.monthDay != ts2.monthDay
            }
            .penalize("Event parts on different monthDays hard penalize", HardMediumSoftScore.ONE_HARD)

    }

    fun firstPartPushesLastPartOutHardPenalize(constraintFactory: ConstraintFactory): Constraint {

        return constraintFactory
            .forEach(EventPart::class.java)
            .filter { eventPart: EventPart ->
                eventPart.part == 1 &&
                eventPart.timeslot?.dayOfWeek != null &&
                eventPart.timeslot?.startTime != null &&
                eventPart.timeslot?.endTime != null
            }
            .filter { eventPart: EventPart ->
                val timeslot = eventPart.timeslot!!

                val totalParts = eventPart.lastPart
                val partDuration = Duration.between(timeslot.startTime, timeslot.endTime)

                val totalMinutes = totalParts * partDuration.toMinutes()
                val totalDuration = Duration.ofMinutes(totalMinutes)

                val workTime = eventPart.user.workTimes.find { wt -> wt.dayOfWeek == timeslot.dayOfWeek }

                // workTime.endTime is non-nullable
                if (workTime == null /*|| workTime.endTime == null*/) {
                    return@filter true
                }

                val possibleEndTime = timeslot.startTime.plus(totalDuration) // Removed !!
                possibleEndTime > workTime.endTime
            }
            .penalize("First part pushes last part out of bounds hard penalize", HardMediumSoftScore.ONE_HARD)

    }

    fun partIsNotFirstForStartOfDayHardPenalize(constraintFactory: ConstraintFactory): Constraint {

        return constraintFactory
            .forEach(EventPart::class.java)
            .filter { eventPart: EventPart ->
                eventPart.timeslot?.dayOfWeek != null && eventPart.timeslot?.startTime != null
            }
            .filter { eventPart: EventPart ->
                val timeslot = eventPart.timeslot!!
                val workTime = eventPart.user.workTimes.find { wt -> wt.dayOfWeek == timeslot.dayOfWeek }

                // workTime.startTime is non-nullable
                if (workTime == null /*|| workTime.startTime == null*/) {

                    return@filter false
                }
                timeslot.startTime == workTime.startTime
            }
            .filter { eventPart: EventPart -> eventPart.part != 1}
            .penalize("Part is not first for start of day hard penalize", HardMediumSoftScore.ONE_HARD)

    }

    fun eventPartsReversedHardPenalize(constraintFactory: ConstraintFactory): Constraint {

        return constraintFactory
            .forEach(EventPart::class.java)
            .join(EventPart::class.java,
                Joiners.equal(EventPart::groupId),
                Joiners.lessThan((EventPart::part)),
            )
            .filter { eventPart1: EventPart, eventPart2: EventPart ->
                val between = Duration.between(eventPart1.timeslot?.endTime,
                    eventPart2.timeslot?.startTime)

                between.isNegative
            }
            .penalize("Event Parts connected in reverse hard penalize", HardMediumSoftScore.ONE_HARD)

    }

    fun hardDeadlineConflictHardPenalize(constraintFactory: ConstraintFactory): Constraint {
        return constraintFactory
            .forEach(EventPart::class.java)
            .filter { eventPart: EventPart ->
                eventPart.hardDeadline != null && eventPart.timeslot != null &&
                eventPart.timeslot?.endTime != null && eventPart.timeslot?.date != null // Assuming timeslot.date exists
            }
            .filter { eventPart: EventPart ->

                val deadlineStr = eventPart.hardDeadline!!

                val deadlineDate = LocalDate.parse(deadlineStr.substring(0, 10))
                val deadlineTime = LocalTime.parse(deadlineStr.substring(11, 19))
                val deadlineMonthDay = MonthDay.from(deadlineDate)

                val timeslot = eventPart.timeslot!!


                if (timeslot.dayOfWeek == null || timeslot.monthDay == null || timeslot.endTime == null) {
                    return@filter false
                }

                (timeslot.dayOfWeek > deadlineDate.dayOfWeek) ||
                        (timeslot.monthDay > deadlineMonthDay) ||
                        ((timeslot.monthDay == deadlineMonthDay) &&
                         (timeslot.dayOfWeek == deadlineDate.dayOfWeek) &&
                         (timeslot.endTime > deadlineTime))
            }
            .penalize("missed hard Deadline hard penalize", HardMediumSoftScore.ONE_HARD)
    }

    fun modifiableConflictHardPenalize(constraintFactory: ConstraintFactory): Constraint {
        return constraintFactory
            .forEach(EventPart::class.java)
            .filter { eventPart: EventPart ->
                !eventPart.modifiable &&
                eventPart.part == 1 &&
                eventPart.startDate != null &&
                eventPart.timeslot != null
            }
            .filter { eventPart: EventPart ->

                val startDateStr = eventPart.startDate!!
                val originalStartDate = LocalDate.parse(startDateStr.substring(0, 10))
                val originalStartTime = LocalTime.parse(startDateStr.substring(11, 19))
                val originalMonthDay = MonthDay.from(originalStartDate)

                val timeslot = eventPart.timeslot!!


                if (timeslot.startTime == null || timeslot.dayOfWeek == null || timeslot.monthDay == null) {
                    return@filter true
                }

                (originalStartTime != timeslot.startTime) ||
                        (originalStartDate.dayOfWeek != timeslot.dayOfWeek) ||
                        (originalMonthDay != timeslot.monthDay)
            }
            .penalize("event is not modifiable but time or day changed hard penalize", HardMediumSoftScore.ONE_HARD)
    }

    fun higherPriorityEventsSoonerForTimeOfDayMediumPenalize(constraintFactory: ConstraintFactory): Constraint {

        return constraintFactory
            .forEach(EventPart::class.java)
            .join(EventPart::class.java,
                Joiners.greaterThan(EventPart::priority),
                Joiners.equal(EventPart::userId),
            )
            .filter { eventPart1: EventPart, eventPart2: EventPart ->
                (eventPart1.timeslot!!.monthDay > eventPart2.timeslot!!.monthDay)
                        || (eventPart1.timeslot!!.dayOfWeek > eventPart2.timeslot!!.dayOfWeek)
                        || ((eventPart1.timeslot!!.dayOfWeek == eventPart2.timeslot!!.dayOfWeek)
                            && (eventPart1.timeslot!!.monthDay == eventPart2.timeslot!!.monthDay)
                            && (eventPart1.timeslot!!.startTime > eventPart2.timeslot!!.startTime))

            }
            .penalize("Priority comes first for time of day medium penalize", HardMediumSoftScore.ONE_MEDIUM)
    }

    fun differentMonthDayConflictSoftPenalize(constraintFactory: ConstraintFactory): Constraint {
        return constraintFactory
            .forEach(EventPart::class.java)
            .filter { eventPart: EventPart -> eventPart.preferredDayOfWeek == null }
            .filter { eventPart: EventPart -> eventPart.preferredTime == null }
            .filter { eventPart: EventPart -> eventPart.preferredEndTimeRange == null }
            .filter { eventPart: EventPart -> eventPart.preferredStartTimeRange == null }
            .filter { eventPart: EventPart -> eventPart.priority == 1 }
            .filter { eventPart: EventPart -> eventPart.positiveImpactTime == null }
            .filter { eventPart: EventPart -> eventPart.positiveImpactDayOfWeek == null }
            .filter { eventPart: EventPart -> eventPart.negativeImpactTime == null }
            .filter { eventPart: EventPart -> eventPart.negativeImpactDayOfWeek == null }
            .filter { eventPart: EventPart ->
                eventPart.endDate != null && eventPart.timeslot != null && eventPart.timeslot?.monthDay != null
            }
            .filter { eventPart: EventPart ->

                val endDateStr = eventPart.endDate!!

                val endMonthDay = MonthDay.parse("--${endDateStr.substring(5, 10)}")

                (eventPart.dailyTaskList || (!eventPart.weeklyTaskList)) &&
                        (endMonthDay != eventPart.timeslot!!.monthDay)
            }
            .penalize("end date monthDay is different from timeslot monthDay soft penalize", HardMediumSoftScore.ONE_SOFT)
    }

    fun notEqualStartDateForNonTaskSoftPenalize(constraintFactory: ConstraintFactory): Constraint {

        return constraintFactory
            .forEach(EventPart::class.java)
            .filter { eventPart: EventPart -> !(eventPart.dailyTaskList) }
            .filter { eventPart: EventPart -> !(eventPart.weeklyTaskList) }
            .filter { eventPart: EventPart -> !(eventPart.gap) }
            .filter { eventPart: EventPart -> eventPart.forEventId == null }
            .filter { eventPart: EventPart -> eventPart.preferredDayOfWeek == null }
            .filter { eventPart: EventPart -> eventPart.preferredTime == null }
            .filter { eventPart: EventPart -> eventPart.preferredEndTimeRange == null }
            .filter { eventPart: EventPart -> eventPart.preferredStartTimeRange == null }
            .filter { eventPart: EventPart -> eventPart.positiveImpactTime == null }
            .filter { eventPart: EventPart -> eventPart.positiveImpactDayOfWeek == null }
            .filter { eventPart: EventPart -> eventPart.negativeImpactTime == null }
            .filter { eventPart: EventPart -> eventPart.negativeImpactDayOfWeek == null }
            .filter { eventPart: EventPart ->
                eventPart.part == 1 &&
                eventPart.startDate != null &&
                eventPart.timeslot != null
            }
            .filter { eventPart: EventPart ->

                val startDateStr = eventPart.startDate!!
                val originalStartDate = LocalDate.parse(startDateStr.substring(0, 10))
                val originalStartTime = LocalTime.parse(startDateStr.substring(11, 19))
                val originalMonthDay = MonthDay.from(originalStartDate)

                val timeslot = eventPart.timeslot!!


                if (timeslot.dayOfWeek == null || timeslot.startTime == null || timeslot.monthDay == null) {
                    return@filter true
                }

                (timeslot.dayOfWeek != originalStartDate.dayOfWeek) ||
                        (timeslot.startTime != originalStartTime) ||
                        (timeslot.monthDay != originalMonthDay)
            }
            .penalize("not equal start date for a non task soft penalize", HardMediumSoftScore.ONE_SOFT)
    }

    fun softDeadlineConflictSoftPenalize(constraintFactory: ConstraintFactory): Constraint {
        return constraintFactory
            .forEach(EventPart::class.java)
            .filter { eventPart: EventPart ->
                eventPart.softDeadline != null && eventPart.timeslot != null &&
                eventPart.timeslot?.endTime != null && eventPart.timeslot?.date != null // Assuming timeslot.date exists
            }
            .filter { eventPart: EventPart ->

                val deadlineStr = eventPart.softDeadline!!
                val deadlineDate = LocalDate.parse(deadlineStr.substring(0, 10))
                val deadlineTime = LocalTime.parse(deadlineStr.substring(11, 19))
                val deadlineMonthDay = MonthDay.from(deadlineDate)

                val timeslot = eventPart.timeslot!!


                if (timeslot.dayOfWeek == null || timeslot.monthDay == null || timeslot.endTime == null) {
                    return@filter false
                }

                (timeslot.dayOfWeek > deadlineDate.dayOfWeek) ||
                        (timeslot.monthDay > deadlineMonthDay) ||
                        ((timeslot.monthDay == deadlineMonthDay) &&
                         (timeslot.dayOfWeek == deadlineDate.dayOfWeek) &&
                         (timeslot.endTime > deadlineTime))
            }
            .penalize("missed soft Deadline soft penalize", HardMediumSoftScore.ONE_SOFT)
    }

    fun positiveImpactTimeScoreMediumPenalize(constraintFactory: ConstraintFactory): Constraint {


        return constraintFactory
            .forEach(EventPart::class.java)
            .filter { eventPart: EventPart ->
                eventPart.positiveImpactScore > 0
            }
            .filter { eventPart: EventPart -> eventPart.part == 1 }
            .filter { eventPart: EventPart ->
                val totalParts = eventPart.lastPart
                val timeslot = eventPart.timeslot!!
                val partDuration = Duration.between(timeslot.startTime, timeslot.endTime)
                val totalMinutes = totalParts * partDuration.toMinutes()
                val totalDuration = Duration.ofMinutes(totalMinutes)
                val workTime = eventPart.user.workTimes.find { wt -> wt.dayOfWeek == timeslot.dayOfWeek }

                // workTime.endTime is non-nullable
                if (workTime == null /*|| workTime.endTime == null*/) {
                    return@filter true
                }
                if (eventPart.positiveImpactDayOfWeek == null || eventPart.positiveImpactTime == null) {
                    return@filter false
                }

                val possibleEndTime = timeslot.startTime.plus(totalDuration) // Removed !!



                // Penalize if not on the positive impact day OR not at the positive impact time
                (eventPart.positiveImpactDayOfWeek!! != timeslot.dayOfWeek) ||
                (eventPart.positiveImpactTime!! != timeslot.startTime!!)
            }
            .penalize("positive impact time score based slot preference not provided medium penalize", HardMediumSoftScore.ONE_MEDIUM)
    }

    fun negativeImpactTimeScoreMediumPenalize(constraintFactory: ConstraintFactory): Constraint {
        return constraintFactory
            .forEach(EventPart::class.java)
            .filter { eventPart: EventPart -> eventPart.negativeImpactScore > 0 }
            .filter { eventPart: EventPart -> eventPart.part == 1}
            .filter { eventPart: EventPart ->
                (eventPart.negativeImpactDayOfWeek == eventPart.timeslot?.dayOfWeek)
                        && (eventPart.negativeImpactTime == eventPart.timeslot?.startTime)

            }
            .penalize("negative impact time score related slot selected medium penalize", HardMediumSoftScore.ONE_MEDIUM)
    }

    fun notPreferredDayOfWeekMediumPenalize(constraintFactory: ConstraintFactory): Constraint {
        return constraintFactory
            .forEach(EventPart::class.java)
            .filter { eventPart: EventPart -> eventPart.preferredDayOfWeek != null }
            .filter { eventPart: EventPart ->
                (eventPart.preferredDayOfWeek!! < eventPart.timeslot?.dayOfWeek) || (eventPart.preferredDayOfWeek!! > eventPart.timeslot?.dayOfWeek)
            }
            .penalize("preferred dayOfWeek not given medium penalize", HardMediumSoftScore.ONE_MEDIUM)
    }

    fun notPreferredStartTimeMediumPenalize(constraintFactory: ConstraintFactory): Constraint {
        return constraintFactory
            .forEach(EventPart::class.java)
            .filter { eventPart: EventPart -> eventPart.preferredTime != null }
            .filter { eventPart: EventPart -> eventPart.part == 1 }
            .filter { eventPart: EventPart ->
                val totalParts = eventPart.lastPart
                val timeslot = eventPart.timeslot!!
                val partDuration = Duration.between(timeslot.startTime, timeslot.endTime)
                val totalMinutes = totalParts * partDuration.toMinutes()
                val totalDuration = Duration.ofMinutes(totalMinutes)
                val workTime = eventPart.user.workTimes.find { wt -> wt.dayOfWeek == timeslot.dayOfWeek }
                // If workTime or endTime is null, or preferredTime is null (already filtered by previous .filter { eventPart: EventPart -> eventPart.preferredTime != null }),
                // it's hard to apply this rule.
                if (workTime?.endTime == null) {
                    return@filter false // Cannot determine if it's within working hours, so don't apply this penalty.
                                        // Other constraints might penalize for being outside work hours.
                }
                val workEndDateTime = LocalDateTime.of(timeslot.date!!, workTime.endTime!!)

                val isWithinWorkTime = eventOverallEndDateTime <= workEndDateTime

                if (workTime == null || workTime.endTime == null || eventPart.preferredTime == null) {

                    return@filter true
                }
                val possibleEndTime = timeslot.startTime.plus(totalDuration) // Removed !!

                (possibleEndTime < workTime.endTime)
            }
            .filter { eventPart: EventPart ->
                val timeslot = eventPart.timeslot!!
                ((eventPart.preferredTime!! < timeslot.startTime) || (eventPart.preferredTime!! > timeslot.startTime))
            }
            .penalize("preferred startTime not given medium penalize", HardMediumSoftScore.ONE_MEDIUM)
    }

    fun notPreferredStartTimeOfTimeRangesHardPenalize(constraintFactory: ConstraintFactory): Constraint {
        return constraintFactory
            .forEach(EventPart::class.java)
            .filter(this::isOutsidePreferredStartTimeRanges)
            .penalize("Timeslot not in preferred start time of preferredTimeRanges hard penalize", HardMediumSoftScore.ONE_HARD)
    }

    fun notPreferredEndTimeOfTimeRangesHardPenalize(constraintFactory: ConstraintFactory): Constraint {
        return constraintFactory
            .forEach(EventPart::class.java)
            .filter(this::isOutsidePreferredEndTimeRanges)
            .penalize("Timeslot not in preferred end time of preferredTimeRanges hard penalize", HardMediumSoftScore.ONE_HARD)
    }

    fun notPreferredStartTimeOfTimeRangesMediumPenalize(constraintFactory: ConstraintFactory): Constraint {
        return constraintFactory
            .forEach(EventPart::class.java)
            .filter(this::isOutsidePreferredStartTimeRanges)
            .penalize("Timeslot not in preferred start time of preferredTimeRanges medium penalize", HardMediumSoftScore.ONE_MEDIUM)
    }

    fun notPreferredEndTimeOfTimeRangesMediumPenalize(constraintFactory: ConstraintFactory): Constraint {
        return constraintFactory
            .forEach(EventPart::class.java)
            .filter(this::isOutsidePreferredEndTimeRanges)
            .penalize("Timeslot not in preferred end time of preferredTimeRanges medium penalize", HardMediumSoftScore.ONE_MEDIUM)
    }

    fun notPreferredStartTimeOfTimeRangesSoftPenalize(constraintFactory: ConstraintFactory): Constraint {
        return constraintFactory
            .forEach(EventPart::class.java)
            .filter(this::isOutsidePreferredStartTimeRanges)
            .penalize("Timeslot not in preferred start time of preferredTimeRanges soft penalize", HardMediumSoftScore.ONE_SOFT)
    }

    fun notPreferredEndTimeOfTimeRangesSoftPenalize(constraintFactory: ConstraintFactory): Constraint {
        return constraintFactory
            .forEach(EventPart::class.java)
            .filter(this::isOutsidePreferredEndTimeRanges)
            .penalize("Timeslot not in preferred end time of preferredTimeRanges soft penalize", HardMediumSoftScore.ONE_SOFT)
    }

    fun notPreferredScheduleStartTimeRangeMediumPenalize(constraintFactory: ConstraintFactory): Constraint {
        return constraintFactory
            .forEach(EventPart::class.java)
            .filter { eventPart: EventPart -> (eventPart.preferredStartTimeRange != null) && (eventPart.preferredEndTimeRange != null) }
            .filter { eventPart: EventPart -> eventPart.part == 1 }
            .filter { eventPart: EventPart ->
                val totalParts = eventPart.lastPart
                val partDuration = Duration.between(eventPart.timeslot?.startTime ?: LocalTime.parse("00:00"), eventPart.timeslot?.endTime ?: LocalTime.parse("00:15"))
                val totalMinutes = totalParts * partDuration.toMinutes()
                val totalDuration = Duration.ofMinutes(totalMinutes)
                val timeslot = eventPart.timeslot!!
                val workTime = eventPart.user.workTimes.find { wt -> wt.dayOfWeek == timeslot.dayOfWeek }

                if (workTime == null || workTime.endTime == null || eventPart.preferredStartTimeRange == null) {
                    return@filter true
                }
                val possibleEndTime = timeslot.startTime.plus(totalDuration) // Removed !!

                (timeslot.startTime < eventPart.preferredStartTimeRange!!) ||
                        (possibleEndTime > workTime.endTime)
            }
            .penalize("timeslot not in preferred start time range medium penalize", HardMediumSoftScore.ONE_MEDIUM)
    }

    fun notPreferredScheduleEndTimeRangeMediumPenalize(constraintFactory: ConstraintFactory): Constraint {
        return constraintFactory
            .forEach(EventPart::class.java)
            .filter { eventPart: EventPart -> (eventPart.preferredStartTimeRange != null) && (eventPart.preferredEndTimeRange != null) }
            .filter { eventPart: EventPart -> eventPart.part == eventPart.lastPart }
            .filter { eventPart: EventPart ->
                eventPart.timeslot!!.endTime > eventPart.preferredEndTimeRange!!
            }
            .penalize("timeslot not in preferred end time range medium penalize", HardMediumSoftScore.ONE_MEDIUM)
    }

    fun externalMeetingModifiableConflictMediumPenalize(constraintFactory: ConstraintFactory): Constraint {
        return constraintFactory
            .forEach(EventPart::class.java)
            .filter { eventPart: EventPart ->
                eventPart.isExternalMeeting &&
                !eventPart.isExternalMeetingModifiable &&
                eventPart.part == 1 &&
                eventPart.startDate != null &&
                eventPart.timeslot != null
            }
            .filter { eventPart: EventPart ->
                val startDateStr = eventPart.startDate!!
                val originalStartDate = LocalDate.parse(startDateStr.substring(0, 10))
                val originalStartTime = LocalTime.parse(startDateStr.substring(11, 19))
                val originalMonthDay = MonthDay.from(originalStartDate)

                val timeslot = eventPart.timeslot!!


                if (timeslot.startTime == null || timeslot.dayOfWeek == null || timeslot.monthDay == null) {
                    return@filter true
                }

                (originalStartTime != timeslot.startTime) ||
                        (originalStartDate.dayOfWeek != timeslot.dayOfWeek) ||
                        (originalMonthDay != timeslot.monthDay)
            }
            .penalize("event is an external meeting and not modifiable but time or day was changed medium penalize", HardMediumSoftScore.ONE_MEDIUM)
    }

    fun meetingModifiableConflictMediumPenalize(constraintFactory: ConstraintFactory): Constraint {
        return constraintFactory
            .forEach(EventPart::class.java)
            .filter { eventPart: EventPart ->
                !eventPart.isMeetingModifiable &&
                eventPart.isMeeting &&
                eventPart.part == 1 &&
                eventPart.startDate != null &&
                eventPart.timeslot != null
            }
            .filter { eventPart: EventPart ->
                val startDateStr = eventPart.startDate!!
                val originalStartDate = LocalDate.parse(startDateStr.substring(0, 10))
                val originalStartTime = LocalTime.parse(startDateStr.substring(11, 19))
                val originalMonthDay = MonthDay.from(originalStartDate)

                val timeslot = eventPart.timeslot!!


                if (timeslot.startTime == null || timeslot.dayOfWeek == null || timeslot.monthDay == null) {
                    return@filter true
                }

                (originalStartTime != timeslot.startTime) ||
                        (originalStartDate.dayOfWeek != timeslot.dayOfWeek) ||
                        (originalMonthDay != timeslot.monthDay)
            }
            .penalize("event is a meeting and not modifiable but time or day was changed medium penalize", HardMediumSoftScore.ONE_MEDIUM)
    }

    fun maxWorkloadConflictSoftPenalize(constraintFactory: ConstraintFactory): Constraint {
        return constraintFactory
            .forEach(EventPart::class.java)
            .filter { eventPart: EventPart -> !eventPart.gap }
            .filter { eventPart: EventPart -> eventPart.part == 1}
            .groupBy(

                { eventPart: EventPart -> eventPart.user },
                { eventPart: EventPart -> eventPart.timeslot?.monthDay },
                { eventPart: EventPart -> eventPart.totalWorkingHours },
                sum { eventPart: EventPart ->

                    try {
                        if (eventPart.startDate != null && eventPart.endDate != null &&
                            eventPart.startDate.length >= 19 && eventPart.endDate.length >= 19) {
                            val startTime = LocalTime.parse(eventPart.startDate.substring(11, 19))
                            val endTime = LocalTime.parse(eventPart.endDate.substring(11, 19))
                            Duration.between(startTime, endTime).toMinutes().toInt()
                        } else {
                            0
                        }
                    } catch (e: Exception) {
                        0
                    }
                }
            )
            .filter { user: User, _: MonthDay?, dailyTotalWorkingHours: Int, calculatedEventWorkloadMinutes: Int ->
                if (user.maxWorkLoadPercent <= 0) { // Removed !! from user.maxWorkLoadPercent
                    return@filter false
                }
                if (dailyTotalWorkingHours <= 0) {

                    return@filter calculatedEventWorkloadMinutes > 0
                }

                val calculatedWorkloadPercentage = (calculatedEventWorkloadMinutes.toDouble() * 100) / (dailyTotalWorkingHours * 60)
                calculatedWorkloadPercentage > user.maxWorkLoadPercent // Removed !! from user.maxWorkLoadPercent
            }
            .penalize("Exceeded max workload soft penalize", HardMediumSoftScore.ONE_SOFT)
    }

    fun backToBackMeetingsPreferredMediumReward(constraintFactory: ConstraintFactory): Constraint {
        return constraintFactory
            .forEach(EventPart::class.java)
            .filter { eventPart: EventPart -> eventPart.user.backToBackMeetings == true }
            .filter { eventPart: EventPart -> (eventPart.isMeeting || eventPart.meetingId != null) }
            .join(EventPart::class.java,
                Joiners.equal { eventPart: EventPart -> eventPart.timeslot?.monthDay },
                Joiners.equal(EventPart::userId)
            )
            .filter { eventPart1: EventPart, eventPart2: EventPart ->
                val between = Duration.between(eventPart1.timeslot?.endTime,
                    eventPart2.timeslot?.startTime)

                (eventPart1.part == eventPart1.lastPart)
                        && (eventPart2.part == 1)
                        && (eventPart1.groupId != eventPart2.groupId)
                        && !between.isNegative && (between.toMinutes() < 15)
            }
            .reward("user prefers back to back meetings medium reward", HardMediumSoftScore.ONE_MEDIUM)
    }

    fun backToBackMeetingsNotPreferredMediumPenalize(constraintFactory: ConstraintFactory): Constraint {
        return constraintFactory
            .forEach(EventPart::class.java)
            .filter { eventPart: EventPart -> !eventPart.user.backToBackMeetings }
            .filter { eventPart: EventPart -> eventPart.isMeeting }
            .join(EventPart::class.java,
                Joiners.equal { eventPart: EventPart -> eventPart.timeslot?.monthDay },
                Joiners.equal(EventPart::userId)
            )
            .filter { eventPart1: EventPart, eventPart2: EventPart ->
                val between = Duration.between(eventPart1.timeslot?.endTime,
                    eventPart2.timeslot?.startTime)

                (eventPart1.part == eventPart1.lastPart)
                        && (eventPart2.part == 1)
                        && (eventPart1.groupId != eventPart2.groupId)
                        && !between.isNegative && between.toMinutes() < 30
            }
            .penalize("user does not prefer back to back meetings medium penalize", HardMediumSoftScore.ONE_MEDIUM)
    }

    fun backToBackBreakConflictMediumPenalize(constraintFactory: ConstraintFactory): Constraint {
        return constraintFactory
            .forEach(EventPart::class.java)
            .filter { eventPart: EventPart -> eventPart.gap }
            .join(EventPart::class.java,
                Joiners.equal { eventPart: EventPart -> eventPart.timeslot?.monthDay },
                Joiners.equal(EventPart::userId)
            )
            .filter { eventPart1: EventPart, eventPart2: EventPart ->
                val between = Duration.between(eventPart1.timeslot?.endTime,
                    eventPart2.timeslot?.startTime)

                (eventPart1.part == eventPart1.lastPart)
                        && (eventPart2.part == 1)
                        && (eventPart1.groupId != eventPart2.groupId)
                        && !between.isNegative && between.toMinutes() < 30
            }
            .penalize("back to back breaks are not preferred medium penalize", HardMediumSoftScore.ONE_MEDIUM)
    }

    fun maxNumberOfMeetingsConflictMediumPenalize(constraintFactory: ConstraintFactory): Constraint {
        return constraintFactory
            .forEach(EventPart::class.java)
            .filter(EventPart::isMeeting)
            .filter { eventPart: EventPart -> eventPart.part == 1}
            .groupBy(
                { eventPart: EventPart -> eventPart.timeslot?.monthDay },
                EventPart::user,
                countDistinct(EventPart::groupId)
            )
            .filter { _, user: User, count: Int ->
                count > user.maxNumberOfMeetings
            }
            .penalize(" max meetings for the day reached medium penalize", HardMediumSoftScore.ONE_MEDIUM)
    }

    fun minNumberOfBreaksConflictSoftPenalize(constraintFactory: ConstraintFactory): Constraint {
        return constraintFactory
            .forEach(EventPart::class.java)
            .filter(EventPart::gap)
            .filter { eventPart: EventPart -> eventPart.part == 1}
            .groupBy(
                { eventPart: EventPart -> eventPart.timeslot?.monthDay },
                EventPart::user,
                countDistinct(EventPart::groupId)
            )
            .filter { _, user: User, count: Int ->
                (count < user.minNumberOfBreaks) || (count > user.minNumberOfBreaks + 2)
            }
            .penalize("min number of breaks for the day are required penalize", HardMediumSoftScore.ONE_SOFT)
    }

}
