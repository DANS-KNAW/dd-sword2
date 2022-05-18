/*
 * Copyright (C) 2022 DANS - Data Archiving and Networked Services (info@dans.knaw.nl)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.knaw.dans.sword2.resource;

import nl.knaw.dans.sword2.core.exceptions.InvalidHeaderException;
import nl.knaw.dans.sword2.core.service.ErrorResponseFactory;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.ws.rs.core.MediaType;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseHandlerTest {
    @Test
    void testDateFormat() {
        var date = OffsetDateTime.of(2022, 5, 18, 17, 18, 30, 40, ZoneOffset.UTC);
        var errorResponseFactory = Mockito.mock(ErrorResponseFactory.class);
        var handler = new BaseHandler(errorResponseFactory);
        assertEquals("Wed, 18 May 2022 17:18:30 +0000", new BaseHandler(errorResponseFactory).formatDateTime(date));
    }

    @Test
    void testEmptyContentDisposition() {
        var errorResponseFactory = Mockito.mock(ErrorResponseFactory.class);
        var handler = new BaseHandler(errorResponseFactory);
        assertNull(handler.getFilenameFromContentDisposition("attachment; filename=test.zip", "wrong key"));
        assertNull(handler.getFilenameFromContentDisposition("attachment", "does not matter"));
        assertNull(handler.getFilenameFromContentDisposition("", "does not matter"));
        assertNull(handler.getFilenameFromContentDisposition(null, "does not matter"));
    }

    @Test
    void testValidContentDisposition() {
        var errorResponseFactory = Mockito.mock(ErrorResponseFactory.class);
        var handler = new BaseHandler(errorResponseFactory);
        assertEquals("test.zip", handler.getFilenameFromContentDisposition("attachment; filename=test.zip", "filename"));
        assertEquals("test.zip", handler.getFilenameFromContentDisposition("attachment;     filename=test.zip", "filename"));
        assertEquals("test.zip", handler.getFilenameFromContentDisposition("attachment; name=test.zip", "name"));
    }

    @Test
    void testContentLengthHeader() {
        var errorResponseFactory = Mockito.mock(ErrorResponseFactory.class);
        var handler = new BaseHandler(errorResponseFactory);
        assertEquals(123, handler.getContentLength("123"));
        assertEquals(-1, handler.getContentLength("letter3"));
        assertEquals(-1, handler.getContentLength("123suffix"));
        assertEquals(-1, handler.getContentLength("prefix123"));
        assertEquals(-1, handler.getContentLength(null));
    }


    @Test
    void testPackaging() {
        var errorResponseFactory = Mockito.mock(ErrorResponseFactory.class);
        var handler = new BaseHandler(errorResponseFactory);

        assertEquals("http://purl.org/net/sword/package/Binary", handler.getPackaging(null));
        assertEquals("input", handler.getPackaging("input"));
    }

    @Test
    void testMediaType() {
        var errorResponseFactory = Mockito.mock(ErrorResponseFactory.class);
        var handler = new BaseHandler(errorResponseFactory);

        assertEquals(MediaType.APPLICATION_OCTET_STREAM_TYPE, handler.getContentType(null));
        assertEquals(MediaType.APPLICATION_XML, handler.getPackaging("application/xml"));
    }

    @Test
    void testInProgress() throws InvalidHeaderException {
        var errorResponseFactory = Mockito.mock(ErrorResponseFactory.class);
        var handler = new BaseHandler(errorResponseFactory);

        assertTrue(handler.getInProgress("true"));
        assertFalse(handler.getInProgress("false"));
        assertFalse(handler.getInProgress(null));
        assertThrows(InvalidHeaderException.class, () -> handler.getInProgress("not-true-or-false"));
    }
}
