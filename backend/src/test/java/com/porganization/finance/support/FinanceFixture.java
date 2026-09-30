package com.porganization.finance.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.porganization.support.IntegrationTest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;

/** Atalhos para montar cenários financeiros pela API nos testes de integração. */
public abstract class FinanceFixture extends IntegrationTest {

    protected final UUID userId = UUID.randomUUID();

    protected String postJson(String url, String json) throws Exception {
        String body = mockMvc.perform(post(url).with(usuario(userId)).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        return body.isBlank() ? "" : JsonPath.read(body, "$.id");
    }

    protected String conta(String nome, String saldoInicial) throws Exception {
        return postJson("/api/finance/accounts",
                "{\"name\":\"" + nome + "\",\"type\":\"CHECKING\",\"initialBalance\":\"" + saldoInicial + "\"}");
    }

    /** Id de uma categoria padrão pelo nome (faz o seed no primeiro acesso). */
    protected String categoria(String nome) throws Exception {
        String body = mockMvc.perform(get("/api/finance/categories").with(usuario(userId)))
                .andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(body, "$[?(@.name == '" + nome + "')].id");
        return ids.getFirst();
    }

    protected String tag(String nome) throws Exception {
        return postJson("/api/finance/tags", "{\"name\":\"" + nome + "\"}");
    }

    protected String transacao(String tipo, String valor, String data, String conta, String categoria, boolean pago,
            String... tags) throws Exception {
        String tagIds = String.join(",", java.util.Arrays.stream(tags).map(t -> "\"" + t + "\"").toList());
        return postJson("/api/finance/transactions", """
                {"type":"%s","amount":"%s","date":"%s","description":"%s","accountId":"%s","categoryId":"%s","paid":%s,"tagIds":[%s]}
                """.formatted(tipo, valor, data, tipo + " " + valor, conta, categoria, pago, tagIds));
    }

    protected String saldo(String conta) throws Exception {
        String body = mockMvc.perform(get("/api/finance/accounts/" + conta).with(usuario(userId)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.balance");
    }
}
