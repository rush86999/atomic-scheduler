package org.acme.kotlin.atomic.meeting.assist.domain

import org.hibernate.type.descriptor.WrapperOptions
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.MonthDay
import java.time.format.DateTimeParseException

class MonthDayDescriptorTest {

    private val descriptor = MonthDayDescriptor()
    private val mockOptions: WrapperOptions? = null // Can be mocked if specific options are needed

    @Test
    fun `test toString with non-null MonthDay`() {
        val monthDay = MonthDay.of(12, 25)
        assertEquals("--12-25", descriptor.toString(monthDay))
    }

    @Test
    fun `test toString with null MonthDay`() {
        assertNull(descriptor.toString(null))
    }

    @Test
    fun `test fromString with valid string`() {
        val monthDayString = "--12-25"
        val expectedMonthDay = MonthDay.of(12, 25)
        assertEquals(expectedMonthDay, descriptor.fromString(monthDayString))
    }

    @Test
    fun `test fromString with invalid string format`() {
        val invalidString = "12-25" // Missing leading --
        assertNull(descriptor.fromString(invalidString))
    }

    @Test
    fun `test fromString with unparseable string`() {
        val unparseableString = "--AB-CD"
        assertNull(descriptor.fromString(unparseableString))
    }

    @Test
    fun `test fromString with null string`() {
        assertNull(descriptor.fromString(null))
    }

    @Test
    fun `test unwrap with non-null MonthDay to String`() {
        val monthDay = MonthDay.of(3, 15)
        val unwrapped = descriptor.unwrap(monthDay, String::class.java, mockOptions)
        assertEquals("--03-15", unwrapped)
    }

    @Test
    fun `test unwrap with null MonthDay`() {
        val unwrapped = descriptor.unwrap(null, String::class.java, mockOptions)
        assertNull(unwrapped)
    }

    @Test
    fun `test unwrap to incompatible class`() {
        val monthDay = MonthDay.of(3, 15)
        assertThrows(IllegalArgumentException::class.java) { // Expecting unknownUnwrap, which results in IllegalArgumentException
            descriptor.unwrap(monthDay, Int::class.java, mockOptions)
        }
    }

    @Test
    fun `test wrap with valid MonthDay string`() {
        val monthDayString = "--07-04"
        val expectedMonthDay = MonthDay.of(7, 4)
        val wrapped = descriptor.wrap(monthDayString, mockOptions)
        assertEquals(expectedMonthDay, wrapped)
    }

    @Test
    fun `test wrap with null value`() {
        val wrapped = descriptor.wrap(null, mockOptions)
        assertNull(wrapped)
    }

    @Test
    fun `test wrap with invalid MonthDay string format`() {
        val invalidString = "07-04" // Missing --
        val wrapped = descriptor.wrap(invalidString, mockOptions)
        assertNull(wrapped) // Expecting null due to try-catch in wrap
    }

    @Test
    fun `test wrap with unparseable MonthDay string`() {
        val unparseableString = "--XX-YY"
        val wrapped = descriptor.wrap(unparseableString, mockOptions)
        assertNull(wrapped) // Expecting null due to try-catch in wrap
    }

    @Test
    fun `test wrap with incompatible type`() {
        val incompatibleValue = 12345
        assertThrows(IllegalArgumentException::class.java) { // Expecting unknownWrap
            descriptor.wrap(incompatibleValue, mockOptions)
        }
    }
}
