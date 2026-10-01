import { ReviewApiError } from './apiClient'

export class FixtureApiClient {
  constructor(scenario) {
    this.scenario = scenario
    this.reviewSequence = null
    this.reviewIndex = 0
    this.acceptanceSequence = null
    this.acceptanceIndex = 0
    this.acceptanceRequests = []
  }

  async review() {
    if (!this.reviewSequence) {
      const response = await fetch(`/test-fixtures/${encodeURIComponent(this.scenario)}.json`)
      if (!response.ok) {
        throw new ReviewApiError({ retryable: false }, `固定测试场景「${this.scenario}」不存在。`)
      }
      const fixture = await response.json()
      this.reviewSequence = fixture.review || []
    }

    if (this.reviewSequence.length === 0) {
      throw new ReviewApiError({ retryable: false }, '固定测试场景没有配置审查响应。')
    }

    const index = Math.min(this.reviewIndex, this.reviewSequence.length - 1)
    this.reviewIndex += 1
    const result = this.reviewSequence[index]
    if (result.delayMs > 0) {
      await new Promise((resolve) => window.setTimeout(resolve, result.delayMs))
    }
    if (result.status < 200 || result.status >= 300) {
      throw new ReviewApiError(result.body, '固定测试场景返回了错误响应。')
    }
    return result.body
  }

  async acceptance(reviewId, answers) {
    this.acceptanceRequests.push({ reviewId, answers })
    if (!this.acceptanceSequence) {
      const response = await fetch(`/test-fixtures/${encodeURIComponent(this.scenario)}.json`)
      if (!response.ok) {
        throw new ReviewApiError({ retryable: false }, `固定测试场景「${this.scenario}」不存在。`)
      }
      const fixture = await response.json()
      this.acceptanceSequence = fixture.acceptance || []
    }

    if (this.acceptanceSequence.length === 0) {
      throw new ReviewApiError({ retryable: false }, '固定测试场景没有配置验收响应。')
    }

    const index = Math.min(this.acceptanceIndex, this.acceptanceSequence.length - 1)
    this.acceptanceIndex += 1
    const result = this.acceptanceSequence[index]
    if (result.delayMs > 0) {
      await new Promise((resolve) => window.setTimeout(resolve, result.delayMs))
    }
    if (result.status < 200 || result.status >= 300) {
      throw new ReviewApiError(result.body, '固定测试场景返回了验收错误响应。')
    }
    return result.body
  }
}
