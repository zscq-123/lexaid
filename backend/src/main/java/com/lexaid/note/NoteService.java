package com.lexaid.note;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lexaid.common.BizException;
import com.lexaid.content.EntityChecker;
import com.lexaid.content.EntityTitleService;
import com.lexaid.stats.StudyRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 笔记服务
 */
@Service
@RequiredArgsConstructor
public class NoteService {

    private final NoteMapper noteMapper;
    private final EntityChecker entityChecker;
    private final EntityTitleService entityTitleService;
    private final StudyRecordService studyRecordService;

    /**
     * 我的笔记；带 targetType/targetId 时筛选某实体下的笔记
     */
    public List<NoteVO> list(Long userId, String targetType, Long targetId) {
        LambdaQueryWrapper<Note> qw = new LambdaQueryWrapper<Note>()
                .eq(Note::getUserId, userId)
                .orderByDesc(Note::getId);
        if (StringUtils.hasText(targetType) && targetId != null) {
            qw.eq(Note::getTargetType, targetType).eq(Note::getTargetId, targetId);
        }
        List<Note> notes = noteMapper.selectList(qw);
        List<NoteVO> vos = new ArrayList<>();
        for (Note n : notes) {
            vos.add(new NoteVO(n.getId(), n.getTargetType(), n.getTargetId(),
                    entityTitleService.title(n.getTargetType(), n.getTargetId()),
                    n.getContent(), n.getCreatedAt(), n.getUpdatedAt()));
        }
        return vos;
    }

    public NoteVO create(Long userId, NoteRequest req) {
        entityChecker.checkExists(req.getTargetType(), req.getTargetId());
        Note n = new Note();
        n.setUserId(userId);
        n.setTargetType(req.getTargetType());
        n.setTargetId(req.getTargetId());
        n.setContent(req.getContent());
        noteMapper.insert(n);
        studyRecordService.record(userId, "NOTE", req.getTargetType(), req.getTargetId(),
                "保存笔记");
        return new NoteVO(n.getId(), n.getTargetType(), n.getTargetId(),
                entityTitleService.title(n.getTargetType(), n.getTargetId()),
                n.getContent(), n.getCreatedAt(), n.getUpdatedAt());
    }

    public NoteVO update(Long userId, Long id, NoteRequest req) {
        Note n = requireOwned(userId, id);
        n.setContent(req.getContent());
        noteMapper.updateById(n);
        return new NoteVO(n.getId(), n.getTargetType(), n.getTargetId(),
                entityTitleService.title(n.getTargetType(), n.getTargetId()),
                n.getContent(), n.getCreatedAt(), n.getUpdatedAt());
    }

    public void delete(Long userId, Long id) {
        requireOwned(userId, id);
        noteMapper.deleteById(id);
    }

    private Note requireOwned(Long userId, Long id) {
        Note n = noteMapper.selectById(id);
        if (n == null) {
            throw new BizException("笔记不存在");
        }
        if (!n.getUserId().equals(userId)) {
            throw new BizException(403, "无权操作他人的笔记");
        }
        return n;
    }
}
