package ir.mahdiparastesh.instatools.json

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
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
        private const val scheduledServerJSEscaped = "{\\\"require\\\":[[\\\"ScheduledServerJS\\\""

        fun findFromRawHtml(
            html: String, isEvaluated: Boolean, onFailure: (e: Exception) -> Unit,
            test: Context? = null, onSuccess: (wrapper: PageConfig) -> Unit,
        ) {
            if ((isEvaluated && html.contains(scheduledServerJSEscaped)) ||
                (!isEvaluated && html.contains(scheduledServerJS))
            ) return findFromPerhapsBakedHtml(
                if (isEvaluated) StringEscapeUtils.unescapeJson(html) else html,
                onFailure, test, onSuccess
            )
            test?.openFileOutput("login.html", 0)
                ?.use { it.write(html.encodeToByteArray()) }

            var read = html
            val scheduledApplyEach = arrayListOf<String>()
            while (read.contains(preScheduledApplyEach)) {
                read = read.substringAfter(preScheduledApplyEach)
                scheduledApplyEach.add(read.substringBefore(");});});"))
            }
            val configWrapper = scheduledApplyEach.find { it.contains("XIGSharedData") }
                ?.let { if (isEvaluated) StringEscapeUtils.unescapeJson(it) else it }
            if (configWrapper != null)
                try {
                    Gson().fromJson(configWrapper, PageConfig::class.java)
                } catch (e: JsonSyntaxException) {
                    if (BuildConfig.DEBUG) throw IllegalStateException(
                        "The structure has changed (${e.message}): $configWrapper"
                    )
                    onFailure(e)
                    null
                }?.also { onSuccess(it) }
            else onFailure(IllegalStateException("Couldn't find XIGSharedData: $html"))
        }

        private fun findFromPerhapsBakedHtml(
            html: String, onFailure: (e: Exception) -> Unit,
            test: Context? = null, onSuccess: (wrapper: PageConfig) -> Unit
        ) {
            val index = html.indexOf(scheduledServerJS)
            if (index == -1) {
                onFailure(IllegalStateException("scheduledServerJS not found in $html"))
                return; }
            val configWrapper = html.substring(index).substringBefore("</script>")
            test?.openFileOutput("wrapper.json", 0)
                ?.use { it.write(configWrapper.encodeToByteArray()) }
            try {
                @Suppress("UNCHECKED_CAST")
                (GsonBuilder().setLenient().create()
                    .fromJson(configWrapper, PageConfig::class.java).require[0][3]
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
