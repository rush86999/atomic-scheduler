package org.acme.kotlin.atomic.meeting.assist.rest

import com.fasterxml.jackson.annotation.JsonProperty
// import com.fasterxml.jackson.databind.node.NullNode // Not used
import io.quarkus.panache.common.Sort
// import kotlinx.coroutines.GlobalScope // Not used directly in this modified version
import kotlinx.coroutines.CoroutineScope // For launch
import kotlinx.coroutines.Dispatchers // For launch
import kotlinx.coroutines.launch // For launch
import org.jboss.logging.Logger
import org.acme.kotlin.atomic.meeting.assist.domain.*
import org.acme.kotlin.atomic.meeting.assist.persistence.*
// import org.acme.kotlin.atomic.meeting.assist.rest.toDto // Mappers will be internal to the class
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.http4k.client.OkHttp
import org.http4k.core.*
import org.http4k.filter.ClientFilters
// import org.http4k.format.Jackson // Not used directly
// import org.http4k.format.Jackson.asJsonObject // Not used directly
// import org.http4k.format.Jackson.asJsonValue // Not used directly
// import org.http4k.format.Jackson.json // Not used directly
import org.optaplanner.core.api.score.ScoreManager
import org.optaplanner.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore
import org.optaplanner.core.api.solver.SolverManager
import java.util.*
import javax.annotation.security.RolesAllowed // Using javax
import javax.inject.Inject                 // Using javax
import javax.transaction.Transactional      // Using javax
import javax.ws.rs.*                       // Using javax
import javax.ws.rs.core.MediaType          // Using javax

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
    // Assuming name and durationInMinutes are not part of this DTO as per user's provided definition here
)

data class EventPartDto(
    val id: String?,
    val groupId: UUID?,
    val eventId: UUID?,
    val part: Int?,
    val lastPart: Boolean?,
    val startDate: String?,
    val endDate: String?,
    val taskId: UUID?,
    val softDeadline: String?,
    val hardDeadline: String?,
    val meetingId: UUID?,
    val userId: String?,
    val hostId: String?,
    val user: UserDto?,
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
    val gap: Long?,
    val preferredStartTimeRange: String?,
    val preferredEndTimeRange: String?,
    val totalWorkingHours: Int?,
    val event: EventDto?,
    val timeslot: TimeslotDto?
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
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class TimeTableResource {

    // Mapper functions moved inside the class as private extensions or methods
    private fun Timeslot.toDtoInternal(): TimeslotDto {
        return TimeslotDto(
            id = this.id?.let { UUID.nameUUIDFromBytes(it.toString().toByteArray()) },
            hostId = this.hostId?.toString(),
            dayOfWeek = this.dayOfWeek.toString(),
            startTime = this.startTime.toString(),
            endTime = this.endTime.toString(),
            monthDay = this.monthDay.toString()
        )
    }

    private fun WorkTime.toDtoInternal(): WorkTimeDto {
        return WorkTimeDto(
            id = this.id?.let { UUID.nameUUIDFromBytes(it.toString().toByteArray()) },
            userId = this.userId.toString(),
            hostId = this.hostId.toString(),
            startTime = this.startTime.toString(),
            endTime = this.endTime.toString()
        )
    }

    private fun User.toDtoInternal(): UserDto {
        return UserDto(
            id = this.id.toString(),
            hostId = this.hostId.toString(),
            maxWorkLoadPercent = this.maxWorkLoadPercent,
            backToBackMeetings = this.backToBackMeetings,
            maxNumberOfMeetings = this.maxNumberOfMeetings,
            workTimes = this.workTimes.map { it.toDtoInternal() } // Uses internal mapper
        )
    }

    private fun PreferredTimeRange.toDtoInternal(): PreferredTimeRangeDto {
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

    private fun Event.toDtoInternal(): EventDto {
        return EventDto(
            id = UUID.fromString(this.id),
            userId = this.userId.toString(),
            hostId = this.hostId.toString(),
            preferredTimeRanges = this.preferredTimeRanges?.map { it.toDtoInternal() } // Uses internal mapper
        )
    }

    private fun EventPart.toDtoInternal(userDtoMap: Map<String, UserDto>, eventDtoMap: Map<String, EventDto>): EventPartDto {
        val mappedUserDto = userDtoMap[this.userId.toString()]
        val mappedEventDto = eventDtoMap[this.eventId]

        return EventPartDto(
            id = this.id?.toString(),
            groupId = UUID.fromString(this.groupId),
            eventId = UUID.fromString(this.eventId),
            part = this.part,
            lastPart = this.part == this.lastPart,
            startDate = this.startDate, // Assuming domain String, DTO String
            endDate = this.endDate,     // Assuming domain String, DTO String
            taskId = this.taskId?.let { UUID.fromString(it) },
            softDeadline = this.softDeadline, // Assuming domain String, DTO String
            hardDeadline = this.hardDeadline, // Assuming domain String, DTO String
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
            timeslot = this.timeslot?.toDtoInternal() // Uses internal mapper
        )
    }


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

    @Inject
    lateinit var objectMapper: com.fasterxml.jackson.databind.ObjectMapper


    @GET
    @Path("/user/byId/{singletonId}/{hostId}")
    fun getTimeTableById(@PathParam("singletonId") singletonId: UUID, @PathParam("hostId") hostId: UUID): TimeTable {
        LOGGER.debug("getTimeTableById request: ${'$'}singletonId, hostId: ${'$'}hostId")
        val solution: TimeTable = findById(singletonId, hostId) // Adjusted to match new findById
        scoreManager.updateScore(solution)
        return solution
    }

    @GET
    @Path("/admin/byId/{singletonId}/{hostId}")
    @RolesAllowed("admin")
    fun adminGetTimeTableById(@PathParam("singletonId") singletonId: UUID, @PathParam("hostId") hostId: UUID): TimeTable {
        LOGGER.debug("adminGetTimeTableById request: ${'$'}singletonId, hostId: ${'$'}hostId")
        val solution: TimeTable = findById(singletonId, hostId) // Adjusted to match new findById
        scoreManager.updateScore(solution)
        return solution
    }

    @Transactional
    protected fun findById(problemId: UUID, hostIdFromRequest: UUID): TimeTable { // Changed signature
        val savedTimeslots = timeslotRepository.list("hostId = ?1", Sort.by("dayOfWeek").and("startTime").and("endTime").and("id"), hostIdFromRequest)
        val savedUsers = userRepository.list("hostId = ?1", hostIdFromRequest)
        val savedEventParts = eventPartRepository.list("hostId = ?1", Sort.by("startDate").and("endDate").and("id"), hostIdFromRequest)

        // Populate relationships for domain objects if necessary (e.g. user.workTimes)
        savedUsers.forEach { user ->
            user.workTimes = workTimeRepository.list("userId = ?1 and hostId = ?2", user.id, hostIdFromRequest)
        }
        savedEventParts.forEach { eventPart ->
            eventPart.user = savedUsers.find { it.id == eventPart.userId }
            eventPart.event = eventRepository.find("id = ?1 and hostId = ?2", eventPart.eventId, hostIdFromRequest).firstResult()
            eventPart.timeslot = savedTimeslots.find { it.id == eventPart.timeslotId }
        }

        return TimeTable(
            problemId.toString(), // Assuming TimeTable domain has an ID string field
            "TimeTable for $hostIdFromRequest", // Example name
            savedTimeslots,
            savedUsers,
            savedEventParts,
            emptyList(), // Placeholder for constraints
            null, // Initial score
            hostIdFromRequest.toString() // Assuming TimeTable domain has hostId string
        )
    }

    @Transactional
    fun callAPI(callBackUrl: String, fileKey: String, hostId: UUID) {
        val client = OkHttp()
        val clientHandler: HttpHandler = ClientFilters.BasicAuth(username, password).then(client)

        val timeslotsForHost = timeslotRepository.list("hostId = ?1", Sort.by("dayOfWeek").and("startTime").and("endTime").and("id"), hostId)
        val usersForHost = userRepository.list("hostId = ?1", hostId)
        usersForHost.forEach { user -> // Populate workTimes for each user before mapping
            user.workTimes = workTimeRepository.list("userId = ?1 and hostId = ?2", user.id, hostId)
        }
        val eventPartsForHost = eventPartRepository.list("hostId = ?1", Sort.by("startDate").and("endDate").and("id"), hostId)
        eventPartsForHost.forEach { eventPart -> // Populate nested domain objects for EventPart
             eventPart.user = usersForHost.find { it.id == eventPart.userId }
             eventPart.event = eventRepository.find("id = ?1 and hostId = ?2", eventPart.eventId, hostId).firstResult()
             eventPart.timeslot = timeslotsForHost.find { it.id == eventPart.timeslotId }
             eventPart.event?.preferredTimeRanges = preferredTimeRangeRepository.list("eventId = ?1 and hostId = ?2", eventPart.event?.id, hostId)
        }


        val timeslotDtos = timeslotsForHost.map { it.toDtoInternal() }
        val userDtos = usersForHost.map { it.toDtoInternal() }
        val userDtoMap = userDtos.associateBy { it.id!! }

        val eventIds = eventPartsForHost.mapNotNull { it.eventId }.distinct()
        val eventsDomainMap = if (eventIds.isNotEmpty()) eventRepository.list("id in ?1", eventIds).associateBy { it.id } else emptyMap()
        eventsDomainMap.values.forEach { event -> // Populate preferredTimeRanges for each event
            event.preferredTimeRanges = preferredTimeRangeRepository.list("eventId = ?1 and hostId = ?2", event.id, hostId)
        }
        val eventDtoMap = eventsDomainMap.mapValues { it.value.toDtoInternal() }

        val eventPartDtos = eventPartsForHost.map { ep ->
            ep.toDtoInternal(userDtoMap, eventDtoMap)
        }

        val timeTableDomainForScore = TimeTable(
            timeslots = timeslotsForHost,
            users = usersForHost,
            eventParts = eventPartsForHost
            // Assuming default constructor or that other fields are nullable/set by OptaPlanner
        )
        scoreManager.updateScore(timeTableDomainForScore)
        val currentScore = timeTableDomainForScore.score?.toString()

        val solutionDto = TimeTableSolutionDto(
            timeslotList = timeslotDtos,
            userList = userDtos,
            eventPartList = eventPartDtos,
            score = currentScore,
            fileKey = fileKey,
            hostId = hostId.toString()
        )

        val bodyString = objectMapper.writeValueAsString(solutionDto)

        deleteTableGivenUser(hostId)

        val request = Request(Method.POST, callBackUrl).body(bodyString).header("Content-Type", "application/json")
        val response = clientHandler(request)

        LOGGER.debug("Callback response status: ${'$'}{response.status}")
        LOGGER.debug("Callback response body: ${'$'}{response.bodyString()}")
    }

    @Transactional
    fun deleteTableGivenUser(
        hostId: UUID,
    ) {
        this.eventPartRepository.delete("hostId = ?1", hostId)
        this.workTimeRepository.delete("hostId = ?1", hostId)
        this.userRepository.delete("hostId = ?1", hostId)
        this.timeslotRepository.delete("hostId = ?1", hostId)
        this.preferredTimeRangeRepository.delete("hostId = ?1", hostId)
        this.eventRepository.delete("hostId = ?1", hostId)
    }


    suspend fun callBackAPI(singletonId: UUID, hostId: UUID, fileKey: String, delay: Long, callBackUrl: String) {
        kotlinx.coroutines.delay(delay)
        solverManager.terminateEarly(singletonId)
        callAPI(callBackUrl, fileKey, hostId)
    }

    fun solve(singletonId: UUID, hostId: UUID, fileKey: String, delay: Long, callBackUrl: String) {
        solverManager.solveAndListen(singletonId,
            { problemId -> findById(problemId, hostId) }, // Corrected findById call
            this::save)

        CoroutineScope(Dispatchers.Default).launch {
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
        this.timeslotRepository.persist(timeslots)
        this.userRepository.persist(userList)

        userList.forEach { user ->
            user.workTimes.forEach { wt ->
                wt.userId = user.id
                wt.hostId = user.hostId
            }
            if (user.workTimes.isNotEmpty()){ // Persist only if list is not empty
                workTimeRepository.persist(user.workTimes)
            }
        }

        val userIds = eventParts.mapNotNull { it.userId }.distinct()
        if (userIds.isNotEmpty()){
            val userMap = userRepository.list("id in ?1", userIds).associateBy { it.id }
            eventParts.forEach { eventPart ->
                eventPart.user = userMap[eventPart.userId]
            }
        }

        val eventsToPersist = eventParts.mapNotNull { it.event }.distinctBy { it.id }
        if (eventsToPersist.isNotEmpty()) {
            eventRepository.persist(eventsToPersist)
        }

        val preferredTimesRangesToPersist = eventsToPersist.flatMap { it.preferredTimeRanges ?: mutableListOf() }.distinctBy { pt -> listOf(pt.dayOfWeek, pt.startTime, pt.endTime, pt.eventId) }
         if (preferredTimesRangesToPersist.isNotEmpty()) {
            preferredTimeRangeRepository.persist(preferredTimesRangesToPersist)
        }

        val eventIdsForAssignment = eventParts.mapNotNull { it.eventId }.distinct()
        if (eventIdsForAssignment.isNotEmpty()){
            val eventMapForAssignment = eventRepository.list("id in ?1", eventIdsForAssignment).associateBy { it.id }
            eventParts.forEach { eventPart ->
                eventPart.event = eventMapForAssignment[eventPart.eventId]
            }
        }

        if (eventParts.isNotEmpty()) {
            eventPartRepository.persist(eventParts.filterNotNull())
        }

        solve(singletonId, hostId, fileKey, delay, callBackUrl)
    }

    @Transactional
    protected fun save(timeTable: TimeTable) {
        if (timeTable.score == null) {
            LOGGER.warn("TimeTable score is null, skipping save. This might be normal if solving was terminated early or failed.")
            return
        }
        for (eventPart in timeTable.eventPartList) {
            if (eventPart.id != null && eventPart.timeslot != null && eventPart.timeslot!!.id != null) { // Added null check for timeslot.id
                val eventPartEntity = eventPartRepository.findById(eventPart.id)
                if (eventPartEntity != null) {
                    // Make sure timeslot is a managed entity or only its ID is used for update
                    val managedTimeslot = timeslotRepository.findById(eventPart.timeslot!!.id!!)
                    if (managedTimeslot != null) {
                        eventPartEntity.timeslot = managedTimeslot
                        eventPartRepository.persist(eventPartEntity) // persist on entity for update
                    } else {
                        LOGGER.warn("Timeslot with id ${'$'}{eventPart.timeslot!!.id} not found for EventPart ${'$'}{eventPart.id}")
                    }
                } else {
                     LOGGER.warn("EventPart with id ${'$'}{eventPart.id} not found during save.")
                }
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
        LOGGER.info("Received ${'$'}action request: ${'$'}args")

        args.hostId?.let { hostId ->
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
        } ?: LOGGER.warn("${'$'}action request failed: singletonId is null. Args: ${'$'}args")
    }

    @POST
    @Path("/user/solve-day")
    fun solveDay(args: PostTableRequestBody) {
        processSolveRequest(args, false)
    }

    @POST
    @Path("/admin/solve-day")
    @RolesAllowed("admin")
    fun adminSolveDay(args: PostTableRequestBody) {
        processSolveRequest(args, true)
    }

    @DELETE
    @Path("/user/delete/{id}")
    @Transactional
    fun deleteTable(
        @PathParam("id") id: UUID,
    ) {
        deleteTableGivenUser(id)
    }

    @DELETE
    @Path("/admin/delete/{id}")
    @RolesAllowed("admin")
    @Transactional
    fun adminDeleteTable(
        @PathParam("id") id: UUID,
    ) {
        deleteTableGivenUser(id)
    }
}
