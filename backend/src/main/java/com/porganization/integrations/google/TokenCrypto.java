package com.porganization.integrations.google;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Cifra os tokens do Google com AES-256-GCM (IV aleatório de 12 bytes por cifra, então cifrar o
 * mesmo texto duas vezes dá resultados diferentes) e assina o state do OAuth com HMAC-SHA256.
 * A chave vem de GOOGLE_TOKEN_KEY: 32 bytes em base64 (gere com `openssl rand -base64 32`).
 */
@Component
public class TokenCrypto {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecureRandom random = new SecureRandom();
    private final byte[] key;

    public TokenCrypto(@Value("${porganization.google.token-key:}") String base64Key) {
        this.key = base64Key.isBlank() ? new byte[0] : Base64.getDecoder().decode(base64Key.trim());
        if (key.length != 0 && key.length != 32) {
            throw new IllegalStateException("GOOGLE_TOKEN_KEY deve ter 32 bytes em base64 (openssl rand -base64 32)");
        }
    }

    public boolean configured() {
        return key.length == 32;
    }

    /** base64(IV || cifra || tag). */
    public String encrypt(String plain) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, aesKey(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao cifrar o token", e);
        }
    }

    public String decrypt(String encoded) {
        try {
            byte[] all = Base64.getDecoder().decode(encoded);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, aesKey(), new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
            return new String(cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao decifrar o token (chave trocada?)", e);
        }
    }

    /** HMAC-SHA256 em base64url, com uma chave derivada (não a mesma bytes da cifra). */
    public String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(macKey(), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao assinar", e);
        }
    }

    /** Comparação em tempo constante. */
    public boolean verify(String payload, String signature) {
        return MessageDigest.isEqual(sign(payload).getBytes(StandardCharsets.US_ASCII), signature.getBytes(StandardCharsets.US_ASCII));
    }

    private SecretKeySpec aesKey() {
        requireKey();
        return new SecretKeySpec(key, "AES");
    }

    private byte[] macKey() throws GeneralSecurityException {
        requireKey();
        Mac derive = Mac.getInstance("HmacSHA256");
        derive.init(new SecretKeySpec(key, "HmacSHA256"));
        return derive.doFinal("porganization-oauth-state".getBytes(StandardCharsets.UTF_8));
    }

    private void requireKey() {
        if (!configured()) {
            throw new IllegalStateException("GOOGLE_TOKEN_KEY não configurada");
        }
    }
}
