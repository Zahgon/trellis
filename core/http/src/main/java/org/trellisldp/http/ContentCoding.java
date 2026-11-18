/*
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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.zip.DataFormatException;
import java.util.zip.GZIPOutputStream;
import java.util.zip.Inflater;

import org.slf4j.Logger;
import org.trellisldp.common.HttpStatus;
import org.trellisldp.common.TrellisResponse;
import org.trellisldp.common.TrellisHttpException;

/**
 * Reading and writing entities that travel compressed.
 *
 * <p>A request entity may carry more than one deflate stream, one after another, and all of them
 * belong to the entity. What follows the last of them decides the answer: one byte says nothing and
 * is passed over, bytes that do not begin a stream are a fault of the server rather than of the
 * request, and a stream that begins and then stops carries whatever it managed to say. A stream
 * whose data does not hold together, or that holds less than its trailer says, is the server's
 * fault too; only an entity whose very first bytes do not begin a stream is a bad request.
 */
final class ContentCoding {

    /** The name of the coding that deflates an entity. */
    static final String GZIP = "gzip";

    /** The name of the coding that leaves an entity as it is. */
    static final String IDENTITY = "identity";

    /** The smallest entity that is written back compressed. */
    static final int MINIMUM_COMPRESSED_SIZE = 256;

    private static final Logger LOGGER = getLogger(ContentCoding.class);

    private static final int HEADER = 10;
    private static final int MAGIC = 2;
    private static final int TRAILER = 8;
    private static final int FEXTRA = 4;
    private static final int FNAME = 8;
    private static final int FCOMMENT = 16;
    private static final int FHCRC = 2;
    private static final int TRUNCATED = -1;
    private static final int MALFORMED = -2;

    /**
     * The media types an answer is never deflated in.
     *
     * <p>These are the forms the server knows about that already carry their own compression. A
     * form it does not know about is deflated whatever it looks like, which is why one picture is
     * deflated and another is not.
     */
    private static final Set<String> COMPRESSED = Set.of(
            "image/png", "image/jpeg", "image/gif", "image/bmp", "image/tiff", "image/x-icon",
            "image/ief", "image/x-cmu-raster", "image/x-portable-anymap", "image/x-portable-bitmap",
            "image/x-portable-graymap", "image/x-portable-pixmap", "image/x-rgb", "image/x-xbitmap",
            "image/x-xpixmap", "image/x-xwindowdump",
            "audio/basic", "audio/midi", "audio/mpeg", "audio/x-aiff", "audio/x-pn-realaudio",
            "audio/x-wav",
            "video/mp4", "video/mpeg", "video/quicktime", "video/x-msvideo", "video/x-sgi-movie",
            "application/compress", "application/zip", "application/gzip", "application/bzip2",
            "application/brotli", "application/x-xz", "application/x-rar-compressed");

    /**
     * Read a request entity, undoing the coding the client wrote it in.
     *
     * @param entity the entity as it arrived
     * @param coding the value of the request's {@code Content-Encoding} header, which may be null
     * @return the entity
     */
    static byte[] decode(final byte[] entity, final String coding) {
        if (!deflated(coding)) {
            return entity;
        }
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        int at = 0;
        int members = 0;
        while (at < entity.length) {
            if (entity.length - at < MAGIC) {
                // One byte on its own says nothing about whether another stream follows it.
                LOGGER.debug("A byte follows a deflate stream");
                return out.toByteArray();
            }
            if ((entity[at] & 0xff) != 0x1f || (entity[at + 1] & 0xff) != 0x8b) {
                if (members > 0) {
                    // Bytes follow a whole stream: they are not an entity, and not the client's fault.
                    throw serverFault("Bytes that are not a deflate stream follow one");
                }
                throw TrellisHttpException.badRequest("Entity is not a deflate stream");
            }
            final int header = readHeader(entity, at);
            if (header == TRUNCATED) {
                // A stream that stops in the middle carries whatever it managed to say.
                LOGGER.debug("A deflate stream ended before its header was complete");
                return out.toByteArray();
            }
            if (header == MALFORMED) {
                // The bytes say they are a stream and then say it in a way the server does not read.
                throw serverFault("A deflate stream names a method the server does not read");
            }
            final int started = out.size();
            final Inflater inflater = new Inflater(true);
            inflater.setInput(entity, header, entity.length - header);
            final byte[] buffer = new byte[8192];
            try {
                while (!inflater.finished()) {
                    final int written = inflater.inflate(buffer);
                    if (written == 0) {
                        if (inflater.needsInput() || inflater.needsDictionary()) {
                            // The stream stops in the middle of its deflated data.
                            LOGGER.debug("A deflate stream ended early");
                            return out.toByteArray();
                        }
                        break;
                    }
                    out.write(buffer, 0, written);
                }
            } catch (final DataFormatException ex) {
                // The bytes are a stream whose deflated data does not hold together, which is a
                // fault in what arrived rather than a request the server can refuse.
                throw serverFault("A deflate stream does not hold together: " + ex.getMessage());
            } finally {
                at = header + (int) inflater.getBytesRead();
                inflater.end();
            }
            // Past the deflated data lies the eight-byte trailer that closes the member: the
            // check of what it says, and the length of what the member held.
            if (at + TRAILER > entity.length) {
                LOGGER.debug("A deflate stream ended before its trailer was complete");
                return out.toByteArray();
            }
            if (readInt(entity, at + 4) != ((out.size() - started) & 0xffffffffL)) {
                // The member says how much it held, and it did not hold that much.
                throw serverFault("A deflate stream does not hold what its trailer says");
            }
            at += TRAILER;
            members++;
        }
        return out.toByteArray();
    }

    /**
     * Whether what the client named the entity's coding as says it is deflated.
     *
     * <p>A client may name more than one coding, and the entity is in the last of them: an entity
     * said to be deflated and then left as it is was left as it is.
     *
     * @param coding the value of the request's {@code Content-Encoding} header, which may be null
     * @return true when the entity is deflated
     */
    private static boolean deflated(final String coding) {
        if (coding == null) {
            return false;
        }
        final String[] names = coding.split(",", -1);
        return GZIP.equalsIgnoreCase(names[names.length - 1].trim());
    }

    /**
     * Read the header of a deflate member and answer where its deflated data starts.
     *
     * @param entity the entity
     * @param at where the member starts
     * @return the offset of the deflated data, {@code TRUNCATED} when the header is cut short, or
     *         {@code MALFORMED} when the bytes are not a header at all
     */
    private static int readHeader(final byte[] entity, final int at) {
        if (entity.length - at < HEADER) {
            return TRUNCATED;
        }
        if ((entity[at + 2] & 0xff) != 8) {
            return MALFORMED;
        }
        final int flags = entity[at + 3] & 0xff;
        int next = at + HEADER;
        if ((flags & FEXTRA) != 0) {
            if (next + 2 > entity.length) {
                return TRUNCATED;
            }
            next += 2 + ((entity[next] & 0xff) | ((entity[next + 1] & 0xff) << 8));
        }
        if ((flags & FNAME) != 0) {
            next = pastString(entity, next);
        }
        if ((flags & FCOMMENT) != 0) {
            next = pastString(entity, next);
        }
        if ((flags & FHCRC) != 0) {
            next += 2;
        }
        return next > entity.length ? TRUNCATED : next;
    }

    /**
     * What the server answers for an entity that is a deflate stream and not a whole one.
     */
    private static TrellisHttpException serverFault(final String message) {
        return new TrellisHttpException(message,
                TrellisResponse.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
    }

    /**
     * Read the four bytes a deflate trailer writes a number in, least significant first.
     */
    private static long readInt(final byte[] entity, final int at) {
        return (entity[at] & 0xffL) | ((entity[at + 1] & 0xffL) << 8)
            | ((entity[at + 2] & 0xffL) << 16) | ((entity[at + 3] & 0xffL) << 24);
    }

    private static int pastString(final byte[] entity, final int at) {
        int next = at;
        while (next < entity.length && entity[next] != 0) {
            next++;
        }
        return next + 1;
    }

    /**
     * Test whether an entity of this media type is worth deflating.
     *
     * <p>A form the server knows carries its own compression is written as it is: deflating it
     * again costs work and saves nothing. A form it does not know is deflated like any other.
     *
     * @param contentType the media type of the answer, which may be null
     * @return true when the answer may be deflated
     */
    static boolean worthEncoding(final String contentType) {
        if (contentType == null) {
            return true;
        }
        return !COMPRESSED.contains(contentType.split(";")[0].trim().toLowerCase());
    }

    /**
     * Deflate an entity.
     *
     * @param entity the entity
     * @return the deflated entity
     */
    static byte[] encode(final byte[] entity) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(entity);
        } catch (final IOException ex) {
            throw new IllegalStateException("Unable to deflate an entity", ex);
        }
        return out.toByteArray();
    }

    /**
     * Test whether a client will take a deflated entity.
     *
     * <p>The coding has to be named: a client that will take anything is not offered one, and a
     * coding the client gave a quality of zero is not one it will take.
     *
     * @param acceptEncoding the values of the request's {@code Accept-Encoding} header
     * @return true when the client named the deflate coding and did not refuse it
     */
    static boolean acceptsGzip(final List<String> acceptEncoding) {
        for (final String header : acceptEncoding) {
            for (final String single : header.split(",")) {
                final String[] parts = single.trim().split(";");
                if (!GZIP.equalsIgnoreCase(unquoted(parts[0].trim()))) {
                    continue;
                }
                String quality = null;
                int qualities = 0;
                for (int i = 1; i < parts.length; i++) {
                    final String parameter = parts[i].trim();
                    if (parameter.startsWith("q=")) {
                        quality = parameter.substring(2).trim();
                        qualities++;
                    }
                }
                // A coding whose quality is written twice has no quality a reader can read, so the
                // coding counts as one the client named and nothing refuses it.
                if (qualities == 1 && isZero(quality)) {
                    return false;
                }
                return true;
            }
        }
        return false;
    }

    /**
     * The name of a coding as it is read, with the quotation marks a client may write around it
     * taken off: a quoted name names the same coding as a bare one.
     *
     * @param name the name as it was written
     * @return the name
     */
    private static String unquoted(final String name) {
        if (name.length() > 1 && name.startsWith("\"") && name.endsWith("\"")) {
            return name.substring(1, name.length() - 1);
        }
        return name;
    }

    /**
     * Read the media types a client said it would take, for deciding whether to write it a page.
     *
     * @param accept the values of the request's {@code Accept} header
     * @return the media types, as they were written
     */
    static List<String> acceptedTypes(final List<String> accept) {
        final List<String> types = new ArrayList<>();
        for (final String header : accept) {
            for (final String single : header.split(",")) {
                if (!single.trim().isEmpty()) {
                    types.add(single.trim());
                }
            }
        }
        return types;
    }

    /**
     * Turn a string entity into the bytes that travel.
     *
     * @param entity the entity
     * @return the bytes
     */
    static byte[] bytes(final String entity) {
        return entity.getBytes(UTF_8);
    }

    private static boolean isZero(final String quality) {
        try {
            return Double.parseDouble(quality) == 0;
        } catch (final NumberFormatException ex) {
            return false;
        }
    }

    private ContentCoding() {
        // prevent instantiation
    }
}
