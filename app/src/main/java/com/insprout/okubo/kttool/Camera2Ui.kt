package com.insprout.okubo.kttool

import android.annotation.TargetApi
import android.app.Activity;
import android.content.Context
import android.graphics.Matrix;
import android.view.TextureView;
import android.view.Display
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.util.Size
import android.view.Surface
import android.graphics.SurfaceTexture
import android.graphics.PointF
import android.graphics.RectF
import android.os.Build
import android.hardware.camera2.CameraAccessException
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import java.io.File
import kotlin.math.max
import kotlin.math.min
import android.hardware.camera2.TotalCaptureResult
import android.media.MediaActionSound
import android.os.Handler
import android.os.Looper
import android.util.Log


@TargetApi(Build.VERSION_CODES.LOLLIPOP)
class Camera2Ui(activity: Activity, textureView: TextureView) : CameraCtrl() {

    private val mDisplay: Display = activity.windowManager.defaultDisplay
    private val mCameraManager: CameraManager = activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val mTextureView: TextureView = textureView

    private val mUiHandler = Handler(Looper.getMainLooper())
    private val mCameraId: String? by lazy lambda@ {
        //カメラIDを取得（背面カメラを選択）
        for (cameraId in mCameraManager.cameraIdList) {
            if (mCameraManager.getCameraCharacteristics(cameraId).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) {
                return@lambda cameraId
            }
        }
        null
    }
    private var mCameraDevice: CameraDevice? = null
    private var mCaptureSession: CameraCaptureSession? = null
    private val mCameraOrientation: Int by lazy {
        mCameraId?.let {
            mCameraManager.getCameraCharacteristics(it).get(CameraCharacteristics.SENSOR_ORIENTATION)
        } ?: 90
    }

    private var mPreviewSize: Size? = null
    private var mImageReader: ImageReader? = null
    private var mMediaActionSound: MediaActionSound? = null


    override fun open() {
        if (mTextureView.isAvailable) {
            // TextureView初期化済み
            openCamera()

        } else {
            // TextureView初期化処理
            mTextureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                    // open
                    openCamera()
                    // 目的に合う previewサイズを選択/設定する
                    setupSize(mCameraId, width, height)
                }

                override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                    // 目的に合う previewサイズを選択/設定する
                    setupSize(mCameraId, width, height)
                }

                override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                    return true
                }

                override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {}
            }
        }
        mMediaActionSound = MediaActionSound().apply {
            this.load(MediaActionSound.SHUTTER_CLICK)
        }
    }


    private val setupSize = lambda@ { cameraId: String?, width: Int, height: Int ->
        // 目的に合う previewサイズを選択/設定する
        cameraId?.let {
            mPreviewSize = getFitPreviewSize(getSupportedPreviewSizes(cameraId), width, height)?.let { size ->
                // 画像のサイズが確定した
                transformView(mTextureView, size)      // 表示領域にサイズ反映

                // 確定したサイズを元に 撮影用のImageReader作成/設定
                mImageReader = when (mCameraOrientation) {
                    90, 270 ->
                        ImageReader.newInstance(size.height, size.width, ImageFormat.JPEG, 1)
                    else ->
                        ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 1)
                }
                return@lambda // 設定完了
            }
        }

        // 背面カメラがなかった場合、選択可能な画像サイズが 0個だった場合
        mImageReader = null
        mPreviewSize = null
    }

    override fun close() {
        closeSession()
        mCameraDevice?.close()
        mCameraDevice = null
        mImageReader?.close()
        mImageReader = null
        mMediaActionSound?.release()
        mMediaActionSound = null
    }

    private val closeSession = {
        mCaptureSession?.close()
        mCaptureSession = null
    }


    override fun takePicture(picture: File, listener: TakePictureListener?) {
        try {
            mImageReader?.let { imageReader ->
                mCaptureSession?.let { session ->
                    mCameraDevice?.let { cameraDevice ->
                        mMediaActionSound?.play(MediaActionSound.SHUTTER_CLICK) // 自力でシャッター音を鳴らす場合

                        cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                            addTarget(imageReader.surface)
                            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
                            set(CaptureRequest.JPEG_ORIENTATION, getRotationDegree(mDisplay.rotation))

                            session.stopRepeating()
                            session.capture(build(), object : CameraCaptureSession.CaptureCallback() {
                                override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                                    super.onCaptureCompleted(session, request, result)
                                    createPreviewSession()
                                }
                            }, mUiHandler)
                        }

                        imageReader.setOnImageAvailableListener({ imageReader ->
                            // 画像撮影成功
                            imageReader.acquireNextImage().use {
                                // use関数を使用する事により スコープから外れる際 自動的にImageReaderのclose()を呼び出す
                                it.planes[0].buffer.let { buffer ->
                                    ByteArray(buffer.remaining()).let { array ->
                                        buffer.get(array)
                                        savePhoto(picture, array, listener)
                                    }
                                }
                            }
                        }, mUiHandler)

                    }
                }
            }

        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }


    private val createPreviewSession = {
        try {
            closeSession()

            mCameraDevice?.let { cameraDevice ->
                val surface = Surface(mTextureView.surfaceTexture.apply {
                    mPreviewSize?.let { setDefaultBufferSize(it.width, it.height) }
                })

                cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                    addTarget(surface);
                    set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                    //set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)

                    cameraDevice.createCaptureSession(
                            mutableListOf(surface).apply { mImageReader?.let { add(it.surface) } },
                            object : CameraCaptureSession.StateCallback() {
                                override fun onConfigured(session: CameraCaptureSession) {
                                    try {
                                        mCaptureSession = session
                                        session.setRepeatingRequest(build(), null, mUiHandler)
                                    } catch (e: CameraAccessException) {
                                        e.printStackTrace()
                                    }
                                }

                                override fun onConfigureFailed(session: CameraCaptureSession) {
                                    mCaptureSession = null
                                }
                            },
                            mUiHandler)
                }
            }

        } catch (e: CameraAccessException) {
            e.printStackTrace();
        }

    }


    private val openCamera = {
        try {
            mCameraId?.let { mCameraManager.openCamera(it, mStateCallback, null) }
        } catch (e: SecurityException) {
            e.printStackTrace();
        } catch (e: CameraAccessException) {
            e.printStackTrace();
        }
    }


    //////////////////////////////////////////////////////////////////////
    //
    // CameraDevice.StateCallback 実装
    //

    private val mStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            mCameraDevice = camera
            createPreviewSession()
        }

        override fun onDisconnected(cameraDevice: CameraDevice) {
            close()
        }

        override fun onError(cameraDevice: CameraDevice, error: Int) {
            close()
        }
    }


    //////////////////////////////////////////////////////////////////////
    //
    // private メソッド
    //

    private val getSupportedPreviewSizes: (String) -> List<Size> = {
        try {
            // 選択可能なサイズが取得できない場合は、サイズ0のListを返す
            mCameraManager.getCameraCharacteristics(it)
                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    ?.getOutputSizes(ImageFormat.JPEG)
                    ?.toList() ?: ArrayList()
        } catch (e: CameraAccessException) {
            ArrayList()
        }
    }


    private val getFitPreviewSize = { sizes: List<Size>, viewWidth: Int, viewHeight: Int ->
        when {
            sizes.isEmpty() ->
                null

            viewWidth <= 0 || viewHeight <= 0 ->
                sizes[0]

            else -> {
                var sizeSelected: Size = sizes[0]
                val viewRatio = wideRatio(viewWidth, viewHeight)
                var ratioSelected = 0f
                var widthSelected = 0
                for (size in sizes) {
                    val ratio = wideRatio(size.width, size.height)
                    val width = max(size.width, size.height)
                    if (isRatioEqual(ratio, ratioSelected) && width < widthSelected) continue
                    if (ratio in ratioSelected * 0.99 .. viewRatio * 1.01) {
                        ratioSelected = ratio
                        widthSelected = width
                        sizeSelected = size
                    }
                }
                sizeSelected
            }
        }
    }

    private val transformView = lambda@ { textureView: TextureView, preview: Size ->
        val previewWidth = preview.width.toFloat()
        val previewHeight = preview.height.toFloat()

        // カメラの取り付け向きと端末の向きから画像の回転量/縮尺を決定する
        val cameraOrientation = (mCameraOrientation - getRotationDegree(mDisplay.rotation) + 360) % 360
        val (scale: Float, degree: Int) = when (cameraOrientation) {
            90, 270 ->
                Pair(
                        min(textureView.width.toFloat() / previewWidth, textureView.height.toFloat() / previewHeight),
                        cameraOrientation - 180
                )

            0, 180 ->
                Pair(
                        min(textureView.width.toFloat() / previewHeight, textureView.height.toFloat() / previewWidth),
                        cameraOrientation
                )

            else ->
                return@lambda
        }

        val viewRect = RectF(0f, 0f, textureView.width.toFloat(), textureView.height.toFloat())
        val bufferRect = RectF(0f, 0f, previewHeight, previewWidth)
        val center = PointF(viewRect.centerX(), viewRect.centerY())
        bufferRect.offset(center.x - bufferRect.centerX(), center.y - bufferRect.centerY())

        textureView.setTransform(Matrix().apply {
            setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
            postScale(scale, scale, center.x, center.y)
            postRotate(degree.toFloat(), center.x, center.y)
        })
    }

}