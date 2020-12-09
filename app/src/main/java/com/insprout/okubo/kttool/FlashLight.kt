@file:Suppress("DEPRECATION")

package com.insprout.okubo.kttool

import android.annotation.TargetApi
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Camera
import android.hardware.camera2.CameraManager
import android.os.Build
import android.hardware.camera2.CameraAccessException
import android.os.Handler
import android.util.Log


/**
 * Created by okubo on 2018/06/07.
 * FlashLightを 点灯させる機能 (android6.0以降 / 5.1以前 両対応)
 */

class FlashLight private constructor(context: Context) {
    private var mFlashLight: IFlashLight

    init {
        // androidのバージョンによって、カメラを制御するクラスを呼び分ける
        mFlashLight = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                FlashLight6(context)
            else ->
                FlashLight5(context)
        }
    }

    // インスタンスの生成をシングルトンにする
    companion object {
        @Volatile
        private var sInstance: FlashLight? = null

        fun getInstance(context: Context): FlashLight =
                sInstance ?: synchronized(this) {
                    sInstance ?: FlashLight(context)
                }
    }

    // Property
    val hasFlash: Boolean
        get() = mFlashLight.hasFlash

    val isFlashing: Boolean
        get() = mFlashLight.isFlashing

    // Method
    val release = {
        mFlashLight.release()
    }

    val turnOn = {
        mFlashLight.turnOn()
    }

    val turnOff = {
        mFlashLight.turnOff()
    }

    val toggle = {
        mFlashLight.toggle()
    }


    //////////////////////////////////////////////////////////////////////
    //
    // Interface
    //

    interface IFlashLight {
        fun release()
        fun turnOn()
        fun turnOff()
        fun toggle(): Boolean
        val hasFlash: Boolean
        val isFlashing: Boolean
    }


    //////////////////////////////////////////////////////////////////////
    //
    // android 6.0以降 の FlashLight機能 実装
    //

    @TargetApi(Build.VERSION_CODES.M)
    class FlashLight6(context: Context) :  FlashLight.IFlashLight {
        companion object {
            const val TAG = "FlashLight6"
        }

        private var mCameraManager: CameraManager? = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        private var mCameraId: String? = null
        private var mFlashing = false

        private val mTorchCallback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                super.onTorchModeChanged(cameraId, enabled)
                mCameraId = cameraId
                mFlashing = enabled
                Log.d("Flash", "cameraId:$cameraId torch:$enabled")
            }
        }

        // Property
        override val hasFlash: Boolean
            get() = (mCameraId != null)
        override val isFlashing: Boolean
            get() = mFlashing

        init {
            mCameraManager?.registerTorchCallback(mTorchCallback, Handler())
        }

        override fun release() {
            mCameraManager?.unregisterTorchCallback(mTorchCallback)
        }

        private val turnOn = { flashing: Boolean ->
            mCameraId?.let {
                try {
                    mCameraManager?.setTorchMode(it, flashing)
                } catch (e: CameraAccessException) {
                    Log.d(TAG, "turnOn(): ${e.message}")
                }
            }
        }

        override fun turnOn() {
            turnOn(true);
        }

        override fun turnOff() {
            turnOn(false);
        }

        override fun toggle(): Boolean {
            turnOn(!mFlashing)
            return !mFlashing
        }

    }


    //////////////////////////////////////////////////////////////////////
    //
    // android 5.1以前の FlashLight機能 実装
    //

    @Suppress("DEPRECATION")
    class FlashLight5(context: Context) : FlashLight.IFlashLight {
        companion object {
            const val TAG = "FlashLight5"
        }

        private var mOpenedCamera: Camera? = null
        private var mFlashing = false
        override val hasFlash = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH);


        // Property
        override val isFlashing: Boolean
            get() = mFlashing


        override fun release() {
            mOpenedCamera?.release();
            mOpenedCamera = null;
        }

        override fun turnOn() {
            if (!hasFlash) {
                mFlashing = false;
                return
            }

            Log.d(TAG, "turnOn(): mFlashing = $mFlashing");
            if (!mFlashing) {
                mOpenedCamera = mOpenedCamera ?: (openCamera() ?: return)
                torch(mOpenedCamera, true);
                mFlashing = true
            }
        }

        override fun turnOff() {
            if (hasFlash) {
                if (mFlashing) {
                    mOpenedCamera?.let { torch(it, false) }
                }
                release()       // Cameraオブジェクトを openしっぱなしだと他のアプリが カメラをコントロールできないので 都度releaseする
            }
            mFlashing = false
        }

        override fun toggle(): Boolean {
            if (mFlashing) {
                turnOff()
            } else {
                turnOn()
            }

            // mFlashingは turnOn()/turnOff()メソッド中で更新される
            return mFlashing
        }


        private val openCamera = {
            try {
                Camera.open()
            } catch (e: RuntimeException) {
                null
            }
        }

        private fun torch(camera: Camera?, torch: Boolean) {
            camera ?: return
            camera.parameters = camera.parameters.apply {
                this.flashMode = if (torch) Camera.Parameters.FLASH_MODE_TORCH else Camera.Parameters.FLASH_MODE_OFF
            }
            camera.stopPreview()
        }

    }

}