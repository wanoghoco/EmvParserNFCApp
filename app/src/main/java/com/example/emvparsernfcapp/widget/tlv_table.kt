package com.example.emvparsernfcapp.widget

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.emvparsernfcapp.model.Tlv
import java.util.Locale



@Composable
fun TlvTable(modifier: Modifier, tlvs: List<Tlv>){

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TableHeaderCell("Tag", weight = FontWeight.Bold,Modifier.weight(1.3f))
            TableHeaderCell("Length", weight = FontWeight.Bold,Modifier.weight(0.6f))
            TableHeaderCell("Value(HEX)", weight = FontWeight.ExtraBold,Modifier.weight(4f))
            TableHeaderCell("Interpretation", weight = FontWeight.Bold,Modifier.weight(1.5f))
            Spacer(modifier = Modifier.width(48.dp))
        }
        HorizontalDivider()

        LazyColumn(modifier) {

            items(tlvs.size) { index->  TagRow(modifier,

                tlvs.get(index),(index == tlvs.lastIndex)
            )



            }

        }

    }
}




@Composable
public fun TableHeaderCell(text:String,weight: FontWeight,modifier: Modifier){
    Text(
        text,
        modifier = Modifier
            .padding(horizontal = 8.dp)
        ,
        fontWeight = weight,
        fontFamily = FontFamily.Monospace,
        fontSize = 14.sp
    )
}


@Composable
public fun TagRow(
    modifier: Modifier,
    tag: Tlv,
    isLast: Boolean
) {

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .animateContentSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tag column
            Text(
                tag.tag,
                modifier = Modifier.weight(1.3f).padding(horizontal = 8.dp),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )

            val lenHex = tag.length
                .toString(16)
                .uppercase(Locale.US)
                .padStart(2, '0')
            Text(
                lenHex,
                fontSize = 12.sp,
                modifier = Modifier.weight(0.7f).padding(horizontal = 8.dp),
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center

            )

            // Value column (hex + ascii)
            Column(modifier = Modifier.weight(4f).padding(horizontal = 8.dp)) {
                SelectionContainer {
                    Text(
                        text = if (tag.malformed == null) tag.valueHex else tag.malformed,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis
                    )
                }

            }
            Text(
                tag?.interpretation ?: "",
                fontSize = 12.sp,
                modifier = Modifier.weight(1.5f).padding(horizontal = 8.dp),
                fontFamily = FontFamily.Monospace
            )
        }
        if (!isLast)
        {
            HorizontalDivider()
        }
        if(!tag.children.isEmpty()){
            Text("Sub TAG",
                modifier.padding(start = 24.dp, top = 4.dp, bottom = 8.dp)
                    .height(20.dp),       fontSize = 14.sp,)
            Column(
                modifier = Modifier
                    .padding(start = 20.dp,0.dp, end = 0.dp)       // indentation for children
            ) {
                tag.children.forEachIndexed { index, child ->
                    TagRow(modifier, child,false)
                    if (index < tag.children.lastIndex)
                        HorizontalDivider()
                }
                Box(Modifier.padding(top = 28.dp))
            }
        }
    }



}
