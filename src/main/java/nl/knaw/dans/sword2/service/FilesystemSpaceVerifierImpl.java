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
package nl.knaw.dans.sword2.service;

import nl.knaw.dans.sword2.exceptions.NotEnoughDiskSpaceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

public class FilesystemSpaceVerifierImpl implements FilesystemSpaceVerifier {
    private static final Logger log = LoggerFactory.getLogger(DepositHandlerImpl.class);
    private final FileService fileService;

    public FilesystemSpaceVerifierImpl(FileService fileService) {
        this.fileService = fileService;
    }

    @Override
    public void assertDirHasEnoughDiskspaceMarginForFile(Path destination, long margin, long contentLength) throws IOException, NotEnoughDiskSpaceException {
        if (contentLength > -1) {
            var availableSpace = fileService.getAvailableDiskSpace(destination);
            log.debug("Free space  = {}", availableSpace);
            log.debug("File length = {}", contentLength);
            log.debug("Margin      = {}", margin);
            log.debug("Extra space = {}", availableSpace - contentLength - margin);

            if (availableSpace - contentLength < margin) {
                throw new NotEnoughDiskSpaceException("Not enough space available");
            }
        }
        else {
            log.debug("Content-length is -1, not checking for disk space margin");
        }
    }
}
