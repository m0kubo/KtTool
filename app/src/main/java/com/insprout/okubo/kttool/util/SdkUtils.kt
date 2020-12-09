package com.insprout.okubo.kttool.util

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.text.Html
import android.text.Spanned
import androidx.core.content.FileProvider

import java.io.File


object SdkUtils {

    //////////////////////////////////////////////////////////////////////////
    //
    // Runtime Permission関連
    //

    /**
     * 指定された パーミッションが付与されているか確認し、権限がない場合は指定されたrequestCodeで権限付与画面を呼び出す。
     *
     * @param activity    この機能を利用するアクティビティ
     * @param permissions 確認するパーミッション(複数)
     * @param requestCode 権限付与画面をよびだす際の、リクエストコード。(onRequestPermissionsResult()で判別する)
     * @return true: すでに必要な権限は付与されている。false: 権限が不足している。
     */
    val requestRuntimePermissions: (Activity, Array<String>, Int) -> Boolean = { activity, permissions, requestCode ->
        // Runtime Permission対応以前の OSバージョンは trueを返す
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            true

        } else {
            // リクエストされたパーミッションの内、許可されてないものを調べる
            getDeniedPermissions(activity, permissions).let {
                if (it.isEmpty()) {
                    true

                } else {
                    // 許可のないパーミッションに対して 権限付与画面を呼び出す
                    activity.requestPermissions(it, requestCode)
                    // 権限不足
                    false
                }
            }
        }

    }

    /**
     * 指定された RUNTIMEパーミッションの内、許可されていないものを返す。
     *
     * @param context     コンテキスト
     * @param permissions 確認するパーミッション(複数)
     * @return 許可されていないバーミッションのリスト。すべて許可されている場合はサイズ0のリストを返す。(nullを返すことはない)
     */
    private val getDeniedPermissions: (Context, Array<String>) -> Array<String> = { context, permissions ->
        mutableListOf<String>().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                permissions.forEach {
                    if (context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED) {
                        // 許可のないpermissionを記録
                        this.add(it)
                    }
                }
            }
        }.toTypedArray()
    }

    /**
     * 指定された パーミッションが付与されているか確認し、権限がない場合は指定されたrequestCodeで権限付与画面を呼び出す。
     * ただし、requestCodeが -1の場合は、権限付与画面は呼び出さない
     *
     * @param context     コンテキスト
     * @param permissions 確認するパーミッション(複数)
     * @return true: すでに必要な権限は付与されている。false: 権限が不足している。
     */
    val checkSelfPermissions: (Context, Array<String>) -> Boolean = { context, permissions ->
        getDeniedPermissions(context, permissions).isEmpty()
    }


    /**
     * onRequestPermissionsResult()で返された結果から、権限がすべて与えられたかチェックする
     *
     * @param grantResults チェックする権限
     * @return true: 必要な権限は全て付与されている。false: 権限が不足している。
     */
    val isGranted: (IntArray) -> Boolean = lambda@ { grantResults ->
        grantResults.forEach {
            if (it != PackageManager.PERMISSION_GRANTED) return@lambda false
        }
        // 必要なPERMISSIONがすべて付与されている
        true
    }

    val isPermissionRationale: (Activity, Array<String>) -> Boolean = lambda@ { activity, permissions ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            permissions.forEach {
                if (!activity.shouldShowRequestPermissionRationale(it)) {
                    // 以前に 「今後は確認しない」にチェックが付けられた permission
                    return@lambda false
                }
            }
        }
        true
    }

//    /**
//     * DOZEの無効化画面を呼び出す。（既に無効化設定されている場合は何もしない）
//     * この機能を利用するには、AndroidManifest.xmlに
//     * "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" パーミッションの利用許可が必要
//     *
//     * @param context コンテキスト
//     */
//    fun requestDisableDozeModeIfNeeded(context: Context) {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//            // DOZEの無効化設定リクエスト
//            val powerManager = context.getSystemService(PowerManager::class.java) ?: return
//            val packageName = context.packageName
//            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
//                // request disabling doze
//                // ユーザに 指定のアプリを Doze無効にしてもらう
//
//                @SuppressLint("BatteryLife")
//                val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
//                intent.data = Uri.parse("package:$packageName")
//                context.startActivity(intent)
//            }
//        }
//    }


    //////////////////////////////////////////////////////////////////////////
    //
    // File系  (主にandroidのバージョンによる apiの違いを吸収するために用意)
    //

    val getUriForFile: (Context, File) -> Uri = { context, file ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // API level 24以降ではこちらのメソッドを使用する
            // 関連する設定を AndroidManifest.xmlなどに登録しておくこと
            FileProvider.getUriForFile(context, context.packageName + ".provider", file)

        } else {
            // 以前のバージョンと同じ Uriを返す
            Uri.fromFile(file)
        }
    }


    //////////////////////////////////////////////////////////////////////////
    //
    // リソース系  (主にandroidのバージョンによる apiの違いを吸収するために用意)
    //

    /**
     * 指定されたリソースIDから Color値を返す
     * @param context    コンテキスト
     * @param resourceId 取得するColorのリソースID
     * @return 取得されたColor値
     */
    @Suppress("DEPRECATION")
    val getColor: (Context, Int) -> Int = { context, resourceId ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            //API level 23以降は Contextから カラー値を参照する
            context.getColor(resourceId)

        } else {
            // Resources経由の カラー値取得は、API level 23以降は 非推奨
            context.resources.getColor(resourceId)
        }
    }

    /**
     * Dimensionでsp単位でサイズ指定を行った場合、画面のdensityの影響をうけてしまうので
     * それを補正した値を返す
     * @param context コンテキスト
     * @param dimensionId dimensionリソースID
     * @return 取得された値
     */
    val getSpDimension: (Context, Int) -> Float = { context, dimensionId ->
        context.resources.let { it.getDimension(dimensionId) / it.displayMetrics.density * it.configuration.fontScale }
    }

     /**
     * htmlの文字列から Spannedオブジェクトを返す
     * @param htmlText htmlの文字列
     * @return 生成されたSpannedオブジェクト
     */
    @Suppress("DEPRECATION")
    val fromHtml: (String) -> Spanned = { htmlText ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // API level 24以降ではこちらのメソッドを使用する
            Html.fromHtml(htmlText, Html.FROM_HTML_MODE_LEGACY)
        } else {
            // API level 24以降では 非推奨
            Html.fromHtml(htmlText)
        }
    }
}