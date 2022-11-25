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
import nl.knaw.dans.sword2.core.exceptions.InvalidDepositException;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.FileBasedConfiguration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.ex.ConfigurationException;

import javax.inject.Singleton;
import java.nio.file.Path;
import java.time.OffsetDateTime;

@Singleton
public class DepositPropertiesManagerImpl implements DepositPropertiesManager {
    private static final String FILENAME = "deposit.properties";

    public DepositPropertiesManagerImpl() {
    }

    private Path getDepositPath(Path path) {
        return path.resolve(FILENAME);
    }

    @Override
    public void saveProperties(Path path, Deposit deposit) throws InvalidDepositException {
        var propertiesFile = getDepositPath(path);

        var params = new Parameters();
        var paramConfig = params.properties()
            .setFileName(propertiesFile.toString());

        var builder = new FileBasedConfigurationBuilder<FileBasedConfiguration>(PropertiesConfiguration.class, null, true).configure(
            paramConfig);

        try {
            var config = builder.getConfiguration();
            mapToConfig(config, deposit);
            builder.save();
        }
        catch (ConfigurationException cex) {
            throw new InvalidDepositException("Unable to save deposit properties", cex);
        }
    }

    @Override
    public Deposit getProperties(Path path) throws InvalidDepositException {
        var propertiesFile = getDepositPath(path);
        var params = new Parameters();
        var paramConfig = params.properties()
            .setFileName(propertiesFile.toString());

        var builder = new FileBasedConfigurationBuilder<FileBasedConfiguration>(PropertiesConfiguration.class, null, true).configure(
            paramConfig);

        try {
            var config = builder.getConfiguration();
            return mapToDeposit(config);
        } catch (ConfigurationException e) {
            throw new IllegalStateException("Could not load properties file", e);
        }
    }

    Deposit mapToDeposit(Configuration config) {
        var deposit = new Deposit();
        deposit.setId(config.getString("bag-store.bag-id"));
        deposit.setCreated(OffsetDateTime.parse(config.getString("creation.timestamp")));
        deposit.setDepositor(config.getString("depositor.userId"));
        deposit.setState(DepositState.valueOf(config.getString("state.label")));
        deposit.setStateDescription(config.getString("state.description"));
        deposit.setBagName(config.getString("bag-store.bag-name"));
        deposit.setSwordToken(config.getString("dataverse.sword-token"));
        deposit.setMimeType(config.getString("easy-sword2.client-message.content-type"));

        return deposit;
    }

    void mapToConfig(Configuration config, Deposit deposit) {
        config.setProperty("bag-store.bag-id", deposit.getId());
        config.setProperty("dataverse.bag-id", String.format("urn:uuid:%s", deposit.getId()));
        config.setProperty("creation.timestamp", deposit.getCreated());
        config.setProperty("deposit.origin", "SWORD2");
        config.setProperty("depositor.userId", deposit.getDepositor());
        config.setProperty("state.label", deposit.getState().toString());
        config.setProperty("state.description", deposit.getStateDescription());
        config.setProperty("bag-store.bag-name", deposit.getBagName());
        config.setProperty("dataverse.sword-token", deposit.getSwordToken());

        if (deposit.getOtherId() != null && !deposit.getOtherId().isEmpty()) {
            config.setProperty("dataverse.other-id", deposit.getOtherId());
        }

        if (deposit.getOtherIdVersion() != null && !deposit.getOtherIdVersion().isEmpty()) {
            config.setProperty("dataverse.other-id-version", deposit.getOtherIdVersion());
        }

        if (deposit.getMimeType() != null) {
            config.setProperty("easy-sword2.client-message.content-type", deposit.getMimeType());
        }
        else {
            config.clearProperty("easy-sword2.client-message.content-type");
        }
    }
}
