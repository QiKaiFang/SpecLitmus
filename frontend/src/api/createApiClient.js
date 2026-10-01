import { ApiClient } from './apiClient'
import { FixtureApiClient } from './fixtureApiClient'

export function createApiClient(location = window.location) {
  const scenario = new URLSearchParams(location.search).get('fixture')
  if (scenario) {
    return {
      client: new FixtureApiClient(scenario),
      fixtureMode: true,
      fixtureScenario: scenario,
    }
  }
  return { client: new ApiClient(), fixtureMode: false, fixtureScenario: null }
}
