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
        if (eventPart.event.preferredTimeRanges.isNullOrEmpty() || eventPart.timeslot == null || eventPart.timeslot?.startTime == null || eventPart.timeslot?.dayOfWeek == null) {
            return false // No preferences to violate or not enough info to check, so not "outside"
        }
        if (eventPart.part != 1) { // Only check for the first part of an event
            return false
        }

        val timeslot = eventPart.timeslot!!
        val workTime = eventPart.user.workTimes.find { wt -> wt.dayOfWeek == timeslot.dayOfWeek }
            ?: return true // No work time defined for the day, so it's effectively outside any valid range for work

        if (workTime.endTime == null) return true // Work end time not defined, cannot ensure event fits

        val totalParts = eventPart.lastPart
        // Use a default duration if endTime is somehow null, though timeslot properties should be set
        val partEndTimeForDurationCalc = timeslot.endTime ?: timeslot.startTime!!.plusMinutes(15) // Default 15 min if endTime null
        val partDuration = Duration.between(timeslot.startTime, partEndTimeForDurationCalc)
        val totalDuration = Duration.ofMinutes(totalParts * partDuration.toMinutes())

        val actualEventEndTime = timeslot.startTime!!.plus(totalDuration)
        if (actualEventEndTime > workTime.endTime) {
            return true // Event exceeds working hours
        }

        val preferredTimeRanges = eventPart.event.preferredTimeRanges!!
        var countMatchingAtOrAfter = 0
        for (timeRange in preferredTimeRanges) {
            if (timeRange.startTime == null) continue

            if (timeRange.dayOfWeek == null || timeRange.dayOfWeek == timeslot.dayOfWeek) { // Time range applies to this day
                if (timeslot.startTime!! >= timeRange.startTime) { // Event starts at or after preferred start
                    countMatchingAtOrAfter++
                }
            }
        }
        // If no preferred time range is met (event starts before all of them for the correct day), it's outside.
        return countMatchingAtOrAfter == 0
    }

    // Helper for preferred end times
    private fun isOutsidePreferredEndTimeRanges(eventPart: EventPart): Boolean {
        if (eventPart.event.preferredTimeRanges.isNullOrEmpty() || eventPart.timeslot == null || eventPart.timeslot?.endTime == null || eventPart.timeslot?.dayOfWeek == null) {
            return false // No preferences to violate or not enough info, so not "outside"
        }
         if (eventPart.part != eventPart.lastPart) { // Only check for the last part of an event
            return false
        }

        val timeslot = eventPart.timeslot!!
        // No direct workTime check here as it's about preferred end, not work boundaries,
        // but work boundaries are inherently checked by solver placing the event part.

        val preferredTimeRanges = eventPart.event.preferredTimeRanges!!
        var countMatchingAtOrBefore = 0
        for (timeRange in preferredTimeRanges) {
            if (timeRange.endTime == null) continue

            if (timeRange.dayOfWeek == null || timeRange.dayOfWeek == timeslot.dayOfWeek) { // Time range applies to this day
                if (timeslot.endTime!! <= timeRange.endTime) { // Event ends at or before preferred end
                    countMatchingAtOrBefore++
                }
            }
        }
        // If no preferred time range is met (event ends after all of them for the correct day), it's outside.
        return countMatchingAtOrBefore == 0
    }


    fun userTimeSlotHardConflict(constraintFactory: ConstraintFactory): Constraint {
        // A user can accommodate at most one event at the same time.
        return constraintFactory
                // Select each pair of 2 different lessons ...
                .forEachUniquePair(
                    EventPart::class.java,
                        // ... in the same timeslot ...
                        Joiners.equal(EventPart::timeslot),
                        // ... in the same user slot ...
                        Joiners.equal(EventPart::userId))
                // ... and penalize each pair with a hard weight.
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
                val timeslot = eventPart.timeslot!! // Already checked not null
                val workTime = eventPart.user.workTimes.find { it.dayOfWeek == timeslot.dayOfWeek }
                if (workTime == null || workTime.startTime == null) {
                    return@filter true // Penalize if no work time defined for the day or startTime is null
                }
                timeslot.startTime!! < workTime.startTime // timeslot.startTime checked
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
                val timeslot = eventPart.timeslot!! // Already checked not null
                val workTime = eventPart.user.workTimes.find { it.dayOfWeek == timeslot.dayOfWeek }
                if (workTime == null || workTime.endTime == null) {
                    return@filter true // Penalize if no work time defined for the day or endTime is null
                }
                timeslot.endTime!! > workTime.endTime // timeslot.endTime checked
            }
            .penalize("eventPart is out of bounds from end time hard penalize", HardMediumSoftScore.ONE_HARD)
    }

    fun sequentialEventPartsDisconnectedByTimeHardPenalize(constraintFactory: ConstraintFactory): Constraint {
        // event parts need to be connected together sequentially
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
                // Ensure timeslots and their necessary properties are non-null
                val ts1 = eventPart1.timeslot
                val ts2 = eventPart2.timeslot
                if (ts1?.endTime == null || ts2?.startTime == null || ts1.dayOfWeek == null || ts2.dayOfWeek == null || ts1.monthDay == null || ts2.monthDay == null) {
                    return@filter true // Penalize if essential time information is missing
                }

                val actualBetween = Duration.between(ts1.endTime, ts2.startTime)

                (actualBetween.isNegative || // End of part1 is after start of part2
                        (ts1.dayOfWeek != ts2.dayOfWeek) || // Different days of week
                        (ts1.monthDay != ts2.monthDay) || // Different month days
                        (actualBetween.toMinutes() > 0)) // Gap between part1 and part2
            }
            .penalize("Sequential Parts disconnected by time hard penalize", HardMediumSoftScore.ONE_HARD)

    }

    fun eventPartsDisconnectedByTimeHardPenalize(constraintFactory: ConstraintFactory): Constraint {
        // event parts need to be connected together sequentially
        return constraintFactory
            .forEach(EventPart::class.java)
            .join(EventPart::class.java,
                Joiners.equal(EventPart::groupId),
                Joiners.lessThan(EventPart::part),
            )
            .filter { eventPart1: EventPart, eventPart2: EventPart ->
                // Ensure timeslots and their necessary properties are non-null
                val ts1 = eventPart1.timeslot
                val ts2 = eventPart2.timeslot
                if (ts1?.startTime == null || ts1.endTime == null || ts2?.startTime == null ||
                    ts1.dayOfWeek == null || ts2.dayOfWeek == null || ts1.monthDay == null || ts2.monthDay == null) {
                    return@filter true // Penalize if essential time information is missing
                }

                val actualBetween = Duration.between(ts1.startTime, ts2.startTime)
                val partDuration = Duration.between(ts1.startTime, ts1.endTime)
                val partDiff = eventPart2.part - eventPart1.part
                val expectedMinutes = partDiff * partDuration.toMinutes()

                (actualBetween.isNegative || // Part2 starts before Part1 (unexpected for LATER parts)
                        (ts1.dayOfWeek != ts2.dayOfWeek) ||
                        (ts1.monthDay != ts2.monthDay) ||
                        (actualBetween.toMinutes() != expectedMinutes))
            }
            .penalize("Event parts disconnected by time hard penalize", HardMediumSoftScore.ONE_HARD)

    }

    fun firstAndLastPartDisconnectedByTimeHardPenalize(constraintFactory: ConstraintFactory): Constraint {
        // event parts need to be connected together sequentially
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
                // Ensure timeslots and their necessary properties are non-null
                val ts1 = eventPart1.timeslot
                val ts2 = eventPart2.timeslot
                if (ts1?.startTime == null || ts1.endTime == null || ts2?.endTime == null ||
                    ts1.dayOfWeek == null || ts2.dayOfWeek == null || ts1.monthDay == null || ts2.monthDay == null) {
                    return@filter true // Penalize if essential time information is missing
                }

                val between = Duration.between(ts1.startTime, ts2.endTime)
                val partDuration = Duration.between(ts1.startTime, ts1.endTime) // Duration of the first part
                // expectedDuration should be the sum of durations of all parts from eventPart1 to eventPart2.
                // This is complex if parts have variable durations.
                // The original logic `eventPart1.lastPart * partDuration.toMinutes()` assumes all parts have the same duration as part1,
                // and `eventPart1.lastPart` is actually the total number of parts in the group if eventPart1 is the first.
                // This seems to be what was intended.
                val totalNumberOfPartsInGroup = eventPart1.lastPart // Assuming eventPart1.part == 1 due to previous filter
                val expectedTotalDurationMinutes = totalNumberOfPartsInGroup * partDuration.toMinutes()


                (between.isNegative || // End of group is before start of group (impossible if start/end are consistent)
                        (ts1.dayOfWeek != ts2.dayOfWeek) || // Group spans multiple days (which this constraint might want to penalize if not intended)
                        (ts1.monthDay != ts2.monthDay) || // Group spans multiple monthDays
                        (kotlin.math.abs(between.toMinutes() - expectedTotalDurationMinutes) > 0)) // Total duration of allocated slots not matching expected.
            }
            .penalize("First and last part disconnected by time for more than 0 min hard penalize", HardMediumSoftScore.ONE_HARD)

    }

    fun eventPartsDisconnectedByMonthDayHardPenalize(constraintFactory: ConstraintFactory): Constraint {
        // event parts need to be connected together sequentially
        return constraintFactory
            .forEach(EventPart::class.java)
            .join(EventPart::class.java,
                Joiners.equal(EventPart::groupId),
                Joiners.lessThan(EventPart::part),
            )
            .filter { eventPart1: EventPart, eventPart2: EventPart ->
                // Assuming all parts of a multi-part event must be on the same monthDay.
                // Penalize if timeslots are on different monthDays.
                // Also ensure that timeslots and monthDays are not null.
                val ts1 = eventPart1.timeslot
                val ts2 = eventPart2.timeslot
                if (ts1?.monthDay == null || ts2?.monthDay == null) {
                    // If monthDay is essential and null, consider it a conflict or handle as per business logic.
                    // For now, if either is null (and they are not both null in a way that makes them "equal"),
                    // this implies a problem, so penalize. Or, filter out such event parts earlier.
                    // If one is null and the other isn't, they are different. If both are null, are they "same"?
                    // Let's assume for this constraint, null monthDay is not expected or should be filtered earlier.
                    // If they must be non-null and same, then (ts1?.monthDay != ts2?.monthDay) covers it if nulls are filtered.
                    // If nulls are possible and mean "not set yet", this might be too strict.
                    // Given the original `!!`, nulls were not expected to proceed.
                    return@filter ts1?.monthDay != ts2?.monthDay // True if one is null and other isn't. False if both are null.
                }
                ts1.monthDay != ts2.monthDay
            }
            .penalize("Event parts on different monthDays hard penalize", HardMediumSoftScore.ONE_HARD)

    }

    fun firstPartPushesLastPartOutHardPenalize(constraintFactory: ConstraintFactory): Constraint {
        // event parts need to be connected together sequentially
        return constraintFactory
            .forEach(EventPart::class.java)
            .filter { eventPart: EventPart ->
                eventPart.part == 1 &&
                eventPart.timeslot?.dayOfWeek != null &&
                eventPart.timeslot?.startTime != null &&
                eventPart.timeslot?.endTime != null
            }
            .filter { eventPart: EventPart ->
                val timeslot = eventPart.timeslot!! // Checked not null

                val totalParts = eventPart.lastPart
                val partDuration = Duration.between(timeslot.startTime, timeslot.endTime) // Nulls checked

                val totalMinutes = totalParts * partDuration.toMinutes()
                val totalDuration = Duration.ofMinutes(totalMinutes)

                val workTime = eventPart.user.workTimes.find { wt -> wt.dayOfWeek == timeslot.dayOfWeek }

                if (workTime == null || workTime.endTime == null) {
                    return@filter true // Penalize if no work time or work end time is undefined for the day
                }

                val possibleEndTime = timeslot.startTime!!.plus(totalDuration) // startTime checked
                possibleEndTime > workTime.endTime
            }
            .penalize("First part pushes last part out of bounds hard penalize", HardMediumSoftScore.ONE_HARD)

    }

    fun partIsNotFirstForStartOfDayHardPenalize(constraintFactory: ConstraintFactory): Constraint {
        // event parts need to be connected together sequentially
        return constraintFactory
            .forEach(EventPart::class.java)
            .filter { eventPart: EventPart ->
                eventPart.timeslot?.dayOfWeek != null && eventPart.timeslot?.startTime != null
            }
            .filter { eventPart: EventPart ->
                val timeslot = eventPart.timeslot!! // Checked not null
                val workTime = eventPart.user.workTimes.find { wt -> wt.dayOfWeek == timeslot.dayOfWeek }

                if (workTime == null || workTime.startTime == null) {
                    // If workTime or its startTime is null, this eventPart cannot be at the start of a non-existent work day.
                    // Depending on desired behavior: could be false (not at start) or true (problematic state).
                    // Let's assume it's not at the start if workTime/startTime is null.
                    return@filter false
                }
                timeslot.startTime == workTime.startTime
            }
            .filter { eventPart: EventPart -> eventPart.part != 1}
            .penalize("Part is not first for start of day hard penalize", HardMediumSoftScore.ONE_HARD)

    }

    fun eventPartsReversedHardPenalize(constraintFactory: ConstraintFactory): Constraint {
        // parts of the same event cannot be reversed
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
        // hard deadline missed for day of week or time of day
        return constraintFactory
            .forEach(EventPart::class.java)
            .filter { eventPart: EventPart ->
                eventPart.hardDeadline != null && eventPart.timeslot != null
            }
            .filter { eventPart: EventPart ->
                // Pre-parse date/time strings
                val deadlineStr = eventPart.hardDeadline!! // Null checked in previous filter
                // Assuming hardDeadline is in ISO-like format "YYYY-MM-DDTHH:MM:SS..."
                // Robust parsing would involve DateTimeFormatter if format can vary significantly.
                // For now, using substring as per original logic, but with safety.
                val deadlineDate = LocalDate.parse(deadlineStr.substring(0, 10))
                val deadlineTime = LocalTime.parse(deadlineStr.substring(11, 19)) // Assuming "HH:MM:SS"
                val deadlineMonthDay = MonthDay.from(deadlineDate)

                val timeslot = eventPart.timeslot!! // Null checked in previous filter

                // Ensure timeslot fields are not null before comparison
                if (timeslot.dayOfWeek == null || timeslot.monthDay == null || timeslot.endTime == null) {
                    return@filter false // Or handle as a violation if this state is invalid
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
                eventPart.startDate != null && // Ensure startDate is not null
                eventPart.timeslot != null
            }
            .filter { eventPart: EventPart ->
                // Pre-parse start date/time strings
                val startDateStr = eventPart.startDate!! // Null checked
                val originalStartDate = LocalDate.parse(startDateStr.substring(0, 10))
                val originalStartTime = LocalTime.parse(startDateStr.substring(11, 19))
                val originalMonthDay = MonthDay.from(originalStartDate)

                val timeslot = eventPart.timeslot!! // Null checked

                // Ensure timeslot fields are not null before comparison
                if (timeslot.startTime == null || timeslot.dayOfWeek == null || timeslot.monthDay == null) {
                    return@filter true // Penalize if timeslot parts are unexpectedly null
                }

                (originalStartTime != timeslot.startTime) ||
                        (originalStartDate.dayOfWeek != timeslot.dayOfWeek) ||
                        (originalMonthDay != timeslot.monthDay)
            }
            .penalize("event is not modifiable but time or day changed hard penalize", HardMediumSoftScore.ONE_HARD)
    }

    fun higherPriorityEventsSoonerForTimeOfDayMediumPenalize(constraintFactory: ConstraintFactory): Constraint {
        // higher priority events come sooner during the day
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
                // Pre-parse end date string
                val endDateStr = eventPart.endDate!! // Null checked
                // Using substring(5, 10) for "MM-DD" and prepending "--" to form "--MM-DD" for MonthDay.parse
                val endMonthDay = MonthDay.parse("--${endDateStr.substring(5, 10)}")

                (eventPart.dailyTaskList || (!eventPart.weeklyTaskList)) &&
                        (endMonthDay != eventPart.timeslot!!.monthDay) // timeslot and monthDay null checked
            }
            .penalize("end date monthDay is different from timeslot monthDay soft penalize", HardMediumSoftScore.ONE_SOFT)
    }

    fun notEqualStartDateForNonTaskSoftPenalize(constraintFactory: ConstraintFactory): Constraint {
        // reward after startDate
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
                eventPart.startDate != null && // Ensure startDate is not null
                eventPart.timeslot != null
            }
            .filter { eventPart: EventPart ->
                // Pre-parse start date/time strings
                val startDateStr = eventPart.startDate!! // Null checked
                val originalStartDate = LocalDate.parse(startDateStr.substring(0, 10))
                val originalStartTime = LocalTime.parse(startDateStr.substring(11, 19))
                val originalMonthDay = MonthDay.from(originalStartDate)

                val timeslot = eventPart.timeslot!! // Null checked

                // Ensure timeslot fields are not null before comparison
                if (timeslot.dayOfWeek == null || timeslot.startTime == null || timeslot.monthDay == null) {
                    return@filter true // Penalize if timeslot parts are unexpectedly null
                }

                (timeslot.dayOfWeek != originalStartDate.dayOfWeek) ||
                        (timeslot.startTime != originalStartTime) ||
                        (timeslot.monthDay != originalMonthDay)
            }
            .penalize("not equal start date for a non task soft penalize", HardMediumSoftScore.ONE_SOFT)
    }

    fun softDeadlineConflictSoftPenalize(constraintFactory: ConstraintFactory): Constraint {
        // soft deadline missed for day of week or monthDay or time of day
        return constraintFactory
            .forEach(EventPart::class.java)
            .filter { eventPart: EventPart ->
                eventPart.softDeadline != null && eventPart.timeslot != null
            }
            .filter { eventPart: EventPart ->
                // Pre-parse soft deadline string
                val deadlineStr = eventPart.softDeadline!! // Null checked
                val deadlineDate = LocalDate.parse(deadlineStr.substring(0, 10))
                val deadlineTime = LocalTime.parse(deadlineStr.substring(11, 19))
                val deadlineMonthDay = MonthDay.from(deadlineDate)

                val timeslot = eventPart.timeslot!! // Null checked

                // Ensure timeslot fields are not null for comparison
                if (timeslot.dayOfWeek == null || timeslot.monthDay == null || timeslot.endTime == null) {
                    return@filter false // Or handle as a violation
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
        // impact score preference of timeOfDay and dayOfWeek only if > 0 points

        return constraintFactory
            .forEach(EventPart::class.java)
            .filter { eventPart: EventPart ->
                eventPart.positiveImpactScore > 0
            }
            .filter { eventPart: EventPart -> eventPart.part == 1 }
            .filter { eventPart: EventPart -> eventPart.timeslot?.startTime != null && eventPart.timeslot?.endTime != null }
            .filter { eventPart: EventPart ->
                val totalParts = eventPart.lastPart
                val timeslot = eventPart.timeslot!! // startTime and endTime checked by previous filter
                val partDuration = Duration.between(timeslot.startTime, timeslot.endTime)
                val totalMinutes = totalParts * partDuration.toMinutes()
                val totalDuration = Duration.ofMinutes(totalMinutes)
                val workTime = eventPart.user.workTimes.find { wt -> wt.dayOfWeek == timeslot.dayOfWeek }

                if (workTime == null || workTime.endTime == null) {
                    return@filter true // Penalize if work time/end time undefined, making "within bounds" check fail
                }
                if (eventPart.positiveImpactDayOfWeek == null || eventPart.positiveImpactTime == null) {
                    return@filter false // Cannot evaluate if preferred impact time/day is not set
                }

                val possibleEndTime = timeslot.startTime!!.plus(totalDuration) // startTime checked

                ((eventPart.positiveImpactDayOfWeek!! < timeslot.dayOfWeek) || (eventPart.positiveImpactDayOfWeek!! > timeslot.dayOfWeek)) ||
                        ((eventPart.positiveImpactTime!! < timeslot.startTime) || (eventPart.positiveImpactTime!! > timeslot.startTime)) ||
                        (possibleEndTime > workTime.endTime)
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
            .filter { eventPart: EventPart -> eventPart.timeslot?.startTime != null && eventPart.timeslot?.endTime != null }
            .filter { eventPart: EventPart ->
                val totalParts = eventPart.lastPart
                val timeslot = eventPart.timeslot!! // startTime and endTime checked by previous filter
                val partDuration = Duration.between(timeslot.startTime, timeslot.endTime)
                val totalMinutes = totalParts * partDuration.toMinutes()
                val totalDuration = Duration.ofMinutes(totalMinutes)
                val workTime = eventPart.user.workTimes.find { wt -> wt.dayOfWeek == timeslot.dayOfWeek }

                if (workTime == null || workTime.endTime == null || eventPart.preferredTime == null) {
                     // If work time, its end, or preferred time is not set, cannot make a valid comparison or it's out of bounds.
                    return@filter true // Consider it a conflict or un-schedulable according to this rule.
                }
                val possibleEndTime = timeslot.startTime!!.plus(totalDuration) // startTime checked

                (possibleEndTime < workTime.endTime) // Check if within work hours first
            }
            .filter { eventPart: EventPart -> // eventPart.preferredTime is checked in previous filter indirectly
                val timeslot = eventPart.timeslot!! // Checked not null
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
                val timeslot = eventPart.timeslot!! // Checked not null
                val workTime = eventPart.user.workTimes.find { wt -> wt.dayOfWeek == timeslot.dayOfWeek }

                if (workTime == null || workTime.endTime == null || eventPart.preferredStartTimeRange == null) {
                    return@filter true // Penalize if essential info for check is missing
                }
                val possibleEndTime = timeslot.startTime!!.plus(totalDuration) // startTime checked

                (timeslot.startTime < eventPart.preferredStartTimeRange!!) || // startTime checked
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
                eventPart.startDate != null && // Ensure startDate is not null
                eventPart.timeslot != null
            }
            .filter { eventPart: EventPart ->
                val startDateStr = eventPart.startDate!! // Null checked
                val originalStartDate = LocalDate.parse(startDateStr.substring(0, 10))
                val originalStartTime = LocalTime.parse(startDateStr.substring(11, 19))
                val originalMonthDay = MonthDay.from(originalStartDate)

                val timeslot = eventPart.timeslot!! // Null checked

                // Ensure timeslot fields are not null for comparison
                if (timeslot.startTime == null || timeslot.dayOfWeek == null || timeslot.monthDay == null) {
                    return@filter true // Penalize if timeslot parts are unexpectedly null
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
                eventPart.startDate != null && // Ensure startDate is not null
                eventPart.timeslot != null
            }
            .filter { eventPart: EventPart ->
                val startDateStr = eventPart.startDate!! // Null checked
                val originalStartDate = LocalDate.parse(startDateStr.substring(0, 10))
                val originalStartTime = LocalTime.parse(startDateStr.substring(11, 19))
                val originalMonthDay = MonthDay.from(originalStartDate)

                val timeslot = eventPart.timeslot!! // Null checked

                // Ensure timeslot fields are not null for comparison
                if (timeslot.startTime == null || timeslot.dayOfWeek == null || timeslot.monthDay == null) {
                    return@filter true // Penalize if timeslot parts are unexpectedly null
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
                // Group by monthDay (or another suitable daily grouping) and user.
                // totalWorkingHours from EventPart is used as a key, implying it should be consistent for the group.
                // If totalWorkingHours can vary for the same user on the same day, this grouping might be problematic.
                // Assuming EventPart::totalWorkingHours means "user's total work hours for this day type".
                { eventPart: EventPart -> eventPart.user }, // Group by User first
                { eventPart: EventPart -> eventPart.timeslot?.monthDay }, // Then by day
                { eventPart: EventPart -> eventPart.totalWorkingHours }, // Include user's daily total hours for this day
                sum { eventPart: EventPart ->
                    // Safe parsing of start/end times for duration calculation
                    try {
                        if (eventPart.startDate != null && eventPart.endDate != null &&
                            eventPart.startDate.length >= 19 && eventPart.endDate.length >= 19) {
                            val startTime = LocalTime.parse(eventPart.startDate.substring(11, 19))
                            val endTime = LocalTime.parse(eventPart.endDate.substring(11, 19))
                            Duration.between(startTime, endTime).toMinutes().toInt()
                        } else {
                            0 // Event part with missing start/end time contributes 0 to workload
                        }
                    } catch (e: Exception) {
                        // Log error or handle as appropriate if parsing fails for valid-looking strings
                        0 // On parsing error, contribute 0 to workload for this part
                    }
                }
            )
            .filter { user: User, _: MonthDay?, dailyTotalWorkingHours: Int, calculatedEventWorkloadMinutes: Int ->
                if (user.maxWorkLoadPercent == null || user.maxWorkLoadPercent!! <= 0) {
                    return@filter false // No workload limit defined or it's invalid, so no conflict.
                }
                if (dailyTotalWorkingHours <= 0) {
                    // If user has no working hours defined for the day, any workload is technically infinite percent.
                    // Or, if calculatedEventWorkloadMinutes is also 0, then it's 0%.
                    // Let's say if no working hours, any event workload is a conflict.
                    return@filter calculatedEventWorkloadMinutes > 0
                }

                val calculatedWorkloadPercentage = (calculatedEventWorkloadMinutes.toDouble() * 100) / (dailyTotalWorkingHours * 60)
                calculatedWorkloadPercentage > user.maxWorkLoadPercent!!
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
