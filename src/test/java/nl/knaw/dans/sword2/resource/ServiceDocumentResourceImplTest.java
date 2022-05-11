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
package nl.knaw.dans.sword2.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import java.io.StringWriter;
import java.net.URI;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import nl.knaw.dans.sword2.DdSword2Application;
import nl.knaw.dans.sword2.DdSword2Configuration;
import nl.knaw.dans.sword2.api.service.ServiceDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(DropwizardExtensionsSupport.class)
class ServiceDocumentResourceImplTest {

    private static DropwizardAppExtension<DdSword2Configuration> EXT = new DropwizardAppExtension<>(
        DdSword2Application.class,
        ResourceHelpers.resourceFilePath("debug-etc/config-servicedocument.yml")
    );

    @Test
    void getServiceDocument() throws JAXBException {
        var url = String.format("http://localhost:%s/servicedocument", EXT.getLocalPort());
        var result = EXT.client()
            .target(url)
            .request()
            .header("authorization", "Basic dXNlcjAwMTp1c2VyMDAx")
            .get();

        var serviceDocument = result.readEntity(ServiceDocument.class);
        printServiceDocument(serviceDocument);

        assertEquals("2.0", serviceDocument.getVersion());
        assertEquals(1,
            serviceDocument.getWorkspaces()
                .size());

        var workspace = serviceDocument.getWorkspaces().get(0);

        assertEquals("EASY SWORD2 Deposit Service", workspace.getTitle());
        assertEquals(2, workspace.getCollections().size());
        var collection1 = workspace.getCollections().get(0);
        var collection2 = workspace.getCollections().get(1);

        assertEquals("collection1", collection1.getTitle());
        assertEquals(URI.create("http://localhost:20320/collection/1"), collection1.getHref());
        assertFalse(collection1.isMediation());
        assertEquals("http://purl.org/net/sword/package/BagIt", collection1.getAcceptPackaging());

        assertEquals("collection2", collection2.getTitle());
        assertEquals(URI.create("http://localhost:20320/collection/2"), collection2.getHref());
        assertFalse(collection2.isMediation());
        assertEquals("http://purl.org/net/sword/package/BagIt", collection2.getAcceptPackaging());

        var checksum = result.getHeaderString("Content-MD5");
        assertEquals("1c62de3fac16661d834bd3c4b6318c96", checksum);
    }

    void printServiceDocument(ServiceDocument serviceDocument) throws JAXBException {
        var ctx = JAXBContext.newInstance(ServiceDocument.class);
        var marshaller = ctx.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        var writer = new StringWriter();
        marshaller.marshal(serviceDocument, writer);
        var str = writer.toString();
        System.out.println(str);
    }
}
