package com.termux.shared.shell.command.environment

import com.termux.shared.errors.Error
import com.termux.shared.file.FileUtils
import com.termux.shared.logger.Logger

object ShellEnvironmentUtils {

    private const val LOG_TAG = "ShellEnvironmentUtils"

    /**
     * Convert environment [HashMap] to `environ` [List] of [String].
     *
     * The items in the environ will have the format `name=value`.
     */
    @JvmStatic
    fun convertEnvironmentToEnviron(environmentMap: HashMap<String, String>): List<String> {
        val environmentList = ArrayList<String>(environmentMap.size)
        for ((name, value) in environmentMap) {
            if (isValidEnvironmentVariableNameValuePair(name, value, true)) {
                environmentList.add("$name=$value")
            }
        }
        return environmentList
    }

    /**
     * Convert environment [HashMap] to [String] where each item equals "key=value".
     */
    @JvmStatic
    fun convertEnvironmentToDotEnvFile(environmentMap: HashMap<String, String>): String {
        return convertEnvironmentToDotEnvFile(convertEnvironmentMapToEnvironmentVariableList(environmentMap))
    }

    /**
     * Convert environment [HashMap] to `.env` file [String].
     *
     * The items in the `.env` file have the format `export name="value"`.
     */
    @JvmStatic
    fun convertEnvironmentToDotEnvFile(environmentList: List<ShellEnvironmentVariable>): String {
        val environment = StringBuilder()
        val sorted = environmentList.sorted()
        for (variable in sorted) {
            if (isValidEnvironmentVariableNameValuePair(variable.name, variable.value, true) && variable.value != null) {
                environment.append("export ").append(variable.name).append("=\"")
                    .append(if (variable.escaped) variable.value else variable.value.replace(Regex("([\"\\\\`$])"), "\\\\$1"))
                    .append("\"\n")
            }
        }
        return environment.toString()
    }

    /**
     * Convert environment [HashMap] to [List] of [ShellEnvironmentVariable]. Each item
     * will have its [ShellEnvironmentVariable.escaped] set to false.
     */
    @JvmStatic
    fun convertEnvironmentMapToEnvironmentVariableList(environmentMap: HashMap<String, String>): List<ShellEnvironmentVariable> {
        val environmentList = ArrayList<ShellEnvironmentVariable>()
        for ((name, value) in environmentMap) {
            environmentList.add(ShellEnvironmentVariable(name, value, false))
        }
        return environmentList
    }

    /**
     * Check if environment variable name and value pair is valid.
     */
    @JvmStatic
    fun isValidEnvironmentVariableNameValuePair(name: String?, value: String?, logErrors: Boolean): Boolean {
        if (!isValidEnvironmentVariableName(name)) {
            if (logErrors)
                Logger.logErrorPrivate(LOG_TAG, "Invalid environment variable name. name=`$name`, value=`$value`")
            return false
        }

        if (!isValidEnvironmentVariableValue(value)) {
            if (logErrors)
                Logger.logErrorPrivate(LOG_TAG, "Invalid environment variable value. name=`$name`, value=`$value`")
            return false
        }

        return true
    }

    /**
     * Check if environment variable name is valid.
     */
    @JvmStatic
    fun isValidEnvironmentVariableName(name: String?): Boolean {
        return name != null && !name.contains("\u0000") && name.matches(Regex("[a-zA-Z_][a-zA-Z0-9_]*"))
    }

    /**
     * Check if environment variable value is valid.
     */
    @JvmStatic
    fun isValidEnvironmentVariableValue(value: String?): Boolean {
        return value != null && !value.contains("\u0000")
    }

    /** Put value in environment if variable exists in System environment. */
    @JvmStatic
    fun putToEnvIfInSystemEnv(environment: HashMap<String, String>, name: String) {
        val value = System.getenv(name)
        if (value != null) {
            environment[name] = value
        }
    }

    /** Put [String] value in environment if value set. */
    @JvmStatic
    fun putToEnvIfSet(environment: HashMap<String, String>, name: String, value: String?) {
        if (value != null) {
            environment[name] = value
        }
    }

    /** Put [Boolean] value "true" or "false" in environment if value set. */
    @JvmStatic
    fun putToEnvIfSet(environment: HashMap<String, String>, name: String, value: Boolean?) {
        if (value != null) {
            environment[name] = value.toString()
        }
    }

    /** Create HOME directory in environment [Map] if set. */
    @JvmStatic
    fun createHomeDir(environment: HashMap<String, String>) {
        val homeDirectory = environment[UnixShellEnvironment.ENV_HOME]
        if (!homeDirectory.isNullOrEmpty()) {
            val error: Error? = FileUtils.createDirectoryFile("shell home", homeDirectory)
            if (error != null) {
                Logger.logErrorExtended(LOG_TAG, "Failed to create shell home directory\n$error")
            }
        }
    }
}
