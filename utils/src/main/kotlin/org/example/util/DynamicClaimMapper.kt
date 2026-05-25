package org.example.util

import org.keycloak.models.ClientSessionContext
import org.keycloak.models.KeycloakSession
import org.keycloak.models.ProtocolMapperModel
import org.keycloak.models.UserSessionModel
import org.keycloak.protocol.oidc.mappers.AbstractOIDCProtocolMapper
import org.keycloak.protocol.oidc.mappers.OIDCAccessTokenMapper
import org.keycloak.provider.ProviderConfigProperty
import org.keycloak.representations.AccessToken
import java.net.URI

class DynamicClaimMapper : AbstractOIDCProtocolMapper(),
   OIDCAccessTokenMapper
    {
    override fun transformAccessToken(
        token: AccessToken, mappingModel: ProtocolMapperModel?,
        session: KeycloakSession?, userSession: UserSessionModel,
        clientSessionCtx: ClientSessionContext?
    ): AccessToken {
        val dynamicValue: String = fetchDynamicValue(userSession.user.username)
        token.getOtherClaims()["customAttr"] = dynamicValue
        return token
    }

    private fun fetchDynamicValue(username: String?): String {

        val uri = URI("http://192.168.0.22:8090/users/$username")

        try{
            val stream = uri.toURL().openStream()
            return String(stream.readAllBytes())
        }catch(e:Exception){
            e.printStackTrace()
            throw e
        }

    }

        companion object {
        const val PROVIDER_ID: String = "dynamic-claim-mapper"
    }

        override fun getDisplayCategory(): String = "Token Mapper"

        override fun getDisplayType(): String = "Dynamic Claim Mapper"

        override fun getId(): String = PROVIDER_ID

        override fun getHelpText(): String = "Adds a dynamic custom claim to the Access Token."

        override fun getConfigProperties(): List<ProviderConfigProperty?> = mutableListOf()

    }