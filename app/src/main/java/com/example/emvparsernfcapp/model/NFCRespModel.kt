package com.example.emvparsernfcapp.model

data class NFCRespModel (
    val maskedPan:String,
    val  currency:String,
    val  amount:String,
    val aid:String,
    val cardExpiry:String,
    val applicationLabel:String
)
