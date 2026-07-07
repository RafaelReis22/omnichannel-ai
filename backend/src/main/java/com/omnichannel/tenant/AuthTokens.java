package com.omnichannel.tenant;

/** Par de tokens emitido no signup, login e refresh. */
public record AuthTokens(String accessToken, String refreshToken, long accessTokenExpiresInSeconds) {
}
