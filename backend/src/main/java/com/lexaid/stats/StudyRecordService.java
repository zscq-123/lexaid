package com.lexaid.stats;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 学习记录埋点服务
 */
@Service
@RequiredArgsConstructor
public class StudyRecordService {

    private final StudyRecordMapper studyRecordMapper;

    public void record(Long userId, String actionType, String targetType, Long targetId, String detail) {
        record(userId, actionType, targetType, targetId, 0, detail);
    }

    public void record(Long userId, String actionType, String targetType, Long targetId,
                       int durationSec, String detail) {
        if (userId == null) {
            return;
        }
        StudyRecord r = new StudyRecord();
        r.setUserId(userId);
        r.setActionType(actionType);
        r.setTargetType(targetType);
        r.setTargetId(targetId);
        r.setDurationSec(durationSec);
        r.setDetail(detail);
        studyRecordMapper.insert(r);
    }
}
