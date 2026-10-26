package com.bcp.workshop.auth;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.util.Map;

@Path("/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {
    private static final Logger LOG = Logger.getLogger(AuthResource.class);
    private static final String REQUEST_NULL_MESSAGE = "Request is null";
    private static final String USERNAME_REQUIRED_MESSAGE = "Username is required";
    private static final String PASSWORD_REQUIRED_MESSAGE = "Password is required";
    private static final String INTERNAL_SERVER_ERROR_MESSAGE = "Internal server error";

    @Inject
    AuthService authService;

    @POST
    @Path("/login")
    public Response login(LoginRequest request) {
        if (request == null) {
            return errorResponse(Response.Status.BAD_REQUEST, REQUEST_NULL_MESSAGE);
        }
        if (request.username == null || request.username.trim().isEmpty()) {
            return errorResponse(Response.Status.BAD_REQUEST, USERNAME_REQUIRED_MESSAGE);
        }
        if (request.password == null || request.password.trim().isEmpty()) {
            return errorResponse(Response.Status.BAD_REQUEST, PASSWORD_REQUIRED_MESSAGE);
        }

        try {
            LoginResponse loginResponse = authService.authenticate(request.username, request.password);
            if (loginResponse == null) {
                return errorResponse(Response.Status.UNAUTHORIZED, "Invalid user " + request.username + " or password");
            }
            return Response.ok(loginResponse).build();
        } catch (Exception error) {
            LOG.error("Login request failed", error);
            return errorResponse(Response.Status.INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR_MESSAGE);
        }
    }

    private Response errorResponse(Response.Status status, String message) {
        return Response.status(status).entity(Map.of("message", message)).build();
    }
}
