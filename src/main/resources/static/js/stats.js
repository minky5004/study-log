// 차트 카드. 숫자는 전부 /api/stats/* 에서 받는다 — 조회 범위도 분야 색 배정도 서버가
// 소유하므로 여기서 다시 정하지 않고 그리기만 한다.
//
// 그리는 것은 통계 화면의 네 카드가 아니라 그 페이지에 실제로 있는 카드다. 홈은 이 파일을
// 부르면서 잔디 하나만 두므로, 자리가 없는 카드는 조용히 건너뛴다.
//
// 빈 캔버스는 고장과 구별되지 않는다. 기록이 없을 때와 불러오지 못했을 때를 서로 다른 문구로
// 남기는 것이 이 파일에서 가장 자주 밟히는 분기다.

const EMPTY_TEXT = '아직 기록이 없습니다';
/**
 * 잔디와 추이는 최근 구간만 본다. 그 구간이 비었다고 "아직 기록이 없습니다" 라고 적으면
 * 두 해를 쌓고 석 달 쉰 사람에게 거짓말이 된다 — 카드 머리의 범위와 짝이 되는 문구를 쓴다.
 */
const WINDOW_EMPTY_TEXT = '이 기간에 기록이 없습니다';
const FAILED_TEXT = '통계를 불러오지 못했습니다';

const MINUTES_PER_HOUR = 60;
const DAYS_PER_WEEK = 7;

/** 도넛 조각 수. 넘는 분야는 "기타" 하나로 묶는다 — 조각이 얇아지면 이름도 색도 못 읽는다 */
const TOP_SLICES = 8;

/** 잔디 진하기 경계(분). 하루 4시간을 맨 위 칸으로 둔다 */
const HEATMAP_STEPS = [1, 60, 120, 240];

document.addEventListener('DOMContentLoaded', () => {
    // 잔디는 CSS Grid 라 CDN 이 막힌 브라우저에서도 그려진다 — 나머지보다 먼저 세운다
    card('heatmap', '/api/stats/heatmap', drawHeatmap, WINDOW_EMPTY_TEXT);

    // 여기서 멈추지 않으면 바로 아래 Chart.defaults 가 예외로 튀어 나머지 세 카드가 문구도 없이
    // 빈 칸으로 남는다. 이 파일이 막으려던 "고장과 구별되지 않는 화면" 이 그것이다
    if (typeof Chart === 'undefined') {
        ['trend', 'categories', 'hours'].forEach(name => note(boxOf(name), FAILED_TEXT));
        return;
    }

    Chart.defaults.color = cssVar('--muted');
    Chart.defaults.borderColor = cssVar('--line');
    Chart.defaults.font.family = getComputedStyle(document.body).fontFamily;

    card('categories', '/api/stats/categories', drawCategories);
    card('hours', '/api/stats/hours', drawHours);
    setUpTrend();
});

/**
 * 카드 하나의 수명 전체. 그리는 함수가 false 를 돌려주면 그릴 것이 없었다는 뜻이라
 * 빈 상태 문구로 바꾼다 — 판정을 각 함수에 맡기는 것은 "비어 있음" 의 모양이 넷 다 다르기 때문.
 */
async function card(name, url, render, emptyText = EMPTY_TEXT) {
    const box = boxOf(name);
    // 그 페이지에 없는 카드. 여기서 멈추지 않으면 자리도 없는 그림을 위해 요청이 나간다
    if (!box) {
        return;
    }
    try {
        if (!render(box, await fetchJson(url))) {
            note(box, emptyText);
        }
    } catch (error) {
        console.error(error);
        note(box, FAILED_TEXT);
    }
}

function boxOf(name) {
    return document.querySelector(`[data-chart="${name}"]`);
}

/**
 * 카드가 어느 구간을 그렸는지. 넷의 범위가 서로 달라 적지 않으면 같은 창으로 읽힌다.
 * 범위 자리는 카드와 따로 놓이므로 카드만 있고 이 자리가 없는 화면도 성립한다.
 */
function showRange(name, text) {
    const target = document.querySelector(`[data-range="${name}"]`);
    if (target) {
        target.textContent = text;
    }
}

async function fetchJson(url) {
    const response = await fetch(url);
    if (!response.ok) {
        throw new Error(`${url} → ${response.status}`);
    }
    return response.json();
}

function note(box, text) {
    // Chart.js 가 없을 때 세 카드를 한꺼번에 훑는 자리가 있어, 없는 카드가 여기까지 온다.
    // 홈은 CDN 을 아예 부르지 않으므로 그 경로가 늘 지나간다
    if (!box) {
        return;
    }

    const paragraph = document.createElement('p');
    paragraph.className = 'card-note';
    paragraph.textContent = text;
    box.replaceChildren(paragraph);
}

/** 카드마다 캔버스를 그때 만든다 — 빈 상태 문구로 한 번 갈아 끼운 뒤에도 다시 그릴 수 있게 */
function canvasIn(box) {
    const canvas = document.createElement('canvas');
    box.replaceChildren(canvas);
    return canvas;
}

// ── 잔디 히트맵 ──────────────────────────────────────

/**
 * 53주 × 7일 격자를 직접 짓는다. Chart.js 에 잔디가 없어 플러그인을 하나 더 매다는 대신
 * CSS Grid 로 그리는 쪽을 골랐다.
 *
 * 격자 범위는 응답이 들고 온 from·to 다. 기록이 있는 날만 행으로 오므로 여기서 "오늘" 을
 * 다시 세면 방문자 시간대에 따라 잔디가 통째로 한 칸 밀린다.
 */
function drawHeatmap(box, response) {
    // 범위는 비어 있어도 적는다 — "이 기간에 기록이 없습니다" 가 어느 기간인지 여기서 나온다
    showRange('heatmap', `${response.from} ~ ${response.to}`);

    const minutesByDate = new Map(response.days.map(day => [day.date, day.totalMinutes]));
    if (minutesByDate.size === 0) {
        return false;
    }

    const from = parseDate(response.from);
    const to = parseDate(response.to);
    const grid = element('div', 'heatmap-grid');
    const months = element('div', 'heatmap-months');

    let column = 1;
    let labelled = null;
    // 첫 칸은 from 이 속한 주의 월요일 — 주 중간에서 시작하면 열이 요일과 어긋난다
    for (const day = mondayOf(from); day <= to; day.setDate(day.getDate() + 1)) {
        grid.append(dayCell(day, from, minutesByDate));

        if (day.getDay() === 1) {
            // 달이 바뀐 열에 이름을 붙인다. "그 달 첫 월요일" 로 고르면 첫 열의 월요일이
            // 8일 이후일 때 맨 앞 몇 주가 이름 없이 남아 다음 달로 읽힌다
            if (day.getMonth() !== labelled) {
                labelled = day.getMonth();
                months.append(monthLabel(day, column));
            }
            column += 1;
        }
    }

    months.style.setProperty('--columns', column - 1);
    box.replaceChildren(
            wrap('heatmap-row', weekdayLabels(), wrap('heatmap-body', months, grid)),
            legend());
    return true;
}

function dayCell(day, from, minutesByDate) {
    // 첫 주에서 from 이전 칸은 자리만 지킨다 — 빼면 뒤 칸이 전부 한 줄씩 당겨진다
    if (day < from) {
        return element('span', 'hm-cell hm-blank');
    }

    const date = isoDate(day);
    const minutes = minutesByDate.get(date) || 0;
    const cell = element(minutes ? 'a' : 'span', `hm-cell hm-${heatmapLevel(minutes)}`);
    cell.title = `${date} · ${minutes ? durationText(minutes) : '기록 없음'}`;
    if (minutes) {
        // 잔디는 단색이라 분야를 말하지 못한다. 그날 무엇을 했는지는 목록의 날짜 필터가 답한다
        cell.href = `/logs?from=${date}&to=${date}`;
    }
    return cell;
}

function heatmapLevel(minutes) {
    return HEATMAP_STEPS.filter(step => minutes >= step).length;
}

function monthLabel(day, column) {
    const label = element('span', 'heatmap-month');
    label.textContent = `${day.getMonth() + 1}월`;
    label.style.gridColumn = column;
    return label;
}

/** 월·수·금만 적는다 — 일곱 줄을 다 채우면 11px 칸 옆에서 글자가 서로 붙는다 */
function weekdayLabels() {
    const labels = element('div', 'heatmap-weekdays');
    ['월', '', '수', '', '금', '', ''].forEach(text => {
        const label = element('span');
        label.textContent = text;
        labels.append(label);
    });
    return labels;
}

function legend() {
    const box = element('div', 'heatmap-legend');
    box.append(text('span', '적음'));
    for (let level = 0; level <= HEATMAP_STEPS.length; level += 1) {
        box.append(element('span', `hm-cell hm-${level}`));
    }
    box.append(text('span', '많음'));
    return box;
}

// ── 추이 · 분야 · 시간대 ─────────────────────────────

/**
 * 주·월 토글. 단위를 바꿀 때마다 서버에 다시 묻는다 — 12주와 12개월은 조회 범위가 달라
 * 한 번 받은 것을 접어서 만들 수 없다.
 */
function setUpTrend() {
    const box = boxOf('trend');
    const buttons = [...document.querySelectorAll('[data-trend-unit]')];
    let chart = null;
    let latest = 0;

    // 다시 그리기 전에 반드시 버린다 — 남겨 두면 옛 차트가 떨어져 나간 캔버스에 계속 매달린다
    const discard = () => {
        if (chart) {
            chart.destroy();
            chart = null;
        }
    };

    const load = async (unit) => {
        // 주·월을 빠르게 눌렀을 때 늦게 온 응답이 뒤엣것을 덮지 않게 한다 — 누른 버튼은 월인데
        // 그려진 눈금은 주가 되는 어긋남이 여기서 난다
        const request = ++latest;
        buttons.forEach(button =>
                button.setAttribute('aria-pressed', String(button.dataset.trendUnit === unit)));
        try {
            const buckets = await fetchJson(`/api/stats/trend?unit=${unit}`);
            if (request !== latest) {
                return;
            }
            discard();
            showRange('trend', `최근 ${buckets.length}${unit === 'week' ? '주' : '개월'}`);
            if (buckets.every(bucket => bucket.totalMinutes === 0)) {
                note(box, WINDOW_EMPTY_TEXT);
                return;
            }
            chart = drawTrend(box, buckets, unit);
        } catch (error) {
            console.error(error);
            if (request !== latest) {
                return;
            }
            discard();
            note(box, FAILED_TEXT);
        }
    };

    buttons.forEach(button => button.addEventListener('click', () => load(button.dataset.trendUnit)));
    load('week');
}

function drawTrend(box, buckets, unit) {
    const minutes = buckets.map(bucket => bucket.totalMinutes);
    return new Chart(canvasIn(box), {
        type: 'bar',
        data: {
            labels: buckets.map(bucket => bucketLabel(bucket.bucket, unit)),
            datasets: [{ data: minutes.map(toHours), backgroundColor: cssVar('--accent') }]
        },
        options: barOptions(minutes)
    });
}

function drawCategories(box, points) {
    if (points.length === 0) {
        return false;
    }

    const top = points.slice(0, TOP_SLICES);
    const labels = top.map(point => point.categoryName);
    const minutes = top.map(point => point.totalMinutes);
    const colors = top.map(point => catColor(point.colorIndex));

    const rest = points.slice(TOP_SLICES)
            .reduce((sum, point) => sum + point.totalMinutes, 0);
    if (rest > 0) {
        labels.push('기타');
        minutes.push(rest);
        colors.push(cssVar('--muted'));
    }

    const total = minutes.reduce((sum, value) => sum + value, 0);
    new Chart(canvasIn(box), {
        type: 'doughnut',
        data: {
            labels,
            // 조각 테두리를 카드 바탕색으로 둬야 인접한 두 분야가 한 덩어리로 보이지 않는다
            datasets: [{ data: minutes, backgroundColor: colors, borderColor: cssVar('--panel'),
                borderWidth: 2 }]
        },
        options: {
            maintainAspectRatio: false,
            cutout: '55%',
            plugins: {
                legend: { position: 'right', labels: { boxWidth: 10, boxHeight: 10 } },
                tooltip: {
                    callbacks: {
                        label: (item) => `${durationText(item.raw)}`
                                + ` · ${Math.round(item.raw / total * 100)}%`
                    }
                }
            }
        }
    });
    return true;
}

function drawHours(box, minutesByHour) {
    if (minutesByHour.every(minutes => minutes === 0)) {
        return false;
    }

    new Chart(canvasIn(box), {
        type: 'bar',
        data: {
            labels: minutesByHour.map((_, hour) => `${hour}시`),
            datasets: [{ data: minutesByHour.map(toHours), backgroundColor: cssVar('--cat-1') }]
        },
        // 24칸에 눈금을 다 적으면 글자가 서로 붙는다. 세 시간마다 하나만 남긴다
        options: barOptions(minutesByHour, {
            callback: function (value, index) {
                return index % 3 === 0 ? this.getLabelForValue(value) : '';
            }
        })
    });
    return true;
}

/**
 * 막대 둘이 쓰는 축 설정. 값을 시간으로 바꿔 담는 것은 세로축이 분이면 읽는 사람이
 * 매번 60 으로 나눠야 하기 때문 — 원본 분은 툴팁이 그대로 보여 준다.
 */
function barOptions(minutes, xTicks = {}) {
    return {
        maintainAspectRatio: false,
        plugins: {
            legend: { display: false },
            tooltip: { callbacks: { label: (item) => durationText(minutes[item.dataIndex]) } }
        },
        scales: {
            x: {
                grid: { display: false },
                ticks: xTicks
            },
            y: {
                beginAtZero: true,
                border: { display: false },
                // 소수 눈금이 서면 "0.5시간" 이 축에 뜬다
                ticks: { precision: 0, callback: (value) => `${value}시간` }
            }
        }
    };
}

// ── 값 다루기 ────────────────────────────────────────

/** 서버의 DisplayText.duration 과 같은 규칙. 분 단위 총량을 화면에 그대로 내보내지 않는다 */
function durationText(minutes) {
    const hours = Math.floor(minutes / MINUTES_PER_HOUR);
    const rest = minutes % MINUTES_PER_HOUR;
    if (hours === 0) {
        return `${rest}분`;
    }
    return rest === 0 ? `${hours}시간` : `${hours}시간 ${rest}분`;
}

function toHours(minutes) {
    return minutes / MINUTES_PER_HOUR;
}

/** 주 버킷은 그 주 월요일 날짜, 월 버킷은 연-월. 12칸 안에서는 연도를 적지 않아도 읽힌다 */
function bucketLabel(bucket, unit) {
    const [, month, day] = bucket.split('-');
    return unit === 'week' ? `${Number(month)}/${Number(day)}` : `${Number(month)}월`;
}

/**
 * "2026-08-03" 을 현지 자정으로 읽는다. Date 생성자에 그대로 넘기면 UTC 자정으로 잡혀
 * 한국에서는 9시간 당겨진 날짜가 되고, 잔디가 하루씩 밀린다.
 */
function parseDate(iso) {
    const [year, month, day] = iso.split('-').map(Number);
    return new Date(year, month - 1, day);
}

function isoDate(date) {
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

function pad(number) {
    return String(number).padStart(2, '0');
}

function mondayOf(date) {
    const monday = new Date(date);
    // getDay() 는 일요일이 0 이라 그대로 빼면 일요일이 한 주 앞 열로 간다
    monday.setDate(monday.getDate() - (monday.getDay() + 6) % DAYS_PER_WEEK);
    return monday;
}

// ── DOM · 색 ─────────────────────────────────────────

/** 색 값은 스타일시트 하나만 갖는다 — 여기에 hex 를 적으면 팔레트가 두 벌이 된다 */
function cssVar(name) {
    return getComputedStyle(document.documentElement).getPropertyValue(name).trim();
}

function catColor(index) {
    return cssVar(`--cat-${index}`) || cssVar('--muted');
}

function element(tag, className) {
    const node = document.createElement(tag);
    if (className) {
        node.className = className;
    }
    return node;
}

function text(tag, content) {
    const node = element(tag);
    node.textContent = content;
    return node;
}

function wrap(className, ...children) {
    const node = element('div', className);
    node.append(...children);
    return node;
}
