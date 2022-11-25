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

import nl.knaw.dans.sword2.TestFixture;
import nl.knaw.dans.sword2.core.exceptions.InvalidDepositException;
import nl.knaw.dans.sword2.core.exceptions.InvalidPartialFileException;
import nl.knaw.dans.sword2.core.exceptions.NotEnoughDiskSpaceException;
import org.apache.commons.io.FileUtils;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BagExtractorImplTest extends TestFixture {


    private final FileService fileService = new FileServiceImpl();
    private final ZipService zipService = new ZipServiceImpl(fileService);
    private final Path testPath = testDir.resolve("bagextractor/");
    private final BagItManager bagItManager = Mockito.mock(BagItManager.class);
    private final ChecksumCalculator checksumCalculator = new ChecksumCalculatorImpl();
    private final FilesystemSpaceVerifier filesystemSpaceVerifier = Mockito.mock(FilesystemSpaceVerifier.class);

    Path getZipFile(String name) {
        var zipName = "/zips/" + name;
        var p = getClass().getResource(zipName);
        assert p != null;
        return Path.of(p.getPath());
    }

    @BeforeEach
    void startUp() throws IOException {
        FileUtils.deleteDirectory(testPath.toFile());
        fileService.ensureDirectoriesExist(testPath);
    }

    @AfterEach
    void tearDown() throws IOException {
        FileUtils.deleteDirectory(testPath.toFile());
    }

    @Test
    void testGenerateEmptyFileMappingForEmptyZip() throws IOException {
        var file = getZipFile("empty.zip");
        var mapping = new BagExtractorImpl(zipService, fileService, bagItManager, filesystemSpaceVerifier).generateFilePathMapping(file);

        assertTrue(mapping.isEmpty());
    }

    @Test
    void testGenerateMappingsForFilesUnderPrefix() throws IOException {
        var file = getZipFile("mix.zip");
        var mapping = new BagExtractorImpl(zipService, fileService, bagItManager, filesystemSpaceVerifier)
            .generateFilePathMapping(file, Pattern.compile("subfolder/"));

        Assertions.assertThat(mapping.keySet()).containsOnly("subfolder/test.txt",
            "subfolder2/subsubfolder/leaf.txt");

        assertTrue(mapping.get("subfolder/test.txt").startsWith("subfolder/"));
        assertTrue(mapping.get("subfolder2/subsubfolder/leaf.txt").startsWith("subfolder/"));
    }

    @Test
    void testShouldUnzipEmptyZip() throws IOException, InvalidDepositException, NotEnoughDiskSpaceException {
        var zipFile = getZipFile("empty.zip");

        new BagExtractorImpl(zipService, fileService, bagItManager, filesystemSpaceVerifier)
            .extractWithFilePathMapping(zipFile, testPath.resolve("emptydir"), 1, Map.of());

        assertEquals(0, fileService.listFiles(testPath.resolve("emptydir")).count());
    }

    @Test
    void testShouldUnzipFileWithOneUnmappedRootEntry() throws IOException, InvalidDepositException, NotEnoughDiskSpaceException {
        var zipFile = getZipFile("one-entry.zip");

        new BagExtractorImpl(zipService, fileService, bagItManager, filesystemSpaceVerifier)
            .extractWithFilePathMapping(zipFile, testPath.resolve("dir1"), 1, Map.of());

        assertEquals(1, fileService.listFiles(testPath.resolve("dir1")).count());

        var contents = FileUtils.readFileToString(testPath.resolve("dir1/test.txt").toFile(), StandardCharsets.UTF_8);
        assertEquals("test", contents.trim());
    }

    @Test
    void testShouldUnzipFileWithOneMappedRootEntry() throws IOException, InvalidDepositException, NotEnoughDiskSpaceException {
        var zipFile = getZipFile("one-entry.zip");
        var filePathMapping = Map.of("test.txt", "renamed.txt");

        new BagExtractorImpl(zipService, fileService, bagItManager, filesystemSpaceVerifier)
            .extractWithFilePathMapping(zipFile, testPath.resolve("dir1"), 1, filePathMapping);

        assertEquals(1, fileService.listFiles(testPath.resolve("dir1")).count());

        var contents = FileUtils.readFileToString(testPath.resolve("dir1/renamed.txt").toFile(), StandardCharsets.UTF_8);
        assertEquals("test", contents.trim());
    }

    @Test
    void testShouldUnzipFileWithOneUnmappedEntryInSubfolder() throws IOException, InvalidDepositException, NotEnoughDiskSpaceException {
        var zipFile = getZipFile("one-entry-in-subfolder.zip");
        var filePathMapping = new HashMap<String, String>();

        new BagExtractorImpl(zipService, fileService, bagItManager, filesystemSpaceVerifier)
            .extractWithFilePathMapping(zipFile, testPath.resolve("dir1"), 1, filePathMapping);

        assertEquals(1, fileService.listDirectories(testPath.resolve("dir1")).size());

        var contents = FileUtils.readFileToString(testPath.resolve("dir1/subfolder/test.txt").toFile(), StandardCharsets.UTF_8);
        assertEquals("test", contents.trim());
    }

    @Test
    void testShouldUnzipFileWithOneMappedEntryInSubfolder() throws IOException, InvalidDepositException, NotEnoughDiskSpaceException {
        var zipFile = getZipFile("one-entry-in-subfolder.zip");
        var filePathMapping = Map.of("subfolder/test.txt", "renamed.txt");

        new BagExtractorImpl(zipService, fileService, bagItManager, filesystemSpaceVerifier)
            .extractWithFilePathMapping(zipFile, testPath.resolve("dir1"), 1, filePathMapping);

        assertEquals(1, fileService.listFiles(testPath.resolve("dir1")).count());

        var contents = FileUtils.readFileToString(testPath.resolve("dir1/renamed.txt").toFile(), StandardCharsets.UTF_8);
        assertEquals("test", contents.trim());
    }

    @Test
    void testShouldUnzipFileWithSeveralEntriesSomeInSubfoldersAndSomeMapped() throws IOException, InvalidDepositException, NotEnoughDiskSpaceException {
        var zipFile = getZipFile("mix.zip");
        var filePathMapping = Map.of(
            "subfolder/test.txt", "renamed.txt",
            "subfolder2/subsubfolder/leaf.txt", "renamed2.txt");

        new BagExtractorImpl(zipService, fileService, bagItManager, filesystemSpaceVerifier)
            .extractWithFilePathMapping(zipFile, testPath.resolve("dir1"), 1, filePathMapping);

        assertEquals(3, fileService.listFiles(testPath.resolve("dir1")).count());

        var contents1 = FileUtils.readFileToString(testPath.resolve("dir1/renamed.txt").toFile(), StandardCharsets.UTF_8);
        assertEquals("test", contents1.trim());

        var contents2 = FileUtils.readFileToString(testPath.resolve("dir1/renamed2.txt").toFile(), StandardCharsets.UTF_8);
        assertEquals("in leaf", contents2.trim());

        var contents3 = FileUtils.readFileToString(testPath.resolve("dir1/root.txt").toFile(), StandardCharsets.UTF_8);
        assertEquals("in root", contents3.trim());
    }

    @Test
    void testExtractOctetStream() throws Exception, InvalidPartialFileException, InvalidDepositException, NotEnoughDiskSpaceException {
        // copy a zip into 3 different files
        var zipFile = getZipFile("double-image.zip");

        var part1 = copyPartOfFile(zipFile, testPath.resolve("part.1"), 0, 1000000);
        var part2 = copyPartOfFile(zipFile, testPath.resolve("part.2"), 1000000, 2000000);
        var part3 = copyPartOfFile(zipFile, testPath.resolve("part.3"), 2000000, Files.size(zipFile));

        new BagExtractorImpl(zipService, fileService, bagItManager, filesystemSpaceVerifier)
            .extractOctetStream(testPath, 1, false);

        assertEquals(1, fileService.listFiles(testPath).count());
    }

    Path copyPartOfFile(Path zipFile, Path name, int start, long size) throws IOException {
        var buffer = new FileInputStream(zipFile.toFile()).readAllBytes();
        var copy = Arrays.copyOfRange(buffer, start, (int) size);

        Files.write(name, copy);

        return name;
    }

    @Test
    void testMultipleFolders() throws IOException {
        var fileService = Mockito.mock(FileService.class);
        Mockito.when(fileService.listDirectories(Mockito.any()))
            .thenReturn(List.of(Path.of("1"), Path.of("2")));

        assertThrows(InvalidDepositException.class, () -> new BagExtractorImpl(zipService, fileService, bagItManager, filesystemSpaceVerifier)
            .getBagDir(Path.of("test")));
    }

    @Test
    void testSingleFolders() throws InvalidDepositException, IOException {
        var fileService = Mockito.mock(FileService.class);
        Mockito.when(fileService.listDirectories(Mockito.any()))
            .thenReturn(List.of(Path.of("1")));

        var bagDir = new BagExtractorImpl(zipService, fileService, bagItManager, filesystemSpaceVerifier).getBagDir(Path.of("test"));
        assertEquals(Path.of("1"), bagDir);
    }

    @Test
    void testZeroFolders() throws IOException {
        var fileService = Mockito.mock(FileService.class);
        Mockito.when(fileService.listDirectories(Mockito.any()))
            .thenReturn(new ArrayList<>());

        assertThrows(InvalidDepositException.class, () -> new BagExtractorImpl(zipService, fileService, bagItManager, filesystemSpaceVerifier)
            .getBagDir(Path.of("test")));
    }

    @Test
    void testCorrectSequenceNumber() throws InvalidPartialFileException {
        var path = Path.of("test.123");
        var number = new BagExtractorImpl(zipService, fileService, bagItManager, filesystemSpaceVerifier).getSequenceNumber(path);
        assertEquals(123, number);
    }

    @Test
    void testCorrectSequenceNumberWithMoreDots() throws InvalidPartialFileException {
        var path = Path.of("test.more.dots..15");
        var number = new BagExtractorImpl(zipService, fileService, bagItManager, filesystemSpaceVerifier).getSequenceNumber(path);

        assertEquals(15, number);
    }

    @Test
    void testIncorrectNumberZero() throws InvalidPartialFileException {
        var path = Path.of("test.0");
        assertThrows(InvalidPartialFileException.class, () -> new BagExtractorImpl(zipService, fileService, bagItManager, filesystemSpaceVerifier).getSequenceNumber(path));
    }

    @Test
    void testMissingSequence() throws InvalidPartialFileException {
        var path = Path.of("test");
        assertThrows(InvalidPartialFileException.class, () -> new BagExtractorImpl(zipService, fileService, bagItManager, filesystemSpaceVerifier).getSequenceNumber(path));
    }

    @Test
    void testNonNumericalExtension() throws InvalidPartialFileException {
        var path = Path.of("test.zip1");
        assertThrows(InvalidPartialFileException.class, () -> new BagExtractorImpl(zipService, fileService, bagItManager, filesystemSpaceVerifier).getSequenceNumber(path));
    }

    @Test
    void testNegativeNumber() throws InvalidPartialFileException {
        var path = Path.of("test.-1");
        assertThrows(InvalidPartialFileException.class, () -> new BagExtractorImpl(zipService, fileService, bagItManager, filesystemSpaceVerifier).getSequenceNumber(path));
    }

    void testExtractWithFilePathMapping() {
        /*

  "extractWithFilepathMapping" should "correctly unzip medium bag and leave it valid" in {
    extractWithFilepathMapping(getZipFile("medium.zip"), outDir, "dummyId")
  }
         */
    }

    void testShouldAcceptMultiplePayloadFilesWithSameChecksum() {
        /*

  it should "accept multiple payload files with the same checksum" in {
    extractWithFilepathMapping(getZipFile("double-image.zip"), outDir, "dummyId")
  }
         */
    }
    /*


     */
}
