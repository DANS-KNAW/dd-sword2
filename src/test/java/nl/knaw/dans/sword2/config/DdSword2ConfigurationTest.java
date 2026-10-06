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
package nl.knaw.dans.sword2.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import io.dropwizard.configuration.ConfigurationFactory;
import io.dropwizard.configuration.YamlConfigurationFactory;
import io.dropwizard.jackson.Jackson;
import org.junit.jupiter.api.Test;

import javax.validation.Validation;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class DdSword2ConfigurationTest {

    private DdSword2Configuration parseConfigurationFile(String configFile) throws Exception {
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            ConfigurationFactory<DdSword2Configuration> factory =
                new YamlConfigurationFactory<>(DdSword2Configuration.class,
                    validatorFactory.getValidator(),
                    Jackson.newObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true), "dw");

            return factory.build(Path.of(configFile).toFile());
        }
    }

    @Test
    public void default_configuration_can_be_parsed() throws Exception {
        var defaultConfigFile = Path.of("src/main/assembly/dist/cfg/config.yml").toFile();
        var configuration = parseConfigurationFile(defaultConfigFile.getAbsolutePath());
        assertThat(configuration).isNotNull();
        assertThat(configuration.getSword2().getCollections().get(0).getDiskSpaceMargin().toGigabytes()).isEqualTo(2);
    }

    @Test
    public void debug_etc_configuration_can_be_parsed() throws Exception {
        var devConfigFile = Path.of("src/test/resources/debug-etc/config.yml").toFile();
        var configuration = parseConfigurationFile(devConfigFile.getAbsolutePath());
        assertThat(configuration).isNotNull();
        assertThat(configuration.getSword2().getCollections().get(0).getDiskSpaceMargin().toGigabytes()).isEqualTo(2);
    }
}
