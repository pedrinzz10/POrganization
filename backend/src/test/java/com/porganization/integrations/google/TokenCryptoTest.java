package com.porganization.integrations.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class TokenCryptoTest {

    private static final String CHAVE = Base64.getEncoder().encodeToString(new byte[32]);
    private final TokenCrypto crypto = new TokenCrypto(CHAVE);

    // I06 T2 (CA2)
    @Test
    void cifraDiferenteDoTextoIdaEVoltaECadaCifraEUnica() {
        String cifrado = crypto.encrypt("abc");

        assertThat(cifrado).isNotEqualTo("abc").doesNotContain("abc");
        assertThat(crypto.decrypt(cifrado)).isEqualTo("abc");
        assertThat(crypto.encrypt("abc")).isNotEqualTo(cifrado);
    }

    @Test
    void cifraAdulteradaOuComOutraChaveNaoAbre() {
        String cifrado = crypto.encrypt("refresh-token");
        byte[] bytes = Base64.getDecoder().decode(cifrado);
        bytes[bytes.length - 1] ^= 1;

        assertThatThrownBy(() -> crypto.decrypt(Base64.getEncoder().encodeToString(bytes))).isInstanceOf(IllegalStateException.class);
        byte[] outra = new byte[32];
        outra[0] = 1;
        assertThatThrownBy(() -> new TokenCrypto(Base64.getEncoder().encodeToString(outra)).decrypt(cifrado))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void assinaturaConfereSoParaOMesmoTexto() {
        String assinatura = crypto.sign("usuario.123");

        assertThat(crypto.verify("usuario.123", assinatura)).isTrue();
        assertThat(crypto.verify("usuario.124", assinatura)).isFalse();
    }

    @Test
    void semChaveNaoCifraEChaveDeTamanhoErradoFalhaNaSubida() {
        assertThat(new TokenCrypto("").configured()).isFalse();
        assertThatThrownBy(() -> new TokenCrypto("").encrypt("x")).hasMessageContaining("GOOGLE_TOKEN_KEY");
        assertThatThrownBy(() -> new TokenCrypto(Base64.getEncoder().encodeToString(new byte[16])))
                .hasMessageContaining("32 bytes");
    }
}
