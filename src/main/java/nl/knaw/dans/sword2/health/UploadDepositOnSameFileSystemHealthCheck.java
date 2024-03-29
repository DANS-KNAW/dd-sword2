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
import nl.knaw.dans.sword2.core.service.FileService;

import java.util.List;

public class UploadDepositOnSameFileSystemHealthCheck extends HealthCheck {
    private final List<CollectionConfig> collectionConfigList;
    private final FileService fileService;

    public UploadDepositOnSameFileSystemHealthCheck(List<CollectionConfig> collectionConfigList, FileService fileService) {
        this.collectionConfigList = collectionConfigList;
        this.fileService = fileService;
    }

    @Override
    protected Result check() throws Exception {
        var result = Result.builder();
        var isValid = true;

        for (var collection : collectionConfigList) {
            var isSameFileStore = fileService.isSameFileSystem(collection.getUploads(), collection.getDeposits());

            if (!isSameFileStore) {
                result.withDetail(collection.getName(), "Upload and deposit path are on different file stores");
                isValid = false;
            }
        }

        if (isValid) {
            return result.healthy().build();
        }
        else {
            return result.unhealthy().withMessage("Some collections have their upload and deposit paths on different file stores").build();
        }
    }
}
