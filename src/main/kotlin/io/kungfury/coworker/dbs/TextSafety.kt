package io.kungfury.coworker.dbs

/**
 * Utilities related to ensuring untrusted input doesn't lead to SQLi when parametrized input can't be used.
 */
object TextSafety {
    private val SAFE_REGEX = Regex("[^A-Za-z0-9]")
    private val SAFE_DASH_REGEX = Regex("[^A-Za-z0-9-]")

    /**
     * Ensures a string is safe, and contains no wild characters.
     *
     * @param str
     *  The string to enforce purity on.
     * @param allowDashes
     *  Whether or not dashes should be allowed.
     */
    fun EnforceStringPurity(str: String, allowDashes: Boolean = false): String {
        if (allowDashes) {
            return SAFE_DASH_REGEX.replace(str, "")
        } else {
            return SAFE_REGEX.replace(str, "")
        }
    }
}