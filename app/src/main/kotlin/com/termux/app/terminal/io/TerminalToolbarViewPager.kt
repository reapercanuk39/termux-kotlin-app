package com.termux.app.terminal.io

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import com.termux.kotlin.R
import com.termux.app.TermuxActivity
import com.termux.shared.termux.extrakeys.ExtraKeysView

object TerminalToolbarViewPager {

    class PageAdapter(
        private val mActivity: TermuxActivity,
        private var mSavedTextInput: String?
    ) : PagerAdapter() {

        override fun getCount(): Int = 2

        override fun isViewFromObject(view: View, `object`: Any): Boolean {
            return view === `object`
        }

        override fun instantiateItem(collection: ViewGroup, position: Int): Any {
            val inflater = LayoutInflater.from(mActivity)
            val layout: View
            if (position == 0) {
                layout = inflater.inflate(R.layout.view_terminal_toolbar_extra_keys, collection, false)
                val extraKeysView = layout as ExtraKeysView
                extraKeysView.extraKeysViewClient = mActivity.termuxTerminalExtraKeys
                extraKeysView.setButtonTextAllCaps(mActivity.properties?.shouldExtraKeysTextBeAllCaps() == true)
                mActivity.setExtraKeysView(extraKeysView)
                extraKeysView.reload(
                    mActivity.termuxTerminalExtraKeys?.getExtraKeysInfo(),
                    mActivity.terminalToolbarDefaultHeight
                )

                // apply extra keys fix if enabled in prefs
                if (mActivity.properties?.isUsingFullScreen() == true && mActivity.properties?.isUsingFullScreenWorkAround() == true) {
                    FullScreenWorkAround.apply(mActivity)
                }
            } else {
                layout = inflater.inflate(R.layout.view_terminal_toolbar_text_input, collection, false)
                val editText = layout.findViewById<EditText>(R.id.terminal_toolbar_text_input)

                if (mSavedTextInput != null) {
                    editText.setText(mSavedTextInput)
                    mSavedTextInput = null
                }

                editText.setOnEditorActionListener { _, _, _ ->
                    val session = mActivity.currentSession
                    if (session != null) {
                        if (session.isRunning) {
                            var textToSend = editText.text.toString()
                            if (textToSend.isEmpty()) textToSend = "\r"
                            session.write(textToSend)
                        } else {
                            mActivity.termuxTerminalSessionClient?.removeFinishedSession(session)
                        }
                        editText.setText("")
                    }
                    true
                }
            }
            collection.addView(layout)
            return layout
        }

        override fun destroyItem(collection: ViewGroup, position: Int, view: Any) {
            collection.removeView(view as View)
        }
    }

    class OnPageChangeListener(
        private val mActivity: TermuxActivity,
        private val mTerminalToolbarViewPager: ViewPager
    ) : ViewPager.SimpleOnPageChangeListener() {

        override fun onPageSelected(position: Int) {
            if (position == 0) {
                mActivity.terminalView.requestFocus()
            } else {
                val editText = mTerminalToolbarViewPager.findViewById<EditText>(R.id.terminal_toolbar_text_input)
                editText?.requestFocus()
            }
        }
    }
}
