package com.txapp.musicplayer.util

import android.content.Context
import java.io.IOException
import java.util.Properties

object VersionHelper {
    private const val VERSION_FILE = "version.properties"
    private const val VERSION_CODE_KEY = "versionCode"
    private const val VERSION_NAME_KEY = "versionName"

    fun getVersionCode(context: Context): Int {
        val properties = loadProperties(context)
        return properties.getProperty(VERSION_CODE_KEY, "1").toIntOrNull() ?: 1
    }

    fun getVersionName(context: Context): String {
        val properties = loadProperties(context)
        return properties.getProperty(VERSION_NAME_KEY, "1.0.0")
    }

    private fun loadProperties(context: Context): Properties {
        val properties = Properties()
        try {
            context.assets.open(VERSION_FILE).use { inputStream ->
                properties.load(inputStream)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return properties
    }
}
