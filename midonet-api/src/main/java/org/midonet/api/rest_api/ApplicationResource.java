/*
 * Copyright 2011 Midokura KK
 * Copyright 2012-2013 Midokura PTE LTD.
 */
package org.midonet.api.rest_api;

import com.google.inject.Inject;
import com.google.inject.servlet.RequestScoped;
import org.midonet.api.Application;
import org.midonet.api.ResourceUriBuilder;
import org.midonet.api.VendorMediaType;
import org.midonet.api.auth.rest_api.TenantResource;
import org.midonet.api.bgp.rest_api.AdRouteResource;
import org.midonet.api.bgp.rest_api.BgpResource;
import org.midonet.api.filter.rest_api.ChainResource;
import org.midonet.api.filter.rest_api.RuleResource;
import org.midonet.api.host.rest_api.HostResource;
import org.midonet.api.host.rest_api.TunnelZoneResource;
import org.midonet.api.monitoring.rest_api.MonitoringResource;
import org.midonet.api.network.rest_api.*;
import org.midonet.api.system_data.rest_api.HostVersionResource;
import org.midonet.api.system_data.rest_api.WriteVersionResource;
import org.midonet.api.system_data.rest_api.SystemStateResource;
import org.midonet.api.tracing.rest_api.TraceConditionResource;
import org.midonet.api.tracing.rest_api.TraceResource;
import org.midonet.api.version.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.security.PermitAll;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

/**
 * The top application resource class.
 */
@RequestScoped
@Path(ResourceUriBuilder.ROOT)
public class ApplicationResource extends AbstractResource {

    private final static Logger log =
            LoggerFactory.getLogger(ApplicationResource.class);

    private final ResourceFactory factory;

    @Inject
    public ApplicationResource(RestApiConfig config, UriInfo uriInfo,
                               SecurityContext context,
                               ResourceFactory factory) {
        super(config, uriInfo, context);
        this.factory = factory;
    }

    /**
     * Tenant resource locator.
     *
     * @return TenantResource object to handle sub-resource requests.
     */
    @Path(ResourceUriBuilder.TENANTS)
    public TenantResource getTenantResource() {
        return factory.getTenantResource();
    }


    /**
     * Router resource locator.
     *
     * @return RouterResource object to handle sub-resource requests.
     */
    @Path(ResourceUriBuilder.ROUTERS)
    public RouterResource getRouterResource() {
        return factory.getRouterResource();
    }

    /**
     * Bridge resource locator.
     *
     * @return BridgeResource object to handle sub-resource requests.
     */
    @Path(ResourceUriBuilder.BRIDGES)
    public BridgeResource getBridgeResource() {
        return factory.getBridgeResource();
    }

    /**
     * Port resource locator.
     *
     * @return PortResource object to handle sub-resource requests.
     */
    @Path(ResourceUriBuilder.PORTS)
    public PortResource getPortResource() {
        return factory.getPortResource();
    }

    /**
     * Route resource locator.
     *
     * @return RouteResource object to handle sub-resource requests.
     */
    @Path(ResourceUriBuilder.ROUTES)
    public RouteResource getRouteResource() {
        return factory.getRouteResource();
    }

    /**
     * Chain resource locator.
     *
     * @return ChainResource object to handle sub-resource requests.
     */
    @Path(ResourceUriBuilder.CHAINS)
    public ChainResource getChainResource() {
        return factory.getChainResource();
    }

    /**
     * PortGroups resource locator.
     *
     * @return ChainResource object to handle sub-resource requests.
     */
    @Path(ResourceUriBuilder.PORT_GROUPS)
    public PortGroupResource getPortGroupResource() {
        return factory.getPortGroupResource();
    }

    /**
     * Rule resource locator.
     *
     * @return RuleResource object to handle sub-resource requests.
     */
    @Path(ResourceUriBuilder.RULES)
    public RuleResource getRuleResource() {
        return factory.getRuleResource();
    }

    /**
     * BGP resource locator.
     *
     * @return BgpResource object to handle sub-resource requests.
     */
    @Path(ResourceUriBuilder.BGP)
    public BgpResource getBgpResource() {
        return factory.getBgpResource();
    }

    /**
     * Ad route resource locator.
     *
     * @return AdRouteResource object to handle sub-resource requests.
     */
    @Path(ResourceUriBuilder.AD_ROUTES)
    public AdRouteResource getAdRouteResource() {
        return factory.getAdRouteResource();
    }

    /**
     * Host resource locator
     *
     * @return HostResource object to handle sub-resource requests.
     */
    @Path(ResourceUriBuilder.HOSTS)
    public HostResource getHostResource() {
        return factory.getHostResource();
    }

    /**
     * Host resource locator
     *
     * @return HostResource object to handle sub-resource requests.
     */
    @Path(ResourceUriBuilder.METRICS)
    public MonitoringResource getMonitoringQueryResource() {
        return factory.getMonitoringQueryResource();
    }

    /**
     * Tunnel Zone resource locator
     *
     * @return TunnelZoneResource object to handle sub-resource requests.
     */
    @Path(ResourceUriBuilder.TUNNEL_ZONES)
    public TunnelZoneResource getTunnelZoneResource() {
        return factory.getTunnelZoneResource();
    }

    /**
     * Trace condition resource locator
     *
     * @return TraceConditionResource object to handle sub-resource requests.
     */
    @Path(ResourceUriBuilder.TRACE_CONDITIONS)
    public TraceConditionResource getTraceConditionResource() {
        return factory.getTraceConditionResource();
    }

    /**
     * Packet Trace resource locator
     *
     * @return TraceResource object to handle sub-resource requests.
     */
    @Path(ResourceUriBuilder.TRACE)
    public TraceResource getTraceResource() {
        return factory.getTraceResource();
    }

    /**
     * Write Version resource locator
     *
     * @return WriteVersionResource object to handle changes to write version.
     */
    @Path(ResourceUriBuilder.WRITE_VERSION)
    public WriteVersionResource getWriteVersionResource() {
        return factory.getWriteVersionResource();
    }

    /**
     * Host Version resource locator
     *
     * @return HostVersion object to handle changes to write version.
     */
    @Path(ResourceUriBuilder.VERSIONS)
    public HostVersionResource geHostVersionResource() {
        return factory.getHostVersionResource();
    }

    /**
     * System State resource locator
     *
     * @return SystemStateResource object to handle changes to system state.
     */
    @Path(ResourceUriBuilder.SYSTEM_STATE)
    public SystemStateResource getSystemStateResource() {
        return factory.getSystemStateResource();
    }

    /**
     * Handler for getting root application resources.
     *
     * @return An Application object.
     */
    @GET
    @PermitAll
    @Produces({ VendorMediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON })
    public Application get() {
        log.debug("ApplicationResource: entered");

        Application a = new Application(getBaseUri());
        a.setVersion(Version.CURRENT);

        log.debug("ApplicationResource: exiting: " + a);
        return a;
    }
}
