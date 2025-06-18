package org.acme.kotlin.atomic.meeting.assist.domain
import org.acme.kotlin.atomic.meeting.assist.domain.EventType // Added import
import org.hibernate.annotations.Fetch
import org.hibernate.annotations.FetchMode
import org.optaplanner.core.api.domain.lookup.PlanningId
import java.util.*
import javax.persistence.*
import javax.validation.Valid
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull

@Entity
@Table(name="event_optaplanner", indexes = [
    Index(name = "sk_userId_event_optaplanner", columnList = "userId"),
    Index(name = "sk_hostId_event_optaplanner", columnList = "hostId"),
])
class Event {
    @PlanningId
    @Id
    @field:NotBlank(message = "Event id must not be blank")
    lateinit var id: String

    @field:NotNull(message = "Event userId must not be null")
    lateinit var userId: UUID

    @field:NotNull(message = "Event hostId must not be null")
    lateinit var hostId: UUID

    @field:NotNull(message = "Event eventType must not be null") // Added field
    lateinit var eventType: EventType // Added field

    @OneToMany(fetch = FetchType.LAZY) // Changed to LAZY
    // @Fetch(value = FetchMode.SUBSELECT) // SUBSELECT is often used with EAGER, less critical for LAZY
    @JoinColumn(name = "eventId", referencedColumnName = "id", insertable = false, updatable = false)
    @field:Valid // Validate each PreferredTimeRange in the list
    var preferredTimeRanges: MutableList<PreferredTimeRange>? = null // List itself can be null or empty


    // No-arg constructor required for Hibernate and Opta Planner
    constructor()

    constructor(id: String, preferredTimeRanges: MutableList<PreferredTimeRange>?, userId: UUID, hostId: UUID, eventType: EventType) { // Added eventType to constructor
        this.id = id
        this.preferredTimeRanges = preferredTimeRanges
        this.userId = userId
        this.hostId = hostId
        this.eventType = eventType // Assigned eventType
    }

}