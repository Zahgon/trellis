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
package org.trellisldp.http;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.supplyAsync;
import static org.eclipse.microprofile.config.ConfigProvider.getConfig;
import static org.slf4j.LoggerFactory.getLogger;
import static org.trellisldp.api.Resource.SpecialResources.*;
import static org.trellisldp.api.TrellisUtils.TRELLIS_DATA_PREFIX;
import static org.trellisldp.api.TrellisUtils.getContainer;
import static org.trellisldp.common.HttpConstants.*;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import org.apache.commons.rdf.api.Dataset;
import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.RDF;
import org.eclipse.microprofile.config.Config;
import org.slf4j.Logger;
import org.trellisldp.api.Metadata;
import org.trellisldp.api.RDFFactory;
import org.trellisldp.api.Resource;
import org.trellisldp.api.StorageConflictException;
import org.trellisldp.api.TrellisRuntimeException;
import org.trellisldp.common.HttpRequestContext;
import org.trellisldp.common.HttpStatus;
import org.trellisldp.common.ServiceBundler;
import org.trellisldp.common.TrellisExtensions;
import org.trellisldp.common.TrellisHttpException;
import org.trellisldp.common.TrellisRequest;
import org.trellisldp.common.TrellisResponse;
import org.trellisldp.http.impl.DeleteHandler;
import org.trellisldp.http.impl.GetConfiguration;
import org.trellisldp.http.impl.GetHandler;
import org.trellisldp.http.impl.MementoResource;
import org.trellisldp.http.impl.OptionsHandler;
import org.trellisldp.http.impl.PatchHandler;
import org.trellisldp.http.impl.PostHandler;
import org.trellisldp.http.impl.PutHandler;
import org.trellisldp.vocabulary.LDP;

/**
 * The LDP operations on a resource, one method of the HTTP protocol at a time.
 *
 * <p>Nothing here knows which web server carries the request: it is handed a request and it answers
 * with a response, which the serving layer writes.
 *
 * @author acoburn
 */
public class TrellisHttpResource {

    private static final Logger LOGGER = getLogger(TrellisHttpResource.class);

    protected static final RDF rdf = RDFFactory.getInstance();

    protected final ServiceBundler trellis;
    protected final Map<String, IRI> extensions;
    protected final String baseUrl;
    protected final String defaultJsonLdProfile;
    protected final boolean weakEtags;
    protected final boolean includeMementoDates;
    protected final boolean preconditionRequired;
    protected final boolean createUncontained;
    protected final boolean supportsCreateOnPatch;

    /**
     * Create a Trellis HTTP resource handler.
     *
     * @param trellis the Trellis application bundle
     */
    public TrellisHttpResource(final ServiceBundler trellis) {
        this(trellis, getConfig());
    }

    private TrellisHttpResource(final ServiceBundler trellis, final Config config) {
        this(trellis, TrellisExtensions.buildExtensionMapFromConfig(config),
                config.getOptionalValue(CONFIG_HTTP_BASE_URL, String.class).orElse(null));
    }

    /**
     * Create a Trellis HTTP resource handler.
     *
     * @param trellis the Trellis application bundle
     * @param extensions the extension graph mapping
     * @param baseUrl a base URL
     */
    public TrellisHttpResource(final ServiceBundler trellis, final Map<String, IRI> extensions,
            final String baseUrl) {
        final Config config = getConfig();
        this.trellis = trellis;
        this.extensions = extensions;
        this.baseUrl = baseUrl;
        this.defaultJsonLdProfile = config.getOptionalValue(CONFIG_HTTP_JSONLD_PROFILE, String.class).orElse(null);
        this.weakEtags = config.getOptionalValue(CONFIG_HTTP_WEAK_ETAG, Boolean.class).orElse(Boolean.TRUE);
        this.includeMementoDates = config.getOptionalValue(CONFIG_HTTP_MEMENTO_HEADER_DATES, Boolean.class)
            .orElse(Boolean.TRUE);
        this.preconditionRequired = config.getOptionalValue(CONFIG_HTTP_PRECONDITION_REQUIRED, Boolean.class)
            .orElse(Boolean.FALSE);
        this.createUncontained = config.getOptionalValue(CONFIG_HTTP_PUT_UNCONTAINED, Boolean.class)
            .orElse(Boolean.FALSE);
        this.supportsCreateOnPatch = config.getOptionalValue(CONFIG_HTTP_PATCH_CREATE, Boolean.class)
            .orElse(Boolean.TRUE);
    }

    /**
     * Initialize the Trellis backend with a root container and default ACL quads.
     */
    public void initialize() {
        final IRI root = rdf.createIRI(TRELLIS_DATA_PREFIX);
        try (final Dataset dataset = rdf.createDataset()) {
            LOGGER.debug("Preparing to initialize Trellis at {}", root);
            trellis.getResourceService().get(root).thenCompose(res -> initialize(root, res, dataset))
                .exceptionally(err -> {
                    LOGGER.warn("Unable to auto-initialize Trellis: {}. See DEBUG log for more info", err.getMessage());
                    LOGGER.debug("Error auto-initializing Trellis", err);
                    return null;
                }).toCompletableFuture().join();
        } catch (final Exception ex) {
            throw new TrellisRuntimeException("Error initializing Trellis HTTP layer", ex);
        }
    }

    private CompletionStage<Void> initialize(final IRI id, final Resource res, final Dataset dataset) {
        if (MISSING_RESOURCE.equals(res) || DELETED_RESOURCE.equals(res)) {
            LOGGER.info("Initializing root container: {}", id);
            return trellis.getResourceService().create(Metadata.builder(id).interactionModel(LDP.BasicContainer)
                    .build(), dataset);
        }
        return completedFuture(null);
    }

    /**
     * Perform a GET operation on an LDP Resource.
     *
     * @implNote The Memento implemenation pattern exactly follows
     *           <a href="https://tools.ietf.org/html/rfc7089#section-4.2.1">section 4.2.1 of RFC 7089</a>.
     * @param ctx the request
     * @return the async response
     */
    public CompletionStage<TrellisResponse> getResource(final HttpRequestContext ctx) {
        return fetchResource(new TrellisRequest(ctx))
            .thenApply(TrellisResponse.Builder::build).exceptionally(this::handleException);
    }

    /**
     * Perform a HEAD operation on an LDP Resource.
     *
     * @param ctx the request
     * @return the async response
     */
    public CompletionStage<TrellisResponse> getResourceHeaders(final HttpRequestContext ctx) {
        return fetchResource(new TrellisRequest(ctx))
            .thenApply(TrellisResponse.Builder::build).exceptionally(this::handleException);
    }

    /**
     * Perform an OPTIONS operation on an LDP Resource.
     *
     * @param ctx the request
     * @return the async response
     */
    public CompletionStage<TrellisResponse> options(final HttpRequestContext ctx) {
        final TrellisRequest req = new TrellisRequest(ctx);
        final OptionsHandler optionsHandler = new OptionsHandler(req, trellis, extensions);
        return supplyAsync(optionsHandler::ldpOptions).thenApply(TrellisResponse.Builder::build)
            .exceptionally(this::handleException);
    }

    /**
     * Perform a PATCH operation on an LDP Resource.
     *
     * @param ctx the request
     * @param body the body
     * @return the async response
     */
    public CompletionStage<TrellisResponse> updateResource(final HttpRequestContext ctx, final String body) {
        final TrellisRequest req = new TrellisRequest(ctx);
        final String urlBase = getBaseUrl(req);
        final IRI identifier = trellis.getResourceService().getResourceIdentifier(urlBase, req.getPath());
        final PatchHandler patchHandler = new PatchHandler(req, body, trellis, extensions, supportsCreateOnPatch,
                defaultJsonLdProfile, urlBase);

        return getParent(identifier).thenCombine(trellis.getResourceService().get(identifier), patchHandler::initialize)
            .thenCompose(patchHandler::updateResource).thenCompose(patchHandler::updateMemento)
            .thenApply(TrellisResponse.Builder::build).exceptionally(this::handleException);
    }

    /**
     * Perform a DELETE operation on an LDP Resource.
     *
     * @param ctx the request
     * @return the async response
     */
    public CompletionStage<TrellisResponse> deleteResource(final HttpRequestContext ctx) {
        final TrellisRequest req = new TrellisRequest(ctx);
        final String urlBase = getBaseUrl(req);
        final IRI identifier = trellis.getResourceService().getResourceIdentifier(urlBase, req.getPath());
        final DeleteHandler deleteHandler = new DeleteHandler(req, trellis, extensions, urlBase);

        return getParent(identifier)
            .thenCombine(trellis.getResourceService().get(identifier), deleteHandler::initialize)
            .thenCompose(deleteHandler::deleteResource).thenApply(TrellisResponse.Builder::build)
            .exceptionally(this::handleException);
    }

    /**
     * Perform a POST operation on a LDP Resource.
     *
     * @param ctx the request
     * @param body the body
     * @return the async response
     */
    public CompletionStage<TrellisResponse> createResource(final HttpRequestContext ctx, final InputStream body) {
        final TrellisRequest req = new TrellisRequest(ctx);
        final String urlBase = getBaseUrl(req);
        final String path = req.getPath();
        final String identifier = getIdentifier(req);
        final String separator = path.isEmpty() ? "" : "/";

        final IRI parent = trellis.getResourceService().getResourceIdentifier(urlBase, path);
        final IRI child = trellis.getResourceService().getResourceIdentifier(urlBase, path + separator + identifier);
        final PostHandler postHandler = new PostHandler(req, parent, identifier, body, trellis, extensions, urlBase);

        return trellis.getResourceService().get(parent)
            .thenCombine(trellis.getResourceService().get(child), postHandler::initialize)
            .thenCompose(postHandler::createResource).thenCompose(postHandler::updateMemento)
            .thenApply(TrellisResponse.Builder::build).exceptionally(this::handleException);
    }

    /**
     * Perform a PUT operation on a LDP Resource.
     *
     * @param ctx the request
     * @param body the body
     * @return the async response
     */
    public CompletionStage<TrellisResponse> setResource(final HttpRequestContext ctx, final InputStream body) {
        final TrellisRequest req = new TrellisRequest(ctx);
        final String urlBase = getBaseUrl(req);
        final IRI identifier = trellis.getResourceService().getResourceIdentifier(urlBase, req.getPath());
        final PutHandler putHandler = new PutHandler(req, body, trellis, extensions, preconditionRequired,
                createUncontained, urlBase);

        return getParent(identifier).thenCombine(trellis.getResourceService().get(identifier), putHandler::initialize)
            .thenCompose(putHandler::setResource).thenCompose(putHandler::updateMemento)
            .thenApply(TrellisResponse.Builder::build).exceptionally(this::handleException);
    }

    private CompletionStage<? extends Resource> getParent(final IRI identifier) {
        return getContainer(identifier).map(trellis.getResourceService()::get)
            .orElseGet(() -> completedFuture(MISSING_RESOURCE));
    }

    private String getBaseUrl(final TrellisRequest req) {
        return baseUrl != null ? baseUrl : req.getBaseUrl();
    }

    private CompletionStage<TrellisResponse.Builder> fetchResource(final TrellisRequest req) {
        final String urlBase = getBaseUrl(req);
        final IRI identifier = trellis.getResourceService().getResourceIdentifier(urlBase, req.getPath());
        final GetConfiguration config = new GetConfiguration(req.getVersion() != null,
                weakEtags, includeMementoDates, defaultJsonLdProfile, urlBase);
        final GetHandler getHandler = new GetHandler(req, trellis, extensions, config);

        // Fetch a memento
        if (req.getVersion() != null) {
            LOGGER.debug("Getting versioned resource: {}", req.getVersion());
            return trellis.getMementoService().get(identifier, req.getVersion().getInstant())
                .thenApply(getHandler::initialize).thenApply(getHandler::standardHeaders)
                .thenCombine(trellis.getMementoService().mementos(identifier), getHandler::addMementoHeaders)
                .thenCompose(getHandler::getRepresentation);

        // Fetch a timemap
        } else if (TIMEMAP.equals(req.getExt())) {
            LOGGER.debug("Getting timemap resource: {}", req.getPath());
            return trellis.getResourceService().get(identifier)
                .thenCombine(trellis.getMementoService().mementos(identifier), (res, mementos) -> {
                    if (MISSING_RESOURCE.equals(res)) {
                        throw TrellisHttpException.notFound();
                    }
                    return new MementoResource(trellis, includeMementoDates).getTimeMapBuilder(mementos, req, urlBase);
                });

        // Fetch a timegate
        } else if (req.getDatetime() != null) {
            LOGGER.debug("Getting timegate resource: {}", req.getDatetime().getInstant());
            return trellis.getMementoService().get(identifier, req.getDatetime().getInstant())
                .thenCombine(trellis.getMementoService().mementos(identifier), (res, mementos) -> {
                    if (MISSING_RESOURCE.equals(res)) {
                        throw TrellisHttpException.notAcceptable();
                    }
                    return new MementoResource(trellis, includeMementoDates).getTimeGateBuilder(mementos, req, urlBase);
                });
        }

        // Fetch the current state of the resource
        LOGGER.debug("Getting resource at: {}", identifier);
        return trellis.getResourceService().get(identifier).thenApply(getHandler::initialize)
            .thenApply(getHandler::standardHeaders)
            .thenCombine(trellis.getMementoService().mementos(identifier), getHandler::addMementoHeaders)
            .thenCompose(getHandler::getRepresentation);
    }

    private String getIdentifier(final TrellisRequest req) {
        final String slug = req.getSlug();
        if (slug != null) {
            return slug;
        }
        return trellis.getResourceService().generateIdentifier();
    }

    private TrellisResponse handleException(final Throwable err) {
        final Throwable cause = err.getCause();
        if (cause instanceof StorageConflictException) {
            LOGGER.debug("Storage conflict error: {}", err.getMessage());
            LOGGER.trace("Storage conflict error: ", err);
            return TrellisResponse.status(HttpStatus.CONFLICT).build();
        } else if (cause instanceof TrellisHttpException) {
            LOGGER.debug("Client error: {}", err.getMessage());
            LOGGER.trace("Client error: ", err);
            return ((TrellisHttpException) cause).getResponse();
        }
        LOGGER.error("Error:", err);
        return TrellisResponse.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
}
