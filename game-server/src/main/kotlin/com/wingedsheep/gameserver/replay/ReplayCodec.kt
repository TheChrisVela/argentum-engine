package com.wingedsheep.gameserver.replay

import com.wingedsheep.gameserver.persistence.persistenceJson
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Encodes a [CompactReplay] for durable storage as compactly as possible: serialize to JSON, gzip,
 * then base64. The action stream is already tiny (kilobytes), but its JSON is highly repetitive
 * (`"type"` discriminators, entity-id strings), so gzip typically shaves another 80–90%. base64
 * keeps it a portable TEXT column across databases (no `bytea` round-tripping in Spring Data JDBC).
 */
object ReplayCodec {

    fun encode(replay: CompactReplay): String {
        val json = persistenceJson.encodeToString(CompactReplay.serializer(), replay)
        val gzipped = ByteArrayOutputStream().also { out ->
            GZIPOutputStream(out).use { it.write(json.toByteArray(Charsets.UTF_8)) }
        }.toByteArray()
        return Base64.getEncoder().encodeToString(gzipped)
    }

    fun decode(encoded: String): CompactReplay {
        val gzipped = Base64.getDecoder().decode(encoded)
        val json = GZIPInputStream(ByteArrayInputStream(gzipped)).use {
            it.readBytes().toString(Charsets.UTF_8)
        }
        return persistenceJson.decodeFromString(CompactReplay.serializer(), json)
    }
}
