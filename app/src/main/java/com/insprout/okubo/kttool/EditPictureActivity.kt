package com.insprout.okubo.kttool

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.Intent
import android.content.Context
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException


class EditPictureActivity : AppCompatActivity() {

    companion object {
        // 写真ファイル用定数
//        const val EXTENSION_PHOTO_FILE = ".jpeg"
//        const val PREFIX_PHOTO_FILE = "IMG_"
//        const val MIME_TYPE_JPEG = "image/jpeg"
        private const val EXTRA_PARAM_1 = "extra.PARAM_1"

        fun startActivity(context: Context, path: String) {
            Intent(context, EditPictureActivity::class.java).let {
                // FLAG_ACTIVITY_CLEAR_TOP: 遷移先のアクティビティが既に動いていればそのアクティビティより上にあるアクティビティを消す。
//                it.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                // FLAG_ACTIVITY_SINGLE_TOP: 既に動いているアクティビティに遷移する際、作りなおさずに再利用する。
                it.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                it.putExtra(EXTRA_PARAM_1, path)
                context.startActivity(it)
            }
        }
    }

    private var mFilename: String? = null
    private var mFilePicture: File? = null


    private val mIvPicture: ImageView by lazy { findViewById<ImageView>(R.id.iv_picture) }

//    override fun onPause() {
//        super.onPause()
//    }
//
//    override fun onResume() {
//        super.onResume()
//    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_pic)

        initVars(this.intent)
        initView()
    }

    private fun initVars(intent: Intent?) {
        mFilename = intent?.getStringExtra(EXTRA_PARAM_1)//?.let { File(it) }
        //mFilePicture = intent?.getStringExtra(EXTRA_PARAM_1)?.let { File(it) }
    }

    private fun initView() {
        mIvPicture.setImageBitmap(readPicture(mFilename))

//        val bm2 = createBitmap()
//        val stream = FileOutputStream("/sdcard/test.jpg")
//        /* Write bitmap to file using JPEG and 80% quality hint for JPEG. */
//        bm2.compress(CompressFormat.JPEG, 80, stream)

//        val srcFile = File(mFilename)
    }

    private fun readPicture(fileName: String?): Bitmap? {
        fileName ?: return null

        try {
            FileInputStream(File(fileName)).use {
                return BitmapFactory.decodeStream(it)
            }

        } catch (e: FileNotFoundException) {
            return null
        }
    }

//    fun onClick(view: View) {
//        when (view.id) {
//            R.id.btn_photo -> takePhoto()
//        }
//    }




}