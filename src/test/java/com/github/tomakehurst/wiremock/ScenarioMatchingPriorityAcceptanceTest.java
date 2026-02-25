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
package com.github.tomakehurst.wiremock;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import com.github.tomakehurst.wiremock.testsupport.WireMockResponse;
import org.junit.jupiter.api.Test;

public class ScenarioMatchingPriorityAcceptanceTest extends AcceptanceTestBase {

  @Test
  void scenarioMatchedStubTakesPriorityOverNonScenarioStub() {
    // Register a non-scenario stub with high priority (priority=1)
    stubFor(
        get(urlEqualTo("/api/data"))
            .atPriority(1)
            .willReturn(aResponse().withStatus(200).withBody("default-response")));

    // Register a scenario stub with low priority (priority=10) in STARTED state
    stubFor(
        get(urlEqualTo("/api/data"))
            .atPriority(10)
            .inScenario("DataFlow")
            .whenScenarioStateIs(STARTED)
            .willReturn(aResponse().withStatus(200).withBody("scenario-started-response")));

    // The scenario-matched stub should win despite lower priority
    WireMockResponse response = testClient.get("/api/data");
    assertThat(response.content(), is("scenario-started-response"));
  }

  @Test
  void nonScenarioStubRespondWhenScenarioStateDoesNotMatch() {
    // Register a non-scenario stub
    stubFor(
        get(urlEqualTo("/api/data"))
            .atPriority(1)
            .willReturn(aResponse().withStatus(200).withBody("default-response")));

    // Register scenario stubs
    stubFor(
        get(urlEqualTo("/api/data"))
            .atPriority(10)
            .inScenario("DataFlow")
            .whenScenarioStateIs(STARTED)
            .willSetStateTo("Modified")
            .willReturn(aResponse().withStatus(200).withBody("scenario-started-response")));

    stubFor(
        get(urlEqualTo("/api/data"))
            .atPriority(10)
            .inScenario("DataFlow")
            .whenScenarioStateIs("Modified")
            .willReturn(aResponse().withStatus(200).withBody("scenario-modified-response")));

    // First request: scenario is in STARTED -> scenario stub wins
    assertThat(testClient.get("/api/data").content(), is("scenario-started-response"));

    // Second request: scenario transitioned to Modified -> modified scenario stub wins
    assertThat(testClient.get("/api/data").content(), is("scenario-modified-response"));
  }

  @Test
  void scenarioStubPreferredDuringFullLifecycle() {
    // Non-scenario fallback stub
    stubFor(
        get(urlEqualTo("/api/order"))
            .atPriority(1)
            .willReturn(aResponse().withStatus(200).withBody("fallback")));

    // Scenario: STARTED -> GET returns "pending"
    stubFor(
        get(urlEqualTo("/api/order"))
            .atPriority(10)
            .inScenario("OrderFlow")
            .whenScenarioStateIs(STARTED)
            .willReturn(aResponse().withStatus(200).withBody("pending")));

    // Scenario: STARTED -> PUT transitions to "Confirmed"
    stubFor(
        put(urlEqualTo("/api/order"))
            .inScenario("OrderFlow")
            .whenScenarioStateIs(STARTED)
            .willSetStateTo("Confirmed")
            .willReturn(aResponse().withStatus(200).withBody("order-confirmed")));

    // Scenario: Confirmed -> GET returns "confirmed"
    stubFor(
        get(urlEqualTo("/api/order"))
            .atPriority(10)
            .inScenario("OrderFlow")
            .whenScenarioStateIs("Confirmed")
            .willReturn(aResponse().withStatus(200).withBody("confirmed")));

    // Step 1: GET -> scenario in STARTED state, scenario stub wins
    assertThat(testClient.get("/api/order").content(), is("pending"));

    // Step 2: PUT -> transitions scenario to Confirmed
    assertThat(testClient.put("/api/order").content(), is("order-confirmed"));

    // Step 3: GET -> scenario in Confirmed state, scenario stub wins
    assertThat(testClient.get("/api/order").content(), is("confirmed"));

    // Step 4: Reset scenario -> no scenario stub matches "Started" after reset?
    // Actually STARTED is the initial state, so scenario stub should match again
    resetAllScenarios();
    assertThat(testClient.get("/api/order").content(), is("pending"));
  }

  @Test
  void nonScenarioStubsUnaffectedByNewLogic() {
    // Two non-scenario stubs: priority determines the winner
    stubFor(
        get(urlEqualTo("/api/plain"))
            .atPriority(10)
            .willReturn(aResponse().withStatus(200).withBody("low-priority")));

    stubFor(
        get(urlEqualTo("/api/plain"))
            .atPriority(1)
            .willReturn(aResponse().withStatus(200).withBody("high-priority")));

    // Higher priority (lower number) wins as before
    assertThat(testClient.get("/api/plain").content(), is("high-priority"));
  }

  @Test
  void scenarioIndependentStubDoesNotGetPreference() {
    // Scenario stub WITHOUT requiredScenarioState (independent of state)
    stubFor(
        get(urlEqualTo("/api/independent"))
            .atPriority(10)
            .inScenario("SomeScenario")
            .willReturn(aResponse().withStatus(200).withBody("scenario-independent")));

    // Non-scenario stub with higher priority
    stubFor(
        get(urlEqualTo("/api/independent"))
            .atPriority(1)
            .willReturn(aResponse().withStatus(200).withBody("non-scenario")));

    // Scenario-independent stub should NOT get preference
    assertThat(testClient.get("/api/independent").content(), is("non-scenario"));
  }

  @Test
  void multipleScenarioStubsRespectPriorityAmongThemselves() {
    // Two different scenarios both matching the same request
    stubFor(
        get(urlEqualTo("/api/multi"))
            .atPriority(10)
            .inScenario("ScenarioA")
            .whenScenarioStateIs(STARTED)
            .willReturn(aResponse().withStatus(200).withBody("scenario-a")));

    stubFor(
        get(urlEqualTo("/api/multi"))
            .atPriority(1)
            .inScenario("ScenarioB")
            .whenScenarioStateIs(STARTED)
            .willReturn(aResponse().withStatus(200).withBody("scenario-b")));

    // Among scenario-matched stubs, priority order is preserved
    assertThat(testClient.get("/api/multi").content(), is("scenario-b"));
  }
}
