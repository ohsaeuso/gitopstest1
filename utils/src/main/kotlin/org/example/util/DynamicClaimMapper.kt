package org.example.util

import org.keycloak.models.ClientSessionContext
import org.keycloak.models.KeycloakSession
import org.keycloak.models.ProtocolMapperModel
import org.keycloak.models.UserSessionModel
import org.keycloak.protocol.oidc.OIDCLoginProtocol
import org.keycloak.protocol.oidc.mappers.AbstractOIDCProtocolMapper
import org.keycloak.protocol.oidc.mappers.OIDCAccessTokenMapper
import org.keycloak.protocol.oidc.mappers.OIDCIDTokenMapper
import org.keycloak.protocol.oidc.mappers.UserInfoTokenMapper
import org.keycloak.provider.ProviderConfigProperty
import org.keycloak.representations.AccessToken
import java.net.URI


class DynamicClaimMapper : AbstractOIDCProtocolMapper(),
   OIDCAccessTokenMapper, OIDCIDTokenMapper, UserInfoTokenMapper
    {

    override fun transformAccessToken(
        token: AccessToken, mappingModel: ProtocolMapperModel,
        session: KeycloakSession, userSession: UserSessionModel,
        clientSessionCtx: ClientSessionContext
    ): AccessToken {
        val baseUrl = DEFAULT_BASE_URL
        val dynamicValue: String = fetchDynamicValue(userSession.user.username, baseUrl)
        token.getOtherClaims()["department_code"] = dynamicValue
        return token
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

    companion object {
        const val PROVIDER_ID: String = "dynamic-claim-mapper"
        const val DEFAULT_BASE_URL: String = "http://worker1:30089"
    }

    override fun getDisplayCategory(): String = TOKEN_MAPPER_CATEGORY

    override fun getDisplayType(): String = "Dynamic Claim Mapper"

    override fun getId(): String = PROVIDER_ID

    override fun getHelpText(): String = "Adds a dynamic custom claim to the Access Token."

    override fun getConfigProperties(): List<ProviderConfigProperty> = mutableListOf()

    override fun getProtocol(): String {
        return OIDCLoginProtocol.LOGIN_PROTOCOL
    }

        override fun getPriority(): Int = 0


    }