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
import com.example.emvparsernfcapp.helpers.buildPdolData
import com.example.emvparsernfcapp.helpers.bytesToHex
import com.example.emvparsernfcapp.helpers.divideArray
import com.example.emvparsernfcapp.helpers.formatEmvAmount
import com.example.emvparsernfcapp.helpers.getCardExpiry
import com.example.emvparsernfcapp.helpers.getCardPan
import com.example.emvparsernfcapp.helpers.getCurrency
import com.example.emvparsernfcapp.helpers.hexStringToByteArray
import com.example.emvparsernfcapp.helpers.maskPan
import com.example.emvparsernfcapp.helpers.parseBerTlv
import com.example.emvparsernfcapp.model.NFCRespModel
import com.example.emvparsernfcapp.model.Tlv
import com.example.emvparsernfcapp.ui.theme.EmvParserNFCAppTheme
import com.example.emvparsernfcapp.widget.TagRow

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class MainActivity2 : ComponentActivity() {
    private val title = mutableStateOf("Tap EMV NFC Card")

    private val currentTransaction = mutableStateOf(NFCRespModel("", "", "", "", ""))

    private val tranxStatus = mutableStateOf(false)
    private val logs = mutableStateOf("")

    private val tlvs =  mutableStateOf<List<Tlv>>(emptyList())

    private val tranxAmount=100;

    private var currentAid="'";

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
                .padding(WindowInsets.safeDrawing.asPaddingValues()).padding(top = 0.dp)
                .verticalScroll(rememberScrollState()).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement= Arrangement.Top
        ) {

            Text(text = "NFC Reader",
                Modifier.padding(top = 24.dp),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold, )


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
                modifier = Modifier.size(250.dp).padding(start = shipftDp)

            )
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
                    Box(Modifier.padding(10.dp).background(Color.LightGray, shape = RoundedCornerShape(12.dp)).fillMaxWidth()){
                        Text(logs.value, Modifier.padding(8.dp))
                    }
                }

                if(!tlvs.value.isEmpty()){
                    Text("EMV TAG",
                        Modifier.padding(start = 24.dp, top = 4.dp, bottom = 8.dp)
                            .height(20.dp),       fontSize = 14.sp,)
                    Column(
                        modifier = Modifier
                            .padding(start = 20.dp,0.dp, end = 0.dp)       // indentation for children
                    ) {
                        tlvs.value.forEachIndexed { index, child ->
                            TagRow(Modifier, child,false)
                            if (index < tlvs.value.lastIndex)
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
        logs.value="NFC Started\n";
        tranxStatus.value=false;
        currentTransaction.value=NFCRespModel("","","","","");

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
                logs.value+="GETTING AIDS RESP: ${respHex}\n\n";

                val aidTagIndex = respHex.indexOf("4F")
                val aidLength = Integer.parseInt(respHex.substring(aidTagIndex + 2, aidTagIndex + 4), 16) * 2
                val aidHex = respHex.substring(aidTagIndex + 4, aidTagIndex + 4 + aidLength)
                val selectAidResp= hexStringToByteArray("00A4040007"+aidHex +"00");
                currentAid=aidHex;
                logs.value+="SELECT AID: ${aidHex}\n\n";
                var selectAidRespResponse = iso.transceive(selectAidResp)
                var selectAidRespHex=bytesToHex(selectAidRespResponse)
                logs.value+="SELECT AID RESP: ${selectAidRespHex}\n\n";
                val pdolIndex = selectAidRespHex.indexOf("9F38")
                var gpoCommandHex="";
                if(pdolIndex==-1){
                    gpoCommandHex="80A8000002830000";
                    val gpoResponse = iso.transceive(hexStringToByteArray(gpoCommandHex))
                    val gpoResponseHex=bytesToHex(gpoResponse)
                    logs.value+="GPO COMMAND RESP: ${selectAidRespHex}\n\n";
                    val indexOf94=gpoResponseHex.indexOf("94")
                    if(indexOf94==-1){
                        throw Exception("unable to read emv card data")
                    }
                    val AFLLength = Integer.parseInt(gpoResponseHex.substring(indexOf94 + 2, indexOf94 + 4), 16) * 2
                    val AFL = gpoResponseHex.substring(indexOf94 + 4, indexOf94 + 4 + AFLLength)
                    logs.value+="AFL: ${AFL}\n\n";
                    val AFLChunk=divideArray(hexStringToByteArray(AFL),4);
                    val readRecordResponse: MutableList<String> = mutableListOf()
                    for (x in 0 until AFLChunk.size) {

                        val chuck: ByteArray = AFLChunk[x]
                        val sfiOrg = chuck[0]
                        val startRecord = chuck[1]
                        val endRecord = chuck[2]
                        val sfiNew = sfiOrg.toInt() or 0x04 // add 4 = set bit 3
                        val numberOfRecordsToRead: Int = (endRecord.toInt()- startRecord.toInt() + 1)
                        for (x in startRecord.toInt() until numberOfRecordsToRead ) {
                            val cmd: ByteArray = hexStringToByteArray("00B2000400")
                            cmd[2] = (x and 0xFF).toByte()
                            cmd[3] =(cmd[3].toInt() or (sfiNew and 0xFF)).toByte()
                            logs.value+="AFL COMMAND ${x}: ${cmd}\n\n";
                            val response = iso.transceive(cmd)
                            logs.value+="AFL COMMAND Response ${x}: ${cmd} ${bytesToHex(response)}\n\n";
                            readRecordResponse.add(bytesToHex(response))
                        }

                    }
                    logs.value+="RESPONSE TLV ${readRecordResponse}\n\n";
                    var allTlvs = ""
                    for (item in readRecordResponse) {
                        allTlvs += item
                    }

                    val (parsed, _) = parseBerTlv( hexStringToByteArray(allTlvs))
                    tlvs.value=parsed;
                    getMasterSafeTag(parsed[0].children)
                    tranxStatus.value=true;

                }
                else{


                    val pdolLength = Integer.parseInt(selectAidRespHex.substring(pdolIndex + 4, pdolIndex + 6), 16) * 2
                    val pdolCommand = selectAidRespHex.substring(pdolIndex + 6, pdolIndex + 6 + pdolLength)
                    logs.value+="PDOL COMMAND GENERATED: ${pdolCommand}\n\n";
                    val gpoCommand= buildPdolData(hexStringToByteArray(pdolCommand),tranxAmount);
                    val pdolDataLength=(bytesToHex(gpoCommand).length/2);
                    val LC="%02X".format(pdolDataLength+2);
                    val pdolDataLengthHex="%02X".format(pdolDataLength)
                    gpoCommandHex=bytesToHex(gpoCommand)
                    logs.value+="GPO COMMAND GENERATED: ${gpoCommandHex}\n\n";
                    val(gpoTlv,_)= parseBerTlv( hexStringToByteArray(gpoCommandHex))
                    gpoCommandHex="80A80000"+LC+"83"+pdolDataLengthHex +gpoCommandHex + "00";
                    val gpoResponse = iso.transceive(hexStringToByteArray(gpoCommandHex))
                    val gpoResponseHex=bytesToHex(gpoResponse)
                    logs.value+="GPO COMMAND RESP: ${gpoResponseHex}\n\n";
                    val (parsed, _) = parseBerTlv( hexStringToByteArray(gpoResponseHex))
                    tlvs.value=parsed;
                    getVisaSafeTag(parsed[0].children)
                    tranxStatus.value=true;

                }


            }
            catch (e: Exception){
                tranxStatus.value=false;
                logs.value+="ERROR : ${e.message}\n\n";
                runOnUiThread { showToast("Error: ${e.message}") }
            }
        }
    }


    private fun getMasterSafeTag(tranxTlv:List<Tlv>){
        val maskPan = maskPan( tranxTlv.firstOrNull { it.tag == "5A" }?.valueHex ?: "")
        val currencyCode = maskPan( tranxTlv.firstOrNull { it.tag == "5F28" }?.valueHex ?: "")
        val expiry = maskPan( tranxTlv.firstOrNull { it.tag == "5F24" }?.valueHex ?: "")
        val nfcResposne= NFCRespModel(
            maskPan,
            currency =currencyCode,
            amount = formatEmvAmount(tranxAmount),
            aid = currentAid,
            cardExpiry =expiry
        )
        currentTransaction.value=nfcResposne;
        AppHelper.saveNFCTranxData(this,nfcResposne)

    }
    private fun getVisaSafeTag(tranxTlv:List<Tlv>){
        val maskPan = maskPan(getCardPan(tranxTlv.firstOrNull { it.tag == "57" }?.valueHex ?: ""))
        val nfcResposne= NFCRespModel(
            maskPan,
            currency = getCurrency(),
            amount = formatEmvAmount(tranxAmount),
            aid = currentAid,
            cardExpiry = getCardExpiry(tranxTlv.firstOrNull { it.tag == "57" }?.valueHex ?: "")

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

}

