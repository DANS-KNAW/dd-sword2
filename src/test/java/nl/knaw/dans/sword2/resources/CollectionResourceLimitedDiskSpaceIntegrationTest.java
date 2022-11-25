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

import ch.qos.logback.classic.LoggerContext;
import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import nl.knaw.dans.sword2.DdSword2Application;
import nl.knaw.dans.sword2.DdSword2Configuration;
import nl.knaw.dans.sword2.TestFixture;
import nl.knaw.dans.sword2.TestFixtureExt;
import nl.knaw.dans.sword2.core.service.FileServiceImpl;
import org.apache.commons.io.FileUtils;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(DropwizardExtensionsSupport.class)
class CollectionResourceLimitedDiskSpaceIntegrationTest extends TestFixtureExt {
    public CollectionResourceLimitedDiskSpaceIntegrationTest() {
        super("test-etc/config-bigmargin.yml");
    }

    @BeforeEach
    void startUp() throws IOException {
        new FileServiceImpl().ensureDirectoriesExist(testDir.resolve("1"));
    }

    @AfterEach
    void tearDown() {
        ((LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory()).stop();
    }

    @Test
    void testFileIsTooBig() throws IOException {
        var path = getClass().getResource("/zips/audiences.zip");
        assert path != null;

        var result = buildRequest("/collection/1")
            .header("content-type", "application/zip")
            .header("content-md5", "bc27e20467a773501a4ae37fb85a9c3f")
            .header("content-disposition", "attachment; filename=bag.zip")
            .header("in-progress", "true")
            .post(Entity.entity(path.openStream(), MediaType.valueOf("application/zip")));

        assertEquals(503, result.getStatus());

    }

    Invocation.Builder buildRequest(String path) {
        var url = String.format("http://localhost:%s%s", EXT.getLocalPort(), path);

        return RequestClientBuilder.buildClient()
            .target(url)
            .register(MultiPartFeature.class)
            .request()
            .header("authorization", "Basic dXNlcjAwMTp1c2VyMDAx");
    }
}
