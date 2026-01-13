package com.termux.shared.shell.command.environment

/**
 * Represents a shell environment variable.
 */
data class ShellEnvironmentVariable(
    /** The name for environment variable */
    @JvmField var name: String,
    /** The value for environment variable */
    @JvmField var value: String,
    /** If environment variable [value] is already escaped. */
    @JvmField var escaped: Boolean = false
) : Comparable<ShellEnvironmentVariable> {

    constructor(name: String, value: String) : this(name, value, false)

    override fun compareTo(other: ShellEnvironmentVariable): Int {
        return this.name.compareTo(other.name)
    }
}
