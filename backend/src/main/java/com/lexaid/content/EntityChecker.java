package com.lexaid.content;

import com.lexaid.common.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 实体存在性校验（收藏、笔记、AI 上下文共用）
 */
@Component
@RequiredArgsConstructor
public class EntityChecker {

    public static final String PROVISION = "PROVISION";
    public static final String CASE = "CASE";
    public static final String VIEWPOINT = "VIEWPOINT";
    public static final String QUESTION = "QUESTION";

    private final ProvisionMapper provisionMapper;
    private final CaseInfoMapper caseInfoMapper;
    private final ScholarViewpointMapper viewpointMapper;
    private final com.lexaid.exam.QuestionMapper questionMapper;

    public boolean exists(String type, Long id) {
        return switch (type) {
            case PROVISION -> provisionMapper.selectById(id) != null;
            case CASE -> caseInfoMapper.selectById(id) != null;
            case VIEWPOINT -> viewpointMapper.selectById(id) != null;
            case QUESTION -> questionMapper.selectById(id) != null;
            default -> false;
        };
    }

    public void checkExists(String type, Long id) {
        if (!exists(type, id)) {
            throw new BizException("目标内容不存在");
        }
    }
}
