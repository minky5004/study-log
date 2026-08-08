// 태그 칸은 쉼표로 여러 개를 받는데 datalist 는 옵션을 입력 전체와 대조한다 — `jpa,트` 를 치면
// 어느 옵션과도 맞지 않아 제안이 사라진다. 앞 토큰을 붙인 값으로 옵션을 다시 써서 마지막 토큰에만
// 제안이 걸리게 한다.
//
// 앞부분은 사용자가 친 그대로 둔다. 쉼표 뒤 공백을 넣거나 빼는 것만으로 옵션이 입력의 접두가
// 아니게 되고, 브라우저는 그런 옵션을 전부 걸러내 제안이 통째로 사라진다 — 수정 폼 프리필이
// 쉼표 뒤에 공백을 넣지 않으므로 이 경로가 실제로 밟힌다.
document.addEventListener('DOMContentLoaded', () => {
    const input = document.querySelector('[data-tag-suggest]');
    const list = input && document.getElementById(input.getAttribute('list'));
    if (!list) {
        return;
    }
    // 서버가 렌더한 옵션이 원본 — 제안 목록을 스크립트에 복사해 두지 않는다
    const tags = [...list.options].map(option => option.value);

    const refresh = () => {
        const typed = input.value.slice(input.value.lastIndexOf(',') + 1).trimStart();
        const prefix = input.value.slice(0, input.value.length - typed.length);
        // 앞에서 이미 고른 태그는 뺀다 — `jpa,` 까지 친 순간 첫 제안이 `jpa,jpa` 가 되지 않게
        const chosen = new Set(prefix.split(',').map(token => token.trim().toLowerCase()));
        list.replaceChildren(...tags
                .filter(tag => tag.startsWith(typed.toLowerCase()) && !chosen.has(tag))
                .map(tag => new Option('', prefix + tag)));
    };

    // 수정 폼과 검증 실패 복귀는 값이 찬 채로 열린다 — 첫 타건 전에도 옵션이 입력과 맞아야 한다
    refresh();
    input.addEventListener('input', refresh);
});
