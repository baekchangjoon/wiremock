import requests
import sys

BASE_URL = "http://localhost:8080"
ADMIN_URL = "http://localhost:8080/__admin"

def setup_stubs():
    # Reset everything (mappings, requests, scenarios)
    requests.post(f"{ADMIN_URL}/reset")
    
    # Stub 1: Initial state -> Transition to State1
    requests.post(f"{ADMIN_URL}/mappings", json={
        "scenarioName": "StateScenario",
        "requiredScenarioState": "Started",
        "newScenarioState": "State1",
        "request": {
            "method": "GET",
            "url": "/state"
        },
        "response": {
            "status": 200,
            "body": "Transitioned to State1"
        }
    })

    # Stub 2: State1 -> Return "In State1"
    requests.post(f"{ADMIN_URL}/mappings", json={
        "scenarioName": "StateScenario",
        "requiredScenarioState": "State1",
        "request": {
            "method": "GET",
            "url": "/state"
        },
        "response": {
            "status": 200,
            "body": "In State1"
        }
    })

def run_scenario_1_isolation():
    print("--- Scenario 1: Isolation ---")
    setup_stubs()
    
    # Client A (Cookie)
    session_a = requests.Session()
    # Trigger transition for Client A
    resp_a1 = session_a.get(f"{BASE_URL}/state")
    print(f"Client A (1st req): {resp_a1.text}") # Expect: Transitioned to State1
    
    resp_a2 = session_a.get(f"{BASE_URL}/state")
    print(f"Client A (2nd req): {resp_a2.text}") # Expect: In State1
    
    # Client B (Header)
    headers_b = {"X-WireMock-Session-Id": "client-b-id"}
    resp_b1 = requests.get(f"{BASE_URL}/state", headers=headers_b)
    print(f"Client B (1st req): {resp_b1.text}") # Expect: Transitioned to State1 (since it starts at Started for this new session)
    
    if resp_a2.text == "In State1" and resp_b1.text == "Transitioned to State1":
        print("PASS: Isolation verified.")
    else:
        print("FAIL: Isolation failed.")

def run_scenario_2_priority():
    print("\n--- Scenario 2: Priority ---")
    setup_stubs()
    
    # Send both Header and Cookie
    # Header ID: header-session -> Transition it first
    requests.get(f"{BASE_URL}/state", headers={"X-WireMock-Session-Id": "header-session"})
    
    # Cookie ID: cookie-session -> Keep it at Started (don't touch)
    
    # Now send request with BOTH. If Header wins, we get "In State1". If Cookie wins, we get "Transitioned to State1".
    cookies = {"WireMockSessionId": "cookie-session"}
    headers = {"X-WireMock-Session-Id": "header-session"}
    
    resp = requests.get(f"{BASE_URL}/state", headers=headers, cookies=cookies)
    print(f"Response with both: {resp.text}")
    
    if resp.text == "In State1":
        print("PASS: Header takes priority.")
    else:
        print("INFO: Cookie takes priority or other behavior.")

def run_scenario_3_client_side_cookie():
    print("\n--- Scenario 3: Client-Side Cookie ---")
    setup_stubs()
    
    # Send custom cookie ID
    cookies = {"WireMockSessionId": "custom-client-id"}
    
    # 1st request -> Transitions to State1
    resp1 = requests.get(f"{BASE_URL}/state", cookies=cookies)
    print(f"Req 1: {resp1.text}")
    
    # 2nd request -> Should be in State1
    resp2 = requests.get(f"{BASE_URL}/state", cookies=cookies)
    print(f"Req 2: {resp2.text}")
    
    if resp1.text == "Transitioned to State1" and resp2.text == "In State1":
        print("PASS: Custom client-side cookie maintains state.")
    else:
        print("FAIL: Custom client-side cookie state lost.")

def run_scenario_4_stateless():
    print("\n--- Scenario 4: Stateless ---")
    setup_stubs()
    
    # Request 1 (No ID) -> Transitions (internally, but lost?)
    resp1 = requests.get(f"{BASE_URL}/state")
    print(f"Req 1: {resp1.text}")
    
    # Request 2 (No ID) -> Should be new session -> Started -> Transitions again
    resp2 = requests.get(f"{BASE_URL}/state")
    print(f"Req 2: {resp2.text}")
    
    if resp1.text == "Transitioned to State1" and resp2.text == "Transitioned to State1":
        print("PASS: Stateless requests reset state (new session each time).")
    else:
        print("FAIL: State persisted without ID.")

if __name__ == "__main__":
    try:
        run_scenario_1_isolation()
        run_scenario_2_priority()
        run_scenario_3_client_side_cookie()
        run_scenario_4_stateless()
    except Exception as e:
        print(f"Error: {e}")
        sys.exit(1)
