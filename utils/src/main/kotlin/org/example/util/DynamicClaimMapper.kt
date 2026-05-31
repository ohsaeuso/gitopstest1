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
        val baseUrl = mappingModel?.getConfig()?.get(CONFIG_BASE_URL) ?: DEFAULT_BASE_URL
        val dynamicValue: String = fetchDynamicValue(userSession.user.username, baseUrl)
        token.getOtherClaims()["customAttr"] = dynamicValue
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
        const val CONFIG_BASE_URL: String = "userServiceBaseUrl"
        const val DEFAULT_BASE_URL: String = "http://worker1:30089"
    }

    override fun getDisplayCategory(): String = "Token Mapper"

    override fun getDisplayType(): String = "Dynamic Claim Mapper"

    override fun getId(): String = PROVIDER_ID

    override fun getHelpText(): String = "Adds a dynamic custom claim to the Access Token."

    // 이제 Keycloak Admin Console에서 클라이언트 → Mappers → Dynamic Claim Mapper 추가 시
    // User Service Base URL 필드가 표시되어 환경별로 다른 URL을 입력할 수 있습니다. 서버 설정이나 환경변수 주입 없이
    // UI에서만 관리 가능합니다.
    override fun getConfigProperties(): List<ProviderConfigProperty> = mutableListOf(
        ProviderConfigProperty().apply {
            name = CONFIG_BASE_URL
            label = "User Service Base URL"
            helpText = "Base URL of the user service (e.g. http://my-service:8080)"
            type = ProviderConfigProperty.STRING_TYPE
            defaultValue = DEFAULT_BASE_URL
        }
    )

}