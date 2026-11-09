package com.lexaid.stats;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lexaid.common.PageResult;
import com.lexaid.content.EntityTitleService;
import com.lexaid.exam.AnswerRecord;
import com.lexaid.exam.AnswerRecordMapper;
import com.lexaid.stats.dto.CategoryAccuracyVO;
import com.lexaid.stats.dto.DailyStatVO;
import com.lexaid.stats.dto.OverviewVO;
import com.lexaid.stats.dto.StudyRecordVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 学习统计服务
 */
@Service
@RequiredArgsConstructor
public class StatsService {

    private final StudyRecordMapper studyRecordMapper;
    private final AnswerRecordMapper answerRecordMapper;
    private final EntityTitleService entityTitleService;

    public OverviewVO overview(Long userId) {
        Long totalAnswer = answerRecordMapper.selectCount(
                new LambdaQueryWrapper<AnswerRecord>().eq(AnswerRecord::getUserId, userId));
        Long totalCorrect = answerRecordMapper.selectCount(
                new LambdaQueryWrapper<AnswerRecord>()
                        .eq(AnswerRecord::getUserId, userId)
                        .eq(AnswerRecord::getIsCorrect, true));
        Long gradedAnswer = answerRecordMapper.selectCount(
                new LambdaQueryWrapper<AnswerRecord>()
                        .eq(AnswerRecord::getUserId, userId)
                        .isNotNull(AnswerRecord::getIsCorrect));
        Integer totalSec = studyRecordMapper.selectTotalDurationSec(userId);
        double accuracy = gradedAnswer == null || gradedAnswer == 0
                ? 0 : (double) totalCorrect / gradedAnswer;
        return new OverviewVO(
                totalSec == null ? 0 : totalSec / 60L,
                totalAnswer == null ? 0 : totalAnswer,
                totalCorrect == null ? 0 : totalCorrect,
                Math.round(accuracy * 10000) / 100.0,
                streakDays(userId));
    }

    /**
     * 连续学习天数：从今天（或昨天）起向前连续有记录的天数
     */
    private long streakDays(Long userId) {
        List<LocalDate> dates = studyRecordMapper.selectActivityDates(userId);
        Set<LocalDate> set = new HashSet<>(dates);
        LocalDate cursor = LocalDate.now();
        if (!set.contains(cursor)) {
            cursor = cursor.minusDays(1);
        }
        long streak = 0;
        while (set.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    public List<DailyStatVO> daily(Long userId, int days) {
        LocalDate from = LocalDate.now().minusDays(days - 1L);
        Map<LocalDate, long[]> stats = new HashMap<>();
        for (Map<String, Object> row : answerRecordMapper.selectDailyAnswer(userId, from)) {
            LocalDate d = ((java.sql.Date) row.get("d")).toLocalDate();
            long c = ((Number) row.get("c")).longValue();
            long ok = ((Number) row.get("ok")).longValue();
            stats.computeIfAbsent(d, k -> new long[3])[0] = c;
            stats.get(d)[1] = ok;
        }
        for (Map<String, Object> row : studyRecordMapper.selectDailyStudy(userId, from)) {
            LocalDate d = ((java.sql.Date) row.get("d")).toLocalDate();
            long s = ((Number) row.get("s")).longValue();
            stats.computeIfAbsent(d, k -> new long[3])[2] = s / 60L;
        }
        List<DailyStatVO> result = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            LocalDate d = from.plusDays(i);
            long[] v = stats.getOrDefault(d, new long[3]);
            result.add(new DailyStatVO(d, v[0], v[1], v[2]));
        }
        return result;
    }

    public List<CategoryAccuracyVO> categoryAccuracy(Long userId) {
        List<CategoryAccuracyVO> result = new ArrayList<>();
        for (Map<String, Object> row : answerRecordMapper.selectCategoryAccuracy(userId)) {
            String category = String.valueOf(row.get("category"));
            long total = ((Number) row.get("total")).longValue();
            long correct = ((Number) row.get("correct")).longValue();
            double accuracy = total == 0 ? 0 : (double) correct / total;
            result.add(new CategoryAccuracyVO(category, total, correct,
                    Math.round(accuracy * 10000) / 100.0, (String) row.get("discipline"), category));
        }
        return result;
    }

    public PageResult<StudyRecordVO> records(Long userId, long page, long size) {
        Page<StudyRecord> p = studyRecordMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<StudyRecord>()
                        .eq(StudyRecord::getUserId, userId)
                        .orderByDesc(StudyRecord::getId));
        List<StudyRecordVO> vos = new ArrayList<>();
        for (StudyRecord r : p.getRecords()) {
            String title = r.getTargetType() == null || r.getTargetId() == null
                    ? null : entityTitleService.title(r.getTargetType(), r.getTargetId());
            vos.add(new StudyRecordVO(r.getId(), r.getActionType(), r.getTargetType(),
                    r.getTargetId(), title, r.getDurationSec(), r.getDetail(), r.getCreatedAt()));
        }
        return new PageResult<>(vos, p.getTotal());
    }
}
