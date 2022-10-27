package ir.mahdiparastesh.instatools.json

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import ir.mahdiparastesh.instatools.BuildConfig
import org.apache.commons.text.StringEscapeUtils

@Suppress("SpellCheckingInspection")
class PageConfig(
    val define: Array<Array<Any>>, val require: Array<Array<Any>>
) {
    companion object {
        private const val preScheduledApplyEach =
            "(new ServerJS()).handleWithCustomApplyEach(ScheduledApplyEach,"
        private const val scheduledServerJS = "{\"require\":[[\"ScheduledServerJS\""

        fun findFromRawHtml(
            html: String,
            onFailure: (e: Exception?) -> Unit, onSuccess: (wrapper: PageConfig) -> Unit
        ) {
            if (html.contains(scheduledServerJS))
                return findFromPerhapsBakedHtml(html, onFailure, onSuccess)

            var read = html
            val scheduledApplyEach = arrayListOf<String>()
            while (read.contains(preScheduledApplyEach)) {
                read = read.substringAfter(preScheduledApplyEach)
                scheduledApplyEach.add(read.substringBefore(");});});"))
            }
            val configWrapper = scheduledApplyEach.find { it.contains("XIGSharedData") }
            if (configWrapper != null)
                try {
                    Gson().fromJson(configWrapper, PageConfig::class.java)
                } catch (e: JsonSyntaxException) {
                    if (BuildConfig.DEBUG) throw e
                    onFailure(e)
                    null
                }?.also { onSuccess(it) }
            else {
                if (BuildConfig.DEBUG) throw Exception("Couldn't find XIGSharedData: $html")
                else onFailure(null)
            }
        }

        private fun findFromPerhapsBakedHtml(
            html: String,
            onFailure: (e: Exception?) -> Unit, onSuccess: (wrapper: PageConfig) -> Unit
        ) {
            val read = StringEscapeUtils.unescapeJson(html)
            val index = read.indexOf(scheduledServerJS)
            if (index == -1) {
                onFailure(IllegalStateException("scheduledServerJS not found in $read"))
                return; }
            val configWrapper = read.substring(index).substringBefore("</script>")
            try {
                @Suppress("UNCHECKED_CAST")
                (Gson().fromJson(configWrapper, PageConfig::class.java).require[0][3]
                        as ArrayList<Map<String, PageConfig>>)
                    .find { Gson().toJson(it).contains("XIGSharedData") }!!.let {
                        Gson().fromJson(
                            Gson().toJson(it), object : TypeToken<Map<String, PageConfig>>() {}.type
                        ) as Map<String, PageConfig>
                    }.values.elementAt(0)
            } catch (e: JsonSyntaxException) {
                onFailure(e)
                null
            }?.also { onSuccess(it) }
        }
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
