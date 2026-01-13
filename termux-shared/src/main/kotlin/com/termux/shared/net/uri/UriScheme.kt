package com.termux.shared.net.uri

/**
 * The [android.net.Uri] schemes.
 *
 * @see <a href="https://www.iana.org/assignments/uri-schemes/uri-schemes.xhtml">IANA URI Schemes</a>
 * @see <a href="https://en.wikipedia.org/wiki/List_of_URI_schemes">List of URI schemes</a>
 */
object UriScheme {
    /** Android app resource. */
    const val SCHEME_ANDROID_RESOURCE: String = "android.resource"

    /** Android content provider. https://www.iana.org/assignments/uri-schemes/prov/content. */
    const val SCHEME_CONTENT: String = "content"

    /** Filesystem or android app asset. https://www.rfc-editor.org/rfc/rfc8089.html. */
    const val SCHEME_FILE: String = "file"

    /** Hypertext Transfer Protocol. */
    const val SCHEME_HTTP: String = "http"

    /** Hypertext Transfer Protocol Secure. */
    const val SCHEME_HTTPS: String = "https"
}
