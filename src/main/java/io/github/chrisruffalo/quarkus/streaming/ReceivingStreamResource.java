package io.github.chrisruffalo.quarkus.streaming;

import io.github.chrisruffalo.quarkus.streaming.dto.StreamResponse;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.io.InputStream;

@Path("/receive")
@RegisterRestClient(configKey = "receiver")
public interface ReceivingStreamResource {

    @POST
    @Consumes({MediaType.APPLICATION_OCTET_STREAM, MediaType.APPLICATION_XML})
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    Uni<StreamResponse> send();

    @Path("/blocking")
    @POST
    @Consumes({MediaType.APPLICATION_OCTET_STREAM, MediaType.APPLICATION_XML})
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    StreamResponse blocking(final InputStream file);

}
