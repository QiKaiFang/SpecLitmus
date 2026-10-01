<script setup>
import { computed, nextTick, ref } from 'vue'
import { createApiClient } from './api/createApiClient'
import { ReviewApiError } from './api/apiClient'
import { reviewR1 } from './data/reviewR1'
import { clearDraft as clearStoredDraft, loadDraft, saveDraft } from './draftStore'
import { buildMarkdownReport, downloadMarkdown, reportFilename } from './reportExporter'

const { client, fixtureMode, fixtureScenario } = createApiClient()
const MAX_REQUIREMENT_LENGTH = 2000
const MAX_ANSWER_LENGTH = 500
let browserStorage = null
try {
  browserStorage = window.localStorage
} catch {
  // Browsers may deny storage access in restricted/privacy contexts.
}
const initialDraft = loadDraft(browserStorage)

const requirementText = ref(initialDraft.requirementText)
const reviewedRequirement = ref('')
const reviewId = ref('')
const questions = ref([])
const paragraphs = ref([])
const metadata = ref(null)
const answers = ref({})
const selectedQuestionId = ref(null)
const reviewError = ref('')
const reviewErrorRetryable = ref(false)
const isReviewing = ref(false)
const acceptanceResult = ref(null)
const acceptanceError = ref('')
const generatedRequirement = ref('')
const generatedAnswerSignature = ref('')
const isGenerating = ref(false)
const sessionExpired = ref(false)
const restoredAnswerDrafts = ref(initialDraft.answerDrafts)
const draftWasRestored = ref(Boolean(initialDraft.requirementText || initialDraft.answerDrafts.length))
const draftStorageError = ref('')
const exportMessage = ref('')

const characterCount = computed(() => requirementText.value.length)
const isBlankRequirement = computed(() => !requirementText.value.trim())
const isOverCharacterLimit = computed(() => characterCount.value > MAX_REQUIREMENT_LENGTH)
const isBusy = computed(() => isReviewing.value || isGenerating.value)
const canSubmitReview = computed(() =>
  !isBusy.value && !isBlankRequirement.value && !isOverCharacterLimit.value,
)
const hasReviewResult = computed(() => reviewedRequirement.value !== '')
const hasCurrentReview = computed(() =>
  hasReviewResult.value
  && reviewedRequirement.value === requirementText.value
  && !sessionExpired.value,
)
const isReviewStale = computed(() => hasReviewResult.value && !hasCurrentReview.value)
const backendFixtureMode = computed(() => metadata.value?.model === 'fixture')
const selectedQuestion = computed(() =>
  questions.value.find((question) => question.questionId === selectedQuestionId.value) || null,
)
const currentAnswerSignature = computed(() => JSON.stringify(
  questions.value.map((question) => [question.questionId, answers.value[question.questionId] || '']),
))
const hasOversizedAnswer = computed(() =>
  questions.value.some((question) => (answers.value[question.questionId] || '').length > MAX_ANSWER_LENGTH),
)
const hasCurrentAcceptance = computed(() =>
  Boolean(acceptanceResult.value)
  && hasCurrentReview.value
  && generatedRequirement.value === requirementText.value
  && generatedAnswerSignature.value === currentAnswerSignature.value,
)
const canExport = computed(() => hasCurrentAcceptance.value && !isBusy.value)
const pendingQuestions = computed(() => {
  const pendingIds = new Set(acceptanceResult.value?.pendingQuestionIds || [])
  return questions.value.filter((question) => pendingIds.has(question.questionId))
})
const sourceParagraphs = computed(() => {
  const selected = selectedQuestion.value
  return paragraphs.value.map((paragraph) => {
    if (selected?.paragraphId !== paragraph.id) {
      return { ...paragraph, before: paragraph.text, highlight: '', after: '', isHighlighted: false }
    }

    const start = selected.quoteStart - paragraph.start
    const end = selected.quoteEnd - paragraph.start
    const hasValidRange = Number.isInteger(selected.quoteStart)
      && Number.isInteger(selected.quoteEnd)
      && start >= 0
      && end > start
      && end <= paragraph.text.length
      && paragraph.text.slice(start, end) === selected.quote
    if (!hasValidRange) {
      return { ...paragraph, before: paragraph.text, highlight: '', after: '', isHighlighted: false }
    }
    return {
      ...paragraph,
      before: paragraph.text.slice(0, start),
      highlight: paragraph.text.slice(start, end),
      after: paragraph.text.slice(end),
      isHighlighted: true,
    }
  })
})

function clearReviewError() {
  reviewError.value = ''
  reviewErrorRetryable.value = false
}

function answeredQuestionDrafts() {
  return questions.value
    .map((question) => ({
      questionId: question.questionId,
      question: question.question,
      text: answers.value[question.questionId] || '',
    }))
    .filter((item) => item.text.trim())
}

function persistDraft() {
  const currentDrafts = answeredQuestionDrafts()
  const saved = saveDraft(browserStorage, {
    requirementText: requirementText.value,
    answerDrafts: currentDrafts.length ? currentDrafts : restoredAnswerDrafts.value,
  })
  draftStorageError.value = saved ? '' : '浏览器未能保存草稿，请检查存储权限。'
}

function clearDraft() {
  if (isBusy.value) return
  const cleared = clearStoredDraft(browserStorage)
  requirementText.value = ''
  reviewedRequirement.value = ''
  reviewId.value = ''
  questions.value = []
  paragraphs.value = []
  metadata.value = null
  answers.value = {}
  selectedQuestionId.value = null
  acceptanceResult.value = null
  generatedRequirement.value = ''
  generatedAnswerSignature.value = ''
  restoredAnswerDrafts.value = []
  draftWasRestored.value = false
  draftStorageError.value = cleared ? '' : '浏览器未能清除持久化草稿，请检查存储权限。'
  exportMessage.value = ''
  sessionExpired.value = false
  clearReviewError()
  acceptanceError.value = ''
}

function loadExample() {
  if (isBusy.value) return
  requirementText.value = reviewR1
  clearReviewError()
  acceptanceError.value = ''
  exportMessage.value = ''
  persistDraft()
}

async function submitReview() {
  if (!canSubmitReview.value) return

  const submittedText = requirementText.value
  const previousAnswerDrafts = answeredQuestionDrafts()
  if (previousAnswerDrafts.length) restoredAnswerDrafts.value = previousAnswerDrafts
  isReviewing.value = true
  clearReviewError()
  acceptanceError.value = ''
  reviewId.value = ''
  reviewedRequirement.value = ''
  questions.value = []
  paragraphs.value = []
  metadata.value = null
  answers.value = {}
  selectedQuestionId.value = null
  acceptanceResult.value = null
  generatedRequirement.value = ''
  generatedAnswerSignature.value = ''
  sessionExpired.value = false
  exportMessage.value = ''

  try {
    const result = await client.review(submittedText)
    reviewId.value = result.reviewId || ''
    questions.value = Array.isArray(result.questions) ? result.questions : []
    paragraphs.value = Array.isArray(result.paragraphs) ? result.paragraphs : []
    answers.value = Object.fromEntries(questions.value.map((question) => [question.questionId, '']))
    metadata.value = result.metadata || null
    reviewedRequirement.value = submittedText
    persistDraft()
  } catch (error) {
    reviewError.value = error instanceof Error ? error.message : '审查失败，请稍后重试。'
    reviewErrorRetryable.value = !(error instanceof ReviewApiError) || error.retryable
  } finally {
    isReviewing.value = false
  }
}

async function submitAcceptance() {
  if (!hasCurrentReview.value || isBusy.value || hasOversizedAnswer.value) return

  const submittedAnswers = questions.value
    .map((question) => ({ questionId: question.questionId, text: answers.value[question.questionId] || '' }))
    .filter((answer) => answer.text.trim().length > 0)

  isGenerating.value = true
  acceptanceError.value = ''
  try {
    const result = await client.acceptance(reviewId.value, submittedAnswers)
    acceptanceResult.value = result
    generatedRequirement.value = requirementText.value
    generatedAnswerSignature.value = currentAnswerSignature.value
    restoredAnswerDrafts.value = []
    persistDraft()
  } catch (error) {
    if (error instanceof ReviewApiError && error.code === 'SESSION_EXPIRED') {
      sessionExpired.value = true
    }
    acceptanceError.value = error instanceof Error ? error.message : '验收清单生成失败，请稍后重试。'
  } finally {
    isGenerating.value = false
  }
}

function onRequirementInput() {
  clearReviewError()
  acceptanceError.value = ''
  exportMessage.value = ''
  draftWasRestored.value = false
  persistDraft()
}

function onAnswerInput() {
  acceptanceError.value = ''
  exportMessage.value = ''
  restoredAnswerDrafts.value = []
  draftWasRestored.value = false
  persistDraft()
}

function exportReport() {
  if (!canExport.value) {
    exportMessage.value = '当前验收结果已过期，请重新审查或生成后再导出。'
    return
  }
  const now = new Date()
  const generatedAt = new Intl.DateTimeFormat('zh-CN', {
    dateStyle: 'long',
    timeStyle: 'medium',
  }).format(now)
  const markdown = buildMarkdownReport({
    generatedAt,
    requirementText: requirementText.value,
    questions: questions.value,
    answers: answers.value,
    acceptanceResult: acceptanceResult.value,
    reviewMetadata: metadata.value,
  })
  downloadMarkdown(markdown, reportFilename(now))
  exportMessage.value = `已下载 ${reportFilename(now)}`
}

function typeLabel(type) {
  return {
    AMBIGUITY: '歧义',
    POSSIBLE_CONFLICT: '可能冲突',
    MISSING_BOUNDARY: '缺少边界',
  }[type] || type
}

function basisLabel(type) {
  return type === 'ANSWER' ? '用户回答' : '原文'
}

function selectQuestion(question) {
  selectedQuestionId.value = question.questionId
  void nextTick(() => {
    document.getElementById(`source-paragraph-${question.paragraphId}`)?.scrollIntoView?.({
      behavior: 'smooth',
      block: 'center',
    })
  })
}
</script>

<template>
  <main class="page-shell">
    <header class="topbar">
      <a class="brand" href="/" aria-label="需求试纸首页">
        <span class="brand-mark" aria-hidden="true">试</span>
        <span>需求试纸</span>
      </a>
      <span v-if="fixtureMode || backendFixtureMode" class="fixture-badge">
        {{ fixtureMode ? `固定测试数据 · ${fixtureScenario}` : '后端固定测试模式' }}
      </span>
      <span v-else class="stage-label">S6 · 草稿与导出</span>
    </header>

    <section class="intro">
      <p class="eyebrow">SPECIFICATION LITMUS</p>
      <h1>让需求先经得起追问</h1>
      <p>补充业务规则，把确认过的内容整理成可追溯的验收清单。</p>
    </section>

    <div class="workspace">
      <section class="panel input-panel" aria-labelledby="input-heading">
        <div class="panel-heading">
          <div>
            <p class="section-index">01 / INPUT</p>
            <h2 id="input-heading">输入需求</h2>
          </div>
          <div class="heading-actions">
            <button class="text-button" type="button" :disabled="isBusy" @click="loadExample">载入示例</button>
            <button class="text-button text-button--danger" type="button" :disabled="isBusy" @click="clearDraft">清除草稿</button>
          </div>
        </div>

        <label class="visually-hidden" for="requirement-text">需求正文</label>
        <textarea
          id="requirement-text"
          v-model="requirementText"
          placeholder="粘贴一段功能需求，描述越具体越容易发现边界……"
          spellcheck="false"
          :disabled="isBusy"
          :aria-invalid="isOverCharacterLimit"
          aria-describedby="requirement-limit"
          @input="onRequirementInput"
        ></textarea>

        <div class="input-footer">
          <span :class="{ 'character-count--invalid': isOverCharacterLimit }">
            {{ characterCount }} / {{ MAX_REQUIREMENT_LENGTH }} 字符
          </span>
          <button class="primary-button" type="button" :disabled="!canSubmitReview" @click="submitReview">
            <span v-if="isReviewing" class="spinner" aria-hidden="true"></span>
            {{ isReviewing ? '正在审查…' : '审查需求' }}
          </button>
        </div>
        <p id="requirement-limit" class="input-hint" :class="{ 'input-hint--invalid': isOverCharacterLimit }">
          <template v-if="isBlankRequirement">请输入需求正文后再审查。</template>
          <template v-else-if="isOverCharacterLimit">已超过 {{ characterCount - MAX_REQUIREMENT_LENGTH }} 字符，请删减后再提交。</template>
          <template v-else>需求正文最多 {{ MAX_REQUIREMENT_LENGTH }} 字符。</template>
        </p>
        <div v-if="reviewError" class="error-notice" role="alert">
          <p class="error-message">{{ reviewError }}</p>
          <button v-if="reviewErrorRetryable" class="retry-button" type="button" :disabled="!canSubmitReview" @click="submitReview">重试</button>
        </div>
        <div v-if="isReviewStale" class="review-stale" role="status">
          {{ sessionExpired ? '审查会话已失效，请重新审查；当前文本仍保留。' : '原文已修改，旧审查已过期，请重新审查。' }}
        </div>
        <p v-if="draftWasRestored" class="draft-notice" role="status">已从此浏览器恢复草稿。为避免混用旧会话，请重新审查后再生成或导出。</p>
        <p v-if="draftStorageError" class="error-message draft-error" role="alert">{{ draftStorageError }}</p>
        <div v-if="restoredAnswerDrafts.length" class="restored-answers">
          <p class="output-label">未绑定的回答草稿</p>
          <article v-for="(item, index) in restoredAnswerDrafts" :key="`${item.questionId}-${index}`">
            <span>{{ item.questionId || `问题 ${index + 1}` }} · {{ item.question || '旧审查问题' }}</span>
            <p>{{ item.text }}</p>
          </article>
          <p>这些回答不会自动填入新卡片，请根据新审查结果重新确认。</p>
        </div>
      </section>

      <section class="results" aria-labelledby="results-heading" aria-live="polite">
        <div class="results-heading">
          <div>
            <p class="section-index">02 / REVIEW</p>
            <h2 id="results-heading">反例与澄清</h2>
          </div>
          <span v-if="hasReviewResult" class="result-count">{{ questions.length }} 项</span>
        </div>

        <div v-if="isReviewing" class="state-card loading-state">
          <span class="large-spinner" aria-hidden="true"></span>
          <p>正在阅读需求并整理需要确认的场景</p>
        </div>
        <div v-else-if="hasReviewResult && questions.length === 0" class="state-card empty-state compact-state">
          <span class="empty-icon" aria-hidden="true">✓</span>
          <h3>本次未发现需要确认的问题</h3>
          <p>仍可直接依据原文尝试生成验收清单。</p>
        </div>
        <div v-else-if="hasReviewResult" class="question-list">
          <article v-for="(item, index) in questions" :key="item.questionId" class="question-card" :class="{ 'question-card--selected': selectedQuestionId === item.questionId }">
            <div class="card-topline">
              <span class="card-number">{{ String(index + 1).padStart(2, '0') }}</span>
              <span class="type-badge">{{ typeLabel(item.type) }}</span>
              <button class="locate-button" type="button" @click="selectQuestion(item)">{{ item.paragraphId }} · 定位</button>
            </div>
            <blockquote><span class="quote-label">建议核对原文</span><span>{{ item.quote }}</span></blockquote>
            <p class="scenario-label">反例场景</p>
            <p class="scenario-text">{{ item.scenario }}</p>
            <div class="question-prompt"><span class="question-mark" aria-hidden="true">?</span><p>{{ item.question }}</p></div>
            <div v-if="item.possibleInterpretations?.length" class="interpretations">
              <span>可能的解释</span>
              <ul><li v-for="interpretation in item.possibleInterpretations" :key="interpretation">{{ interpretation }}</li></ul>
            </div>
            <label class="answer-label" :for="`answer-${item.questionId}`">你的确认（可选）</label>
            <textarea
              :id="`answer-${item.questionId}`"
              v-model="answers[item.questionId]"
              class="answer-input"
              :maxlength="MAX_ANSWER_LENGTH"
              placeholder="补充明确的业务规则；留空则保留为待确认。"
              :disabled="isBusy || !hasCurrentReview"
              @input="onAnswerInput"
            ></textarea>
            <div class="answer-footer">
              <span>{{ (answers[item.questionId] || '').length }} / {{ MAX_ANSWER_LENGTH }}</span>
              <span v-if="answers[item.questionId]?.trim()" class="answer-status">待生成</span>
            </div>
          </article>
        </div>
        <div v-else class="state-card initial-state">
          <span class="initial-icon" aria-hidden="true">✳</span>
          <h3>审查结果会显示在这里</h3>
          <p>载入示例或粘贴需求，然后开始首轮审查。</p>
        </div>
        <p v-if="hasReviewResult && metadata" class="metadata-line">模型 {{ metadata.model }} <span>·</span> Prompt {{ metadata.promptVersion }}</p>
      </section>

      <section class="panel acceptance-panel" aria-labelledby="acceptance-heading" aria-live="polite">
        <div class="results-heading">
          <div><p class="section-index">03 / ACCEPTANCE</p><h2 id="acceptance-heading">验收清单</h2></div>
          <span v-if="hasCurrentAcceptance" class="result-count">已生成</span>
        </div>

        <template v-if="hasCurrentReview">
          <div v-if="questions.length === 0" class="no-question-note">本次没有审查问题，可以直接依据原文尝试生成；依据不足时会保留说明。</div>
          <p v-else class="clarification-summary">已回答 {{ questions.filter((item) => answers[item.questionId]?.trim()).length }} / {{ questions.length }} 个问题</p>
          <button class="primary-button acceptance-button" type="button" :disabled="isBusy || hasOversizedAnswer" @click="submitAcceptance">
            <span v-if="isGenerating" class="spinner" aria-hidden="true"></span>
            {{ isGenerating ? '正在生成…' : '确认回答并生成验收清单' }}
          </button>
          <p v-if="acceptanceError" class="error-message acceptance-error" role="alert">{{ acceptanceError }}</p>
        </template>

        <div v-if="acceptanceResult && !hasCurrentAcceptance" class="acceptance-stale" role="status">这份验收结果对应旧的需求或回答，已标记为过期；请重新生成当前版本。</div>
        <div v-if="acceptanceResult" class="acceptance-output">
          <div v-if="acceptanceResult.warnings?.length" class="warning-list">
            <p class="output-label">需要人工确认</p>
            <ul><li v-for="warning in acceptanceResult.warnings" :key="warning">{{ warning }}</li></ul>
          </div>
          <div v-if="acceptanceResult.cases?.length" class="case-list">
            <article v-for="item in acceptanceResult.cases" :key="item.caseId" class="acceptance-case">
              <div class="case-heading"><span class="case-id">{{ item.caseId }}</span><h3>{{ item.title }}</h3></div>
              <dl class="given-when-then">
                <div><dt>GIVEN</dt><dd>{{ item.given }}</dd></div>
                <div><dt>WHEN</dt><dd>{{ item.when }}</dd></div>
                <div><dt>THEN</dt><dd>{{ item.then }}</dd></div>
              </dl>
              <div class="basis-list">
                <p class="output-label">规则依据</p>
                <ul>
                  <li v-for="basis in item.basis" :key="`${basis.sourceType}-${basis.sourceId}-${basis.quote}`">
                    <span class="basis-type">{{ basisLabel(basis.sourceType) }} {{ basis.sourceId }}</span><span>{{ basis.quote }}</span>
                  </li>
                </ul>
              </div>
            </article>
          </div>
          <div v-else class="no-case-note">当前没有足够的已确认规则生成验收用例。</div>
          <div v-if="pendingQuestions.length" class="pending-list">
            <p class="output-label">仍待确认</p>
            <ul><li v-for="item in pendingQuestions" :key="item.questionId"><span>{{ item.questionId }}</span>{{ item.question }}</li></ul>
          </div>
          <div v-else class="all-confirmed">本轮审查问题均已收到确认回答。</div>
          <p v-if="acceptanceResult.metadata" class="metadata-line">模型 {{ acceptanceResult.metadata.model }} <span>·</span> Prompt {{ acceptanceResult.metadata.promptVersion }}</p>
        </div>
        <div v-else-if="!hasCurrentReview" class="source-state">
          <span class="initial-icon" aria-hidden="true">⌁</span>
          <p>完成一次当前版本的审查后，在这里确认规则并生成 Given / When / Then 用例。</p>
        </div>
      </section>

      <section class="panel report-panel" aria-labelledby="report-heading">
        <div class="panel-heading">
          <div><p class="section-index">04 / REPORT</p><h2 id="report-heading">Markdown 报告</h2></div>
          <span v-if="hasCurrentAcceptance" class="result-count">当前版本</span>
        </div>
        <p class="report-description">导出原需求、审查问题、已确认回答、验收用例、规则依据、待确认项及模型版本。</p>
        <div v-if="acceptanceResult && !hasCurrentAcceptance" class="acceptance-stale" role="status">需求或回答已变化，旧报告不可导出；请为当前版本重新生成验收清单。</div>
        <button class="primary-button export-button" type="button" :disabled="!canExport" @click="exportReport">下载 Markdown</button>
        <p v-if="exportMessage" class="export-message" role="status">{{ exportMessage }}</p>
      </section>

      <section class="panel source-panel" aria-labelledby="source-heading">
        <div class="panel-heading">
          <div><p class="section-index">05 / EVIDENCE</p><h2 id="source-heading">原文依据</h2></div>
          <span v-if="selectedQuestion" class="source-selection">{{ selectedQuestion.paragraphId }}</span>
        </div>
        <div v-if="hasReviewResult && sourceParagraphs.length" class="source-list">
          <article v-for="paragraph in sourceParagraphs" :id="`source-paragraph-${paragraph.id}`" :key="paragraph.id" class="source-paragraph" :class="{ 'source-paragraph--selected': paragraph.isHighlighted }">
            <div class="source-paragraph-heading"><span>{{ paragraph.id }}</span><span>{{ paragraph.start }}–{{ paragraph.end }}</span></div>
            <p class="source-text">
              <template v-if="paragraph.isHighlighted"><span>{{ paragraph.before }}</span><mark>{{ paragraph.highlight }}</mark><span>{{ paragraph.after }}</span></template>
              <template v-else>{{ paragraph.before }}</template>
            </p>
          </article>
        </div>
        <div v-else class="source-state">
          <span class="initial-icon" aria-hidden="true">⌁</span>
          <p>点击问题卡片上的“定位”，可在这里核对完整原文与高亮引用。</p>
        </div>
      </section>
    </div>

    <footer class="page-footer">
      <span>用例只采用原文与本轮已确认的规则，模型输出仍需业务复核。</span>
      <span>未回答的问题会保留为待确认项</span>
    </footer>
  </main>
</template>
