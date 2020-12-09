@file:Suppress("DEPRECATION")

package com.insprout.okubo.kttool

import android.annotation.SuppressLint
import android.app.Activity
import android.hardware.Camera
import android.view.*

import java.io.IOException
import kotlin.math.round
import java.io.File


@SuppressLint("ClickableViewAccessibility")
class CameraUi(activity: Activity, surfaceView: SurfaceView) : CameraCtrl(), SurfaceHolder.Callback {

//    private val mContext = activity
    private val mDisplay: Display = activity.windowManager.defaultDisplay
    private val mSurfaceView: SurfaceView = surfaceView.apply {
        setOnTouchListener(View.OnTouchListener { _, motionEvent ->
            if (mFocusing) return@OnTouchListener true
            if (motionEvent.actionMasked == MotionEvent.ACTION_UP) {
                autoFocus()
            }
            true
        })
    }

    private var mCamera: Camera? = null
    private var mFocusing = false

    override fun open() {
        mSurfaceView.holder.addCallback(this)
    }

    override fun close() {
        closeCamera()
        mSurfaceView.holder.removeCallback(this)
    }

    private val closeCamera = {
        mCamera?.apply {
            stopPreview()
            release()
            mCamera = null
        }
    }

    private val autoFocus = {
        mCamera?.apply {
            mFocusing = true
            autoFocus(Camera.AutoFocusCallback { _, _ ->
                mFocusing = false
            })
        }
    }

    override fun takePicture(picture: File, listener: TakePictureListener?) {
        mCamera?.apply {
            // 撮影するプレビューサイズを決定する。撮影可能な画像サイズと プレビュー用サイズとは異なる。
            // プレビュー用のサイズを決める際に縦横比が撮影可能なサイズのものを抽出しているので、その縦横比にマッチする撮影サイズを選択する
            selectPictureSize(parameters.supportedPictureSizes, mSurfaceView.width, mSurfaceView.height)?.let { size ->
                setupCameraRotation(this)
                parameters.setPictureSize(size.width, size.height)

                this.takePicture(null, null,
                        Camera.PictureCallback { data, camera ->
                            savePhoto(picture, data, mDisplay.rotation, listener)

                            //プレビュー再開
                            camera.startPreview()
                        }
                )
            }
        }
    }


    //////////////////////////////////////////////////////////////////////
    //
    // SurfaceHolder.Callback 実装
    //

    override fun surfaceCreated(surfaceHolder: SurfaceHolder) {
        try {
            mCamera = Camera.open()?.apply {
                // SurfaceViewの縦横比に近い プレビューサイズを選択する
                determinePreviewSize(this, mSurfaceView.width, mSurfaceView.height)
                setPreviewDisplay(surfaceHolder)
            }

        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: RuntimeException) {
            e.printStackTrace()
        }
    }

    override fun surfaceChanged(surfaceHolder: SurfaceHolder, format: Int, viewWidth: Int, viewHeight: Int) {
        mCamera?.apply {
            stopPreview()

            // AndroidManifest.xmlで android:configChanges="orientation"が指定された場合にそなえて
            // 念のためここでも プレビュー表示サイズとSurfaceViewとのサイズチェックを行っておく
            if (viewWidth != mSurfaceView.width || viewHeight != mSurfaceView.height) {
                determinePreviewSize(this, viewWidth, viewHeight)
            }

            // カメラを再開
            startPreview()
            autoFocus()
        }
    }

    override fun surfaceDestroyed(surfaceHolder: SurfaceHolder) {
        // 想定外の挙動でSurfaceが破棄された場合にそなえて、念のためrelease処理を呼んでおく
        closeCamera()
    }


    private val determinePreviewSize = { camera: Camera, targetWidth: Int, targetHeight: Int ->
        // 対応するプレビューサイズを決定する
        // プレビュー用のサイズと、撮影できるサイズは異なる。
        // プレビュー用のサイズ群から 縦横比が撮影可能なサイズに一致するもののみを抽出する
        selectFitSize(
                availableSizes(camera.parameters.supportedPreviewSizes, camera.parameters.supportedPictureSizes),
                wideRatio(targetWidth, targetHeight)
        )?.let { size ->
            // 選択されたサイズを プレビューサイズとして設定する
            camera.parameters.setPreviewSize(size.width, size.height)
            // 端末の向きから、表示画像の縦横比を求める (ついでに カメラ画像の回転処理もしておく)
            val previewRatio = when (mDisplay.rotation) {
            // 反時計回りに 90度 (横)
                Surface.ROTATION_90 -> {
                    camera.setDisplayOrientation(0)
                    size.width.toDouble() / size.height.toDouble()
                }
            // 時計回りに 90度 (横)
                Surface.ROTATION_270 -> {
                    camera.setDisplayOrientation(180)
                    size.width.toDouble() / size.height.toDouble()
                }
            // 180度 (上下逆さま)
                Surface.ROTATION_180 -> {
                    camera.setDisplayOrientation(270)
                    size.height.toDouble() / size.width.toDouble()
                }
            // 正位置 (縦)
            //Surface.ROTATION_0 -> {
                else -> {
                    camera.setDisplayOrientation(90)
                    size.height.toDouble() / size.width.toDouble()
                }
            }

            // 画像が歪まない様に、SurfaceViewのサイズを プレビュー画像の縦横比にあわせてリサイズする
            if (mSurfaceView.width.toDouble() / mSurfaceView.height.toDouble() > previewRatio) {
                // 表示エリアより プレビュー画像の方が 幅が短い
                mSurfaceView.layoutParams.width = round(mSurfaceView.height * previewRatio).toInt()

            } else {
                // 表示エリアより プレビュー画像の方が 高さが低い
                mSurfaceView.layoutParams.height = round(mSurfaceView.width / previewRatio).toInt()
            }
        }
    }


    private val setupCameraRotation = { camera: Camera ->
        camera.setDisplayOrientation(getRotationDegree(mDisplay.rotation))
    }


    private val selectFitSize: (List<Camera.Size>, Float) -> Camera.Size? = { sizes, maxWideRate ->
        selectWideSize(sizes, maxWideRate) ?: selectSquareSize(sizes)
    }

    // 指定比率以下で、最も横長なサイズを選ぶ
    // 縦横比が同じ場合は、解像度の高いものを返す
    private val selectWideSize: (List<Camera.Size>, Float) -> Camera.Size? = { sizes, maxWideRate ->
        if (sizes.isEmpty()) {
            null

        } else {
            var candidate: Camera.Size? = null
            var candidateRatio = 0f
            var candidateWidth = 0
            sizes.forEach { size ->
                wideRatio(size).let { ratio ->
                    if (ratio in candidateRatio..maxWideRate && size.width > candidateWidth) {
                        candidateRatio = ratio
                        candidateWidth = size.width
                        candidate = size
                    }
                }
            }
            candidate
        }
    }


    // 最も 正方形に近いサイズを選ぶ
    private val selectSquareSize: (List<Camera.Size>) -> Camera.Size? = {
        if (it.isEmpty()) {
            null

        } else {
            var candidate: Camera.Size = it[0]
            var candidateRatio = Float.POSITIVE_INFINITY
            var candidateWidth = 0
            it.forEach { size ->
                wideRatio(size).let { ratio ->
                    if (ratio > 0 && ratio <= candidateRatio && size.width > candidateWidth) {
                        candidateRatio = ratio
                        candidateWidth = size.width
                        candidate = size
                    }
                }
            }
            candidate
        }
    }

    // プレビュー可能サイズと、撮影可能サイズで、縦横比が共通なサイズのみをかえす
    private val availableSizes: (List<Camera.Size>, List<Camera.Size>) -> List<Camera.Size> = { previewSizes, photoSizes ->
        mutableListOf<Camera.Size>().apply {
            for (previewSize in previewSizes) {
                for (photoSize in photoSizes) {
                    if (isRatioEqual(previewSize, photoSize)) {
                        // 縦横比が共通なサイズがあった場合、それをListに追加
                        this.add(previewSize)
                        break
                    }
                }
            }
        }.takeIf {
            it.isNotEmpty()         // 縦横比が共通なサイズが１つ以上見つかれば、それを返す
        } ?: previewSizes           // 縦横比が共通なサイズが１つも見つからなければ、オリジナルのプレビューサイズ群をそのまま返す
    }

    private val selectPictureSize = { pictureSizes: List<Camera.Size>, width: Int, height: Int ->
        var size: Camera.Size? = null
        val ratio = wideRatio(width, height)
        for (pictureSize in pictureSizes) {
            if (isRatioEqual(wideRatio(pictureSize), ratio)) {
                if (size == null || size.width < pictureSize.width) {
                    size = pictureSize
                }
            }
        }
        size
    }

    // 指定された2つのサイズの縦横比が同じかどうかを判別する
    // 計算誤差を鑑み、差が1%以内なら同一比率とみなす
    private fun isRatioEqual(size1:Camera.Size, size2:Camera.Size): Boolean {
        return isRatioEqual(wideRatio(size1), wideRatio(size2))
    }

    // (長寸 / 短寸)の値を返す
    private fun wideRatio(size: Camera.Size): Float {
        return wideRatio(size.width, size.height)
    }

}