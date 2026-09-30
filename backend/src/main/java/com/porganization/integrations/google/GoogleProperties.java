package com.porganization.integrations.google;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Credenciais do app no Google Cloud (OAuth client do tipo "Aplicativo da Web") e os endereços do
 * Google, configuráveis para os testes apontarem para o WireMock.
 *
 * @param redirectUri a URL do callback na API, cadastrada no Google: https://<api>/api/integrations/google/callback
 */
@ConfigurationProperties("porganization.google")
public record GoogleProperties(
        String clientId,
        String clientSecret,
        String redirectUri,
        String authUri,
        String tokenUri,
        String revokeUri,
        String calendarApi) {

    public boolean configured() {
        return notBlank(clientId) && notBlank(clientSecret) && notBlank(redirectUri);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
