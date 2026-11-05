// OpenAI API mock — 번역 요청 병합(single-flight) 전후 비교 측정용.
//
// 실제 OpenAI 대신 이 서버를 바라보게 해서, 같은 키로 동시 요청이 들어올 때
// 번역 API가 몇 번 불리는지를 센다. 호출마다 지연을 줘서 "둘 다 캐시 미스를 보고
// 각자 호출하는" 좁은 틈이 실제로 겹치게 만든다.
//
// 실행:
//   node backend/loadtest/mock/openai-mock.mjs
//   PORT=4000 DELAY_MS=300 node backend/loadtest/mock/openai-mock.mjs
//
// 백엔드가 이 서버를 보게 하려면 기동 시 base-url을 덮어쓴다:
//   ./gradlew bootRun --args='--spring.ai.openai.base-url=http://localhost:4000 --spring.ai.openai.api-key=mock'
//
// 엔드포인트
//   POST /v1/chat/completions  번역 호출. DELAY_MS 만큼 지연 후 고정 문자열 반환
//   POST /v1/embeddings        클러스터링이 혹시 타더라도 500이 나지 않게 하는 더미
//   GET  /__stats              누적 호출 수
//   POST /__reset              카운터 0으로

import http from 'node:http';

const PORT = Number(process.env.PORT ?? 4000);
const DELAY_MS = Number(process.env.DELAY_MS ?? 300);

// 번역문은 짧게 고정한다. OpenAiTranslationClient가 원문의 6배를 넘는 응답을
// "설명 혼입"으로 보고 버리므로, 길면 번역이 저장되지 않아 측정이 틀어진다.
const TRANSLATED = 'テスト翻訳';

const stats = { chatCalls: 0, embeddingCalls: 0, startedAt: new Date().toISOString() };

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

function sendJson(res, status, body) {
    const payload = JSON.stringify(body);
    res.writeHead(status, {
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(payload),
    });
    res.end(payload);
}

function readBody(req) {
    return new Promise((resolve) => {
        let raw = '';
        req.on('data', (chunk) => {
            raw += chunk;
        });
        req.on('end', () => resolve(raw));
    });
}

async function handleChat(req, res) {
    // 카운터는 지연 전에 올린다 — 동시에 들어온 호출을 전부 세기 위해서다.
    const seq = ++stats.chatCalls;
    await readBody(req);
    await sleep(DELAY_MS);
    console.log(`[chat] #${seq} (delay ${DELAY_MS}ms)`);
    sendJson(res, 200, {
        id: `chatcmpl-mock-${seq}`,
        object: 'chat.completion',
        created: Math.floor(Date.now() / 1000),
        model: 'gpt-4o-mock',
        choices: [
            {
                index: 0,
                message: { role: 'assistant', content: TRANSLATED },
                finish_reason: 'stop',
            },
        ],
        usage: { prompt_tokens: 1, completion_tokens: 1, total_tokens: 2 },
    });
}

async function handleEmbeddings(req, res) {
    const seq = ++stats.embeddingCalls;
    await readBody(req);
    console.log(`[embedding] #${seq}`);
    sendJson(res, 200, {
        object: 'list',
        model: 'text-embedding-3-small-mock',
        data: [{ object: 'embedding', index: 0, embedding: new Array(1536).fill(0) }],
        usage: { prompt_tokens: 1, total_tokens: 1 },
    });
}

const server = http.createServer(async (req, res) => {
    const path = (req.url ?? '').split('?')[0];

    if (req.method === 'POST' && path === '/v1/chat/completions') {
        return handleChat(req, res);
    }
    if (req.method === 'POST' && path === '/v1/embeddings') {
        return handleEmbeddings(req, res);
    }
    if (req.method === 'GET' && path === '/__stats') {
        return sendJson(res, 200, { ...stats, delayMs: DELAY_MS });
    }
    if (req.method === 'POST' && path === '/__reset') {
        stats.chatCalls = 0;
        stats.embeddingCalls = 0;
        stats.startedAt = new Date().toISOString();
        console.log('[reset] 카운터를 0으로 되돌렸다');
        return sendJson(res, 200, { ...stats, delayMs: DELAY_MS });
    }

    console.log(`[404] ${req.method} ${path}`);
    sendJson(res, 404, { error: { message: `no mock route for ${req.method} ${path}` } });
});

server.listen(PORT, () => {
    console.log(`OpenAI mock listening on http://localhost:${PORT} (delay ${DELAY_MS}ms)`);
    console.log('  POST /v1/chat/completions  POST /v1/embeddings  GET /__stats  POST /__reset');
});
