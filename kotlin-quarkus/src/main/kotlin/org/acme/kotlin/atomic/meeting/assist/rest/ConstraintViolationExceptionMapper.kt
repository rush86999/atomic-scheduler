package org.acme.kotlin.atomic.meeting.assist.rest

import javax.validation.ConstraintViolationException
import javax.ws.rs.core.Response
import javax.ws.rs.ext.ExceptionMapper
import javax.ws.rs.ext.Provider

@Provider
class ConstraintViolationExceptionMapper : ExceptionMapper<ConstraintViolationException> {

    override fun toResponse(exception: ConstraintViolationException): Response {
        val errors = exception.constraintViolations.map { violation ->
            // Property path might be complex for nested objects, e.g., "userList[0].email"
            // Simple path: violation.propertyPath.toString().split(".").last()
            object {
                val property = violation.propertyPath.toString()
                val message = violation.message
                val invalidValue = violation.invalidValue?.toString() ?: "null"
            }
        }

        val errorResponse = object {
            val message = "Validation failed"
            val errors = errors
        }

        return Response.status(Response.Status.BAD_REQUEST).entity(errorResponse).build()
    }
}
