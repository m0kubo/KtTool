package com.insprout.okubo.kttool

import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

import java.io.File
import java.text.SimpleDateFormat
import java.util.*


class PhotoActivity : AppCompatActivity() {

    companion object {
        // 写真ファイル用定数
        const val EXTENSION_PHOTO_FILE = ".jpeg"
        const val PREFIX_PHOTO_FILE = "IMG_"
        const val MIME_TYPE_JPEG = "image/jpeg"

        fun startActivity(context: Context) {
            Intent(context, PhotoActivity::class.java).let {
                // FLAG_ACTIVITY_CLEAR_TOP: 遷移先のアクティビティが既に動いていればそのアクティビティより上にあるアクティビティを消す。
                it.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                // FLAG_ACTIVITY_SINGLE_TOP: 既に動いているアクティビティに遷移する際、作りなおさずに再利用する。
                it.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                context.startActivity(it)
            }
        }
    }

    private val mCameraUi: CameraCtrl? by lazy { CameraCtrl.newInstance(this, findViewById(R.id.preview)) }
    private val mDfFilename = SimpleDateFormat("yyMMdd_HHmmss_SSS", Locale.getDefault())

    override fun onPause() {
        super.onPause()
        mCameraUi?.close()
    }

    override fun onResume() {
        super.onResume()
        mCameraUi?.open()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo)
    }


    fun onClick(view: View) {
        when (view.id) {
            R.id.btn_photo -> takePhoto()
        }
    }

    private fun takePhoto() {
        mCameraUi?.apply {
            File( Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), photoFilename() ).let {file ->
                this.takePicture(file, object : CameraCtrl.TakePictureListener {
                    override fun onTakePicture(result: Boolean) {
                        if (result) {
                            // 撮影完了メッセージ
                            Toast.makeText(this@PhotoActivity, getString(R.string.msg_photo_complete) + file.path, Toast.LENGTH_SHORT).show()
                            // コンテンツ管理DBに画像を登録
                            MediaScannerConnection.scanFile(
                                    this@PhotoActivity,
                                    arrayOf(file.absolutePath),
                                    arrayOf(MIME_TYPE_JPEG),
                                    null
                            )
                            EditPictureActivity.startActivity(this@PhotoActivity, file.path)
                            finish()
                        }
                    }
                })
            }
        }
    }

    // 現在時刻から 写真のファイル名を生成する
    private val photoFilename = {
        PREFIX_PHOTO_FILE + mDfFilename.format(Date(System.currentTimeMillis())) + EXTENSION_PHOTO_FILE
    }

}