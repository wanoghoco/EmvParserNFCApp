package com.example.emvparsernfcapp

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.imageLoader
import com.example.emvparsernfcapp.helpers.AppHelper
import com.example.emvparsernfcapp.helpers.appUnpredictableNumber
import com.example.emvparsernfcapp.helpers.buildPdolAsTlv
import com.example.emvparsernfcapp.helpers.bytesToHex
import com.example.emvparsernfcapp.helpers.divideArray
import com.example.emvparsernfcapp.helpers.findTagNew
import com.example.emvparsernfcapp.helpers.formatEmvAmount
import com.example.emvparsernfcapp.helpers.getCardExpiry
import com.example.emvparsernfcapp.helpers.getCardPan
import com.example.emvparsernfcapp.helpers.getCurrency
import com.example.emvparsernfcapp.helpers.getField13
import com.example.emvparsernfcapp.helpers.getField4
import com.example.emvparsernfcapp.helpers.getField7
import com.example.emvparsernfcapp.helpers.getRRN
import com.example.emvparsernfcapp.helpers.getServiceCode
import com.example.emvparsernfcapp.helpers.getStan
import com.example.emvparsernfcapp.helpers.getTerminalGeneratedData
import com.example.emvparsernfcapp.helpers.hexStringToByteArray
import com.example.emvparsernfcapp.helpers.maskPan
import com.example.emvparsernfcapp.helpers.parseBerTlv
import com.example.emvparsernfcapp.model.NFCRespModel
import com.example.emvparsernfcapp.model.RavenEmv
import com.example.emvparsernfcapp.model.Tlv
import com.example.emvparsernfcapp.ui.theme.EmvParserNFCAppTheme
import com.example.emvparsernfcapp.widget.TagRow
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject


class MainActivity2 : ComponentActivity() {
    private val title = mutableStateOf("Tap EMV NFC Card")

    private val currentTransaction = mutableStateOf(NFCRespModel("", "", "", "", "",""))

    private val tranxStatus = mutableStateOf(false)
    private val logs = mutableStateOf("")

    private val transactionResponse = mutableStateOf("")

    private val tlvs =  mutableStateOf<List<Tlv>>(emptyList())

    private var allTransactionTags: List<Tlv> = emptyList()

    private var batchData="";

    private var batchData2="";

    private val tranxAmount=20;

    private var currentAid="'";

    var iccTags: Array<String?> = arrayOf<String>(
        "9F26",
        "9F27",
        "9F10",
        "9F37",
        "9F36",
        "95",
        "9A",
        "9C",
        "9F02",
        "5F2A",
        "5F34",
        "82",
        "9F1A",
        "9F03",
        "9F33",
        "84",
        "9F34",
        "9F35",
        "9F41",
        "9F12",
        "4F"
    ) as Array<String?>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EmvParserNFCAppTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    EmvNFCApp(modifier = Modifier.padding(innerPadding))
                }
            }
        }

    }

    @Composable
    fun EmvNFCApp(modifier: Modifier){
        var verboseAllowed = remember { mutableStateOf(false) }

        Column(
            Modifier
                .padding(WindowInsets.safeDrawing.asPaddingValues())
                .padding(top = 0.dp)
                .verticalScroll(rememberScrollState())
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement= Arrangement.Top
        ) {

            Text(
                text = "NFC Reader",
                Modifier.padding(top = 24.dp),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )


            Box(Modifier.padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 0.dp)){

                Text(title.value)
            }

            val imageLoader = LocalContext.current.imageLoader.newBuilder()
                .components {
                    add(GifDecoder.Factory()) // Add the GIF decoder factory
                }
                .build()



            val statusIcon=if(tranxStatus.value){
                LaunchedEffect(Unit) {
                    delay(2800)
                    tranxStatus.value=false;
                }
                R.drawable.success
            }
            else{
                R.drawable.ngc_gif;
            }
            val shipftDp=if(tranxStatus.value){
                0.dp
            }
            else{
                54.dp
            }
            AsyncImage(
                model = statusIcon,
                contentDescription = "Animated loading spinner",
                imageLoader = imageLoader, // Pass the configured ImageLoader
                modifier = Modifier
                    .size(250.dp)
                    .padding(start = shipftDp)

            )
            if(!(transactionResponse.value.isEmpty())){
                Text("Transaction Response: ${transactionResponse.value}", Modifier.padding(12.dp), fontSize = 20.sp)
            }
            if(!(currentTransaction.value.maskedPan.isEmpty())){
                Text("PAN: "+currentTransaction.value.maskedPan+"\n"+
                        "CURRENCY: "+currentTransaction.value.currency+"\n"+
                        "AMOUNT: "+currentTransaction.value.amount+"\n"+
                        "AID: "+currentTransaction.value.aid+"\n"+
                        "EXPIRY: "+currentTransaction.value.cardExpiry+"\n"
                    , Modifier.padding(12.dp), fontSize = 20.sp)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(16.dp)
            ) {
                Text("Verbose Mode",Modifier.padding(end = 6.dp))
                Switch( modifier = Modifier.scale(0.7f),
                    checked = verboseAllowed.value,
                    onCheckedChange = {
                        logs.value="";
                        tlvs.value =  emptyList();
                        verboseAllowed.value = it }
                )
            }

            Button({
                shareTransactionLog();
            }, Modifier.padding(top = 20.dp, bottom = 12.dp)) {
                Text(text = "Share Logs")
            }

            if(verboseAllowed.value){

                Box(Modifier.padding(12.dp)){
                    Box(Modifier
                        .padding(10.dp)
                        .background(Color.LightGray, shape = RoundedCornerShape(12.dp))
                        .fillMaxWidth()){
                        Text(logs.value, Modifier.padding(8.dp))
                    }
                }

                if(!tlvs.value.isEmpty()){
                    Text(
                        "EMV TAG",
                        Modifier
                            .padding(start = 24.dp, top = 4.dp, bottom = 8.dp)
                            .height(20.dp),
                        fontSize = 14.sp,
                    )
                    Column(
                        modifier = Modifier
                            .padding(start = 20.dp,0.dp, end = 0.dp)       // indentation for children
                    ) {
                        allTransactionTags.forEachIndexed { index, child ->
                            TagRow(Modifier, child,false)
                            if (index < allTransactionTags.lastIndex)
                                HorizontalDivider()
                        }
                        Box(Modifier.padding(top = 28.dp))
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        val nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if(nfcAdapter==null){
            title.value="NFC NOT SUPPORTED";
            return;
        }
        val options = Bundle()
        nfcAdapter.enableReaderMode(
            this,
            { tag -> onTagDiscovered(tag) }, // callback
            NfcAdapter.FLAG_READER_NFC_A or
                    NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_NFC_F or
                    NfcAdapter.FLAG_READER_NFC_V or
                    NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            options
        )

    }
    override fun onPause() {
        super.onPause()
        NfcAdapter.getDefaultAdapter(this).disableReaderMode(this)
    }
    private fun onTagDiscovered(tag: Tag) {
        tlvs.value=emptyList<Tlv>();
        appUnpredictableNumber="";
        logs.value="NFC Started\n";
        batchData="";
        batchData2="";
        transactionResponse.value="";
        tranxStatus.value=false;
        currentTransaction.value=NFCRespModel("","","","","","");

        CoroutineScope(Dispatchers.IO).launch {
            val iso = IsoDep.get(tag)
            if (iso == null) {

                runOnUiThread {
                    logs.value+="IsoDep not supported by tag\n";
                    showToast("IsoDep not supported by tag");
                }
                return@launch
            }

            try {
                iso.connect()
                iso.timeout = 5000
                logs.value+="GETTING AIDS\n";
                val selectPpse = hexStringToByteArray("00A404000E325041592E5359532E444446303100")
                var selectPpseResponse = iso.transceive(selectPpse)
                val respHex= (bytesToHex(selectPpseResponse));
                batchData2=respHex;
                logs.value+="GETTING AIDS RESP: ${respHex}\n\n";

                val parsedLabel=parseBerTlv(hexStringToByteArray(respHex))
                print(parsedLabel)
                 val applicationLabel=""

                val aidTagIndex = respHex.indexOf("4F")
                val aidLength = Integer.parseInt(respHex.substring(aidTagIndex + 2, aidTagIndex + 4), 16) * 2
                val aidHex = respHex.substring(aidTagIndex + 4, aidTagIndex + 4 + aidLength)
                val selectAidResp= hexStringToByteArray("00A4040007"+aidHex +"00");
                currentAid=aidHex;
                logs.value+="SELECT AID: ${aidHex}\n\n";
                var selectAidRespResponse = iso.transceive(selectAidResp)
                var selectAidRespHex=bytesToHex(selectAidRespResponse)
                batchData=selectAidRespHex;
                logs.value+="SELECT AID RESP: ${selectAidRespHex}\n\n";
                val pdolIndex = selectAidRespHex.indexOf("9F38")
                var gpoCommandHex="";
                if(pdolIndex==-1){
                    gpoCommandHex="80A8000002830000";
                    val gpoResponse = iso.transceive(hexStringToByteArray(gpoCommandHex))
                    val gpoResponseHex=bytesToHex(gpoResponse)
                    batchData+=gpoResponseHex;
                    logs.value+="GPO COMMAND RESP: ${selectAidRespHex}\n\n";
                    val indexOf94=gpoResponseHex.indexOf("94")
                    if(indexOf94==-1){
                        throw Exception("unable to read emv card data")
                    }

                    val AFLLength = Integer.parseInt(gpoResponseHex.substring(indexOf94 + 2, indexOf94 + 4), 16) * 2
                    val AFL = gpoResponseHex.substring(indexOf94 + 4, indexOf94 + 4 + AFLLength)
                    logs.value += "AFL: $AFL\n\n"

                    val AFLChunks = divideArray(hexStringToByteArray(AFL), 4)
                    val readRecordResponses: MutableList<String> = mutableListOf()

                    for (chunk in AFLChunks) {
                        val sfiOrg = chunk[0]
                        val startRecord = chunk[1].toInt() and 0xFF
                        val endRecord = chunk[2].toInt() and 0xFF
                        val offlineAuth = chunk[3] // usually ignored

                        // Correct SFI extraction per EMV spec (upper 5 bits)
                        val sfi = (sfiOrg.toInt() and 0xFF) shr 3

                        logs.value += "Processing AFL entry: SFI=${sfi} Records=$startRecord-$endRecord\n"

                        for (record in startRecord..endRecord) {
                            val cmd = hexStringToByteArray("00B2000000")
                            cmd[2] = record.toByte()
                            cmd[3] = ((sfi shl 3) or 0x04).toByte() // Correct P2

                            logs.value += "READ RECORD command for record $record: ${bytesToHex(cmd)}\n"

                            val response = iso.transceive(cmd)
                            logs.value += "READ RECORD response for record $record: ${bytesToHex(response)}\n"
                            readRecordResponses.add(bytesToHex(response))
                        }
                    }

                    logs.value+="RESPONSE TLV ${readRecordResponses}\n\n";
                    var allTlvs = ""
                    for (item in readRecordResponses) {
                        allTlvs += item
                    }
                    batchData+= getTerminalGeneratedData(applicationContext, isMaster = true);
                    batchData+=allTlvs;
                    val cdol1Index = batchData.indexOf("8C");
                    if(cdol1Index==-1){
                        throw Exception("unable to find card risk management data 1")
                    }
                    val cdolCommand = findTagNew(batchData,"8C");
                    var cdolAsTlvHex=bytesToHex(buildPdolAsTlv(hexStringToByteArray(cdolCommand!!),tranxAmount));
                    val (cdoltlv, _) = parseBerTlv( hexStringToByteArray(cdolAsTlvHex));
                    var cdolResponseCommandHex=generateOnlyValueFromTLV(cdoltlv)
                    batchData+=cdolAsTlvHex;
                    val cdolDataLength=(cdolResponseCommandHex.length/2);
                    val LC="%02X".format(cdolDataLength);
                    val fullCdolCommandHex = "80AE8000" + LC + cdolResponseCommandHex + "00"
                    print(fullCdolCommandHex)
                    val cdolResponse = iso.transceive(hexStringToByteArray(fullCdolCommandHex))
                    val cdolResponseHex=bytesToHex(cdolResponse)
                    batchData+=cdolResponseHex;


                     val cdol2Command = findTagNew(batchData,"8D")!!
                    val cdol2AsTlvHex=bytesToHex(buildPdolAsTlv(hexStringToByteArray(cdol2Command),tranxAmount));
                     batchData+=cdol2AsTlvHex;
                    val (cdol2TLV, _) =parseBerTlv(hexStringToByteArray(cdol2AsTlvHex))

                    val cdol2ResponseCommandHex =generateOnlyValueFromTLV(cdol2TLV)
                    val cdol2DataLength=(cdol2ResponseCommandHex.length/2);
                    var LC2="%02X".format(cdol2DataLength);
                    val fullCdol2CommandHex="80AE4000"+LC2 +cdol2ResponseCommandHex +"00";
                    print(fullCdol2CommandHex)
                    val cdol2Response = iso.transceive(hexStringToByteArray(fullCdol2CommandHex))
                    val cdol2ResponseHex=bytesToHex(cdol2Response)
                    batchData+=cdol2ResponseHex;

                    val (parsed2, _) = parseBerTlv( hexStringToByteArray(batchData))
                    val flattenData=flattenTlvToString(parsed2);
                    val (transactionData, _) = parseBerTlv( hexStringToByteArray(flattenData));
                    allTransactionTags= transactionData;
                    tlvs.value=transactionData;
                    getMasterSafeTag(transactionData,applicationLabel)
                    tranxStatus.value=true;
                    val paymentPayload=prepareTransactionPayload();
                    val paymentResponse= paymentPayload?.let { debitCard(it) };
                    tranxStatus.value=true;
                    if (paymentResponse != null) {
                        transactionResponse.value=paymentResponse
                    };
                    print(paymentResponse)

                }
                else{
                    val pdolLength = Integer.parseInt(selectAidRespHex.substring(pdolIndex + 4, pdolIndex + 6), 16) * 2
                    val pdolCommand = selectAidRespHex.substring(pdolIndex + 6, pdolIndex + 6 + pdolLength)
                    logs.value+="PDOL COMMAND GENERATED: ${pdolCommand}\n\n";
                    val gpoCommandAsTlv= buildPdolAsTlv(hexStringToByteArray(pdolCommand),tranxAmount);
                    batchData+=bytesToHex(gpoCommandAsTlv);
                    val (parseGPoCommandTLV, _) = parseBerTlv( gpoCommandAsTlv);
                    val gpoCommand=hexStringToByteArray(generateOnlyValueFromTLV(parseGPoCommandTLV))
                    val pdolDataLength=(bytesToHex(gpoCommand).length/2);
                    val LC="%02X".format(pdolDataLength+2);
                    val pdolDataLengthHex="%02X".format(pdolDataLength)
                    gpoCommandHex=bytesToHex(gpoCommand)
                    logs.value+="GPO COMMAND GENERATED: ${gpoCommandHex}\n\n";
                    val(gpoTlv,_)= parseBerTlv( hexStringToByteArray(gpoCommandHex))
                    gpoCommandHex="80A80000"+LC+"83"+pdolDataLengthHex +gpoCommandHex +"00";
                    val gpoResponse = iso.transceive(hexStringToByteArray(gpoCommandHex))
                    val gpoResponseHex=bytesToHex(gpoResponse)
                    batchData+=gpoResponseHex;
                    batchData+= getTerminalGeneratedData(applicationContext, isMaster = false);
                    batchData+=batchData2;
                    logs.value+="GPO COMMAND RESP: ${gpoResponseHex}\n\n";
                    val (parsed, _) = parseBerTlv( hexStringToByteArray(gpoResponseHex))
                    tlvs.value=parsed;
                    val (parsed2, _) = parseBerTlv( hexStringToByteArray(batchData))
                    val (transactionData, _) = parseBerTlv( hexStringToByteArray(flattenTlvToString(parsed2)));
                    allTransactionTags= transactionData;
                    getVisaSafeTag(parsed[0].children,applicationLabel)
                    val paymentPayload=prepareTransactionPayload();
                    val paymentResponse= paymentPayload?.let { debitCard(it) };
                    tranxStatus.value=true;
                    if (paymentResponse != null) {
                        transactionResponse.value=paymentResponse
                    };
                    print(paymentResponse)
                }

            }
            catch (e: Exception){
                tranxStatus.value=false;
                logs.value+="ERROR : ${e.message}\n\n";
                runOnUiThread { showToast("Error: ${e.message}") }
            }
        }
    }


    private fun debitCard(body: String):String {
        val client = OkHttpClient()

        val requestBody = body.toRequestBody(
            "application/json".toMediaType()
        )

        val request = Request.Builder()
            .url("https://posapi.getravenbank.com/v1/card/processing")
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Request failed: ${response.code}")
            }

            val bodyString = response.body?.string()
                ?: throw Exception("Empty response body")

            val json = JSONObject(bodyString)
            if(json.getString("status")=="fail"){
                return json
                    .getString("message")
            }

            return json
                .getJSONObject("data")
                .getJSONObject("data")
                .getString("meaning")+".\n status Code:"+json
                .getJSONObject("data")
                .getJSONObject("data")
                .getString("resp")
        }
    }

    private fun flattenTlvToString(tlvData: List<Tlv>): String {
        val sb = StringBuilder()

        for (x in tlvData) {
            if (x.children.isNotEmpty()) {
                // Recursively flatten children
                sb.append(flattenTlvToString(x.children))
            } else {
                // Ensure tag is even length
                val tag = if (x.tag.length % 2 != 0) "0${x.tag}" else x.tag

                // Length encoding
                val lengthHex = when {
                    x.length <= 127 -> x.length.toString(16).uppercase().padStart(2, '0')
                    x.length <= 255 -> "81" + x.length.toString(16).uppercase().padStart(2, '0')
                    x.length <= 65535 -> "82" + x.length.toString(16).uppercase().padStart(4, '0')
                    else -> throw IllegalArgumentException("Value too long")
                }

                // Ensure valueHex is even-length
                val valueHex = if (x.valueHex.length % 2 != 0) "0${x.valueHex}" else x.valueHex

                sb.append(tag).append(lengthHex).append(valueHex)
            }
        }

        return sb.toString()
    }


    private fun generateOnlyValueFromTLV(tlvData: List<Tlv>): String {
        val sb = StringBuilder()

        for (x in tlvData) {
                val valueHex = if (x.valueHex.length % 2 != 0) "0${x.valueHex}" else x.valueHex
                sb.append(valueHex)
        }

        return sb.toString()
    }

    private fun getMasterSafeTag(tranxTlv:List<Tlv>,  label:String){
        val maskPan = maskPan( tranxTlv.firstOrNull { it.tag == "5A" }?.valueHex ?: "")
        val currencyCode = maskPan( tranxTlv.firstOrNull { it.tag == "5F28" }?.valueHex ?: "")
        val expiry = maskPan( tranxTlv.firstOrNull { it.tag == "5F24" }?.valueHex ?: "")
        val nfcResposne= NFCRespModel(
            maskPan,
            currency =currencyCode,
            amount = formatEmvAmount(tranxAmount),
            aid = currentAid,
            cardExpiry =expiry,
            applicationLabel = label
        )
        currentTransaction.value=nfcResposne;
        AppHelper.saveNFCTranxData(this,nfcResposne)

    }

    private fun findTranxTag(tagName: String?): String? {
        for (tag in allTransactionTags) {
            if (tag.tag==tagName) {
                val sb = StringBuilder()
                val lengthHex = tag.length.toString(16).uppercase().padStart(2, '0')
                sb.append(tag.tag).append(lengthHex).append(tag.valueHex)
                return sb.toString();
            }
        }
        if(tagName=="4F"){
           return "4F07"+findTraxTagValue("84");
        }
        System.out.println( "did not find tag =" + tagName + "❗️️❗️️❗️️️")
        return ""
    }

    private fun findTraxTagValue(tagName: String?): String? {
            for (tag in allTransactionTags) {
                if (tag.tag==tagName) {
                    return tag.valueHex;
                }
            }
         System.out.println( "did not find tag =" + tagName + "❗️️❗️️❗️️️")
            return ""
    }



    private fun getVisaSafeTag(tranxTlv:List<Tlv>,label:String){
        val maskPan = maskPan(getCardPan(tranxTlv.firstOrNull { it.tag == "57" }?.valueHex ?: ""))
        val nfcResposne= NFCRespModel(
            maskPan,
            currency = getCurrency(),
            amount = formatEmvAmount(tranxAmount),
            aid = currentAid,
            cardExpiry = getCardExpiry(tranxTlv.firstOrNull { it.tag == "57" }?.valueHex ?: "")
, applicationLabel = label
        )
        currentTransaction.value=nfcResposne;
        AppHelper.saveNFCTranxData(this,nfcResposne)

    }

    private fun  showToast(toast:String){
        Toast.makeText(this,toast, Toast.LENGTH_SHORT).show()
    }

    private fun shareTransactionLog() {
        val text=  AppHelper.getAllNFCTranxDataStr(this);
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }

        val chooser = Intent.createChooser(intent, "Share via")
        startActivity(chooser)
    }


    private fun prepareTransactionPayload(): String? {
        val ravenEmv: RavenEmv = RavenEmv()
        ravenEmv.field0 = "0200"
        ravenEmv.field2 = getCardPan(findTraxTagValue("57"))
        ravenEmv.field3 = "000000"
         ravenEmv.field4 = getField4(java.lang.String.valueOf(tranxAmount) + "00")
        ravenEmv.field7 = getField7()
        ravenEmv.field11 = getStan()
        ravenEmv.field12 = ravenEmv.field11
        ravenEmv.field13 =getField13()
        ravenEmv.field14 = getCardExpiry(findTraxTagValue("57"))
        ravenEmv.field18 = "5251"
        ravenEmv.field22 = "912"
        ravenEmv.field23 = findTraxTagValue("5F34")
        ravenEmv.field25 = "00"
        ravenEmv.field26 = "06"
        ravenEmv.field28 = "D00000000"
        ravenEmv.field32 = findTraxTagValue("57")!!.substring(0, 6)
        ravenEmv.field35 = findTraxTagValue("57")
        ravenEmv.field37 = getRRN()
        ravenEmv.field40 = getServiceCode(findTraxTagValue("57"))
        ravenEmv.field41 = "203561CB"
        ravenEmv.field42 ="2035LA207033237";
        ravenEmv.field43 = "RAVENPAY LTD POS SETTLETHE QUAD PLOTLANG"
        ravenEmv.field49 = "566"
        ravenEmv.field52 = ""
        ravenEmv.field55 = getICCTags()
        ravenEmv.field123 = "510101511344101"
        ravenEmv.clrsesskey = "61C7DF86921AD9DF20469D7670BF8057"
        ravenEmv.port = "5013"
        ravenEmv.host = "196.6.103.18"
        ravenEmv.ssl = true
        ravenEmv.totalamount = tranxAmount.toInt().toString()
        ravenEmv.rrn = ravenEmv.field37
        ravenEmv.stan = ravenEmv.field11
        ravenEmv.track = findTraxTagValue("57")
        ravenEmv.expirydate = getCardExpiry(findTraxTagValue("57"))
        ravenEmv.pan =getCardPan(findTraxTagValue("57"))
        ravenEmv.tid = "203561CB"
        ravenEmv.filename = findTraxTagValue("84")
        ravenEmv.unpredictable = findTraxTagValue("9F37")
        ravenEmv.capabilities = findTraxTagValue("9F33")
        ravenEmv.cryptogram = findTraxTagValue("9F26")
        ravenEmv.tvr = findTraxTagValue("95")
        ravenEmv.iad = findTraxTagValue("9F10")
        ravenEmv.cvm = findTraxTagValue("9F34")
        ravenEmv.cip = ""
        ravenEmv.amount = tranxAmount.toInt().toString()
        ravenEmv.atc = findTraxTagValue("9F36")
        ravenEmv.aip = findTraxTagValue("82")
        ravenEmv.panseqno = findTraxTagValue("5F34")
        ravenEmv.pinblock = ""
        ravenEmv.clrpin = "76B670C7BAE5C12652F2F10258341C4A"
        ravenEmv.account = "Default"
        ravenEmv.mid = "2035LA207033237"
        ravenEmv.sn = "P140224033000080"
        ravenEmv.processor = "NIBSS"
        ravenEmv.processor = "kimono"
        ravenEmv.field124 = ""
        ravenEmv.paymentMode = "NFC"
        ravenEmv.field128 = ""

        val paymentPayload = Gson().toJson(ravenEmv)
        return paymentPayload
    }

    private fun getICCTags(): String? {
        val builder = java.lang.StringBuilder()
        try {
            for (s in iccTags) {
                builder.append(findTranxTag(s))
                continue
            }
            return builder.toString()
        } catch (e: java.lang.Exception) {
            return null
        }
    }

}

