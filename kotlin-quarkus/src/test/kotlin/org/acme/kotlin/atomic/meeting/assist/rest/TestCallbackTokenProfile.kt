package org.acme.kotlin.atomic.meeting.assist.rest

import io.quarkus.test.junit.QuarkusTestProfile

class TestCallbackTokenProfile : QuarkusTestProfile {
    override fun getConfigOverrides(): Map<String, String> {
        return mapOf(
            "CALLBACK_SECRET_TOKEN" to "test-secret-token"
        )
    }
}
