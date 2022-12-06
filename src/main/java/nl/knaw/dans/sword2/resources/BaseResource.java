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
package nl.knaw.dans.sword2.resources;

import nl.knaw.dans.sword2.core.config.SwordError;
import nl.knaw.dans.sword2.core.config.UriRegistry;
import nl.knaw.dans.sword2.core.exceptions.InvalidHeaderException;
import nl.knaw.dans.sword2.core.service.ErrorResponseFactory;
import org.apache.commons.fileupload.ParameterParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

public class BaseResource {
    private static final Logger log = LoggerFactory.getLogger(BaseResource.class);

    private final ErrorResponseFactory errorResponseFactory;
    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z");

    public BaseResource(ErrorResponseFactory errorResponseFactory) {
        this.errorResponseFactory = errorResponseFactory;
    }

    Response buildSwordErrorResponse(SwordError errorCode) {
        return buildSwordErrorResponse(errorCode, null);
    }

    Response buildSwordErrorResponse(SwordError errorCode, String errorMessage) {
        var errorResponse = errorResponseFactory.buildSwordErrorResponse(errorCode, errorMessage);

        return Response.status(errorResponse.getCode())
            .header("Content-Type", "text/xml")
            .entity(errorResponse.getError())
            .build();
    }

    String formatDateTime(OffsetDateTime dateTime) {
        return dateTime.format(dateTimeFormatter);
    }

    String getParameterValueFromContentDisposition(String contentDisposition, String key) {
        if (contentDisposition == null || key == null) {
            return null;
        }

        var parameterParser = new ParameterParser();
        var parameters = parameterParser.parse(contentDisposition, ';');

        return parameters.get(key);
    }

    String getPackaging(String header) {
        if (header == null) {
            return UriRegistry.PACKAGE_BINARY;
        }

        return header;
    }

    long getContentLength(String header) {
        try {
            if (header != null) {
                return Long.parseLong(header);
            }
        }
        catch (NumberFormatException e) {
            log.warn("Invalid content-length header: {}", header);
        }

        return -1L;
    }

    boolean getInProgress(String header) throws InvalidHeaderException {
        if (header == null) {
            return false;
        }

        if ("true".equals(header)) {
            return true;
        }
        else if ("false".equals(header)) {
            return false;
        }

        throw new InvalidHeaderException("In-Progress header must be either 'true' or 'false'");
    }

    MediaType getContentType(String contentType) {
        if (contentType == null) {
            return MediaType.APPLICATION_OCTET_STREAM_TYPE;
        }

        return MediaType.valueOf(contentType);
    }
}
