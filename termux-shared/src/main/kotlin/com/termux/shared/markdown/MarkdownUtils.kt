package com.termux.shared.markdown

import android.content.Context
import android.graphics.Typeface
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.BackgroundColorSpan
import android.text.style.BulletSpan
import android.text.style.QuoteSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.util.Linkify
import androidx.core.content.ContextCompat
import com.google.common.base.Strings
import com.termux.shared.R
import com.termux.shared.theme.ThemeUtils
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonSpansFactory
import io.noties.markwon.MarkwonVisitor
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.node.BlockQuote
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.ListItem
import org.commonmark.node.StrongEmphasis
import java.util.regex.Pattern

object MarkdownUtils {

    const val backtick = "`"
    @JvmField
    val backticksPattern: Pattern = Pattern.compile("($backtick+)")

    /**
     * Get the markdown code [String] for a [String]. This ensures all backticks "`" are
     * properly escaped so that markdown does not break.
     */
    @JvmStatic
    fun getMarkdownCodeForString(string: String?, codeBlock: Boolean): String? {
        if (string == null) return null
        if (string.isEmpty()) return ""

        val maxConsecutiveBackTicksCount = getMaxConsecutiveBackTicksCount(string)

        // markdown requires surrounding backticks count to be at least one more than the count
        val backticksCountToUse = if (codeBlock) {
            maxConsecutiveBackTicksCount + 3
        } else {
            maxConsecutiveBackTicksCount + 1
        }

        val backticksToUse = Strings.repeat(backtick, backticksCountToUse)

        return if (codeBlock) {
            "$backticksToUse\n$string\n$backticksToUse"
        } else {
            var s = string
            // add a space to any prefixed or suffixed backtick characters
            if (s.startsWith(backtick)) s = " $s"
            if (s.endsWith(backtick)) s = "$s "
            "$backticksToUse$s$backticksToUse"
        }
    }

    /**
     * Get the max consecutive backticks "`" in a [String].
     */
    @JvmStatic
    fun getMaxConsecutiveBackTicksCount(string: String?): Int {
        if (string.isNullOrEmpty()) return 0

        var maxCount = 0
        val matcher = backticksPattern.matcher(string)
        while (matcher.find()) {
            val match = matcher.group(1)
            val matchCount = match?.length ?: 0
            if (matchCount > maxCount) maxCount = matchCount
        }

        return maxCount
    }

    @JvmStatic
    fun getLiteralSingleLineMarkdownStringEntry(label: String, obj: Any?, def: String): String {
        return "**$label**: ${obj?.toString() ?: def}  "
    }

    @JvmStatic
    fun getSingleLineMarkdownStringEntry(label: String, obj: Any?, def: String): String {
        return if (obj != null) {
            "**$label**: ${getMarkdownCodeForString(obj.toString(), false)}  "
        } else {
            "**$label**: $def  "
        }
    }

    @JvmStatic
    fun getMultiLineMarkdownStringEntry(label: String, obj: Any?, def: String): String {
        return if (obj != null) {
            "**$label**:\n${getMarkdownCodeForString(obj.toString(), true)}\n"
        } else {
            "**$label**: $def\n"
        }
    }

    @JvmStatic
    fun getLinkMarkdownString(label: String, url: String?): String {
        return if (url != null) {
            "[${label.replace("]", "\\]")}](${url.replace(")", "\\)")})"
        } else {
            label
        }
    }

    @JvmStatic
    fun getRecyclerMarkwonBuilder(context: Context): Markwon {
        return Markwon.builder(context)
            .usePlugin(LinkifyPlugin.create(Linkify.EMAIL_ADDRESSES or Linkify.WEB_URLS))
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureVisitor(builder: MarkwonVisitor.Builder) {
                    builder.on(FencedCodeBlock::class.java) { visitor, fencedCodeBlock ->
                        val code = visitor.configuration()
                            .syntaxHighlight()
                            .highlight(fencedCodeBlock.info, fencedCodeBlock.literal.trim())
                        visitor.builder().append(code)
                    }
                }

                override fun configureSpansFactory(builder: MarkwonSpansFactory.Builder) {
                    // Do not change color for night themes
                    if (!ThemeUtils.isNightModeEnabled(context)) {
                        builder.setFactory(Code::class.java) { _, _ ->
                            arrayOf(
                                BackgroundColorSpan(ContextCompat.getColor(context, R.color.background_markdown_code_inline))
                            )
                        }
                    }
                }
            })
            .build()
    }

    @JvmStatic
    fun getSpannedMarkwonBuilder(context: Context): Markwon {
        return Markwon.builder(context)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureSpansFactory(builder: MarkwonSpansFactory.Builder) {
                    builder
                        .setFactory(Emphasis::class.java) { _, _ -> StyleSpan(Typeface.ITALIC) }
                        .setFactory(StrongEmphasis::class.java) { _, _ -> StyleSpan(Typeface.BOLD) }
                        .setFactory(BlockQuote::class.java) { _, _ -> QuoteSpan() }
                        .setFactory(Strikethrough::class.java) { _, _ -> StrikethroughSpan() }
                        .setFactory(Code::class.java) { _, _ ->
                            arrayOf(
                                BackgroundColorSpan(ContextCompat.getColor(context, R.color.background_markdown_code_inline)),
                                TypefaceSpan("monospace"),
                                AbsoluteSizeSpan(48)
                            )
                        }
                        .setFactory(ListItem::class.java) { _, _ -> BulletSpan() }
                }
            })
            .build()
    }

    @JvmStatic
    fun getSpannedMarkdownText(context: Context?, string: String?): Spanned? {
        if (context == null || string == null) return null
        val markwon = getSpannedMarkwonBuilder(context)
        return markwon.toMarkdown(string)
    }
}
