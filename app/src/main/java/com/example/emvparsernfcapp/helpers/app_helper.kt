package com.example.emvparsernfcapp.helpers

import android.content.Context
import com.example.emvparsernfcapp.model.NFCRespModel
import com.google.firebase.crashlytics.buildtools.reloc.com.google.common.reflect.TypeToken
import com.google.gson.Gson



class AppHelper {

    companion object {

        fun saveNFCTranxData(context: Context, newResp: NFCRespModel) {
              val gson = Gson()
            val prefs = context.getSharedPreferences("nfc_data", Context.MODE_PRIVATE)
            val json = prefs.getString("nfc_list", "[]")
            val type = object : TypeToken<MutableList<NFCRespModel>>() {}.type
            val list: MutableList<NFCRespModel> = gson.fromJson(json, type)
            list.add(newResp)
            val newJson = gson.toJson(list)
            prefs.edit().putString("nfc_list", newJson).apply()
        }

        fun getAllNFCTranxData(context: Context): List<NFCRespModel> {
              val gson = Gson()
            val prefs = context.getSharedPreferences("nfc_data", Context.MODE_PRIVATE)
            val json = prefs.getString("nfc_list", "[]")
            val type = object : TypeToken<List<NFCRespModel>>() {}.type
            return gson.fromJson(json, type)
        }
        fun getAllNFCTranxDataStr(context: Context): String? {
            val prefs = context.getSharedPreferences("nfc_data", Context.MODE_PRIVATE)
            val json = prefs.getString("nfc_list", "[]")
             return json;
        }
    }
}