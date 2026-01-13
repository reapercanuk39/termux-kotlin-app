package com.termux.shared.termux.extrakeys

/**
 * The class that implements special buttons for [ExtraKeysView].
 */
class SpecialButton(
    /** The special button key. */
    val key: String
) {
    init {
        map[key] = this
    }

    override fun toString(): String = key

    companion object {
        private val map = HashMap<String, SpecialButton>()

        @JvmField
        val CTRL = SpecialButton("CTRL")
        @JvmField
        val ALT = SpecialButton("ALT")
        @JvmField
        val SHIFT = SpecialButton("SHIFT")
        @JvmField
        val FN = SpecialButton("FN")

        /**
         * Get the [SpecialButton] for [key].
         *
         * @param key The unique key name for the special button.
         */
        @JvmStatic
        fun valueOf(key: String): SpecialButton? = map[key]
    }
}
