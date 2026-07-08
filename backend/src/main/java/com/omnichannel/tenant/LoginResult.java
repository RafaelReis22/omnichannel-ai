package com.omnichannel.tenant;

/**
 * Resultado do login: tokens direto (sem 2FA) ou um desafio MFA a ser
 * completado em /api/auth/2fa/verify.
 */
public record LoginResult(AuthTokens tokens, String mfaChallengeToken) {

	public static LoginResult authenticated(AuthTokens tokens) {
		return new LoginResult(tokens, null);
	}

	public static LoginResult mfaChallenge(String challengeToken) {
		return new LoginResult(null, challengeToken);
	}

	public boolean requiresMfa() {
		return mfaChallengeToken != null;
	}
}
