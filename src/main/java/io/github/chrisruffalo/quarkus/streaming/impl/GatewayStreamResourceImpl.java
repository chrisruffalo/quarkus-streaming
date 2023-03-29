package io.github.chrisruffalo.quarkus.streaming.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.chrisruffalo.quarkus.streaming.GatewayStreamResource;
import io.github.chrisruffalo.quarkus.streaming.dto.StreamResponse;
import io.quarkus.vertx.http.runtime.CurrentVertxRequest;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.smallrye.mutiny.unchecked.Unchecked;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.RequestOptions;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.core.http.HttpClientRequest;
import io.vertx.mutiny.core.http.HttpClientResponse;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.client.api.WebClientApplicationException;

import java.io.IOException;


public class GatewayStreamResourceImpl implements GatewayStreamResource {

    @Inject
    Logger logger;

    @Inject
    CurrentVertxRequest request;

    public HttpClient client() {
        return request.getCurrent().vertx().createHttpClient();
    }

    public RequestOptions options(final HttpServerRequest source) {
        final RequestOptions options = new RequestOptions();
        options.setURI("/receive");
        options.setHost("localhost");
        options.setPort(8080);
        options.setMethod(HttpMethod.POST);

        // copy headers
        options.setHeaders(source.headers());

        // optionally remove headers
        options.removeHeader("Accept");

        // force header because this endpoint can only parse JSON
        options.addHeader("Accept", MediaType.APPLICATION_JSON);


        // return option
        return options;
    }

    @Override
    public Uni<StreamResponse> send() {
        final HttpServerRequest current = request.getCurrent().request();
        return Uni.createFrom().item(current)
                .onItem().transform(Unchecked.function(r -> {
                    if (!r.headers().contains("Content-Length")) {
                        throw new WebClientApplicationException(Response.Status.BAD_GATEWAY.getStatusCode(), "Content-Length is required");
                    }
                    return r;
                }))
                .onItem().transform(Unchecked.function(r -> {
                    final String contentLengthHeader = r.headers().get("Content-Length");
                    long contentLength = -1;
                    try {
                        contentLength = Long.parseLong(contentLengthHeader);
                    } catch (Exception ex) {
                        // no-op ignore
                    }
                    if (contentLength < 1) {
                        throw new WebClientApplicationException(Response.Status.BAD_GATEWAY.getStatusCode(), "A non-zero, positive Content-Length is required");
                    }
                    return r;
                }))
                .emitOn(Infrastructure.getDefaultWorkerPool()).onItem().transformToUni(r -> new io.vertx.mutiny.core.http.HttpClient(client()).request(options(r))
                    .emitOn(Infrastructure.getDefaultWorkerPool()).onItem().transformToUni(c -> {
                        logger.infof("sending file...");
                        return c.send(new io.vertx.mutiny.core.http.HttpServerRequest(new io.vertx.mutiny.core.http.HttpServerRequest(r)));
                    })
                )
                .emitOn(Infrastructure.getDefaultWorkerPool()).onItem().transformToUni(HttpClientResponse::body)
                .onItem().transform(Unchecked.function(b -> {
                    ObjectMapper mapper = new ObjectMapper();
                    try {
                        return mapper.readValue(b.getBytes(), StreamResponse.class);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }));
    }
}
