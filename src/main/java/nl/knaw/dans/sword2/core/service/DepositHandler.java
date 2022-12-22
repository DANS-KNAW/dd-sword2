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
import nl.knaw.dans.sword2.core.auth.Depositor;
import nl.knaw.dans.sword2.core.exceptions.CollectionNotFoundException;
import nl.knaw.dans.sword2.core.exceptions.DepositNotFoundException;
import nl.knaw.dans.sword2.core.exceptions.DepositReadOnlyException;
import nl.knaw.dans.sword2.core.exceptions.HashMismatchException;
import nl.knaw.dans.sword2.core.exceptions.InvalidContentTypeException;
import nl.knaw.dans.sword2.core.exceptions.InvalidDepositException;
import nl.knaw.dans.sword2.core.exceptions.InvalidPartialFileException;
import nl.knaw.dans.sword2.core.exceptions.InvalidSupportedBagPackagingException;
import nl.knaw.dans.sword2.core.exceptions.NotEnoughDiskSpaceException;

import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public interface DepositHandler {

    Deposit getDeposit(String depositId, Depositor depositor) throws DepositNotFoundException, InvalidDepositException;

    Deposit getDeposit(String depositId) throws DepositNotFoundException, InvalidDepositException;

    List<Deposit> getOpenDeposits();

    Deposit createDepositWithPayload(String collectionId, Depositor depositor, boolean inProgress, MediaType contentType, String hash, String packaging, String filename, long filesize,
        InputStream inputStream)
        throws CollectionNotFoundException, IOException, NotEnoughDiskSpaceException, HashMismatchException, InvalidDepositException, InvalidSupportedBagPackagingException, InvalidContentTypeException;

    Deposit addPayloadToDeposit(String depositId, Depositor depositor, boolean inProgress, MediaType contentType, String hash, String packaging, String filename, long filesize,
        InputStream inputStream)
        throws CollectionNotFoundException, IOException, NotEnoughDiskSpaceException, HashMismatchException, DepositNotFoundException, DepositReadOnlyException, InvalidDepositException;

    Deposit finalizeDeposit(String depositId)
        throws DepositNotFoundException, InvalidDepositException, InvalidPartialFileException, CollectionNotFoundException, IOException, NotEnoughDiskSpaceException;

}

