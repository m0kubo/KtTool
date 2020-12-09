package com.insprout.okubo.kttool

import android.util.SparseArray
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ProgressBar
import android.view.Gravity
import android.content.DialogInterface
import android.view.LayoutInflater
import android.os.Bundle
import android.app.Activity
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context
import android.content.res.Resources
import android.view.View
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager


class DialogUi {

    companion object {

        //////////////////////////
        //
        // public 定数
        //

        // ダイアログのタイプ
        const val STYLE_ALERT_DIALOG = -1           // 通常ダイアログスタイル
        const val STYLE_PROGRESS_DIALOG = 0         // ProgressDialog風 スタイル

        // interface用 public 定数
        const val EVENT_BUTTON_POSITIVE = DialogInterface.BUTTON_POSITIVE
        const val EVENT_BUTTON_NEGATIVE = DialogInterface.BUTTON_NEGATIVE
        const val EVENT_BUTTON_NEUTRAL = DialogInterface.BUTTON_NEUTRAL
        const val EVENT_DIALOG_CREATED = -100
        const val EVENT_DIALOG_SHOWN = -101

        // パラメータ省略時のデフォルト値
        const val REQUEST_CODE_DEFAULT = -1


        //////////////////////////
        //
        // 内部使用 private 定数
        //

        // 引数受け渡し用のキー
        private const  val KEY_REQUEST_CODE = "dialog.REQUEST_CODE"
        private const val KEY_DIALOG_TITLE = "dialog.TITLE"
        private const val KEY_DIALOG_TEXT = "dialog.TEXT"
        private const val KEY_DIALOG_LAYOUT = "dialog.LAYOUT_ID"
        private const val KEY_DIALOG_ICON = "dialog.ICON_ID"
        private const val KEY_DIALOG_POSITIVE_BUTTON_TEXT = "button.positive.TEXT"
        private const val KEY_DIALOG_NEGATIVE_BUTTON_TEXT = "button.negative.TEXT"
        private const val KEY_DIALOG_NEUTRAL_BUTTON_TEXT = "button.neutral.TEXT"
        private const val KEY_CHOICE_TYPE = "choice.TYPE"
        private const val KEY_CHOICE_ARRAY = "choice.ARRAY"
        private const val KEY_CHOICE_SELECTED = "choice.SELECTED"
        private const val KEY_CHOICE_SELECTED_ITEMS = "choice.SELECTED_ITEMS"
        private const val KEY_PROGRESS_STYLE = "progress.STYLE"

        // ListViewの選択方法
        private const val LIST_TYPE_NO_CHOICE = 0           // ListView (checkBoxなし)
        private const val LIST_TYPE_SINGLE_CHOICE = 1       // ListView (単一選択)
        private const val LIST_TYPE_MULTI_CHOICE = 2        // ListView (複数選択)

        // パラメータ省略時のデフォルト値
        private const val ID_LAYOUT_DEFAULT = -1
        private const val ID_STRING_DEFAULT_OK = android.R.string.ok
        private const val ID_STRING_DEFAULT_CANCEL = android.R.string.cancel

        // Fragmentの タグの接頭句
        private const val TAG_PREFIX = "DIALOG_"


        //////////////////////////
        //
        // public functions

        /**
         * Dialog作成時に設定したリクエストコードで Dialogを 閉じる
         * @param requestCode 作成時に設定した リクエストコード
         */
        val dismiss = { requestCode: Int ->
            DialogUiFragment.dismiss(requestCode)
        }

        /**
         * Dialog作成時に設定したリクエストコードで DialogFragmentを 取得する
         * @param requestCode 作成時に設定した リクエストコード
         * @return DialogFragment
         */
        val getFragment = { requestCode: Int ->
            DialogUiFragment[requestCode]
        }
//        val getFragment: (Int) -> DialogFragment? = {
//            DialogUiFragment[it]
//        }

        //////////////////////////
        //
        // private functions

        private val getFragmentManager: (FragmentActivity) -> FragmentManager = {
            // Fragment関連の import宣言  API level11以降のみのサポートの場合
            it.supportFragmentManager
        }

        private val getString: (Context, Int) -> String? = { context, resourceId ->
            try {
                context.getString(resourceId)
            } catch (e: Resources.NotFoundException) {
                null
            }
        }

        private val getStringArrays: (Context, Int) -> Array<String>? = { context, resourceId ->
            try {
                context.resources.getStringArray(resourceId)
            } catch (e: Resources.NotFoundException) {
                null
            }
        }

//        private val getFragmentTag = { requestCode: Int ->
//            TAG_PREFIX + Integer.toHexString(requestCode)
//        }
        private val getFragmentTag: (Int) -> String = { TAG_PREFIX + Integer.toHexString(it) }

    }


    //////////////////////////
    //
    // interface
    //

    interface DialogEventListener {
        /**
         * AlertDialogの イベントを通知する
         *
         * whichは DialogInterface.OnClickListenerなどで返されるwhichの値
         * (POSITIVEボタン押下：-1、NEGATIVEボタン押下：-2、NEUTRALボタン押下：-3、リスト項目番号：0以上の整数)に
         * 加え、DialogFragment created：-100、DialogFragment shown：-101 が返される
         *
         * Listenerに渡される viewは Dialogの形態によって内容が異なる
         * - メッセージと ダイアログ標準ボタンのみの場合、常にnull
         * - 選択リストが設定されている場合、選択リストのListViewオブジェクト
         * - カスタムViewが設定されている場合、そのViewオブジェクト
         */
        fun onDialogEvent(requestCode: Int, dialog: AlertDialog, which: Int, view: View?)
    }



    ////////////////////////////////////
    //
    // Builderクラス
    //

    class Builder {
        private var mActivity: FragmentActivity

        private var mRequestCode = REQUEST_CODE_DEFAULT
        private var mLayoutId = ID_LAYOUT_DEFAULT
        private var mDialogStyle = STYLE_ALERT_DIALOG

        private var mTitle: String? = null
        private var mMessage: String? = null
        private var mLabelPositive: String? = null
        private var mLabelNegative: String? = null
        private var mLabelNeutral: String? = null

        private var mListType = LIST_TYPE_SINGLE_CHOICE
        private var mListItems: Array<String>? = null
        private var mCheckedItem = -1
        private var mCheckedList: BooleanArray? = null
        private var mIconId = -1


        constructor(activity: FragmentActivity) {
            mActivity = activity
        }

        constructor(activity: FragmentActivity, dialogStyle: Int) {
            mActivity = activity
            mDialogStyle = dialogStyle
        }

        fun setRequestCode(requestCode: Int): Builder {
            mRequestCode = requestCode
            return this
        }

        fun setView(layoutId: Int): Builder {
            mLayoutId = layoutId
            return this
        }

        fun setTitle(title: String?): Builder {
            mTitle = title
            return this
        }

        fun setTitle(titleId: Int): Builder {
            return setTitle(getString(mActivity, titleId))
        }

        fun setMessage(message: String?): Builder {
            mMessage = message
            return this
        }

        fun setMessage(messageId: Int): Builder {
            return setMessage(getString(mActivity, messageId))
        }

        fun setIcon(iconId: Int): Builder {
            mIconId = iconId
            return this
        }

        fun setPositiveButton(text: String? = getString(mActivity, ID_STRING_DEFAULT_OK)): Builder {
            mLabelPositive = text
            return this
        }

        fun setPositiveButton(textId: Int): Builder {
            return setPositiveButton(getString(mActivity, textId))
        }

        fun setNegativeButton(text: String? = getString(mActivity, ID_STRING_DEFAULT_CANCEL)): Builder {
            mLabelNegative = text
            return this
        }

        fun setNegativeButton(textId: Int): Builder {
            return setNegativeButton(getString(mActivity, textId))
        }

        fun setNeutralButton(text: String?): Builder {
            mLabelNeutral = text
            return this
        }

        fun setNeutralButton(textId: Int): Builder {
            return setNeutralButton(getString(mActivity, textId))
        }

        fun setItems(items: Array<String>?): Builder {
            mListType = LIST_TYPE_NO_CHOICE
            mListItems = items
            return this
        }

        fun setItems(itemsId: Int): Builder {
            return setItems(getStringArrays(mActivity, itemsId))
        }

        fun setSingleChoiceItems(items: Array<String>?, checkedItem: Int = -1): Builder {
            mListType = LIST_TYPE_SINGLE_CHOICE
            mListItems = items
            mCheckedItem = checkedItem
            return this
        }

        fun setSingleChoiceItems(itemsId: Int, checkedItem: Int = -1): Builder {
            return setSingleChoiceItems(getStringArrays(mActivity, itemsId), checkedItem)
        }

        fun setMultiChoiceItems(items: Array<String>?, checkedItems: BooleanArray? = null): Builder {
            mListType = LIST_TYPE_MULTI_CHOICE
            mListItems = items
            mCheckedList = checkedItems
            return this
        }

        fun setMultiChoiceItems(itemsId: Int, checkedItems: BooleanArray? = null): Builder {
            return setMultiChoiceItems(getStringArrays(mActivity, itemsId), checkedItems)
        }

        fun create(): DialogFragment {
            return DialogUiFragment().apply {
                this.arguments =  Bundle().apply {
                    putInt(KEY_REQUEST_CODE, mRequestCode)
                    putInt(KEY_DIALOG_LAYOUT, mLayoutId)
                    putInt(KEY_PROGRESS_STYLE, mDialogStyle)
                    // ダイアログ共通設定
                    putString(KEY_DIALOG_TITLE, mTitle)
                    putString(KEY_DIALOG_TEXT, mMessage)
                    putInt(KEY_DIALOG_ICON, mIconId)
                    putString(KEY_DIALOG_POSITIVE_BUTTON_TEXT, mLabelPositive)
                    putString(KEY_DIALOG_NEGATIVE_BUTTON_TEXT, mLabelNegative)
                    putString(KEY_DIALOG_NEUTRAL_BUTTON_TEXT, mLabelNeutral)
                    // ListViewダイアログ用設定
                    putInt(KEY_CHOICE_TYPE, mListType)
                    putStringArray(KEY_CHOICE_ARRAY, mListItems)
                    putInt(KEY_CHOICE_SELECTED, mCheckedItem)
                    putBooleanArray(KEY_CHOICE_SELECTED_ITEMS, mCheckedList)
                }
            }
        }

        fun show(): DialogFragment {
            return create().apply {
                this.show(getFragmentManager(mActivity), getFragmentTag(mRequestCode))
            }
        }

    }


    ////////////////////////////////////
    //
    // 基本DialogFragmentクラス
    //

    class DialogUiFragment : DialogFragment(), DialogInterface.OnClickListener, DialogInterface.OnCancelListener, DialogInterface.OnShowListener, DialogInterface.OnMultiChoiceClickListener {
        private object Const {
            const val DIP_PADDING_PROGRESS = 15.0f
        }
        private val mInflater by lazy { activity!!.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater }

        private lateinit var mAlertDialog: AlertDialog
        private var mStyle = STYLE_ALERT_DIALOG
        private var mCustomView: View? = null
        private var mListener: DialogEventListener? = null
        private var mRequestCode: Int = 0
        private var mChoiceList: Array<String>? = null

        override fun onAttach(context: Context) {
            super.onAttach(context)
            if (context is DialogEventListener) {
                mListener = context
            }
        }

        @Suppress("DEPRECATION")
        override fun onAttach(activity: Activity) {
            super.onAttach(activity)
            if (activity is DialogEventListener) {
                mListener = activity
            }
        }

        override fun onDestroyView() {
            sMapFragment.remove(mRequestCode)
            super.onDestroyView()
        }

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            mRequestCode = arguments?.getInt(KEY_REQUEST_CODE) ?: -1
            mStyle = arguments?.getInt(KEY_PROGRESS_STYLE, STYLE_ALERT_DIALOG) ?: STYLE_ALERT_DIALOG
            mChoiceList = arguments?.getStringArray(KEY_CHOICE_ARRAY)
            sMapFragment.put(mRequestCode, this)

            val title = arguments?.getString(KEY_DIALOG_TITLE)
            var message = arguments?.getString(KEY_DIALOG_TEXT)
            val iconId = arguments?.getInt(KEY_DIALOG_ICON) ?: 0
            val buttonOk = arguments?.getString(KEY_DIALOG_POSITIVE_BUTTON_TEXT)
            val buttonCancel = arguments?.getString(KEY_DIALOG_NEGATIVE_BUTTON_TEXT)
            val buttonNeutral = arguments?.getString(KEY_DIALOG_NEUTRAL_BUTTON_TEXT)

            val listType = arguments?.getInt(KEY_CHOICE_TYPE, LIST_TYPE_NO_CHOICE)
            val selected = arguments?.getInt(KEY_CHOICE_SELECTED, -1) ?: -1
            val selectedArray = arguments?.getBooleanArray(KEY_CHOICE_SELECTED_ITEMS)
            val layoutId = arguments?.getInt(KEY_DIALOG_LAYOUT, ID_LAYOUT_DEFAULT) ?: ID_LAYOUT_DEFAULT

            mAlertDialog = AlertDialog.Builder(activity).also { builder ->
                when {
                    // プログレスダイアログ風の場合
                    mStyle == STYLE_PROGRESS_DIALOG -> {
                        builder.setView(buildProgressDialog(context, message))
                        // messageは ProgressDialog(風の)Viewで表示するので、AlertDialogオリジナルの setMessage()を行わないようにする。
                        message = null
                    }

                    // カスタムダイアログの場合
                    layoutId != ID_LAYOUT_DEFAULT -> {
                        mCustomView = mInflater.inflate(layoutId, null)
                        // カスタムViewを設定
                        builder.setView(mCustomView)
                    }

                    // 選択リストが指定されている場合
                    else -> {
                        mChoiceList?.let {
                            // setMessage()を実行すると setSingleChoiceItems()等が無視されるので、
                            // setSingleChoiceItems()等を呼び出す場合は、setMessage()を行わないようにする。
                            message = null

                            // 選択リストを設定
                            when (listType) {
                                // 単一指定ListView
                                LIST_TYPE_SINGLE_CHOICE ->
                                    builder.setSingleChoiceItems(mChoiceList, selected, this)

                                // 複数指定ListView
                                LIST_TYPE_MULTI_CHOICE ->
                                    builder.setMultiChoiceItems(mChoiceList,
                                            if (selectedArray != null && selectedArray.size < it.size) {
                                                // multiChoiceListの場合、初期選択状態を示す配列は選択項目数分指定していないと例外でおちる
                                                // 不足している場合は落ちないように、配列の要素を増やす
                                                BooleanArray(it.size).apply {
                                                    System.arraycopy(selectedArray, 0, this, 0, selectedArray.size)
                                                }
                                            } else {
                                                selectedArray   // nullでも可
                                            },
                                            this
                                    )

                                // checkboxなしListView
                                else ->
                                    builder.setItems(mChoiceList, this)
                            }
                        }
                    }
                }
                builder.setTitle(title)
                builder.setMessage(message)        // messageと カスタムViewは両立する
                if (iconId > 0) builder.setIcon(iconId)

                // ダイアログボタンを設定
                buttonOk?.let { builder.setPositiveButton(it, this) }
                buttonCancel?.let { builder.setNegativeButton(it, this) }
                buttonNeutral?.let { builder.setNeutralButton(it, this) }
                isCancelable = (buttonCancel != null)               // キャンセルボタンがない場合は、Backキーによるキャンセルも無効

            }.create()

            mAlertDialog.setCanceledOnTouchOutside(false)
            mAlertDialog.setOnShowListener(this)

            // Dialogが createされた事をListenerに通知する。(主にカスタムViewの初期化処理のため)
            callbackToListener(EVENT_DIALOG_CREATED)

            return mAlertDialog
        }

        override fun onClick(dialog: DialogInterface, which: Int) {
            callbackToListener(which)
        }

        override fun onClick(dialogInterface: DialogInterface, which: Int, b: Boolean) {
            callbackToListener(which)
        }

        override fun onCancel(dialog: DialogInterface) {
            super.onCancel(dialog)
            callbackToListener(EVENT_BUTTON_NEGATIVE)
        }

        private val callbackToListener = { which: Int ->
            mListener?.onDialogEvent(
                    mRequestCode,
                    mAlertDialog,
                    which,
                    when {
                        mCustomView != null -> mCustomView
                        mChoiceList != null -> mAlertDialog.listView
                        else -> null
                    })
        }

        override fun onShow(dialogInterface: DialogInterface) {
            callbackToListener(EVENT_DIALOG_SHOWN)
        }

        private val buildProgressDialog: (Context?, String?) -> View = { context, message ->
            // ダイアログのレイアウトを設定
            LinearLayout(context).apply {
                val pxPadding = (Const.DIP_PADDING_PROGRESS * resources.displayMetrics.density).toInt()

                orientation = LinearLayout.HORIZONTAL
                setVerticalGravity(Gravity.CENTER_VERTICAL)
                setPadding(pxPadding, pxPadding, pxPadding, pxPadding)

                // プログレスバー表示 追加
                addView(ProgressBar(context))

                // メッセージ表示 追加
                addView(TextView(context).apply {
                    text = message
                    setPadding(pxPadding, 0, 0, 0)          // progress表示と メッセージの間もマージンをあける
                })
            }
        }

        companion object {

            ////////////////////////////////////
            //
            // static メンバー/クラス
            //

            private val sMapFragment = SparseArray<DialogUiFragment>()

            /**
             * リクエストコードを指定して DialogUiFragmentのインスタンスを取得する
             * (画面の回転などで Fragmentが自動で再作成された場合、タグ名指定でのFragment取得ができなくなるためこのメソッドを用意)
             * @param requestCode リクエストコード
             * @return DialogUiFragmentのインスタンス
             */
            operator fun get(requestCode: Int): DialogFragment? {
                return sMapFragment[requestCode]
            }

            /**
             * リクエストコードを指定して DialogUiFragmentを 非表示にする
             * @param requestCode リクエストコード
             */
            val dismiss = { requestCode: Int ->
                get(requestCode)?.dismissAllowingStateLoss()
            }
        }
    }

}