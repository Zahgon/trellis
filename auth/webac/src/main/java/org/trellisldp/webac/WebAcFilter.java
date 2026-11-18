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
package org.trellisldp.webac;

import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;
import static java.util.function.Predicate.isEqual;
import static java.util.stream.Collectors.toList;
import static org.eclipse.microprofile.config.ConfigProvider.getConfig;
import static org.slf4j.LoggerFactory.getLogger;
import static org.trellisldp.api.TrellisUtils.TRELLIS_DATA_PREFIX;
import static org.trellisldp.common.HttpConstants.CONFIG_HTTP_BASE_URL;
import static org.trellisldp.common.HttpConstants.PREFER;
import static org.trellisldp.common.Prefer.PREFER_REPRESENTATION;
import static org.trellisldp.common.TrellisResponse.LINK;
import static org.trellisldp.common.TrellisRequest.buildBaseUrl;
import static org.trellisldp.vocabulary.Trellis.AnonymousAgent;
import static org.trellisldp.vocabulary.Trellis.PreferAudit;
import static org.trellisldp.vocabulary.Trellis.effectiveAcl;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.RDF;
import org.eclipse.microprofile.config.Config;
import org.slf4j.Logger;
import org.trellisldp.api.RDFFactory;
import org.trellisldp.api.ResourceService;
import org.trellisldp.api.Session;
import org.trellisldp.common.HttpConstants;
import org.trellisldp.common.HttpRequestContext;
import org.trellisldp.common.HttpSession;
import org.trellisldp.common.LinkHeader;
import org.trellisldp.common.Prefer;
import org.trellisldp.common.TrellisHttpException;
import org.trellisldp.common.TrellisResponse;
import org.trellisldp.common.Uris;
import org.trellisldp.vocabulary.ACL;

/**
 * A filter that implements WebAC-based authorization.
 *
 * @see <a href="https://github.com/solid/web-access-control-spec">SOLID WebACL Specification</a>
 *
 * @author acoburn
 */
public class WebAcFilter {

    /**
     * The configuration key controlling which WWW-Authenticate challenges are provided on 401 errors.
     *
     * <p>Multiple challenges should be separated with commas.
     */
    public static final String CONFIG_WEBAC_CHALLENGES = "trellis.webac.challenges";

    /**
     * The configuration key controlling with HTTP methods should apply to the acl:Read.
     *
     * <p>Values defined here will be in addition to GET, HEAD and OPTIONS. Multiple methods should
     * be separated with commas.
     */
    public static final String CONFIG_WEBAC_READABLE_METHODS = "trellis.webac.readable-methods";
    /**
     * The configuration key controlling with HTTP methods should apply to the acl:Write.
     *
     * <p>Values defined here will be in addition to GET, HEAD and OPTIONS. Multiple methods should
     * be separated with commas.
     */
    public static final String CONFIG_WEBAC_WRITABLE_METHODS = "trellis.webac.writable-methods";
    /**
     * The configuration key controlling with HTTP methods should apply to the acl:Append.
     *
     * <p>Values defined here will be in addition to GET, HEAD and OPTIONS. Multiple methods should
     * be separated with commas.
     */
    public static final String CONFIG_WEBAC_APPENDABLE_METHODS = "trellis.webac.appendable-methods";

    /** The configuration key controlling the realm used in a WWW-Authenticate header, or 'trellis' by default. */
    public static final String CONFIG_WEBAC_REALM = "trellis.webac.realm";

    /** The configuration key controlling the scope(s) used in a WWW-Authenticate header. */
    public static final String CONFIG_WEBAC_SCOPE = "trellis.webac.scope";

    /** The configuration key controlling if WebAC checks are enabled or not. Its enabled by default. */
    public static final String CONFIG_WEBAC_ENABED = "trellis.webac.enabled";

    /** The session value for storing access modes. */
    public static final String SESSION_WEBAC_MODES = "trellis.webac.session-modes";

    private static final Logger LOGGER = getLogger(WebAcFilter.class);
    private static final Set<String> readable = new HashSet<>(asList("GET", "HEAD", "OPTIONS"));
    private static final Set<String> writable = new HashSet<>(asList("PUT", "PATCH", "DELETE"));
    private static final Set<String> appendable = new HashSet<>(singletonList("POST"));
    private static final String SLASH = "/";
    private static final RDF rdf = RDFFactory.getInstance();

    private ResourceService resourceService;
    private WebAcService accessService;
    private List<String> challenges;
    private String baseUrl;

    private final boolean enabled;

    /**
     * Create a WebAC filter.
     */
    public WebAcFilter() {
        final Config config = getConfig();

        final String realm = config.getOptionalValue(CONFIG_WEBAC_REALM, String.class).orElse("trellis");
        final String scope = config.getOptionalValue(CONFIG_WEBAC_SCOPE, String.class).orElse("");

        this.challenges = stream(config.getOptionalValue(CONFIG_WEBAC_CHALLENGES, String.class).orElse("").split(","))
                .map(String::trim).map(ch -> buildChallenge(ch, realm, scope)).collect(toList());
        this.baseUrl = config.getOptionalValue(CONFIG_HTTP_BASE_URL, String.class).orElse(null);
        this.enabled = config.getOptionalValue(CONFIG_WEBAC_ENABED, Boolean.class).orElse(Boolean.TRUE);

        config.getOptionalValue(CONFIG_WEBAC_READABLE_METHODS, String.class).ifPresent(r ->
                stream(r.split(",")).map(String::trim).map(String::toUpperCase).forEach(readable::add));
        config.getOptionalValue(CONFIG_WEBAC_WRITABLE_METHODS, String.class).ifPresent(w ->
                stream(w.split(",")).map(String::trim).map(String::toUpperCase).forEach(writable::add));
        config.getOptionalValue(CONFIG_WEBAC_APPENDABLE_METHODS, String.class).ifPresent(a ->
                stream(a.split(",")).map(String::trim).map(String::toUpperCase).forEach(appendable::add));
    }

    /**
     * Set the access service.
     * @param accessService the access service
     */
    public void setAccessService(final WebAcService accessService) {
        this.accessService = requireNonNull(accessService, "Access service may not be null!");
    }

    /**
     * Set the resource service.
     * @param resourceService the resource service
     */
    public void setResourceService(final ResourceService resourceService) {
        this.resourceService = requireNonNull(resourceService, "Resource service may not be null!");
    }

    /**
     * Set the challenges.
     * @param challenges the response challenges
     */
    public void setChallenges(final List<String> challenges) {
        this.challenges = requireNonNull(challenges, "Challenges may not be null!");
    }

    /**
     * Set the base URL.
     * @param baseUrl the base URL
     */
    public void setBaseUrl(final String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /**
     * Check whether the agent behind a request may do what it asks.
     *
     * @param ctx the request
     */
    public void filter(final HttpRequestContext ctx) {
        if (!enabled) {
            return; // If WebAC is disabled then skip the checks
        }

        final String path = ctx.getPath();
        final String base = getBaseUrl(ctx, baseUrl);
        final Session s = buildSession(ctx, baseUrl);
        final String method = ctx.getMethod();

        final IRI resourceIdentifier = resourceService.getResourceIdentifier(base, path);
        final AuthorizedModes modes = accessService.getAuthorizedModes(resourceIdentifier, s);
        ctx.setProperty(SESSION_WEBAC_MODES, modes);

        final Prefer prefer = Prefer.valueOf(ctx.getHeaderString(PREFER));

        // Control-level access
        if (ctx.getQueryParameters().getOrDefault(HttpConstants.EXT, emptyList())
                .contains(HttpConstants.ACL) || reqAudit(prefer)) {
            verifyCanControl(modes.getAccessModes(), s, resourceIdentifier.getIRIString());
        // Everything else
        } else {
            if (readable.contains(method) || reqRepresentation(prefer)) {
                verifyCanRead(modes.getAccessModes(), s, resourceIdentifier.getIRIString());
            }
            if (writable.contains(method)) {
                verifyCanWrite(modes.getAccessModes(), s, resourceIdentifier.getIRIString());
            }
            if (appendable.contains(method)) {
                verifyCanAppend(modes.getAccessModes(), s, resourceIdentifier.getIRIString());
            }
        }
    }

    /**
     * Add the links that say where the access controls of a resource live.
     *
     * @param req the request
     * @param res the answer being built
     */
    public void filter(final HttpRequestContext req, final TrellisResponse res) {
        final Object sessionModes = req.getProperty(SESSION_WEBAC_MODES);
        if (res.isSuccessful() && !"DELETE".equals(req.getMethod())
                && sessionModes instanceof AuthorizedModes) {
            final AuthorizedModes modes = (AuthorizedModes) sessionModes;
            if (modes.getAccessModes().contains(ACL.Control)) {
                final boolean isAcl = req.getQueryParameters()
                    .getOrDefault(HttpConstants.EXT, emptyList()).contains(HttpConstants.ACL);
                final String rel = isAcl ? HttpConstants.ACL + " self" : HttpConstants.ACL;
                final String path = req.getPath();
                res.getHeaders().add(LINK, LinkHeader.fromUri(aclUri(path.startsWith(SLASH) ? path : SLASH + path))
                        .rel(rel).build().toString());
                modes.getEffectiveAcl().map(IRI::getIRIString).map(acl -> effectiveAclToUrlPath(acl, path, res))
                    .ifPresent(urlPath -> res.getHeaders().add(LINK, LinkHeader.fromUri(aclUri(urlPath))
                            .rel(effectiveAcl.getIRIString()).build().toString()));
            }
        }
    }

    protected void verifyCanAppend(final Set<IRI> modes, final Session session, final String path) {
        if (!modes.contains(ACL.Append) && !modes.contains(ACL.Write)) {
            LOGGER.debug("User: {} cannot Append to {}", session.getAgent(), path);
            if (AnonymousAgent.equals(session.getAgent())) {
                throw TrellisHttpException.notAuthorized(challenges);
            }
            throw TrellisHttpException.forbidden();
        }
        LOGGER.trace("User: {} can append to {}", session.getAgent(), path);
    }

    protected void verifyCanControl(final Set<IRI> modes, final Session session, final String path) {
        if (!modes.contains(ACL.Control)) {
            LOGGER.debug("User: {} cannot Control {}", session.getAgent(), path);
            if (AnonymousAgent.equals(session.getAgent())) {
                throw TrellisHttpException.notAuthorized(challenges);
            }
            throw TrellisHttpException.forbidden();
        }
        LOGGER.trace("User: {} can control {}", session.getAgent(), path);
    }

    protected void verifyCanWrite(final Set<IRI> modes, final Session session, final String path) {
        if (!modes.contains(ACL.Write)) {
            LOGGER.debug("User: {} cannot Write to {}", session.getAgent(), path);
            if (AnonymousAgent.equals(session.getAgent())) {
                throw TrellisHttpException.notAuthorized(challenges);
            }
            throw TrellisHttpException.forbidden();
        }
        LOGGER.trace("User: {} can write to {}", session.getAgent(), path);
    }

    protected void verifyCanRead(final Set<IRI> modes, final Session session, final String path) {
        if (!modes.contains(ACL.Read)) {
            LOGGER.debug("User: {} cannot Read from {}", session.getAgent(), path);
            if (AnonymousAgent.equals(session.getAgent())) {
                throw TrellisHttpException.notAuthorized(challenges);
            }
            throw TrellisHttpException.forbidden();
        }
        LOGGER.trace("User: {} can read {}", session.getAgent(), path);
    }

    static boolean reqAudit(final Prefer prefer) {
        return prefer != null && prefer.getInclude().contains(PreferAudit.getIRIString());
    }

    static boolean reqRepresentation(final Prefer prefer) {
        return prefer != null && prefer.getPreference().filter(isEqual(PREFER_REPRESENTATION)).isPresent();
    }

    static String getBaseUrl(final HttpRequestContext ctx, final String baseUrl) {
        if (baseUrl != null) {
            return baseUrl;
        }
        return buildBaseUrl(ctx.getBaseUri(), ctx.getHeaders());
    }

    /**
     * Build the URI of the access controls of a resource at a path.
     *
     * @param path the path of the resource
     * @return the URI of its access controls
     */
    static java.net.URI aclUri(final String path) {
        return Uris.queryParam(Uris.fromPath(path), HttpConstants.EXT, HttpConstants.ACL);
    }

    static Session buildSession(final HttpRequestContext ctx, final String baseUrl) {
        final Session session = HttpSession.from(ctx.getAuthContext());
        final String context = getBaseUrl(ctx, baseUrl);
        if (session.getAgent().getIRIString().startsWith(context)) {
            final String path = session.getAgent().getIRIString().substring(context.length());
            if (path.startsWith(SLASH)) {
                return new HttpSession(rdf.createIRI(TRELLIS_DATA_PREFIX + path.substring(1)));
            }
            return new HttpSession(rdf.createIRI(TRELLIS_DATA_PREFIX + path));
        }
        return session;
    }

    static String buildChallenge(final String challenge, final String realm, final String scope) {
        final String realmParam = realm.isEmpty() ? "" : " realm=\"" + realm + "\"";
        final String scopeParam = scope.isEmpty() ? "" : " scope=\"" + scope + "\"";
        return challenge + realmParam + scopeParam;
    }

    static String effectiveAclToUrlPath(final String effectiveAclPath, final String resourceAclPath,
            final TrellisResponse response) {

        final String effectivePath = normalizePath(effectiveAclPath);
        final String resourcePath = normalizePath(resourceAclPath);
        final boolean inherited = ! effectivePath.equals(resourcePath);
        final boolean isContainer = inherited || response.getHeaders().get(LINK).stream()
            .map(LinkHeader::valueOf).anyMatch(link ->
                    link.getUri().toString().endsWith("Container") && link.getRels().contains(LinkHeader.TYPE));
        if (SLASH.equals(effectivePath) || !isContainer) {
            return effectivePath;
        }
        return effectivePath + SLASH;
    }

    static String normalizePath(final String path) {
        if (path.startsWith(TRELLIS_DATA_PREFIX)) {
            return path.substring(TRELLIS_DATA_PREFIX.length() - 1);
        } else if (path.startsWith(SLASH)) {
            return path;
        }
        return SLASH + path;
    }
}
