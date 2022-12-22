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

import nl.knaw.dans.sword2.core.Deposit;
import nl.knaw.dans.sword2.core.DepositState;
import nl.knaw.dans.sword2.core.auth.Depositor;
import nl.knaw.dans.sword2.core.config.CollectionConfig;
import nl.knaw.dans.sword2.core.config.UriRegistry;
import nl.knaw.dans.sword2.core.exceptions.CollectionNotFoundException;
import nl.knaw.dans.sword2.core.exceptions.DepositNotFoundException;
import nl.knaw.dans.sword2.core.exceptions.DepositReadOnlyException;
import nl.knaw.dans.sword2.core.exceptions.HashMismatchException;
import nl.knaw.dans.sword2.core.exceptions.InvalidContentTypeException;
import nl.knaw.dans.sword2.core.exceptions.InvalidDepositException;
import nl.knaw.dans.sword2.core.exceptions.InvalidPartialFileException;
import nl.knaw.dans.sword2.core.exceptions.InvalidSupportedBagPackagingException;
import nl.knaw.dans.sword2.core.exceptions.NotEnoughDiskSpaceException;
import nl.knaw.dans.sword2.core.finalizer.DepositFinalizerEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DepositHandlerImpl implements DepositHandler {

    private static final Logger log = LoggerFactory.getLogger(DepositHandlerImpl.class);

    private final BagExtractor bagExtractor;
    private final FileService fileService;
    private final DepositPropertiesManager depositPropertiesManager;
    private final CollectionManager collectionManager;
    private final UserManager userManager;
    private final BlockingQueue<DepositFinalizerEvent> depositFinalizerQueue;
    private final BagItManager bagItManager;
    private final FilesystemSpaceVerifier filesystemSpaceVerifier;
    private final String emailAddress;

    public DepositHandlerImpl(BagExtractor bagExtractor, FileService fileService, DepositPropertiesManager depositPropertiesManager, CollectionManager collectionManager,
        UserManager userManager, BlockingQueue<DepositFinalizerEvent> depositFinalizerQueue, BagItManager bagItManager, FilesystemSpaceVerifier filesystemSpaceVerifier, String emailAddress) {
        this.bagExtractor = bagExtractor;
        this.fileService = fileService;
        this.depositPropertiesManager = depositPropertiesManager;
        this.collectionManager = collectionManager;
        this.userManager = userManager;
        this.depositFinalizerQueue = depositFinalizerQueue;
        this.bagItManager = bagItManager;
        this.filesystemSpaceVerifier = filesystemSpaceVerifier;
        this.emailAddress = emailAddress;
    }

    @Override
    public Deposit createDepositWithPayload(String collectionId, Depositor depositor, boolean inProgress, MediaType contentType, String hash, String packaging, String filename, long filesize,
        InputStream inputStream) throws CollectionNotFoundException, IOException, NotEnoughDiskSpaceException, HashMismatchException, InvalidDepositException, InvalidSupportedBagPackagingException, InvalidContentTypeException {

        var id = UUID.randomUUID().toString();
        var collection = collectionManager.getCollectionByPath(collectionId, depositor);
        var path = collection.getUploads().resolve(id).resolve(filename);
        var depositFolder = path.getParent();

        try {
            // make sure the upload directory exists
            fileService.ensureDirectoriesExist(collection.getUploads());
            filesystemSpaceVerifier.assertDirHasEnoughDiskspaceMarginForFile(collection.getUploads(), collection.getDiskSpaceMargin(), filesize);

            // check if the hash matches the one provided by the user
            var calculatedHash = fileService.copyFileWithMD5Hash(inputStream, path);

            if (hash == null || !hash.equals(calculatedHash)) {
                throw new HashMismatchException(String.format("Hash %s does not match expected hash %s", calculatedHash, hash));
            }

            CheckContentError(contentType, packaging);

            var deposit = new Deposit();
            deposit.setId(id);
            deposit.setCollectionId(collection.getName());
            deposit.setInProgress(inProgress);
            deposit.setFilename(filename);
            deposit.setMd5(calculatedHash);
            deposit.setPackaging(packaging);
            deposit.setContentLength(filesize);
            deposit.setDepositor(depositor.getName());
            deposit.setState(DepositState.DRAFT);
            deposit.setStateDescription("Deposit is open for additional data");
            deposit.setCreated(OffsetDateTime.now());
            deposit.setMimeType(contentType.toString());

            // now store these properties
            // set state to draft
            depositPropertiesManager.saveProperties(depositFolder, deposit);

            startFinalizingDeposit(deposit);

            return deposit;
        }
        catch (HashMismatchException | IOException | InvalidDepositException | InvalidSupportedBagPackagingException | InvalidContentTypeException e) {
            // cleanup files
            cleanupFile(path);
            throw e;
        }
    }

    private void CheckContentError(MediaType contentType, String packaging) throws InvalidSupportedBagPackagingException, InvalidContentTypeException {
        if (!packaging.isEmpty() && !confirmPackageHeader(packaging))
            throw new InvalidSupportedBagPackagingException(String.format("Unsupported Media Type %s", packaging));
        if (!confirmContentType(contentType))
            throw new InvalidContentTypeException(String.format("Not Acceptable content type %s", contentType));
    }

    void cleanupFile(Path path) {
        log.info("Cleaning up file {}", path);

        try {
            fileService.deleteFile(path);
        }
        catch (IOException e) {
            log.error("Unable to clean up file {}", path, e);
        }
    }

    @Override
    public Deposit addPayloadToDeposit(String depositId, Depositor depositor, boolean inProgress, MediaType contentType, String hash, String packaging, String filename, long filesize,
        InputStream inputStream)
        throws IOException, NotEnoughDiskSpaceException, HashMismatchException, DepositNotFoundException, DepositReadOnlyException, CollectionNotFoundException, InvalidDepositException {

        var deposit = getDeposit(depositId, depositor);
        var path = deposit.getPath().resolve(filename);
        var collection = collectionManager.getCollectionByName(deposit.getCollectionId());

        filesystemSpaceVerifier.assertDirHasEnoughDiskspaceMarginForFile(path.getParent(), collection.getDiskSpaceMargin(), filesize);

        if (!DepositState.DRAFT.equals(deposit.getState())) {
            throw new DepositReadOnlyException(String.format("Deposit id %s is not in DRAFT state.", deposit.getId()));
        }

        // check if the hash matches the one provided by the user
        var calculatedHash = fileService.copyFileWithMD5Hash(inputStream, path);

        if (hash == null || !hash.equals(calculatedHash)) {
            throw new HashMismatchException(String.format("Hash %s does not match expected hash %s", calculatedHash, hash));
        }

        deposit.setInProgress(inProgress);
        depositPropertiesManager.saveProperties(path.getParent(), deposit);

        startFinalizingDeposit(deposit);
        return deposit;
    }

    @Override
    public Deposit getDeposit(String depositId, Depositor depositor) throws DepositNotFoundException, InvalidDepositException {
        var deposit = getDeposit(depositId);

        if (depositor.getName().equals(deposit.getDepositor())) {
            return deposit;
        }

        throw new DepositNotFoundException(String.format("Deposit with id %s could not be found", depositId));
    }

    @Override
    public Deposit getDeposit(String depositId) throws DepositNotFoundException, InvalidDepositException {
        var collections = collectionManager.getCollections();

        for (var collection : collections) {
            var basePaths = new ArrayList<Path>();
            basePaths.add(collection.getUploads());
            basePaths.add(collection.getDeposits());

            if (collection.getDepositTrackingPath() != null) {
                basePaths.addAll(collection.getDepositTrackingPath());
            }

            for (var path : basePaths) {
                var depositPath = path.resolve(depositId);
                var exists = fileService.exists(depositPath);

                log.trace("Checking if {} exists (answer: {})", depositPath, exists);

                if (exists) {
                    var deposit = depositPropertiesManager.getProperties(depositPath);
                    deposit.setPath(depositPath);
                    deposit.setCollectionId(collection.getName());

                    return deposit;
                }
            }
        }

        throw new DepositNotFoundException(String.format("Deposit with id %s could not be found", depositId));
    }

    @Override
    public List<Deposit> getOpenDeposits() {
        return collectionManager.getCollections().stream().map(collection -> {
                try {
                    return fileService.listDirectories(collection.getUploads()).stream().map(path -> {
                        try {
                            var deposit = depositPropertiesManager.getProperties(path);
                            deposit.setPath(path);
                            deposit.setCollectionId(collection.getName());

                            return deposit;
                        }
                        catch (Exception | InvalidDepositException e) {
                            log.error("Unable to open deposit from path {}", path, e);
                        }

                        return null;
                    }).filter(Objects::nonNull);
                }
                catch (IOException e) {
                    log.error("Unable to list directories in path {}", collection.getUploads());
                }

                return null;
            })
            .filter(Objects::nonNull)
            .flatMap(s -> s)
            .filter(deposit -> DepositState.UPLOADED.equals(deposit.getState()) || DepositState.FINALIZING.equals(deposit.getState()))
            .collect(Collectors.toList());
    }

    void startFinalizingDeposit(Deposit deposit) throws CollectionNotFoundException, InvalidDepositException {
        // if deposit is not in progress
        if (deposit.isInProgress()) {
            log.info("Deposit is still in progress, not finalizing");
            return;
        }

        log.info("Finalizing deposit with id {}", deposit.getId());

        var collection = collectionManager.getCollectionByName(deposit.getCollectionId());
        var path = getUploadPath(collection, deposit.getId());

        // set state to UPLOADED
        deposit.setState(DepositState.UPLOADED);
        depositPropertiesManager.saveProperties(path, deposit);

        try {
            depositFinalizerQueue.put(new DepositFinalizerEvent(deposit.getId()));
        }
        catch (InterruptedException e) {
            log.error("Interrupted while putting task on queue", e);
        }
    }

    @Override
    public Deposit finalizeDeposit(String depositId)
        throws DepositNotFoundException, IOException, NotEnoughDiskSpaceException, InvalidDepositException, InvalidPartialFileException, CollectionNotFoundException {

        try {
            var deposit = getDeposit(depositId);
            var path = deposit.getPath();
            var depositor = userManager.getDepositorById(deposit.getDepositor());

            log.info("Finalizing deposit with id {}", depositId);
            deposit.setState(DepositState.FINALIZING);
            deposit.setStateDescription("Finalizing deposit");
            depositPropertiesManager.saveProperties(path, deposit);

            var collection = collectionManager.getCollectionByName(deposit.getCollectionId());

            log.info("Extracting files for deposit {}", depositId);
            bagExtractor.extractBag(path,
                collection.getDiskSpaceMargin(),
                deposit.getMimeType(),
                depositor.getFilepathMapping());

            var bagDir = bagExtractor.getBagDir(path);
            log.info("Bag dir found, it is named {}", bagDir);

            deposit.setState(DepositState.SUBMITTED);
            deposit.setStateDescription("Deposit is valid and ready for post-submission processing");
            deposit.setBagName(bagDir.getFileName().toString());
            deposit.setMimeType(null);

            var metadata = bagItManager.getBagItMetaData(path.resolve(deposit.getBagName()), depositId);
            deposit.setSwordToken(metadata.getSwordToken());
            deposit.setOtherId(metadata.getOtherId());
            deposit.setOtherIdVersion(metadata.getOtherIdVersion());

            depositPropertiesManager.saveProperties(path, deposit);

            removeZipFiles(path);

            var targetPath = getDepositPath(collection, depositId);
            fileService.move(path, targetPath);

            return deposit;
        }
        catch (InvalidDepositException | InvalidPartialFileException e) {
            setDepositToInvalid(depositId, e.getMessage());
            throw e;
        }
        catch (CollectionNotFoundException e) {
            setDepositToFailed(depositId, getGenericErrorMessage(depositId));
            throw e;
        }
        catch (NotEnoughDiskSpaceException e) {
            setDepositToRetrying(depositId);
            throw e;
        }
    }

    void cleanupDepositFiles(Deposit deposit, DepositState state) throws CollectionNotFoundException {
        var collection = collectionManager.getCollectionByName(deposit.getCollectionId());

        if (!collection.getAutoClean().contains(state)) {
            log.trace("Cleanup for state {} is not allowed; only cleaning up for state(s) {}", state, collection.getAutoClean());
            return;
        }

        log.info("Cleaning up zip files and bag directory for deposit {} due to state {}", deposit.getId(), deposit.getPath());

        try {
            removeZipFiles(deposit.getPath());

            var directories = fileService.listDirectories(deposit.getPath());

            for (var directory : directories) {
                log.debug("Deleting directory {} because of state {}", directory, state);

                try {
                    fileService.deleteDirectory(directory);
                }
                catch (IOException e) {
                    log.error("Unable to delete directory {}", directory, e);
                }
            }
        }
        catch (IOException e) {
            log.error("Unable to clean path {} because of IOException", deposit.getPath(), e);
        }
    }

    String getGenericErrorMessage(String depositId) {
        var timestamp = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        return String.format("The server encountered an unexpected condition. "
            + "Please contact the SWORD service administrator at %s. "
            + "The error occured at timestamp %s. Your 'DepositID' is %s", this.emailAddress, timestamp, depositId);
    }

    void setDepositToInvalid(String depositId, String message) throws InvalidDepositException, DepositNotFoundException, CollectionNotFoundException {
        var deposit = getDeposit(depositId);

        try {
            var path = deposit.getPath();

            log.info("Marking deposit with id {} as INVALID; reason: {}", depositId, message);
            deposit.setState(DepositState.INVALID);
            deposit.setStateDescription(message);
            depositPropertiesManager.saveProperties(path, deposit);
        }
        finally {
            cleanupDepositFiles(deposit, DepositState.INVALID);
        }
    }

    void setDepositToRetrying(String depositId) throws InvalidDepositException, DepositNotFoundException, CollectionNotFoundException {
        var deposit = getDeposit(depositId);

        try {
            var path = deposit.getPath();

            log.info("Rescheduling deposit with id {}", depositId);
            deposit.setState(DepositState.UPLOADED);
            deposit.setStateDescription("Rescheduled, waiting for more disk space");
            depositPropertiesManager.saveProperties(path, deposit);
        }
        finally {
            cleanupDepositFiles(deposit, DepositState.UPLOADED);
        }
    }

    void setDepositToFailed(String depositId, String message) throws InvalidDepositException, DepositNotFoundException, CollectionNotFoundException {
        var deposit = getDeposit(depositId);

        try {
            var path = deposit.getPath();

            log.info("Marking deposit with id {} as FAILED; reason: {}", depositId, message);
            deposit.setState(DepositState.FAILED);
            deposit.setStateDescription(message);
            depositPropertiesManager.saveProperties(path, deposit);
        }
        finally {
            cleanupDepositFiles(deposit, DepositState.FAILED);
        }
    }

    private Stream<Path> getDepositFiles(Path path) throws IOException {
        return fileService.listFiles(path).filter(f -> !f.getFileName().equals(Path.of("deposit.properties")));
    }

    private void removeZipFiles(Path path) throws IOException {
        var files = getDepositFiles(path).collect(Collectors.toList());

        for (var file : files) {
            try {
                log.debug("Deleting zip file {}", file);
                fileService.deleteFile(file);
            }
            catch (IOException e) {
                log.warn("Unable to remove file {}", file, e);
            }
        }
    }

    private Path getDepositPath(CollectionConfig collectionConfig, String id) {
        return collectionConfig.getDeposits().resolve(id);
    }

    private Path getUploadPath(CollectionConfig collectionConfig, String id) {
        return collectionConfig.getUploads().resolve(id);
    }

    private boolean confirmPackageHeader(String packageHeader) {
        return UriRegistry.PACKAGE_BAGIT.equals(packageHeader) ;
    }

    private boolean confirmContentType(MediaType contentType) {
        return UriRegistry.supportedContentType.contains(contentType);
    }
}
