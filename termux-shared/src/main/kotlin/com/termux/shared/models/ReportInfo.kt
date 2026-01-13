package com.termux.shared.models

import androidx.annotation.Keep
import com.termux.shared.markdown.MarkdownUtils
import com.termux.shared.android.AndroidUtils
import java.io.Serializable

/**
 * An object that stored info for [com.termux.shared.activities.ReportActivity].
 */
open class ReportInfo(
    /** The user action that was being processed for which the report was generated. */
    @JvmField val userAction: String?,
    /** The internal app component that sent the report. */
    @JvmField val sender: String?,
    /** The report title. */
    @JvmField val reportTitle: String?
) : Serializable {

    /** The timestamp for the report. */
    @JvmField
    val reportTimestamp: String = AndroidUtils.getCurrentMilliSecondUTCTimeStamp()

    /** The markdown report text prefix. Will not be part of copy and share operations, etc. */
    @JvmField
    var reportStringPrefix: String? = null

    /** The markdown report text. */
    @JvmField
    var reportString: String? = null

    /** The markdown report text suffix. Will not be part of copy and share operations, etc. */
    @JvmField
    var reportStringSuffix: String? = null

    /** If set to true, then report header info will be added to the report when markdown is generated. */
    @JvmField
    var addReportInfoHeaderToMarkdown = false

    /** The label for the report file to save if user selects menu_item_save_report_to_file. */
    @JvmField
    var reportSaveFileLabel: String? = null

    /** The path for the report file to save if user selects menu_item_save_report_to_file. */
    @JvmField
    var reportSaveFilePath: String? = null

    fun setReportStringPrefix(reportStringPrefix: String?) {
        this.reportStringPrefix = reportStringPrefix
    }

    fun setReportString(reportString: String?) {
        this.reportString = reportString
    }

    fun setReportStringSuffix(reportStringSuffix: String?) {
        this.reportStringSuffix = reportStringSuffix
    }

    fun setAddReportInfoHeaderToMarkdown(addReportInfoHeaderToMarkdown: Boolean) {
        this.addReportInfoHeaderToMarkdown = addReportInfoHeaderToMarkdown
    }

    fun setReportSaveFileLabel(reportSaveFileLabel: String?) {
        this.reportSaveFileLabel = reportSaveFileLabel
    }

    fun setReportSaveFilePath(reportSaveFilePath: String?) {
        this.reportSaveFilePath = reportSaveFilePath
    }

    fun setReportSaveFileLabelAndPath(reportSaveFileLabel: String?, reportSaveFilePath: String?) {
        this.reportSaveFileLabel = reportSaveFileLabel
        this.reportSaveFilePath = reportSaveFilePath
    }

    companion object {
        /**
         * Explicitly define `serialVersionUID` to prevent exceptions on deserialization.
         */
        @Keep
        private const val serialVersionUID = 1L

        /**
         * Get a markdown [String] for [ReportInfo].
         *
         * @param reportInfo The [ReportInfo] to convert.
         * @return Returns the markdown [String].
         */
        @JvmStatic
        fun getReportInfoMarkdownString(reportInfo: ReportInfo?): String {
            if (reportInfo == null) return "null"

            val markdownString = StringBuilder()

            if (reportInfo.addReportInfoHeaderToMarkdown) {
                markdownString.append("## Report Info\n\n")
                markdownString.append("\n").append(MarkdownUtils.getSingleLineMarkdownStringEntry("User Action", reportInfo.userAction, "-"))
                markdownString.append("\n").append(MarkdownUtils.getSingleLineMarkdownStringEntry("Sender", reportInfo.sender, "-"))
                markdownString.append("\n").append(MarkdownUtils.getSingleLineMarkdownStringEntry("Report Timestamp", reportInfo.reportTimestamp, "-"))
                markdownString.append("\n##\n\n")
            }

            markdownString.append(reportInfo.reportString)

            return markdownString.toString()
        }
    }
}
