package com.disaster.alert.alertapi.domain.event.evaluation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 라벨 CSV 를 evaluation_label 에 적재한다. 같은 dataset 을 다시 적재하면 기존 행을 모두 지우고 다시 넣는다(파일이 기준, 멱등).
 * 파싱을 삭제보다 먼저 끝내 잘못된 CSV 가 기존 라벨을 건드리지 못하게 하고, 삭제와 삽입은 한 트랜잭션이라 도중에 실패하면 함께 롤백된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationLabelLoader {

    private final EvaluationLabelRepository repository;

    /** @return 저장한 행 수 */
    @Transactional
    public int load(String dataset, String csvText) {
        if (dataset == null || dataset.isBlank()) {
            throw new IllegalArgumentException("dataset 이 비어 있습니다");
        }
        List<EvaluationLabelRecord> records = EvaluationLabelParser.parse(csvText);

        repository.deleteByDataset(dataset);
        // IDENTITY 키라 save 시점에 INSERT 가 나가므로 FK 위반이 이 호출 안에서 발생해 트랜잭션이 롤백된다.
        repository.saveAll(records.stream().map(r -> EvaluationLabel.of(dataset, r)).toList());

        log.info("평가 라벨 적재: dataset={}, {}건", dataset, records.size());
        return records.size();
    }
}
