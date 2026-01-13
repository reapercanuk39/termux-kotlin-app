package com.termux.shared.activities

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar

import com.termux.shared.interact.ShareUtils
import com.termux.shared.logger.Logger
import com.termux.shared.R
import com.termux.shared.models.TextIOInfo
import com.termux.shared.view.KeyboardUtils

import java.util.Locale

/**
 * An activity to edit or view text based on config passed as [TextIOInfo].
 *
 * Add Following to `AndroidManifest.xml` to use in an app:
 *
 * `<activity android:name="com.termux.shared.activities.TextIOActivity" android:theme="@style/Theme.AppCompat.TermuxTextIOActivity" />`
 */
open class TextIOActivity : AppCompatActivity() {

    private lateinit var mTextIOLabel: TextView
    private lateinit var mTextIOLabelSeparator: View
    private lateinit var mTextIOText: EditText
    private lateinit var mTextIOHorizontalScrollView: HorizontalScrollView
    private lateinit var mTextIOTextLinearLayout: LinearLayout
    private lateinit var mTextIOTextCharacterUsage: TextView

    private var mTextIOInfo: TextIOInfo? = null
    private var mBundle: Bundle? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Logger.logVerbose(LOG_TAG, "onCreate")

        setContentView(R.layout.activity_text_io)

        mTextIOLabel = findViewById(R.id.text_io_label)
        mTextIOLabelSeparator = findViewById(R.id.text_io_label_separator)
        mTextIOText = findViewById(R.id.text_io_text)
        mTextIOHorizontalScrollView = findViewById(R.id.text_io_horizontal_scroll_view)
        mTextIOTextLinearLayout = findViewById(R.id.text_io_text_linear_layout)
        mTextIOTextCharacterUsage = findViewById(R.id.text_io_text_character_usage)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        if (toolbar != null) {
            setSupportActionBar(toolbar)
        }

        mBundle = null
        val intent = intent
        if (intent != null)
            mBundle = intent.extras
        else if (savedInstanceState != null)
            mBundle = savedInstanceState

        updateUI()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Logger.logVerbose(LOG_TAG, "onNewIntent")

        // Views must be re-created since different configs for isEditingTextDisabled() and
        // isHorizontallyScrollable() will not work or at least reliably
        finish()
        startActivity(intent)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun updateUI() {
        val bundle = mBundle ?: run {
            finish()
            return
        }

        @Suppress("DEPRECATION")
        val textIOInfo = bundle.getSerializable(EXTRA_TEXT_IO_INFO_OBJECT) as? TextIOInfo ?: run {
            finish()
            return
        }
        mTextIOInfo = textIOInfo

        supportActionBar?.let { actionBar ->
            if (textIOInfo.title != null)
                actionBar.title = textIOInfo.title
            else
                actionBar.title = "Text Input"

            if (textIOInfo.shouldShowBackButtonInActionBar()) {
                actionBar.setDisplayHomeAsUpEnabled(true)
                actionBar.setDisplayShowHomeEnabled(true)
            }
        }

        mTextIOLabel.visibility = View.GONE
        mTextIOLabelSeparator.visibility = View.GONE
        if (textIOInfo.isLabelEnabled) {
            mTextIOLabel.visibility = View.VISIBLE
            mTextIOLabelSeparator.visibility = View.VISIBLE
            mTextIOLabel.text = textIOInfo.label
            mTextIOLabel.filters = arrayOf<InputFilter>(InputFilter.LengthFilter(TextIOInfo.LABEL_SIZE_LIMIT_IN_BYTES))
            mTextIOLabel.textSize = textIOInfo.labelSize.toFloat()
            mTextIOLabel.setTextColor(textIOInfo.labelColor)
            mTextIOLabel.typeface = Typeface.create(textIOInfo.labelTypeFaceFamily, textIOInfo.labelTypeFaceStyle)
        }

        if (textIOInfo.isHorizontallyScrollable) {
            mTextIOHorizontalScrollView.isEnabled = true
            mTextIOText.setHorizontallyScrolling(true)
        } else {
            // Remove mTextIOHorizontalScrollView and add mTextIOText in its place
            val parent = mTextIOHorizontalScrollView.parent as? ViewGroup
            if (parent != null && parent.indexOfChild(mTextIOText) < 0) {
                val params = mTextIOHorizontalScrollView.layoutParams
                val index = parent.indexOfChild(mTextIOHorizontalScrollView)
                mTextIOTextLinearLayout.removeAllViews()
                mTextIOHorizontalScrollView.removeAllViews()
                parent.removeView(mTextIOHorizontalScrollView)
                parent.addView(mTextIOText, index, params)
                mTextIOText.setHorizontallyScrolling(false)
            }
        }

        mTextIOText.setText(textIOInfo.text)
        mTextIOText.filters = arrayOf<InputFilter>(InputFilter.LengthFilter(textIOInfo.textLengthLimit))
        mTextIOText.textSize = textIOInfo.textSize.toFloat()
        mTextIOText.setTextColor(textIOInfo.textColor)
        mTextIOText.typeface = Typeface.create(textIOInfo.textTypeFaceFamily, textIOInfo.textTypeFaceStyle)

        // setTextIsSelectable must be called after changing KeyListener to regain focusability and selectivity
        if (textIOInfo.isEditingTextDisabled) {
            mTextIOText.isCursorVisible = false
            mTextIOText.keyListener = null
            mTextIOText.setTextIsSelectable(true)
        }

        if (textIOInfo.shouldShowTextCharacterUsage()) {
            mTextIOTextCharacterUsage.visibility = View.VISIBLE
            updateTextIOTextCharacterUsage(textIOInfo.text)

            mTextIOText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(editable: Editable?) {
                    editable?.let {
                        updateTextIOTextCharacterUsage(it.toString())
                    }
                }
            })
        } else {
            mTextIOTextCharacterUsage.visibility = View.GONE
            mTextIOText.addTextChangedListener(null)
        }
    }

    private fun updateTextIOInfoText() {
        mTextIOInfo?.text = mTextIOText.text.toString()
    }

    private fun updateTextIOTextCharacterUsage(text: String?) {
        val actualText = text ?: ""
        mTextIOTextCharacterUsage.text = String.format(Locale.getDefault(), "%1\$d/%2\$d", actualText.length, mTextIOInfo?.textLengthLimit ?: 0)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        updateTextIOInfoText()
        outState.putSerializable(EXTRA_TEXT_IO_INFO_OBJECT, mTextIOInfo)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_text_io, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val text = mTextIOText.text.toString()

        when (item.itemId) {
            android.R.id.home -> confirm()
            R.id.menu_item_cancel -> cancel()
            R.id.menu_item_share_text -> ShareUtils.shareText(this, mTextIOInfo?.title, text)
            R.id.menu_item_copy_text -> ShareUtils.copyTextToClipboard(this, text, null)
        }

        return false
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        confirm()
    }

    /** Confirm current text and send it back to calling [Activity]. */
    private fun confirm() {
        updateTextIOInfoText()
        KeyboardUtils.hideSoftKeyboard(this, mTextIOText)
        setResult(Activity.RESULT_OK, getResultIntent())
        finish()
    }

    /** Cancel current text and notify calling [Activity]. */
    private fun cancel() {
        KeyboardUtils.hideSoftKeyboard(this, mTextIOText)
        setResult(Activity.RESULT_CANCELED, getResultIntent())
        finish()
    }

    private fun getResultIntent(): Intent {
        val intent = Intent()
        val bundle = Bundle()
        bundle.putSerializable(EXTRA_TEXT_IO_INFO_OBJECT, mTextIOInfo)
        intent.putExtras(bundle)
        return intent
    }

    companion object {
        private val CLASS_NAME = ReportActivity::class.java.canonicalName
        @JvmField
        val EXTRA_TEXT_IO_INFO_OBJECT = CLASS_NAME + ".EXTRA_TEXT_IO_INFO_OBJECT"

        private const val LOG_TAG = "TextIOActivity"

        /**
         * Get the [Intent] that can be used to start the [TextIOActivity].
         *
         * @param context The [Context] for operations.
         * @param textIOInfo The [TextIOInfo] containing info for the edit text.
         */
        @JvmStatic
        fun newInstance(context: Context, textIOInfo: TextIOInfo): Intent {
            val intent = Intent(context, TextIOActivity::class.java)
            val bundle = Bundle()
            bundle.putSerializable(EXTRA_TEXT_IO_INFO_OBJECT, textIOInfo)
            intent.putExtras(bundle)
            return intent
        }
    }
}
