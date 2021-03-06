package org.yamcs.prometheus;

import java.io.IOException;
import java.io.InputStream;

import org.yamcs.Plugin;
import org.yamcs.PluginException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.http.Handler;
import org.yamcs.http.HandlerContext;
import org.yamcs.http.HttpRequestHandler;
import org.yamcs.http.HttpServer;
import org.yamcs.logging.Log;
import org.yamcs.security.SystemPrivilege;

import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.hotspot.DefaultExports;

public class PrometheusPlugin implements Plugin {

    public static final SystemPrivilege PRIV_GET_METRICS = new SystemPrivilege("Prometheus.GetMetrics");

    private static final Log log = new Log(PrometheusPlugin.class);

    @Override
    public void onLoad(YConfiguration config) throws PluginException {
        YamcsServer yamcs = YamcsServer.getServer();
        yamcs.getSecurityStore().addSystemPrivilege(PRIV_GET_METRICS);

        CollectorRegistry registry = CollectorRegistry.defaultRegistry;
        if (config.getBoolean("jvm")) {
            DefaultExports.register(registry);
        }
        new YamcsInfoExports().register(registry);
        new InstancesExports().register(registry);
        new LinkExports().register(registry);
        new ProcessorExports().register(registry);

        HttpServer httpServer = yamcs.getGlobalService(HttpServer.class);
        if (httpServer == null) {
            log.warn("Can't mount metrics endpoint. Yamcs does not appear to be running an HTTP Server.");
            return;
        }

        new ApiExports(httpServer.getMetricRegistry()).register(registry);

        try (InputStream in = getClass().getResourceAsStream("/yamcs-prometheus.protobin")) {
            httpServer.getProtobufRegistry().importDefinitions(in);
        } catch (IOException e) {
            throw new PluginException(e);
        }

        httpServer.addApi(new PrometheusApi(registry));

        // Prometheus by default expects a /metrics path.
        // Redirect it to /api/prometheus/metrics for convenience
        Handler redirectHandler = new RedirectHandler();
        httpServer.addHandler("metrics", () -> redirectHandler);
    }

    @Sharable
    private static final class RedirectHandler extends Handler {
        @Override
        public void handle(HandlerContext ctx) {
            ChannelHandlerContext nettyCtx = ctx.getNettyChannelHandlerContext();
            FullHttpRequest nettyRequest = ctx.getNettyFullHttpRequest();

            FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                    HttpResponseStatus.TEMPORARY_REDIRECT);
            QueryStringDecoder qs = new QueryStringDecoder(nettyRequest.uri());
            String location = qs.rawPath().replaceFirst("metrics", "api/prometheus/metrics");
            String q = qs.rawQuery();
            if (!q.isEmpty()) {
                location += "?" + q;
            }
            response.headers().add(HttpHeaderNames.LOCATION, location);
            HttpRequestHandler.sendResponse(nettyCtx, nettyRequest, response);
        }
    }
}
