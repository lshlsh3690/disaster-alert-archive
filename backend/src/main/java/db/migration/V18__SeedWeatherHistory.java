package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;
import org.springframework.core.io.ClassPathResource;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.zip.GZIPInputStream;

/**
 * 과거 ASOS 시간자료 일괄 시드 적재.
 *
 * <p><b>대상 데이터</b>: 2023-09-01 00:00 ~ 2026-05-21 11:00 KST 구간의
 * 시군구별 1시간 단위 ASOS 관측값 약 600만 row. {@code source = ASOS_HISTORY}.
 *
 * <p><b>적재 방식</b>: PostgreSQL {@code COPY FROM STDIN} — 600만 row 를 1~3분 안에 처리.
 * JDBC INSERT 대비 압도적으로 빠르다.
 *
 * <p><b>CSV 컬럼 (9개)</b>:
 * legal_district_code, observed_at, temperature, precipitation,
 * wind_speed, wind_direction, humidity, pressure, source
 * <ul>
 *   <li>{@code id}: BIGSERIAL 이 자동 채움</li>
 *   <li>{@code created_at}: V12 의 컬럼 DEFAULT {@code now()} 가 채움</li>
 *   <li>나머지: CSV 값 그대로</li>
 * </ul>
 *
 * <p><b>시드 파일 부재 시</b>: 적재 skip — Flyway 는 정상 완료로 기록. 운영/dev 모두 안전.
 *
 * <p><b>시드 생성 방법</b>: 별도 backfill 브랜치(폐기됨) 의 admin API 로 dev 적재 후
 * {@code COPY (... WHERE source='ASOS_HISTORY' ORDER BY observed_at, legal_district_code)
 * TO STDOUT WITH (FORMAT csv, HEADER true)} 로 export → {@code gzip -9} 압축.
 */
public class V18__SeedWeatherHistory extends BaseJavaMigration {

    private static final String GZIPPED_PATH = "data/weather_history.csv.gz";
    private static final String PLAIN_PATH = "data/weather_history.csv";

    /**
     * COPY 컬럼 리스트는 CSV 헤더 순서와 정확히 일치해야 한다.
     * created_at 은 컬럼 DEFAULT now() 가 채우므로 컬럼 리스트에서 제외.
     */
    private static final String COPY_SQL = """
            COPY weather_observation
                (legal_district_code, observed_at,
                 temperature, precipitation, wind_speed, wind_direction,
                 humidity, pressure, source)
            FROM STDIN
            WITH (FORMAT csv, HEADER true)
            """;

    @Override
    public void migrate(Context context) throws Exception {
        Connection conn = context.getConnection();
        CopyManager copyManager = conn.unwrap(PGConnection.class).getCopyAPI();

        try (InputStream raw = openSeedStream()) {
            if (raw == null) {
                System.out.println("[V18] 시드 파일 없음 — ASOS_HISTORY 적재 skip");
                return;
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(raw, StandardCharsets.UTF_8))) {
                long start = System.currentTimeMillis();
                long rows = copyManager.copyIn(COPY_SQL, reader);
                long elapsed = System.currentTimeMillis() - start;
                System.out.println("[V18] ASOS_HISTORY 시드 적재 완료: "
                        + rows + " rows, " + elapsed + " ms");
            }
        }
    }

    /**
     * gzip 우선, 없으면 비압축 fallback, 둘 다 없으면 null.
     * 운영 패키징 시 보통 gzip 만 포함.
     */
    private InputStream openSeedStream() throws Exception {
        ClassPathResource gzipped = new ClassPathResource(GZIPPED_PATH);
        if (gzipped.exists()) {
            return new GZIPInputStream(gzipped.getInputStream());
        }
        ClassPathResource plain = new ClassPathResource(PLAIN_PATH);
        if (plain.exists()) {
            return plain.getInputStream();
        }
        return null;
    }
}
