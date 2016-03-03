package org.mule.modules.oidctokenvalidator.client;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.oauth2.sdk.auth.ClientSecretBasic;
import com.nimbusds.oauth2.sdk.auth.Secret;
import com.nimbusds.oauth2.sdk.id.ClientID;
import org.mule.api.store.ObjectStoreException;
import org.mule.modules.oidctokenvalidator.client.relyingparty.RelyingPartyHandler;
import org.mule.modules.oidctokenvalidator.client.tokenvalidation.TokenValidator;
import org.mule.modules.oidctokenvalidator.config.ConnectorConfig;
import org.mule.modules.oidctokenvalidator.config.SingleSignOnConfig;
import org.mule.modules.oidctokenvalidator.exception.HTTPConnectException;
import org.mule.modules.oidctokenvalidator.exception.MetaDataInitializationException;
import org.mule.modules.oidctokenvalidator.exception.TokenValidationException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

public class OpenIdConnectClient {

    private TokenValidator tokenValidator;
    private SingleSignOnConfig ssoConfig;

    public OpenIdConnectClient(SingleSignOnConfig ssoConfig, TokenValidator tokenValidator)
            throws MetaDataInitializationException {
        this.ssoConfig = ssoConfig;
        try {
            ssoConfig.buildProviderMetadata();
        } catch (Exception e) {
            throw new MetaDataInitializationException("Error during MetaData initialization from identity provider: " + e.getMessage());
        }
        this.tokenValidator = tokenValidator;
    }

    public Map<String, Object> ssoTokenValidation(String authHeader)
            throws TokenValidationException, HTTPConnectException {
        return tokenValidator.introspectionTokenValidation(authHeader, ssoConfig);
    }

    public Map<String, Object> localTokenValidation(String authHeader) throws TokenValidationException {
        JWTClaimsSet jwtClaimSet = tokenValidator.localTokenValidation(authHeader, ssoConfig);
        return jwtClaimSet.toJSONObject();
    }

    public void actAsRelyingParty(RelyingPartyHandler relyingPartyHandler) throws ObjectStoreException, ParseException, java.text.ParseException {

        if (relyingPartyHandler.hasTokenCookieAndIsStored()) {
            relyingPartyHandler.handleRequest();
        } else if (relyingPartyHandler.hasRedirectCookieAndIsStored()) {
            relyingPartyHandler.handleTokenRequest();
        } else {
            relyingPartyHandler.handleRedirect();
        }
    }
}