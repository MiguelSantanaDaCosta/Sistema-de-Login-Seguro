// Descrição: Cliente HTTP do ms-chamados. Repassa a identidade do usuário logado
//            em um JWT assinado com o segredo compartilhado (claims sub + roles).
package com.pfc.thindesk.client;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.pfc.thindesk.security.JwtUtil;

@Component
public class ChamadoClient {

    private final RestClient http;
    private final JwtUtil jwtUtil;

    public ChamadoClient(@Value("${app.chamados.url}") String baseUrl, JwtUtil jwtUtil) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(3000);
        f.setReadTimeout(8000);
        this.http = RestClient.builder().baseUrl(baseUrl + "/api/chamados").requestFactory(f).build();
        this.jwtUtil = jwtUtil;
    }

    /** Chamados do usuário logado, do mais recente para o mais antigo. */
    public List<ChamadoMs> listarDoUsuario(String username) {
        return executar(() -> http.get().uri("/usuario/{u}", username)
                .header("Authorization", bearer()).retrieve()
                .body(new ParameterizedTypeReference<List<ChamadoMs>>() {}));
    }

    /** Fila do técnico (apenas TECNICO/ADMIN). */
    public List<ChamadoMs> listarAbertos() {
        return executar(() -> http.get().uri("/abertos")
                .header("Authorization", bearer()).retrieve()
                .body(new ParameterizedTypeReference<List<ChamadoMs>>() {}));
    }

    public ChamadoMs abrir(String titulo, String descricao) {
        return executar(() -> http.post().contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearer())
                .body(Map.of("titulo", titulo, "descricao", descricao))
                .retrieve().body(ChamadoMs.class));
    }

    public ChamadoMs resolver(String id, String solucao) {
        return executar(() -> http.put().uri("/{id}/resolver", id)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearer())
                .body(Map.of("solucao", solucao))
                .retrieve().body(ChamadoMs.class));
    }

    // JWT curto com a identidade e os papéis do usuário da requisição atual
    private String bearer() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new ChamadoApiException(401, "Usuário não autenticado");
        }
        List<String> roles = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
        return "Bearer " + jwtUtil.gerarToken(auth.getName(), false, roles);
    }

    private <T> T executar(java.util.function.Supplier<T> chamada) {
        try {
            return chamada.get();
        } catch (RestClientResponseException e) {
            throw new ChamadoApiException(e.getStatusCode().value(), mensagemDe(e));
        } catch (ChamadoApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ChamadoApiException("Serviço de chamados indisponível no momento.", e);
        }
    }

    // o ms devolve {status, erro, mensagem, timestamp}
    private String mensagemDe(RestClientResponseException e) {
        try {
            JsonNode n = new com.fasterxml.jackson.databind.ObjectMapper().readTree(e.getResponseBodyAsString());
            if (n.hasNonNull("mensagem")) {
                return n.get("mensagem").asText();
            }
        } catch (Exception ignored) {
            // corpo não era JSON
        }
        return "Erro " + e.getStatusCode().value() + " no serviço de chamados.";
    }
}
