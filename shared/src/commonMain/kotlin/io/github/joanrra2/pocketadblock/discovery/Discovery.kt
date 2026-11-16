package io.github.joanrra2.pocketadblock.discovery

import io.github.joanrra2.pocketadblock.board.BoardAddress
import io.github.joanrra2.pocketadblock.board.BoardClient
import io.github.joanrra2.pocketadblock.net.Ipv4
import io.github.joanrra2.pocketadblock.net.LocalNetwork
import io.github.joanrra2.pocketadblock.platform.MdnsBrowser
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

enum class FoundVia { Mdns, Sweep, SetupAccessPoint, Typed }

data class FoundBoard(val address: BoardAddress, val via: FoundVia)

data class DiscoveryState(
    val running: Boolean = false,
    val network: LocalNetwork? = null,
    val boards: List<FoundBoard> = emptyList(),
    val swept: Int = 0,
    val sweepTotal: Int = 0,
)

/**
 * Finds boards on the phone's Wi-Fi without knowing their address:
 *  1. mDNS: boards announce `_http._tcp` (instance "DNS Sinkhole"); found addresses are
 *     still checked with [BoardClient.probe] before they are listed.
 *  2. A sweep of the phone's /24, for routers and mesh systems that drop mDNS. A board
 *     is recognised by its 401 answer with `realm="sinkhole"`; no password is sent.
 *  3. On a board's setup Wi-Fi ("Sinkhole-XXXX", phone at 192.168.4.x) the board at
 *     192.168.4.1 is marked as a setup access point.
 */
class Discovery(
    private val client: BoardClient,
    private val mdns: MdnsBrowser,
    private val network: () -> LocalNetwork?,
    private val sweepParallel: Int = 64,
    private val sweepTimeoutMs: Long = 900,
    private val mdnsGraceMs: Long = 2500,
) {
    fun search(): Flow<DiscoveryState> = channelFlow {
        val net = network()
        val hosts = net?.let { Ipv4.sweepHosts(it.address, it.prefixLength) }.orEmpty()
        val lock = Mutex()
        var state = DiscoveryState(running = true, network = net, sweepTotal = hosts.size)
        send(state)

        suspend fun update(change: (DiscoveryState) -> DiscoveryState) = lock.withLock {
            state = change(state)
            send(state)
        }

        suspend fun found(address: BoardAddress, via: FoundVia) = update { s ->
            if (s.boards.any { it.address == address }) s
            else s.copy(boards = s.boards + FoundBoard(address, if (isSetupAccessPoint(net, address)) FoundVia.SetupAccessPoint else via))
        }

        val mdnsJob = launch {
            mdns.browse().collect { host ->
                val address = BoardAddress(host)
                if (client.probe(address) == BoardClient.Probe.Board) found(address, FoundVia.Mdns)
            }
        }

        coroutineScope {
            val permits = Semaphore(sweepParallel)
            for (h in hosts) {
                launch {
                    permits.withPermit {
                        val address = BoardAddress(Ipv4.format(h))
                        if (client.probe(address, sweepTimeoutMs) == BoardClient.Probe.Board) found(address, FoundVia.Sweep)
                        update { it.copy(swept = it.swept + 1) }
                    }
                }
            }
        }

        // mDNS answers usually arrive within a second; give it a moment after the sweep.
        withTimeoutOrNull(mdnsGraceMs) { mdnsJob.join() }
        mdnsJob.cancel()
        update { it.copy(running = false) }
    }

    companion object {
        private val SETUP_NET = Ipv4.parse("192.168.4.0")!!
        private val SETUP_IP = Ipv4.parse("192.168.4.1")!!

        fun isSetupAccessPoint(net: LocalNetwork?, address: BoardAddress): Boolean =
            net != null && (net.address and -256) == SETUP_NET && Ipv4.parse(address.host) == SETUP_IP
    }
}
