package com.termux.shared.shell.command

import android.content.Intent
import android.net.Uri
import com.termux.shared.data.DataUtils
import com.termux.shared.data.IntentUtils
import com.termux.shared.errors.Error
import com.termux.shared.logger.Logger
import com.termux.shared.markdown.MarkdownUtils
import com.termux.shared.shell.command.result.ResultConfig
import com.termux.shared.shell.command.result.ResultData

class ExecutionCommand @JvmOverloads constructor(
    /** The optional unique id for the [ExecutionCommand]. This should equal -1 if execution
     * command is not going to be managed by a shell manager. */
    var id: Int? = null
) {
    /*
    The [ExecutionState.SUCCESS] and [ExecutionState.FAILED] is defined based on
    successful execution of command without any internal errors or exceptions being raised.
    The shell command [exitCode] being non-zero **does not** mean that execution command failed.
    Only the [errCode] being non-zero means that execution command failed from the Termux app
    perspective.
    */

    /** The [Enum] that defines [ExecutionCommand] state. */
    enum class ExecutionState(val stateName: String, val value: Int) {
        PRE_EXECUTION("Pre-Execution", 0),
        EXECUTING("Executing", 1),
        EXECUTED("Executed", 2),
        SUCCESS("Success", 3),
        FAILED("Failed", 4);

        fun getName(): String = stateName
    }

    enum class Runner(private val runnerName: String) {
        /** Run command in TerminalSession. */
        TERMINAL_SESSION("terminal-session"),

        /** Run command in AppShell. */
        APP_SHELL("app-shell");

        fun getName(): String = runnerName

        fun equalsRunner(runner: String?): Boolean {
            return runner != null && runner == this.runnerName
        }

        companion object {
            /** Get [Runner] for [name] if found, otherwise `null`. */
            @JvmStatic
            fun runnerOf(name: String?): Runner? {
                for (v in entries) {
                    if (v.runnerName == name) {
                        return v
                    }
                }
                return null
            }

            /** Get [Runner] for [name] if found, otherwise [def]. */
            @JvmStatic
            fun runnerOf(name: String?, def: Runner): Runner {
                return runnerOf(name) ?: def
            }
        }
    }

    enum class ShellCreateMode(private val mode: String) {
        /** Always create TerminalSession. */
        ALWAYS("always"),

        /** Create shell only if no shell with [shellName] found. */
        NO_SHELL_WITH_NAME("no-shell-with-name");

        fun getMode(): String = mode

        fun equalsMode(sessionCreateMode: String?): Boolean {
            return sessionCreateMode != null && sessionCreateMode == this.mode
        }

        companion object {
            /** Get [ShellCreateMode] for [mode] if found, otherwise `null`. */
            @JvmStatic
            fun modeOf(mode: String?): ShellCreateMode? {
                for (v in entries) {
                    if (v.mode == mode) {
                        return v
                    }
                }
                return null
            }
        }
    }

    /** The process id of command. */
    var mPid: Int = -1

    /** The current state of the [ExecutionCommand]. */
    private var currentState: ExecutionState = ExecutionState.PRE_EXECUTION
    /** The previous state of the [ExecutionCommand]. */
    private var previousState: ExecutionState = ExecutionState.PRE_EXECUTION

    /** The executable for the [ExecutionCommand]. */
    var executable: String? = null
    /** The executable Uri for the [ExecutionCommand]. */
    var executableUri: Uri? = null
    /** The executable arguments array for the [ExecutionCommand]. */
    var arguments: Array<String>? = null
    /** The stdin string for the [ExecutionCommand]. */
    var stdin: String? = null
    /** The current working directory for the [ExecutionCommand]. */
    var workingDirectory: String? = null

    /** The terminal transcript rows for the [ExecutionCommand]. */
    var terminalTranscriptRows: Int? = null

    /** The [Runner] for the [ExecutionCommand]. */
    var runner: String? = null

    /** If the [ExecutionCommand] is meant to start a failsafe terminal session. */
    var isFailsafe: Boolean = false

    /**
     * The [ExecutionCommand] custom log level for background AppShell
     * commands. By default, StreamGobbler only logs stdout and
     * stderr if [Logger] `CURRENT_LOG_LEVEL` is >= [Logger.LOG_LEVEL_VERBOSE] and
     * AppShell only logs stdin if `CURRENT_LOG_LEVEL` is >=
     * [Logger.LOG_LEVEL_DEBUG].
     */
    var backgroundCustomLogLevel: Int? = null

    /** The session action of [Runner.TERMINAL_SESSION] commands. */
    var sessionAction: String? = null

    /** The shell name of commands. */
    var shellName: String? = null

    /** The [ShellCreateMode] of commands. */
    var shellCreateMode: String? = null

    /** Whether to set [ExecutionCommand] shell environment. */
    var setShellCommandShellEnvironment: Boolean = false

    /** The command label for the [ExecutionCommand]. */
    var commandLabel: String? = null
    /** The markdown text for the command description for the [ExecutionCommand]. */
    var commandDescription: String? = null
    /** The markdown text for the help of command for the [ExecutionCommand]. This can be used
     * to provide useful info to the user if an internal error is raised. */
    var commandHelp: String? = null

    /** Defines the markdown text for the help of the Termux plugin API that was used to start the
     * [ExecutionCommand]. This can be used to provide useful info to the user if an internal
     * error is raised. */
    var pluginAPIHelp: String? = null

    /** Defines the [Intent] received which started the command. */
    var commandIntent: Intent? = null

    /** Defines if [ExecutionCommand] was started because of an external plugin request
     * like with an intent or from within Termux app itself. */
    var isPluginExecutionCommand: Boolean = false

    /** Defines the [ResultConfig] for the [ExecutionCommand] containing information
     * on how to handle the result. */
    val resultConfig: ResultConfig = ResultConfig()

    /** Defines the [ResultData] for the [ExecutionCommand] containing information
     * of the result. */
    val resultData: ResultData = ResultData()

    /** Defines if processing results already called for this [ExecutionCommand]. */
    var processingResultsAlreadyCalled: Boolean = false

    constructor(
        id: Int?,
        executable: String?,
        arguments: Array<String>?,
        stdin: String?,
        workingDirectory: String?,
        runner: String?,
        isFailsafe: Boolean
    ) : this(id) {
        this.executable = executable
        this.arguments = arguments
        this.stdin = stdin
        this.workingDirectory = workingDirectory
        this.runner = runner
        this.isFailsafe = isFailsafe
    }

    fun isPluginExecutionCommandWithPendingResult(): Boolean {
        return isPluginExecutionCommand && resultConfig.isCommandWithPendingResult()
    }

    @Synchronized
    fun setState(newState: ExecutionState): Boolean {
        // The state transition cannot go back or change if already at ExecutionState.SUCCESS
        if (newState.value < currentState.value || currentState == ExecutionState.SUCCESS) {
            Logger.logError(
                LOG_TAG,
                "Invalid ${getCommandIdAndLabelLogString()} state transition from \"${currentState.getName()}\" to \"${newState.getName()}\""
            )
            return false
        }

        // The ExecutionState.FAILED can be set again, like to add more errors, but we don't update
        // previousState with the currentState value if its at ExecutionState.FAILED to
        // preserve the last valid state
        if (currentState != ExecutionState.FAILED)
            previousState = currentState

        currentState = newState
        return true
    }

    @Synchronized
    fun hasExecuted(): Boolean {
        return currentState.value >= ExecutionState.EXECUTED.value
    }

    @Synchronized
    fun isExecuting(): Boolean {
        return currentState == ExecutionState.EXECUTING
    }

    @Synchronized
    fun isSuccessful(): Boolean {
        return currentState == ExecutionState.SUCCESS
    }

    @Synchronized
    fun setStateFailed(error: Error): Boolean {
        return setStateFailed(error.type, error.code, error.message, null)
    }

    @Synchronized
    fun setStateFailed(error: Error, throwable: Throwable): Boolean {
        return setStateFailed(error.type, error.code, error.message, listOf(throwable))
    }

    @Synchronized
    fun setStateFailed(error: Error, throwablesList: List<Throwable>?): Boolean {
        return setStateFailed(error.type, error.code, error.message, throwablesList)
    }

    @Synchronized
    fun setStateFailed(code: Int, message: String?): Boolean {
        return setStateFailed(null, code, message, null)
    }

    @Synchronized
    fun setStateFailed(code: Int, message: String?, throwable: Throwable): Boolean {
        return setStateFailed(null, code, message, listOf(throwable))
    }

    @Synchronized
    fun setStateFailed(code: Int, message: String?, throwablesList: List<Throwable>?): Boolean {
        return setStateFailed(null, code, message, throwablesList)
    }

    @Synchronized
    fun setStateFailed(type: String?, code: Int, message: String?, throwablesList: List<Throwable>?): Boolean {
        if (!this.resultData.setStateFailed(type, code, message, throwablesList)) {
            Logger.logWarn(LOG_TAG, "setStateFailed for ${getCommandIdAndLabelLogString()} resultData encountered an error.")
        }

        return setState(ExecutionState.FAILED)
    }

    @Synchronized
    fun shouldNotProcessResults(): Boolean {
        if (processingResultsAlreadyCalled) {
            return true
        } else {
            processingResultsAlreadyCalled = true
            return false
        }
    }

    @Synchronized
    fun isStateFailed(): Boolean {
        if (currentState != ExecutionState.FAILED)
            return false

        if (!resultData.isStateFailed()) {
            Logger.logWarn(
                LOG_TAG,
                "The ${getCommandIdAndLabelLogString()} has an invalid errCode value set in errors list while having ExecutionState.FAILED state.\n${resultData.errorsList}"
            )
            return false
        } else {
            return true
        }
    }

    override fun toString(): String {
        return if (!hasExecuted())
            getExecutionInputLogString(this, ignoreNull = true, logStdin = true)
        else
            getExecutionOutputLogString(this, ignoreNull = true, logResultData = true, logStdoutAndStderr = true)
    }

    fun getIdLogString(): String {
        return if (id != null) "($id) " else ""
    }

    fun getPidLogString(): String = "Pid: `$mPid`"

    fun getCurrentStateLogString(): String = "Current State: `${currentState.getName()}`"

    fun getPreviousStateLogString(): String = "Previous State: `${previousState.getName()}`"

    fun getCommandLabelLogString(): String {
        return if (!commandLabel.isNullOrEmpty()) commandLabel!! else "Execution Command"
    }

    fun getCommandIdAndLabelLogString(): String = "${getIdLogString()}${getCommandLabelLogString()}"

    fun getExecutableLogString(): String = "Executable: `$executable`"

    fun getArgumentsLogString(): String = getArgumentsLogString("Arguments", arguments)

    fun getWorkingDirectoryLogString(): String = "Working Directory: `$workingDirectory`"

    fun getRunnerLogString(): String = Logger.getSingleLineLogStringEntry("Runner", runner, "-")

    fun getIsFailsafeLogString(): String = "isFailsafe: `$isFailsafe`"

    fun getStdinLogString(): String {
        return if (DataUtils.isNullOrEmpty(stdin))
            "Stdin: -"
        else
            Logger.getMultiLineLogStringEntry("Stdin", stdin, "-")
    }

    fun getBackgroundCustomLogLevelLogString(): String = "Background Custom Log Level: `$backgroundCustomLogLevel`"

    fun getSessionActionLogString(): String = Logger.getSingleLineLogStringEntry("Session Action", sessionAction, "-")

    fun getShellNameLogString(): String = Logger.getSingleLineLogStringEntry("Shell Name", shellName, "-")

    fun getShellCreateModeLogString(): String = Logger.getSingleLineLogStringEntry("Shell Create Mode", shellCreateMode, "-")

    fun getSetRunnerShellEnvironmentLogString(): String = "Set Shell Command Shell Environment: `$setShellCommandShellEnvironment`"

    fun getCommandDescriptionLogString(): String = Logger.getSingleLineLogStringEntry("Command Description", commandDescription, "-")

    fun getCommandHelpLogString(): String = Logger.getSingleLineLogStringEntry("Command Help", commandHelp, "-")

    fun getPluginAPIHelpLogString(): String = Logger.getSingleLineLogStringEntry("Plugin API Help", pluginAPIHelp, "-")

    fun getCommandIntentLogString(): String {
        return if (commandIntent == null)
            "Command Intent: -"
        else
            Logger.getMultiLineLogStringEntry("Command Intent", IntentUtils.getIntentString(commandIntent), "-")
    }

    fun getIsPluginExecutionCommandLogString(): String = "isPluginExecutionCommand: `$isPluginExecutionCommand`"

    companion object {
        private const val LOG_TAG = "ExecutionCommand"

        /**
         * Get a log friendly [String] for [ExecutionCommand] execution input parameters.
         *
         * @param executionCommand The [ExecutionCommand] to convert.
         * @param ignoreNull Set to `true` if non-critical `null` values are to be ignored.
         * @param logStdin Set to `true` if [stdin] should be logged.
         * @return Returns the log friendly [String].
         */
        @JvmStatic
        fun getExecutionInputLogString(executionCommand: ExecutionCommand?, ignoreNull: Boolean, logStdin: Boolean): String {
            if (executionCommand == null) return "null"

            val logString = StringBuilder()

            logString.append(executionCommand.getCommandIdAndLabelLogString()).append(":")

            if (executionCommand.mPid != -1)
                logString.append("\n").append(executionCommand.getPidLogString())

            if (executionCommand.previousState != ExecutionState.PRE_EXECUTION)
                logString.append("\n").append(executionCommand.getPreviousStateLogString())
            logString.append("\n").append(executionCommand.getCurrentStateLogString())

            logString.append("\n").append(executionCommand.getExecutableLogString())
            logString.append("\n").append(executionCommand.getArgumentsLogString())
            logString.append("\n").append(executionCommand.getWorkingDirectoryLogString())
            logString.append("\n").append(executionCommand.getRunnerLogString())
            logString.append("\n").append(executionCommand.getIsFailsafeLogString())

            if (Runner.APP_SHELL.equalsRunner(executionCommand.runner)) {
                if (logStdin && (!ignoreNull || !DataUtils.isNullOrEmpty(executionCommand.stdin)))
                    logString.append("\n").append(executionCommand.getStdinLogString())

                if (!ignoreNull || executionCommand.backgroundCustomLogLevel != null)
                    logString.append("\n").append(executionCommand.getBackgroundCustomLogLevelLogString())
            }

            if (!ignoreNull || executionCommand.sessionAction != null)
                logString.append("\n").append(executionCommand.getSessionActionLogString())

            if (!ignoreNull || executionCommand.shellName != null) {
                logString.append("\n").append(executionCommand.getShellNameLogString())
            }

            if (!ignoreNull || executionCommand.shellCreateMode != null) {
                logString.append("\n").append(executionCommand.getShellCreateModeLogString())
            }

            logString.append("\n").append(executionCommand.getSetRunnerShellEnvironmentLogString())

            if (!ignoreNull || executionCommand.commandIntent != null)
                logString.append("\n").append(executionCommand.getCommandIntentLogString())

            logString.append("\n").append(executionCommand.getIsPluginExecutionCommandLogString())
            if (executionCommand.isPluginExecutionCommand)
                logString.append("\n").append(ResultConfig.getResultConfigLogString(executionCommand.resultConfig, ignoreNull))

            return logString.toString()
        }

        /**
         * Get a log friendly [String] for [ExecutionCommand] execution output parameters.
         *
         * @param executionCommand The [ExecutionCommand] to convert.
         * @param ignoreNull Set to `true` if non-critical `null` values are to be ignored.
         * @param logResultData Set to `true` if [resultData] should be logged.
         * @param logStdoutAndStderr Set to `true` if [ResultData.stdout] and [ResultData.stderr] should be logged.
         * @return Returns the log friendly [String].
         */
        @JvmStatic
        fun getExecutionOutputLogString(
            executionCommand: ExecutionCommand?,
            ignoreNull: Boolean,
            logResultData: Boolean,
            logStdoutAndStderr: Boolean
        ): String {
            if (executionCommand == null) return "null"

            val logString = StringBuilder()

            logString.append(executionCommand.getCommandIdAndLabelLogString()).append(":")

            logString.append("\n").append(executionCommand.getPreviousStateLogString())
            logString.append("\n").append(executionCommand.getCurrentStateLogString())

            if (logResultData)
                logString.append("\n").append(ResultData.getResultDataLogString(executionCommand.resultData, logStdoutAndStderr))

            return logString.toString()
        }

        /**
         * Get a log friendly [String] for [ExecutionCommand] with more details.
         *
         * @param executionCommand The [ExecutionCommand] to convert.
         * @return Returns the log friendly [String].
         */
        @JvmStatic
        fun getDetailedLogString(executionCommand: ExecutionCommand?): String {
            if (executionCommand == null) return "null"

            val logString = StringBuilder()

            logString.append(getExecutionInputLogString(executionCommand, ignoreNull = false, logStdin = true))
            logString.append(getExecutionOutputLogString(executionCommand, ignoreNull = false, logResultData = true, logStdoutAndStderr = true))

            logString.append("\n").append(executionCommand.getCommandDescriptionLogString())
            logString.append("\n").append(executionCommand.getCommandHelpLogString())
            logString.append("\n").append(executionCommand.getPluginAPIHelpLogString())

            return logString.toString()
        }

        /**
         * Get a markdown [String] for [ExecutionCommand].
         *
         * @param executionCommand The [ExecutionCommand] to convert.
         * @return Returns the markdown [String].
         */
        @JvmStatic
        fun getExecutionCommandMarkdownString(executionCommand: ExecutionCommand?): String {
            if (executionCommand == null) return "null"

            if (executionCommand.commandLabel == null) executionCommand.commandLabel = "Execution Command"

            val markdownString = StringBuilder()

            markdownString.append("## ").append(executionCommand.commandLabel).append("\n")

            if (executionCommand.mPid != -1)
                markdownString.append("\n").append(MarkdownUtils.getSingleLineMarkdownStringEntry("Pid", executionCommand.mPid, "-"))

            markdownString.append("\n").append(MarkdownUtils.getSingleLineMarkdownStringEntry("Previous State", executionCommand.previousState.getName(), "-"))
            markdownString.append("\n").append(MarkdownUtils.getSingleLineMarkdownStringEntry("Current State", executionCommand.currentState.getName(), "-"))

            markdownString.append("\n").append(MarkdownUtils.getSingleLineMarkdownStringEntry("Executable", executionCommand.executable, "-"))
            markdownString.append("\n").append(getArgumentsMarkdownString("Arguments", executionCommand.arguments))
            markdownString.append("\n").append(MarkdownUtils.getSingleLineMarkdownStringEntry("Working Directory", executionCommand.workingDirectory, "-"))
            markdownString.append("\n").append(MarkdownUtils.getSingleLineMarkdownStringEntry("Runner", executionCommand.runner, "-"))
            markdownString.append("\n").append(MarkdownUtils.getSingleLineMarkdownStringEntry("isFailsafe", executionCommand.isFailsafe, "-"))

            if (Runner.APP_SHELL.equalsRunner(executionCommand.runner)) {
                if (!DataUtils.isNullOrEmpty(executionCommand.stdin))
                    markdownString.append("\n").append(MarkdownUtils.getMultiLineMarkdownStringEntry("Stdin", executionCommand.stdin, "-"))
                if (executionCommand.backgroundCustomLogLevel != null)
                    markdownString.append("\n").append(MarkdownUtils.getSingleLineMarkdownStringEntry("Background Custom Log Level", executionCommand.backgroundCustomLogLevel, "-"))
            }

            markdownString.append("\n").append(MarkdownUtils.getSingleLineMarkdownStringEntry("Session Action", executionCommand.sessionAction, "-"))

            markdownString.append("\n").append(MarkdownUtils.getSingleLineMarkdownStringEntry("Shell Name", executionCommand.shellName, "-"))
            markdownString.append("\n").append(MarkdownUtils.getSingleLineMarkdownStringEntry("Shell Create Mode", executionCommand.shellCreateMode, "-"))
            markdownString.append("\n").append(MarkdownUtils.getSingleLineMarkdownStringEntry("Set Shell Command Shell Environment", executionCommand.setShellCommandShellEnvironment, "-"))

            markdownString.append("\n").append(MarkdownUtils.getSingleLineMarkdownStringEntry("isPluginExecutionCommand", executionCommand.isPluginExecutionCommand, "-"))

            markdownString.append("\n\n").append(ResultConfig.getResultConfigMarkdownString(executionCommand.resultConfig))

            markdownString.append("\n\n").append(ResultData.getResultDataMarkdownString(executionCommand.resultData))

            if (executionCommand.commandDescription != null || executionCommand.commandHelp != null) {
                if (executionCommand.commandDescription != null)
                    markdownString.append("\n\n### Command Description\n\n").append(executionCommand.commandDescription).append("\n")
                if (executionCommand.commandHelp != null)
                    markdownString.append("\n\n### Command Help\n\n").append(executionCommand.commandHelp).append("\n")
                markdownString.append("\n##\n")
            }

            if (executionCommand.pluginAPIHelp != null) {
                markdownString.append("\n\n### Plugin API Help\n\n").append(executionCommand.pluginAPIHelp)
                markdownString.append("\n##\n")
            }

            return markdownString.toString()
        }

        /**
         * Get a log friendly [String] for [List] argumentsArray.
         * If argumentsArray are null or of size 0, then `Arguments: -` is returned. Otherwise
         * following format is returned:
         *
         * Arguments:
         * ```
         * Arg 1: `value`
         * Arg 2: 'value`
         * ```
         *
         * @param argumentsArray The [Array] argumentsArray to convert.
         * @return Returns the log friendly [String].
         */
        @JvmStatic
        fun getArgumentsLogString(label: String, argumentsArray: Array<String>?): String {
            val argumentsString = StringBuilder("$label:")

            if (argumentsArray != null && argumentsArray.isNotEmpty()) {
                argumentsString.append("\n```\n")
                for (i in argumentsArray.indices) {
                    argumentsString.append(
                        Logger.getSingleLineLogStringEntry(
                            "Arg ${i + 1}",
                            DataUtils.getTruncatedCommandOutput(argumentsArray[i], Logger.LOGGER_ENTRY_MAX_SAFE_PAYLOAD / 5, true, false, true),
                            "-"
                        )
                    ).append("\n")
                }
                argumentsString.append("```")
            } else {
                argumentsString.append(" -")
            }

            return argumentsString.toString()
        }

        /**
         * Get a markdown [String] for [Array] argumentsArray.
         * If argumentsArray are null or of size 0, then `**Arguments:** -` is returned. Otherwise
         * following format is returned:
         *
         * **Arguments:**
         *
         * **Arg 1:**
         * ```
         * value
         * ```
         * **Arg 2:**
         * ```
         * value
         * ```
         *
         * @param argumentsArray The [Array] argumentsArray to convert.
         * @return Returns the markdown [String].
         */
        @JvmStatic
        fun getArgumentsMarkdownString(label: String, argumentsArray: Array<String>?): String {
            val argumentsString = StringBuilder("**$label:**")

            if (argumentsArray != null && argumentsArray.isNotEmpty()) {
                argumentsString.append("\n")
                for (i in argumentsArray.indices) {
                    argumentsString.append(MarkdownUtils.getMultiLineMarkdownStringEntry("Arg ${i + 1}", argumentsArray[i], "-")).append("\n")
                }
            } else {
                argumentsString.append(" -  ")
            }

            return argumentsString.toString()
        }
    }
}
