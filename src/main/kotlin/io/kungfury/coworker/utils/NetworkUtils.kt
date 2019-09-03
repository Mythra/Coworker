package io.kungfury.coworker.utils

import java.net.InetAddress
import java.net.NetworkInterface
import java.net.UnknownHostException
import java.util.Enumeration

/**
 * A Series of network utilities to help interacting with the network.
 */
object NetworkUtils {
    /**
     * Get the local lan address, respecting all NICs.
     */
    fun getLocalHostLANAddress(): InetAddress {
        try {
            var candidateAddress: InetAddress? = null
            // Iterate all NICs
            val interfaces: Enumeration<NetworkInterface> = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                val inetAddrs: Enumeration<InetAddress> = iface.inetAddresses
                while (inetAddrs.hasMoreElements()) {
                    val inetAddr = inetAddrs.nextElement()
                    if (!inetAddr.isLoopbackAddress) {

                        if (inetAddr.isSiteLocalAddress) {
                            return inetAddr
                        } else if (candidateAddress == null) {
                            // Found non-loopback address, but not necessarily site-local.
                            // Store it as a candidate to be returned if site-local can't be found.
                            candidateAddress = inetAddr
                        }
                    }
                }
            }
            if (candidateAddress != null) {
                // We did not find a site-local addr, but we found some other non-loopback addr.
                // Server might have a non-site-local addr assigned to it's NIC (or it might be running
                // IPv6 which deprecates the "site-local" concept).
                return candidateAddress
            }
            // At this point we did not find a non-loopback addr.
            // Fall back to returning whatever Inet.getLocalHost() returns.
            return InetAddress.getLocalHost() ?: throw UnknownHostException("The JDK InetAddress.getLocalHost() method unexpectedly returned null.")
        } catch (exception: Exception) {
            val unknownHostException = UnknownHostException("Failed to determine LAN Address: $exception")
            unknownHostException.initCause(exception)
            throw unknownHostException
        }
    }
}
