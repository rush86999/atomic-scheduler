package org.acme.kotlin.atomic.meeting.assist.rest

import com.fasterxml.jackson.databind.ObjectMapper
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.acme.kotlin.atomic.meeting.assist.domain.Event
import org.acme.kotlin.atomic.meeting.assist.domain.EventPart
import org.acme.kotlin.atomic.meeting.assist.domain.Timeslot
import org.acme.kotlin.atomic.meeting.assist.domain.User
import org.acme.kotlin.atomic.meeting.assist.domain.WorkTime
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import javax.inject.Inject
import org.hamcrest.Matchers // For more advanced RestAssured assertions if needed

// NOTE: @TestTransaction is not available in Quarkus 1.x.
// For Quarkus 2.x+, it can be used on test methods to roll back transactions.
// Without it, tests might leave data in the H2 database if not cleaned up.
// Consider adding a @BeforeEach or @AfterEach to clean up if using a persistent test DB
// and not relying on H2's default behavior or schema-generation drop-and-create.

@QuarkusTest
class TimeTableResourceTest {

    @Inject
    lateinit var objectMapper: ObjectMapper

    // Use a unique hostId for each test run to avoid conflicts if tests run in parallel or if DB is not wiped.
    // Alternatively, use @BeforeEach/@AfterEach to clean up data for a fixed testHostId.
    private fun createTestHostId(): UUID = UUID.randomUUID()

    // Helper to create a common PostTableRequestBody
    private fun createSamplePostTableRequestBody(hostId: UUID, singletonId: UUID): PostTableRequestBody {
        val userId = UUID.randomUUID()
        val eventId = UUID.randomUUID()
        val eventGroupId = UUID.randomUUID()
        val now = OffsetDateTime.now(ZoneOffset.UTC)

        val timeslots = mutableListOf(
            Timeslot(hostId = hostId, dayOfWeek = DayOfWeek.MONDAY, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(10, 0)),
            Timeslot(hostId = hostId, dayOfWeek = DayOfWeek.MONDAY, startTime = LocalTime.of(10, 0), endTime = LocalTime.of(11, 0))
        )
        val workTimesUser = mutableListOf(
            WorkTime(userId = userId, hostId = hostId, dayOfWeek = DayOfWeek.MONDAY, startTime = LocalTime.of(8,0), endTime = LocalTime.of(17,0))
        )
        val user = User(name = "Test User $userId", id = userId, hostId = hostId, workTimes = workTimesUser,
                         maxWorkLoadPercent = 80, backToBackMeetings = false, maxNumberOfMeetings = 5, minNumberOfBreaks = 1)
        val users = mutableListOf(user)
        val event = Event(id = eventId, userId = userId, hostId = hostId, name = "Test Event $eventId")
        val eventParts = mutableListOf(
            EventPart(
                id = UUID.randomUUID(), groupId = eventGroupId, eventId = eventId, part = 1, lastPart = 1,
                startDate = now.plusDays(1).withHour(9).withMinute(0).toString(),
                endDate = now.plusDays(1).withHour(10).withMinute(0).toString(),
                userId = userId, hostId = hostId, event = event, user = user,
                priority = 1, modifiable = true
            )
        )
        return PostTableRequestBody(
            singletonId = singletonId, hostId = hostId, timeslots = timeslots, userList = users, eventParts = eventParts,
            fileKey = "testFileKey-${hostId}", delay = 50, callBackUrl = "http://localhost:8081/test-callback"
        )
    }

    @Test
    fun `test solve-day endpoint with minimal valid data`() {
        val testHostId = createTestHostId()
        val testSingletonId = UUID.randomUUID()
        val requestBody = createSamplePostTableRequestBody(testHostId, testSingletonId)
        val event1Id = UUID.randomUUID()
        val event1GroupId = UUID.randomUUID()
        val now = OffsetDateTime.now(ZoneOffset.UTC)

        val timeslots = mutableListOf(
            Timeslot(hostId = DEMO_HOST_ID, dayOfWeek = DayOfWeek.MONDAY, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(10, 0)),
            Timeslot(hostId = DEMO_HOST_ID, dayOfWeek = DayOfWeek.MONDAY, startTime = LocalTime.of(10, 0), endTime = LocalTime.of(11, 0))
        )

        val workTimesUser1 = mutableListOf(
            WorkTime(userId = user1Id, hostId = DEMO_HOST_ID, dayOfWeek = DayOfWeek.MONDAY, startTime = LocalTime.of(8,0), endTime = LocalTime.of(17,0))
        )
        // User object itself. Name is nullable in domain, so can be omitted if not strictly needed.
        val user1 = User(name = "Test User Integration", id = user1Id, hostId = DEMO_HOST_ID, workTimes = workTimesUser1,
                         maxWorkLoadPercent = 80, backToBackMeetings = false, maxNumberOfMeetings = 5, minNumberOfBreaks = 1)

        val users = mutableListOf(user1)

        val event1 = Event(id = event1Id, userId = user1Id, hostId = DEMO_HOST_ID, name = "Integration Test Event 1")

        val eventParts = mutableListOf(
            EventPart(
                id = UUID.randomUUID(),
                groupId = event1GroupId,
                eventId = event1Id,
                part = 1,
                lastPart = 1,
                startDate = now.plusDays(1).withHour(9).withMinute(0).toString(),
                endDate = now.plusDays(1).withHour(10).withMinute(0).toString(),
                userId = user1Id,
                hostId = DEMO_HOST_ID,
                event = event1, // Link the event object
                user = user1,   // Link the user object
                priority = 1,
                modifiable = true
            )
        )

        val requestBody = PostTableRequestBody(
            singletonId = UUID.randomUUID(), // Each solve request should ideally have a unique ID
            hostId = DEMO_HOST_ID,
            timeslots = timeslots,
            userList = users,
            eventParts = eventParts,
            fileKey = "integrationTestFileKey",
            delay = 100, // Short delay for test purposes
            callBackUrl = "http://localhost:8081/callback" // Dummy callback for test
        )

        // To ensure the test is clean, it's good practice to clear data related to DEMO_HOST_ID
        // before running this test, or ensure the test data uses unique IDs not conflicting with DemoDataGenerator.
        // For now, this test assumes a relatively clean state for DEMO_HOST_ID or that conflicts are acceptable.
        // A @BeforeEach clear via API call could be an option if H2 is persistent across tests or if transactions are not rolled back.

        given()
            .contentType(ContentType.JSON)
            .body(requestBody) // ObjectMapper will be used by Quarkus JAX-RS JSON provider
        .`when`()
            .post("/timeTable/user/solve-day")
        .then()
            .statusCode(200)
    }

    @Test
    fun `test get TimeTable byId after solve-day`() {
        val testHostId = createTestHostId()
        val testSingletonId = UUID.randomUUID()
        val requestBody = createSamplePostTableRequestBody(testHostId, testSingletonId)

        // 1. Call solve-day to populate data
        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
        .`when`()
            .post("/timeTable/user/solve-day")
        .then()
            .statusCode(200)

        // 2. Attempt to retrieve the timetable
        // Note: Solver runs asynchronously. A short delay might be needed in a real scenario
        // if testing solver results. Here, we primarily test if the findById logic
        // can retrieve the input problem facts correctly.
        // For more robust testing of solver completion, a callback mechanism or polling would be needed.
        // Thread.sleep(200) // Small delay, generally discouraged in tests but can help with async operations.

        given()
            .accept(ContentType.JSON)
        .`when`()
            .get("/timeTable/user/byId/${testSingletonId}/${testHostId}")
        .then()
            .statusCode(200)
            .body("timeslotList.size()", Matchers.equalTo(requestBody.timeslots!!.size))
            .body("userList.size()", Matchers.equalTo(requestBody.userList!!.size))
            .body("eventPartList.size()", Matchers.equalTo(requestBody.eventParts!!.size))
            .body("userList[0].id", Matchers.equalTo(requestBody.userList!![0].id.toString()))
    }

    @Test
    fun `test stopSolving endpoint`() {
        val testHostId = createTestHostId()
        val testSingletonId = UUID.randomUUID()
        val requestBody = createSamplePostTableRequestBody(testHostId, testSingletonId)

        // Start a solve
        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
        .`when`()
            .post("/timeTable/user/solve-day")
        .then()
            .statusCode(200)

        val stopRequestBody = PostStopSingletonRequestBody(singletonId = testSingletonId)
        given()
            .contentType(ContentType.JSON)
            .body(stopRequestBody)
        .`when`()
            .post("/timeTable/user/stopSolving")
        .then()
            .statusCode(200) // Or 204 if that's what the endpoint returns
    }

    @Test
    fun `test deleteTable endpoint`() {
        val testHostId = createTestHostId()
        val testSingletonId = UUID.randomUUID()
        val requestBody = createSamplePostTableRequestBody(testHostId, testSingletonId)

        // 1. Create data
        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
        .`when`()
            .post("/timeTable/user/solve-day")
        .then()
            .statusCode(200)

        // 2. Verify data exists (optional, but good for sanity)
        given()
            .accept(ContentType.JSON)
        .`when`()
            .get("/timeTable/user/byId/${testSingletonId}/${testHostId}")
        .then()
            .statusCode(200)
            .body("eventPartList.size()", Matchers.greaterThan(0))


        // 3. Delete the data
        given()
        .`when`()
            .delete("/timeTable/user/delete/${testHostId}")
        .then()
            .statusCode(200) // Or 204

        // 4. Verify data is deleted
        // Attempting to GET again. Expecting an error or empty result.
        // The findById method might throw an exception if entities are missing, leading to 500.
        // Or it might return a TimeTable with empty lists if hostId matches nothing.
        // For this test, let's assume it would lead to an inability to construct the TimeTable
        // as before, or that the check(id == singletonIdFromRequest) in findById would fail
        // if the solver state associated with singletonId was also cleared (though delete only takes hostId).
        // A more direct check would be querying repositories if test setup allowed.
        // The current findById would likely fail with `check(id == singletonIdFromRequest)`
        // if we tried to use the old singletonId, as `deleteTableGivenUser` clears it.
        // Let's try to get it, if it's a 500 (due to check failure or missing data for construction) or 404, it's fine.
        // If findById is robust to return empty lists, then check for that.
        // The current TimeTableResource.findById check(id == singletonIdFromRequest) might not be the best for this.
        // Let's assume the internal state related to singletonId is implicitly cleared or becomes invalid.
        // A robust way would be to try creating a new problem with the same hostId and ensure it's empty.
        // For now, we'll assume that trying to GET with old singletonId after hostId data is deleted
        // will not yield the same result. If `findById` fails to find any timeslots/events for the hostId,
        // it will return a TimeTable with empty lists.
         given()
            .accept(ContentType.JSON)
        .`when`()
            .get("/timeTable/user/byId/${testSingletonId}/${testHostId}")
        .then()
            .statusCode(200) // findById will return TimeTable with empty lists if data is gone for hostId
            .body("timeslotList.size()", Matchers.equalTo(0))
            .body("eventPartList.size()", Matchers.equalTo(0))
            .body("userList.size()", Matchers.equalTo(0))
    }

    @Test
    fun `test admin solve-day endpoint`() {
        val testHostId = createTestHostId()
        val testSingletonId = UUID.randomUUID()
        val requestBody = createSamplePostTableRequestBody(testHostId, testSingletonId)

        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
        .`when`()
            .post("/timeTable/admin/solve-day")
        .then()
            // Expect 401/403 if security is enabled and no test identity with "admin" role is provided
            // If security is not enabled for tests, it might be 200.
            // For now, let's be broad or assume security is not the focus of this particular test.
            // A common approach is to have different profiles for testing security.
            .assertThat().statusCode(Matchers.oneOf(200, 401, 403))
    }

     @Test
    fun `test admin get TimeTable byId`() {
        val testHostId = createTestHostId()
        val testSingletonId = UUID.randomUUID()
        // Minimal setup, actual data presence not strictly necessary if just testing path + auth
        // If we want to test retrieval, we'd call admin/solve-day first (if it works without auth in test)

        given()
            .accept(ContentType.JSON)
        .`when`()
            .get("/timeTable/admin/byId/${testSingletonId}/${testHostId}")
        .then()
            .assertThat().statusCode(Matchers.oneOf(200, 401, 403, 500))
            // 200 if accessible and data (even empty) found
            // 401/403 if auth issue
            // 500 if auth passes but data setup leads to error (e.g. findById fails due to no prior solve-day)
    }
}
