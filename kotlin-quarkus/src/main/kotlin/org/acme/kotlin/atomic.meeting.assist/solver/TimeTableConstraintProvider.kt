package org.acme.kotlin.atomic.meeting.assist.solver

import org.acme.kotlin.atomic.meeting.assist.domain.EventPart
import org.acme.kotlin.atomic.meeting.assist.domain.EventType
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
                // Removed notPreferredStartTimeOfTimeRangesHardPenalize - keeping Medium
                // Removed notPreferredEndTimeOfTimeRangesHardPenalize - keeping Medium
                meetingWithSameEventSlotHardConflict(constraintFactory),
                taskWithinWorkingHours(constraintFactory),

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
                // Removed notPreferredStartTimeOfTimeRangesSoftPenalize - keeping Medium
                // Removed notPreferredEndTimeOfTimeRangesSoftPenalize - keeping Medium
                maxWorkloadConflictSoftPenalize(constraintFactory),
                minNumberOfBreaksConflictSoftPenalize(constraintFactory),
                prioritizeTasksWithEarlierDeadlines(constraintFactory),
                // rewardPreferredMeetingTimeSoftReward removed
        )
    }

    // rewardPreferredMeetingTimeSoftReward function removed

    private fun prioritizeTasksWithEarlierDeadlines(constraintFactory: ConstraintFactory): Constraint {
        return constraintFactory
            .forEach(EventPart::class.java)
            .filter { eventPart ->
                eventPart.event.eventType == EventType.TASK &&
                eventPart.hardDeadline != null && // Only consider tasks with a hard deadline
                eventPart.timeslot != null &&
                eventPart.timeslot?.date != null &&
                eventPart.timeslot?.startTime != null
            }
            .penalize("Prioritize tasks with earlier deadlines",
                      HardMediumSoftScore.ONE_SOFT,
                      eventPart -> {
                          // Calculate the difference in days between the scheduled start and the deadline.
                          // The smaller the difference, the higher the penalty if it's not scheduled early.
                          // This is a simple way to encourage earlier scheduling for tighter deadlines.
                          // A more sophisticated approach might consider the actual duration to the deadline.
                          val scheduledDateTime = LocalDateTime.of(eventPart.timeslot!!.date, eventPart.timeslot!!.startTime)
                          val deadlineDateTime = eventPart.hardDeadline!!

                          // We want to penalize scheduling a task later if its deadline is sooner.
                          // This logic might need refinement. A simpler penalty might be just for being close to the deadline.
                          // For now, let's penalize based on how late it is relative to its deadline proximity.
                          // This is tricky. A simpler approach: penalize by the number of days from now until deadline.
                          // The closer the deadline, the more important it is to schedule.
                          // This requires a "current date" context, which OptaPlanner doesn't directly provide in constraints.
                          //
                          // Alternative: Penalize tasks scheduled *later* that have *earlier* deadlines
                          // This requires comparing two tasks.

                          // Let's try a simpler penalty: Penalize based on how late the task is scheduled,
                          // weighted by the inverse of the time to its deadline.
                          // This is still complex.

                          // Simplest initial approach: Penalize more if the deadline is soon and the task is not scheduled.
                          // However, this constraint is on an *assigned* EventPart.
                          // So, the task *is* scheduled. We want to encourage it to be scheduled *earlier* if its deadline is *sooner*.

                          // Let's consider the number of days from the scheduled start to the deadline.
                          // If a task due tomorrow is scheduled today, that's good.
                          // If a task due next week is scheduled today, that's also good, but less critical.
                          // This constraint should penalize tasks with *earlier* deadlines that are scheduled *later* than other tasks.

                          // This requires a comparison. Let's use a simpler per-task penalty for now:
                          // Penalize tasks if their start date is "late" relative to their deadline.
                          // What is "late"? Perhaps a percentage of the time between creation and deadline?
                          // This is also getting complex.

                          // Let's use a very simple penalty: the number of days from the start of the planning window
                          // to the start of the task. The later it's scheduled, the higher the penalty, but this should be
                          // scaled by deadline proximity.

                          // Simplest penalty: Number of hours between scheduled start and deadline.
                          // We want to REWARD tasks that are further from their deadline,
                          // or PENALIZE tasks that are closer to their deadline.
                          // Let's penalize based on the number of hours from scheduled start to deadline.
                          // The fewer hours, the higher the penalty (because it's "last minute").
                          // No, this is the opposite. We want to penalize tasks that *could* have been scheduled earlier but weren't.

                          // Let's penalize the *lateness* of the task.
                          // Lateness = Scheduled Start Time - Reference Time (e.g. today)
                          // And this penalty should be higher for tasks with earlier deadlines.

                          // For now, a simple penalty: just the number of days until the deadline.
                          // This isn't quite right. We want to penalize scheduling a task with an early deadline LATE.

                          // Let's use the number of days the task is scheduled before its deadline.
                          // If it's scheduled far in advance, the penalty is low.
                          // If it's scheduled close to the deadline, the penalty is high.
                          // Constraint should penalize tasks that are scheduled close to their hard deadline.
                          // The penalty is higher if the task is scheduled closer to its deadline.
                          val scheduledStartDateTime = LocalDateTime.of(eventPart.timeslot!!.date!!, eventPart.timeslot!!.startTime!!)
                          val durationToDeadline = Duration.between(scheduledStartDateTime, eventPart.hardDeadline!!)

                          // Penalize more if durationToDeadline is small.
                          // Avoid division by zero or negative durations if scheduled after deadline (hard constraint should catch that).
                          if (durationToDeadline.isNegative || durationToDeadline.isZero) {
                              return 1000 // High penalty if at or after deadline (though another hard constraint should handle this)
                          }
                          // The smaller the duration, the larger the penalty. Inverse relationship.
                          // Add 1 to avoid issues with very small fractions if duration is large.
                          // Cap the penalty to avoid excessively large numbers.
                          // Maximize (1 / hours_to_deadline)
                          val hoursToDeadline = durationToDeadline.toHours().toInt()
                          if (hoursToDeadline <= 0) return 100 // High penalty if very close or past
                          return 100 / hoursToDeadline // Example: 100/1 = 100, 100/24 = 4, 100/168 (week) = 0
                      })
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

        if (workTime.startTime == null || workTime.endTime == null) {
            return true // Work time start/end not defined, cannot ensure event fits
        }

        // Calculate event start and end LocalDateTime
        val eventPartStartDateTime = LocalDateTime.of(timeslot.date!!, timeslot.startTime!!)

        val partDuration = Duration.between(timeslot.startTime!!, timeslot.endTime!!)
        val totalEventDuration = Duration.ofMinutes(eventPart.lastPart * partDuration.toMinutes())
        val eventOverallEndDateTime = eventPartStartDateTime.plus(totalEventDuration)

        // Check if event exceeds working hours for that day
        val workEndDateTime = LocalDateTime.of(timeslot.date!!, workTime.endTime!!)
        if (eventOverallEndDateTime > workEndDateTime) {
            return true // Event exceeds working hours
        }

        // Check against preferred time ranges
        val preferredTimeRanges = eventPart.event.preferredTimeRanges!!
        // True if no preferred range matches.
        // An event part is considered inside preferred start time if its start time is >= any preferred start time for that day.
        val isInsideAnyPreferredRange = preferredTimeRanges.any { timeRange ->
            if (timeRange.startTime == null) return@any false // Skip ill-defined preferred range
            // Check if preferredTR applies to the eventPart's dayOfWeek (can be null on TR for any day)
            (timeRange.dayOfWeek == null || timeRange.dayOfWeek == timeslot.dayOfWeek) &&
            // Compare LocalTime part only, as dayOfWeek is already matched
            (timeslot.startTime!! >= timeRange.startTime!!)
        }

        return !isInsideAnyPreferredRange // Outside if not inside any
    }

    // Helper for preferred end times
    private fun isOutsidePreferredEndTimeRanges(eventPart: EventPart): Boolean {
        if (eventPart.event.preferredTimeRanges.isNullOrEmpty() ||
            eventPart.timeslot?.date == null || // Added null check for date
            eventPart.timeslot?.startTime == null || // startTime needed for duration calc
            eventPart.timeslot?.endTime == null ||
            eventPart.timeslot?.dayOfWeek == null) {
            return false // Not enough info or no preferences
        }
        // This constraint applies only to the last part of a multi-part event
        if (eventPart.part != eventPart.lastPart) {
            return false
        }

        val timeslot = eventPart.timeslot!! // This is the timeslot of the *last part*

        // Calculate the actual end LocalDateTime of this event part (which is the last part)
        // val eventPartActualEndDateTime = LocalDateTime.of(timeslot.date!!, timeslot.endTime!!) // Not directly needed for comparison logic below

        // Check against preferred time ranges
        val preferredTimeRanges = eventPart.event.preferredTimeRanges!!
        // True if no preferred range matches.
        // An event part is considered inside preferred end time if its end time is <= any preferred end time for that day.
        val isInsideAnyPreferredRange = preferredTimeRanges.any { timeRange ->
            if (timeRange.endTime == null) return@any false // Skip ill-defined preferred range
            (timeRange.dayOfWeek == null || timeRange.dayOfWeek == timeslot.dayOfWeek) &&
            (timeslot.endTime!! <= timeRange.endTime!!)
        }

        return !isInsideAnyPreferredRange // Outside if not inside any
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
            .filter { ep1: EventPart, ep2: EventPart ->
                val ep1IsMeeting = ep1.event.eventType == EventType.ONE_ON_ONE_MEETING || ep1.event.eventType == EventType.GROUP_MEETING
                val ep2IsMeeting = ep2.event.eventType == EventType.ONE_ON_ONE_MEETING || ep2.event.eventType == EventType.GROUP_MEETING
                (ep1IsMeeting && !ep2IsMeeting) || (!ep1IsMeeting && ep2IsMeeting)
            }
            .penalize("Meetings should not be same timeslot as another event penalize hard", HardMediumSoftScore.ONE_HARD)
    }

    fun meetingNotSameTimeSlotHardConflict(constraintFactory: ConstraintFactory): Constraint {
        return constraintFactory
            .forEach(EventPart::class.java)
            .filter { eventPart: EventPart ->
                eventPart.meetingId != null &&
                (eventPart.event.eventType == EventType.ONE_ON_ONE_MEETING || eventPart.event.eventType == EventType.GROUP_MEETING)
            }
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
                if (ts1?.startTime == null || ts1.endTime == null || ts2?.startTime == null || // Keep basic null checks
                    ts1.dayOfWeek == null || ts2.dayOfWeek == null || ts1.monthDay == null || ts2.monthDay == null) {
                    return@filter true // Penalize if essential time information is missing for day/monthDay comparison
                }

                // Simplified: Penalize if any two parts of the same event are on different days or monthDays.
                // The detailed contiguity and gap checks are handled by sequentialEventPartsDisconnectedByTimeHardPenalize.
                // Apply "different day" penalty only if NOT a task
                if (eventPart1.event.eventType != EventType.TASK && eventPart2.event.eventType != EventType.TASK) {
                    (ts1.dayOfWeek != ts2.dayOfWeek) || (ts1.monthDay != ts2.monthDay)
                } else {
                    false // Do not penalize tasks for being on different days/monthDays
                }
            }
            .penalize("Event parts on different days hard penalize", HardMediumSoftScore.ONE_HARD) // Renamed for clarity

    }

    fun firstAndLastPartDisconnectedByTimeHardPenalize(constraintFactory: ConstraintFactory): Constraint {
        // Ensures the entire event (from start of first part to end of last part) occurs on the same calendar day.
        return constraintFactory
            .forEach(EventPart::class.java)
            .join(EventPart::class.java,
                Joiners.equal(EventPart::groupId),
                Joiners.lessThan(EventPart::part), // Ensures eventPart1 is an earlier part than eventPart2
            )
            .filter { eventPart1: EventPart, eventPart2: EventPart ->
                // Apply this only to the actual first and last parts of the event group
                (eventPart1.part == 1) && (eventPart2.part == eventPart2.lastPart)
            }
            .filter { eventPart1: EventPart, eventPart2: EventPart ->
                // Ensure timeslots and their necessary properties are non-null
                val ts1 = eventPart1.timeslot
                val ts2 = eventPart2.timeslot
                if (ts1?.dayOfWeek == null || ts1.monthDay == null || ts2?.dayOfWeek == null || ts2.monthDay == null) {
                    return@filter true // Penalize if essential time information is missing for day/monthDay comparison
                }

                // Penalize if the first and last parts are on different days or monthDays.
                // Duration checks are removed as they were based on flawed uniform duration assumption.
                // Contiguity is handled by sequentialEventPartsDisconnectedByTimeHardPenalize.
                // Apply "different day" penalty only if NOT a task
                if (eventPart1.event.eventType != EventType.TASK && eventPart2.event.eventType != EventType.TASK) {
                    (ts1.dayOfWeek != ts2.dayOfWeek) || (ts1.monthDay != ts2.monthDay)
                } else {
                    false // Do not penalize tasks
                }
            }
            .penalize("First and last part on different days hard penalize", HardMediumSoftScore.ONE_HARD) // Renamed for clarity

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
                // Apply "different monthDay" penalty only if NOT a task
                if (eventPart1.event.eventType != EventType.TASK && eventPart2.event.eventType != EventType.TASK) {
                    ts1.monthDay != ts2.monthDay
                } else {
                    false // Do not penalize tasks
                }
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
        // hard deadline missed
        return constraintFactory
            .forEach(EventPart::class.java)
            .filter { eventPart: EventPart ->
                eventPart.hardDeadline != null && eventPart.timeslot != null &&
                eventPart.timeslot?.endTime != null && eventPart.timeslot?.date != null // Assuming timeslot.date exists
            }
            .filter { eventPart: EventPart ->
                val deadlineDateTime = eventPart.hardDeadline!!
                val timeslot = eventPart.timeslot!!
                val eventPartEndDateTime = LocalDateTime.of(timeslot.date!!, timeslot.endTime!!)
                eventPartEndDateTime > deadlineDateTime
            }
            .penalize("missed hard Deadline hard penalize", HardMediumSoftScore.ONE_HARD)
    }

    private fun taskWithinWorkingHours(constraintFactory: ConstraintFactory): Constraint {
        return constraintFactory
            .forEach(EventPart::class.java)
            .filter { eventPart -> eventPart.event.eventType == EventType.TASK }
            .filter { eventPart ->
                val timeslot = eventPart.timeslot
                if (timeslot?.dayOfWeek == null || timeslot.startTime == null || timeslot.endTime == null) {
                    return@filter true // Penalize if timeslot info is missing for a task
                }
                val workTime = eventPart.user.workTimes.find { it.dayOfWeek == timeslot.dayOfWeek }
                if (workTime == null || workTime.startTime == null || workTime.endTime == null) {
                    return@filter true // Penalize if no work time defined for the day or start/end time is null
                }
                // Check if task part is outside working hours
                (timeslot.startTime < workTime.startTime || timeslot.endTime > workTime.endTime)
            }
            .penalize("Task scheduled outside working hours", HardMediumSoftScore.ONE_HARD)
    }

    fun modifiableConflictHardPenalize(constraintFactory: ConstraintFactory): Constraint {
        return constraintFactory
            .forEach(EventPart::class.java)
            .filter { eventPart: EventPart ->
                !eventPart.modifiable &&
                eventPart.part == 1 &&
                eventPart.startDate != null && // This is LocalDateTime now
                eventPart.timeslot != null &&
                eventPart.timeslot?.startTime != null && eventPart.timeslot?.date != null // Assuming timeslot.date
            }
            .filter { eventPart: EventPart ->
                val originalStartDateTime = eventPart.startDate!!
                val timeslot = eventPart.timeslot!!
                val currentEventPartStartDateTime = LocalDateTime.of(timeslot.date!!, timeslot.startTime!!)
                currentEventPartStartDateTime != originalStartDateTime
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
                // eventPart.endDate is LocalDateTime?, timeslot and monthDay null checked in previous filter
                val endMonthDay = MonthDay.from(eventPart.endDate!!) // Convert LocalDateTime to MonthDay

                (eventPart.dailyTaskList || (!eventPart.weeklyTaskList)) &&
                        (endMonthDay != eventPart.timeslot!!.monthDay)
            }
            .penalize("end date monthDay is different from timeslot monthDay soft penalize", HardMediumSoftScore.ONE_SOFT)
    }

    fun notEqualStartDateForNonTaskSoftPenalize(constraintFactory: ConstraintFactory): Constraint {
        // reward after startDate
        return constraintFactory
            .forEach(EventPart::class.java)
            .filter { eventPart: EventPart -> !(eventPart.dailyTaskList) }
            .filter { eventPart: EventPart -> !(eventPart.weeklyTaskList) }
            .filter { eventPart: EventPart -> eventPart.event.eventType != EventType.TASK } // Added for clarity
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
                eventPart.startDate != null && // This is LocalDateTime now
                eventPart.timeslot != null &&
                eventPart.timeslot?.startTime != null && eventPart.timeslot?.date != null // Assuming timeslot.date
            }
            .filter { eventPart: EventPart ->
                val originalStartDateTime = eventPart.startDate!!
                val timeslot = eventPart.timeslot!!
                val currentEventPartStartDateTime = LocalDateTime.of(timeslot.date!!, timeslot.startTime!!)
                currentEventPartStartDateTime != originalStartDateTime
            }
            .penalize("not equal start date for a non task soft penalize", HardMediumSoftScore.ONE_SOFT)
    }

    fun softDeadlineConflictSoftPenalize(constraintFactory: ConstraintFactory): Constraint {
        // soft deadline missed
        return constraintFactory
            .forEach(EventPart::class.java)
            .filter { eventPart: EventPart ->
                eventPart.softDeadline != null && eventPart.timeslot != null &&
                eventPart.timeslot?.endTime != null && eventPart.timeslot?.date != null // Assuming timeslot.date exists
            }
            .filter { eventPart: EventPart ->
                val deadlineDateTime = eventPart.softDeadline!!
                val timeslot = eventPart.timeslot!!
                val eventPartEndDateTime = LocalDateTime.of(timeslot.date!!, timeslot.endTime!!)
                eventPartEndDateTime > deadlineDateTime
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
            .filter { eventPart: EventPart ->
                eventPart.timeslot?.date != null && // Ensure date is available
                eventPart.timeslot?.startTime != null &&
                eventPart.timeslot?.endTime != null
            }
            .filter { eventPart: EventPart ->
                val timeslot = eventPart.timeslot!!
                val partDuration = Duration.between(timeslot.startTime!!, timeslot.endTime!!)
                val totalEventDuration = Duration.ofMinutes(eventPart.lastPart * partDuration.toMinutes())

                val eventPartStartDateTime = LocalDateTime.of(timeslot.date!!, timeslot.startTime!!)
                val eventOverallEndDateTime = eventPartStartDateTime.plus(totalEventDuration)

                val workTime = eventPart.user.workTimes.find { wt -> wt.dayOfWeek == timeslot.dayOfWeek }
                if (workTime?.endTime == null) { // Also check workTime itself for null
                    return@filter true // Penalize if work time/end time undefined
                }
                val workEndDateTime = LocalDateTime.of(timeslot.date!!, workTime.endTime!!)
                if (eventOverallEndDateTime > workEndDateTime) {
                    return@filter true // Event exceeds working hours, so it's not a "positive impact" placement
                }

                if (eventPart.positiveImpactDayOfWeek == null || eventPart.positiveImpactTime == null) {
                    return@filter false // No specific positive impact day/time defined, so no penalty from this rule
                }

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
                eventPart.timeslot?.date != null && // Ensure date is available
                eventPart.timeslot?.startTime != null &&
                eventPart.timeslot?.endTime != null
            }
            .filter { eventPart: EventPart -> // This filter now focuses purely on the preferred time match
                val timeslot = eventPart.timeslot!! // Null checks done in prior filters
                // Penalize if preferredTime is set and does not match the timeslot's startTime.
                // The check for fitting within working hours should be handled by other constraints (like outOfWorkTimesBoundary).
                // Or, if this constraint should ONLY penalize if it's within working hours but not at preferred time:

                val partDuration = Duration.between(timeslot.startTime!!, timeslot.endTime!!)
                val totalEventDuration = Duration.ofMinutes(eventPart.lastPart * partDuration.toMinutes())
                val eventPartStartDateTime = LocalDateTime.of(timeslot.date!!, timeslot.startTime!!)
                val eventOverallEndDateTime = eventPartStartDateTime.plus(totalEventDuration)

                val workTime = eventPart.user.workTimes.find { wt -> wt.dayOfWeek == timeslot.dayOfWeek }
                // If workTime or endTime is null, or preferredTime is null (already filtered by previous .filter { eventPart: EventPart -> eventPart.preferredTime != null }),
                // it's hard to apply this rule.
                if (workTime?.endTime == null) {
                    return@filter false // Cannot determine if it's within working hours, so don't apply this penalty.
                                        // Other constraints might penalize for being outside work hours.
                }
                val workEndDateTime = LocalDateTime.of(timeslot.date!!, workTime.endTime!!)

                val isWithinWorkTime = eventOverallEndDateTime <= workEndDateTime

                if (!isWithinWorkTime) {
                    return@filter false // If it's not even within working time, this specific "not preferred start time" penalty shouldn't apply.
                                        // Let "out of work bounds" constraints handle it.
                }

                // Penalize if it IS within work time BUT start time is not the preferred one.
                // eventPart.preferredTime is guaranteed non-null by the first filter of this constraint.
                eventPart.preferredTime!! != timeslot.startTime!!
            }
            .penalize("preferred startTime not given medium penalize", HardMediumSoftScore.ONE_MEDIUM)
    }

    fun notPreferredStartTimeOfTimeRangesHardPenalize(constraintFactory: ConstraintFactory): Constraint {
        return constraintFactory
            .forEach(EventPart::class.java)
            .filter(this::isOutsidePreferredStartTimeRanges)
            .penalize("Timeslot not in preferred start time of preferredTimeRanges hard penalize", HardMediumSoftScore.ONE_HARD)
    }

    // fun notPreferredEndTimeOfTimeRangesHardPenalize(constraintFactory: ConstraintFactory): Constraint {
    //     return constraintFactory
    //         .forEach(EventPart::class.java)
    //         .filter(this::isOutsidePreferredEndTimeRanges)
    //         .penalize("Timeslot not in preferred end time of preferredTimeRanges hard penalize", HardMediumSoftScore.ONE_HARD)
    // }

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

    // fun notPreferredStartTimeOfTimeRangesSoftPenalize(constraintFactory: ConstraintFactory): Constraint {
    //     return constraintFactory
    //         .forEach(EventPart::class.java)
    //         .filter(this::isOutsidePreferredStartTimeRanges)
    //         .penalize("Timeslot not in preferred start time of preferredTimeRanges soft penalize", HardMediumSoftScore.ONE_SOFT)
    // }

    // fun notPreferredEndTimeOfTimeRangesSoftPenalize(constraintFactory: ConstraintFactory): Constraint {
    //     return constraintFactory
    //         .forEach(EventPart::class.java)
    //         .filter(this::isOutsidePreferredEndTimeRanges)
    //         .penalize("Timeslot not in preferred end time of preferredTimeRanges soft penalize", HardMediumSoftScore.ONE_SOFT)
    // }

    fun notPreferredScheduleStartTimeRangeMediumPenalize(constraintFactory: ConstraintFactory): Constraint {
        return constraintFactory
            .forEach(EventPart::class.java)
            .filter { eventPart -> eventPart.timeslot != null } // Ensure timeslot is assigned
            .filter { eventPart -> eventPart.preferredStartTimeRange != null } // Check this specific preference exists
            .filter { eventPart -> eventPart.part == 1 } // Apply to the first part of an event
            .filter { eventPart ->
                // Penalize if the event's actual start time is before its preferred start time range
                eventPart.timeslot!!.startTime < eventPart.preferredStartTimeRange!!
            }
            .penalize("Scheduled start time is before preferred start time range", HardMediumSoftScore.ONE_MEDIUM)
    }

    fun notPreferredScheduleEndTimeRangeMediumPenalize(constraintFactory: ConstraintFactory): Constraint {
        return constraintFactory
            .forEach(EventPart::class.java)
            .filter { eventPart -> eventPart.timeslot != null } // Ensure timeslot is assigned
            .filter { eventPart -> eventPart.preferredEndTimeRange != null } // Check this specific preference exists
            // No need to filter for eventPart.part == eventPart.lastPart,
            // as the preferredEndTimeRange applies to the desired end time of the event's timeslot.
            // If an event spans multiple timeslots, this would apply to the end time of each part's timeslot.
            // For a single-slot meeting, this is fine. For multi-slot, this means each part should ideally end within this range.
            // The problem description implies the range is for the start time.
            // Let's assume preferredEndTimeRange on EventPart means "the event should start such that it ends by this time, OR the preferred start slot itself should end by this time."
            // Given the DTO has preferredStartTimeFrom and preferredStartTimeTo, this means the *chosen start slot* should be within this window.
            // So, notPreferredScheduleEndTimeRangeMediumPenalize should check if timeslot.startTime > eventPart.preferredEndTimeRange (which is derived from DTO's preferredStartTimeTo)
            .filter { eventPart ->
                // Penalize if the event's actual start time is after its preferred end time of the start range.
                // (i.e., event starts after the preferred window to start has closed)
                eventPart.timeslot!!.startTime > eventPart.preferredEndTimeRange!! // preferredEndTimeRange here means the end of the preferred *start* window
            }
            .penalize("Scheduled start time is after preferred start time window ends", HardMediumSoftScore.ONE_MEDIUM)
    }

    fun externalMeetingModifiableConflictMediumPenalize(constraintFactory: ConstraintFactory): Constraint {
        return constraintFactory
            .forEach(EventPart::class.java)
            .filter { eventPart: EventPart ->
                eventPart.isExternalMeeting &&
                !eventPart.isExternalMeetingModifiable &&
                eventPart.part == 1 &&
                eventPart.startDate != null && // This is LocalDateTime now
                eventPart.timeslot != null &&
                eventPart.timeslot?.startTime != null && eventPart.timeslot?.date != null // Assuming timeslot.date
            }
            .filter { eventPart: EventPart ->
                val originalStartDateTime = eventPart.startDate!!
                val timeslot = eventPart.timeslot!!
                val currentEventPartStartDateTime = LocalDateTime.of(timeslot.date!!, timeslot.startTime!!)
                currentEventPartStartDateTime != originalStartDateTime
            }
            .penalize("event is an external meeting and not modifiable but time or day was changed medium penalize", HardMediumSoftScore.ONE_MEDIUM)
    }

    fun meetingModifiableConflictMediumPenalize(constraintFactory: ConstraintFactory): Constraint {
        return constraintFactory
            .forEach(EventPart::class.java)
            .filter { eventPart: EventPart ->
                !eventPart.isMeetingModifiable &&
                (eventPart.event.eventType == EventType.ONE_ON_ONE_MEETING || eventPart.event.eventType == EventType.GROUP_MEETING) &&
                eventPart.part == 1 &&
                eventPart.startDate != null && // This is LocalDateTime now
                eventPart.timeslot != null &&
                eventPart.timeslot?.startTime != null && eventPart.timeslot?.date != null // Assuming timeslot.date
            }
            .filter { eventPart: EventPart ->
                val originalStartDateTime = eventPart.startDate!!
                val timeslot = eventPart.timeslot!!
                val currentEventPartStartDateTime = LocalDateTime.of(timeslot.date!!, timeslot.startTime!!)
                currentEventPartStartDateTime != originalStartDateTime
            }
            .penalize("event is a meeting and not modifiable but time or day was changed medium penalize", HardMediumSoftScore.ONE_MEDIUM)
    }

    fun maxWorkloadConflictSoftPenalize(constraintFactory: ConstraintFactory): Constraint {
        return constraintFactory
            .forEach(EventPart::class.java)
            .filter { eventPart: EventPart -> !eventPart.gap } // Only consider non-gap event parts for workload
            .filter { eventPart: EventPart -> eventPart.timeslot != null && eventPart.timeslot?.date != null } // Ensure timeslot and date are assigned
            .groupBy(
                EventPart::user, // Group by user
                { eventPart -> eventPart.timeslot!!.date }, // Then by the specific date of the timeslot
                sum { eventPart -> // Sum the actual duration of assigned timeslots
                    Duration.between(eventPart.timeslot!!.startTime, eventPart.timeslot!!.endTime).toMinutes().toInt()
                }
            )
            .filter { user: User, date: LocalDate, totalScheduledMinutes: Int ->
                if (user.maxWorkLoadPercent == null || user.maxWorkLoadPercent <= 0) {
                    return@filter false // No workload limit defined or it's invalid.
                }

                val workTimeForDay = user.workTimes.find { wt -> wt.dayOfWeek == date.dayOfWeek }
                if (workTimeForDay == null) {
                    // No work time defined for this specific day for the user.
                    // If there are scheduled minutes, it's effectively 100%+ workload against 0 capacity.
                    return@filter totalScheduledMinutes > 0
                }

                val userDailyCapacityMinutes = Duration.between(workTimeForDay.startTime, workTimeForDay.endTime).toMinutes()
                if (userDailyCapacityMinutes <= 0) {
                     // User has work time defined but duration is zero or negative, any work is overload.
                    return@filter totalScheduledMinutes > 0
                }

                val allowedScheduledMinutes = userDailyCapacityMinutes * (user.maxWorkLoadPercent / 100.0)

                totalScheduledMinutes > allowedScheduledMinutes
            }
            .penalize("Exceeded max workload soft penalize", HardMediumSoftScore.ONE_SOFT) { _, _, _, _ -> 1 } // Provide a match weighter
    }

    fun backToBackMeetingsPreferredMediumReward(constraintFactory: ConstraintFactory): Constraint {
        return constraintFactory
            .forEach(EventPart::class.java)
            .filter { eventPart: EventPart -> eventPart.user.backToBackMeetings == true }
            .filter { eventPart: EventPart -> (eventPart.event.eventType == EventType.ONE_ON_ONE_MEETING || eventPart.event.eventType == EventType.GROUP_MEETING) }
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
            .filter { eventPart: EventPart -> (eventPart.event.eventType == EventType.ONE_ON_ONE_MEETING || eventPart.event.eventType == EventType.GROUP_MEETING) }
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
            .filter { ep: EventPart -> ep.event.eventType == EventType.ONE_ON_ONE_MEETING || ep.event.eventType == EventType.GROUP_MEETING }
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
