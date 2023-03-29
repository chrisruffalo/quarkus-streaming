package io.github.chrisruffalo.quarkus.streaming.impl;

import io.github.chrisruffalo.quarkus.streaming.ReceivingStreamResource;
import io.github.chrisruffalo.quarkus.streaming.dto.StreamResponse;
import io.quarkus.vertx.http.runtime.CurrentVertxRequest;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.smallrye.mutiny.unchecked.Unchecked;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.CopyOptions;
import io.vertx.core.file.OpenOptions;
import io.vertx.mutiny.core.file.FileSystem;
import io.vertx.mutiny.core.http.HttpServerRequest;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.client.api.WebClientApplicationException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.Optional;
import java.util.UUID;

public class ReceivingStreamResourceImpl implements ReceivingStreamResource {

    @Inject
    Logger logger;

    @Inject
    CurrentVertxRequest request;

    @Override
    public Uni<StreamResponse> send() {

        final String fileName = UUID.randomUUID() + ".bin"; // don't necessarily figure it out right now
        final Path path = Paths.get("target", "data", fileName).toAbsolutePath().normalize();

        final HttpServerRequest httpServerRequest = new HttpServerRequest(request.getCurrent().request());

        // create the uni that will open the file
        return Uni.createFrom().item(path).emitOn(Infrastructure.getDefaultWorkerPool())
        .onItem().transformToUni(Unchecked.function(p -> {

            // create path before starting
            if (!Files.exists(p.getParent())) {
                try {
                    Files.createDirectories(p.getParent());
                } catch (IOException e) {
                    logger.errorf("Could not create data directory \"%s\" for uploads: %s", p.getParent(), e.getMessage());
                }
            }

            if (!Files.exists(p)) {
                try {
                    Files.createFile(p);
                } catch (IOException e) {
                    logger.errorf("Could not create file directory \"%s\" for uploads: %s", p, e.getMessage());
                }
            }

            final String contentLengthHeader = httpServerRequest.headers().get("Content-length");
            long contentLengthValue = -1;
            if (contentLengthHeader != null && !contentLengthHeader.isEmpty()) {
                try {
                    contentLengthValue = Long.parseLong(contentLengthHeader);
                } catch (Exception ex) {
                    // no-op / silent ignore error
                }
            }
            final long contentLength = contentLengthValue;
            logger.infof("request of size %d -> %s", contentLengthValue, path);
            if (contentLengthValue < 1) {
                throw new WebClientApplicationException(Response.Status.BAD_REQUEST.getStatusCode(), "A positive, non-zero Content-Length is required");
            }

            return new FileSystem(request.getCurrent().vertx().fileSystem())
                .open(p.toString(), new OpenOptions().setCreate(true).setRead(true).setWrite(true))
                .emitOn(Infrastructure.getDefaultWorkerPool()).onItem().transformToUni(f -> f.write(httpServerRequest.bodyAndAwait()))
                .onItem().transformToUni(v -> {
                    logger.infof("closed file");
                    return Uni.createFrom().item(contentLength);
                });
        })).map(Unchecked.function(contentLength-> {
            final StreamResponse response = new StreamResponse();
            response.setReceivedSize(contentLength);
            return response;
        }));

    }

    @Override
    @Blocking
    public StreamResponse blocking(InputStream file) {
        final String fileName = UUID.randomUUID() + ".bin"; // don't necessarily figure it out right now
        final Path path = Paths.get("target", "data", fileName).toAbsolutePath().normalize();

        // create path before starting
        if (!Files.exists(path.getParent())) {
            try {
                Files.createDirectories(path.getParent());
            } catch (IOException e) {
                logger.errorf("Could not create data directory \"%s\" for uploads: %s", path.getParent(), e.getMessage());
            }
        }

        if (!Files.exists(path)) {
            try {
                Files.createFile(path);
            } catch (IOException e) {
                logger.errorf("Could not create file directory \"%s\" for uploads: %s", path, e.getMessage());
            }
        }

        final StreamResponse r = new StreamResponse();

        try {
            long bytes = Files.copy(file, path, StandardCopyOption.REPLACE_EXISTING);
            r.setReceivedSize(bytes);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return r;
    }
}
