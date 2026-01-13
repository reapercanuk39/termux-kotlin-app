package com.termux.shared.shell.command.environment

import android.content.Context
import com.termux.shared.shell.command.ExecutionCommand
import java.io.File

/**
 * Environment for Android.
 *
 * https://cs.android.com/android/platform/superproject/+/android-12.0.0_r32:frameworks/base/core/java/android/os/Environment.java
 * https://cs.android.com/android/platform/superproject/+/android-12.0.0_r32:system/core/rootdir/init.environ.rc.in
 * https://cs.android.com/android/platform/superproject/+/android-5.0.0_r1.0.1:system/core/rootdir/init.environ.rc.in
 * https://cs.android.com/android/_/android/platform/system/core/+/refs/tags/android-12.0.0_r32:rootdir/init.rc;l=910
 * https://cs.android.com/android/platform/superproject/+/android-12.0.0_r32:packages/modules/SdkExtensions/derive_classpath/derive_classpath.cpp;l=96
 */
open class AndroidShellEnvironment : UnixShellEnvironment() {

    @JvmField
    protected var shellCommandShellEnvironment: ShellCommandShellEnvironment? = ShellCommandShellEnvironment()

    /** Get shell environment for Android. */
    override fun getEnvironment(currentPackageContext: Context, isFailSafe: Boolean): HashMap<String, String> {
        val environment = HashMap<String, String>()

        environment[ENV_HOME] = "/"
        environment[ENV_LANG] = "en_US.UTF-8"
        System.getenv(ENV_PATH)?.let { environment[ENV_PATH] = it }
        environment[ENV_TMPDIR] = "/data/local/tmp"

        environment[ENV_COLORTERM] = "truecolor"
        environment[ENV_TERM] = "xterm-256color"

        ShellEnvironmentUtils.putToEnvIfInSystemEnv(environment, "ANDROID_ASSETS")
        ShellEnvironmentUtils.putToEnvIfInSystemEnv(environment, "ANDROID_DATA")
        ShellEnvironmentUtils.putToEnvIfInSystemEnv(environment, "ANDROID_ROOT")
        ShellEnvironmentUtils.putToEnvIfInSystemEnv(environment, "ANDROID_STORAGE")

        // EXTERNAL_STORAGE is needed for /system/bin/am to work on at least
        // Samsung S7 - see https://plus.google.com/110070148244138185604/posts/gp8Lk3aCGp3.
        // https://cs.android.com/android/_/android/platform/system/core/+/fc000489
        ShellEnvironmentUtils.putToEnvIfInSystemEnv(environment, "EXTERNAL_STORAGE")
        ShellEnvironmentUtils.putToEnvIfInSystemEnv(environment, "ASEC_MOUNTPOINT")
        ShellEnvironmentUtils.putToEnvIfInSystemEnv(environment, "LOOP_MOUNTPOINT")

        ShellEnvironmentUtils.putToEnvIfInSystemEnv(environment, "ANDROID_RUNTIME_ROOT")
        ShellEnvironmentUtils.putToEnvIfInSystemEnv(environment, "ANDROID_ART_ROOT")
        ShellEnvironmentUtils.putToEnvIfInSystemEnv(environment, "ANDROID_I18N_ROOT")
        ShellEnvironmentUtils.putToEnvIfInSystemEnv(environment, "ANDROID_TZDATA_ROOT")

        ShellEnvironmentUtils.putToEnvIfInSystemEnv(environment, "BOOTCLASSPATH")
        ShellEnvironmentUtils.putToEnvIfInSystemEnv(environment, "DEX2OATBOOTCLASSPATH")
        ShellEnvironmentUtils.putToEnvIfInSystemEnv(environment, "SYSTEMSERVERCLASSPATH")

        return environment
    }

    override fun getDefaultWorkingDirectoryPath(): String {
        return "/"
    }

    override fun getDefaultBinPath(): String {
        return "/system/bin"
    }

    override fun setupShellCommandEnvironment(
        currentPackageContext: Context,
        executionCommand: ExecutionCommand
    ): HashMap<String, String> {
        val environment = getEnvironment(currentPackageContext, executionCommand.isFailsafe)

        val workingDirectory = executionCommand.workingDirectory
        environment[ENV_PWD] = if (!workingDirectory.isNullOrEmpty()) {
            File(workingDirectory).absolutePath // PWD must be absolute path
        } else {
            getDefaultWorkingDirectoryPath()
        }
        ShellEnvironmentUtils.createHomeDir(environment)

        if (executionCommand.setShellCommandShellEnvironment && shellCommandShellEnvironment != null) {
            environment.putAll(shellCommandShellEnvironment!!.getEnvironment(currentPackageContext, executionCommand))
        }

        return environment
    }
}
