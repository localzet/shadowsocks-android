

package com.localzet.shadowsocks.plugin

import java.util.*

/**
 * Helper class for processing plugin options.
 *
 * Based on: https://github.com/apache/ant/blob/588ce1f/src/main/org/apache/tools/ant/types/Commandline.java
 */
class PluginOptions : HashMap<String, String?> {
    var id = ""

    constructor() : super()
    constructor(initialCapacity: Int) : super(initialCapacity)
    constructor(initialCapacity: Int, loadFactor: Float) : super(initialCapacity, loadFactor)

    private constructor(options: String?, parseId: Boolean) : this() {
        @Suppress("NAME_SHADOWING")
        var parseId = parseId
        if (options.isNullOrEmpty()) return
        check(options.all { !it.isISOControl() }) { "No control characters allowed." }
        val tokenizer = StringTokenizer("$options;", "\\=;", true)
        val current = StringBuilder()
        var key: String? = null
        while (tokenizer.hasMoreTokens()) when (val nextToken = tokenizer.nextToken()) {
            "\\" -> current.append(tokenizer.nextToken())
            "=" -> if (key == null) {
                key = current.toString()
                current.setLength(0)
            } else current.append(nextToken)
            ";" -> {
                if (key != null) {
                    put(key, current.toString())
                    key = null
                } else if (current.isNotEmpty()) {
                    if (parseId) id = current.toString() else put(current.toString(), null)
                }
                current.setLength(0)
                parseId = false
            }
            else -> current.append(nextToken)
        }
    }

    constructor(options: String?) : this(options, true)
    constructor(id: String, options: String?) : this(options, false) {
        this.id = id
    }

    /**
     * Put but if value is null or default, the entry is deleted.
     *
     * @return Old value before put.
     */
    fun putWithDefault(key: String, value: String?, default: String? = null) =
            if (value == null || value == default) remove(key) else put(key, value)

    private fun append(result: StringBuilder, str: String) = str.indices.map { str[it] }.forEach {
        when (it) {
            '\\', '=', ';' -> {
                result.append('\\') // intentionally no break
                result.append(it)
            }
            else -> result.append(it)
        }
    }

    fun toString(trimId: Boolean): String {
        val result = StringBuilder()
        if (!trimId) if (id.isEmpty()) return "" else append(result, id)
        for ((key, value) in entries) {
            if (result.isNotEmpty()) result.append(';')
            append(result, key)
            if (value != null) {
                result.append('=')
                append(result, value)
            }
        }
        return result.toString()
    }

    override fun toString(): String = toString(true)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return javaClass == other?.javaClass && super.equals(other) && id == (other as PluginOptions).id
    }
    override fun hashCode(): Int = Objects.hash(super.hashCode(), id)
}
