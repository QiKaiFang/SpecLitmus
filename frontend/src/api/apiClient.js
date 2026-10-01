export class ApiClient {
  async review(requirementText) {
    const response = await fetch('/api/reviews', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ requirementText }),
    })

    const body = await response.json().catch(() => null)
    if (!response.ok) {
      throw new Error(body?.message || '审查请求失败，请检查服务后重试。')
    }
    return body
  }
}
