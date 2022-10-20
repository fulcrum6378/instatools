package ir.mahdiparastesh.instatools.json

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
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
                if (BuildConfig.DEBUG) throw Exception("Couldn't find XIGSharedData!!")
                else onFailure(null)
            }
        }

        fun findFromJsEval(
            html: String,
            onFailure: (e: Exception?) -> Unit, onSuccess: (wrapper: PageConfig) -> Unit
        ) {
            val read = StringEscapeUtils.unescapeJson(html)
            val configWrapper = read.substring(read.indexOf(scheduledServerJS))
                .substringBefore("</script>")
            try {
                Gson().fromJson(
                    configWrapper, //
                    PageConfig::class.java
                )
            } catch (e: JsonSyntaxException) {
                if (BuildConfig.DEBUG) throw e
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
