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
package com.github.tomakehurst.wiremock.store;

import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.github.tomakehurst.wiremock.stubbing.SessionId;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.wiremock.annotations.Beta;

@Beta(justification = "Session-aware Scenarios API")
public class SessionAwareScenariosStore implements ScenariosStore {

  private final ConcurrentHashMap<String, Scenario> scenarioMap = new ConcurrentHashMap<>();
  private final Set<String> activeSessions = ConcurrentHashMap.newKeySet();

  @Override
  public Stream<String> getAllKeys() {
    return scenarioMap.keySet().stream();
  }

  @Override
  public Stream<Scenario> getAll() {
    return scenarioMap.values().stream();
  }

  public Stream<Scenario> getAllForSession(SessionId sessionId) {
    String prefix = sessionId.isGlobal() ? "" : sessionId.getValue() + "::";
    return scenarioMap.entrySet().stream()
        .filter(
            entry -> {
              if (sessionId.isGlobal()) {
                return !entry.getKey().contains("::");
              } else {
                return entry.getKey().startsWith(prefix);
              }
            })
        .map(entry -> entry.getValue());
  }

  @Override
  public Optional<Scenario> get(String key) {
    return Optional.ofNullable(scenarioMap.get(key));
  }

  public Optional<Scenario> get(SessionId sessionId, String scenarioName) {
    String key = sessionId.buildScenarioKey(scenarioName);
    return Optional.ofNullable(scenarioMap.get(key));
  }

  @Override
  public void put(String key, Scenario content) {
    scenarioMap.put(key, content);
    extractSessionId(key).ifPresent(activeSessions::add);
  }

  public void put(SessionId sessionId, String scenarioName, Scenario content) {
    String key = sessionId.buildScenarioKey(scenarioName);
    scenarioMap.put(key, content);
    if (!sessionId.isGlobal()) {
      activeSessions.add(sessionId.getValue());
    }
  }

  @Override
  public void remove(String key) {
    scenarioMap.remove(key);
  }

  public void remove(SessionId sessionId, String scenarioName) {
    String key = sessionId.buildScenarioKey(scenarioName);
    scenarioMap.remove(key);
  }

  @Override
  public void clear() {
    scenarioMap.clear();
    activeSessions.clear();
  }

  public void clearSession(SessionId sessionId) {
    if (sessionId.isGlobal()) {
      scenarioMap.keySet().removeIf(key -> !key.contains("::"));
    } else {
      String prefix = sessionId.getValue() + "::";
      scenarioMap.keySet().removeIf(key -> key.startsWith(prefix));
      activeSessions.remove(sessionId.getValue());
    }
  }

  public Set<String> getActiveSessions() {
    return Set.copyOf(activeSessions);
  }

  public boolean hasSession(String sessionIdValue) {
    return activeSessions.contains(sessionIdValue);
  }

  private Optional<String> extractSessionId(String key) {
    int separatorIndex = key.indexOf("::");
    if (separatorIndex > 0) {
      return Optional.of(key.substring(0, separatorIndex));
    }
    return Optional.empty();
  }
}
