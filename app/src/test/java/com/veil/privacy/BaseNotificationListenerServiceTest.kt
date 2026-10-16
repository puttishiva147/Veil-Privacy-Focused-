package com.veil.privacy

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.veil.privacy.model.ParsedNotificationInfo
import com.veil.privacy.model.SelectedContact
import com.veil.privacy.notification.BaseNotificationListenerService
import com.veil.privacy.storage.VeilDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BaseNotificationListenerServiceTest {

    private lateinit var db: VeilDatabase
    private lateinit var testService: TestListenerService

    // Concrete test implementation of abstract BaseNotificationListenerService
    class TestListenerService : BaseNotificationListenerService() {
        var lastTransformedSender: String? = null
        var lastTransformedContact: SelectedContact? = null

        fun publicFindMatching(sender: String, contacts: List<SelectedContact>): SelectedContact? {
            return findMatchingSelectedContact(sender, contacts)
        }

        override suspend fun applyPrivacyTransformation(
            sbn: android.service.notification.StatusBarNotification,
            parsed: ParsedNotificationInfo,
            matchedContact: SelectedContact
        ) {
            lastTransformedSender = parsed.sender
            lastTransformedContact = matchedContact
        }
    }

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, VeilDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        testService = TestListenerService()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun testSelectedContactDaoPersistAndRetrieve() = runBlocking {
        val contact = SelectedContact(
            contactId = "101",
            displayName = "Dr. Jane Foster",
            selectedPhoneNumber = "+1 555-019-2834",
            normalizedPhoneNumber = "+15550192834",
            source = "ANDROID_CONTACTS",
            isEnabled = true
        )

        val id = db.selectedContactDao().insert(contact)
        val retrieved = db.selectedContactDao().getByIdSync(id)

        assertNotNull(retrieved)
        assertEquals("Dr. Jane Foster", retrieved?.displayName)
        assertEquals("+15550192834", retrieved?.normalizedPhoneNumber)
    }

    @Test
    fun testFindMatchingSelectedContactByPhoneDigits() {
        val contacts = listOf(
            SelectedContact(
                id = 1,
                displayName = "Jane Foster",
                selectedPhoneNumber = "+1 (555) 019-2834",
                normalizedPhoneNumber = "+15550192834"
            )
        )

        // Notification sender with local digits
        val matchLocal = testService.publicFindMatching("5550192834", contacts)
        assertNotNull(matchLocal)
        assertEquals("Jane Foster", matchLocal?.displayName)

        // Notification sender with international format
        val matchInternational = testService.publicFindMatching("+1 555 019 2834", contacts)
        assertNotNull(matchInternational)
        assertEquals("Jane Foster", matchInternational?.displayName)
    }

    @Test
    fun testFindMatchingSelectedContactByDisplayName() {
        val contacts = listOf(
            SelectedContact(
                id = 1,
                displayName = "Marcus Wright",
                selectedPhoneNumber = "+15551234567",
                normalizedPhoneNumber = "+15551234567"
            )
        )

        val matchName = testService.publicFindMatching("marcus wright", contacts)
        assertNotNull(matchName)
        assertEquals("Marcus Wright", matchName?.displayName)

        val matchWithSpaces = testService.publicFindMatching("  Marcus   Wright  ", contacts)
        assertNotNull(matchWithSpaces)
        assertEquals("Marcus Wright", matchWithSpaces?.displayName)
    }

    @Test
    fun testNonMatchingSenderReturnsNull() {
        val contacts = listOf(
            SelectedContact(
                id = 1,
                displayName = "Elena Rostova",
                selectedPhoneNumber = "+447911123456"
            )
        )

        val nonMatch = testService.publicFindMatching("Unknown Alert", contacts)
        assertNull(nonMatch)

        val nonMatchPhone = testService.publicFindMatching("+19998887777", contacts)
        assertNull(nonMatchPhone)
    }
}
