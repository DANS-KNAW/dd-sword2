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

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;

@XmlEnum
public enum SwordError {
    @XmlEnumValue("http://purl.org/net/sword/error/ErrorBadRequest")
    ERROR_BAD_REQUEST("Some parameters sent with the POST were not understood",
        400),
    @XmlEnumValue("http://purl.org/net/sword/error/TargetOwnerUnknown")
    ERROR_TARGET_OWNER_UNKNOWN("Used in mediated deposit when the server does not know the identity of the On-Behalf-Of user",
        403),
    @XmlEnumValue("http://purl.org/net/sword/error/MethodNotAllowed")
    ERROR_METHOD_NOT_ALLOWED("Request method is not allowed",
        405),
    @XmlEnumValue("http://purl.org/net/sword/error/ErrorContent")
    ERROR_CONTENT_NOT_ACCEPTABLE("The supplied format is not the same as that identified in the packaging header that supported by the server",
        406),
    @XmlEnumValue("http://purl.org/net/sword/error/ErrorContent")
    ERROR_CONTENT_UNSUPPORTED_MEDIA_TYPE("The supplied media type is not the same as that supported by the server",
        415),
    @XmlEnumValue("http://purl.org/net/sword/error/ErrorChecksumMismatch")
    ERROR_CHECKSUM_MISMATCH("Checksum sent does not match the calculated checksum",
        412),

    @XmlEnumValue("http://purl.org/net/sword/error/MediationNotAllowed")
    ERROR_MEDIATION_NOT_ALLOWED("Mediation is not supported by the server",
        412),

    @XmlEnumValue("http://purl.org/net/sword/error/MaxUploadSizeExceeded")
    ERROR_MAX_UPLOAD_SIZE_EXCEEDED("The supplied data size exceeds the server's maximum upload size limit",
        413);

    private final String summaryText;
    private final int statusCode;
    SwordError(String summaryText, int statusCode){
        this.summaryText = summaryText;
        this.statusCode = statusCode;
    }

    public String getSummaryText() {
        return summaryText;
    }
    public int getStatusCode() {
        return statusCode;
    }
}
