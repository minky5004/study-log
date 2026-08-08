// 태그 칸은 쉼표로 여러 개를 받는데 datalist 는 입력 전체와 옵션을 맞춘다 — `jpa, 트` 를 치면
// 어느 옵션과도 맞지 않아 제안이 사라진다. 앞 토큰을 붙여 둔 값으로 옵션을 다시 써서
// 마지막 토큰에만 제안이 걸리게 한다. 고르면 칸 전체가 완성된 목록으로 바뀐다.
document.addEventListener('DOMContentLoaded', () => {
    const input = document.querySelector('[data-tag-suggest]');
    const list = input && document.getElementById(input.getAttribute('list'));
    if (!list) {
        return;
    }
    // 서버가 렌더한 옵션이 원본 — 제안 목록을 스크립트에 복사해 두지 않는다
    const tags = [...list.options].map(option => option.value);

    input.addEventListener('input', () => {
        const cut = input.value.lastIndexOf(',');
        const prefix = cut < 0 ? '' : input.value.slice(0, cut + 1).trimEnd() + ' ';
        const typed = input.value.slice(cut + 1).trim().toLowerCase();
        list.replaceChildren(...tags
                .filter(tag => tag.startsWith(typed))
                .map(tag => new Option('', prefix + tag)));
    });
});
