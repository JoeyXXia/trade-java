package dev.commerceflow.trade.identity;

import java.io.IOException;
import jakarta.servlet.http.HttpServletResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

final class ErrorResponses {
    private static final ObjectMapper JSON = new ObjectMapper();
    private ErrorResponses() {}
    static void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        JSON.writeValue(response.getWriter(), new ApiErrors.ErrorBody(code, message));
    }
}
