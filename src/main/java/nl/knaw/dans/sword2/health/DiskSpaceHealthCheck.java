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
package nl.knaw.dans.sword2.health;

import com.codahale.metrics.health.HealthCheck;
import nl.knaw.dans.sword2.config.CollectionConfig;
import nl.knaw.dans.sword2.core.exceptions.NotEnoughDiskSpaceException;
import nl.knaw.dans.sword2.core.service.FilesystemSpaceVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DiskSpaceHealthCheck extends HealthCheck {
    private static final Logger log = LoggerFactory.getLogger(DiskSpaceHealthCheck.class);

    private final List<CollectionConfig> collectionConfigList;
    private final FilesystemSpaceVerifier filesystemSpaceVerifier;

    public DiskSpaceHealthCheck(List<CollectionConfig> collectionConfigList, FilesystemSpaceVerifier filesystemSpaceVerifier) {
        this.collectionConfigList = collectionConfigList;
        this.filesystemSpaceVerifier = filesystemSpaceVerifier;
    }

    @Override
    protected Result check() {
        var errors = new ArrayList<CollectionConfig>();

        for (var collection : collectionConfigList) {
            try {
                // note that the deposits folder and the uploads folder should be on the same partition so only a single check is required
                // this is being checked in the UploadDepositIsOnSameFileSystemHealthCheck
                filesystemSpaceVerifier.assertDirHasEnoughDiskspaceMargin(collection.getUploads(), collection.getDiskSpaceMargin().toBytes());
            }
            catch (NotEnoughDiskSpaceException e) {
                errors.add(collection);
            }
            catch (IOException e) {
                log.error("Error checking disk space", e);
                errors.add(collection);
            }
        }

        if (errors.size() > 0) {
            var builder = Result.builder().withMessage("One or more partitions do not have enough disk space left");

            for (var error : errors) {
                builder.withDetail(error.getName(), error.toString());
            }

            return builder.unhealthy().build();
        }

        return Result.healthy();
    }
}
