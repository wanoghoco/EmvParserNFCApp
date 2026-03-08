package com.example.emvparsernfcapp.helpers
import android.content.Context
import com.example.emvparsernfcapp.model.Tlv
import org.jpos.tlv.TLVList
import org.jpos.tlv.TLVMsg
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date


var appUnpredictableNumber="";

fun parseBerTlv(
    bytes: ByteArray,
    start: Int = 0
): Pair<List<Tlv>, Int> {

    val tlvs = mutableListOf<Tlv>()
    var index = start

    while (index < bytes.size) {
        try {
            if (index >= bytes.size) break
            val tagBytes = mutableListOf<Byte>()
            tagBytes.add(bytes[index])
            index++

            // Multi-byte tag (0x1F)
            // this check for most signficant byte[MSB] for 1
            if ((tagBytes[0].toInt() and 0x1F) == 0x1F) {
                while (index < bytes.size) {
                    val b = bytes[index]
                    tagBytes.add(b)
                    index++
                    if ((b.toInt() and 0x80) == 0) break
                }
            }

            val tagHex = tagBytes.joinToString("") { "%02X".format(it) }


            if (index >= bytes.size) {
                tlvs.add(
                    Tlv(
                        tag = tagHex,
                        length = 0,
                        valueHex = "",
                        interpretation = "",
                        malformed = "Missing length byte"
                    )
                )
                break
            }
            // this convert to unassigned integers
            var len = bytes[index].toInt() and 0xFF
            index++

            // this check for most signficant byte[MSB] for 0
            if ((len and 0x80) != 0) {
                val lenCount = len and 0x7F
                if (index + lenCount > bytes.size) {
                    tlvs.add(
                        Tlv(
                            tag = tagHex,
                            length = 0,
                            valueHex = "",
                            interpretation = "",
                            malformed = "Length field extends beyond TLV data"
                        )
                    )
                    break
                }

                len = 0
                repeat(lenCount) {
                    len = (len shl 8) or (bytes[index].toInt() and 0xFF)
                    index++
                }
            }

            if (index + len > bytes.size) {
                tlvs.add(
                    Tlv(
                        tag = tagHex,
                        length = len,
                        valueHex = bytesToHex(bytes.copyOfRange(index, bytes.size)),
                        interpretation = "",
                        malformed = "Declared length ($len) exceeds available TLV bytes"
                    )
                )
                break
            }

            val value = bytes.copyOfRange(index, index + len)
            index += len

            //Is Bit 5 of the tag byte set to 1
            //This means its value field is a sequence of other encoded data elements (like a list, sequence, or set)
            val isConstructed = (tagBytes[0].toInt() and 0x20) != 0

            val children = if (isConstructed) {
                // Recursively parse children
                parseBerTlv(value, 0).first
            } else emptyList()


            tlvs.add(
                Tlv(
                    tag = tagHex,
                    length = len,
                    valueHex = bytesToHex(value),
                    interpretation = interpretEmv(tagHex, bytesToHex(value)),
                    malformed = null,
                    children = children,
                    isConstructed = isConstructed
                )
            )
        }
        catch (e: Exception) {
            tlvs.add(
                Tlv(
                    tag = "",
                    length = 0,
                    valueHex = "",
                    interpretation = "",
                    malformed = "Parser crash: ${e.message}",
                    children = emptyList(),
                    isConstructed = false
                )
            )
            break
        }
    }

    return tlvs to index
}


private fun parseMsgRecursive(msg: TLVMsg, out: MutableList<Tlv>) {
    val tag = msg.tag.toString(16).uppercase().padStart(2, '0')
    val valueHex = bytesToHex(msg.value)
    val interpretation = interpretEmv(tag, valueHex)

    out.add(Tlv(
        tag = tag,
        length = msg.value.size, // <- CORRECTED: Use the actual byte array size of the value
        valueHex = valueHex,
        interpretation = interpretation
    ))
    val firstByte = msg.tag.toInt()
    if ((firstByte and 0x20) == 0x20) { // constructed tag
        val childList = TLVList()
        try {
            childList.unpack(msg.value)
            childList.tags.forEach { child ->
                parseMsgRecursive(child, out)
            }
        } catch (_: Exception) {

        }
    }
}


private fun interpretEmv(tag: String, valueHex: String): String ?{
    val emvTagNames = mapOf(
        "9F02" to "Amount, Authorized",
        "5A"   to "PAN",
        "57"   to "Track 2 Equivalent Data",
        "9F26" to "ARQC",
        "9F10" to "Issuer Data",
        "84"   to "AID",
        "9F02" to "Amount, Authorized (Numeric)",
        "9F03" to "Amount, Other (Numeric)",
        "9F1A" to "Terminal Country Code",
        "9A" to "Transaction Date",
        "9C" to "Transaction Type",
        "5F2A" to "Transaction Currency Code",
        "5F34" to "Application PAN Sequence Number",
        "82" to "Application Interchange Profile",
        "95" to "Terminal Verification Results",
        "9F36" to "Application Transaction Counter (ATC)",
        "9F1E" to "Interface Device (IFD) Serial Number",
        "9F10" to "Issuer Application Data",
        "9F26" to "Application Cryptogram (AC)",
        "9F27" to "Cryptogram Information Data (CID)",
        "9F37" to "Unpredictable Number",
        "5F24" to "Application Expiration Date",
        "5F34 " to "(PAN) Sequence Number",
        "8E" to "CVM",
        "5F28" to "Issuer Country Code",
        "9F42" to "Issuer Currency Code",
        "90" to "Issuer Public Key Certificate"

    )

    return when (tag) {
        "9F02" -> {
            val digits = bcdToDigits(valueHex)
            val amount = digits.toLongOrNull() ?: 0L
            "Amount: %.2f".format(amount / 100.0)
        }
        else -> {

            emvTagNames[tag]?.let { "($it)" } ?: null
        }
    }
}

private fun bcdToDigits(hex: String): String {
    val sb = StringBuilder()
    hex.chunked(2).forEach {
        val v = it.toInt(16)
        val high = (v shr 4) and 0xF
        val low = v and 0xF
        if (high != 0xF) sb.append(high)
        if (low != 0xF) sb.append(low)
    }
    return sb.toString()
}


public fun hexStringToByteArray(hex: String): ByteArray {
    val clean = hex.replace("\\s+".toRegex(), "")
    return ByteArray(clean.length / 2) { i ->
        ((clean[i * 2].digitToInt(16) shl 4) + clean[i * 2 + 1].digitToInt(16)).toByte()
    }
}


public fun bytesToHex(bytes: ByteArray): String {
    return bytes.joinToString("") { "%02X".format(it) }
}


fun parsePdol(pdol: ByteArray): List<Pair<String, Int>> {
    val result = mutableListOf<Pair<String, Int>>()
    var i = 0
    while (i < pdol.size) {
        var tag = pdol[i].toInt() and 0xFF
        i++
        if (tag and 0x1F == 0x1F) {
            tag = (tag shl 8) or (pdol[i].toInt() and 0xFF)
            i++
        }
        val tagHex = "%02X".format(tag)
        val length = pdol[i].toInt() and 0xFF
        i++
        result.add(tagHex to length)
    }
    return result
}


fun formatEmvAmount(amount: Int): String {
    val formattedAmount=amount*100;
    require(formattedAmount >= 0) { "Amount must be non-negative" }
    return formattedAmount.toString().padStart(12, '0')
}
fun buildARC(approved: Boolean = true): ByteArray {
    return if (approved) {
        byteArrayOf(0x30, 0x30) // Approved
    } else {
        byteArrayOf(0x05, 0x00) // Declined
    }
}
fun buildIAD(simulateApproved: Boolean = true): ByteArray {
    return if (simulateApproved) {
        ByteArray(10) { 0x00.toByte() } // 10 bytes zeros
    } else {
        // you can fill with custom data if simulating real issuer
        ByteArray(10) { 0xFF.toByte() }
    }
}

fun buildCustomerExclusiveData(): ByteArray {
    return ByteArray(20) { 0x00.toByte() }
}


fun getTransactionTime(): ByteArray {
    val calendar = Calendar.getInstance()
    val hh = calendar.get(Calendar.HOUR_OF_DAY)
    val mm = calendar.get(Calendar.MINUTE)
    val ss = calendar.get(Calendar.SECOND)

    fun toBcd(value: Int): Byte {
        return ((value / 10) shl 4 or (value % 10)).toByte()
    }

    return byteArrayOf(
        toBcd(hh),
        toBcd(mm),
        toBcd(ss)
    )
}


fun getTransactioinSequenceCounter(context: Context): ByteArray {
    val prefs = context.getSharedPreferences("emv_prefs", Context.MODE_PRIVATE)
    val currentTsc = prefs.getInt("tsc", 0)
    val nextTsc = currentTsc + 1
    prefs.edit().putInt("tsc", nextTsc).apply()
    val value = ByteBuffer.allocate(4).putInt(nextTsc).array()
    return byteArrayOf(
        0x9F.toByte(),
        0x41.toByte(),
        0x04,
        value[0],
        value[1],
        value[2],
        value[3]
    )
}

fun buildTerminalCapabilitiesTlv(): ByteArray {
    val value = hexStringToByteArray("E008C8")
    return byteArrayOf(
        0x9F.toByte(),
        0x33.toByte(),
        0x03,
        value[0], value[1], value[2]
    )
}

fun buildTerminalTypeTlv(): ByteArray {
    val terminalType: Byte = 0x01

    return byteArrayOf(
        0x9F.toByte(),
        0x35.toByte(),
        0x01,
        terminalType
    )
}

fun buildCvmResultTlv(cvmCode: Byte, conditionCode: Byte, resultCode: Byte): ByteArray {
    return byteArrayOf(
        0x9F.toByte(),
        0x34.toByte(),
        0x03,
        cvmCode,
        conditionCode,
        resultCode
    )
}


fun getTerminalGeneratedData(context: Context,isMaster: Boolean=false):String{

    var data="";
    data+=bytesToHex(buildTerminalCapabilitiesTlv())
    data+=bytesToHex(getTransactioinSequenceCounter(context));
  if(!isMaster){
      data+=bytesToHex(buildTerminalTypeTlv());
      data+=bytesToHex(buildCvmResultTlv(0x1F, 0x00, 0x00));
  }
    return data;
}

fun buildPdolAsTlv(pdol: ByteArray, amount: Int): ByteArray {

    val tagValues = mapOf(
        "C7" to hexStringToByteArray("3400400298"),
        "9F66" to hexStringToByteArray("E0000000"), // TTQ
        "9F02" to hexStringToByteArray(formatEmvAmount(amount)),   // Amount
        "9F03" to hexStringToByteArray("000000000000"),   // Other Amount
        "9F1A" to hexStringToByteArray(getCurrencyCode()),           // Country
        "95"   to hexStringToByteArray("0000008801"),     // TVR
       // "95"   to hexStringToByteArray("0000008801"),     // TVR
        "5F2A" to hexStringToByteArray(getCurrency()),           // Currency
        "9A"   to hexStringToByteArray(getEmvDate()),         // Date
        "9C"   to hexStringToByteArray("00"),             // Txn type
        "9F37" to hexStringToByteArray(generateUnpredictableNumber()),
        "9F35" to hexStringToByteArray("22"),   // terminal type
        "9F45" to hexStringToByteArray("0000"),   // data authentication code
        "9F4C" to hexStringToByteArray("0000000000000000"),    //ICC Dynamic Number
        "9F34" to hexStringToByteArray("3F0001"),    // Cardholder Verification Method (CVM)
        "9F21" to getTransactionTime(),   // transaction time
        "9F7C" to buildCustomerExclusiveData(), // customer exclusuive data
        "91"   to buildIAD(true),// issuer authentication data
        "8A"   to buildARC(true)) // authorization resposne code


    val pdolItems = parsePdol(pdol)
    val tlv = mutableListOf<Byte>()

    for ((tag, length) in pdolItems) {

        val value = tagValues[tag] ?: ByteArray(length) { 0x00 }

        if (value.size != length) {
            throw IllegalArgumentException("Tag $tag expects $length bytes, got ${value.size}")
        }

        // TAG
        tlv.addAll(hexStringToByteArray(tag).toList())

        // LENGTH (PDOL lengths are always <= 127, so 1-byte length is enough)
        tlv.add(length.toByte())

        // VALUE
        tlv.addAll(value.toList())
    }

    return tlv.toByteArray()
}


fun getCurrencyCode():String{
    return "0566";
}

fun getCurrency():String{
    return "0566";
}
fun getEmvDate(): String {
    val cal = java.util.Calendar.getInstance()
    val year = cal.get(java.util.Calendar.YEAR) % 100       // last 2 digits
    val month = cal.get(java.util.Calendar.MONTH) + 1        // 1–12
    val day = cal.get(java.util.Calendar.DAY_OF_MONTH)

    val hex = String.format("%02d%02d%02d", year, month, day)
    return hex;
}

fun generateUnpredictableNumber(): String {
    if(!appUnpredictableNumber.isEmpty()){
        return appUnpredictableNumber;
    }
    val random = java.security.SecureRandom()
    val un = ByteArray(4)
    random.nextBytes(un)
    appUnpredictableNumber= bytesToHex(un)
    return appUnpredictableNumber;
}

fun divideArray(aflBytes: ByteArray, chunkSize: Int): List<ByteArray> {
    return aflBytes.toList()
        .chunked(chunkSize)
        .map { it.toByteArray() }
}

fun getCardPan(track2Data: String?): String {
    if (track2Data == null || !track2Data.contains("D")) {
        return ""
    }
    return track2Data.split("D".toRegex()).dropLastWhile { it.isEmpty() }
        .toTypedArray()[0].toString().replace("F", "")
}

fun maskPan(pan: String): String {
         if (pan.length < 10) return pan
         val visibleStart = pan.take(6)
         val visibleEnd = pan.takeLast(4)
         val masked = visibleStart + "*".repeat(pan.length - 10) + visibleEnd
         return masked
    }


fun getCardExpiry(track2Data: String?): String {
    if (track2Data == null || !track2Data.contains("D") || track2Data.length < 20) {
        return ""
    }
    val dataAfterD =
        track2Data.split("D".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1]
    return dataAfterD.substring(0, 4)
}

fun decodeApplicationLabel(hexString: String): String {
    val cleanHex = hexString.replace(" ", "")
    val sb = StringBuilder()
    for (i in cleanHex.indices step 2) {
        val byteHex = cleanHex.substring(i, i + 2)
        val decimal = byteHex.toInt(16)  // Hex to decimal
        sb.append(decimal.toChar())      // Decimal to ASCII char
    }
    return sb.toString()
}

fun getField4(amountStr: String): String {
    var amountStr = amountStr
    val index = amountStr.indexOf(".")
    if (amountStr.substring(index + 1, amountStr.length).length < 2) {
        amountStr = amountStr + "0"
    }
    amountStr = amountStr.replace(".", "")
    val amtlen = amountStr.length
    val amtBuilder = java.lang.StringBuilder()
    if (amtlen < 12) {
        for (i in 0..<(12 - amtlen)) {
            amtBuilder.append("0")
        }
    }
    amtBuilder.append(amountStr)
    amountStr = amtBuilder.toString()
    return amountStr
}

fun getField7(): String {
    val simpleDateFormat = SimpleDateFormat("MMddHHmmss")
    val datetime = simpleDateFormat.format(Date())
    return datetime
}

fun getStan(): String {
    val dateFormatStan = SimpleDateFormat("yyyyMMddHHmmssSSS")
    val stan = dateFormatStan.format(Date())
    return stan.substring(stan.length - 6)
}

fun getRRN(): String {
    val dateFormatStan = SimpleDateFormat("yyyyMMddHHmmssSSS")
    val rrn = dateFormatStan.format(Date())
    return rrn.substring(rrn.length - 12)
}

fun getField13(): String {
    val simpleDateFormat = SimpleDateFormat("MMdd")
    val date = simpleDateFormat.format(Date())
    return date
}

fun getServiceCode(track2Data: String?): String? {
    if (track2Data.isNullOrEmpty()) return null

    val indexOfToken = track2Data.indexOf('D')
    if (indexOfToken == -1) return null

    val indexOfServiceCode = indexOfToken + 5
    if (track2Data.length < indexOfServiceCode + 3) return null

    return track2Data.substring(indexOfServiceCode, indexOfServiceCode + 3)
}

fun findTagNew(data: String, targetTag: String): String? {
    var i = 0
    val hex = data.uppercase().replace(" ", "")

    while (i < hex.length) {
        // Read tag (handle 2-byte tags like 9F xx)
        var tag = hex.substring(i, i + 2)
        i += 2

        // Check if it's a multi-byte tag (first byte ends in 1F)
        if ((tag.toInt(16) and 0x1F) == 0x1F) {
            tag += hex.substring(i, i + 2)
            i += 2
        }

        // Read length
        val lenByte = hex.substring(i, i + 2).toInt(16)
        i += 2

        val length: Int
        if (lenByte <= 0x7F) {
            length = lenByte
        } else {
            // Multi-byte length
            val numLenBytes = lenByte and 0x7F
            length = hex.substring(i, i + numLenBytes * 2).toInt(16)
            i += numLenBytes * 2
        }

        val valueLength = length * 2 // in hex chars

        // Read value
        val value = hex.substring(i, i + valueLength)

        if (tag == targetTag.uppercase()) {
            return value
        }

        // If constructed tag, recurse into it
        val firstByte = tag.substring(0, 2).toInt(16)
        if ((firstByte and 0x20) != 0) {
            val nested = findTagNew(value, targetTag)
            if (nested != null) return nested
        }

        i += valueLength
    }

    return null
}