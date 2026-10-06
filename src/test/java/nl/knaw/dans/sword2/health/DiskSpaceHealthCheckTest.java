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

import io.dropwizard.util.DataSize;
import nl.knaw.dans.sword2.config.CollectionConfig;
import nl.knaw.dans.sword2.core.DepositState;
import nl.knaw.dans.sword2.core.exceptions.NotEnoughDiskSpaceException;
import nl.knaw.dans.sword2.core.service.FilesystemSpaceVerifier;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DiskSpaceHealthCheckTest {

    @Test
    void checkReturnsValidIfNoExceptionIsThrown() {
        var collection1 = new CollectionConfig("name", "path", Path.of("uploads"), Path.of("deposits"), DataSize.bytes(100), Collections.emptyList(), List.of(DepositState.INVALID));
        var config = List.of(collection1);

        var filesystemSpaceVerifier = Mockito.mock(FilesystemSpaceVerifier.class);
        var result = new DiskSpaceHealthCheck(config, filesystemSpaceVerifier).check();

        assertTrue(result.isHealthy());
    }


    @Test
    void checkReturnsValidWithMultipleConfigs() throws IOException, NotEnoughDiskSpaceException {
        var collection1 = new CollectionConfig("name", "path", Path.of("uploads"), Path.of("deposits"), DataSize.bytes(100), Collections.emptyList(),List.of(DepositState.INVALID));
        var collection2 = new CollectionConfig("name2", "path2", Path.of("uploads2"), Path.of("deposits2"), DataSize.bytes(100), Collections.emptyList(),List.of(DepositState.INVALID));

        var config = List.of(collection1, collection2);

        var filesystemSpaceVerifier = Mockito.mock(FilesystemSpaceVerifier.class);
        var result = new DiskSpaceHealthCheck(config, filesystemSpaceVerifier).check();

        assertTrue(result.isHealthy());
    }

    @Test
    void checkReturnsInValidIfExceptionIsThrown() throws IOException, NotEnoughDiskSpaceException {
        var collection1 = new CollectionConfig("name", "path", Path.of("uploads"), Path.of("deposits"), DataSize.bytes(100), Collections.emptyList(),List.of(DepositState.INVALID));
        var config = List.of(collection1);

        var filesystemSpaceVerifier = Mockito.mock(FilesystemSpaceVerifier.class);
        Mockito.doThrow(NotEnoughDiskSpaceException.class).when(filesystemSpaceVerifier).assertDirHasEnoughDiskspaceMargin(Mockito.any(), Mockito.anyLong());

        var result = new DiskSpaceHealthCheck(config, filesystemSpaceVerifier).check();

        assertFalse(result.isHealthy());
    }

    @Test
    void checkReturnsInValidIfExceptionIsThrownButOnlyForOneCollection() throws IOException, NotEnoughDiskSpaceException {
        var collection1 = new CollectionConfig("name", "path", Path.of("uploads"), Path.of("deposits"), DataSize.bytes(100), Collections.emptyList(), List.of(DepositState.INVALID));
        var collection2 = new CollectionConfig("name2", "path2", Path.of("uploads2"), Path.of("deposits2"), DataSize.bytes(100), Collections.emptyList(),List.of(DepositState.INVALID));

        var config = List.of(collection1, collection2);

        var filesystemSpaceVerifier = Mockito.mock(FilesystemSpaceVerifier.class);
        Mockito.doThrow(NotEnoughDiskSpaceException.class).when(filesystemSpaceVerifier).assertDirHasEnoughDiskspaceMargin(Mockito.eq(Path.of("uploads2")), Mockito.anyLong());

        var result = new DiskSpaceHealthCheck(config, filesystemSpaceVerifier).check();

        assertFalse(result.isHealthy());
    }
}
