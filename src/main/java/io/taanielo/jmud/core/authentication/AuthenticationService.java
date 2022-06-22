package io.taanielo.jmud.core.authentication;

import java.io.IOException;

public interface AuthenticationService {
    void authenticate(String input, SuccessHandler successHandler) throws IOException;

    /**
     * Handler for successful authentication
     *
     * When called, authentication is completed and given user is logged in.
     */
    interface SuccessHandler {
        void handle(User authenticatedUser);
    }
}
