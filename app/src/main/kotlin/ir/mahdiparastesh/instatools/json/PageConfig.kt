package ir.mahdiparastesh.instatools.json

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import com.google.gson.internal.LinkedTreeMap
import com.google.gson.reflect.TypeToken
import ir.mahdiparastesh.instatools.BuildConfig
import org.apache.commons.text.StringEscapeUtils

/** Resolves an HTML from Instagram and collects required data. */
@Suppress("SpellCheckingInspection")
class PageConfig(
    val define: HashMap<String, List<Any>>, val require: HashMap<String, List<Any>>
) {
    companion object {
        private const val scheduledServerJS = "{\"require\":[[\"ScheduledServerJS\""

        @Suppress("UNCHECKED_CAST")
        fun create(map: LinkedTreeMap<String, Any>): PageConfig = PageConfig(
            HashMap<String, List<Any>>().apply {
                (map["define"] as? ArrayList<ArrayList<Any>>)?.also { arr ->
                    for (i in arr) this[i[0] as String] = i.subList(1, i.size)
                }
            }, HashMap<String, List<Any>>().apply {
                (map["require"] as? ArrayList<ArrayList<Any>>)?.also { arr ->
                    for (i in arr) this[i[0] as String] = i.subList(1, i.size)
                }
            }
        )

        fun findFromHtml(
            rawHtml: String, isEvaluated: Boolean, onFailure: (e: Exception) -> Unit,
            testHtml: Context? = null, testJson: Context? = null,
            onSuccess: (wrapper: PageConfig) -> Unit,
        ) {
            val html = if (isEvaluated) StringEscapeUtils.unescapeJson(rawHtml) else rawHtml
            testHtml?.openFileOutput("login.html", 0)
                ?.use { it.write(html.encodeToByteArray()) }

            // Find the JSON blocks containing "scheduledServerJS" and find XIGSharedData
            var read = html
            val jsons = arrayListOf<String>()
            while (read.contains(scheduledServerJS)) {
                read = read.substring(read.indexOf(scheduledServerJS))
                jsons.add(read.substringBefore("</script>"))
                read = read.substringAfter("</script>")
            }
            val json = jsons.find { it.contains("XIGSharedData") }

            if (json != null) try {
                // Find the read PageConfig out of the boilerplate
                @Suppress("UNCHECKED_CAST")
                (GsonBuilder().setLenient().create()
                    .fromJson<Map<String, List<List<Any>>>>(
                        json, object : TypeToken<Map<String, List<List<Any>>>>() {}.type
                    )["require"]!![0][3] as ArrayList<Map<String, Any>>)
                    .find { Gson().toJson(it).contains("XIGSharedData") }!!
                    .values.elementAt(0) as LinkedTreeMap<String, Any>
            } catch (e: JsonSyntaxException) {
                if (BuildConfig.DEBUG) throw IllegalStateException(
                    "The structure has changed (${e.message}): $json"
                )
                onFailure(e)
                null
            }?.also {
                testJson?.openFileOutput("wrapper.json", 0)
                    ?.use { j -> j.write(Gson().toJson(it).encodeToByteArray()) }
                onSuccess(create(it))
            } else onFailure(NeedAuth())
        }

        class NeedAuth : Exception()
    }

    data class RawSharedData(val config: SharedDataConfig, val rollout_hash: String?)

    data class SharedDataConfig(val viewer: GraphQl.User)

    data class PolarisRoot(
        //val actorID: String,
        val rootView: PolarisView,
        //val tracePolicy: String,
        //val meta: PolarisMeta,
        //val prefetchable: Boolean,
        //val entityKeyConfig: Map<String, Any?>,
        //val hostableView: Map<String, Any?>,
        //val url: String "\/p\/CeyIexyDcYd\/"
        val params: PolarisRootParams,
        //val routePath: String,
    )

    data class PolarisView(
        val props: PolarisViewProps,
        val resource: PolarisRootRes,
    )

    data class PolarisViewProps(
        val media_id: String,
        val media_owner_id: String,
        val media_type: Float,
        //val page_logging: Map<String, Any>,
        val user: GraphQl.User,
    )

    data class PolarisRootRes(val __dr: String)
    // post => "PolarisPostRoot.react"
    // story => "PolarisStoriesMediaRoot.react"
    // highlight => "PolarisStoriesHighlightsRoot.react"

    // data class PolarisMeta(val title: String/*, val accessory: Any?, val favicon: Any?*/)

    data class PolarisRootParams(
        val highlight_reel_id: String?,
        val initial_media_id: String?,
        val username: String?,
    )
}
