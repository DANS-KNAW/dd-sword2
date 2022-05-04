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

import nl.knaw.dans.sword2.auth.Depositor;
import nl.knaw.dans.sword2.exceptions.DepositNotFoundException;
import nl.knaw.dans.sword2.models.entry.Link;
import nl.knaw.dans.sword2.models.statement.Feed;
import nl.knaw.dans.sword2.models.statement.FeedAuthor;
import nl.knaw.dans.sword2.models.statement.FeedCategory;
import nl.knaw.dans.sword2.models.statement.TextElement;
import nl.knaw.dans.sword2.service.DepositHandler;
import nl.knaw.dans.sword2.service.ErrorResponseFactory;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.net.URI;

public class StatementHandlerImpl extends BaseHandler implements StatementHandler {
    private final URI baseUrl;
    private final DepositHandler depositHandler;

    public StatementHandlerImpl(URI baseUrl, DepositHandler depositHandler, ErrorResponseFactory errorResponseFactory) {
        super(errorResponseFactory);
        this.baseUrl = baseUrl;
        this.depositHandler = depositHandler;
    }

    @Override
    public Response getStatement(String depositId, HttpHeaders headers, Depositor depositor) {
        try {
            var deposit = depositHandler.getDeposit(depositId, depositor);
            var url = baseUrl.resolve("/statement/" + depositId).toString();
            var feed = new Feed();

            feed.setId(url);
            feed.setTitle(new TextElement(String.format("Deposit %s", depositId), "text"));
            feed.setUpdated(deposit.getCreated().toString());
            feed.addLink(new Link(URI.create(url), "self", null));
            feed.setCategory(new FeedCategory("State", "http://purl.org/net/sword/terms/state",
                deposit.getState().toString(), deposit.getStateDescription()));
            feed.getAuthors().add(new FeedAuthor("DANS-EASY"));

            return Response.status(Response.Status.OK)
                .entity(feed)
                .build();
        }
        catch (DepositNotFoundException e) {
            throw new WebApplicationException(404);
        }
    }
}
