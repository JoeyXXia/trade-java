package dev.commerceflow.trade.identity;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.*;
import static org.assertj.core.api.Assertions.*;

class TraceFilterTest {
    @Test void clearsThreadContextOnSuccessAndExceptionAndIgnoresCallerTrace() throws Exception {
        var filter = new TraceFilter();
        var request = new MockHttpServletRequest();
        request.addHeader("X-Trace-Id", "untrusted-caller-value");
        var first = new MockHttpServletResponse();
        filter.doFilter(request, first, (req,res) -> {
            assertThat(TraceIds.current()).isEqualTo(first.getHeader("X-Trace-Id"));
            assertThat(TraceIds.current()).isNotEqualTo("untrusted-caller-value");
        });
        assertThat(MDC.get("traceId")).isNull();
        var second = new MockHttpServletResponse();
        assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest(), second,
            (req,res) -> { throw new ServletException("fixture failure"); })).isInstanceOf(ServletException.class);
        assertThat(second.getHeader("X-Trace-Id")).isNotEqualTo(first.getHeader("X-Trace-Id"));
        assertThat(MDC.get("traceId")).isNull();
    }
}
