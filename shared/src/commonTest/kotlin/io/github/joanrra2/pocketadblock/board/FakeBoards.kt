package io.github.joanrra2.pocketadblock.board

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf

/** A trimmed copy of a real board's GET /api/status (firmware v0.4.2, web_server.cpp). */
const val SAMPLE_STATUS = """
{"version":"0.4.2","edition":"pro","hostname":"sinkhole","uptime":86400,"freeHeap":180000,"minFreeHeap":150000,
 "defaultPassword":false,"firmwarePendingVerify":false,
 "wifi":{"connected":true,"state":"connected","reason":"","attempts":0,"nextRetry":0,"ssid":"Home","ip":"192.168.18.23",
  "rssi":-52,"apOn":false,"apAlways":false,"apClients":0,"apForcedFor":0,"apSsid":"Sinkhole-4F2A","apIp":"192.168.4.1",
  "captive":false,"txDbm10":195,"txReduced":false,"ip6Global":false,"ip6":["fe80::eaf6:aff:fea8:d544"]},
 "dns":{"blocking":true,"resumeIn":0,"blocked":4291,"allowed":14051,"timeouts":2,"dropped":0,"bypassBlocked":1,
  "cnameBlocked":3,"rateLimited":0,"dotOn":true,"dotConnected":true,"dotQueries":900,"dotConnects":4,"pending":0,
  "upstream":"9.9.9.9","backup":"","winsPrimary":10,"winsBackup":0,"filterUs10":480,"upstreamMode":"builtin",
  "cacheSize":4096,"cachePsram":true,"cacheHits":9000,"cacheMisses":5000},
 "memory":{"psram":true,"psramFree":11300000,"internalFree":176000},
 "blocklist":{"count":140212,"capacity":2200000,"format":2,"signed":true,"keyConfigured":true,"bloomBytes":0,
  "bloomPsram":false,"bloomReady":false,"bloomSkips":0,"slot":"A","activeFor":3600,"created":1763164800,"writing":false,
  "custom":4,"allow":2},
 "update":{"running":false,"lastOk":true,"lastAgo":600,"message":"already up to date"},
 "firmware":{"board":"esp32s3-pro","latest":"0.4.2","available":false,"installing":false,"bytes":0,"checkedAgo":100,
  "checkOk":true,"message":"","signedBuild":true,"auto":false,"manifest":true},
 "dhcp":{"running":false,"leases":0,"lastLease":"","discovers":0,"requests":0,"others":0,"offers":0,"acks":0,"naks":0},
 "mqtt":{"configured":true,"connected":true}}
"""

/** Test-only credentials for the fake board; not a real board's password. */
const val TEST_USER = "admin"
const val TEST_PASSWORD = "test-password-123"

/**
 * An HTTP client where [boards] answer like Pocket AdBlock boards, [others] answer 401
 * with another realm (a router's page), and every other host is unreachable.
 */
fun fakeLan(boards: Set<String>, others: Set<String> = emptySet()): HttpClient = HttpClient(MockEngine { req ->
    val host = req.url.host
    val auth = req.headers[HttpHeaders.Authorization]
    when (host) {
        in boards -> when (auth) {
            null -> respond("authentication required", HttpStatusCode.Unauthorized,
                headersOf(HttpHeaders.WWWAuthenticate, "Basic realm=\"sinkhole\", charset=\"UTF-8\""))
            BoardClient.basicAuth(TEST_USER, TEST_PASSWORD) -> respond(SAMPLE_STATUS, HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"))
            else -> respond("authentication required", HttpStatusCode.Unauthorized,
                headersOf(HttpHeaders.WWWAuthenticate, "Basic realm=\"sinkhole\", charset=\"UTF-8\""))
        }
        in others -> respond("", HttpStatusCode.Unauthorized, headersOf(HttpHeaders.WWWAuthenticate, "Basic realm=\"Router\""))
        else -> throw IllegalStateException("connect timed out")
    }
})
