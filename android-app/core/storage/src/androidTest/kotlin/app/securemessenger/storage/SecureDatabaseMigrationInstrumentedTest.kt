package app.securemessenger.storage

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecureDatabaseMigrationInstrumentedTest {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SecureMessengerDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrationFourToFiveCreatesDisabledSignalRecordTables() {
        migrationHelper.createDatabase(TEST_DATABASE, 4).close()

        val migrated = migrationHelper.runMigrationsAndValidate(
            TEST_DATABASE,
            5,
            true,
            SecureMessengerDatabase.MIGRATION_4_5,
        )
        migrated.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name LIKE 'signal_%_records'",
        ).use { cursor ->
            assertEquals(5, cursor.count)
        }
        migrated.close()
    }

    @Test
    fun migrationFiveToSixAddsLibsignalTablesAndDropsOldKyberRows() {
        migrationHelper.createDatabase(TEST_DATABASE, 5).use { db ->
            db.execSQL(
                "INSERT INTO signal_kyber_prekey_records (kyberPreKeyId, encryptedRecord) VALUES (7, X'0102')",
            )
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            TEST_DATABASE,
            6,
            true,
            SecureMessengerDatabase.MIGRATION_5_6,
        )
        migrated.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' " +
                "AND name IN ('signal_local_identity', 'signal_kyber_base_key_uses')",
        ).use { cursor ->
            assertEquals(2, cursor.count)
        }
        // Old rows used the v1 AAD without the last-resort flag, so they must not survive.
        migrated.query("SELECT COUNT(*) FROM signal_kyber_prekey_records").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
        migrated.close()
    }

    @Test
    fun migrationSixToSevenAddsV2TablesAndKeepsExistingRows() {
        migrationHelper.createDatabase(TEST_DATABASE, 6).use { db ->
            db.execSQL(
                "INSERT INTO contacts (contactId, encryptedLocalDisplayName, queueId, deviceId, encodedPublicKey, " +
                    "fingerprint, verificationStatus) VALUES ('legacy', X'01', 'legacy-queue', 'device', X'02', 'FP', 'UNVERIFIED')",
            )
            db.execSQL(
                "INSERT INTO signal_session_records (recipientId, deviceId, encryptedRecord) VALUES ('someone', 1, X'0304')",
            )
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            TEST_DATABASE,
            7,
            true,
            SecureMessengerDatabase.MIGRATION_6_7,
        )
        migrated.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name LIKE 'v2_%'",
        ).use { cursor ->
            assertEquals(5, cursor.count)
        }
        migrated.query("SELECT COUNT(*) FROM contacts WHERE contactId = 'legacy'").use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
        }
        migrated.query("SELECT COUNT(*) FROM signal_session_records").use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
        }
        migrated.close()
    }

    /** DEC-021: the explicit legacy reset. Legacy tables are gone; everything else survives. */
    @Test
    fun migrationSevenToEightDropsLegacyTablesAndKeepsProfileSignalAndV2Rows() {
        migrationHelper.createDatabase(TEST_DATABASE, 7).use { db ->
            db.execSQL("INSERT INTO local_profile (profileId, encryptedDisplayName, deviceId) VALUES ('local', X'01', 'legacy-device')")
            db.execSQL(
                "INSERT INTO contacts (contactId, encryptedLocalDisplayName, queueId, deviceId, encodedPublicKey, " +
                    "fingerprint, verificationStatus) VALUES ('legacy', X'01', 'legacy-queue', 'device', X'02', 'FP', 'UNVERIFIED')",
            )
            db.execSQL("INSERT INTO encrypted_sessions (contactId, encryptedState, updatedAtEpochMillis) VALUES ('legacy', X'0A0B', 1)")
            db.execSQL(
                "INSERT INTO encrypted_messages (messageId, contactId, encryptedPayload, encodedAssociatedData, outgoing, " +
                    "status, createdAtEpochMillis, deliveryAttemptCount, nextRetryAtEpochMillis) " +
                    "VALUES ('m1', 'legacy', X'0C', X'0D', 1, 'PENDING', 1, 0, NULL)",
            )
            db.execSQL("INSERT INTO accepted_replay (conversationId, senderDeviceId, messageNumber, acceptedAtEpochMillis) VALUES ('c', 's', 1, 1)")
            db.execSQL("INSERT INTO signal_session_records (recipientId, deviceId, encryptedRecord) VALUES ('v2-contact', 1, X'0304')")
            db.execSQL(
                "INSERT INTO v2_contacts (contactId, encryptedDisplayName, identityKey, peerQueueId, peerRoutingTagHex, " +
                    "localRoutingTagHex, state, createdAtEpochMillis) VALUES ('v2-contact', X'01', X'05', 'peer-queue-1', 'aa', 'bb', 'ACTIVE', 1)",
            )
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            TEST_DATABASE,
            8,
            true,
            SecureMessengerDatabase.MIGRATION_7_8,
        )
        SecureMessengerDatabase.LEGACY_TABLES.forEach { table ->
            migrated.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = '$table'").use { cursor ->
                assertEquals("legacy table $table survived", 0, cursor.count)
            }
        }
        listOf("local_profile", "signal_session_records", "v2_contacts").forEach { table ->
            migrated.query("SELECT COUNT(*) FROM $table").use { cursor ->
                cursor.moveToFirst()
                assertEquals("$table lost rows", 1, cursor.getInt(0))
            }
        }
        migrated.close()
    }

    /**
     * Phase 2 exit gate: a v5 install full of prototype data migrates through every step to v8. The
     * prototype data is dropped on purpose (DEC-021); the profile, the vault's canary (DEC-023),
     * survives byte for byte, so the vault key still opens it.
     */
    @Test
    fun migrationFromFiveWithPrototypeDataReachesEightDropsItAndKeepsTheProfile() {
        val profileBlob = "0102030405060708"
        migrationHelper.createDatabase(TEST_DATABASE, 5).use { db ->
            db.execSQL("INSERT INTO local_profile (profileId, encryptedDisplayName, deviceId) VALUES ('local', X'$profileBlob', 'legacy-device')")
            db.execSQL(
                "INSERT INTO contacts (contactId, encryptedLocalDisplayName, queueId, deviceId, encodedPublicKey, " +
                    "fingerprint, verificationStatus) VALUES ('legacy', X'01', 'legacy-queue', 'device', X'02', 'FP', 'UNVERIFIED')",
            )
            db.execSQL(
                "INSERT INTO contact_devices (contactId, deviceId, queueId, encodedPublicKey, fingerprint, approvalStatus, " +
                    "firstSeenEpochMillis) VALUES ('legacy', 'device', 'legacy-queue', X'02', 'FP', 'PRIMARY_UNVERIFIED', 1)",
            )
            db.execSQL("INSERT INTO encrypted_sessions (contactId, encryptedState, updatedAtEpochMillis) VALUES ('legacy', X'0A0B', 1)")
            db.execSQL(
                "INSERT INTO encrypted_messages (messageId, contactId, encryptedPayload, encodedAssociatedData, outgoing, " +
                    "status, createdAtEpochMillis, deliveryAttemptCount, nextRetryAtEpochMillis) " +
                    "VALUES ('m1', 'legacy', X'0C', X'0D', 1, 'PENDING', 1, 0, NULL)",
            )
            db.execSQL("INSERT INTO accepted_replay (conversationId, senderDeviceId, messageNumber, acceptedAtEpochMillis) VALUES ('c', 's', 1, 1)")
            db.execSQL("INSERT INTO signal_kyber_prekey_records (kyberPreKeyId, encryptedRecord) VALUES (7, X'0102')")
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            TEST_DATABASE,
            8,
            true,
            SecureMessengerDatabase.MIGRATION_5_6,
            SecureMessengerDatabase.MIGRATION_6_7,
            SecureMessengerDatabase.MIGRATION_7_8,
        )
        SecureMessengerDatabase.LEGACY_TABLES.forEach { table ->
            migrated.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = '$table'").use { cursor ->
                assertEquals("legacy table $table survived", 0, cursor.count)
            }
        }
        migrated.query("SELECT hex(encryptedDisplayName) FROM local_profile WHERE profileId = 'local'").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals(profileBlob.uppercase(), cursor.getString(0))
        }
        listOf("signal_kyber_prekey_records", "v2_contacts", "v2_messages", "signal_session_records").forEach { table ->
            migrated.query("SELECT COUNT(*) FROM $table").use { cursor ->
                cursor.moveToFirst()
                assertEquals("$table is not empty", 0, cursor.getInt(0))
            }
        }
        migrated.close()
    }

    /**
     * DEC-024: the v2 test conversations are reset once for the relay-queue change. The profile and
     * the local libsignal identity survive byte for byte; everything tied to old contacts is gone.
     */
    @Test
    fun migrationEightToNineResetsV2ConversationsAndKeepsProfileAndLocalIdentity() {
        migrationHelper.createDatabase(TEST_DATABASE, 8).use { db ->
            db.execSQL("INSERT INTO local_profile (profileId, encryptedDisplayName, deviceId) VALUES ('local', X'0A0B', '')")
            db.execSQL("INSERT INTO signal_local_identity (singletonId, encryptedRecord) VALUES (1, X'0C0D')")
            db.execSQL("INSERT INTO signal_session_records (recipientId, deviceId, encryptedRecord) VALUES ('c', 1, X'01')")
            db.execSQL("INSERT INTO signal_identity_records (recipientId, deviceId, encryptedRecord) VALUES ('c', 1, X'02')")
            db.execSQL("INSERT INTO signal_prekey_records (preKeyId, encryptedRecord) VALUES (5, X'03')")
            db.execSQL(
                "INSERT INTO v2_contacts (contactId, encryptedDisplayName, identityKey, peerQueueId, peerRoutingTagHex, " +
                    "localRoutingTagHex, state, createdAtEpochMillis) VALUES ('c', X'01', X'05', 'v2-old-queue-1', 'aa', 'bb', 'ACTIVE', 1)",
            )
        }

        val migrated = migrationHelper.runMigrationsAndValidate(TEST_DATABASE, 9, true, SecureMessengerDatabase.MIGRATION_8_9)
        SecureMessengerDatabase.RELAY_V3_RESET_TABLES.forEach { table ->
            migrated.query("SELECT COUNT(*) FROM $table").use { cursor ->
                cursor.moveToFirst()
                assertEquals("$table kept rows", 0, cursor.getInt(0))
            }
        }
        migrated.query("SELECT hex(encryptedDisplayName) FROM local_profile").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals("0A0B", cursor.getString(0))
        }
        migrated.query("SELECT hex(encryptedRecord) FROM signal_local_identity").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals("0C0D", cursor.getString(0))
        }
        migrated.close()
    }

    /**
     * DEC-036: Room v12 adds the recording's state and upload progress to every message (none has a
     * recording yet) and an empty table for the pieces. Every row survives byte for byte.
     */
    @Test
    fun migrationElevenToTwelveAddsVoiceColumnsAndThePiecesTableAndKeepsEveryRow() {
        migrationHelper.createDatabase(TEST_DATABASE, 11).use { db ->
            db.execSQL(
                "INSERT INTO v2_messages (messageId, contactId, outgoing, kind, innerMessageIdHex, encryptedBody, status, " +
                    "createdAtEpochMillis, peerQueueId, envelope, deliveryAttemptCount, nextRetryAtEpochMillis, isRead, replyToInnerIdHex) " +
                    "VALUES ('m', 'c', 0, 'TEXT', '01', X'0C0D', 'RECEIVED', 5, NULL, NULL, 0, NULL, 0, 'ab')",
            )
        }

        val migrated = migrationHelper.runMigrationsAndValidate(TEST_DATABASE, 12, true, SecureMessengerDatabase.MIGRATION_11_12)
        migrated.query("SELECT hex(encryptedBody), isRead, replyToInnerIdHex, mediaState, mediaProgress FROM v2_messages").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals("0C0D", cursor.getString(0))
            assertEquals(0, cursor.getInt(1))
            assertEquals("ab", cursor.getString(2))
            assertTrue(cursor.isNull(3))
            assertEquals(0, cursor.getInt(4))
        }
        migrated.query("SELECT COUNT(*) FROM v2_media_chunks").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
        migrated.close()
    }

    /**
     * DEC-039: Room v13 adds "listened" to every message. Voice messages stored before it count as
     * listened (no old one gets the dot or sends "played" late); a text keeps the default. Every row
     * survives byte for byte.
     */
    @Test
    fun migrationTwelveToThirteenMarksOldVoiceMessagesListenedAndKeepsEveryRow() {
        migrationHelper.createDatabase(TEST_DATABASE, 12).use { db ->
            db.execSQL(
                "INSERT INTO v2_messages (messageId, contactId, outgoing, kind, innerMessageIdHex, encryptedBody, status, " +
                    "createdAtEpochMillis, peerQueueId, envelope, deliveryAttemptCount, nextRetryAtEpochMillis, isRead, replyToInnerIdHex, " +
                    "mediaState, mediaProgress) VALUES ('t', 'c', 0, 'TEXT', '01', X'0C0D', 'RECEIVED', 5, NULL, NULL, 0, NULL, 0, NULL, NULL, 0)",
            )
            db.execSQL(
                "INSERT INTO v2_messages (messageId, contactId, outgoing, kind, innerMessageIdHex, encryptedBody, status, " +
                    "createdAtEpochMillis, peerQueueId, envelope, deliveryAttemptCount, nextRetryAtEpochMillis, isRead, replyToInnerIdHex, " +
                    "mediaState, mediaProgress) VALUES ('v', 'c', 1, 'VOICE', '02', X'0E0F', 'SENT', 6, NULL, NULL, 1, NULL, 1, 'ab', 'READY', 3)",
            )
        }

        val migrated = migrationHelper.runMigrationsAndValidate(TEST_DATABASE, 13, true, SecureMessengerDatabase.MIGRATION_12_13)
        migrated.query("SELECT messageId, hex(encryptedBody), replyToInnerIdHex, mediaState, mediaProgress, mediaListened FROM v2_messages ORDER BY messageId").use { cursor ->
            assertEquals(2, cursor.count)
            cursor.moveToFirst()
            assertEquals("t", cursor.getString(0))
            assertEquals("0C0D", cursor.getString(1))
            assertEquals(0, cursor.getInt(5))
            cursor.moveToNext()
            assertEquals("v", cursor.getString(0))
            assertEquals("0E0F", cursor.getString(1))
            assertEquals("ab", cursor.getString(2))
            assertEquals("READY", cursor.getString(3))
            assertEquals(3, cursor.getInt(4))
            assertEquals(1, cursor.getInt(5))
        }
        migrated.close()
    }

    /**
     * DEC-035: Room v11 only adds three nullable columns. Every row survives byte for byte, every chat
     * is unpinned and unmuted, every stored text answers nothing, and the seen marks stay as they were.
     */
    @Test
    fun migrationTenToElevenAddsPinMuteAndReplyAndKeepsEveryRow() {
        migrationHelper.createDatabase(TEST_DATABASE, 10).use { db ->
            db.execSQL("INSERT INTO local_profile (profileId, encryptedDisplayName, deviceId) VALUES ('local', X'0A0B', '')")
            db.execSQL(
                "INSERT INTO v2_contacts (contactId, encryptedDisplayName, identityKey, peerQueueId, peerRoutingTagHex, " +
                    "localRoutingTagHex, state, createdAtEpochMillis, verifiedAtEpochMillis) " +
                    "VALUES ('c', X'01', X'05', 'q3-peer', 'aa', 'bb', 'ACTIVE', 1, 7)",
            )
            listOf(
                "('in', 'c', 0, 'TEXT', '01', X'0C0D', 'RECEIVED', 5, NULL, NULL, 0, NULL, 0)",
                "('out', 'c', 1, 'TEXT', '02', X'0E', 'PENDING', 6, 'q3-peer', X'0F', 1, 9, 1)",
            ).forEach { row ->
                db.execSQL(
                    "INSERT INTO v2_messages (messageId, contactId, outgoing, kind, innerMessageIdHex, encryptedBody, status, " +
                        "createdAtEpochMillis, peerQueueId, envelope, deliveryAttemptCount, nextRetryAtEpochMillis, isRead) VALUES $row",
                )
            }
        }

        val migrated = migrationHelper.runMigrationsAndValidate(TEST_DATABASE, 11, true, SecureMessengerDatabase.MIGRATION_10_11)
        migrated.query(
            "SELECT messageId, isRead, hex(encryptedBody), status, hex(envelope), replyToInnerIdHex FROM v2_messages ORDER BY messageId",
        ).use { cursor ->
            assertEquals(2, cursor.count)
            cursor.moveToFirst()
            assertEquals("in", cursor.getString(0))
            assertEquals(0, cursor.getInt(1))
            assertEquals("0C0D", cursor.getString(2))
            assertTrue(cursor.isNull(5))
            cursor.moveToNext()
            assertEquals("out", cursor.getString(0))
            assertEquals("PENDING", cursor.getString(3))
            assertEquals("0F", cursor.getString(4))
            assertTrue(cursor.isNull(5))
        }
        migrated.query(
            "SELECT verifiedAtEpochMillis, hex(encryptedDisplayName), state, pinnedAtEpochMillis, mutedUntilEpochMillis FROM v2_contacts",
        ).use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals(7, cursor.getInt(0))
            assertEquals("01", cursor.getString(1))
            assertEquals("ACTIVE", cursor.getString(2))
            assertTrue(cursor.isNull(3))
            assertTrue(cursor.isNull(4))
        }
        migrated.close()
    }

    /**
     * DEC-029: Room v10 only adds two local columns. Every row survives byte for byte, existing
     * messages count as seen (no flood of old unread badges), and no contact is marked verified.
     */
    @Test
    fun migrationNineToTenAddsTheSeenMarkAndTheVerifiedDateAndKeepsEveryRow() {
        migrationHelper.createDatabase(TEST_DATABASE, 9).use { db ->
            db.execSQL("INSERT INTO local_profile (profileId, encryptedDisplayName, deviceId) VALUES ('local', X'0A0B', '')")
            db.execSQL(
                "INSERT INTO v2_contacts (contactId, encryptedDisplayName, identityKey, peerQueueId, peerRoutingTagHex, " +
                    "localRoutingTagHex, state, createdAtEpochMillis) VALUES ('c', X'01', X'05', 'q3-peer', 'aa', 'bb', 'ACTIVE', 1)",
            )
            listOf(
                "('in', 'c', 0, 'TEXT', '01', X'0C0D', 'RECEIVED', 5, NULL, NULL, 0, NULL)",
                "('out', 'c', 1, 'TEXT', '02', X'0E', 'PENDING', 6, 'q3-peer', X'0F', 1, 9)",
            ).forEach { row ->
                db.execSQL(
                    "INSERT INTO v2_messages (messageId, contactId, outgoing, kind, innerMessageIdHex, encryptedBody, status, " +
                        "createdAtEpochMillis, peerQueueId, envelope, deliveryAttemptCount, nextRetryAtEpochMillis) VALUES $row",
                )
            }
        }

        val migrated = migrationHelper.runMigrationsAndValidate(TEST_DATABASE, 10, true, SecureMessengerDatabase.MIGRATION_9_10)
        migrated.query(
            "SELECT messageId, isRead, hex(encryptedBody), status, hex(envelope), nextRetryAtEpochMillis FROM v2_messages ORDER BY messageId",
        ).use { cursor ->
            assertEquals(2, cursor.count)
            cursor.moveToFirst()
            assertEquals("in", cursor.getString(0))
            assertEquals(1, cursor.getInt(1))
            assertEquals("0C0D", cursor.getString(2))
            cursor.moveToNext()
            assertEquals("out", cursor.getString(0))
            assertEquals(1, cursor.getInt(1))
            assertEquals("PENDING", cursor.getString(3))
            assertEquals("0F", cursor.getString(4))
            assertEquals(9, cursor.getInt(5))
        }
        migrated.query("SELECT verifiedAtEpochMillis, hex(encryptedDisplayName), state FROM v2_contacts").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertTrue(cursor.isNull(0))
            assertEquals("01", cursor.getString(1))
            assertEquals("ACTIVE", cursor.getString(2))
        }
        migrated.query("SELECT hex(encryptedDisplayName) FROM local_profile").use { cursor ->
            cursor.moveToFirst()
            assertEquals("0A0B", cursor.getString(0))
        }
        migrated.close()
    }

    private companion object {
        const val TEST_DATABASE = "signal-store-migration-test"
    }
}
