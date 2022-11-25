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
package nl.knaw.dans.sword2;

import io.dropwizard.configuration.FileConfigurationSourceProvider;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import org.apache.commons.text.StringSubstitutor;

import java.util.Collections;

/**
 * Fixture for integration tests that run the whole dropwizard app using the DropwizardAppExtension.
 */
public abstract class TestFixtureExt extends TestFixture {

    protected final DropwizardAppExtension<DdSword2Configuration> EXT;

    /**
     * Initializes the app with the giving config.yml. The variable <code>${TEST_DIR}</code> can be used in config.yml to resolve to <code>testDir</code>.
     *
     * @param configYml the config.yml to use
     */
    protected TestFixtureExt(String configYml) {
        EXT = new DropwizardAppExtension<>(
            DdSword2Application.class,
            ResourceHelpers.resourceFilePath(configYml),
            new SubstitutingSourceProvider(new FileConfigurationSourceProvider(), new StringSubstitutor(Collections.singletonMap("TEST_DIR", testDir.toString()))));
    }
}
