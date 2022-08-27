package ir.mahdiparastesh.instatools.json

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import ir.mahdiparastesh.instatools.BuildConfig
import org.apache.commons.text.StringEscapeUtils

@Suppress("SpellCheckingInspection")
class PageConfig(
    val define: Array<Array<Any>>,
    val require: Array<Array<Any>>
) {
    companion object {
        private const val preScheduledApplyEach =
            "(new ServerJS()).handleWithCustomApplyEach(ScheduledApplyEach,"

        fun findConfigWrapper(
            html: String, unescape: Boolean = false,
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
                    Gson().fromJson(
                        if (unescape) StringEscapeUtils.unescapeJson(configWrapper) else configWrapper,
                        PageConfig::class.java
                    )
                } catch (e: JsonSyntaxException) {
                    if (BuildConfig.DEBUG) throw e //Exception("Shared data not found in\n$unescaped")
                    onFailure(e)
                    null
                }?.also { onSuccess(it) }
            else onFailure(null)
        }
    }

    data class RawSharedData(val config: SharedDataConfig, val rollout_hash: String?)

    data class SharedDataConfig(val viewer: GraphQl.User)

    data class PolarisRoot(
        val rootView: PolarisView, //val url: String "\/p\/CeyIexyDcYd\/"
        val params: PolarisRootParams,
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

    data class PolarisRootParams(val initial_media_id: String, val username: String)
}
