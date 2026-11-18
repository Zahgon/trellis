/*
 * Copyright (c) 2020 Aaron Coburn and individual contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.trellisldp.helidon;

import static java.util.Collections.emptyList;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;
import static org.eclipse.microprofile.config.ConfigProvider.getConfig;
import static org.slf4j.LoggerFactory.getLogger;
import static org.trellisldp.helidon.TrellisUtils.getAuthFilters;
import static org.trellisldp.helidon.TrellisUtils.getCorsConfiguration;
import static org.trellisldp.helidon.TrellisUtils.getWebacService;

import io.helidon.webserver.DirectHandler;
import io.helidon.webserver.Routing;
import io.helidon.webserver.SocketConfiguration;
import io.helidon.webserver.WebServer;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.trellisldp.common.HttpRequestFilter;
import org.trellisldp.common.HttpResponseFilter;
import org.trellisldp.common.ServiceBundler;
import org.trellisldp.helidon.config.BasicAuthConfiguration;
import org.trellisldp.helidon.config.JwtAuthConfiguration;
import org.trellisldp.helidon.config.TrellisConfiguration;
import org.trellisldp.http.CacheControlFilter;
import org.trellisldp.http.TrellisHttpFilter;
import org.trellisldp.http.TrellisHttpResource;
import org.trellisldp.http.TrellisHttpService;
import org.trellisldp.http.WebSubHeaderFilter;
import org.trellisldp.vocabulary.Trellis;
import org.trellisldp.webac.WebAcFilter;
import org.trellisldp.webac.WebAcService;

/**
 * A base class for Helidon-based Trellis applications.
 *
 * <p>An application reads its configuration, builds the services the LDP layer runs on, puts the
 * filters around that layer in the order they have always run in, and serves the result on two
 * connectors: the API on one, the operational surface on the other.
 */
public abstract class AbstractTrellisApplication<T extends TrellisConfiguration> {

    private static final Logger LOGGER = getLogger(AbstractTrellisApplication.class);

    /** The configuration key controlling whether an application should initialize its own root resource. */
    public static final String CONFIG_HELIDON_INITIALIZE_ROOT = "trellis.helidon.initialize-root";

    private final Lifecycle lifecycle = new Lifecycle();

    /**
     * Get the Trellis {@link ServiceBundler}. This object collects the various
     * Trellis services used in an application.
     * @return the ServiceBundler
     */
    protected abstract ServiceBundler getServiceBundler();

    /**
     * Get the type of the configuration this application reads.
     * @return the configuration type
     */
    protected abstract Class<T> getConfigurationClass();

    /**
     * Get any additional services to register on the API connector.
     *
     * @implSpec By default, this returns an empty list.
     * @return any additional services
     */
    protected List<io.helidon.webserver.Service> getComponents() {
        return emptyList();
    }

    /**
     * Get the checks the operational surface answers with.
     *
     * @implSpec By default, this returns no checks.
     * @return the checks, by the name each answers under
     */
    protected java.util.Map<String, HealthCheck> getHealthChecks() {
        return Collections.emptyMap();
    }

    /**
     * Get the LDP resource handler.
     *
     * @param config the configuration
     * @param initialize true if the root resource should be created when it is missing
     * @return the LDP resource handler
     */
    protected TrellisHttpResource getLdpComponent(final T config, final boolean initialize) {
        final TrellisHttpResource ldpResource = new TrellisHttpResource(getServiceBundler(),
                Collections.singletonMap("acl", Trellis.PreferAccessControl), config.getBaseUrl());
        if (initialize) {
            ldpResource.initialize();
        }
        return ldpResource;
    }

    /**
     * Setup the trellis application.
     *
     * @apiNote This method is called before anything is served. It can be used to configure or
     *          register any of the Trellis-related services that an implementation instantiates.
     * @param config the configuration
     * @param lifecycle what the running service holds open
     */
    protected void initialize(final T config, final Lifecycle lifecycle) {
        LOGGER.debug("Initializing Trellis application with {}", config.getClass());
    }

    /**
     * Get the name of the application.
     * @return the name
     */
    public String getName() {
        return "Trellis LDP";
    }

    /**
     * Run the application.
     *
     * @param args the command line: the word {@code server} and the path of a configuration file
     * @throws Exception if the service cannot be started
     */
    public void run(final String... args) throws Exception {
        if (args.length < 2 || !"server".equals(args[0])) {
            throw new IllegalArgumentException("usage: " + getName() + " server <configuration file>");
        }
        io.helidon.common.LogConfig.configureRuntime();
        final T config = ConfigurationLoader.load(Paths.get(args[1]), getConfigurationClass());
        final WebServer server = build(config);
        server.start().toCompletableFuture().join();
        LOGGER.info("{} started on port {}", getName(), server.port());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.shutdown().toCompletableFuture().join();
            lifecycle.stop();
        }));
        server.whenShutdown().toCompletableFuture().join();
    }

    /**
     * Build the web server the application is served on.
     *
     * @param config the configuration
     * @return the web server
     */
    public WebServer build(final T config) {
        initialize(config, lifecycle);

        final TrellisHttpResource ldpResource = getLdpComponent(config, getConfig()
                    .getOptionalValue(CONFIG_HELIDON_INITIALIZE_ROOT, Boolean.class).orElse(Boolean.TRUE));
        final TrellisHttpService ldpService = new TrellisHttpService(ldpResource);

        // The order a request meets the filters in: who is behind it, then what the request has to
        // be before it is looked at, then whether that agent may do what it asks. This is the order
        // the old wiring ran them in - authentication, then the LDP layer's own request filter,
        // then authorization - and the LDP filter sits between the two because it says so itself.
        getAuthFilters(config).forEach(ldpService::addRequestFilter);
        ldpService.addRequestFilter(new TrellisHttpFilter());

        // An answer meets the response filters in their own order, which is not the reverse of the
        // request one: the cache-control, the hub link and the cross-origin headers are written
        // first and the access-control links last, as the old wiring wrote them.
        final CacheControlFilter cacheFilter = new CacheControlFilter();
        cacheFilter.setMaxAge(config.getCache().getMaxAge());
        cacheFilter.setMustRevalidate(config.getCache().getMustRevalidate());
        cacheFilter.setNoCache(config.getCache().getNoCache());
        final List<HttpResponseFilter> responseFilters = new ArrayList<>();
        responseFilters.add(cacheFilter);

        // WebSub
        ofNullable(config.getHubUrl()).filter(hub -> !hub.isEmpty()).ifPresent(hub -> {
            final WebSubHeaderFilter webSubFilter = new WebSubHeaderFilter();
            webSubFilter.setHub(hub);
            responseFilters.add(webSubFilter);
        });

        // CORS
        ofNullable(getCorsConfiguration(config)).ifPresent(cors ->
            responseFilters.add(new CrossOriginResourceSharingFilter(cors.getAllowOrigin(),
                        cors.getAllowMethods(), cors.getAllowHeaders(), cors.getExposeHeaders(),
                        cors.getAllowCredentials(), cors.getMaxAge())));

        // Authorization
        final WebAcService webac = getWebacService(config, getServiceBundler().getResourceService());
        if (webac != null) {
            final List<String> challengeTypes = new ArrayList<>();
            of(config.getAuth().getJwt()).filter(JwtAuthConfiguration::getEnabled).map(x -> "Bearer")
                .ifPresent(challengeTypes::add);
            of(config.getAuth().getBasic()).filter(BasicAuthConfiguration::getEnabled).map(x -> "Basic")
                .ifPresent(challengeTypes::add);

            final List<String> challenges = challengeTypes.stream().map(String::trim)
                .map(ch -> TrellisUtils.buildChallenge(ch, config.getAuth().getRealm(), config.getAuth().getScope()))
                .collect(toList());

            final WebAcFilter webacFilter = new WebAcFilter();
            webacFilter.setChallenges(challenges);
            webacFilter.setBaseUrl(config.getBaseUrl());
            webacFilter.setAccessService(webac);
            webacFilter.setResourceService(getServiceBundler().getResourceService());
            ldpService.addRequestFilter((HttpRequestFilter) webacFilter::filter);
            responseFilters.add(webacFilter::filter);
        }

        responseFilters.forEach(ldpService::addResponseFilter);

        final Routing.Builder routing = Routing.builder();
        getComponents().forEach(routing::register);
        routing.register(ldpService);
        routing.error(Throwable.class, ldpService::handleFailure);

        final AdminService admin = new AdminService();
        getHealthChecks().forEach(admin::register);

        return WebServer.builder(routing.build())
            .directHandler(ldpService.unreadableRequestHandler(), DirectHandler.EventType.BAD_REQUEST)
            .port(config.getServer().getApplicationPort())
            .addSocket(SocketConfiguration.builder().name("admin")
                    .port(config.getServer().getAdminPort()).build(),
                    Routing.builder().register(admin).build())
            .build();
    }

    /**
     * Get what the running service holds open.
     *
     * @return the lifecycle
     */
    protected Lifecycle getLifecycle() {
        return lifecycle;
    }
}
