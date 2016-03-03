package org.mule.modules.oidctokenvalidator.client.relyingparty;

import com.nimbusds.oauth2.sdk.*;
import com.nimbusds.oauth2.sdk.auth.ClientAuthentication;
import com.nimbusds.oauth2.sdk.http.HTTPResponse;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.id.State;
import com.nimbusds.oauth2.sdk.token.RefreshToken;
import com.nimbusds.openid.connect.sdk.AuthenticationRequest;
import com.nimbusds.openid.connect.sdk.Nonce;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponse;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponseParser;
import com.nimbusds.openid.connect.sdk.token.OIDCTokens;
import org.mule.modules.oidctokenvalidator.client.relyingparty.storage.TokenData;
import org.mule.modules.oidctokenvalidator.config.SingleSignOnConfig;
import org.mule.modules.oidctokenvalidator.exception.RequestTokenFromSsoException;

import java.io.IOException;
import java.net.URI;

public class TokenRequester {

    public AuthenticationRequest buildRedirectRequest(SingleSignOnConfig ssoConfig) {
        State state = new State();
        Nonce nonce = new Nonce();
        Scope scope = Scope.parse("openid");
        ClientID clientId = ssoConfig.getClientSecretBasic().getClientID();

        return new AuthenticationRequest(
                ssoConfig.getProviderMetadata().getAuthorizationEndpointURI(),
                new ResponseType(ResponseType.Value.CODE),
                scope, clientId, ssoConfig.getRedirectUri(), state, nonce);
    }

    public TokenData requestTokensFromSso(String authCode, SingleSignOnConfig ssoConfig) throws RequestTokenFromSsoException {
        try {
            AuthorizationCodeGrant authCodeGrant = new AuthorizationCodeGrant(new AuthorizationCode(authCode), ssoConfig.getRedirectUri());
            TokenRequest tokenReq = new TokenRequest(ssoConfig.getProviderMetadata().getTokenEndpointURI(), ssoConfig.getClientSecretBasic(), authCodeGrant);
            HTTPResponse tokenHTTPResp = tokenReq.toHTTPRequest().send();
            TokenResponse tokenResponse = OIDCTokenResponseParser.parse(tokenHTTPResp);
            if (tokenResponse instanceof TokenErrorResponse) {
                ErrorObject error = ((TokenErrorResponse) tokenResponse).getErrorObject();
                throw new RequestTokenFromSsoException(error.getDescription());
            }
            OIDCTokenResponse oidcTokenResponse = (OIDCTokenResponse) tokenResponse;
            OIDCTokens tokens = oidcTokenResponse.getOIDCTokens();
            return new TokenData(tokens);
        } catch (Exception e) {
            throw new RequestTokenFromSsoException(e.getMessage());
        }
    }

    public TokenData refreshTokenSet(TokenData tokenData, SingleSignOnConfig ssoConfig) throws RequestTokenFromSsoException, IOException, ParseException {
        RefreshToken refreshToken = tokenData.getRefreshToken();
        AuthorizationGrant refreshTokenGrant = new RefreshTokenGrant(refreshToken);

        ClientAuthentication clientAuth = ssoConfig.getClientSecretBasic();

        URI tokenEndpoint = ssoConfig.getProviderMetadata().getTokenEndpointURI();

        TokenRequest request = new TokenRequest(tokenEndpoint, clientAuth, refreshTokenGrant);
        TokenResponse response = OIDCTokenResponseParser.parse(request.toHTTPRequest().send());

        if(response instanceof TokenErrorResponse) {
            throw new RequestTokenFromSsoException("Refresh tokens from SSO failed");
        }
        OIDCTokenResponse oidcTokenResponse = (OIDCTokenResponse) response;

        OIDCTokens refreshedTokens = oidcTokenResponse.getOIDCTokens();
        return new TokenData(refreshedTokens, tokenData.getCookieId());
    }
}
