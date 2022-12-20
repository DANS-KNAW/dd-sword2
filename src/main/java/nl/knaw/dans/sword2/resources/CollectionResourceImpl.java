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

import nl.knaw.dans.sword2.api.error.Generator;
import nl.knaw.dans.sword2.api.statement.Feed;
import nl.knaw.dans.sword2.api.statement.FeedEntry;
import nl.knaw.dans.sword2.core.auth.Depositor;
import nl.knaw.dans.sword2.core.config.SwordError;
import nl.knaw.dans.sword2.core.config.UriRegistry;
import nl.knaw.dans.sword2.core.exceptions.CollectionNotFoundException;
import nl.knaw.dans.sword2.core.exceptions.HashMismatchException;
import nl.knaw.dans.sword2.core.exceptions.InvalidDepositException;
import nl.knaw.dans.sword2.core.exceptions.InvalidHeaderException;
import nl.knaw.dans.sword2.core.exceptions.NotEnoughDiskSpaceException;
import nl.knaw.dans.sword2.core.service.DepositHandler;
import nl.knaw.dans.sword2.core.service.DepositReceiptFactory;
import nl.knaw.dans.sword2.core.service.ErrorResponseFactory;
import org.glassfish.jersey.media.multipart.MultiPart;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.List;

public class CollectionResourceImpl extends BaseResource implements CollectionResource {

    private final DepositHandler depositHandler;
    private final DepositReceiptFactory depositReceiptFactory;

    public CollectionResourceImpl(DepositHandler depositHandler, DepositReceiptFactory depositReceiptFactory, ErrorResponseFactory errorResponseFactory) {
        super(errorResponseFactory);
        this.depositHandler = depositHandler;
        this.depositReceiptFactory = depositReceiptFactory;
    }

    @Override
    public Feed getCollection(HttpHeaders headers, Depositor depositor) {
        var feed = new Feed();
        feed.setGenerator(new Generator(URI.create("http://www.swordapp.org/"), "2.0"));
        feed.setEntries(List.of(new FeedEntry()));

        return feed;
    }

    @Override
    public Response depositMultipart(MultiPart multiPart, String collectionId, HttpHeaders headers, Depositor depositor) {
        return buildSwordErrorResponse(SwordError.ERROR_METHOD_NOT_ALLOWED);
    }

    @Override
    public Response depositAtom(String collectionId, HttpHeaders headers, Depositor depositor) {
        return buildSwordErrorResponse(SwordError.ERROR_METHOD_NOT_ALLOWED);
    }

    @Override
    public Response depositAnything(InputStream inputStream, String collectionId, HttpHeaders headers, Depositor depositor) {

        try {
            var contentType = getContentType(headers.getHeaderString("content-type"));
            var inProgress = getInProgress(headers.getHeaderString("in-progress"));
            var contentDisposition = headers.getHeaderString("content-disposition");
            var md5 = headers.getHeaderString("content-md5");
            var packaging = getPackaging(headers.getHeaderString("packaging"));

            var filename = getParameterValueFromContentDisposition(contentDisposition, "filename");

            if (filename == null) {
                throw new InvalidHeaderException("Content-Disposition header is missing or has an invalid 'filename' parameter");
            }

            var fileSize = getContentLength(headers.getHeaderString("content-length"));

            var deposit = depositHandler.createDepositWithPayload(collectionId, depositor, inProgress, contentType, md5, packaging, filename, fileSize, inputStream);

            var entry = depositReceiptFactory.createDepositReceipt(deposit);

            return Response.status(Response.Status.CREATED)
                .header("Last-Modified", formatDateTime(deposit.getCreated()))
                .header("Location", depositReceiptFactory.getDepositLocation(deposit))
                .entity(entry)
                .build();

        }
        catch (IOException | InvalidHeaderException | InvalidDepositException e) {
            return buildSwordErrorResponse(SwordError.ERROR_BAD_REQUEST, e.getMessage());
        }
        catch (CollectionNotFoundException e) {
            return buildSwordErrorResponse(SwordError.ERROR_METHOD_NOT_ALLOWED, e.getMessage());
        }
        catch (HashMismatchException e) {
            return buildSwordErrorResponse(SwordError.ERROR_CHECKSUM_MISMATCH);
        }
        catch (NotEnoughDiskSpaceException e) {
            throw new WebApplicationException(503);
        }
    }
}
