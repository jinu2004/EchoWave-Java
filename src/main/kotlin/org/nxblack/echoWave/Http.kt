package org.nxblack.echoWave

import java.net.http.HttpClient
import java.time.Duration

object Http {
    val  client: HttpClient = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_2)
        .connectTimeout(Duration.ofSeconds(20))
        .build()
}