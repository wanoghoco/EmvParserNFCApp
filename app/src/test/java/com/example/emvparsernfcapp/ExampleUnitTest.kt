import com.example.emvparsernfcapp.helpers.hexStringToByteArray
import com.example.emvparsernfcapp.helpers.maskPan
import com.example.emvparsernfcapp.helpers.parseBerTlv
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EmvNFCAppTest {

    @Test
    fun `parse single TLV correctly`() {
        val hex = "9F0206000000000100"
        val (tlvs,_) = parseBerTlv(hexStringToByteArray(hex))

        assertEquals(1, tlvs.size)
        val t = tlvs[0]
        assertEquals("9F02", t.tag)
        assertEquals(6, t.length)
        assertEquals("000000000100", t.valueHex)
        assertTrue(t.malformed == null)
    }

    @Test
    fun `parse multiple TLVs correctly`() {
        val hex = "9F02060000000001005F2A020840"
        val (tlvs,_) = parseBerTlv(hexStringToByteArray(hex))
        assertEquals(2, tlvs.size)
        assertEquals("9F02", tlvs[0].tag)
        assertEquals("5F2A", tlvs[1].tag)
    }

    @Test
    fun `detect truncated TLV`() {
        val hex = "9F02060000"
        val (tlvs,_) =parseBerTlv(hexStringToByteArray(hex))

        assertEquals(1, tlvs.size)
        val t = tlvs[0]
        assertEquals("9F02", t.tag)
        // Check that the TLV is flagged as malformed
        assertTrue(t.malformed!!.contains("exceeds available TLV bytes"))
    }


    @Test
    fun `unknown tag is parsed but shows no interpretation`() {
        val hex = "DF330112"
        val (tlvs,_) =parseBerTlv(hexStringToByteArray(hex))

        assertEquals(1, tlvs.size)
        val t = tlvs[0]
        assertEquals("DF33", t.tag)
        assertEquals(null, t.interpretation) // Unknown tags
    }


    @Test
    fun `test PAN masking logic`() {

        assertEquals("1234567", maskPan("1234567"))

        assertEquals("1234567890", maskPan("1234567890"))

        val inputPan = "1234567890123456"
        val expectedMasked = "123456******3456"
        assertEquals(expectedMasked, maskPan(inputPan))

        val longPan = "12345678901234567890"
        val expected = "123456************7890"
        assertEquals(expected, maskPan(longPan))
    }
}
