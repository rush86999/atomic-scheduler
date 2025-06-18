package org.acme.kotlin.atomic.meeting.assist.rest

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.node.NullNode
import io.quarkus.panache.common.Sort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
import javax.annotation.security.RolesAllowed
import javax.inject.Inject
import javax.transaction.Transactional
import javax.validation.Valid
import javax.validation.constraints.Min
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotEmpty
import javax.validation.constraints.NotNull
import javax.ws.rs.*

// DTOs for callAPI payload
data class TimeslotDto(
    val id: UUID?,
    val hostId: String?,
    val dayOfWeek: String?,
    val startTime: String?,
    val endTime: String?,
    val monthDay: String?
)

data class WorkTimeDto(
    val id: UUID?,
    val userId: String?,
    val hostId: String?,
    val startTime: String?,
    val endTime: String?
)

data class UserDto(
    val id: String?,
    val hostId: String?,
    val maxWorkLoadPercent: Int?,
    val backToBackMeetings: Boolean?,
    val maxNumberOfMeetings: Int?,
    val workTimes: List<WorkTimeDto>?
)

data class PreferredTimeRangeDto(
    val id: UUID?,
    val eventId: UUID?,
    val userId: String?,
    val hostId: String?,
    val dayOfWeek: String?,
    val startTime: String?,
    val endTime: String?
)

data class EventDto(
    val id: UUID?,
    val userId: String?,
    val hostId: String?,
    val preferredTimeRanges: List<PreferredTimeRangeDto>?
)

data class EventPartDto(
    val id: String?,
    val groupId: UUID?,
    val eventId: UUID?,
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
    val forEventId: UUID?,
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
    val gap: Boolean?, // Changed from Long? to Boolean?
    val preferredStartTimeRange: String?,
    val preferredEndTimeRange: String?,
    val totalWorkingHours: Int?,
    val event: EventDto?, // Assuming EventDto is defined
    val timeslot: TimeslotDto? // Assuming TimeslotDto is defined
)

data class TimeTableSolutionDto(
    val timeslotList: List<TimeslotDto>?,
    val userList: List<UserDto>?,
    val eventPartList: List<EventPartDto>?,
    val score: String?,
    val fileKey: String?,
    val hostId: String?
)


data class PostTableRequestBody(
    @field:JsonProperty("singletonId")
    @field:NotNull(message = "singletonId must not be null")
    var singletonId: UUID? = null,

    @field:JsonProperty("hostId")
    @field:NotNull(message = "hostId must not be null")
    var hostId: UUID? = null,

    @field:JsonProperty("timeslots")
    @field:NotEmpty(message = "timeslots must not be empty")
    @field:Valid // This will trigger validation on each Timeslot object in the list
    var timeslots: MutableList<Timeslot>? = null,

    @field:JsonProperty("userList")
    @field:NotEmpty(message = "userList must not be empty")
    @field:Valid // This will trigger validation on each User object in the list
    var userList: MutableList<User>? = null,

    @field:JsonProperty("eventParts")
    @field:NotEmpty(message = "eventParts must not be empty")
    @field:Valid // This will trigger validation on each EventPart object in the list
    var eventParts: MutableList<EventPart>? = null,

    @field:NotBlank(message = "fileKey must not be blank")
    var fileKey: String,

    @field:Min(value = 0, message = "delay must be non-negative")
    val delay: Long,

    @field:NotBlank(message = "callBackUrl must not be blank")
    // Consider adding @org.hibernate.validator.constraints.URL if available and desired
    val callBackUrl: String,
)

data class PostStopSingletonRequestBody(
    @field:JsonProperty("singletonId")
    @field:NotNull(message = "singletonId must not be null")
    var singletonId: UUID? = null,
)


@Path("/timeTable")
class TimeTableResource {

    // Create a CoroutineScope tied to the lifecycle of this resource.
    // If TimeTableResource is @ApplicationScoped (default for JAX-RS resources), this scope lasts for the app lifetime.
    // Use SupervisorJob so failure of one coroutine doesn't cancel others.
    private val resourceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

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
        if (id != singletonIdFromRequest) {
            // Or NotFoundException if singletonId is the primary resource key and id is just a parameter for this specific call
            throw BadRequestException("Path parameter singletonId ($singletonIdFromRequest) does not match the id ($id) used for lookup context.")
        }
        // hostIdFromRequest is a UUID from @PathParam, it cannot be null. The previous check was redundant.

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

    private fun mapTimeslotsToDto(timeslots: List<Timeslot>): List<TimeslotDto> {
        return timeslots.map { ts ->
            TimeslotDto(ts.id, ts.hostId?.toString(), ts.dayOfWeek?.toString(), ts.startTime?.toString(), ts.endTime?.toString(), ts.monthDay?.toString())
        }
    }

    private fun mapUsersToDto(users: List<User>): List<UserDto> {
        return users.map { u ->
            val workTimeDtos = u.workTimes.map { wt -> // Accessing u.workTimes will trigger lazy load if session is active
                WorkTimeDto(wt.id, wt.userId?.toString(), wt.hostId?.toString(), wt.startTime?.toString(), wt.endTime?.toString())
            }
            UserDto(u.id?.toString(), u.hostId?.toString(), u.maxWorkLoadPercent, u.backToBackMeetings, u.maxNumberOfMeetings, workTimeDtos)
        }
    }

    private fun mapEventPartsToDto(
        eventParts: List<EventPart>,
        userDtos: List<UserDto>,
        eventsMap: Map<String, Event>, // Pass String ID for Event
        allTimeslotsForHost: List<Timeslot> // Pass all timeslots for host to find matches
    ): List<EventPartDto> {
        return eventParts.map { ep ->
            val userDto = userDtos.find { it.id == ep.userId?.toString() }
            val eventDomain = eventsMap[ep.eventId] // ep.eventId is String

            val preferredTimeRangeDtos = eventDomain?.preferredTimeRanges?.map { ptr -> // Accessing eventDomain.preferredTimeRanges will trigger lazy load
                PreferredTimeRangeDto(ptr.id, ptr.eventId, ptr.userId?.toString(), ptr.hostId?.toString(), ptr.dayOfWeek?.toString(), ptr.startTime?.toString(), ptr.endTime?.toString())
            }
            val eventDto = eventDomain?.let { e ->
                EventDto(e.id, e.userId?.toString(), e.hostId?.toString(), preferredTimeRangeDtos)
            }
            val timeslotDto = ep.timeslot?.let { ts ->
                allTimeslotsForHost.find { it.id == ts.id }?.let {
                    TimeslotDto(it.id, it.hostId?.toString(), it.dayOfWeek?.toString(), it.startTime?.toString(), it.endTime?.toString(), it.monthDay?.toString())
                }
            }

            EventPartDto(
                id = ep.id?.toString(), // EventPart.id is Long, DTO id is String?
                groupId = UUID.fromString(ep.groupId), // EventPart.groupId is String, DTO groupId is UUID?
                eventId = UUID.fromString(ep.eventId),
                part = ep.part,
                lastPart = ep.part == ep.lastPart, // Corrected: lastPart on EventPart is Int (total parts)
                startDate = ep.startDate,
                endDate = ep.endDate,
                taskId = ep.taskId?.let { UUID.fromString(it) },
                softDeadline = ep.softDeadline,
                hardDeadline = ep.hardDeadline,
                meetingId = ep.meetingId?.let { UUID.fromString(it) },
                userId = ep.userId?.toString(),
                hostId = ep.hostId?.toString(),
                user = userDto,
                priority = ep.priority,
                isPreEvent = ep.isPreEvent,
                isPostEvent = ep.isPostEvent,
                forEventId = ep.forEventId?.let { UUID.fromString(it) },
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
                gap = if(ep.gap) ep.gap else null, // EventPart.gap is Boolean, DTO gap is Long? - Mismatch! Changed DTO to Boolean?
                preferredStartTimeRange = ep.preferredStartTimeRange?.toString(),
                preferredEndTimeRange = ep.preferredEndTimeRange?.toString(),
                totalWorkingHours = ep.totalWorkingHours, // EventPart.totalWorkingHours is Int, DTO is Int?
                event = eventDto,
                timeslot = timeslotDto
            )
        }
    }


    @Transactional
    fun callAPI(callBackUrl: String, fileKey: String, hostId: UUID) {
        val client = OkHttp()
        val clientHandler: HttpHandler = ClientFilters.BasicAuth(username, password).then(client)

        // Efficiently fetch data
        val timeslotsForHost = timeslotRepository.list("hostId", Sort.by("dayOfWeek").and("startTime").and("endTime").and("id"), hostId)
        val usersForHost = userRepository.list("hostId", hostId)
        val eventPartsForHost = eventPartRepository.list("hostId", Sort.by("startDate").and("endDate").and("id"), hostId)

        // Pre-fetch Events for all eventPartsForHost to optimize
        val eventIds = eventPartsForHost.mapNotNull { it.eventId }.distinct()
        val eventsMap = if (eventIds.isNotEmpty()) eventRepository.list("id in ?1", eventIds).associateBy { it.id } else emptyMap()

        // Populate DTOs using helper methods
        val timeslotDtos = mapTimeslotsToDto(timeslotsForHost)
        val userDtos = mapUsersToDto(usersForHost) // This will trigger lazy load of workTimes within transaction
        val eventPartDtos = mapEventPartsToDto(eventPartsForHost, userDtos, eventsMap, timeslotsForHost) // This will trigger lazy load of preferredTimeRanges within transaction

        // Calculate score
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
            hostId = hostId.toString()
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

        resourceScope.launch { // Changed to use resourceScope
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
            eventPart.user = userMap[eventPart.userId]
            // LOGGER.debug("Assigned user ${eventPart.user} to eventPart ${eventPart.id}")
        }

        // Optimize Event fetching and persist related entities
        var eventsToPersist: MutableList<Event> = mutableListOf()
        eventParts.forEach { ep -> eventsToPersist.add(ep.event) }
        val distinctEventsToPersist = eventsToPersist.distinctBy { it.id }
        eventRepository.persist(distinctEventsToPersist)

        var preferredTimesRangesToPersist: MutableList<PreferredTimeRange> = mutableListOf()
        distinctEventsToPersist.forEach { event -> event.preferredTimeRanges?.forEach { pt -> preferredTimesRangesToPersist.add(pt) } }
        val distinctPreferredTimeRanges = preferredTimesRangesToPersist.distinctBy { pt -> listOf(pt.dayOfWeek, pt.startTime, pt.endTime, pt.eventId) }
        preferredTimeRangeRepository.persist(distinctPreferredTimeRanges)

        // Assign fetched Events to EventParts
        val eventIds = eventParts.map { it.eventId }.distinct()
        val eventMap = eventRepository.list("id in ?1", eventIds).associateBy { it.id }
        eventParts.forEach { eventPart ->
            eventPart.event = eventMap[eventPart.eventId]
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


    private fun processSolveRequest(@Valid args: PostTableRequestBody, isAdmin: Boolean) { // Added @Valid here
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
    fun solveDay(@Valid args: PostTableRequestBody) { // Added @Valid here
        processSolveRequest(args, false)
    }

    @POST
    @Path("/admin/solve-day")
    @RolesAllowed("admin")
    fun adminSolveDay(@Valid args: PostTableRequestBody) { // Added @Valid here
        processSolveRequest(args, true)
    }

    @DELETE
    @Path("/user/delete/{id}")
    fun deleteTable(
        @PathParam("id") id: UUID,
    ) {
        deleteTableGivenUser(id)
    }

    @DELETE
    @Path("/admin/delete/{id}")
    @RolesAllowed("admin")
    fun adminDeleteTable(
        @PathParam("id") id: UUID,
    ) {
        deleteTableGivenUser(id)
    }

}
