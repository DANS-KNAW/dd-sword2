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

import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

public class ChecksumCalculatorImpl implements
    ChecksumCalculator {

    @Override
    public String calculateChecksum(Path path, String algorithm)
        throws NoSuchAlgorithmException, IOException {

        var md = MessageDigest.getInstance(algorithm);
        var is = Files.newInputStream(path);
        var buf = new byte[1024 * 8];

        var bytesRead = 0;

        while ((bytesRead = is.read(buf)) != -1) {
            md.update(buf, 0, bytesRead);
        }

        return DatatypeConverter.printHexBinary(md.digest())
            .toLowerCase(Locale.ROOT);
    }
}

