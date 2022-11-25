package nl.knaw.dans.sword2;

import io.dropwizard.configuration.FileConfigurationSourceProvider;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import org.apache.commons.text.StringSubstitutor;

import java.util.Collections;

public abstract class TestFixtureExt extends TestFixture {

    protected final DropwizardAppExtension<DdSword2Configuration> EXT;

    protected TestFixtureExt(String configYml) {
        EXT = new DropwizardAppExtension<>(
            DdSword2Application.class,
            ResourceHelpers.resourceFilePath(configYml),
            new SubstitutingSourceProvider(new FileConfigurationSourceProvider(), new StringSubstitutor(Collections.singletonMap("TEST_DIR", testDir.toString()))));
           }
}
