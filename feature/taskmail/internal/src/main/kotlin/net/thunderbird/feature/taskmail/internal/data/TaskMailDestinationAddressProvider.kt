package net.thunderbird.feature.taskmail.internal.data

internal interface TaskMailDestinationAddressProvider {
    fun getDestinationAddress(): String?
}
