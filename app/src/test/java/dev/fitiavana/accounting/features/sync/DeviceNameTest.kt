package dev.fitiavana.accounting.features.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class DeviceNameTest {
    @Test
    fun theSameIdAlwaysGivesTheSameName() {
        val id = "3f2b9c1e-1111-4222-8333-444455556666"

        assertEquals(DeviceName.of(id), DeviceName.of(id))
    }

    @Test
    fun aNameIsAnAdjectiveAndANounSeparatedBySpaces() {
        val name = DeviceName.of(UUID.randomUUID().toString())

        val words = name.split(" ")
        assertEquals(2, words.size)
        assertTrue(words.all { it.isNotBlank() && it[0].isUpperCase() })
    }

    @Test
    fun differentIdsMostlyGetDifferentNames() {
        val names = (1..200).map { DeviceName.of(UUID.nameUUIDFromBytes("device-$it".toByteArray()).toString()) }

        assertTrue(names.toSet().size > 150)
    }

    @Test
    fun shortIdIsTheFirstFourCharactersInUpperCase() {
        assertEquals("3F2B", DeviceName.shortId("3f2b9c1e-1111-4222-8333-444455556666"))
    }
}
