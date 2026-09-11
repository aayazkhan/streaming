package com.streaming.platform.searchservice

import com.streaming.platform.content.AccessTier
import com.streaming.platform.content.ContentSummary
import com.streaming.platform.content.ContentType
import com.streaming.platform.search.AutocompleteSuggestion
import com.streaming.platform.search.SearchIndex
import com.streaming.platform.search.SearchQuery
import com.streaming.platform.search.SearchResult
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put

data class OpenSearchConfig(
    val endpoint: String,
    val index: String,
    val apiKey: String? = null,
)

class OpenSearchSearchIndex(
    private val client: HttpClient,
    private val config: OpenSearchConfig,
) : SearchIndex {
    override suspend fun search(query: SearchQuery): Result<SearchResult> = runCatching {
        val size = query.limit.coerceIn(1, 50)
        val response = client.post("${config.endpoint.trimEnd('/')}/${config.index}/_search") {
            contentType(Json)
            config.apiKey?.let { header("Authorization", "ApiKey $it") }
            setBody(buildSearchBody(query, size))
        }
        if (!response.status.isSuccess()) throw SearchException(HttpStatusCode.fromValue(response.status.value), "SEARCH_INDEX_ERROR", "Search index request failed")
        val root = response.body<JsonObject>()
        val hits = root["hits"]?.jsonObject?.get("hits")?.jsonArray ?: JsonArray(emptyList())
        val rows = hits.map { hit ->
            val hitObject = hit.jsonObject
            val source = hitObject["_source"]?.jsonObject ?: error("Search document has no source")
            source.toSummary()
        }
        val sort = hits.lastOrNull()?.jsonObject?.get("sort")?.jsonArray
        SearchResult(rows, sort?.let(::cursorFromSort))
    }

    override suspend fun autocomplete(prefix: String, limit: Int): Result<List<AutocompleteSuggestion>> = runCatching {
        val normalized = prefix.trim()
        if (normalized.length !in 2..80) return@runCatching emptyList()
        val response = client.post("${config.endpoint.trimEnd('/')}/${config.index}/_search") {
            contentType(Json)
            config.apiKey?.let { header("Authorization", "ApiKey $it") }
            setBody(buildJsonObject {
                put("size", limit.coerceIn(1, 20))
                put("_source", buildJsonArray { add("id"); add("title"); add("type") })
                put("query", buildJsonObject {
                    put("match_phrase_prefix", buildJsonObject { put("title", normalized) })
                })
            })
        }
        if (!response.status.isSuccess()) throw SearchException(HttpStatusCode.fromValue(response.status.value), "SEARCH_INDEX_ERROR", "Search index request failed")
        response.body<JsonObject>()["hits"]!!.jsonObject["hits"]!!.jsonArray.map { hit ->
            val source = hit.jsonObject["_source"]!!.jsonObject
            AutocompleteSuggestion(source.string("title"), source.string("id"), ContentType.valueOf(source.string("type")))
        }
    }

    private fun buildSearchBody(query: SearchQuery, size: Int) = buildJsonObject {
        put("size", size)
        put("_source", buildJsonArray { listOf("id", "type", "title", "synopsis", "posterUrl", "backdropUrl", "releaseYear", "durationSeconds", "accessTier", "rating", "createdAt").forEach(::add) })
        put("query", buildJsonObject {
            put("bool", buildJsonObject {
                put("must", buildJsonArray {
                    add(buildJsonObject {
                        put("multi_match", buildJsonObject {
                            put("query", query.query)
                            put("fields", buildJsonArray { add("title^3"); add("synopsis") })
                            put("fuzziness", "AUTO")
                        })
                    })
                })
                put("filter", buildJsonArray {
                    query.type?.let { add(buildJsonObject { put("term", buildJsonObject { put("type", it.name) }) }) }
                    query.genre?.let { add(buildJsonObject { put("term", buildJsonObject { put("genres", it) }) }) }
                    query.releaseYear?.let { add(buildJsonObject { put("term", buildJsonObject { put("releaseYear", it) }) }) }
                })
            })
        })
        put("sort", buildJsonArray { add(buildJsonObject { put("_score", "desc") }); add(buildJsonObject { put("_id", "asc") }) })
        query.cursor?.let { cursor ->
            val parts = cursor.split(':', limit = 2)
            require(parts.size == 2) { "Search cursor is invalid" }
            put("search_after", buildJsonArray { add(parts[0].toDouble()); add(parts[1]) })
        }
    }

    private fun cursorFromSort(sort: JsonArray): String? = if (sort.size >= 2) "${sort[0].jsonPrimitive.content}:${sort[1].jsonPrimitive.content}" else null

    private fun JsonObject.toSummary() = ContentSummary(
        id = string("id"),
        type = ContentType.valueOf(string("type")),
        title = string("title"),
        synopsis = optionalString("synopsis"),
        posterUrl = optionalString("posterUrl"),
        backdropUrl = optionalString("backdropUrl"),
        releaseYear = optionalString("releaseYear")?.toIntOrNull(),
        durationSeconds = optionalString("durationSeconds")?.toIntOrNull(),
        accessTier = AccessTier.valueOf(string("accessTier")),
        rating = optionalString("rating")?.toDoubleOrNull(),
        createdAt = Instant.parse(string("createdAt")),
    )

    private fun JsonObject.string(key: String): String = this[key]?.jsonPrimitive?.content ?: error("Search document field is missing: $key")
    private fun JsonObject.optionalString(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
}
