package com.termux.shared.termux.data

import java.util.LinkedHashSet
import java.util.regex.Pattern

object TermuxUrlUtils {

    private var URL_MATCH_REGEX: Pattern? = null

    @JvmStatic
    fun getUrlMatchRegex(): Pattern {
        URL_MATCH_REGEX?.let { return it }

        val regexSb = StringBuilder()

        regexSb.append("(")                       // Begin first matching group.
        regexSb.append("(?:")                     // Begin scheme group.
        regexSb.append("dav|")                    // The DAV proto.
        regexSb.append("dict|")                   // The DICT proto.
        regexSb.append("dns|")                    // The DNS proto.
        regexSb.append("file|")                   // File path.
        regexSb.append("finger|")                 // The Finger proto.
        regexSb.append("ftp(?:s?)|")              // The FTP proto.
        regexSb.append("git|")                    // The Git proto.
        regexSb.append("gemini|")                 // The Gemini proto.
        regexSb.append("gopher|")                 // The Gopher proto.
        regexSb.append("http(?:s?)|")             // The HTTP proto.
        regexSb.append("imap(?:s?)|")             // The IMAP proto.
        regexSb.append("irc(?:[6s]?)|")           // The IRC proto.
        regexSb.append("ip[fn]s|")                // The IPFS proto.
        regexSb.append("ldap(?:s?)|")             // The LDAP proto.
        regexSb.append("pop3(?:s?)|")             // The POP3 proto.
        regexSb.append("redis(?:s?)|")            // The Redis proto.
        regexSb.append("rsync|")                  // The Rsync proto.
        regexSb.append("rtsp(?:[su]?)|")          // The RTSP proto.
        regexSb.append("sftp|")                   // The SFTP proto.
        regexSb.append("smb(?:s?)|")              // The SAMBA proto.
        regexSb.append("smtp(?:s?)|")             // The SMTP proto.
        regexSb.append("svn(?:(?:\\+ssh)?)|")     // The Subversion proto.
        regexSb.append("tcp|")                    // The TCP proto.
        regexSb.append("telnet|")                 // The Telnet proto.
        regexSb.append("tftp|")                   // The TFTP proto.
        regexSb.append("udp|")                    // The UDP proto.
        regexSb.append("vnc|")                    // The VNC proto.
        regexSb.append("ws(?:s?)")                // The Websocket proto.
        regexSb.append(")://")                    // End scheme group.
        regexSb.append(")")                       // End first matching group.

        // Begin second matching group.
        regexSb.append("(")

        // User name and/or password in format 'user:pass@'.
        regexSb.append("(?:\\S+(?::\\S*)?@)?")

        // Begin host group.
        regexSb.append("(?:")

        // IP address (from http://www.regular-expressions.info/examples.html).
        regexSb.append("(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)|")

        // Host name or domain.
        regexSb.append("(?:(?:[a-z\\u00a1-\\uffff0-9]-*)*[a-z\\u00a1-\\uffff0-9]+)(?:(?:\\.(?:[a-z\\u00a1-\\uffff0-9]-*)*[a-z\\u00a1-\\uffff0-9]+)*(?:\\.(?:[a-z\\u00a1-\\uffff0-9]-*){1,}[a-z\\u00a1-\\uffff0-9]{1,}))?|")

        // Just path. Used in case of 'file://' scheme.
        regexSb.append("/(?:(?:[a-z\\u00a1-\\uffff0-9]-*)*[a-z\\u00a1-\\uffff0-9]+)")

        // End host group.
        regexSb.append(")")

        // Port number.
        regexSb.append("(?::\\d{1,5})?")

        // Resource path with optional query string.
        regexSb.append("(?:/[a-zA-Z0-9:@%\\-._~!$&()*+,;=?/]*)?")

        // Fragment.
        regexSb.append("(?:#[a-zA-Z0-9:@%\\-._~!$&()*+,;=?/]*)?")

        // End second matching group.
        regexSb.append(")")

        URL_MATCH_REGEX = Pattern.compile(
            regexSb.toString(),
            Pattern.CASE_INSENSITIVE or Pattern.MULTILINE or Pattern.DOTALL
        )

        return URL_MATCH_REGEX!!
    }

    @JvmStatic
    fun extractUrls(text: String): LinkedHashSet<CharSequence> {
        val urlSet = LinkedHashSet<CharSequence>()
        val matcher = getUrlMatchRegex().matcher(text)

        while (matcher.find()) {
            val matchStart = matcher.start(1)
            val matchEnd = matcher.end()
            val url = text.substring(matchStart, matchEnd)
            urlSet.add(url)
        }

        return urlSet
    }
}
