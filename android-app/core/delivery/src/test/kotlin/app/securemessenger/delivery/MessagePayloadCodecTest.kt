package app.securemessenger.delivery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessagePayloadCodecTest {
    @Test
    fun `text payload round trips and legacy raw text remains readable`() {
        val encoded = MessagePayloadCodec.encodeText("hello")

        assertEquals(MessagePayload.Text("hello"), MessagePayloadCodec.decode(encoded))
        assertEquals(MessagePayload.Text("legacy"), MessagePayloadCodec.decode("legacy".toByteArray()))
    }

    @Test
    fun `delivery receipt payload contains no plaintext message body`() {
        val receipt = DeliveryReceipt(
            originalMessageId = "message-id",
            conversationId = "conversation",
            originalSenderDeviceId = "sender-device",
            originalRecipientDeviceId = "recipient-device",
            timestampEpochMillis = 1_700_000_000_000L,
        )
        val encoded = MessagePayloadCodec.encodeDeliveryReceipt(receipt)

        assertEquals(MessagePayload.Receipt(receipt), MessagePayloadCodec.decode(encoded))
        assertFalse(encoded.toString(Charsets.UTF_8).contains("hello plaintext"))
        assertTrue(MessagePayloadCodec.decode(encoded) is MessagePayload.Receipt)
    }
}
