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

import io.dropwizard.Application;
import io.dropwizard.auth.AuthDynamicFeature;
import io.dropwizard.auth.AuthValueFactoryProvider;
import io.dropwizard.auth.basic.BasicCredentialAuthFilter;
import io.dropwizard.forms.MultiPartBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import java.util.concurrent.ArrayBlockingQueue;
import nl.knaw.dans.sword2.auth.Depositor;
import nl.knaw.dans.sword2.auth.SwordAuthenticator;
import nl.knaw.dans.sword2.resource.CollectionHandlerImpl;
import nl.knaw.dans.sword2.resource.ContainerHandlerImpl;
import nl.knaw.dans.sword2.resource.ServiceDocumentHandlerImpl;
import nl.knaw.dans.sword2.resource.StatementHandlerImpl;
import nl.knaw.dans.sword2.service.BagExtractorImpl;
import nl.knaw.dans.sword2.service.ChecksumCalculatorImpl;
import nl.knaw.dans.sword2.service.CollectionManagerImpl;
import nl.knaw.dans.sword2.service.DepositHandlerImpl;
import nl.knaw.dans.sword2.service.DepositPropertiesManagerImpl;
import nl.knaw.dans.sword2.service.DepositReceiptFactoryImpl;
import nl.knaw.dans.sword2.service.FileServiceImpl;
import nl.knaw.dans.sword2.service.UserManagerImpl;
import nl.knaw.dans.sword2.service.ZipServiceImpl;
import nl.knaw.dans.sword2.service.finalizer.DepositFinalizerEvent;
import nl.knaw.dans.sword2.service.finalizer.DepositFinalizerManager;
import org.glassfish.jersey.media.multipart.MultiPartFeature;

public class DdSword2Application extends Application<DdSword2Configuration> {

    public static void main(final String[] args) throws Exception {
        new DdSword2Application().run(args);
    }

    @Override
    public String getName() {
        return "Dd Sword2";
    }

    @Override
    public void initialize(final Bootstrap<DdSword2Configuration> bootstrap) {
        // TODO: application initialization
        bootstrap.addBundle(new MultiPartBundle());

    }

    @Override
    public void run(final DdSword2Configuration configuration, final Environment environment)
        throws Exception {
        var fileService = new FileServiceImpl();
        var depositPropertiesManager = new DepositPropertiesManagerImpl(configuration.getSword2());
        var checksumCalculator = new ChecksumCalculatorImpl();

        var userManager = new UserManagerImpl(configuration.getUsers());

        var executorService = configuration.getSword2()
            .getFinalizingQueue()
            .build(environment);

        var queue = new ArrayBlockingQueue<DepositFinalizerEvent>(configuration.getSword2()
            .getFinalizingQueue()
            .getMaxQueueSize());

        var collectionManager = new CollectionManagerImpl(configuration.getSword2()
            .getCollections());

        var zipService = new ZipServiceImpl(fileService);

        var bagExtractor = new BagExtractorImpl(zipService, fileService);
        var depositHandler = new DepositHandlerImpl(configuration.getSword2(),
            bagExtractor,
            fileService,
            depositPropertiesManager, collectionManager, userManager, queue);

        var depositReceiptFactory = new DepositReceiptFactoryImpl(configuration.getSword2()
            .getBaseUrl());

        var depositFinalizerManager = new DepositFinalizerManager(executorService,
            depositHandler,
            queue);

        environment.jersey()
            .register(MultiPartFeature.class);
        // Set up authentication
        environment.jersey()
            .register(new AuthDynamicFeature(
                new BasicCredentialAuthFilter.Builder<Depositor>()
                    .setAuthenticator(new SwordAuthenticator(configuration.getUsers()))
                    .setRealm("SWORD2")
                    .buildAuthFilter()));

        // For @Auth
        environment.jersey()
            .register(new AuthValueFactoryProvider.Binder<>(Depositor.class));

        // Managed classes
        environment.lifecycle()
            .manage(depositFinalizerManager);

        // Resources
        environment.jersey()
            .register(new CollectionHandlerImpl(depositHandler, depositReceiptFactory,
                checksumCalculator));

        environment.jersey()
            .register(new ContainerHandlerImpl(depositReceiptFactory, depositHandler));

        environment.jersey()
            .register(new StatementHandlerImpl(configuration.getSword2()
                .getBaseUrl(), depositHandler));

        environment.jersey()
            .register(new ServiceDocumentHandlerImpl(configuration.getUsers(),
                configuration.getSword2()
                    .getCollections(),
                configuration.getSword2()
                    .getBaseUrl()));
    }
}
