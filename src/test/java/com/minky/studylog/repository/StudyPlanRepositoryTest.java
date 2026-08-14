package com.minky.studylog.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.minky.studylog.domain.PlanPriority;
import com.minky.studylog.domain.StudyPlan;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * 이 클래스가 지는 것은 <b>H2 로는 확인되지 않는 것</b>뿐이다 — 마이그레이션이 세운 CHECK 제약과
 * enum 의 저장 형태. 나머지 규칙(정렬)은 DB 를 밟지 않는 서비스 테스트에 있다.
 *
 * <p>배선의 근거는 {@link PostgresTestContainer} · {@link StudyLogRepositoryTest}.
 *
 * <p><b>DB 를 두 경로로 만진다.</b> 값을 넣고 읽는 것은 {@link TestEntityManager} 로 — 테스트
 * 트랜잭션 안이라야 저장한 행이 보인다. 반대로 제약 위반은 {@link DataSource} 에서 딴 커넥션으로
 * 낸다 — 하이버네이트를 거치면 예외가 감싸여 제약 이름이 최상위 메시지에서 사라진다.
 */
@DataJpaTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestContainer.class)
@ActiveProfiles("test")
class StudyPlanRepositoryTest {

    @Autowired StudyPlanRepository studyPlanRepository;
    @Autowired TestEntityManager entityManager;
    @Autowired DataSource dataSource;

    @Test
    @DisplayName("미완료 조회에 완료 항목이 섞이지 않음")
    void findsOnlyPending() {
        StudyPlan pending = studyPlanRepository.save(
                new StudyPlan("공부 메모장", null, PlanPriority.HIGH));
        StudyPlan done = studyPlanRepository.save(
                new StudyPlan("옵시디언 활용법", null, PlanPriority.NORMAL));
        done.toggle();
        studyPlanRepository.flush();

        assertThat(studyPlanRepository.findByDoneFalse())
                .extracting(StudyPlan::getTitle)
                .containsExactly(pending.getTitle());
    }

    /**
     * 완료 시각을 SQL 로 박아 넣는다. {@code toggle()} 두 번으로 만들면 두 시각이 벽시계
     * 해상도 안에 들어와 같은 값이 되고, 그때 순서는 정의되지 않아 이 단언이 흔들린다.
     */
    @Test
    @DisplayName("완료 목록은 최근 완료 순")
    void ordersDoneByCompletedAtDesc() {
        insertDone("옵시디언 활용법", "2026-08-12 09:00:00");
        insertDone("강의 계속 보기", "2026-08-14 09:00:00");
        insertDone("주식 게임", "2026-08-13 09:00:00");

        assertThat(studyPlanRepository.findByDoneTrueOrderByCompletedAtDesc())
                .extracting(StudyPlan::getTitle)
                .containsExactly("강의 계속 보기", "주식 게임", "옵시디언 활용법");
    }

    /**
     * {@code ORDINAL} 로 바뀌면 여기서만 드러난다 — 도메인도 화면도 enum 상수를 그대로 주고받아
     * 저장 형태가 숫자로 내려가도 전부 초록이다. 이 컬럼이 상수 이름을 담는 것이 뒷사람이
     * 상수를 사이에 끼워도 이미 저장된 행의 뜻이 밀리지 않는 근거다.
     */
    @Test
    @DisplayName("우선순위는 상수 이름으로 저장 — 순서 번호가 아니라")
    void storesPriorityAsName() {
        studyPlanRepository.saveAndFlush(new StudyPlan("ai 게임", null, PlanPriority.LOW));

        assertThat(nativeSingle("select priority from study_plan")).isEqualTo("LOW");
    }

    @Test
    @DisplayName("목록 밖 우선순위는 DB 가 거부 — 손으로 넣은 행까지 막는 자리")
    void rejectsUnknownPriority() {
        assertThatThrownBy(() -> executeOnOwnConnection("""
                insert into study_plan (title, priority, done, created_at)
                values ('주식 게임', 'URGENT', false, now())"""))
                .hasMessageContaining("ck_study_plan_priority");
    }

    /**
     * 애플리케이션에서는 {@link StudyPlan#toggle()} 하나가 둘을 함께 뒤집으므로 이 행은 만들 수
     * 없다. 막는 대상은 그 경로 밖 — 손으로 고친 행 · 뒤에 붙는 마이그레이션의 실수.
     */
    @Test
    @DisplayName("완료인데 완료 시각이 없는 행은 DB 가 거부")
    void rejectsDoneWithoutCompletedAt() {
        assertThatThrownBy(() -> executeOnOwnConnection("""
                insert into study_plan (title, priority, done, created_at)
                values ('카드 게임', 'NORMAL', true, now())"""))
                .hasMessageContaining("ck_study_plan_completed_at");
    }

    @Test
    @DisplayName("미완료인데 완료 시각이 남은 행도 거부 — 체크 해제가 시각을 지우지 않으면 나오는 모양")
    void rejectsPendingWithCompletedAt() {
        assertThatThrownBy(() -> executeOnOwnConnection("""
                insert into study_plan (title, priority, done, created_at, completed_at)
                values ('로그라이크 게임', 'NORMAL', false, now(), now())"""))
                .hasMessageContaining("ck_study_plan_completed_at");
    }

    private void insertDone(String title, String completedAt) {
        entityManager.getEntityManager().createNativeQuery("""
                        insert into study_plan (title, priority, done, created_at, completed_at)
                        values (?1, 'NORMAL', true, now(), cast(?2 as timestamp))""")
                .setParameter(1, title)
                .setParameter(2, completedAt)
                .executeUpdate();
    }

    private String nativeSingle(String sql) {
        return (String) entityManager.getEntityManager()
                .createNativeQuery(sql)
                .getSingleResult();
    }

    private void executeOnOwnConnection(String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
