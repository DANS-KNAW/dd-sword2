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
import nl.knaw.dans.sword2.core.auth.Depositor;
import nl.knaw.dans.sword2.core.exceptions.CollectionNotFoundException;
import nl.knaw.dans.sword2.core.exceptions.DepositNotFoundException;
import nl.knaw.dans.sword2.core.exceptions.DepositReadOnlyException;
import nl.knaw.dans.sword2.core.exceptions.HashMismatchException;
import nl.knaw.dans.sword2.core.exceptions.InvalidDepositException;
import nl.knaw.dans.sword2.core.exceptions.InvalidHeaderException;
import nl.knaw.dans.sword2.core.exceptions.NotEnoughDiskSpaceException;
import nl.knaw.dans.sword2.core.service.DepositHandler;
import nl.knaw.dans.sword2.core.service.DepositReceiptFactory;
import nl.knaw.dans.sword2.core.service.ErrorResponseFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

public class ContainerResourceImpl extends BaseResource implements ContainerResource {
    private static final Logger log = LoggerFactory.getLogger(ContainerResourceImpl.class);

    private final DepositReceiptFactory depositReceiptFactory;
    private final DepositHandler depositHandler;
    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z");

    public ContainerResourceImpl(DepositReceiptFactory depositReceiptFactory, DepositHandler depositHandler, ErrorResponseFactory errorResponseFactory) {
        super(errorResponseFactory);
        this.depositReceiptFactory = depositReceiptFactory;
        this.depositHandler = depositHandler;
    }

    @Override
    public Response getDepositReceipt(String depositId, HttpHeaders headers, Depositor depositor) {
        log.info("Received getDepositReceipt request for deposit with ID {} and user {}", depositId, depositor.getName());

        try {
            var deposit = depositHandler.getDeposit(depositId, depositor);
            var entry = depositReceiptFactory.createDepositReceipt(deposit);
            var location = depositReceiptFactory.getDepositLocation(deposit);

            return Response.status(Response.Status.OK)
                .header("Location", location)
                .header("Content-Type", "application/atom+xml;type=entry")
                .header("Last-Modified", OffsetDateTime.now().format(dateTimeFormatter))
                .entity(entry)
                .build();

        }
        catch (DepositNotFoundException e) {
            log.error("Deposit with id {} could not be found", depositId, e);
            throw new WebApplicationException(404);
        }
        catch (InvalidDepositException e) {
            log.error("Deposit with id {} is invalid", depositId, e);
            throw new WebApplicationException(500);
        }
    }

    @Override
    public Response getDepositReceiptHead(String depositId, HttpHeaders headers, Depositor depositor) {
        log.info("Received getDepositReceiptHead request for deposit with ID {} and user {}", depositId, depositor.getName());

        try {
            var deposit = depositHandler.getDeposit(depositId, depositor);
            var location = depositReceiptFactory.getDepositLocation(deposit);

            return Response.status(Response.Status.OK)
                .header("Location", location)
                .header("Content-Type", "application/atom+xml;type=entry")
                .header("Last-Modified", OffsetDateTime.now().format(dateTimeFormatter))
                .build();

        }
        catch (DepositNotFoundException e) {
            log.error("Deposit with id {} could not be found", depositId, e);
            throw new WebApplicationException(403);
        }
        catch (InvalidDepositException e) {
            log.error("Deposit with id {} is invalid", depositId, e);
            throw new WebApplicationException(500);
        }
    }

    @Override
    public Response addMedia(InputStream inputStream, String depositId, HttpHeaders headers, Depositor depositor) {
        log.info("Received getDepositReceiptHead request for deposit with ID {} and user {}", depositId, depositor.getName());

        try {
            var contentType = getContentType(headers.getHeaderString("content-type"));
            var inProgress = getInProgress(headers.getHeaderString("in-progress"));

            var contentDisposition = headers.getHeaderString("content-disposition");
            var md5 = headers.getHeaderString("content-md5");
            var packaging = getPackaging(headers.getHeaderString("packaging"));

            var filename = getParameterValueFromContentDisposition(contentDisposition, "filename");
            var fileSize = getContentLength(headers.getHeaderString("content-length"));

            var deposit = depositHandler.addPayloadToDeposit(depositId, depositor, inProgress, contentType, md5, packaging, filename, fileSize, inputStream);
            var entry = depositReceiptFactory.createDepositReceipt(deposit);
            var location = depositReceiptFactory.getDepositLocation(deposit);

            return Response.status(Response.Status.OK)
                .header("Location", location)
                .header("Content-Type", "application/atom+xml;type=entry")
                .header("Last-Modified", OffsetDateTime.now().format(dateTimeFormatter))
                .entity(entry)
                .build();
        }
        catch (IOException e) {
            log.error("An IOException occurred while processing the request for deposit with ID {}", depositId, e);
            return buildSwordErrorResponse(SwordError.ERROR_BAD_REQUEST, e.getMessage());
        }
        catch (InvalidHeaderException e) {
            log.error("An invalid header was received while processing the request for deposit with ID {}", depositId, e);
            return buildSwordErrorResponse(SwordError.ERROR_BAD_REQUEST, e.getMessage());
        }
        catch (CollectionNotFoundException | DepositReadOnlyException e) {
            log.error("The deposit with ID {} is read-only", depositId, e);
            return buildSwordErrorResponse(SwordError.ERROR_METHOD_NOT_ALLOWED, e.getMessage());
        }
        catch (HashMismatchException e) {
            log.error("The content has a different checksum than the one provided for deposit with ID {}", depositId, e);
            return buildSwordErrorResponse(SwordError.ERROR_CHECKSUM_MISMATCH);
        }
        catch (NotEnoughDiskSpaceException e) {
            log.error("The content could not be stored due to insufficient disk space, for deposit with ID {}", depositId, e);
            throw new WebApplicationException(e, 503);
        }
        catch (DepositNotFoundException e) {
            log.error("Deposit with ID {} could not be found", depositId, e);
            throw new WebApplicationException(e, 404);
        }
        catch (InvalidDepositException e) {
            log.error("The deposit with ID {} is invalid", depositId, e);
            throw new WebApplicationException(e, 500);
        }
    }

}
