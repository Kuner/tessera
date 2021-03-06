package com.quorum.tessera.api.filter;

import com.quorum.tessera.config.Peer;
import com.quorum.tessera.core.config.ConfigService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.net.URISyntaxException;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class IPWhitelistFilterTest {

    private ContainerRequestContext ctx;

    private IPWhitelistFilter filter;

    @Before
    public void init() throws URISyntaxException {

        this.ctx = mock(ContainerRequestContext.class);
        final UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getBaseUri()).thenReturn(new URI("otherhost"));
        when(ctx.getUriInfo()).thenReturn(uriInfo);

        final ConfigService configService = mock(ConfigService.class);

        Peer peer = new Peer("http://whitelistedHost:8080");
        when(configService.getPeers()).thenReturn(singletonList(peer));
        when(configService.isUseWhiteList()).thenReturn(true);

        this.filter = new IPWhitelistFilter(configService);

    }

    @Test
    public void disabledFilterAllowsAllRequests() {
        final ConfigService configService = mock(ConfigService.class);
        when(configService.getPeers()).thenReturn(emptyList());
        when(configService.isUseWhiteList()).thenReturn(false);


        final IPWhitelistFilter filter = new IPWhitelistFilter(configService);

        final HttpServletRequest request = mock(HttpServletRequest.class);
        doReturn("someotherhost").when(request).getRemoteAddr();
        doReturn("someotherhost").when(request).getRemoteHost();

        filter.setHttpServletRequest(request);
        filter.filter(ctx);

        verifyZeroInteractions(request);
        verifyZeroInteractions(ctx);
    }

    @Test
    public void hostNotInWhitelistGetsRejected() {

        final Response expectedResponse = Response.status(Response.Status.UNAUTHORIZED).build();

        final HttpServletRequest request = mock(HttpServletRequest.class);
        doReturn("someotherhost").when(request).getRemoteAddr();
        doReturn("someotherhost").when(request).getRemoteHost();

        filter.setHttpServletRequest(request);

        filter.filter(ctx);

        verify(request).getRemoteHost();
        verify(request).getRemoteAddr();

        final ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
        verify(ctx).abortWith(captor.capture());

        assertThat(captor.getValue()).isEqualToComparingFieldByFieldRecursively(expectedResponse);

    }

    @Test
    public void hostInWhitelistGetsAccepted() {
        final HttpServletRequest request = mock(HttpServletRequest.class);
        doReturn("whitelistedHost").when(request).getRemoteAddr();

        filter.setHttpServletRequest(request);

        filter.filter(ctx);

        verify(request).getRemoteHost();
        verify(request).getRemoteAddr();
        verify(ctx).getUriInfo();
        verifyNoMoreInteractions(ctx);

    }

    @Test
    public void errorFilteringStopsFutureFilters() {

        //show that one request goes through okay
        final HttpServletRequest request = mock(HttpServletRequest.class);
        doReturn("whitelistedHost").when(request).getRemoteAddr();
        filter.setHttpServletRequest(request);
        filter.filter(ctx);
        verify(request).getRemoteHost();
        verify(request).getRemoteAddr();
        verify(ctx).getUriInfo();
        verifyNoMoreInteractions(ctx);

        //show the second one errors
        final HttpServletRequest requestError = mock(HttpServletRequest.class);
        doThrow(RuntimeException.class).when(requestError).getRemoteHost();
        filter.setHttpServletRequest(requestError);
        filter.filter(ctx);
        verify(ctx, times(2)).getUriInfo();
        verifyNoMoreInteractions(ctx);
        verify(request).getRemoteAddr();

        //show the third doesn't get filtered
        final HttpServletRequest requestIgnored = mock(HttpServletRequest.class);
        filter.setHttpServletRequest(requestIgnored);
        filter.filter(ctx);
        verifyZeroInteractions(requestIgnored);
        verifyZeroInteractions(ctx);
    }

    @Test
    public void unixsocketIsWhitelisted() throws URISyntaxException {

        final HttpServletRequest requestError = mock(HttpServletRequest.class);
        filter.setHttpServletRequest(requestError);

        final UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getBaseUri()).thenReturn(new URI("unixsocket"));
        when(ctx.getUriInfo()).thenReturn(uriInfo);

        filter.filter(ctx);

        verify(ctx).getUriInfo();
        verifyNoMoreInteractions(ctx);
        verifyZeroInteractions(requestError);
    }

}
