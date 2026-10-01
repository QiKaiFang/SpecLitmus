function inlineText(value) {
  return String(value ?? '')
    .replaceAll('\\', '\\\\')
    .replaceAll('`', '\\`')
    .replaceAll('|', '\\|')
    .replaceAll('\r\n', ' ')
    .replaceAll('\n', ' ')
}

function textBlock(value) {
  const text = String(value ?? '')
  const longestRun = Math.max(0, ...Array.from(text.matchAll(/`+/g), (match) => match[0].length))
  const fence = '`'.repeat(Math.max(3, longestRun + 1))
  return `${fence}\n${text}\n${fence}`
}

function metadataLine(label, metadata) {
  if (!metadata) return `- ${label}：不可用`
  return `- ${label}：${inlineText(metadata.model || '不可用')} · Prompt ${inlineText(metadata.promptVersion || '不可用')}`
}

export function buildMarkdownReport({
  generatedAt,
  requirementText,
  questions = [],
  answers = {},
  acceptanceResult,
  reviewMetadata,
}) {
  if (!acceptanceResult) throw new Error('缺少当前有效的验收结果。')

  const lines = [
    '# 需求审查与验收报告',
    '',
    `- 生成时间：${inlineText(generatedAt)}`,
    metadataLine('审查模型', reviewMetadata),
    metadataLine('验收模型', acceptanceResult.metadata),
    '',
    '## 原需求',
    '',
    textBlock(requirementText),
    '',
    '## 审查问题',
    '',
  ]

  if (questions.length === 0) {
    lines.push('本轮审查未发现需要确认的问题。', '')
  } else {
    questions.forEach((question) => {
      lines.push(
        `### ${inlineText(question.questionId)} · ${inlineText(question.type)}`,
        '',
        `- 原文段落：${inlineText(question.paragraphId)}`,
        '',
        '**原文引用**',
        '',
        textBlock(question.quote),
        '',
        '**反例场景**',
        '',
        textBlock(question.scenario),
        '',
        '**澄清问题**',
        '',
        textBlock(question.question),
        '',
      )
      if (question.possibleInterpretations?.length) {
        lines.push('**可能的解释**', '')
        question.possibleInterpretations.forEach((item) => lines.push(`- ${inlineText(item)}`))
        lines.push('')
      }
    })
  }

  lines.push('## 用户确认回答', '')
  const confirmedAnswers = questions
    .map((question) => ({ question, text: answers[question.questionId] || '' }))
    .filter((item) => item.text.trim())
  if (confirmedAnswers.length === 0) {
    lines.push('本轮没有提交非空回答。', '')
  } else {
    confirmedAnswers.forEach(({ question, text }) => {
      lines.push(`### ${inlineText(question.questionId)} · ${inlineText(question.question)}`, '', textBlock(text), '')
    })
  }

  lines.push('## 验收用例', '')
  if (!acceptanceResult.cases?.length) {
    lines.push('当前没有足够的已确认规则生成验收用例。', '')
  } else {
    acceptanceResult.cases.forEach((item) => {
      lines.push(
        `### ${inlineText(item.caseId)} · ${inlineText(item.title)}`,
        '',
        '**Given**', '', textBlock(item.given), '',
        '**When**', '', textBlock(item.when), '',
        '**Then**', '', textBlock(item.then), '',
        '**规则依据**', '',
      )
      item.basis?.forEach((basis) => {
        lines.push(
          `- ${basis.sourceType === 'ANSWER' ? '用户回答' : '原文'} ${inlineText(basis.sourceId)}`,
          '',
          textBlock(basis.quote),
          '',
        )
      })
    })
  }

  lines.push('## 尚未确认的问题', '')
  const byId = new Map(questions.map((question) => [question.questionId, question]))
  if (!acceptanceResult.pendingQuestionIds?.length) {
    lines.push('本轮审查问题均已收到确认回答。', '')
  } else {
    acceptanceResult.pendingQuestionIds.forEach((questionId) => {
      const question = byId.get(questionId)
      lines.push(`- ${inlineText(questionId)}：${inlineText(question?.question || '问题内容不可用')}`)
    })
    lines.push('')
  }

  lines.push('## 模型提示', '')
  if (!acceptanceResult.warnings?.length) {
    lines.push('无。', '')
  } else {
    acceptanceResult.warnings.forEach((warning) => lines.push(`- ${inlineText(warning)}`))
    lines.push('')
  }

  lines.push('---', '', '本报告由已校验的数据生成，模型输出仍需业务人员复核。', '')
  return lines.join('\n')
}

export function reportFilename(date = new Date()) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `requirement-review-${year}-${month}-${day}.md`
}

export function downloadMarkdown(markdown, filename, browser = window) {
  const url = browser.URL.createObjectURL(new Blob([markdown], { type: 'text/markdown;charset=utf-8' }))
  const anchor = browser.document.createElement('a')
  anchor.href = url
  anchor.download = filename
  anchor.hidden = true
  browser.document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  browser.URL.revokeObjectURL(url)
}
