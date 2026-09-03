package com.oxygenupdater.models

import androidx.compose.runtime.Immutable

@Immutable
@JvmInline
value class DeviceRequestFilter private constructor(val value: String) {

    override fun toString() = "DeviceRequestFilter.$value"

    companion object {
        val All get() = DeviceRequestFilter("all")
        val Enabled get() = DeviceRequestFilter("enabled")
        val Disabled get() = DeviceRequestFilter("disabled")
    }
}
