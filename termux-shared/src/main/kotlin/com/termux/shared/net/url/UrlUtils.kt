package com.termux.shared.net.url

import com.termux.shared.data.DataUtils
import com.termux.shared.logger.Logger
import java.net.MalformedURLException
import java.net.URL

object UrlUtils {

    /** The parts of a [URL]. */
    enum class UrlPart {
        AUTHORITY,
        FILE,
        HOST,
        REF,
        FRAGMENT,
        PATH,
        PORT,
        PROTOCOL,
        QUERY,
        USER_INFO
    }

    private const val LOG_TAG = "UrlUtils"

    /**
     * Join a url base and destination.
     *
     * @param base The base url to open.
     * @param destination The destination url to open.
     * @param logError If an error message should be logged.
     * @return Returns the joined [String] Url, otherwise `null`.
     */
    @JvmStatic
    fun joinUrl(base: String?, destination: String?, logError: Boolean): String? {
        if (DataUtils.isNullOrEmpty(base)) return null
        return try {
            URL(URL(base), destination).toString()
        } catch (e: MalformedURLException) {
            if (logError) {
                Logger.logError(LOG_TAG, "Failed to join url base \"$base\" and destination \"$destination\": ${e.message}")
            }
            null
        }
    }

    /**
     * Get [URL] from url string.
     *
     * @param urlString The urlString string.
     * @return Returns the [URL] if a valid urlString, otherwise `null`.
     */
    @JvmStatic
    fun getUrl(urlString: String?): URL? {
        if (DataUtils.isNullOrEmpty(urlString)) return null
        return try {
            URL(urlString)
        } catch (e: MalformedURLException) {
            null
        }
    }

    /**
     * Get a [URL] part from url string.
     *
     * @param urlString The urlString string.
     * @param urlPart The part to get.
     * @return Returns the [URL] part if a valid urlString and part, otherwise `null`.
     */
    @JvmStatic
    fun getUrlPart(urlString: String?, urlPart: UrlPart): String? {
        val url = getUrl(urlString) ?: return null
        return when (urlPart) {
            UrlPart.AUTHORITY -> url.authority
            UrlPart.FILE -> url.file
            UrlPart.HOST -> url.host
            UrlPart.REF, UrlPart.FRAGMENT -> url.ref
            UrlPart.PATH -> url.path
            UrlPart.PORT -> url.port.toString()
            UrlPart.PROTOCOL -> url.protocol
            UrlPart.QUERY -> url.query
            UrlPart.USER_INFO -> url.userInfo
        }
    }

    /** Remove "https://www.", "https://", "www.", etc */
    @JvmStatic
    fun removeProtocol(urlString: String?): String? {
        return urlString?.replaceFirst("^(http[s]?://www\\.|http[s]?://|www\\.)".toRegex(), "")
    }

    @JvmStatic
    fun areUrlsEqual(url1: String?, url2: String?): Boolean {
        if (url1 == null && url2 == null) return true
        if (url1 == null || url2 == null) return false
        return removeProtocol(url1)?.replace("/+$".toRegex(), "") == 
               removeProtocol(url2)?.replace("/+$".toRegex(), "")
    }
}
