package com.insprout.okubo.kttool

import android.app.Activity
import android.view.Surface
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import androidx.exifinterface.media.ExifInterface

import java.io.File
import java.io.IOException
import java.util.*

import kotlin.math.max
import kotlin.math.min


abstract class CameraCtrl() {

    //////////////////////////////////////////////////////////////////////
    //
    // Interface
    //

    interface TakePictureListener : EventListener {
        fun onTakePicture(result: Boolean)
    }


    //////////////////////////////////////////////////////////////////////
    //
    // abstractメソッド 宣言
    //

    abstract fun open()
    abstract fun close()
    abstract fun takePicture(picture: File, listener: TakePictureListener?)


    //////////////////////////////////////////////////////////////////////
    //
    // Utilityメソッド
    //

    companion object {

        fun newInstance(activity: Activity, view: View): CameraCtrl? {
            return when (view) {
                is SurfaceView ->
                    CameraUi(activity, view)
                is TextureView ->
                    Camera2Ui(activity, view)
                else ->
                    null
            }
        }

    }


    //////////////////////////////////////////////////////////////////////
    //
    // 共通メソッド
    //

    /**
     * バイト列をjpegファイルとして指定の Fileに書き出す。(Exif情報は付加しない)
     * また、exif情報で 画像の向きも付加する
     *
     * @param file            出力先ファイル
     * @param data            画像データ
     * @param listener        撮影結果リスナー
     */
    protected fun savePhoto(file: File?, data: ByteArray?, listener: TakePictureListener?) {
        savePhoto(file, data, -1, listener)
    }

    /**
     * バイト列をjpegファイルとして指定の Fileに書き出し、Exifでorientation情報を付加する
     * また、exif情報で 画像の向きも付加する
     *
     * @param file            出力先ファイル
     * @param data            画像データ
     * @param displayRotation 端末の向き。Display#getRotation()で得られる値
     * @param listener        撮影結果リスナー
     */
    protected fun savePhoto(file: File?, data: ByteArray?, displayRotation: Int, listener: TakePictureListener?) {
        var result = false

        data?.let { byteArray ->
            file?.let { filePhoto ->
                try {
                    filePhoto.outputStream().use {
                        it.write(byteArray)
                        result = true
                    }
                } catch (e: IOException) {
                    //e.printStackTrace()
                    result = false
                }

                if (result && displayRotation >= 0) {
                    // 画像の回転情報をつけておく
                    try {
                        ExifInterface(filePhoto.path).apply {
                            this.setAttribute(ExifInterface.TAG_ORIENTATION, getExifOrientation(displayRotation).toString())
                            this.saveAttributes()
                        }
                    } catch (e: IOException) {
                    }
                }
                if (!result) filePhoto.delete()
            }
        }

        listener?.onTakePicture(result)
    }

    /**
     * 端末の向きから、画像の向き(ExifInterface設定値)を返す
     * ( getRotationDegree()と同等のメソッドだが、こちらは角度ではなく EXIF用の設定を返す )
     *
     * @param displayRotation 端末の向き。Display#getRotation()で得られる値
     * @return 画像の向き(ExifInterface設定値)
     */
    protected val getExifOrientation: (Int) -> Int = {
        when (it) {
            // 反時計回りに 90度 (横)
            Surface.ROTATION_90 ->
                ExifInterface.ORIENTATION_NORMAL

            // 時計回りに 90度 (横)
            Surface.ROTATION_270 ->
                ExifInterface.ORIENTATION_ROTATE_180

            // 180度 (上下逆さま)
            Surface.ROTATION_180 ->
                ExifInterface.ORIENTATION_ROTATE_270

            // 正位置 (縦)
            //Surface.ROTATION_0 ->
            else ->
                ExifInterface.ORIENTATION_ROTATE_90
        }
    }


    /**
     * 端末の向きから、カメラの補正角度(degree)を返す
     *
     * @param displayRotation 端末の向き。Display#getRotation()で得られる値
     * @return カメラの補正角度(degree)
     */
    protected val getRotationDegree: (Int) -> Int = {
        when (it) {
            // 反時計回りに 90度 (横)
            Surface.ROTATION_90 ->
                0

            // 時計回りに 90度 (横)
            Surface.ROTATION_270 ->
                180

            // 180度 (上下逆さま)
            Surface.ROTATION_180 ->
                270

            // 正位置 (縦)
            //Surface.ROTATION_0 ->
            else ->
                90
        }
    }

    /**
     *  (長寸 / 短寸)の値を返す
     *  どちらかに 0以下の値が指定された場合は 0を返す
     *  (よって結果は 0もしくは 1.0以上の正の数となる)
     *  @param width 幅
     *  @param height 高さ
     *  @return 長寸/短寸
     */
    protected val wideRatio: (Int, Int) -> Float = { width, height ->
        if (width <= 0 || height <= 0)
            0f
        else
            max(width, height).toFloat() / min(width, height).toFloat()
    }

    /**
     * 指定された2つのサイズの縦横比が同じかどうかを判別する
     * 計算誤差を鑑み、差が1%以内なら同一比率とみなす
     * どちらかに 0が指定された場合は、不正な比率が指定されたと見做し falseを返す
     *  @param ratio1 比較される比率
     *  @param ratio2 比較する比率
     *  @return 結果
     */
    protected val isRatioEqual: (Float, Float) -> Boolean = { ratio1, ratio2 ->
        (ratio1 > 0) && (ratio2 > 0) && ((ratio1 / ratio2) in 0.99f..1.01f)
    }

}