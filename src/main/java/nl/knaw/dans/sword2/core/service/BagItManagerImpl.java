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

import gov.loc.repository.bagit.domain.Bag;
import gov.loc.repository.bagit.exceptions.InvalidBagitFileFormatException;
import gov.loc.repository.bagit.exceptions.MaliciousPathException;
import gov.loc.repository.bagit.exceptions.UnparsableVersionException;
import gov.loc.repository.bagit.exceptions.UnsupportedAlgorithmException;
import gov.loc.repository.bagit.reader.BagReader;
import gov.loc.repository.bagit.verify.BagVerifier;
import gov.loc.repository.bagit.writer.ManifestWriter;
import nl.knaw.dans.sword2.core.exceptions.InvalidDepositException;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class BagItManagerImpl implements BagItManager {
    private static final Logger log = LoggerFactory.getLogger(BagItManagerImpl.class);

    private final FileService fileService;
    private final ChecksumCalculator checksumCalculator;

    public BagItManagerImpl(FileService fileService, ChecksumCalculator checksumCalculator) {
        this.fileService = fileService;
        this.checksumCalculator = checksumCalculator;
    }

    @Override
    public BagItMetaData getBagItMetaData(Path path, String depositId) throws InvalidDepositException {
        try {
            var bag = new BagReader().read(path);
            var metadata = new BagItMetaData();

            var swordToken = bag.getMetadata().get("Is-Version-Of");

            if (swordToken != null) {
                for (var token : swordToken) {
                    if (token.startsWith("urn:uuid:")) {
                        metadata.setSwordToken("sword:" + token.substring("urn:uuid:".length()));
                    }
                    else {
                        throw new InvalidDepositException(String.format("The deposit located at %s and ID %s has an invalid SWORD token: %s", path, depositId, token));
                    }
                }
            }
            else {
                metadata.setSwordToken("sword:" + depositId);
            }

            metadata.setOtherId(getMetadata(bag, "Has-Organizational-Identifier", ""));
            metadata.setOtherId(getMetadata(bag, "Has-Organizational-Identifier-Version", ""));

            return metadata;
        }
        catch (IOException | UnparsableVersionException | MaliciousPathException | UnsupportedAlgorithmException | InvalidBagitFileFormatException e) {
            throw new InvalidDepositException(String.format("The deposit located at %s and ID %s could not be correctly parsed", path, depositId), e);
        }
    }

    boolean isManifestFile(Path path) {
        return path.getFileName().toString().startsWith("manifest-");
    }

    List<Path> getManifestFiles(Path path) throws IOException {
        return fileService.listFiles(path).filter(this::isManifestFile).collect(Collectors.toList());
    }

    List<Pair<String, String>> parseManifestFile(Path path, Map<String, String> filePathMapping) throws IOException {
        return fileService.readLines(path).stream().map(line -> line.split("\\s+", 2)).filter(line -> line.length == 2).map(line -> Pair.of(line[0], line[1]))
            .map(item -> Pair.of(item.getKey(), filePathMapping.getOrDefault(item.getValue(), item.getValue()))).collect(Collectors.toList());
    }

    String formatFileOutput(Pair<String, String> pair) {
        return String.format("%s  %s", pair.getKey(), pair.getValue());
    }

    Bag getBag(Path path) throws MaliciousPathException, UnparsableVersionException, UnsupportedAlgorithmException, InvalidBagitFileFormatException, IOException {
        return new BagReader().read(path);
    }

    Path writeFilePathMapping(Path bagDir, Map<String, String> filePathMapping) throws IOException, InvalidDepositException {
        var output = bagDir.resolve("original-filepaths.txt");

        var content = filePathMapping.entrySet().stream().map(entry -> {
            var originalName = entry.getKey();
            var newName = entry.getValue();
            return String.format("%s  %s", newName, originalName);
        }).collect(Collectors.joining("\n"));

        return fileService.writeContentToFile(output, content);
    }

    Map<String, String> createRelativeFilePathMapping(Path bagDir, Map<String, String> filePathMapping) {
        var parent = bagDir.getFileName();
        var newMap = new HashMap<String, String>(filePathMapping.size());

        for (var entry : filePathMapping.entrySet()) {
            var key = parent.relativize(Path.of(entry.getKey())).toString();
            var value = parent.relativize(Path.of(entry.getValue())).toString();

            newMap.put(key, value);
        }

        return newMap;
    }

    @Override
    public void updateManifests(Path path, Map<String, String> filePathMapping) throws IOException, InvalidDepositException {
        if (filePathMapping.isEmpty()) {
            log.debug("No file path mapping entries, not renaming payload manifest entries");
            return;
        }

        var bagDir = getBagDir(path);
        var relativeFilePathMapping = createRelativeFilePathMapping(bagDir, filePathMapping);

        // write a file with file path mapping
        var originalFilePaths = writeFilePathMapping(bagDir, relativeFilePathMapping);

        // rewrite the manifest files to reference the renamed files
        writePayloadManifestFiles(bagDir, relativeFilePathMapping);

        // rewrite the tag manifest files
        writeTagManifestFiles(bagDir, originalFilePaths);
    }

    @Override
    public void verifyBagItRepository(Path path) throws InvalidDepositException {
        try {
            var bagDir = getBagDir(path);
            var bag = getBag(bagDir);
            var ignoreHiddenFiles = true;
            var verifier = new BagVerifier();

            log.trace("Verifying bag is complete on path {}", bagDir);
            verifier.isComplete(bag, ignoreHiddenFiles);

            log.trace("Verifying bag is valid on path {}", bagDir);
            verifier.isValid(bag, ignoreHiddenFiles);
        }
        catch (Exception e) {
            // not only the exception message (e.g. a file path) but also the exception class (e.g. FileNotFoundException)
            throw new InvalidDepositException(e.toString(), e);
        }
    }

    void writePayloadManifestFiles(Path bagDir, Map<String, String> filePathMapping) throws IOException {
        var files = getManifestFiles(bagDir);

        for (var file : files) {
            var fileList = parseManifestFile(file, filePathMapping);

            var outputContent = fileList.stream().map(this::formatFileOutput).collect(Collectors.joining("\n"));

            log.trace("Writing new payload manifest to path {}: {}", file, outputContent);
            fileService.writeContentToFile(file, outputContent);
        }
    }

    void writeTagManifestFiles(Path path, Path... extraFiles) {
        try {
            var bag = getBag(path);

            for (var manifest : bag.getTagManifests()) {
                var map = manifest.getFileToChecksumMap();
                var newMap = new HashMap<Path, String>();
                var algorithm = manifest.getAlgorithm().getMessageDigestName();

                for (var entry : map.entrySet()) {
                    newMap.put(entry.getKey(), checksumCalculator.calculateChecksum(entry.getKey(), algorithm));
                }

                for (var entry : extraFiles) {
                    newMap.put(entry, checksumCalculator.calculateChecksum(entry, algorithm));
                }

                manifest.setFileToChecksumMap(newMap);
            }

            log.trace("Writing new tag manifest to path {}: {}", path, bag.getTagManifests());
            ManifestWriter.writeTagManifests(bag.getTagManifests(), path, path, StandardCharsets.UTF_8);
        }
        catch (Exception e) {
            log.error("Unable to get bag", e);
        }
    }

    String getMetadata(Bag bag, String key, String defaultValue) {
        var data = bag.getMetadata().get(key);

        if (data == null) {
            return defaultValue;
        }

        return data.stream().findFirst().orElse(defaultValue);
    }

    @Override
    public Path getBagDir(Path path) throws IOException, InvalidDepositException {
        var files = fileService.listDirectories(path);

        if (files.size() != 1) {
            throw new InvalidDepositException(String.format("A deposit package must contain exactly one top-level directory, number found: %s", files.size()));
        }

        return files.get(0);
    }
}
