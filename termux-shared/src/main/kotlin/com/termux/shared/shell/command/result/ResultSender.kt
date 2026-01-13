package com.termux.shared.shell.command.result

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle

import com.termux.kotlin.shared.R
import com.termux.shared.data.DataUtils
import com.termux.shared.markdown.MarkdownUtils
import com.termux.shared.errors.Error
import com.termux.shared.file.FileUtils
import com.termux.shared.logger.Logger
import com.termux.shared.errors.FunctionErrno
import com.termux.shared.android.AndroidUtils
import com.termux.shared.shell.command.ShellCommandConstants.RESULT_SENDER

object ResultSender {

    private const val LOG_TAG = "ResultSender"

    /**
     * Send result stored in [ResultConfig] to command caller via
     * [ResultConfig.resultPendingIntent] and/or by writing it to files in
     * [ResultConfig.resultDirectoryPath]. If both are not `null`, then result will be
     * sent via both.
     *
     * @param context The [Context] for operations.
     * @param logTag The log tag to use for logging.
     * @param label The label for the command.
     * @param resultConfig The [ResultConfig] object containing information on how to send the result.
     * @param resultData The [ResultData] object containing result data.
     * @param logStdoutAndStderr Set to `true` if [ResultData.stdout] and [ResultData.stderr]
     *                           should be logged.
     * @return Returns the [Error] if failed to send the result, otherwise `null`.
     */
    @JvmStatic
    fun sendCommandResultData(context: Context?, logTag: String?, label: String?, resultConfig: ResultConfig?, resultData: ResultData?, logStdoutAndStderr: Boolean): Error? {
        if (context == null || resultConfig == null || resultData == null)
            return FunctionErrno.ERRNO_NULL_OR_EMPTY_PARAMETERS.getError("context, resultConfig or resultData", "sendCommandResultData")

        if (resultConfig.resultPendingIntent != null) {
            val error = sendCommandResultDataWithPendingIntent(context, logTag, label, resultConfig, resultData, logStdoutAndStderr)
            if (error != null || resultConfig.resultDirectoryPath == null)
                return error
        }

        return if (resultConfig.resultDirectoryPath != null) {
            sendCommandResultDataToDirectory(context, logTag, label, resultConfig, resultData, logStdoutAndStderr)
        } else {
            FunctionErrno.ERRNO_UNSET_PARAMETERS.getError("resultConfig.resultPendingIntent or resultConfig.resultDirectoryPath", "sendCommandResultData")
        }
    }

    /**
     * Send result stored in [ResultConfig] to command caller via [ResultConfig.resultPendingIntent].
     *
     * @param context The [Context] for operations.
     * @param logTag The log tag to use for logging.
     * @param label The label for the command.
     * @param resultConfig The [ResultConfig] object containing information on how to send the result.
     * @param resultData The [ResultData] object containing result data.
     * @param logStdoutAndStderr Set to `true` if [ResultData.stdout] and [ResultData.stderr]
     *                           should be logged.
     * @return Returns the [Error] if failed to send the result, otherwise `null`.
     */
    @JvmStatic
    fun sendCommandResultDataWithPendingIntent(context: Context?, logTag: String?, label: String?, resultConfig: ResultConfig?, resultData: ResultData?, logStdoutAndStderr: Boolean): Error? {
        if (context == null || resultConfig == null || resultData == null || resultConfig.resultPendingIntent == null || resultConfig.resultBundleKey == null)
            return FunctionErrno.ERRNO_NULL_OR_EMPTY_PARAMETER.getError("context, resultConfig, resultData, resultConfig.resultPendingIntent or resultConfig.resultBundleKey", "sendCommandResultDataWithPendingIntent")

        val actualLogTag = DataUtils.getDefaultIfNull(logTag, LOG_TAG)
        val actualLabel = label ?: "unknown"

        Logger.logDebugExtended(actualLogTag ?: "", "Sending result for command \"$actualLabel\":\n$resultConfig\n${ResultData.getResultDataLogString(resultData, logStdoutAndStderr)}")

        var resultDataStdout = resultData.stdout.toString()
        var resultDataStderr = resultData.stderr.toString()

        var truncatedStdout: String? = null
        var truncatedStderr: String? = null

        val stdoutOriginalLength = resultDataStdout.length.toString()
        val stderrOriginalLength = resultDataStderr.length.toString()

        // Truncate stdout and stdout to max TRANSACTION_SIZE_LIMIT_IN_BYTES
        if (resultDataStderr.isEmpty()) {
            truncatedStdout = DataUtils.getTruncatedCommandOutput(resultDataStdout, DataUtils.TRANSACTION_SIZE_LIMIT_IN_BYTES, false, false, false)
        } else if (resultDataStdout.isEmpty()) {
            truncatedStderr = DataUtils.getTruncatedCommandOutput(resultDataStderr, DataUtils.TRANSACTION_SIZE_LIMIT_IN_BYTES, false, false, false)
        } else {
            truncatedStdout = DataUtils.getTruncatedCommandOutput(resultDataStdout, DataUtils.TRANSACTION_SIZE_LIMIT_IN_BYTES / 2, false, false, false)
            truncatedStderr = DataUtils.getTruncatedCommandOutput(resultDataStderr, DataUtils.TRANSACTION_SIZE_LIMIT_IN_BYTES / 2, false, false, false)
        }

        if (truncatedStdout != null && truncatedStdout.length < resultDataStdout.length) {
            Logger.logWarn(actualLogTag ?: "", "The result for command \"$actualLabel\" stdout length truncated from $stdoutOriginalLength to ${truncatedStdout.length}")
            resultDataStdout = truncatedStdout
        }

        if (truncatedStderr != null && truncatedStderr.length < resultDataStderr.length) {
            Logger.logWarn(actualLogTag ?: "", "The result for command \"$actualLabel\" stderr length truncated from $stderrOriginalLength to ${truncatedStderr.length}")
            resultDataStderr = truncatedStderr
        }

        var resultDataErrmsg: String? = null
        if (resultData.isStateFailed()) {
            resultDataErrmsg = ResultData.getErrorsListLogString(resultData)
            if (resultDataErrmsg.isEmpty()) resultDataErrmsg = null
        }

        val errmsgOriginalLength = resultDataErrmsg?.length?.toString()

        // Truncate error to max TRANSACTION_SIZE_LIMIT_IN_BYTES / 4
        // trim from end to preserve start of stacktraces
        val truncatedErrmsg = DataUtils.getTruncatedCommandOutput(resultDataErrmsg, DataUtils.TRANSACTION_SIZE_LIMIT_IN_BYTES / 4, true, false, false)
        if (truncatedErrmsg != null && resultDataErrmsg != null && truncatedErrmsg.length < resultDataErrmsg.length) {
            Logger.logWarn(actualLogTag ?: "", "The result for command \"$actualLabel\" error length truncated from $errmsgOriginalLength to ${truncatedErrmsg.length}")
            resultDataErrmsg = truncatedErrmsg
        }

        val resultBundle = Bundle()
        resultBundle.putString(resultConfig.resultStdoutKey, resultDataStdout)
        resultBundle.putString(resultConfig.resultStdoutOriginalLengthKey, stdoutOriginalLength)
        resultBundle.putString(resultConfig.resultStderrKey, resultDataStderr)
        resultBundle.putString(resultConfig.resultStderrOriginalLengthKey, stderrOriginalLength)
        if (resultData.exitCode != null)
            resultBundle.putInt(resultConfig.resultExitCodeKey, resultData.exitCode!!)
        resultBundle.putInt(resultConfig.resultErrCodeKey, resultData.errCode)
        resultBundle.putString(resultConfig.resultErrmsgKey, resultDataErrmsg)

        val resultIntent = Intent()
        resultIntent.putExtra(resultConfig.resultBundleKey, resultBundle)

        try {
            resultConfig.resultPendingIntent!!.send(context, Activity.RESULT_OK, resultIntent)
        } catch (e: PendingIntent.CanceledException) {
            // The caller doesn't want the result? That's fine, just ignore
            Logger.logDebug(actualLogTag ?: "", "The command \"$actualLabel\" creator ${resultConfig.resultPendingIntent!!.creatorPackage} does not want the results anymore")
        }

        return null
    }

    /**
     * Send result stored in [ResultConfig] to command caller by writing it to files in
     * [ResultConfig.resultDirectoryPath].
     *
     * @param context The [Context] for operations.
     * @param logTag The log tag to use for logging.
     * @param label The label for the command.
     * @param resultConfig The [ResultConfig] object containing information on how to send the result.
     * @param resultData The [ResultData] object containing result data.
     * @param logStdoutAndStderr Set to `true` if [ResultData.stdout] and [ResultData.stderr]
     *                           should be logged.
     * @return Returns the [Error] if failed to send the result, otherwise `null`.
     */
    @JvmStatic
    fun sendCommandResultDataToDirectory(context: Context?, logTag: String?, label: String?, resultConfig: ResultConfig?, resultData: ResultData?, logStdoutAndStderr: Boolean): Error? {
        if (context == null || resultConfig == null || resultData == null || DataUtils.isNullOrEmpty(resultConfig.resultDirectoryPath))
            return FunctionErrno.ERRNO_NULL_OR_EMPTY_PARAMETER.getError("context, resultConfig, resultData or resultConfig.resultDirectoryPath", "sendCommandResultDataToDirectory")

        val actualLogTag = DataUtils.getDefaultIfNull(logTag, LOG_TAG)
        val actualLabel = label ?: "unknown"

        val resultDataStdout = resultData.stdout.toString()
        val resultDataStderr = resultData.stderr.toString()

        var resultDataExitCode = ""
        if (resultData.exitCode != null)
            resultDataExitCode = resultData.exitCode.toString()

        var resultDataErrmsg: String? = null
        if (resultData.isStateFailed()) {
            resultDataErrmsg = ResultData.getErrorsListLogString(resultData)
        }
        resultDataErrmsg = DataUtils.getDefaultIfNull(resultDataErrmsg, "")

        resultConfig.resultDirectoryPath = FileUtils.getCanonicalPath(resultConfig.resultDirectoryPath, null)

        Logger.logDebugExtended(actualLogTag ?: "", "Writing result for command \"$actualLabel\":\n$resultConfig\n${ResultData.getResultDataLogString(resultData, logStdoutAndStderr)}")

        // If resultDirectoryPath is not a directory, or is not readable or writable, then just return
        // Creation of missing directory and setting of read, write and execute permissions are
        // only done if resultDirectoryPath is under resultDirectoryAllowedParentPath.
        // We try to set execute permissions, but ignore if they are missing, since only read and write
        // permissions are required for working directories.
        var error = FileUtils.validateDirectoryFileExistenceAndPermissions("result", resultConfig.resultDirectoryPath,
            resultConfig.resultDirectoryAllowedParentPath, true,
            FileUtils.APP_WORKING_DIRECTORY_PERMISSIONS, true, true,
            true, true)
        if (error != null) {
            error.appendMessage("\n" + context.getString(R.string.msg_directory_absolute_path, "Result", resultConfig.resultDirectoryPath))
            return error
        }

        if (resultConfig.resultSingleFile) {
            // If resultFileBasename is null, empty or contains forward slashes "/"
            if (DataUtils.isNullOrEmpty(resultConfig.resultFileBasename) ||
                    resultConfig.resultFileBasename!!.contains("/")) {
                return ResultSenderErrno.ERROR_RESULT_FILE_BASENAME_NULL_OR_INVALID.getError(resultConfig.resultFileBasename)
            }

            val errorOrOutput: String

            if (resultData.isStateFailed()) {
                try {
                    errorOrOutput = if (DataUtils.isNullOrEmpty(resultConfig.resultFileErrorFormat)) {
                        String.format(RESULT_SENDER.FORMAT_FAILED_ERR__ERRMSG__STDOUT__STDERR__EXIT_CODE,
                            MarkdownUtils.getMarkdownCodeForString(resultData.errCode.toString(), false),
                            MarkdownUtils.getMarkdownCodeForString(resultDataErrmsg, true),
                            MarkdownUtils.getMarkdownCodeForString(resultDataStdout, true),
                            MarkdownUtils.getMarkdownCodeForString(resultDataStderr, true),
                            MarkdownUtils.getMarkdownCodeForString(resultDataExitCode, false))
                    } else {
                        String.format(resultConfig.resultFileErrorFormat!!,
                            resultData.errCode, resultDataErrmsg, resultDataStdout, resultDataStderr, resultDataExitCode)
                    }
                } catch (e: Exception) {
                    return ResultSenderErrno.ERROR_FORMAT_RESULT_ERROR_FAILED_WITH_EXCEPTION.getError(e.message)
                }
            } else {
                try {
                    errorOrOutput = if (DataUtils.isNullOrEmpty(resultConfig.resultFileOutputFormat)) {
                        if (resultDataStderr.isEmpty() && resultDataExitCode == "0")
                            String.format(RESULT_SENDER.FORMAT_SUCCESS_STDOUT, resultDataStdout)
                        else if (resultDataStderr.isEmpty())
                            String.format(RESULT_SENDER.FORMAT_SUCCESS_STDOUT__EXIT_CODE,
                                resultDataStdout,
                                MarkdownUtils.getMarkdownCodeForString(resultDataExitCode, false))
                        else
                            String.format(RESULT_SENDER.FORMAT_SUCCESS_STDOUT__STDERR__EXIT_CODE,
                                MarkdownUtils.getMarkdownCodeForString(resultDataStdout, true),
                                MarkdownUtils.getMarkdownCodeForString(resultDataStderr, true),
                                MarkdownUtils.getMarkdownCodeForString(resultDataExitCode, false))
                    } else {
                        String.format(resultConfig.resultFileOutputFormat!!,
                            resultDataStdout, resultDataStderr, resultDataExitCode)
                    }
                } catch (e: Exception) {
                    return ResultSenderErrno.ERROR_FORMAT_RESULT_OUTPUT_FAILED_WITH_EXCEPTION.getError(e.message)
                }
            }

            // Write error or output to temp file
            // Check errCode file creation below for explanation for why temp file is used
            val tempFilename = resultConfig.resultFileBasename + "-" + AndroidUtils.getCurrentMilliSecondLocalTimeStamp()
            error = FileUtils.writeTextToFile(tempFilename, resultConfig.resultDirectoryPath + "/" + tempFilename,
                null, errorOrOutput, false)
            if (error != null) {
                return error
            }

            // Move error or output temp file to final destination
            error = FileUtils.moveRegularFile("error or output temp file", resultConfig.resultDirectoryPath + "/" + tempFilename,
                resultConfig.resultDirectoryPath + "/" + resultConfig.resultFileBasename, false)
            if (error != null) {
                return error
            }
        } else {
            var filename: String

            // Default to no suffix, useful if user expects result in an empty directory, like created with mktemp
            if (resultConfig.resultFilesSuffix == null)
                resultConfig.resultFilesSuffix = ""

            // If resultFilesSuffix contains forward slashes "/"
            if (resultConfig.resultFilesSuffix!!.contains("/")) {
                return ResultSenderErrno.ERROR_RESULT_FILES_SUFFIX_INVALID.getError(resultConfig.resultFilesSuffix)
            }

            // Write result to result files under resultDirectoryPath

            // Write stdout to file
            if (resultDataStdout.isNotEmpty()) {
                filename = RESULT_SENDER.RESULT_FILE_STDOUT_PREFIX + resultConfig.resultFilesSuffix
                error = FileUtils.writeTextToFile(filename, resultConfig.resultDirectoryPath + "/" + filename,
                    null, resultDataStdout, false)
                if (error != null) {
                    return error
                }
            }

            // Write stderr to file
            if (resultDataStderr.isNotEmpty()) {
                filename = RESULT_SENDER.RESULT_FILE_STDERR_PREFIX + resultConfig.resultFilesSuffix
                error = FileUtils.writeTextToFile(filename, resultConfig.resultDirectoryPath + "/" + filename,
                    null, resultDataStderr, false)
                if (error != null) {
                    return error
                }
            }

            // Write exitCode to file
            if (resultDataExitCode.isNotEmpty()) {
                filename = RESULT_SENDER.RESULT_FILE_EXIT_CODE_PREFIX + resultConfig.resultFilesSuffix
                error = FileUtils.writeTextToFile(filename, resultConfig.resultDirectoryPath + "/" + filename,
                    null, resultDataExitCode, false)
                if (error != null) {
                    return error
                }
            }

            // Write errmsg to file
            if (resultData.isStateFailed() && resultDataErrmsg!!.isNotEmpty()) {
                filename = RESULT_SENDER.RESULT_FILE_ERRMSG_PREFIX + resultConfig.resultFilesSuffix
                error = FileUtils.writeTextToFile(filename, resultConfig.resultDirectoryPath + "/" + filename,
                    null, resultDataErrmsg, false)
                if (error != null) {
                    return error
                }
            }

            // Write errCode to file
            // This must be created after writing to other result files has already finished since
            // caller should wait for this file to be created to be notified that the command has
            // finished and should then start reading from the rest of the result files if they exist.
            // Since there may be a delay between creation of errCode file and writing to it or flushing
            // to disk, we create a temp file first and then move it to the final destination, since
            // caller may otherwise read from an empty file in some cases.

            // Write errCode to temp file
            var tempFilename = RESULT_SENDER.RESULT_FILE_ERR_PREFIX + "-" + AndroidUtils.getCurrentMilliSecondLocalTimeStamp()
            if (resultConfig.resultFilesSuffix!!.isNotEmpty()) tempFilename = "$tempFilename-${resultConfig.resultFilesSuffix}"
            error = FileUtils.writeTextToFile(tempFilename, resultConfig.resultDirectoryPath + "/" + tempFilename,
                null, resultData.errCode.toString(), false)
            if (error != null) {
                return error
            }

            // Move errCode temp file to final destination
            filename = RESULT_SENDER.RESULT_FILE_ERR_PREFIX + resultConfig.resultFilesSuffix
            error = FileUtils.moveRegularFile(RESULT_SENDER.RESULT_FILE_ERR_PREFIX + " temp file", resultConfig.resultDirectoryPath + "/" + tempFilename,
                resultConfig.resultDirectoryPath + "/" + filename, false)
            if (error != null) {
                return error
            }
        }

        return null
    }
}
