package io.kungfury.coworker

import io.kungfury.coworker.utils.NetworkUtils

object NodeIdentifier {
    @JvmStatic var id: String = NetworkUtils.getLocalHostLANAddress().hostAddress
}
