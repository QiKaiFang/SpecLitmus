export class FixtureApiClient {
  constructor(scenario) {
    this.scenario = scenario
    this.reviewSequence = null
    this.reviewIndex = 0
  }

  async review() {
    if (!this.reviewSequence) {
      const response = await fetch(`/test-fixtures/${encodeURIComponent(this.scenario)}.json`)
      if (!response.ok) {
        throw new Error(`固定测试场景「${this.scenario}」不存在。`)
      }
      const fixture = await response.json()
      this.reviewSequence = fixture.review || []
    }

    if (this.reviewSequence.length === 0) {
      throw new Error('固定测试场景没有配置审查响应。')
    }

    const index = Math.min(this.reviewIndex, this.reviewSequence.length - 1)
    this.reviewIndex += 1
    const result = this.reviewSequence[index]
    if (result.delayMs > 0) {
      await new Promise((resolve) => window.setTimeout(resolve, result.delayMs))
    }
    if (result.status < 200 || result.status >= 300) {
      throw new Error(result.body?.message || '固定测试场景返回了错误响应。')
    }
    return result.body
  }
}
