package se.crisp.codekvast.support.web.config;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import javax.servlet.FilterChain;
import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class WebjarVersionFilterTest {
    private static final String RECOGNIZED_WEBJAR_WITHOUT_VERSION = "/webjars/sockjs-client/sockjs-client.js";
    private static final String RECOGNIZED_WEBJAR_WITH_VERSION = "/webjars/sockjs-client/0.3.4/sockjs-client.js";

    private static WebjarVersionFilter filter = new WebjarVersionFilter();

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @BeforeClass
    public static void beforeTest() throws Exception {
        filter.init(null);
    }

    @Test
    public void testExpandRequestURI_no_webjar() throws Exception {
        assertThat(filter.expandRequestURI("/favicon.ico"), nullValue());
    }

    @Test
    public void testExpandRequestURI_recognized_webjar() throws Exception {
        assertThat(filter.expandRequestURI(RECOGNIZED_WEBJAR_WITHOUT_VERSION), is(RECOGNIZED_WEBJAR_WITH_VERSION));
    }

    @Test
    public void testExpandRequestURI_unrecognized_webjar_with_explicit_version() throws Exception {
        assertThat(filter.expandRequestURI("/webjars/foo/4.5.6/foo.js"), nullValue());
    }

    @Test
    public void testExpandRequestURI_unrecognized_webjar() throws Exception {
        assertThat(filter.expandRequestURI("/webjars/bar/bar.js"), nullValue());
    }

    @Test
    public void testDoFilter_notWebjarRequest() throws Exception {
        // given
        when(request.getRequestURI()).thenReturn("/favicon.ico");

        // when
        filter.doFilter(request, response, filterChain);

        // then
        verify(filterChain).doFilter(request, response);
        verify(request, times(0)).getRequestDispatcher(anyString());
    }

    @Test
    public void testDoFilter_webjarRequest() throws Exception {
        // given
        when(request.getRequestURI()).thenReturn(RECOGNIZED_WEBJAR_WITHOUT_VERSION);
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(request.getRequestDispatcher(RECOGNIZED_WEBJAR_WITH_VERSION)).thenReturn(dispatcher);

        // when
        filter.doFilter(request, response, filterChain);

        // then
        verify(request).getRequestDispatcher(RECOGNIZED_WEBJAR_WITH_VERSION);
        verify(dispatcher).forward(request, response);
        verify(filterChain, times(0)).doFilter(request, response);
    }


}
