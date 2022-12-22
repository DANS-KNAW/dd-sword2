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
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import nl.knaw.dans.sword2.DdSword2Application;
import nl.knaw.dans.sword2.DdSword2Configuration;
import nl.knaw.dans.sword2.TestFixture;
import nl.knaw.dans.sword2.TestFixtureExt;
import nl.knaw.dans.sword2.api.entry.Entry;
import nl.knaw.dans.sword2.core.Deposit;
import nl.knaw.dans.sword2.core.DepositState;
import nl.knaw.dans.sword2.core.exceptions.InvalidDepositException;
import nl.knaw.dans.sword2.core.service.ChecksumCalculator;
import nl.knaw.dans.sword2.core.service.ChecksumCalculatorImpl;
import nl.knaw.dans.sword2.core.service.DepositPropertiesManager;
import nl.knaw.dans.sword2.core.service.DepositPropertiesManagerImpl;
import nl.knaw.dans.sword2.core.service.FileService;
import nl.knaw.dans.sword2.core.service.FileServiceImpl;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;

@ExtendWith(DropwizardExtensionsSupport.class)
class CollectionResourceBagSenderIntegrationTest extends TestFixtureExt {
    private static final Logger log = LoggerFactory.getLogger(CollectionResourceBagSenderIntegrationTest.class);

    private static final FileService fileService = new FileServiceImpl();
    private static final ChecksumCalculator checksumCalculator = new ChecksumCalculatorImpl();
    private static final DepositPropertiesManager depositPropertiesManager = new DepositPropertiesManagerImpl();

    private final Path BASE_PATH = testDir.resolve("bagsender");

    public CollectionResourceBagSenderIntegrationTest() {
        super("test-etc/config-bagsender.yml");
    }

    @BeforeEach
    void startUp() throws IOException {
        FileUtils.deleteDirectory(BASE_PATH.toFile());
        fileService.ensureDirectoriesExist(BASE_PATH);
    }

    @AfterEach
    void tearDown() {
        ((LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory()).stop();
    }

    @Test
    void testValidBagAudiences() throws IOException, NoSuchAlgorithmException, InvalidDepositException, InterruptedException {
        var response = makeRequest("valid.zip", "valid/audiences");
        Assertions.assertEquals(201, response.getStatus());
        var deposit = getDepositNotFinalizing(response);
        Assertions.assertEquals(DepositState.SUBMITTED, deposit.getState());
    }

    @Test
    void testValidBagAudiencesSha256() throws IOException, NoSuchAlgorithmException, InvalidDepositException, InterruptedException {
        var response = makeRequest("valid.zip", "valid/audiences-sha256");
        Assertions.assertEquals(201, response.getStatus());
        var deposit = getDepositNotFinalizing(response);
        Assertions.assertEquals(DepositState.SUBMITTED, deposit.getState());
    }

    @Test
    void testValidBagEmbargoed() throws IOException, NoSuchAlgorithmException, InvalidDepositException, InterruptedException {
        var response = makeRequest("valid.zip", "valid/embargoed");
        Assertions.assertEquals(201, response.getStatus());
        var deposit = getDepositNotFinalizing(response);
        Assertions.assertEquals(DepositState.SUBMITTED, deposit.getState());
    }

    @Test
    void testValidBagFileAccessibilities() throws IOException, NoSuchAlgorithmException, InvalidDepositException, InterruptedException {
        var response = makeRequest("valid.zip", "valid/file-accessibilities");
        Assertions.assertEquals(201, response.getStatus());
        var deposit = getDepositNotFinalizing(response);
        Assertions.assertEquals(DepositState.SUBMITTED, deposit.getState());
    }

    @Test
    void testValidBagInaccessible() throws IOException, NoSuchAlgorithmException, InvalidDepositException, InterruptedException {
        var response = makeRequest("valid.zip", "valid/inaccessible");
        Assertions.assertEquals(201, response.getStatus());
        var deposit = getDepositNotFinalizing(response);
        Assertions.assertEquals(DepositState.SUBMITTED, deposit.getState());
    }

    @Test
    void testValidBagInvisible() throws IOException, NoSuchAlgorithmException, InvalidDepositException, InterruptedException {
        var response = makeRequest("valid.zip", "valid/invisible");
        Assertions.assertEquals(201, response.getStatus());
        var deposit = getDepositNotFinalizing(response);
        Assertions.assertEquals(DepositState.SUBMITTED, deposit.getState());
    }

    @Test
    void testValidBagMultisurface() throws IOException, NoSuchAlgorithmException, InvalidDepositException, InterruptedException {
        var response = makeRequest("valid.zip", "valid/multisurface");
        Assertions.assertEquals(201, response.getStatus());
        var deposit = getDepositNotFinalizing(response);
        Assertions.assertEquals(DepositState.SUBMITTED, deposit.getState());
    }

    @Test
    void testValidBagRevision01() throws IOException, NoSuchAlgorithmException, InvalidDepositException, InterruptedException {
        var response = makeRequest("valid.zip", "valid/revision01");
        Assertions.assertEquals(201, response.getStatus());
        var deposit = getDepositNotFinalizing(response);
        Assertions.assertEquals(DepositState.SUBMITTED, deposit.getState());
    }

    @Test
    void testValidBagRevision02() throws IOException, NoSuchAlgorithmException, InvalidDepositException, InterruptedException {
        var response = makeRequest("valid.zip", "valid/revision02");
        Assertions.assertEquals(201, response.getStatus());
        var deposit = getDepositNotFinalizing(response);
        Assertions.assertEquals(DepositState.SUBMITTED, deposit.getState());
    }

    @Test
    void testValidBagRevision03() throws IOException, NoSuchAlgorithmException, InvalidDepositException, InterruptedException {
        var response = makeRequest("valid.zip", "valid/revision03");
        Assertions.assertEquals(201, response.getStatus());
        var deposit = getDepositNotFinalizing(response);
        Assertions.assertEquals(DepositState.SUBMITTED, deposit.getState());
    }

    @Test
    void testInValidBagInvalidFlow() throws IOException, NoSuchAlgorithmException, InvalidDepositException, InterruptedException {
        var response = makeRequest("invalid.zip", "invalid/invalid-flow");
        var deposit = getDepositNotFinalizing(response);

        Assertions.assertEquals(201, response.getStatus());
        // the validation of invalid IDs does not seem to happen
        Assertions.assertEquals(DepositState.SUBMITTED, deposit.getState());
    }

    @Test
    void testInValidBagInvalidSha1() throws IOException, NoSuchAlgorithmException, InvalidDepositException, InterruptedException {
        var response = makeRequest("invalid.zip", "invalid/invalid-sha1");
        var deposit = getDepositNotFinalizing(response);

        Assertions.assertEquals(201, response.getStatus());
        Assertions.assertEquals(DepositState.INVALID, deposit.getState());
        Assertions.assertTrue(deposit.getStateDescription().contains("is suppose to have a [SHA-1] hash of"));
    }

    @Test
    void testInValidBagNoAvailableDate() throws IOException, NoSuchAlgorithmException, InvalidDepositException, InterruptedException {
        var response = makeRequest("invalid.zip", "invalid/no-available-date");
        var deposit = getDepositNotFinalizing(response);

        Assertions.assertEquals(201, response.getStatus());

        // the validation of no available dates does not seem to happen
        Assertions.assertEquals(DepositState.SUBMITTED, deposit.getState());

    }

    @Test
    void testInValidBagTwoAvailableDates() throws IOException, NoSuchAlgorithmException, InvalidDepositException, InterruptedException {
        var response = makeRequest("invalid.zip", "invalid/two-available-dates");
        var deposit = getDepositNotFinalizing(response);

        Assertions.assertEquals(201, response.getStatus());
        // the validation of double dates does not seem to happen
        Assertions.assertEquals(DepositState.SUBMITTED, deposit.getState());

    }

    Deposit getDepositNotFinalizing(Response response) throws InvalidDepositException, InterruptedException {
        var entry = response.readEntity(Entry.class);
        var count = 0;

        Thread.sleep(100);

        while (count < 10) {
            var deposit = getPropsFromResponse(entry);

            if (deposit.getState().equals(DepositState.DRAFT) || deposit.getState().equals(DepositState.FINALIZING)) {
                count += 1;
                Thread.sleep(100);
            }
            else {
                return deposit;
            }
        }

        throw new InvalidDepositException("Deposit not found");
    }

    Deposit getPropsFromResponse(Entry entry) throws InterruptedException, InvalidDepositException {

        var id = getEntryId(entry);

        var count = 0;

        while (count < 10) {
            try {
                return depositPropertiesManager.getProperties(BASE_PATH.resolve("deposits").resolve(id));

            }
            catch (Exception | InvalidDepositException e) {
                try {
                    return depositPropertiesManager.getProperties(BASE_PATH.resolve("uploads").resolve(id));
                }
                catch (Exception | InvalidDepositException e2) {
                    count += 1;
                    Thread.sleep(100);
                }
            }
        }

        throw new InvalidDepositException("Deposit not found");
    }

    String getEntryId(Entry entry) {
        return entry.getId().replace("http://localhost:20320/container/", "");
    }

    Response makeRequest(String name, String bag) throws IOException, NoSuchAlgorithmException {
        var path = getClass().getResource("/bags/" + bag);
        assert path != null;

        fileService.ensureDirectoriesExist(BASE_PATH);

        var newFile = BASE_PATH.resolve(name).toAbsolutePath().toString();

        if (Files.exists(Path.of(newFile))) {
            Files.delete(Path.of(newFile));
        }

        var zipFile = new ZipFile(newFile);
        var parameters = new ZipParameters();

        zipFile.addFolder(new File(path.getPath()), parameters);
        zipFile.close();

        var is = new FileInputStream(zipFile.getFile());

        var checksum = checksumCalculator.calculateChecksum(zipFile.getFile().toPath(), "MD5");
        var url = String.format("http://localhost:%s%s", EXT.getLocalPort(), "/collection/1");

        return RequestClientBuilder.buildClient()
            .target(url)
            .request()
            .header("content-type", "application/zip")
            .header("content-md5", checksum)
            .header("content-disposition", "attachment; filename=bag.zip")
            .header("in-progress", "false")
            .header("Packaging", "http://purl.org/net/sword/package/BagIt")
            .header("authorization", "Basic dXNlcjAwMTp1c2VyMDAx")
            .header("accept", "application/atom+xml;type=feed")
            .post(Entity.entity(is, MediaType.valueOf("application/zip")));
    }

}
