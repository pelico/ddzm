package com.ddzm.core

import java.util.concurrent.ConcurrentHashMap

/**
 * 稳定、线程安全的插件注册表。id 冲突时拒绝后来者（返回 false），
 * 避免两个模块悄悄互相覆盖；需要显式替换请用 [replace]。
 */
class PluginRegistry<T : Any> {

    private val entries = ConcurrentHashMap<String, T>()

    fun register(id: String, entry: T): Boolean {
        require(id.isNotBlank()) { "plugin id must not be blank" }
        return entries.putIfAbsent(id, entry) == null
    }

    fun replace(id: String, entry: T): T? = entries.put(id, entry)

    operator fun get(id: String): T? = entries[id]

    fun require(id: String): T =
        entries[id] ?: throw NoSuchElementException("plugin not registered: $id")

    fun ids(): Set<String> = entries.keys.toSortedSet()

    /** 按 id 排序返回，保证布局解析结果可复现。 */
    fun all(): List<Pair<String, T>> =
        entries.entries.sortedBy { it.key }.map { it.key to it.value }

    fun values(): List<T> = all().map { it.second }

    fun size(): Int = entries.size

    fun clear() = entries.clear()
}
