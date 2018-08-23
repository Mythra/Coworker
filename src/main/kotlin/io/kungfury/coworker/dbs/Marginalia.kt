package io.kungfury.coworker.dbs

/**
 * Marginalia adds comments to the end of SQL Queries in order to denote where they were called from.
 *
 */
object Marginalia {
    private val map = HashMap<String, String>()

    /**
     * Add "Marginalia" comments to queries (detect line numbers, etc.)
     *
     * @param uniqueId
     *  The unique ID of the query. Ideally: `ClassName_QueryName`.
     * @param queryToInstrument
     *  The query to instrument. (adds the comment, and the ';' for you).
     */
    fun AddMarginalia(uniqueId: String, queryToInstrument: String): String {
        if (map[uniqueId] != null) {
            return "$queryToInstrument /* ${map[uniqueId]} */;"
        } else {
            val caller = Exception().stackTrace[1]
            val lineNo = caller.lineNumber
            val clazz = caller.className
            val func = caller.methodName

            val finalized = "Class: $clazz, Function: $func, Line: $lineNo"
            map[uniqueId] = finalized
            return "$queryToInstrument /* $finalized */;"
        }
    }

    /**
     * Clears the current Marginalia Cache.
     */
    fun ClearMarginaliaCache() {
        map.clear()
    }
}
