package com.insprout.okubo.kttool

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.net.Uri
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

import com.insprout.okubo.kttool.util.SdkUtils

import java.io.*

import org.mozilla.universalchardet.UniversalDetector;


class TextViewerActivity : AppCompatActivity(), DialogUi.DialogEventListener {
    companion object {
        private const val REQ_DLG_CHAR_SET = 101
        private const val REQ_DLG_FONT_SIZE = 102

        private const val CHARSET_UTF8 = "UTF-8"
        private const val CHARSET_SJIS = "SHIFT_JIS"
        private const val CHARSET_JIS = "ISO-2022-JP"
        private const val CHARSET_EUC_JP = "EUC-JP"

        private const val FONT_SIZE_SMALL = 13.0f
        private const val FONT_SIZE_MEDIUM = 18.0f
        private const val FONT_SIZE_LARGE = 22.0f

        private const val REQUEST_PERMISSION_ACCESS_STORAGE = 100
        private val PERMISSIONS_READ_STORAGE : Array<String> = arrayOf( Manifest.permission.READ_EXTERNAL_STORAGE )


        fun startActivity(context: Context) {
            Intent(context, TextViewerActivity::class.java).let {
                // FLAG_ACTIVITY_CLEAR_TOP: 遷移先のアクティビティが既に動いていればそのアクティビティより上にあるアクティビティを消す。
                it.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                // FLAG_ACTIVITY_SINGLE_TOP: 既に動いているアクティビティに遷移する際、作りなおさずに再利用する。
                it.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                context.startActivity(it)
            }
        }
    }

    private val mTextView: TextView by lazy { findViewById<TextView>(R.id.tv_viewer) }

    private var mFileUri: Uri? = null
    private var mCharSet: String? = null
    private var mSpFontSize = FONT_SIZE_MEDIUM
    private val mCharSetArray: Array<String> = arrayOf(
            CHARSET_UTF8,
            CHARSET_SJIS,
            CHARSET_JIS,
            CHARSET_EUC_JP
    )
    private val mFontSizeArray:FloatArray = floatArrayOf(
            FONT_SIZE_SMALL,
            FONT_SIZE_MEDIUM,
            FONT_SIZE_LARGE
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_viewer)

        initVars(intent)
        initView()

        viewFile()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        // このアクティビティは SINGLE_TOPで起動されているため 既にこのアクティビティが起動している状態で
        // startActivityされた場合、onCreate()メソッドは呼び出されない。そのためここでパラメータのチェックする
        // ただし、このアクティビティが初めて起動される場合(onCreate()メソッドが呼び出される場合)は、このonNewIntent()は呼ばれない
        initVars(intent)
        viewFile()
    }


    private fun initVars(intent: Intent) {
        mFileUri = intent.data
        mCharSet = null
        Settings.getFontSize(this).takeIf { it >= FONT_SIZE_SMALL }?.let { mSpFontSize = it }
    }

    private fun initView() {
        setFontSize(mSpFontSize)
    }

    private fun viewFile() {
        // Runtimeパーミッションの確認
        if (!SdkUtils.requestRuntimePermissions(this, PERMISSIONS_READ_STORAGE, REQUEST_PERMISSION_ACCESS_STORAGE)) return

        // 表示するファイルが指定されていなかった場合は、前回表示したファイルを開く
        // 前回表示したファイルもなければ、Toastを表示して終了
        mFileUri = mFileUri ?: Settings.getFileUri(this)
                ?: return Toast.makeText(this, R.string.toast_no_file_specified, Toast.LENGTH_SHORT).show()

        mFileUri?.let {
            viewFileDelayed(it, mCharSet)
            Settings.putFileUri(this, it)
        }
    }

    private fun viewFileDelayed(fileUri: Uri, charSet: String?) {
        // プログレスダイアログ表示
        DialogUi.Builder(this, DialogUi.STYLE_PROGRESS_DIALOG).apply {
            setTitle(getString(R.string.toast_view_fmt, ""))
            setMessage(fileUri.toString())
        }.show()

        // ファイル表示に時間がかかる場合があるので、一度システムに制御をもどす
        Handler().postDelayed( {
            viewFile(fileUri, charSet)
            // プログレスダイアログ消去
            DialogUi.dismiss(DialogUi.REQUEST_CODE_DEFAULT)
        }, 100)
    }

    private fun viewFile(fileUri: Uri, charSet: String?) {
        getInputStream(fileUri)?.let {
            mTextView.text = try {
                StringBuilder().apply {
                    mCharSet = charSet ?: detectCharSet(fileUri) ?: CHARSET_UTF8
                    InputStreamReader(it, mCharSet).readLines().forEachIndexed { index, line ->
                        if (index >= 1) append("\n")
                        append(line)
                    }
                }.toString()

            } catch (e: IOException) {
                null
            }
        }
    }

    private val detectCharSet: (Uri?) -> String? = { uri ->
        try {
            // 文字コード判定ライブラリの実装
            UniversalDetector(null).apply {
                getInputStream(uri)?.use() { input ->
                    ByteArray(4096).let { buf ->
                        while (!this.isDone) {
                            input.read(buf).takeIf { it > 0 }?.let { this.handleData(buf, 0, it) } ?: return@use
                        }
                    }
                }
                this.dataEnd()  // charSet判別終了
            }.detectedCharset   // charSet判別結果取得

        } catch (e: IOException) {
            null
        }
    }

    private val getInputStream: (Uri?) -> InputStream? = { uri ->
        try {
            when (uri?.scheme) {
                "file" ->
                    uri.path?.let { FileInputStream(it) }   // uri.pathが nullの場合は nullを返す

                "content" ->
                    contentResolver.openInputStream(uri)

                else ->
                    null
            }

        } catch(e: FileNotFoundException) {
            null
        }
    }


    private fun setFontSize(size: Float) {
        resources.displayMetrics.apply {
            mTextView.textSize = size * this.scaledDensity / this.density
        }
    }

    private val getFontSizeLabel: (Float) -> String = {
        getString(R.string.label_font_size_fmt, it)
    }

    private fun changeFontSize() {
        // フォントサイズ設定値リストから、選択用(表示用)文字列リストを作成する
        val arrayLabels = Array( mFontSizeArray.size ) { getFontSizeLabel(mFontSizeArray[it]) }
        DialogUi.Builder(this).apply {
            setTitle(R.string.menu_font_size)
            setSingleChoiceItems(arrayLabels, mFontSizeArray.indexOf(mSpFontSize))
            setPositiveButton()
            setNegativeButton()
            setRequestCode(REQ_DLG_FONT_SIZE)
        }.show()
    }

    private fun changeCharSet() {
        DialogUi.Builder(this).apply {
            setTitle(R.string.menu_char_set)
            setSingleChoiceItems(mCharSetArray, mCharSetArray.indexOf(mCharSet))
            setPositiveButton()
            setNegativeButton()
            setRequestCode(REQ_DLG_CHAR_SET)
        }.show()
    }


    /////////////////////////////////////////////////////////////////////////
    //
    // Dialog 関連
    //

    // AlertDialogが DialogFragmentでの使用が推奨されるようになった為、AlertDialogの Listenerは Activityに implementsして使用する事。
    // そうしないと、(メモリ枯渇などによる)Fragmentの再作成時に Listenerが参照されなくなる。

    override fun onDialogEvent(requestCode: Int, dialog: AlertDialog, which: Int, view: View?) {
        when (requestCode) {
            REQ_DLG_FONT_SIZE -> {
                if (view is ListView) {
                    // which には ボタンID (DialogInterface.BUTTON_NEGATIVE : -2)などもくるので注意
                    if (which == DialogUi.EVENT_BUTTON_POSITIVE) {
                        mSpFontSize = mFontSizeArray[ view.checkedItemPosition ]
                        Settings.putFontSize(applicationContext, mSpFontSize)
                        setFontSize(mSpFontSize)
                    }
                }
            }

            REQ_DLG_CHAR_SET -> {
                if (view is ListView) {
                    // which には ボタンID (DialogInterface.BUTTON_NEGATIVE : -2)などもくるので注意
                    if (which == DialogUi.EVENT_BUTTON_POSITIVE) {
                        // charSetが変更された
                        mCharSet = mCharSetArray[ view.checkedItemPosition ]
                        mFileUri?.let { viewFile(it, mCharSet) }
                    }
                }
            }

        }
    }


    /////////////////////////////////////////////////////////////////////////
    //
    // menu関連
    //

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_viewer, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_char_set)?.let {
            val label = getString(R.string.menu_char_set)
            if (!mCharSet.isNullOrEmpty()) {
                it.title = label + mCharSet
                it.isEnabled = true
            } else {
                it.title = label
                it.isEnabled = false
            }
        }
        menu.findItem(R.id.action_font_size)?.title = getString(R.string.menu_font_size) + getFontSizeLabel(mSpFontSize)

        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            // backボタン
            android.R.id.home -> {
                finish()
                true
            }

            R.id.action_font_size -> {
                changeFontSize()
                true
            }

            R.id.action_char_set -> {
                changeCharSet()
                true
            }

            else ->
                super.onOptionsItemSelected(item)
        }
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            REQUEST_PERMISSION_ACCESS_STORAGE -> {
                // PERMISSIONが すべて付与されたか確認する
                if (SdkUtils.isGranted(grantResults)) {
                    viewFile()
                } else {
                    finish()
                }
            }
        }
    }

}