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

import nl.knaw.dans.sword2.api.error.Error;
import nl.knaw.dans.sword2.core.config.SwordError;

public interface ErrorResponseFactory {

    ErrorResponse buildSwordErrorResponse(SwordError errorCode);

    ErrorResponse buildSwordErrorResponse(SwordError errorCode, String errorMessage);

    class ErrorResponse {
        private Error error;
        private int code;

        public ErrorResponse(Error error, int code) {
            this.error = error;
            this.code = code;
        }

        public Error getError() {
            return error;
        }

        public void setError(Error error) {
            this.error = error;
        }

        public int getCode() {
            return code;
        }

        public void setCode(int code) {
            this.code = code;
        }

    }
}
