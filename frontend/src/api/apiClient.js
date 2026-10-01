export class ReviewApiError extends Error {
  constructor(body, fallbackMessage) {
    super(body?.message || fallbackMessage)
    this.name = 'ReviewApiError'
    this.code = body?.code || 'NETWORK_UNAVAILABLE'
    this.retryable = body?.retryable !== false
  }
}

export class ApiClient {
  async review(requirementText) {
    let response
    try {
      response = await fetch('/api/reviews', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ requirementText }),
      })
    } catch {
      throw new ReviewApiError(null, '无法连接到审查服务，请检查网络或服务状态后重试。')
    }

    const body = await response.json().catch(() => null)
    if (!response.ok) {
      throw new ReviewApiError(body, '审查请求失败，请检查服务后重试。')
    }
    return body
  }
}
