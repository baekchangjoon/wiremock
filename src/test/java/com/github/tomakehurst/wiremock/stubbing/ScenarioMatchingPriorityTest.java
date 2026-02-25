/*
 * Copyright (C) 2026 Thomas Akehurst
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
package com.github.tomakehurst.wiremock.stubbing;

import static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder.responseDefinition;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.http.RequestMethod.GET;
import static com.github.tomakehurst.wiremock.http.RequestMethod.PUT;
import static com.github.tomakehurst.wiremock.matching.RequestPatternBuilder.newRequestPattern;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static com.github.tomakehurst.wiremock.testsupport.MockRequestBuilder.aRequest;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ScenarioMatchingPriorityTest {

  private StoreBackedStubMappings mappings;

  @BeforeEach
  public void init() {
    mappings = new InMemoryStubMappings();
  }

  @Test
  public void scenarioMatchedStubShouldBePriorOverNonScenarioStub() {
    // Non-scenario stub with high priority (priority=1)
    mappings.addMapping(
        StubMapping.builder()
            .setRequest(newRequestPattern(GET, urlEqualTo("/resource")).build())
            .setResponse(responseDefinition().withStatus(200).withBody("non-scenario").build())
            .setPriority(1)
            .build());

    // Scenario stub with low priority (priority=10) in STARTED state
    mappings.addMapping(
        StubMapping.builder()
            .setRequest(newRequestPattern(GET, urlEqualTo("/resource")).build())
            .setResponse(responseDefinition().withStatus(200).withBody("scenario-started").build())
            .setPriority(10)
            .setScenarioName("TestScenario")
            .setRequiredScenarioState(STARTED)
            .build());

    Request request = aRequest().withMethod(GET).withUrl("/resource").build();
    ResponseDefinition response =
        mappings.serveFor(ServeEvent.of(request)).getResponseDefinition();

    // Scenario-matched stub should win despite lower priority
    assertThat(response.getBody(), is("scenario-started"));
  }

  @Test
  public void nonScenarioStubSelectedWhenScenarioStateDoesNotMatch() {
    // Non-scenario stub
    mappings.addMapping(
        StubMapping.builder()
            .setRequest(newRequestPattern(GET, urlEqualTo("/resource")).build())
            .setResponse(responseDefinition().withStatus(200).withBody("non-scenario").build())
            .setPriority(1)
            .build());

    // Scenario stub requiring "Modified" state (but scenario is in STARTED)
    mappings.addMapping(
        StubMapping.builder()
            .setRequest(newRequestPattern(GET, urlEqualTo("/resource")).build())
            .setResponse(responseDefinition().withStatus(200).withBody("scenario-modified").build())
            .setPriority(10)
            .setScenarioName("TestScenario")
            .setRequiredScenarioState("Modified")
            .build());

    Request request = aRequest().withMethod(GET).withUrl("/resource").build();
    ResponseDefinition response =
        mappings.serveFor(ServeEvent.of(request)).getResponseDefinition();

    // Scenario stub doesn't match current state, so non-scenario stub wins
    assertThat(response.getBody(), is("non-scenario"));
  }

  @Test
  public void scenarioStubSelectedAfterStateTransition() {
    // Non-scenario stub with high priority
    mappings.addMapping(
        StubMapping.builder()
            .setRequest(newRequestPattern(GET, urlEqualTo("/resource")).build())
            .setResponse(responseDefinition().withStatus(200).withBody("non-scenario").build())
            .setPriority(1)
            .build());

    // Scenario stub for STARTED state
    mappings.addMapping(
        StubMapping.builder()
            .setRequest(newRequestPattern(GET, urlEqualTo("/resource")).build())
            .setResponse(responseDefinition().withStatus(200).withBody("scenario-started").build())
            .setPriority(10)
            .setScenarioName("TestScenario")
            .setRequiredScenarioState(STARTED)
            .build());

    // PUT to transition scenario state
    mappings.addMapping(
        StubMapping.builder()
            .setRequest(newRequestPattern(PUT, urlEqualTo("/resource")).build())
            .setResponse(responseDefinition().withStatus(200).build())
            .setScenarioName("TestScenario")
            .setRequiredScenarioState(STARTED)
            .setNewScenarioState("Modified")
            .build());

    // Scenario stub for Modified state
    mappings.addMapping(
        StubMapping.builder()
            .setRequest(newRequestPattern(GET, urlEqualTo("/resource")).build())
            .setResponse(responseDefinition().withStatus(200).withBody("scenario-modified").build())
            .setPriority(10)
            .setScenarioName("TestScenario")
            .setRequiredScenarioState("Modified")
            .build());

    // Before transition: scenario-started stub should win
    Request getRequest = aRequest("get1").withMethod(GET).withUrl("/resource").build();
    assertThat(
        mappings.serveFor(ServeEvent.of(getRequest)).getResponseDefinition().getBody(),
        is("scenario-started"));

    // Trigger state transition
    Request putRequest = aRequest("put").withMethod(PUT).withUrl("/resource").build();
    mappings.serveFor(ServeEvent.of(putRequest));

    // After transition: scenario-modified stub should win
    Request getRequest2 = aRequest("get2").withMethod(GET).withUrl("/resource").build();
    assertThat(
        mappings.serveFor(ServeEvent.of(getRequest2)).getResponseDefinition().getBody(),
        is("scenario-modified"));
  }

  @Test
  public void noScenarioStubsBehaviorUnchanged() {
    // Two non-scenario stubs: higher priority wins
    mappings.addMapping(
        StubMapping.builder()
            .setRequest(newRequestPattern(GET, urlEqualTo("/resource")).build())
            .setResponse(responseDefinition().withStatus(200).withBody("low-priority").build())
            .setPriority(10)
            .build());

    mappings.addMapping(
        StubMapping.builder()
            .setRequest(newRequestPattern(GET, urlEqualTo("/resource")).build())
            .setResponse(responseDefinition().withStatus(200).withBody("high-priority").build())
            .setPriority(1)
            .build());

    Request request = aRequest().withMethod(GET).withUrl("/resource").build();
    ResponseDefinition response =
        mappings.serveFor(ServeEvent.of(request)).getResponseDefinition();

    assertThat(response.getBody(), is("high-priority"));
  }

  @Test
  public void multipleScenarioStubsPreservesPriorityOrder() {
    // Two scenario stubs with different priorities - higher priority scenario stub wins
    mappings.addMapping(
        StubMapping.builder()
            .setRequest(newRequestPattern(GET, urlEqualTo("/resource")).build())
            .setResponse(
                responseDefinition().withStatus(200).withBody("scenario-low-priority").build())
            .setPriority(10)
            .setScenarioName("Scenario1")
            .setRequiredScenarioState(STARTED)
            .build());

    mappings.addMapping(
        StubMapping.builder()
            .setRequest(newRequestPattern(GET, urlEqualTo("/resource")).build())
            .setResponse(
                responseDefinition().withStatus(200).withBody("scenario-high-priority").build())
            .setPriority(1)
            .setScenarioName("Scenario2")
            .setRequiredScenarioState(STARTED)
            .build());

    Request request = aRequest().withMethod(GET).withUrl("/resource").build();
    ResponseDefinition response =
        mappings.serveFor(ServeEvent.of(request)).getResponseDefinition();

    assertThat(response.getBody(), is("scenario-high-priority"));
  }

  @Test
  public void scenarioIndependentStubDoesNotGetPreference() {
    // Scenario stub without requiredScenarioState (independent of state)
    mappings.addMapping(
        StubMapping.builder()
            .setRequest(newRequestPattern(GET, urlEqualTo("/resource")).build())
            .setResponse(
                responseDefinition().withStatus(200).withBody("scenario-independent").build())
            .setPriority(10)
            .setScenarioName("TestScenario")
            .build());

    // Non-scenario stub with higher priority
    mappings.addMapping(
        StubMapping.builder()
            .setRequest(newRequestPattern(GET, urlEqualTo("/resource")).build())
            .setResponse(responseDefinition().withStatus(200).withBody("non-scenario").build())
            .setPriority(1)
            .build());

    Request request = aRequest().withMethod(GET).withUrl("/resource").build();
    ResponseDefinition response =
        mappings.serveFor(ServeEvent.of(request)).getResponseDefinition();

    // Scenario-independent stub should NOT get preference; priority order applies
    assertThat(response.getBody(), is("non-scenario"));
  }
}
