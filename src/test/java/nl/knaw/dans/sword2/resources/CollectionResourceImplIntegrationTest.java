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
import io.dropwizard.configuration.ConfigurationSourceProvider;
import io.dropwizard.configuration.FileConfigurationSourceProvider;
import io.dropwizard.configuration.ResourceConfigurationSourceProvider;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.testing.ConfigOverride;
import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import nl.knaw.dans.sword2.DdSword2Application;
import nl.knaw.dans.sword2.DdSword2Configuration;
import nl.knaw.dans.sword2.TestFixture;
import nl.knaw.dans.sword2.TestFixtureExt;
import nl.knaw.dans.sword2.api.entry.Entry;
import nl.knaw.dans.sword2.api.error.Error;
import nl.knaw.dans.sword2.api.statement.Feed;
import nl.knaw.dans.sword2.core.service.FileServiceImpl;
import org.apache.commons.configuration2.FileBasedConfiguration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.text.StringSubstitutor;
import org.glassfish.jersey.media.multipart.BodyPart;
import org.glassfish.jersey.media.multipart.MultiPart;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.core.MediaType;
import javax.xml.bind.DatatypeConverter;
import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(DropwizardExtensionsSupport.class)
class CollectionResourceImplIntegrationTest extends TestFixtureExt {

    public CollectionResourceImplIntegrationTest() {
        super("test-etc/config-regular.yml");
    }


    @BeforeEach
    void startUp() throws IOException {
        FileUtils.deleteDirectory(testDir.toFile());
        new FileServiceImpl().ensureDirectoriesExist(testDir.resolve("1"));
    }

    @AfterEach
    void tearDown() {
        ((LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory()).stop();
    }

    Builder buildRequest(String path) {
        var url = String.format("http://localhost:%s%s", EXT.getLocalPort(), path);

        return RequestClientBuilder.buildClient()
                .target(url)
                .register(MultiPartFeature.class)
                .request()
                .header("authorization", "Basic dXNlcjAwMTp1c2VyMDAx");
    }

    @Test
    void testZipDepositDraftState() throws IOException, JAXBException, ConfigurationException {
        var path = getClass().getResource("/zips/audiences.zip");

        assert path != null;

        var result = buildRequest("/collection/1")
                .header("content-type", "application/zip")
                .header("content-md5", "bc27e20467a773501a4ae37fb85a9c3f")
                .header("content-disposition", "attachment; filename=bag.zip")
                .header("in-progress", "true")
                .post(Entity.entity(path.openStream(), MediaType.valueOf("application/zip")));

        assertEquals(201, result.getStatus());

        var receipt = result.readEntity(Entry.class);
        var parts = receipt.getId()
                .split("/");
        var id = parts[parts.length - 1];

        var firstPath = testDir.resolve("1/uploads/").resolve(id);

        assertTrue(Files.exists(firstPath.resolve("deposit.properties")));
        assertTrue(Files.exists(firstPath.resolve("bag.zip")));

        var config = getProperties(firstPath);

        assertNotNull(config.getString("bag-store.bag-id"));
        assertNotNull(config.getString("dataverse.bag-id"));
        assertNotNull(config.getString("creation.timestamp"));
        assertEquals("SWORD2", config.getString("deposit.origin"));
        assertEquals("DRAFT", config.getString("state.label"));
        assertEquals("user001", config.getString("depositor.userId"));
        assertEquals("Deposit is open for additional data", config.getString("state.description"));

        var statusResult = buildRequest("/statement/" + id)
                .get();

        var feed = statusResult.readEntity(Feed.class);
        assertEquals("http://localhost:20320/statement/" + id, feed.getId());
    }

    @Test
    void testDepositReceiptFromContainerEndpoint() throws IOException, JAXBException, ConfigurationException {
        var path = getClass().getResource("/zips/audiences.zip");

        assert path != null;

        var result = buildRequest("/collection/1")
                .header("content-type", "application/zip")
                .header("content-md5", "bc27e20467a773501a4ae37fb85a9c3f")
                .header("content-disposition", "attachment; filename=bag.zip")
                .header("in-progress", "true")
                .post(Entity.entity(path.openStream(), MediaType.valueOf("application/zip")));

        assertEquals(201, result.getStatus());

        var receipt = result.readEntity(Entry.class);
        var parts = receipt.getId()
                .split("/");
        var id = parts[parts.length - 1];

        var statusResult = buildRequest("/container/" + id)
                .get();

        var feed = statusResult.readEntity(Entry.class);
        assertEquals("http://localhost:20320/container/" + id, feed.getId());
    }

    @Test
    void testDepositReceiptFromContainerEndpointHead() throws IOException {
        var path = getClass().getResource("/zips/audiences.zip");

        assert path != null;

        var result = buildRequest("/collection/1")
                .header("content-type", "application/zip")
                .header("content-md5", "bc27e20467a773501a4ae37fb85a9c3f")
                .header("content-disposition", "attachment; filename=bag.zip")
                .header("in-progress", "true")
                .post(Entity.entity(path.openStream(), MediaType.valueOf("application/zip")));

        assertEquals(201, result.getStatus());

        var receipt = result.readEntity(Entry.class);
        var parts = receipt.getId()
                .split("/");
        var id = parts[parts.length - 1];

        var statusResult = buildRequest("/container/" + id)
                .head();

        assertEquals(200, statusResult.getStatus());
        assertEquals("http://localhost:20320/container/" + id, statusResult.getHeaderString("location"));
    }

    @Test
    void testDepositReceiptFromContainerEndpointNotFound() {
        var statusResult = buildRequest("/container/" + "random_id")
                .get();

        assertEquals(404, statusResult.getStatus());
    }

    @Test
    void testZipInParts() throws
            IOException, ConfigurationException, NoSuchAlgorithmException, InterruptedException {
        var path = getClass().getResource("/zips/audiences.zip");
        assert path != null;

        var bytes = path.openStream().readAllBytes();
        var bagSize = bytes.length / 3;
        var firstPart = Arrays.copyOfRange(bytes, 0, bagSize);

        var checksum = md5Checksum(firstPart);
        var result = buildRequest("/collection/1")
                .header("content-type", "application/octet-stream")
                .header("content-md5", checksum)
                .header("content-disposition", "attachment; filename=bag.zip.1")
                .header("in-progress", "true")
                .post(Entity.entity(firstPart, MediaType.valueOf("application/octet-stream")));

        assertEquals(201, result.getStatus());

        var receipt = result.readEntity(Entry.class);
        var parts = receipt.getId().split("/");
        var id = parts[parts.length - 1];

        var secondPart = Arrays.copyOfRange(bytes, bagSize, bagSize * 2);
        var checksum2 = md5Checksum(secondPart);
        var result2 = buildRequest("/container/" + id)
                .header("content-type", "application/octet-stream")
                .header("content-md5", checksum2)
                .header("content-disposition", "attachment; filename=bag.zip.2")
                .header("in-progress", "true")
                .post(Entity.entity(secondPart, MediaType.valueOf("application/octet-stream")));
        assertEquals(200, result2.getStatus());

        var thirdPart = Arrays.copyOfRange(bytes, bagSize * 2, bytes.length);
        var checksum3 = md5Checksum(thirdPart);

        var result3 = buildRequest("/container/" + id)
                .header("content-type", "application/octet-stream")
                .header("content-md5", checksum3)
                .header("content-disposition", "attachment; filename=bag.zip.3")
                .header("in-progress", "false")
                .post(Entity.entity(thirdPart, MediaType.valueOf("application/zip")));
        assertEquals(200, result3.getStatus());

        var count = 0;
        var state = "";

        // waiting at most 5 seconds for the background thread to handle this
        while (count < 5) {
            var statement = buildRequest("/statement/" + id).get(Feed.class);
            state = statement.getCategory().getTerm();

            if (state.equals("SUBMITTED")) {
                break;
            }
            Thread.sleep(1000);
            count += 1;
        }

        assertEquals("SUBMITTED", state);

        var firstPath = testDir.resolve("1/deposits/").resolve(id);
        assertTrue(Files.exists(firstPath.resolve("deposit.properties")));
        assertTrue(Files.exists(firstPath.resolve("audiences/bagit.txt")));
        assertFalse(Files.exists(firstPath.resolve("bag.zip.1")));
        assertFalse(Files.exists(firstPath.resolve("bag.zip.2")));
        assertFalse(Files.exists(firstPath.resolve("bag.zip.3")));
    }

    @Test
    void testZipInPartsWithInvalidHash() throws
            IOException, ConfigurationException, NoSuchAlgorithmException, InterruptedException {
        var path = getClass().getResource("/zips/audiences.zip");
        assert path != null;

        var bytes = path.openStream().readAllBytes();
        var bagSize = bytes.length / 3;
        var firstPart = Arrays.copyOfRange(bytes, 0, bagSize);

        var checksum = md5Checksum(firstPart);
        var result = buildRequest("/collection/1")
                .header("content-type", "application/octet-stream")
                .header("content-md5", checksum)
                .header("content-disposition", "attachment; filename=bag.zip.1")
                .header("in-progress", "true")
                .post(Entity.entity(firstPart, MediaType.valueOf("application/octet-stream")));

        assertEquals(201, result.getStatus());

        var receipt = result.readEntity(Entry.class);
        var parts = receipt.getId().split("/");
        var id = parts[parts.length - 1];

        var secondPart = Arrays.copyOfRange(bytes, bagSize, bagSize * 2);
        var checksum2 = md5Checksum(secondPart);
        var result2 = buildRequest("/container/" + id)
                .header("content-type", "application/octet-stream")
                .header("content-md5", checksum2)
                .header("content-disposition", "attachment; filename=bag.zip.2")
                .header("in-progress", "true")
                .post(Entity.entity(secondPart, MediaType.valueOf("application/octet-stream")));
        assertEquals(200, result2.getStatus());

        var thirdPart = Arrays.copyOfRange(bytes, bagSize * 2, bytes.length);
        var checksum3 = md5Checksum(thirdPart);

        var result3 = buildRequest("/container/" + id)
                .header("content-type", "application/octet-stream")
                .header("content-md5", "invalid_checksum")
                .header("content-disposition", "attachment; filename=bag.zip.3")
                .header("in-progress", "false")
                .post(Entity.entity(thirdPart, MediaType.valueOf("application/zip")));
        assertEquals(412, result3.getStatus());

        var firstPath = testDir.resolve("1/uploads/").resolve(id);
        assertTrue(Files.exists(firstPath.resolve("bag.zip.1")));
        assertTrue(Files.exists(firstPath.resolve("bag.zip.2")));
        assertTrue(Files.exists(firstPath.resolve("bag.zip.3")));
    }

    @Test
    void testZipDepositUploaded() throws IOException, ConfigurationException, InterruptedException {
        var path = getClass().getResource("/zips/audiences.zip");

        assert path != null;

        var result = buildRequest("/collection/1")
                .header("content-type", "application/zip")
                .header("content-md5", "bc27e20467a773501a4ae37fb85a9c3f")
                .header("in-progress", "false")
                .header("content-disposition", "attachment; filename=bag.zip")
                .post(Entity.entity(path.openStream(), MediaType.valueOf("application/zip")));

        assertEquals(201, result.getStatus());

        var receipt = result.readEntity(Entry.class);
        var parts = receipt.getId().split("/");
        var id = parts[parts.length - 1];

        var count = 0;
        var state = "";

        // waiting at most 5 seconds for the background thread to handle this
        while (count < 5) {
            var statement = buildRequest("/statement/" + id)
                    .get(Feed.class);

            state = statement.getCategory()
                    .getTerm();

            if (state.equals("SUBMITTED")) {
                break;
            }
            Thread.sleep(1000);
            count += 1;
        }

        assertEquals("SUBMITTED", state);

        var firstPath = testDir.resolve("1/deposits").resolve(id);
        var config = getProperties(firstPath);

        assertNotNull(config.getString("bag-store.bag-id"));
        assertNotNull(config.getString("dataverse.bag-id"));
        assertNotNull(config.getString("creation.timestamp"));
        assertEquals("SWORD2", config.getString("deposit.origin"));
        assertEquals("SUBMITTED", config.getString("state.label"));
        assertEquals("user001", config.getString("depositor.userId"));
        assertEquals("Deposit is valid and ready for post-submission processing", config.getString("state.description"));
        assertEquals("audiences", config.getString("bag-store.bag-name"));

    }

    @Test
    void testInvalidZipDepositUploaded() throws IOException, ConfigurationException, InterruptedException {
        var path = getClass().getResource("/zips/invalid-sha1.zip");

        assert path != null;

        var result = buildRequest("/collection/1")
                .header("content-type", "application/zip")
                .header("content-md5", "db45b2cfeb223d35d25a6d5208b528db")
                .header("in-progress", "false")
                .header("content-disposition", "attachment; filename=bag.zip")
                .post(Entity.entity(path.openStream(), MediaType.valueOf("application/zip")));

        assertEquals(201, result.getStatus());

        var receipt = result.readEntity(Entry.class);
        var parts = receipt.getId().split("/");
        var id = parts[parts.length - 1];

        var count = 0;
        var state = "";

        // waiting at most 5 seconds for the background thread to handle this
        while (count < 5) {
            var statement = buildRequest("/statement/" + id)
                    .get(Feed.class);

            state = statement.getCategory()
                    .getTerm();

            if (state.equals("INVALID")) {
                break;
            }
            Thread.sleep(1000);
            count += 1;
        }

        assertEquals("INVALID", state);

        var firstPath = testDir.resolve("1/uploads").resolve(id);
        var config = getProperties(firstPath);

        assertNotNull(config.getString("bag-store.bag-id"));
        assertNotNull(config.getString("dataverse.bag-id"));
        assertNotNull(config.getString("creation.timestamp"));
        assertEquals("SWORD2", config.getString("deposit.origin"));
        assertEquals("INVALID", config.getString("state.label"));
        assertEquals("user001", config.getString("depositor.userId"));
        assertTrue(config.getString("state.description").contains("is suppose to have a [SHA-1] hash of"));
        assertNull(config.getString("bag-store.bag-name"));

        assertFalse(Files.exists(firstPath.resolve("bag.zip")));
        assertFalse(Files.exists(firstPath.resolve("invalid-sha1")));
    }

    @Test
    void testInvalidHash() throws IOException {
        var path = getClass().getResource("/zips/audiences.zip");

        assert path != null;

        var result = buildRequest("/collection/1")
                .header("content-type", "application/zip")
                .header("content-md5", "invalid_hash")
                .header("in-progress", "false")
                .header("content-disposition", "attachment; filename=bag.zip")
                .post(Entity.entity(path.openStream(), MediaType.valueOf("application/zip")));

        assertEquals(412, result.getStatus());

        var error = result.readEntity(Error.class);
        assertEquals("ERROR", error.getTitle());
        assertEquals("Processing failed", error.getTreatment());
        assertEquals("http://purl.org/net/sword/error/ErrorChecksumMismatch", error.getSummary());
    }

    @Test
    void testMultipartZipFileNotImplemented() throws IOException {
        var path = getClass().getResource("/zips/audiences.zip");
        assert path != null;

        var multiPart = new MultiPart();
        var payloadPart = new BodyPart(MediaType.valueOf("application/zip"));
        payloadPart.getHeaders().add("content-disposition", "attachment; filename=bag.zip; name=payload");
        payloadPart.getHeaders().add("content-md5", "bc27e20467a773501a4ae37fb85a9c3f");
        payloadPart.getHeaders().add("packaging", "http://purl.org/net/sword/package/BagIt");
        payloadPart.entity(path.openStream());

        multiPart.bodyPart(payloadPart);
        multiPart.setMediaType(MediaType.valueOf("multipart/related"));

        var result = buildRequest("/collection/1")
                .header("content-length", 1000)
                .header("in-progress", "true")
                .post(Entity.entity(multiPart, multiPart.getMediaType()));

        assertEquals(405, result.getStatus());
    }

    @Test
    void testUnknownCollection() throws IOException {
        var path = getClass().getResource("/zips/audiences.zip");

        assert path != null;

        var result = buildRequest("/collection/123")
                .header("content-type", "application/zip")
                .header("content-md5", "bc27e20467a773501a4ae37fb85a9c3f")
                .header("content-disposition", "attachment; filename=bag.zip")
                .header("in-progress", "true")
                .post(Entity.entity(path.openStream(), MediaType.valueOf("application/zip")));

        assertEquals(405, result.getStatus());

        var error = result.readEntity(Error.class);
        assertEquals("ERROR", error.getTitle());
        assertEquals("Processing failed", error.getTreatment());
        assertEquals("Collection with id 123 could not be found", error.getSummary());
    }

    @Test
    void testInvalidHeader() throws IOException {
        var path = getClass().getResource("/zips/audiences.zip");

        assert path != null;

        var result = buildRequest("/collection/123")
                .header("content-type", "application/zip")
                .header("content-md5", "bc27e20467a773501a4ae37fb85a9c3f")
                .header("content-disposition", "attachment; filename=bag.zip")
                .header("in-progress", "this is something else")
                .post(Entity.entity(path.openStream(), MediaType.valueOf("application/zip")));

        assertEquals(400, result.getStatus());

        var error = result.readEntity(Error.class);
        assertEquals("ERROR", error.getTitle());
        assertEquals("Processing failed", error.getTreatment());
        assertEquals("In-Progress header must be either 'true' or 'false'", error.getSummary());
    }

    @Test
    void testAtomFileNotImplemented() throws IOException {
        var path = getClass().getResource("/zips/audiences.zip");
        assert path != null;

        var result = buildRequest("/collection/1")
                .header("content-length", 1000)
                .header("in-progress", "true")
                .header("content-type", "application/atom+xml")
                .post(Entity.entity(path.openStream(), MediaType.valueOf("application/atom+xml")));

        assertEquals(405, result.getStatus());
    }

    FileBasedConfiguration getProperties(Path path) throws ConfigurationException {
        var params = new Parameters();
        var paramConfig = params.properties()
                .setFileName(path.resolve("deposit.properties")
                        .toString());

        var builder = new FileBasedConfigurationBuilder<FileBasedConfiguration>(
                PropertiesConfiguration.class, null, true).configure(
                paramConfig);

        return builder.getConfiguration();
    }

    String md5Checksum(byte[] parts) throws NoSuchAlgorithmException {
        var md = MessageDigest.getInstance("MD5");
        md.update(parts);

        return DatatypeConverter.printHexBinary(md.digest())
                .toLowerCase(Locale.ROOT);
    }
}
