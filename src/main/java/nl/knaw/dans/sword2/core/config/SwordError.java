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

package nl.knaw.dans.sword2.core.config;

public enum SwordError {
    ERROR_BAD_REQUEST("http://purl.org/net/sword/error/ErrorBadRequest",
        "Some parameters sent with the POST were not understood",
        400),
    ERROR_TARGET_OWNER_UNKNOWN("http://purl.org/net/sword/error/TargetOwnerUnknown",
        "Used in mediated deposit when the server does not know the identity of the On-Behalf-Of user",
        403),
    ERROR_METHOD_NOT_ALLOWED("http://purl.org/net/sword/error/MethodNotAllowed",
        "Request method is not allowed",
        405),
    ERROR_CONTENT_NOT_ACCEPTABLE("http://purl.org/net/sword/error/ErrorContent",
        "The supplied format is not the same as that identified in the packaging header that supported by the server",
        406),
    ERROR_CONTENT_UNSUPPORTED_MEDIA_TYPE("http://purl.org/net/sword/error/ErrorContent",
        "The supplied media type is not the same as that supported by the server",
        415),
    ERROR_CHECKSUM_MISMATCH("http://purl.org/net/sword/error/ErrorChecksumMismatch",
        "Checksum sent does not match the calculated checksum",
        412),

    ERROR_MEDIATION_NOT_ALLOWED("http://purl.org/net/sword/error/MediationNotAllowed",
        "Mediation is not supported by the server",
        412),

    ERROR_MAX_UPLOAD_SIZE_EXCEEDED("http://purl.org/net/sword/error/MaxUploadSizeExceeded",
        "The supplied data size exceeds the server's maximum upload size limit",
        413);

    private final String iri;
    private final String summaryText;
    private final int statusCode;
    SwordError(String iri, String summaryText, int statusCode){
        this.iri = iri;
        this.summaryText = summaryText;
        this.statusCode = statusCode;
    }

    public String getIri() {
        return iri;
    }
    public String getSummaryText() {
        return summaryText;
    }
    public int getStatusCode() {
        return statusCode;
    }
}
