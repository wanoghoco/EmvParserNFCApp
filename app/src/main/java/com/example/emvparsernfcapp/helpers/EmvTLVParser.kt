package com.example.emvparsernfcapp.helpers
import com.example.emvparsernfcapp.model.Tlv
import org.jpos.tlv.TLVList
import org.jpos.tlv.TLVMsg



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

            var len = bytes[index].toInt() and 0xFF
            index++

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
    require(amount >= 0) { "Amount must be non-negative" }
    return amount.toString().padStart(12, '0')
}
fun buildPdolData(pdol: ByteArray,amount:Int): ByteArray {

    val tagValues = mapOf(
        "9F66" to hexStringToByteArray("E0000000"),       // TTQ
        "9F02" to hexStringToByteArray(formatEmvAmount(amount)),   // Amount
        "9F03" to hexStringToByteArray("000000000000"),   // Other Amount
        "9F1A" to hexStringToByteArray(getCurrencyCode()),           // Country
        "95"   to hexStringToByteArray("0000000000"),     // TVR
        "5F2A" to hexStringToByteArray(getCurrency()),           // Currency
        "9A"   to hexStringToByteArray(getEmvDate()),         // Date
        "9C"   to hexStringToByteArray("00"),             // Txn type
        "9F37" to hexStringToByteArray(generateUnpredictableNumber()))        // Unpredictable number

    val pdolItems = parsePdol(pdol)
    val result = mutableListOf<Byte>()
    for ((tag, length) in pdolItems) {
        val value = tagValues[tag] ?: ByteArray(length) { 0x00 } // default 0s
        if (value.size != length) {
            throw IllegalArgumentException("Tag $tag expects $length bytes, got ${value.size}")
        }
        result.addAll(value.toList())
    }
    return result.toByteArray()
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
    val random = java.security.SecureRandom()
    val un = ByteArray(4)
    random.nextBytes(un)
    return bytesToHex(un)
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