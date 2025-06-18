package org.acme.kotlin.atomic.meeting.assist.domain

import org.hibernate.type.descriptor.WrapperOptions
import org.hibernate.type.descriptor.java.AbstractTypeDescriptor
import org.hibernate.type.descriptor.java.ImmutableMutabilityPlan
import java.time.MonthDay
import java.time.format.DateTimeParseException
import org.jboss.logging.Logger

class MonthDayDescriptor : AbstractTypeDescriptor<MonthDay>(MonthDay::class.java, ImmutableMutabilityPlan()) {

    companion object {
        private val LOGGER = Logger.getLogger(MonthDayDescriptor::class.java)
    }

    override fun toString(value: MonthDay?): String? {
        return value?.toString() // Standard format: --MM-DD
    }

    override fun fromString(string: String?): MonthDay? {
        return if (string != null) {
            try {
                MonthDay.parse(string)
            } catch (e: DateTimeParseException) {
                LOGGER.warn("Failed to parse MonthDay from string: '$string'", e)
                null // Or throw a specific application exception
            }
        } else {
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <X> unwrap(value: MonthDay?, type: Class<X>, options: WrapperOptions): X? {
        if (value == null) {
            return null
        }
        if (String::class.java.isAssignableFrom(type)) {
            return value.toString() as X
        }
        throw unknownUnwrap(type)
    }

    override fun <X> wrap(value: X?, options: WrapperOptions): MonthDay? {
        if (value == null) {
            return null
        }
        if (value is String) {
            return try {
                MonthDay.parse(value) // Standard format: --MM-DD
            } catch (e: DateTimeParseException) {
                LOGGER.warn("Failed to parse MonthDay from string value: '$value'", e)
                // Depending on strictness, could throw an exception or return null
                null
            }
        }
        // Potentially handle other types if the database might return them, e.g., java.sql.Date
        // if (value is java.sql.Date) {
        //     return MonthDay.from((value).toLocalDate());
        // }
        // if (value is java.time.LocalDate) {
        //     return MonthDay.from(value);
        // }
        throw unknownWrap(value::class.java)
    }
}