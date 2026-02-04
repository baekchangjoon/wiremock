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

import com.github.tomakehurst.wiremock.http.Cookie;
import com.github.tomakehurst.wiremock.http.Request;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class SessionId {

  public static final String HEADER_NAME = "X-WireMock-Session-Id";
  public static final String COOKIE_NAME = "WireMockSessionId";
  public static final String GLOBAL_SESSION_ID = "__global__";

  private final String value;
  private final boolean isNew;

  private SessionId(String value, boolean isNew) {
    this.value = value;
    this.isNew = isNew;
  }

  public static SessionId global() {
    return new SessionId(GLOBAL_SESSION_ID, false);
  }

  public static SessionId of(String value) {
    return new SessionId(value, false);
  }

  public static SessionId newSession() {
    return new SessionId(UUID.randomUUID().toString(), true);
  }

  public static SessionId extractFrom(Request request) {
    // Priority 1: Explicit header
    String headerValue = request.getHeader(HEADER_NAME);
    if (headerValue != null && !headerValue.isEmpty()) {
      return new SessionId(headerValue, false);
    }

    // Priority 2: Cookie
    Map<String, Cookie> cookies = request.getCookies();
    if (cookies != null) {
      Cookie sessionCookie = cookies.get(COOKIE_NAME);
      if (sessionCookie != null && !sessionCookie.getValues().isEmpty()) {
        String cookieValue = sessionCookie.getValues().get(0);
        if (cookieValue != null && !cookieValue.isEmpty()) {
          return new SessionId(cookieValue, false);
        }
      }
    }

    // No session found - create a new one
    return newSession();
  }

  public String getValue() {
    return value;
  }

  public boolean isNew() {
    return isNew;
  }

  public boolean isGlobal() {
    return GLOBAL_SESSION_ID.equals(value);
  }

  public String buildScenarioKey(String scenarioName) {
    if (isGlobal()) {
      return scenarioName;
    }
    return value + "::" + scenarioName;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SessionId sessionId = (SessionId) o;
    return Objects.equals(value, sessionId.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }

  @Override
  public String toString() {
    return "SessionId{" + "value='" + value + '\'' + ", isNew=" + isNew + '}';
  }
}
