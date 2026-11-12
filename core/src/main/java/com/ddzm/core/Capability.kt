package com.ddzm.core

/**
 * 运行期能力清单。所有能力都由探测得出，绝不由编译期常量假定成立。
 *
 * 设备无 root / 无系统签名时，[SYSTEM_ACTIVITY_EMBED] 恒不授予；
 * [SHELL_SPLIT_SCREEN] 仅在外部 shell 桥接（如 Shizuku）确实可用时授予。
 */
enum class Capability {
    ROLE_HOME,
    APP_LIST_VISIBLE,
    HIDDEN_PROFILES,
    OVERLAY_WINDOW,
    MEDIA_SESSION,
    NOTIFICATION_LISTENER,
    SHELL_SPLIT_SCREEN,
    SYSTEM_FREEFORM,
    SYSTEM_ACTIVITY_EMBED
}

/**
 * 一次能力探测的不可变结果。[granted] 之外的能力一律视为不可用。
 */
data class CapabilitySnapshot(
    val granted: Set<Capability> = emptySet(),
    val probedAtMillis: Long = 0L,
    val source: String = "unset"
) {
    fun has(capability: Capability): Boolean = capability in granted

    fun hasAll(required: Collection<Capability>): Boolean =
        required.isEmpty() || granted.containsAll(required)

    fun missing(required: Collection<Capability>): Set<Capability> =
        required.filterNot { it in granted }.toSet()

    companion object {
        val EMPTY = CapabilitySnapshot()
    }
}

/**
 * 能力探测 SPI。实现在 :window 模块，core 只消费结果。
 */
interface CapabilityProbe {
    fun probe(): CapabilitySnapshot
}
