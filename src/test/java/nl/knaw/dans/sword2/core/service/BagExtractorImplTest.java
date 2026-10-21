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
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
    void simpleDepositShouldPreserveExistingFilePathMapping() throws Exception, NotEnoughDiskSpaceException {
        var entries = bagEntries();
        var originalMapping = "data/test.txt  data/original name.txt\n";
        entries.put("bag/original-filepaths.txt", originalMapping);
        createZip("bag.zip", entries);

        realBagExtractor().extractBag(testPath, 1, "application/zip", true);

        assertEquals("test", Files.readString(testPath.resolve("bag/data/test.txt")));
        assertEquals(originalMapping, Files.readString(testPath.resolve("bag/original-filepaths.txt")));
        assertEquals(entries.get("bag/manifest-sha1.txt"), Files.readString(testPath.resolve("bag/manifest-sha1.txt")));
    }

    @Test
    void simpleDepositShouldStillMapPathsWhenNoMappingExists() throws Exception, NotEnoughDiskSpaceException {
        createZip("bag.zip", bagEntries());

        realBagExtractor().extractBag(testPath, 1, "application/zip", true);

        Assertions.assertThat(testPath.resolve("bag/data/test.txt")).doesNotExist();
        Assertions.assertThat(Files.readString(testPath.resolve("bag/original-filepaths.txt"))).contains("  data/test.txt");
    }

    @Test
    void simpleDepositShouldNotMapPathsWhenDisabled() throws Exception, NotEnoughDiskSpaceException {
        createZip("bag.zip", bagEntries());

        realBagExtractor().extractBag(testPath, 1, "application/zip", false);

        assertEquals("test", Files.readString(testPath.resolve("bag/data/test.txt")));
        Assertions.assertThat(testPath.resolve("bag/original-filepaths.txt")).doesNotExist();
    }

    @Test
    void mappingFilenameInPayloadShouldNotDisableMapping() throws Exception, NotEnoughDiskSpaceException {
        var entries = bagEntries();
        entries.put("bag/data/original-filepaths.txt", "test");
        entries.put("bag/manifest-sha1.txt", entries.get("bag/manifest-sha1.txt")
            + "a94a8fe5ccb19ba61c4c0873d391e987982fbbd3  data/original-filepaths.txt\n");
        createZip("bag.zip", entries);

        realBagExtractor().extractBag(testPath, 1, "application/zip", true);

        Assertions.assertThat(testPath.resolve("bag/data/test.txt")).doesNotExist();
        Assertions.assertThat(testPath.resolve("bag/data/original-filepaths.txt")).doesNotExist();
        Assertions.assertThat(Files.readString(testPath.resolve("bag/original-filepaths.txt")))
            .contains("  data/test.txt", "  data/original-filepaths.txt");
    }

    @Test
    void continuedZipDepositShouldInspectLaterPartsBeforeMappingAnyPayload() throws Exception, NotEnoughDiskSpaceException {
        var firstPart = createZip("part1.zip", bagEntries());
        var originalMapping = "data/test.txt  data/first original.txt\ndata/second.txt  data/second original.txt\n";
        var lastPart = createZip("part2.zip", Map.of(
            "bag/original-filepaths.txt", originalMapping,
            "bag/data/second.txt", "second payload"));
        var extractor = Mockito.spy(new BagExtractorImpl(zipService, fileService, bagItManager, filesystemSpaceVerifier));
        Mockito.doReturn(List.of(firstPart, lastPart)).when(extractor).getZipFilesInDeposit(testPath);

        extractor.extractBag(testPath, 1, "application/zip", true);

        assertEquals("test", Files.readString(testPath.resolve("bag/data/test.txt")));
        assertEquals("second payload", Files.readString(testPath.resolve("bag/data/second.txt")));
        assertEquals(originalMapping, Files.readString(testPath.resolve("bag/original-filepaths.txt")));
        Mockito.verify(bagItManager, Mockito.times(2)).updateManifests(testPath, Map.of());
    }

    @Test
    void continuedOctetStreamDepositShouldPreserveExistingFilePathMapping() throws Exception, NotEnoughDiskSpaceException {
        var entries = bagEntries();
        var originalMapping = "data/test.txt  data/original name.txt\n";
        entries.put("bag/original-filepaths.txt", originalMapping);
        createOctetStreamParts(entries);

        realBagExtractor().extractBag(testPath, 1, "application/octet-stream", true);

        assertEquals("test", Files.readString(testPath.resolve("bag/data/test.txt")));
        assertEquals(originalMapping, Files.readString(testPath.resolve("bag/original-filepaths.txt")));
        assertEquals(entries.get("bag/manifest-sha1.txt"), Files.readString(testPath.resolve("bag/manifest-sha1.txt")));
        Assertions.assertThat(testPath.resolve("part.1")).doesNotExist();
        Assertions.assertThat(testPath.resolve("part.2")).doesNotExist();
    }

    @Test
    void continuedOctetStreamDepositShouldStillMapPathsWhenNoMappingExists() throws Exception, NotEnoughDiskSpaceException {
        createOctetStreamParts(bagEntries());

        realBagExtractor().extractBag(testPath, 1, "application/octet-stream", true);

        Assertions.assertThat(testPath.resolve("bag/data/test.txt")).doesNotExist();
        Assertions.assertThat(Files.readString(testPath.resolve("bag/original-filepaths.txt"))).contains("  data/test.txt");
    }

    private BagExtractorImpl realBagExtractor() {
        return new BagExtractorImpl(zipService, fileService, new BagItManagerImpl(fileService, checksumCalculator), filesystemSpaceVerifier);
    }

    private Map<String, String> bagEntries() {
        return new HashMap<>(Map.of(
            "bag/bagit.txt", "BagIt-Version: 0.97\nTag-File-Character-Encoding: UTF-8\n",
            "bag/manifest-sha1.txt", "a94a8fe5ccb19ba61c4c0873d391e987982fbbd3  data/test.txt\n",
            "bag/data/test.txt", "test"));
    }

    private Path createZip(String filename, Map<String, String> entries) throws IOException {
        var zipFile = testPath.resolve(filename);
        try (var output = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            for (var entry : entries.entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return zipFile;
    }

    private void createOctetStreamParts(Map<String, String> entries) throws IOException {
        var zipFile = createZip("bag.zip", entries);
        var bytes = Files.readAllBytes(zipFile);
        Files.write(testPath.resolve("part.2"), Arrays.copyOfRange(bytes, bytes.length / 2, bytes.length));
        Files.write(testPath.resolve("part.1"), Arrays.copyOfRange(bytes, 0, bytes.length / 2));
        Files.delete(zipFile);
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
    void testIncorrectNumberZero() {
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
    void getSequenceNumber_should_throw_error_with_00_suffix() throws InvalidPartialFileException {
        var path = Path.of("test.zip.00");
        var e = assertThrows(InvalidPartialFileException.class,
            () -> new BagExtractorImpl(zipService, fileService, bagItManager, filesystemSpaceVerifier).getSequenceNumber(path));

        assertTrue(e.getMessage().contains("It should be a positive sequence number (> 0)"));
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
