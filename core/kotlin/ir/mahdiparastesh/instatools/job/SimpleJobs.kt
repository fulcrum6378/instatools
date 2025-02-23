package ir.mahdiparastesh.instatools.job

import ir.mahdiparastesh.instatools.api.*

object SimpleJobs {

    /**
     * If a user doesn't exist, HTTP error code 404 will be thrown!
     * @throws Api.FailureException
     */
    @Throws(Api.FailureException::class)
    fun userInfo(userId: String): User =
        Api.json<Rest.UserInfo>(Api.Endpoint.USER_INFO.url.format(userId)).user

    /**
     * If a user doesn't exist, HTTP error code 404 will be thrown!
     * @throws Api.FailureException
     */
    @Throws(Api.FailureException::class)
    fun profileInfo(userName: String): User =
        Api.json<GraphQl>(Api.Endpoint.PROFILE_INFO.url.format(userName)).data!!.user!!

    /**
     * Performs any of the actions specified in [GraphQlQuery] concerning [Media].
     * @throws Api.FailureException
     */
    @Throws(Api.FailureException::class)
    fun actionMedia(
        med: Media, graphQlQuery: GraphQlQuery, result: (success: Boolean) -> Unit
    ) {
        val gql = Api.json<GraphQl>(Api.Endpoint.QUERY.url, true, graphQlQuery.body(med.id()))
        result(gql.data != null)
    }
}
