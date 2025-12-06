package com.example.emvparsernfcapp.model

data class Tlv(
    val tag: String,
    val length: Int,
    val valueHex: String,
    val interpretation: String?,
    val malformed: String? = null,
    val children: List<Tlv> = emptyList(),
    val isConstructed: Boolean = false
)