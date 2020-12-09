package com.insprout.okubo.kttool

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.NumberPicker
import androidx.appcompat.app.AppCompatActivity

import com.insprout.okubo.kttool.util.SdkUtils


class MainActivity : AppCompatActivity(), View.OnClickListener, DialogUi.DialogEventListener  {
    companion object {
        const val REQ_ADJUST_SCALE = 100

        const val MAX_ADJUST_MILLI_VALUE = 50
    }

    private lateinit var mFlashLight: FlashLight

    private val mRulerView: RulerView by lazy { findViewById<RulerView>(R.id.v_ruler) }
    private val mButton: View by lazy { findViewById<View>(R.id.btn_flash) }

    private val mAdjustRateValues: IntArray = IntArray (MAX_ADJUST_MILLI_VALUE * 2 + 1) {
        it - MAX_ADJUST_MILLI_VALUE
    }
    private val mAdjustRateLabels: Array<String> by lazy {
        // Context#getString() メソッドを呼び出すので by lazyで初期化する
        Array<String>( mAdjustRateValues.size ) {
            getString(R.string.fmt_adjust_scale, mAdjustRateValues[it] / 10.0f)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initVars()
        initView()
    }

    private fun initVars() {
        mFlashLight = FlashLight.getInstance(this)
    }

    private fun initView() {
        mRulerView.apply {
            lineColor = Color.BLUE
            textColor = Color.GRAY
            adjustRate = convertRate(Settings.getAdjustRate(this@MainActivity))
        }
        mButton.isSelected = mFlashLight.isFlashing
    }

    private val convertRate: (Int) -> Float = { 1.0f + it / 1000.0f }

    override fun onDestroy() {
        mFlashLight.release()
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_adjust_scale -> {
                // スケール補正ダイアログを表示する
                DialogUi.Builder(this).apply {
                    setTitle(R.string.action_adjust_scale)
                    setMessage(R.string.msg_adjust_scale)
                    setView(R.layout.dlg_adjust_scale)
                    setPositiveButton()
                    setNegativeButton()
                    setRequestCode(REQ_ADJUST_SCALE)
                }.show()
                true
            }

            else ->
                super.onOptionsItemSelected(item)
        }
    }

    override fun onClick(view: View?) {
        when (view?.id) {
            R.id.btn_flash -> {
                val flashing : Boolean = mFlashLight.toggle()
                mButton.isSelected = flashing
            }

            R.id.btn_viewer -> {
                TextViewerActivity.startActivity(this)
            }

            R.id.btn_angle -> {
                if (SdkUtils.requestRuntimePermissions(this, Settings.PERMISSIONS_CAMERA, Settings.REQUEST_PERMIT_CAMERA)) {
                    HorizonMeterActivity.startActivity(this)
                }
            }

            R.id.btn_camera -> {
                if (SdkUtils.requestRuntimePermissions(this, Settings.PERMISSIONS_PHOTO, Settings.REQUEST_PERMIT_PHOTO)) {
                    PhotoActivity.startActivity(this)
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            Settings.REQUEST_PERMIT_CAMERA -> {
                if (SdkUtils.isGranted(grantResults)) {
                    HorizonMeterActivity.startActivity(this)
                }
            }
            Settings.REQUEST_PERMIT_PHOTO -> {
                if (SdkUtils.isGranted(grantResults)) {
                    PhotoActivity.startActivity(this)
                }
            }
        }
    }

    override fun onDialogEvent(requestCode: Int, dialog: AlertDialog, which: Int, view: View?) {
        when (requestCode) {
            REQ_ADJUST_SCALE -> {
                when (which) {
                // カスタムDialog 作成イベント
                    DialogUi.EVENT_DIALOG_CREATED -> {
                        view?.findViewById<NumberPicker>(R.id.np_adjust)?.apply {
                            // 子Viewなどの初期化
                            // Pickerの選択肢を設定する
                            this.maxValue = mAdjustRateLabels.size - 1
                            this.minValue = 0
                            this.displayedValues = mAdjustRateLabels
                            this.wrapSelectorWheel = false
                            // 保存されている設定値を 反映しておく
                            mAdjustRateValues.indexOf(Settings.getAdjustRate(this@MainActivity))
                                    .takeIf { it >= 0 }?.let { this.value = it }
                        }
                    }

                // OKボタン押下
                    DialogUi.EVENT_BUTTON_POSITIVE ->
                        view?.findViewById<NumberPicker>(R.id.np_adjust)?.apply {
                            mAdjustRateValues[ this.value ].let {
                                Settings.putAdjustRate(this@MainActivity, it)
                                mRulerView.adjustRate = convertRate(it)
                                mRulerView.invalidate()     // 再描画
                            }
                        }
                }
            }
        }
    }

}
