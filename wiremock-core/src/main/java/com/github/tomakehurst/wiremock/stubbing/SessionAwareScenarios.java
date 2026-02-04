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

import static com.github.tomakehurst.wiremock.common.ParameterUtils.getFirstNonNull;
import static java.util.stream.Collectors.toList;

import com.github.tomakehurst.wiremock.admin.NotFoundException;
import com.github.tomakehurst.wiremock.store.SessionAwareScenariosStore;
import java.util.List;
import java.util.Set;

public class SessionAwareScenarios implements Scenarios {

  private final SessionAwareScenariosStore store;

  public SessionAwareScenarios(SessionAwareScenariosStore store) {
    this.store = store;
  }

  public SessionAwareScenarios() {
    this(new SessionAwareScenariosStore());
  }

  @Override
  public boolean isSessionAware() {
    return true;
  }

  @Override
  public Scenario getByName(String name) {
    return getByName(SessionId.global(), name);
  }

  @Override
  public Scenario getByName(SessionId sessionId, String name) {
    return store.get(sessionId, name).orElse(null);
  }

  @Override
  public List<Scenario> getAll() {
    return store.getAll().collect(toList());
  }

  @Override
  public List<Scenario> getAllForSession(SessionId sessionId) {
    return store.getAllForSession(sessionId).collect(toList());
  }

  @Override
  public void onStubMappingAdded(StubMapping mapping) {
    if (mapping.isInScenario()) {
      String scenarioName = mapping.getScenarioName();
      // Add to global session - this is the template
      Scenario scenario =
          getFirstNonNull(
                  store.get(SessionId.global(), scenarioName).orElse(null),
                  Scenario.inStartedState(scenarioName))
              .withStubMapping(mapping);
      store.put(SessionId.global(), scenarioName, scenario);
    }
  }

  @Override
  public void onStubMappingUpdated(StubMapping oldMapping, StubMapping newMapping) {
    if (oldMapping.isInScenario()
        && !oldMapping.getScenarioName().equals(newMapping.getScenarioName())) {
      Scenario scenarioForOldMapping =
          store
              .get(SessionId.global(), oldMapping.getScenarioName())
              .map(scenario -> scenario.withoutStubMapping(oldMapping))
              .orElseThrow(IllegalStateException::new);

      if (scenarioForOldMapping.getMappings().isEmpty()) {
        store.remove(SessionId.global(), scenarioForOldMapping.getId());
      } else {
        store.put(SessionId.global(), oldMapping.getScenarioName(), scenarioForOldMapping);
      }
    }

    if (newMapping.isInScenario()) {
      String scenarioName = newMapping.getScenarioName();
      Scenario scenario =
          getFirstNonNull(
                  store.get(SessionId.global(), scenarioName).orElse(null),
                  Scenario.inStartedState(scenarioName))
              .withStubMapping(newMapping);
      store.put(SessionId.global(), scenarioName, scenario);
    }
  }

  @Override
  public void onStubMappingRemoved(StubMapping mapping) {
    if (mapping.isInScenario()) {
      final String scenarioName = mapping.getScenarioName();
      Scenario scenario =
          store
              .get(SessionId.global(), scenarioName)
              .orElseThrow(IllegalStateException::new)
              .withoutStubMapping(mapping);

      if (scenario.getMappings().isEmpty()) {
        store.remove(SessionId.global(), scenarioName);
      } else {
        store.put(SessionId.global(), scenarioName, scenario);
      }
    }
  }

  @Override
  public void onStubServed(StubMapping mapping) {
    onStubServed(SessionId.global(), mapping);
  }

  @Override
  public void onStubServed(SessionId sessionId, StubMapping mapping) {
    if (mapping.isInScenario()) {
      final String scenarioName = mapping.getScenarioName();
      Scenario scenario = getOrInitializeScenario(sessionId, scenarioName);

      if (mapping.modifiesScenarioState()
          && (mapping.getRequiredScenarioState() == null
              || scenario.getState().equals(mapping.getRequiredScenarioState()))) {
        Scenario newScenario = scenario.setState(mapping.getNewScenarioState());
        store.put(sessionId, scenarioName, newScenario);
      }
    }
  }

  private Scenario getOrInitializeScenario(SessionId sessionId, String scenarioName) {
    return store
        .get(sessionId, scenarioName)
        .orElseGet(
            () -> {
              // Initialize from global template or create new
              Scenario globalScenario =
                  store
                      .get(SessionId.global(), scenarioName)
                      .orElse(Scenario.inStartedState(scenarioName));
              Scenario sessionScenario =
                  new Scenario(
                      globalScenario.getId(),
                      null,
                      Scenario.STARTED,
                      null,
                      globalScenario.getMappings());
              store.put(sessionId, scenarioName, sessionScenario);
              return sessionScenario;
            });
  }

  @Override
  public void reset() {
    store
        .getAll()
        .map(Scenario::reset)
        .forEach(scenario -> store.put(scenario.getId(), scenario));
  }

  @Override
  public void resetSession(SessionId sessionId) {
    store
        .getAllForSession(sessionId)
        .map(Scenario::reset)
        .forEach(scenario -> store.put(sessionId, scenario.getId(), scenario));
  }

  @Override
  public void resetSingle(String name) {
    resetSingle(SessionId.global(), name);
  }

  @Override
  public void resetSingle(SessionId sessionId, String name) {
    setSingleScenarioState(sessionId, name, Scenario::reset);
  }

  @Override
  public void setSingle(String name, String state) {
    setSingle(SessionId.global(), name, state);
  }

  @Override
  public void setSingle(SessionId sessionId, String name, String state) {
    setSingleScenarioState(sessionId, name, scenario -> scenario.setState(state));
  }

  private void setSingleScenarioState(
      SessionId sessionId, String name, java.util.function.Function<Scenario, Scenario> fn) {
    Scenario scenario = getOrInitializeScenario(sessionId, name);
    store.put(sessionId, name, fn.apply(scenario));
  }

  @Override
  public void clear() {
    store.clear();
  }

  @Override
  public void clearSession(SessionId sessionId) {
    store.clearSession(sessionId);
  }

  @Override
  public boolean mappingMatchesScenarioState(StubMapping mapping) {
    return mappingMatchesScenarioState(SessionId.global(), mapping);
  }

  @Override
  public boolean mappingMatchesScenarioState(SessionId sessionId, StubMapping mapping) {
    Scenario scenario = getOrInitializeScenario(sessionId, mapping.getScenarioName());
    String currentScenarioState = scenario.getState();
    return mapping.getRequiredScenarioState().equals(currentScenarioState);
  }

  @Override
  public Set<String> getActiveSessions() {
    return store.getActiveSessions();
  }
}
