package com.termux.app.agents.models

/**
 * Capability system for agent permission control.
 * 
 * Agents must declare capabilities in their definition.
 * The daemon enforces these before any action.
 */
sealed class Capability {
    abstract val name: String
    
    /**
     * Filesystem capabilities
     */
    sealed class Filesystem : Capability() {
        object Read : Filesystem() {
            override val name = "filesystem.read"
        }
        object Write : Filesystem() {
            override val name = "filesystem.write"
        }
        object Exec : Filesystem() {
            override val name = "filesystem.exec"
        }
        object Delete : Filesystem() {
            override val name = "filesystem.delete"
        }
        
        companion object {
            fun all() = listOf(Read, Write, Exec, Delete)
        }
    }
    
    /**
     * Network capabilities
     */
    sealed class Network : Capability() {
        object None : Network() {
            override val name = "network.none"
        }
        object Local : Network() {
            override val name = "network.local"
        }
        object External : Network() {
            override val name = "network.external"
        }
        
        companion object {
            fun all() = listOf(None, Local, External)
        }
    }
    
    /**
     * Execution capabilities - control which binaries can be run
     */
    sealed class Exec : Capability() {
        object Pkg : Exec() {
            override val name = "exec.pkg"
        }
        object Git : Exec() {
            override val name = "exec.git"
        }
        object Qemu : Exec() {
            override val name = "exec.qemu"
        }
        object Iso : Exec() {
            override val name = "exec.iso"
        }
        object Apk : Exec() {
            override val name = "exec.apk"
        }
        object Docker : Exec() {
            override val name = "exec.docker"
        }
        object Shell : Exec() {
            override val name = "exec.shell"
        }
        object Python : Exec() {
            override val name = "exec.python"
        }
        object Build : Exec() {
            override val name = "exec.build"
        }
        object Analyze : Exec() {
            override val name = "exec.analyze"
        }
        object Compress : Exec() {
            override val name = "exec.compress"
        }
        object Custom : Exec() {
            override val name = "exec.custom"
        }
        object Curl : Exec() {
            override val name = "exec.curl"
        }
        object Wget : Exec() {
            override val name = "exec.wget"
        }
        object Ssh : Exec() {
            override val name = "exec.ssh"
        }
        object Tar : Exec() {
            override val name = "exec.tar"
        }
        object Unzip : Exec() {
            override val name = "exec.unzip"
        }
        object BusyBox : Exec() {
            override val name = "exec.busybox"
        }
        
        companion object {
            fun all() = listOf(Pkg, Git, Qemu, Iso, Apk, Docker, Shell, Python, Build, Analyze, Compress, Custom, Curl, Wget, Ssh, Tar, Unzip, BusyBox)
        }
    }
    
    /**
     * Memory capabilities
     */
    sealed class Memory : Capability() {
        object Read : Memory() {
            override val name = "memory.read"
        }
        object Write : Memory() {
            override val name = "memory.write"
        }
        object Shared : Memory() {
            override val name = "memory.shared"
        }
        
        companion object {
            fun all() = listOf(Read, Write, Shared)
        }
    }
    
    /**
     * System capabilities
     */
    sealed class System : Capability() {
        object Info : System() {
            override val name = "system.info"
        }
        object Process : System() {
            override val name = "system.process"
        }
        object Proc : System() {
            override val name = "system.proc"
        }
        object Env : System() {
            override val name = "system.env"
        }
        
        companion object {
            fun all() = listOf(Info, Process, Proc, Env)
        }
    }
    
    companion object {
        /**
         * All known capabilities as a set.
         * Must be lazy to avoid static initialization order issues with sealed class companions.
         */
        val ALL: Set<Capability> by lazy {
            (Filesystem.all() + Network.all() + Exec.all() + Memory.all() + System.all()).toSet()
        }
        
        /**
         * All known capabilities
         */
        fun all(): List<Capability> = 
            Filesystem.all() + Network.all() + Exec.all() + Memory.all() + System.all()
        
        /**
         * Parse capability from string name
         */
        fun fromString(name: String): Capability? {
            return all().find { it.name == name }
        }
        
        /**
         * Parse multiple capabilities from string list
         */
        fun fromStrings(names: List<String>): Set<Capability> {
            return names.mapNotNull { fromString(it) }.toSet()
        }
        
        /**
         * Binary to capability mapping for enforcement
         */
        val BINARY_CAPABILITIES: Map<String, Capability> = mapOf(
            // Package management
            "pkg" to Exec.Pkg,
            "apt" to Exec.Pkg,
            "apt-get" to Exec.Pkg,
            "apt-cache" to Exec.Pkg,
            "dpkg" to Exec.Pkg,
            "dpkg-deb" to Exec.Pkg,
            // Git
            "git" to Exec.Git,
            // QEMU
            "qemu-system-x86_64" to Exec.Qemu,
            "qemu-system-aarch64" to Exec.Qemu,
            "qemu-system-arm" to Exec.Qemu,
            "qemu-img" to Exec.Qemu,
            // ISO tools
            "xorriso" to Exec.Iso,
            "mkisofs" to Exec.Iso,
            "isoinfo" to Exec.Iso,
            "genisoimage" to Exec.Iso,
            // APK tools
            "apktool" to Exec.Apk,
            "jadx" to Exec.Apk,
            "aapt" to Exec.Apk,
            "aapt2" to Exec.Apk,
            "zipalign" to Exec.Apk,
            "apksigner" to Exec.Apk,
            // Docker
            "docker" to Exec.Docker,
            "podman" to Exec.Docker,
            // Shell
            "bash" to Exec.Shell,
            "sh" to Exec.Shell,
            "zsh" to Exec.Shell,
            // Python
            "python" to Exec.Python,
            "python3" to Exec.Python,
            "pip" to Exec.Python,
            "pip3" to Exec.Python,
            // Build tools
            "make" to Exec.Build,
            "cmake" to Exec.Build,
            "gradle" to Exec.Build,
            "gradlew" to Exec.Build,
            "ninja" to Exec.Build,
            "meson" to Exec.Build,
            // Compression
            "tar" to Exec.Compress,
            "gzip" to Exec.Compress,
            "bzip2" to Exec.Compress,
            "xz" to Exec.Compress,
            "zstd" to Exec.Compress,
            "zip" to Exec.Compress,
            "unzip" to Exec.Compress,
            // BusyBox
            "busybox" to Exec.BusyBox,
            "busybox-modern" to Exec.BusyBox
        )
        
        /**
         * Get required capability for a binary
         */
        fun getRequiredCapability(binary: String): Capability? {
            return BINARY_CAPABILITIES[binary]
        }
    }
}
