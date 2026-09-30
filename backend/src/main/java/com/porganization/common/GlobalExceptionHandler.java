package com.porganization.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Todos os erros da API saem como ProblemDetail (RFC 9457, application/problem+json), com
 * títulos em português. A base ResponseEntityExceptionHandler cobre as exceções do Spring MVC
 * (JSON malformado, método não suportado etc.) no mesmo formato.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final Map<Integer, String> TITLES = Map.of(
            400, "Requisição inválida",
            401, "Não autenticado",
            403, "Acesso negado",
            404, "Não encontrado",
            405, "Método não permitido",
            409, "Conflito",
            415, "Tipo de conteúdo não suportado",
            500, "Erro interno");

    public record FieldErrorDetail(String field, String message) {
    }

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail handleNotFound(NotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(InvalidRequestException.class)
    ResponseEntity<Object> handleInvalidRequest(InvalidRequestException ex) {
        return badRequest(List.of(new FieldErrorDetail(ex.getField(), ex.getMessage())), new HttpHeaders());
    }

    // Lançadas dentro de controllers (ex.: @CurrentUser com sub inválido) não podem virar 500
    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .body(problem(HttpStatus.UNAUTHORIZED, "Faça login novamente."));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, "Você não tem acesso a este recurso.");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        // O detalhe fica só no log do servidor; a resposta nunca carrega mensagem interna nem stack trace
        log.error("Erro inesperado", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Ocorreu um erro inesperado. Tente novamente.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldErrorDetail> errors = new ArrayList<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.add(new FieldErrorDetail(error.getField(), error.getDefaultMessage()));
        }
        ex.getBindingResult().getGlobalErrors()
                .forEach(error -> errors.add(new FieldErrorDetail(error.getObjectName(), error.getDefaultMessage())));
        return badRequest(errors, headers);
    }

    // @Valid/@NotBlank etc. direto em parâmetros de controller (path, query)
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldErrorDetail> errors = new ArrayList<>();
        ex.getParameterValidationResults().forEach(result -> result.getResolvableErrors().forEach(error ->
                errors.add(new FieldErrorDetail(result.getMethodParameter().getParameterName(),
                        error.getDefaultMessage()))));
        return badRequest(errors, headers);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.badRequest().headers(headers)
                .body(problem(HttpStatus.BAD_REQUEST, "O corpo da requisição está ausente ou mal formatado."));
    }

    // Traduz o título dos ProblemDetail gerados pela base (405, 415 etc.)
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem) {
            String title = TITLES.get(statusCode.value());
            if (title != null) {
                problem.setTitle(title);
            }
        }
        return response;
    }

    private ResponseEntity<Object> badRequest(List<FieldErrorDetail> errors, HttpHeaders headers) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Um ou mais campos são inválidos.");
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().headers(headers).body(problem);
    }

    private static ProblemDetail problem(HttpStatus status, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(TITLES.getOrDefault(status.value(), status.getReasonPhrase()));
        return problem;
    }
}
