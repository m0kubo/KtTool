package com.insprout.okubo.kttool

import android.Manifest
import android.content.Context
import android.net.Uri
import android.preference.PreferenceManager


object Settings {

    val PERMISSIONS_CAMERA = arrayOf( Manifest.permission.CAMERA )
    val PERMISSIONS_PHOTO = arrayOf( Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE )

    const val REQUEST_PERMIT_CAMERA = 500
    const val REQUEST_PERMIT_PHOTO = 501

    private const val KEY_ADJUST_RATE = "settings.ADJUST_RATE"
    private const val KEY_FILE_PATH = "viewer.FILE_PATH"
    private const val KEY_FILE_SIZE = "viewer.FONT_SIZE"


    val getAdjustRate: (Context) -> Int = { context ->
        PreferenceManager.getDefaultSharedPreferences(context).getInt(KEY_ADJUST_RATE, 0)
    }

    val putAdjustRate = { context: Context, value: Int ->
        PreferenceManager.getDefaultSharedPreferences(context).edit().putInt(KEY_ADJUST_RATE, value).apply()
    }


    val getFontSize: (Context) -> Float = { context ->
        PreferenceManager.getDefaultSharedPreferences(context).getFloat(KEY_FILE_SIZE, 0f)
    }

    val putFontSize = { context: Context, fontSize: Float ->
        PreferenceManager.getDefaultSharedPreferences(context).edit().putFloat(KEY_FILE_SIZE, fontSize).apply()
    }

    val getFileUri: (Context) -> Uri? = { context ->
        PreferenceManager.getDefaultSharedPreferences(context).getString(KEY_FILE_PATH, null)?.let { Uri.parse(it) }
    }

    val putFileUri = { context: Context, uri: Uri? ->
        PreferenceManager.getDefaultSharedPreferences(context).edit().putString(KEY_FILE_PATH, uri?.toString()).apply()
    }

}