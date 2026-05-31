package org.example.util

import org.jboss.logging.Logger
import org.keycloak.models.ClientSessionContext
import org.keycloak.models.KeycloakSession
import org.keycloak.models.ProtocolMapperModel
import org.keycloak.models.UserSessionModel
import org.keycloak.protocol.oidc.mappers.*
import org.keycloak.provider.ProviderConfigProperty
import org.keycloak.representations.IDToken
import java.net.URI

class DynamicClaimMapper : AbstractOIDCProtocolMapper(), OIDCAccessTokenMapper, OIDCIDTokenMapper, UserInfoTokenMapper {
    override fun setClaim(
        token: IDToken, mappingModel: ProtocolMapperModel,
        userSession: UserSessionModel, keycloakSession: KeycloakSession,
        clientSessionCtx: ClientSessionContext
    ) {
        val baseUrl = DEFAULT_BASE_URL
        val dynamicValue: String = fetchDynamicValue(userSession.user.username, baseUrl)
        token.getOtherClaims()["department_code"] = dynamicValue
        OIDCAttributeMapperHelper.mapClaim(token, mappingModel, listOf(dynamicValue))

    }

    fun fetchDynamicValue(username: String?, baseUrl: String): String {
        val uri = URI("$baseUrl/users/$username/departments")
        try {
            val stream = uri.toURL().openStream()
            return String(stream.readAllBytes())
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    override fun getConfigProperties(): MutableList<ProviderConfigProperty?> {
        return Companion.configProperties
    }

    override fun getHelpText(): String {
        return "Adds custom value to the token."
    }

    override fun getDisplayCategory(): String {
        return "Token mapper"
    }

    override fun getDisplayType(): String {
        return "Custom Value Token Mapper"
    }

    override fun getId(): String {
        return PROVIDER_ID
    }

    companion object {
        private val logger: Logger? = Logger.getLogger(DynamicClaimMapper::class.java)
        const val PROVIDER_ID: String = "dynamic-claim-mapper"
        const val DEFAULT_BASE_URL: String = "http://worker1:30089"
        private val configProperties: MutableList<ProviderConfigProperty?> = ArrayList()

        init {
            OIDCAttributeMapperHelper.addTokenClaimNameConfig(configProperties)
            OIDCAttributeMapperHelper.addIncludeInTokensConfig(configProperties, DynamicClaimMapper::class.java)
        }
    }
}