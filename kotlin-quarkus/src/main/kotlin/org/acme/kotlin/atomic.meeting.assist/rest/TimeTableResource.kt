package org.acme.kotlin.atomic.meeting.assist.rest

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.node.NullNode
import io.quarkus.panache.common.Sort
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.jboss.logging.Logger
import org.acme.kotlin.atomic.meeting.assist.domain.*
import org.acme.kotlin.atomic.meeting.assist.persistence.*
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.http4k.client.OkHttp
import org.http4k.core.*
import org.http4k.filter.ClientFilters
import org.http4k.format.Jackson
import org.http4k.format.Jackson.asJsonObject
import org.http4k.format.Jackson.asJsonValue
import org.http4k.format.Jackson.json
import org.optaplanner.core.api.score.ScoreManager
import org.optaplanner.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore
import org.optaplanner.core.api.solver.SolverManager
import java.time.LocalDateTime
import java.util.*
import javax.ws.rs.core.Response // Added import for Response.Status
import javax.annotation.security.RolesAllowed
import javax.inject.Inject
import javax.transaction.Transactional
import javax.ws.rs.*

// DTOs for callAPI payload
data class TimeslotDto(
    val id: Long?, // Changed from UUID?
    val hostId: UUID?, // Changed from String?
    val dayOfWeek: String?,
    val startTime: String?,
    val endTime: String?,
    val monthDay: String?
)

data class WorkTimeDto(
    val id: Long?, // Changed from UUID?
    val userId: UUID?, // Changed from String?
    val hostId: UUID?, // Changed from String?
    val startTime: String?,
    val endTime: String?
)

data class UserDto(
    val id: UUID?, // Changed from String?
    val name: String?, // Added name
    val hostId: UUID?, // Changed from String?
    val maxWorkLoadPercent: Int?,
    val backToBackMeetings: Boolean?,
    val maxNumberOfMeetings: Int?,
    val workTimes: List<WorkTimeDto>?
)

data class PreferredTimeRangeDto(
    val id: Long?, // Changed from UUID?
    val eventId: String?, // Changed from UUID?
    val userId: UUID?, // Changed from String?
    val hostId: UUID?, // Changed from String?
    val dayOfWeek: String?,
    val startTime: String?,
    val endTime: String?
)

data class EventDto(
    val id: String?, // Changed from UUID?
    val name: String?, // Added name
    val userId: UUID?, // Changed from String?
    val hostId: UUID?, // Changed from String?
    val preferredTimeRanges: List<PreferredTimeRangeDto>?
)

data class EventPartDto(
    val id: Long?, // Changed from String?
    val groupId: String?, // Changed from UUID?
    val eventId: String?, // Changed from UUID?
    val part: Int?,
    val lastPart: Boolean?,
    val startDate: LocalDateTime?,
    val endDate: LocalDateTime?,
    val taskId: UUID?,
    val softDeadline: LocalDateTime?,
    val hardDeadline: LocalDateTime?,
    val meetingId: UUID?,
    val userId: String?,
    val hostId: String?,
    val user: UserDto?, // Assuming UserDto is defined
    val priority: Int?,
    val isPreEvent: Boolean?,
    val isPostEvent: Boolean?,
    val forEventId: String?, // Changed from UUID?
    val positiveImpactScore: Int?,
    val negativeImpactScore: Int?,
    val positiveImpactDayOfWeek: String?,
    val positiveImpactTime: String?,
    val negativeImpactDayOfWeek: String?,
    val negativeImpactTime: String?,
    val modifiable: Boolean?,
    val preferredDayOfWeek: String?,
    val preferredTime: String?,
    val isExternalMeeting: Boolean?,
    val isExternalMeetingModifiable: Boolean?,
    val isMeetingModifiable: Boolean?,
    val isMeeting: Boolean?,
    val dailyTaskList: Boolean?,
    val weeklyTaskList: Boolean?,
    val gap: Boolean?, // Changed from Long?
    val preferredStartTimeRange: String?,
    val preferredEndTimeRange: String?,
    // val totalWorkingHours: Int?, // Removed as not in domain EventPart
    val event: EventDto?, // Assuming EventDto is defined
    val timeslot: TimeslotDto? // Assuming TimeslotDto is defined
)

data class TimeTableSolutionDto(
    val timeslotList: List<TimeslotDto>?,
    val userList: List<UserDto>?,
    val eventPartList: List<EventPartDto>?,
    val score: String?,
    val fileKey: String?,
    val hostId: UUID? // Changed from String?
)


data class PostTableRequestBody(
    @field:JsonProperty("singletonId")
    var singletonId: UUID? = null,
    @field:JsonProperty("hostId")
    var hostId: UUID? = null,
    @field:JsonProperty("timeslots")
    var timeslots: MutableList<Timeslot>? = null,
    @field:JsonProperty("userList")
    var userList: MutableList<User>? = null,
    @field:JsonProperty("eventParts")
    var eventParts: MutableList<EventPart>? = null,
    var fileKey: String,
    val delay: Long,
    val callBackUrl: String,
)

data class PostStopSingletonRequestBody(
    @field:JsonProperty("singletonId")
    var singletonId: UUID? = null,
)


@Path("/timeTable")
class TimeTableResource {

    companion object {
        private val LOGGER = Logger.getLogger(TimeTableResource::class.java)
    }

    @ConfigProperty(name = "USERNAME")
    lateinit var username: String

    @ConfigProperty(name = "PASSWORD")
    lateinit var password: String

    @Inject
    lateinit var timeslotRepository: TimeslotRepository
    @Inject
    lateinit var userRepository: UserRepository
    @Inject
    lateinit var eventPartRepository: EventPartRepository
    @Inject
    lateinit var workTimeRepository: WorkTimeRepository

    @Inject
    lateinit var preferredTimeRangeRepository: PreferredTimeRangeRepository

    @Inject
    lateinit var eventRepository: EventRepository

    @Inject
    lateinit var solverManager: SolverManager<TimeTable, UUID>

    @Inject
    lateinit var scoreManager: ScoreManager<TimeTable, HardMediumSoftScore>

    // Inject Jackson ObjectMapper
    @Inject
    lateinit var objectMapper: com.fasterxml.jackson.databind.ObjectMapper


    @GET
    @Path("/user/byId/{singletonId}/{hostId}")
    fun getTimeTableById(@PathParam("singletonId") singletonId: UUID, @PathParam("hostId") hostId: UUID): TimeTable {
        // Get the solver status before loading the solution
        // to avoid the race condition that the solver terminates between them
//        val solverStatus = getSolverStatus()
        LOGGER.debug("getTimeTableById request: singletonId: $singletonId, hostId: $hostId")
        val solution: TimeTable = findById(singletonId, singletonId, hostId)
        scoreManager.updateScore(solution) // Sets the score
//        solution.solverStatus = solverStatus
        return solution
    }

    @GET
    @Path("/admin/byId/{singletonId}/{hostId}")
    @RolesAllowed("admin")
    fun adminGetTimeTableById(@PathParam("singletonId") singletonId: UUID, @PathParam("hostId") hostId: UUID): TimeTable {
        // Get the solver status before loading the solution
        // to avoid the race condition that the solver terminates between them
//        val solverStatus = getSolverStatus()
        LOGGER.debug("adminGetTimeTableById request: singletonId: $singletonId, hostId: $hostId")
        val solution: TimeTable = findById(singletonId, singletonId, hostId)
        scoreManager.updateScore(solution) // Sets the score
//        solution.solverStatus = solverStatus
        return solution
    }

    @Transactional
    protected fun findById(id: UUID, singletonIdFromRequest: UUID, hostIdFromRequest: UUID): TimeTable {
        check(id == singletonIdFromRequest) { "There is no timeTable with id ($id)." }
        // Occurs in a single transaction, so each initialized lesson references the same timeslot/room instance
        // that is contained by the timeTable's timeslotList/roomList.
        check(hostIdFromRequest != null) { "hostIdFromRequest is null ($hostIdFromRequest)" }
//        solverManager.terminateEarly(singletonIdFromRequest)
        val savedTimeslots = timeslotRepository.list("hostId", Sort.by("dayOfWeek").and("startTime").and("endTime").and("id"), hostIdFromRequest)
        LOGGER.debug("savedTimeslots: $savedTimeslots")
        val savedUsers = userRepository.list("hostId", hostIdFromRequest) // Fetch users
        LOGGER.debug("savedUsers for findById: $savedUsers")
        val savedEvents = eventPartRepository.list("hostId", Sort.by("startDate").and("endDate").and("id"), hostIdFromRequest)
        return TimeTable(
            savedTimeslots,
            savedUsers, // Pass users to constructor
            savedEvents
        )
    }

    @Transactional
    fun callAPI(callBackUrl: String, fileKey: String, hostId: UUID) {
        val client = OkHttp()
        val clientHandler: HttpHandler = ClientFilters.BasicAuth(username, password).then(client)

        // Efficiently fetch data
        val timeslotsForHost = timeslotRepository.list("hostId", Sort.by("dayOfWeek").and("startTime").and("endTime").and("id"), hostId)
        val usersForHost = userRepository.list("hostId", hostId) // Assuming WorkTimes are fetched as needed (e.g. eager)
        val eventPartsForHost = eventPartRepository.list("hostId", Sort.by("startDate").and("endDate").and("id"), hostId)

        // Populate DTOs
        val timeslotDtos = timeslotsForHost.map { ts ->
            TimeslotDto(ts.id, ts.hostId, ts.dayOfWeek?.toString(), ts.startTime?.toString(), ts.endTime?.toString(), ts.monthDay?.toString())
        }

        val userDtos = usersForHost.map { u ->
            val workTimeDtos = u.workTimes.map { wt ->
                WorkTimeDto(wt.id, wt.userId, wt.hostId, wt.startTime?.toString(), wt.endTime?.toString())
            }
            UserDto(u.id, u.name, u.hostId, u.maxWorkLoadPercent, u.backToBackMeetings, u.maxNumberOfMeetings, workTimeDtos)
        }

        // Fetch related Events and their PreferredTimeRanges efficiently
        val eventIds = eventPartsForHost.mapNotNull { it.eventId }.distinct() // eventId is String
        val eventsForHost = if (eventIds.isNotEmpty()) eventRepository.list("id in ?1", eventIds).associateBy { it.id } else emptyMap()

        // Assuming PreferredTimeRanges are part of the Event objects due to EAGER fetch or similar
        val eventPartDtos = eventPartsForHost.map { ep ->
            val userDto = userDtos.find { it.id == ep.userId } // ep.userId is UUID
            val eventDomainObject = eventsForHost[ep.eventId] // ep.eventId is String

            val preferredTimeRangeDtos = eventDomainObject?.preferredTimeRanges?.map { ptr ->
                PreferredTimeRangeDto(ptr.id, ptr.eventId, ptr.userId, ptr.hostId, ptr.dayOfWeek?.toString(), ptr.startTime?.toString(), ptr.endTime?.toString())
            }
            val eventDto = eventDomainObject?.let { e ->
                EventDto(e.id, e.name, e.userId, e.hostId, preferredTimeRangeDtos)
            }
            val timeslotDto = ep.timeslot?.let { ts -> // ep.timeslot might be null
                 timeslotsForHost.find { it.id == ts.id }?.let { // find matching from already fetched timeslots
                     TimeslotDto(it.id, it.hostId, it.dayOfWeek?.toString(), it.startTime?.toString(), it.endTime?.toString(), it.monthDay?.toString())
                 }
            }

            EventPartDto(
                id = ep.id, // Long?
                groupId = ep.groupId, // String
                eventId = ep.eventId, // String
                part = ep.part,
                lastPart = ep.lastPart,
                startDate = ep.startDate,
                endDate = ep.endDate,
                taskId = ep.taskId,
                softDeadline = ep.softDeadline,
                hardDeadline = ep.hardDeadline,
                meetingId = ep.meetingId,
                userId = ep.userId?.toString(),
                hostId = ep.hostId?.toString(),
                user = userDto,
                priority = ep.priority,
                isPreEvent = ep.isPreEvent,
                isPostEvent = ep.isPostEvent,
                forEventId = ep.forEventId, // String?
                positiveImpactScore = ep.positiveImpactScore,
                negativeImpactScore = ep.negativeImpactScore,
                positiveImpactDayOfWeek = ep.positiveImpactDayOfWeek?.toString(),
                positiveImpactTime = ep.positiveImpactTime?.toString(),
                negativeImpactDayOfWeek = ep.negativeImpactDayOfWeek?.toString(),
                negativeImpactTime = ep.negativeImpactTime?.toString(),
                modifiable = ep.modifiable,
                preferredDayOfWeek = ep.preferredDayOfWeek?.toString(),
                preferredTime = ep.preferredTime?.toString(),
                isExternalMeeting = ep.isExternalMeeting,
                isExternalMeetingModifiable = ep.isExternalMeetingModifiable,
                isMeetingModifiable = ep.isMeetingModifiable,
                isMeeting = ep.isMeeting,
                dailyTaskList = ep.dailyTaskList,
                weeklyTaskList = ep.weeklyTaskList,
                gap = ep.gap, // Boolean?
                preferredStartTimeRange = ep.preferredStartTimeRange?.toString(),
                preferredEndTimeRange = ep.preferredEndTimeRange?.toString(),
                // totalWorkingHours removed
                event = eventDto,
                timeslot = timeslotDto
            )
        }

        // Calculate score (similar to how it was done before, if TimeTable instance is needed)
        // For simplicity, let's assume score calculation might need the original domain objects,
        // or it's passed/calculated differently. Here, we'll pass null if not easily available.
        // This part might need adjustment based on how score is obtained.
        // One way: Reconstruct a minimal TimeTable for score calculation if necessary or fetch score separately.
        val currentScore = try {
            // Construct TimeTable with all required lists: timeslots, users, event parts
            val tableForScore = TimeTable(
                timeslotsForHost, // Already fetched Timeslot objects
                usersForHost,     // Already fetched User objects
                eventPartsForHost // Already fetched EventPart objects
            )
            scoreManager.updateScore(tableForScore)
            tableForScore.score?.toString()
        } catch (e: Exception) {
            LOGGER.warn("Error calculating score for callback: ${e.message}")
            null
        }


        val solutionDto = TimeTableSolutionDto(
            timeslotList = timeslotDtos,
            userList = userDtos,
            eventPartList = eventPartDtos,
            score = currentScore,
            fileKey = fileKey,
            hostId = hostId // UUID
        )

        // Serialize DTO to JSON
        val bodyString = objectMapper.writeValueAsString(solutionDto)

        deleteTableGivenUser(hostId)

        val request = Request(Method.POST, callBackUrl).body(bodyString).header("Content-Type", "application/json")
        val response = clientHandler(request)

        LOGGER.debug("Callback response status: ${response.status}")
        LOGGER.debug("Callback response body: ${response.bodyString()}")
    }

    @Transactional
    fun deleteTableGivenUser(
        hostId: UUID,
    ) {
        // this.singletonId = null // Class member removed
        this.eventPartRepository.delete("hostId", hostId)
        this.workTimeRepository.delete("hostId", hostId)
        this.userRepository.delete("hostId", hostId)
        this.timeslotRepository.delete("hostId", hostId)
        this.preferredTimeRangeRepository.delete("hostId", hostId)
        this.eventRepository.delete("hostId", hostId)
    }


    suspend fun callBackAPI(singletonId: UUID, hostId: UUID, fileKey: String, delay: Long, callBackUrl: String) {
        kotlinx.coroutines.delay(delay)
        solverManager.terminateEarly(singletonId)
        callAPI(callBackUrl, fileKey, hostId)
    }

    fun solve(singletonId: UUID, hostId: UUID, fileKey: String, delay: Long, callBackUrl: String) {
        // check(singletonId !== null) {"singletonId is null $singletonId"} // No longer needed as it's a parameter

        solverManager.solveAndListen(singletonId,
            { findById(it, singletonId, hostId) }, // Pass singletonId and hostId to findById
            this::save)

        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
            callBackAPI(singletonId, hostId, fileKey, delay, callBackUrl)
        }

    }

    @Transactional
    fun createTableAndSolve(
        singletonId: UUID,
        hostId: UUID,
        timeslots: MutableList<Timeslot>,
        userList: MutableList<User>,
        eventParts: MutableList<EventPart>,
        fileKey: String,
        delay: Long,
        callBackUrl: String,
    ) {
        // Class members this.singletonId, this.hostId, this.fileKey are removed.
        // These values are now passed as parameters.

        this.timeslotRepository.persist(timeslots)
        val savedTimeslots = timeslotRepository.list("hostId", hostId)
        LOGGER.debug("savedTimeslots: $savedTimeslots")
        this.userRepository.persist(userList)
        val savedUsers = userRepository.list("hostId", hostId)
        LOGGER.debug("savedUsers: $savedUsers")
        savedUsers.forEach { workTimeRepository.persist(it.workTimes) }

        val savedWorkTimes = workTimeRepository.list("hostId", hostId)
        LOGGER.debug("savedWorkTimes: $savedWorkTimes")
        LOGGER.debug("eventParts: $eventParts")

        // Optimize User fetching
        val userIds = eventParts.map { it.userId }.distinct()
        val userMap = userRepository.list("id in ?1", userIds).associateBy { it.id }
        eventParts.forEach { eventPart ->
            userMap[eventPart.userId]?.let { eventPart.user = it }
            // LOGGER.debug("Assigned user ${eventPart.user} to eventPart ${eventPart.id}")
        }

        // Optimize Event fetching and persist related entities
        var eventsToPersist: MutableList<Event> = mutableListOf()
        // Ensure ep.event is not null before adding. If it can be null, handle appropriately.
        eventParts.forEach { ep -> ep.event?.let { eventsToPersist.add(it) } }
        val distinctEventsToPersist = eventsToPersist.distinctBy { it.id }
        eventRepository.persist(distinctEventsToPersist)

        var preferredTimesRangesToPersist: MutableList<PreferredTimeRange> = mutableListOf()
        distinctEventsToPersist.forEach { event -> event.preferredTimeRanges?.forEach { pt -> preferredTimesRangesToPersist.add(pt) } }
        val distinctPreferredTimeRanges = preferredTimesRangesToPersist.distinctBy { pt -> listOf(pt.dayOfWeek, pt.startTime, pt.endTime, pt.eventId) }
        preferredTimeRangeRepository.persist(distinctPreferredTimeRanges)

        // Assign fetched Events to EventParts
        val eventIds = eventParts.mapNotNull { it.eventId }.distinct() // eventId is String
        val eventMap = if(eventIds.isNotEmpty()) eventRepository.list("id in ?1", eventIds).associateBy { it.id } else emptyMap()

        eventParts.forEach { eventPart ->
            eventMap[eventPart.eventId]?.let { eventPart.event = it }
            // LOGGER.debug("Assigned event ${eventPart.event} to eventPart ${eventPart.id}")
        }

        eventPartRepository.persist(eventParts)
        val savedEventParts = eventPartRepository.list("hostId", Sort.by("startDate").and("endDate").and("id"), hostId)
        LOGGER.debug("savedEvents: $savedEventParts")


        solve(singletonId, hostId, fileKey, delay, callBackUrl)
    }

    @Transactional
    protected fun save(timeTable: TimeTable) {
        if (timeTable == null) {
            LOGGER.warn("timeTable is null")
            return
        }
        for (eventPart in timeTable.eventPartList) {
            if (eventPart.id != null && eventPart.timeslot != null) {
                eventPartRepository.update("timeslot = ?1 where id = ?2", eventPart.timeslot, eventPart.id)
            } else {
                LOGGER.warn("Skipping save for EventPart with null id or timeslot: ${eventPart.id}")
            }
        }
    }

    @POST
    @Path("/user/stopSolving")
    fun stopSolving(
        args: PostStopSingletonRequestBody,
    ) {
        solverManager.terminateEarly(args.singletonId)
    }

    @POST
    @Path("/admin/stopSolving")
    @RolesAllowed("admin")
    fun adminStopSolving(
        args: PostStopSingletonRequestBody,
    ) {
        solverManager.terminateEarly(args.singletonId)
    }


    private fun processSolveRequest(args: PostTableRequestBody, isAdmin: Boolean) {
        val action = if (isAdmin) "adminSolveDay" else "solveDay"
        LOGGER.info("Received $action request: $args")

        // Note: The logic for checking active solving processes based on a class member singletonId
        // needs to be re-evaluated. If we want to prevent multiple solves for the same host,
        // we might need a different mechanism, perhaps checking active solver sessions
        // in SolverManager using args.singletonId if that's intended to be unique per solve request.
        // For now, we'll assume a new solve request for a given hostId can proceed after cleaning up.

        args.hostId?.let { hostId ->
            // Consider if singletonId from args should be used to check/stop an existing solve
            // For now, any existing data for the hostId is deleted.
            deleteTableGivenUser(hostId)
        }


        args.singletonId?.let { singletonId ->
            args.hostId?.let { hostId ->
                args.timeslots?.let { timeslots ->
                    args.userList?.let { userList ->
                        args.eventParts?.let { eventParts ->
                            createTableAndSolve(
                                singletonId, hostId, timeslots, userList, eventParts,
                                args.fileKey, args.delay, args.callBackUrl
                            )
                        }
                    }
                }
            }
        } ?: LOGGER.warn("$action request failed: singletonId is null. Args: $args")
    }

    @POST
    @Path("/user/solve-day")
    fun solveDay(args: PostTableRequestBody) {
        // Ensure hostId is not null before proceeding
        args.hostId ?: throw WebApplicationException("hostId must be provided", Response.Status.BAD_REQUEST)
        processSolveRequest(args, false)
    }

    @POST
    @Path("/admin/solve-day")
    @RolesAllowed("admin")
    fun adminSolveDay(args: PostTableRequestBody) {
        // Ensure hostId is not null before proceeding
        args.hostId ?: throw WebApplicationException("hostId must be provided", Response.Status.BAD_REQUEST)
        processSolveRequest(args, true)
    }

    @DELETE
    @Path("/user/delete/{hostId}") // Changed from id to hostId to match deleteTableGivenUser
    fun deleteTable(
        @PathParam("hostId") hostId: UUID,
    ) {
        deleteTableGivenUser(hostId)
    }

    @DELETE
    @Path("/admin/delete/{hostId}") // Changed from id to hostId to match deleteTableGivenUser
    @RolesAllowed("admin")
    fun adminDeleteTable(
        @PathParam("hostId") hostId: UUID,
    ) {
        deleteTableGivenUser(hostId)
    }

}
