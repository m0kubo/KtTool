package com.insprout.okubo.kttool

import android.content.Context
import android.content.Intent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.SensorEvent
import android.hardware.Sensor
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

import com.insprout.okubo.kttool.util.SdkUtils


class HorizonMeterActivity : AppCompatActivity(), SensorEventListener {
    companion object {

        fun startActivity(context: Context) {
            Intent(context, HorizonMeterActivity::class.java).let {
                // FLAG_ACTIVITY_CLEAR_TOP: 遷移先のアクティビティが既に動いていればそのアクティビティより上にあるアクティビティを消す。
                it.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                // FLAG_ACTIVITY_SINGLE_TOP: 既に動いているアクティビティに遷移する際、作りなおさずに再利用する。
                it.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                context.startActivity(it)
            }
        }
    }

    private val mSensorManager: SensorManager by lazy { getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    private val mMagneticSensor: Sensor? by lazy {              // 磁気センサー
        mSensorManager.getSensorList(Sensor.TYPE_MAGNETIC_FIELD)?.let { if (it.isNotEmpty()) it[0] else null }
    }
    private val mAccelerometerSensor: Sensor? by lazy {         // 加速度センサー
        mSensorManager.getSensorList(Sensor.TYPE_ACCELEROMETER)?.let { if (it.isNotEmpty()) it[0] else null }
    }
    private val mTvHorizon: TextView by lazy { findViewById<TextView>(R.id.tv_angle) }
    private val mTvFace: TextView by lazy { findViewById<TextView>(R.id.tv_face) }
    private val mCameraUi: CameraCtrl? by lazy { CameraCtrl.newInstance(this, findViewById(R.id.preview)) }

    private val mIsSensorReady: Boolean
            get() = (mMagneticSensor != null && mAccelerometerSensor != null);


    override fun onResume() {
        super.onResume()
        if (mIsSensorReady) {
            mSensorManager.registerListener(this, mMagneticSensor, SensorManager.SENSOR_DELAY_NORMAL)
            mSensorManager.registerListener(this, mAccelerometerSensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
        mCameraUi?.open()
    }

    override fun onPause() {
        super.onPause()
        if (mIsSensorReady) {
            mSensorManager.unregisterListener(this)
        }
        mCameraUi?.close()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_horizon)

        if (!mIsSensorReady) {
            Toast.makeText(this, R.string.msg_missing_sensor, Toast.LENGTH_LONG).show()
            finish()

        } else if (!SdkUtils.checkSelfPermissions(this, Settings.PERMISSIONS_CAMERA)) {
            finish()
        }
    }


    //////////////////////////////////////////////////////////////////////
    //
    // SensorEventListener 実装
    //

    private object Const {
        const val MATRIX_SIZE = 16
    }
    /* 回転行列 */
    private var inR = FloatArray(Const.MATRIX_SIZE)
    private var outR = FloatArray(Const.MATRIX_SIZE)
    private var I = FloatArray(Const.MATRIX_SIZE)

    /* センサーの値 */
    private val orientationValues = FloatArray(3)
    private var magneticValues: FloatArray? = FloatArray(3)
    private var accelerometerValues: FloatArray? = FloatArray(3)

    override fun onSensorChanged(event: SensorEvent) {
        if (event.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) return

        when (event.sensor.type) {
            Sensor.TYPE_MAGNETIC_FIELD ->
                magneticValues = event.values.clone()
            Sensor.TYPE_ACCELEROMETER ->
                accelerometerValues = event.values.clone()
        }

        if (magneticValues != null && accelerometerValues != null) {
            SensorManager.getRotationMatrix(inR, I, accelerometerValues, magneticValues)
            SensorManager.remapCoordinateSystem(inR, SensorManager.AXIS_X, SensorManager.AXIS_Z, outR)
            SensorManager.getOrientation(outR, orientationValues)

            mTvHorizon.text = getString(R.string.fmt_degree_horizon, Math.abs(Math.toDegrees(orientationValues[2].toDouble()) + 90))       //Y軸方向,roll
            mTvFace.text = getString(R.string.fmt_degree_face, Math.toDegrees(orientationValues[1].toDouble()))       //Y軸方向,roll
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, i: Int) {}

}