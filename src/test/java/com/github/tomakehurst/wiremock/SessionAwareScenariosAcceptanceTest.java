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
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static com.github.tomakehurst.wiremock.testsupport.TestFiles.filePath;
import static com.github.tomakehurst.wiremock.testsupport.TestHttpHeader.withHeader;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.stubbing.SessionId;
import com.github.tomakehurst.wiremock.testsupport.TestHttpHeader;
import com.github.tomakehurst.wiremock.testsupport.WireMockResponse;
import com.github.tomakehurst.wiremock.testsupport.WireMockTestClient;
import java.util.Locale;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SessionAwareScenariosAcceptanceTest {

  protected static WireMockServer wireMockServer;
  protected static WireMockTestClient testClient;

  @BeforeAll
  public static void setupServer() {
    wireMockServer =
        new WireMockServer(
            wireMockConfig()
                .dynamicPort()
                .withRootDirectory(filePath("empty"))
                .sessionAwareScenarios(true));
    wireMockServer.start();
    testClient = new WireMockTestClient(wireMockServer.port());
    WireMock.configureFor(wireMockServer.port());
    Locale.setDefault(Locale.ENGLISH);
  }

  @AfterAll
  public static void serverShutdown() {
    wireMockServer.stop();
  }

  @BeforeEach
  public void resetServer() {
    WireMock.reset();
  }

  @Test
  public void differentSessionsShouldHaveIndependentScenarioStates() {
    givenThat(
        get(urlEqualTo("/customer"))
            .willReturn(aResponse().withBody("Initial Data"))
            .inScenario("CustomerUpdate")
            .whenScenarioStateIs(STARTED));

    givenThat(
        put(urlEqualTo("/customer"))
            .willReturn(aResponse().withStatus(HTTP_OK))
            .inScenario("CustomerUpdate")
            .willSetStateTo("DataModified")
            .whenScenarioStateIs(STARTED));

    givenThat(
        get(urlEqualTo("/customer"))
            .willReturn(aResponse().withBody("Modified Data"))
            .inScenario("CustomerUpdate")
            .whenScenarioStateIs("DataModified"));

    // Session A: GET -> PUT -> GET (should see modified data)
    String sessionA = "session-A";
    assertThat(
        testClient.get("/customer", withHeader(SessionId.HEADER_NAME, sessionA)).content(),
        is("Initial Data"));
    testClient.put("/customer", withHeader(SessionId.HEADER_NAME, sessionA));
    assertThat(
        testClient.get("/customer", withHeader(SessionId.HEADER_NAME, sessionA)).content(),
        is("Modified Data"));

    // Session B: GET -> GET -> GET (should always see initial data)
    String sessionB = "session-B";
    assertThat(
        testClient.get("/customer", withHeader(SessionId.HEADER_NAME, sessionB)).content(),
        is("Initial Data"));
    assertThat(
        testClient.get("/customer", withHeader(SessionId.HEADER_NAME, sessionB)).content(),
        is("Initial Data"));
    assertThat(
        testClient.get("/customer", withHeader(SessionId.HEADER_NAME, sessionB)).content(),
        is("Initial Data"));
  }

  @Test
  public void sessionCookieShouldBeSetForNewSessions() {
    givenThat(get(urlEqualTo("/test")).willReturn(aResponse().withBody("OK")));

    // Request without session - should get a cookie
    WireMockResponse response = testClient.get("/test");
    assertThat(response.statusCode(), is(HTTP_OK));

    String setCookieHeader = response.firstHeader("Set-Cookie");
    assertThat(setCookieHeader, is(notNullValue()));
    assertThat(setCookieHeader, containsString(SessionId.COOKIE_NAME + "="));
  }

  @Test
  public void sessionCookieShouldMaintainState() {
    givenThat(
        get(urlEqualTo("/resource"))
            .willReturn(aResponse().withBody("State 1"))
            .inScenario("CookieSession")
            .whenScenarioStateIs(STARTED));

    givenThat(
        put(urlEqualTo("/resource"))
            .willReturn(aResponse().withStatus(HTTP_OK))
            .inScenario("CookieSession")
            .willSetStateTo("State2")
            .whenScenarioStateIs(STARTED));

    givenThat(
        get(urlEqualTo("/resource"))
            .willReturn(aResponse().withBody("State 2"))
            .inScenario("CookieSession")
            .whenScenarioStateIs("State2"));

    // First request - get initial response and cookie
    WireMockResponse firstResponse = testClient.get("/resource");
    assertThat(firstResponse.content(), is("State 1"));

    String setCookieHeader = firstResponse.firstHeader("Set-Cookie");
    assertThat(setCookieHeader, is(notNullValue()));

    // Extract session ID from cookie
    String sessionCookie = extractSessionCookie(setCookieHeader);

    // PUT request with cookie
    testClient.put("/resource", withHeader("Cookie", sessionCookie));

    // GET with same cookie should see modified state
    WireMockResponse secondResponse = testClient.get("/resource", withHeader("Cookie", sessionCookie));
    assertThat(secondResponse.content(), is("State 2"));
  }

  @Test
  public void explicitHeaderShouldTakePriorityOverCookie() {
    givenThat(
        get(urlEqualTo("/priority"))
            .willReturn(aResponse().withBody("Initial"))
            .inScenario("PriorityTest")
            .whenScenarioStateIs(STARTED));

    givenThat(
        put(urlEqualTo("/priority"))
            .willReturn(aResponse().withStatus(HTTP_OK))
            .inScenario("PriorityTest")
            .willSetStateTo("Changed")
            .whenScenarioStateIs(STARTED));

    givenThat(
        get(urlEqualTo("/priority"))
            .willReturn(aResponse().withBody("Changed"))
            .inScenario("PriorityTest")
            .whenScenarioStateIs("Changed"));

    // Modify state for session via header
    String headerSession = "header-session";
    testClient.get("/priority", withHeader(SessionId.HEADER_NAME, headerSession));
    testClient.put("/priority", withHeader(SessionId.HEADER_NAME, headerSession));

    // Request with both header and cookie - header should take priority
    String cookieSession = SessionId.COOKIE_NAME + "=cookie-session";
    WireMockResponse response =
        testClient.get(
            "/priority",
            withHeader(SessionId.HEADER_NAME, headerSession),
            withHeader("Cookie", cookieSession));

    assertThat(response.content(), is("Changed"));
  }

  @Test
  public void multipleSessionsShouldWorkConcurrently() {
    givenThat(
        get(urlEqualTo("/counter"))
            .willReturn(aResponse().withBody("0"))
            .inScenario("Counter")
            .whenScenarioStateIs(STARTED));

    givenThat(
        post(urlEqualTo("/counter"))
            .willReturn(aResponse().withStatus(HTTP_OK))
            .inScenario("Counter")
            .willSetStateTo("1")
            .whenScenarioStateIs(STARTED));

    givenThat(
        get(urlEqualTo("/counter"))
            .willReturn(aResponse().withBody("1"))
            .inScenario("Counter")
            .whenScenarioStateIs("1"));

    givenThat(
        post(urlEqualTo("/counter"))
            .willReturn(aResponse().withStatus(HTTP_OK))
            .inScenario("Counter")
            .willSetStateTo("2")
            .whenScenarioStateIs("1"));

    givenThat(
        get(urlEqualTo("/counter"))
            .willReturn(aResponse().withBody("2"))
            .inScenario("Counter")
            .whenScenarioStateIs("2"));

    // Session 1: 0 -> 1
    assertThat(
        testClient.get("/counter", withHeader(SessionId.HEADER_NAME, "s1")).content(), is("0"));
    testClient.post("/counter", withHeader(SessionId.HEADER_NAME, "s1"));
    assertThat(
        testClient.get("/counter", withHeader(SessionId.HEADER_NAME, "s1")).content(), is("1"));

    // Session 2: still at 0
    assertThat(
        testClient.get("/counter", withHeader(SessionId.HEADER_NAME, "s2")).content(), is("0"));

    // Session 1: 1 -> 2
    testClient.post("/counter", withHeader(SessionId.HEADER_NAME, "s1"));
    assertThat(
        testClient.get("/counter", withHeader(SessionId.HEADER_NAME, "s1")).content(), is("2"));

    // Session 2: 0 -> 1
    testClient.post("/counter", withHeader(SessionId.HEADER_NAME, "s2"));
    assertThat(
        testClient.get("/counter", withHeader(SessionId.HEADER_NAME, "s2")).content(), is("1"));

    // Session 3: new session, starts at 0
    assertThat(
        testClient.get("/counter", withHeader(SessionId.HEADER_NAME, "s3")).content(), is("0"));
  }

  @Test
  public void sessionStatesShouldBeCompletelyIsolated() {
    // This test simulates the exact scenario from the user's requirement:
    // User A: 1. GET customer, 2. PUT customer (modify), 3. GET customer (should see modified)
    // User B: 1. GET customer, 2. GET customer, 3. GET customer (should always see initial)

    givenThat(
        get(urlEqualTo("/api/customer"))
            .willReturn(aResponse().withBody("Initial Customer Data"))
            .inScenario("CustomerFlow")
            .whenScenarioStateIs(STARTED));

    givenThat(
        put(urlEqualTo("/api/customer"))
            .willReturn(aResponse().withStatus(HTTP_OK).withBody("Customer Updated"))
            .inScenario("CustomerFlow")
            .willSetStateTo("Modified")
            .whenScenarioStateIs(STARTED));

    givenThat(
        get(urlEqualTo("/api/customer"))
            .willReturn(aResponse().withBody("Modified Customer Data"))
            .inScenario("CustomerFlow")
            .whenScenarioStateIs("Modified"));

    // User A's flow
    String userA = "user-A-session";
    assertThat(
        testClient.get("/api/customer", withHeader(SessionId.HEADER_NAME, userA)).content(),
        is("Initial Customer Data"));
    assertThat(
        testClient.put("/api/customer", withHeader(SessionId.HEADER_NAME, userA)).content(),
        is("Customer Updated"));
    assertThat(
        testClient.get("/api/customer", withHeader(SessionId.HEADER_NAME, userA)).content(),
        is("Modified Customer Data"));

    // User B's flow - should be completely independent of User A
    String userB = "user-B-session";
    assertThat(
        testClient.get("/api/customer", withHeader(SessionId.HEADER_NAME, userB)).content(),
        is("Initial Customer Data"));
    assertThat(
        testClient.get("/api/customer", withHeader(SessionId.HEADER_NAME, userB)).content(),
        is("Initial Customer Data"));
    assertThat(
        testClient.get("/api/customer", withHeader(SessionId.HEADER_NAME, userB)).content(),
        is("Initial Customer Data"));

    // Verify User A still sees modified state
    assertThat(
        testClient.get("/api/customer", withHeader(SessionId.HEADER_NAME, userA)).content(),
        is("Modified Customer Data"));
  }

  @Test
  public void headerShouldOverrideCookieInBothDirections() {
    givenThat(
        get(urlEqualTo("/priority-test"))
            .willReturn(aResponse().withBody("Initial"))
            .inScenario("PriorityBidirectional")
            .whenScenarioStateIs(STARTED));

    givenThat(
        put(urlEqualTo("/priority-test"))
            .willReturn(aResponse().withStatus(HTTP_OK))
            .inScenario("PriorityBidirectional")
            .willSetStateTo("Changed")
            .whenScenarioStateIs(STARTED));

    givenThat(
        get(urlEqualTo("/priority-test"))
            .willReturn(aResponse().withBody("Changed"))
            .inScenario("PriorityBidirectional")
            .whenScenarioStateIs("Changed"));

    // Setup: session-C is in Changed state, session-D is in Started state
    String sessionC = "session-C";
    String sessionD = "session-D";

    testClient.get("/priority-test", withHeader(SessionId.HEADER_NAME, sessionC));
    testClient.put("/priority-test", withHeader(SessionId.HEADER_NAME, sessionC));
    testClient.get("/priority-test", withHeader(SessionId.HEADER_NAME, sessionD));

    // Test 1: Header(sessionC=Changed) + Cookie(sessionD=Started) -> Should return Changed
    WireMockResponse response1 =
        testClient.get(
            "/priority-test",
            withHeader(SessionId.HEADER_NAME, sessionC),
            withHeader("Cookie", SessionId.COOKIE_NAME + "=" + sessionD));
    assertThat(response1.content(), is("Changed"));

    // Test 2: Header(sessionD=Started) + Cookie(sessionC=Changed) -> Should return Initial
    WireMockResponse response2 =
        testClient.get(
            "/priority-test",
            withHeader(SessionId.HEADER_NAME, sessionD),
            withHeader("Cookie", SessionId.COOKIE_NAME + "=" + sessionC));
    assertThat(response2.content(), is("Initial"));
  }

  @Test
  public void cookieShouldContainProperAttributes() {
    givenThat(get(urlEqualTo("/cookie-attributes")).willReturn(aResponse().withBody("OK")));

    WireMockResponse response = testClient.get("/cookie-attributes");
    String setCookieHeader = response.firstHeader("Set-Cookie");

    assertThat(setCookieHeader, is(notNullValue()));
    assertThat(setCookieHeader, containsString(SessionId.COOKIE_NAME + "="));
    assertThat(setCookieHeader, containsString("Path=/"));
    assertThat(setCookieHeader, containsString("HttpOnly"));
    assertThat(setCookieHeader, containsString("SameSite=Lax"));
  }

  @Test
  public void existingSessionShouldNotReceiveNewCookie() {
    givenThat(get(urlEqualTo("/existing-session")).willReturn(aResponse().withBody("OK")));

    String existingSession = "existing-session-id";

    // Request with explicit header - should not get a new cookie
    WireMockResponse response =
        testClient.get("/existing-session", withHeader(SessionId.HEADER_NAME, existingSession));

    assertThat(response.statusCode(), is(HTTP_OK));
    String setCookieHeader = response.firstHeader("Set-Cookie");
    assertThat(setCookieHeader, is(nullValue()));
  }

  private String extractSessionCookie(String setCookieHeader) {
    // Extract just the cookie name=value part
    String[] parts = setCookieHeader.split(";");
    return parts[0].trim();
  }
}
