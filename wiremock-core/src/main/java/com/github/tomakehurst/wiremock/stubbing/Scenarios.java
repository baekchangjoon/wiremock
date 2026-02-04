/*
 * Copyright (C) 2022-2026 Thomas Akehurst
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

import java.util.List;
import java.util.Set;

public interface Scenarios {
  Scenario getByName(String name);

  List<Scenario> getAll();

  void onStubMappingAdded(StubMapping mapping);

  void onStubMappingUpdated(StubMapping oldMapping, StubMapping newMapping);

  void onStubMappingRemoved(StubMapping mapping);

  void onStubServed(StubMapping mapping);

  void reset();

  void resetSingle(String name);

  void setSingle(String name, String state);

  void clear();

  boolean mappingMatchesScenarioState(StubMapping mapping);

  // Session-aware methods
  default Scenario getByName(SessionId sessionId, String name) {
    return getByName(name);
  }

  default List<Scenario> getAllForSession(SessionId sessionId) {
    return getAll();
  }

  default void onStubServed(SessionId sessionId, StubMapping mapping) {
    onStubServed(mapping);
  }

  default void resetSession(SessionId sessionId) {
    reset();
  }

  default void resetSingle(SessionId sessionId, String name) {
    resetSingle(name);
  }

  default void setSingle(SessionId sessionId, String name, String state) {
    setSingle(name, state);
  }

  default void clearSession(SessionId sessionId) {
    clear();
  }

  default boolean mappingMatchesScenarioState(SessionId sessionId, StubMapping mapping) {
    return mappingMatchesScenarioState(mapping);
  }

  default Set<String> getActiveSessions() {
    return Set.of();
  }

  default boolean isSessionAware() {
    return false;
  }
}
