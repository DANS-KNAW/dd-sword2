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
package nl.knaw.dans.sword2.core.service;

import nl.knaw.dans.sword2.core.config.SwordError;
import nl.knaw.dans.sword2.api.error.Error;
import nl.knaw.dans.sword2.api.error.Generator;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

public class ErrorResponseFactoryImpl implements ErrorResponseFactory {
    private final DateTimeFormatter errorDateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
    @Override
    public ErrorResponse buildSwordErrorResponse(SwordError errorCode) {
        return buildSwordErrorResponse(errorCode, null);
    }

    @Override
    public ErrorResponse buildSwordErrorResponse(SwordError errorCode, String errorMessage) {
        var errorDocument = new Error();
        errorDocument.setTitle("ERROR");
        errorDocument.setTreatment("Processing failed");
        errorDocument.setGenerator(new Generator(URI.create("http://www.swordapp.org/"), "2.0"));
        errorDocument.setSummary(errorMessage == null ? errorCode.getSummaryText() : errorMessage);
        errorDocument.setUpdated(OffsetDateTime.now().format(errorDateTimeFormatter));

        return new ErrorResponse(errorDocument, errorCode.getStatusCode());
    }
}
