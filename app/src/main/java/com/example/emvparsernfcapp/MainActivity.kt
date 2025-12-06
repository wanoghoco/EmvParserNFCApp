package com.example.emvparsernfcapp
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.emvparsernfcapp.helpers.hexStringToByteArray
import com.example.emvparsernfcapp.helpers.parseBerTlv
import com.example.emvparsernfcapp.ui.theme.EmvParserNFCAppTheme
import com.example.emvparsernfcapp.widget.TlvTable
import com.example.emvparsernfcapp.model.Tlv



class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppCompatDelegate.setDefaultNightMode(MODE_NIGHT_NO)

        enableEdgeToEdge()
        setContent {
            EmvParserNFCAppTheme {

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    EmvParserApp(modifier = Modifier.padding(innerPadding))
                }
            }
        }

    }

    @Composable
    fun EmvParserApp(modifier: Modifier){
        val emvTags = remember { mutableStateOf("") }
        var tlvs = remember { mutableStateOf<List<Tlv>>(emptyList()) }
        Column(
            Modifier
                .padding(WindowInsets.safeDrawing.asPaddingValues()).padding(top = 0.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement= Arrangement.Top
        ) {
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth(),) {

                Image(painterResource(R.drawable.nfc,),null, Modifier.size(54.dp).padding(end = 16.dp)
                    .clickable{
                        startActivity(Intent(this@MainActivity, MainActivity2::class.java))
                    })
            }
            Text(text = "TLV Parser",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold)


            Box(Modifier.padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 0.dp)){
                BasicTextField(
                    value = emvTags.value,
                    maxLines = 4,
                    onValueChange = { newText -> emvTags.value = newText },
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            color = Color.Black,
                            width = 1.dp,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(top = 14.dp, bottom = 14.dp, start = 8.dp, end = 8.dp),
                    textStyle = TextStyle(fontSize = 18.sp, color = Color.Black),

                    decorationBox = { innerTextField ->
                        if (emvTags.value.isEmpty()) {
                            Text(
                                text = "Enter text here...",
                                style = TextStyle(color = Color.Gray, fontSize = 18.sp)
                            )
                        }
                        innerTextField()
                    }
                )
            }


            Button({
                val (parsed, _) = parseBerTlv( hexStringToByteArray(emvTags.value))
                tlvs.value=parsed;
            }, Modifier.padding(top = 20.dp, bottom = 12.dp)) {
                Text(text = "Decode TLV")
            }

            TlvTable(Modifier,tlvs.value)
        }
    }

}

