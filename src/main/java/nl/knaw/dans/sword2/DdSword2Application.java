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
import io.dropwizard.client.HttpClientBuilder;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.forms.MultiPartBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import nl.knaw.dans.sword2.core.auth.Depositor;
import nl.knaw.dans.sword2.core.auth.SwordAuthenticator;
import nl.knaw.dans.sword2.core.finalizer.DepositFinalizerEvent;
import nl.knaw.dans.sword2.core.finalizer.DepositFinalizerManager;
import nl.knaw.dans.sword2.core.service.BagExtractorImpl;
import nl.knaw.dans.sword2.core.service.BagItManagerImpl;
import nl.knaw.dans.sword2.core.service.ChecksumCalculatorImpl;
import nl.knaw.dans.sword2.core.service.CollectionManagerImpl;
import nl.knaw.dans.sword2.core.service.DepositHandlerImpl;
import nl.knaw.dans.sword2.core.service.DepositPropertiesManagerImpl;
import nl.knaw.dans.sword2.core.service.DepositReceiptFactoryImpl;
import nl.knaw.dans.sword2.core.service.ErrorResponseFactoryImpl;
import nl.knaw.dans.sword2.core.service.FileServiceImpl;
import nl.knaw.dans.sword2.core.service.FilesystemSpaceVerifierImpl;
import nl.knaw.dans.sword2.core.service.UserManagerImpl;
import nl.knaw.dans.sword2.core.service.ZipServiceImpl;
import nl.knaw.dans.sword2.health.DiskSpaceHealthCheck;
import nl.knaw.dans.sword2.health.ExecutorQueueHealthCheck;
import nl.knaw.dans.sword2.health.FileSystemPermissionHealthCheck;
import nl.knaw.dans.sword2.health.QueueHealthCheck;
import nl.knaw.dans.sword2.health.UploadDepositOnSameFileSystemHealthCheck;
import nl.knaw.dans.sword2.resources.CollectionResourceImpl;
import nl.knaw.dans.sword2.resources.ContainerResourceImpl;
import nl.knaw.dans.sword2.resources.HashHeaderInterceptor;
import nl.knaw.dans.sword2.resources.ServiceDocumentResourceImpl;
import nl.knaw.dans.sword2.resources.StatementResourceImpl;
import org.glassfish.jersey.media.multipart.MultiPartFeature;

import java.util.concurrent.ArrayBlockingQueue;

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
        bootstrap.addBundle(new MultiPartBundle());
        bootstrap.setConfigurationSourceProvider(new SubstitutingSourceProvider(bootstrap.getConfigurationSourceProvider(),
                new EnvironmentVariableSubstitutor(false)));
    }

    @Override
    public void run(final DdSword2Configuration configuration, final Environment environment) throws Exception {
        var fileService = new FileServiceImpl();
        var depositPropertiesManager = new DepositPropertiesManagerImpl();
        var checksumCalculator = new ChecksumCalculatorImpl();
        var filesystemSpaceVerifier = new FilesystemSpaceVerifierImpl(fileService);

        var errorResponseFactory = new ErrorResponseFactoryImpl();

        var bagItManager = new BagItManagerImpl(fileService, checksumCalculator);
        var userManager = new UserManagerImpl(configuration.getUsers());

        var finalizingExecutor = configuration.getSword2().getFinalizingQueue().build(environment);
        var rescheduleExecutor = configuration.getSword2().getRescheduleQueue().build(environment);

        var queue = new ArrayBlockingQueue<DepositFinalizerEvent>(configuration.getSword2().getFinalizingQueue().getMaxQueueSize());

        var collectionManager = new CollectionManagerImpl(configuration.getSword2().getCollections());

        var zipService = new ZipServiceImpl(fileService);

        var bagExtractor = new BagExtractorImpl(zipService, fileService, bagItManager, filesystemSpaceVerifier);
        var depositHandler = new DepositHandlerImpl(bagExtractor, fileService, depositPropertiesManager, collectionManager, userManager, queue, bagItManager,
            filesystemSpaceVerifier, configuration.getSword2().getEmailAddress());

        var depositReceiptFactory = new DepositReceiptFactoryImpl(configuration.getSword2().getBaseUrl());

        var depositFinalizerManager = new DepositFinalizerManager(finalizingExecutor, depositHandler, queue, rescheduleExecutor, configuration.getSword2().getRescheduleDelay());

        var httpClient = new HttpClientBuilder(environment).using(configuration.getHttpClientConfiguration())
            .build(getName());

        environment.jersey().register(MultiPartFeature.class);

        // Add a md5 output hash header
        environment.jersey().register(HashHeaderInterceptor.class);

        // Set up authentication
        environment.jersey().register(
            new AuthDynamicFeature(new BasicCredentialAuthFilter.Builder<Depositor>().setAuthenticator(new SwordAuthenticator(configuration.getUsers(), httpClient)).setRealm("SWORD2").buildAuthFilter()));

        // For @Auth
        environment.jersey().register(new AuthValueFactoryProvider.Binder<>(Depositor.class));

        // Managed classes
        environment.lifecycle().manage(depositFinalizerManager);

        // Resources
        environment.jersey().register(new CollectionResourceImpl(depositHandler, depositReceiptFactory, errorResponseFactory));

        environment.jersey().register(new ContainerResourceImpl(depositReceiptFactory, depositHandler, errorResponseFactory));

        environment.jersey().register(new StatementResourceImpl(configuration.getSword2().getBaseUrl(), depositHandler, errorResponseFactory));

        environment.jersey().register(new ServiceDocumentResourceImpl(configuration.getSword2().getCollections(), configuration.getSword2().getBaseUrl()));

        // Health checks
        var collections = configuration.getSword2().getCollections();
        environment.healthChecks().register("DiskSpace", new DiskSpaceHealthCheck(collections, filesystemSpaceVerifier));
        environment.healthChecks().register("UploadDepositIsOnSameFileSystem", new UploadDepositOnSameFileSystemHealthCheck(collections, fileService));
        environment.healthChecks().register("FileSystemPermissions", new FileSystemPermissionHealthCheck(collections, fileService));
        environment.healthChecks().register("FinalizerQueue", new QueueHealthCheck(queue));
        environment.healthChecks().register("FinalizingExecutor", new ExecutorQueueHealthCheck(finalizingExecutor));
        environment.healthChecks().register("RescheduleExecutor", new ExecutorQueueHealthCheck(rescheduleExecutor));
    }
}
