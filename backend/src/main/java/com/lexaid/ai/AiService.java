package com.lexaid.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexaid.ai.dto.ChatHistoryItem;
import com.lexaid.ai.dto.ChatMessageVO;
import com.lexaid.ai.dto.ChatRequest;
import com.lexaid.ai.dto.ChatResponse;
import com.lexaid.common.TextUtil;
import com.lexaid.common.UserContext;
import com.lexaid.content.CaseInfo;
import com.lexaid.content.CaseInfoMapper;
import com.lexaid.content.Provision;
import com.lexaid.content.ProvisionMapper;
import com.lexaid.content.ScholarViewpoint;
import com.lexaid.content.ScholarViewpointMapper;
import com.lexaid.exam.Question;
import com.lexaid.exam.QuestionMapper;
import com.lexaid.relation.RelatedGroups;
import com.lexaid.relation.RelatedItem;
import com.lexaid.relation.RelationService;
import com.lexaid.stats.StudyRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 问答服务：知识上下文注入 + 引用来源；未配置 Key 时降级为本地检索结果
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    private static final String SYSTEM_PROMPT =
            "你是 LexAid 法律学习助手。请用简洁准确的中文回答，必须结合提供的知识内容，回答末尾列出引用来源。"
                    + "无法确定时明确说明，并建议用户查阅原文。";
    private static final Pattern REFS_JSON = Pattern.compile("refs\\s*[:：]\\s*(\\[.*?])", Pattern.DOTALL);

    private final AiProperties aiProperties;
    private final ChatMessageMapper chatMessageMapper;
    private final ProvisionMapper provisionMapper;
    private final CaseInfoMapper caseInfoMapper;
    private final ScholarViewpointMapper viewpointMapper;
    private final QuestionMapper questionMapper;
    private final RelationService relationService;
    private final StudyRecordService studyRecordService;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    public ChatResponse chat(Long userId, ChatRequest req) {
        // 1. 组装知识上下文与引用来源
        String contextText = "";
        List<RelatedItem> refs = new ArrayList<>();
        if (StringUtils.hasText(req.getContextType()) && req.getContextId() != null) {
            ContextBundle bundle = loadContext(req.getContextType(), req.getContextId());
            contextText = bundle.text();
            refs.addAll(bundle.refs());
        } else {
            refs.addAll(localSearchRefs(req.getMessage()));
        }
        if (!refs.isEmpty()) {
            contextText = contextText + "\n\n本地检索到的相关材料：\n" + formatRefs(refs);
        }

        // 2. 保存用户提问并记录学习行为
        saveMessage(userId, "USER", req.getMessage(), null);
        studyRecordService.record(userId, "CHAT", req.getContextType(), req.getContextId(),
                "AI 提问：" + TextUtil.excerpt(req.getMessage(), 50));

        // 3. 未配置 AI_API_KEY：降级为本地检索结果
        if (!StringUtils.hasText(aiProperties.getApiKey())) {
            String answer = "AI 服务未配置，以下为本地相关检索结果。" + formatRefsForAnswer(refs);
            saveMessage(userId, "ASSISTANT", answer, refs);
            return new ChatResponse(answer, refs);
        }

        // 4. 调用 OpenAI 兼容接口
        try {
            String answer = callChatApi(req, contextText);
            List<RelatedItem> answerRefs = extractRefs(answer);
            if (answerRefs.isEmpty()) {
                answerRefs = refs;
            }
            saveMessage(userId, "ASSISTANT", answer, answerRefs);
            return new ChatResponse(answer, answerRefs);
        } catch (Exception e) {
            log.warn("AI 调用失败，降级返回本地结果", e);
            String answer = "AI 服务调用失败，以下为本地相关检索结果。" + formatRefsForAnswer(refs);
            saveMessage(userId, "ASSISTANT", answer, refs);
            return new ChatResponse(answer, refs);
        }
    }

    /**
     * 最近 20 条对话
     */
    public List<ChatMessageVO> history(Long userId) {
        List<ChatMessage> messages = chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getUserId, userId)
                        .orderByDesc(ChatMessage::getId)
                        .last("LIMIT 20"));
        List<ChatMessageVO> vos = new ArrayList<>();
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage m = messages.get(i);
            vos.add(new ChatMessageVO(m.getId(), m.getRole(), m.getContent(),
                    parseRefs(m.getRefs()), m.getCreatedAt()));
        }
        return vos;
    }

    private String callChatApi(ChatRequest req, String contextText) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", aiProperties.getModel());
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
        if (req.getHistory() != null) {
            for (ChatHistoryItem h : req.getHistory()) {
                String role = "assistant".equals(h.role()) ? "assistant" : "user";
                messages.add(Map.of("role", role, "content", h.content()));
            }
        }
        String userContent = StringUtils.hasText(contextText)
                ? req.getMessage() + "\n\n【参考资料】\n" + contextText
                : req.getMessage();
        messages.add(Map.of("role", "user", "content", userContent));
        body.put("messages", messages);
        body.put("temperature", 0.3);

        String json = webClientBuilder.build()
                .post()
                .uri(aiProperties.getBaseUrl() + "/chat/completions")
                .header("Authorization", "Bearer " + aiProperties.getApiKey())
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(60));
        try {
            JsonNode root = objectMapper.readTree(json);
            return root.path("choices").get(0).path("message").path("content").asText();
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("AI 响应解析失败", e);
        }
    }

    /**
     * 加载实体内容及关联摘要注入上下文
     */
    private ContextBundle loadContext(String type, Long id) {
        StringBuilder sb = new StringBuilder();
        List<RelatedItem> refs = new ArrayList<>();
        String self = "";
        switch (type) {
            case "PROVISION" -> {
                Provision p = provisionMapper.selectById(id);
                if (p != null) {
                    self = "【法条】" + p.getLawName() + " " + p.getArticleNo() + "\n" + p.getContent();
                    refs.add(new RelatedItem("PROVISION", p.getId(),
                            p.getLawName() + " " + p.getArticleNo(),
                            TextUtil.excerpt(p.getContent(), 80)));
                }
            }
            case "CASE" -> {
                CaseInfo c = caseInfoMapper.selectById(id);
                if (c != null) {
                    self = "【案例】" + c.getTitle() + "\n案情摘要：" + c.getSummary()
                            + "\n裁判要旨：" + c.getHolding();
                    refs.add(new RelatedItem("CASE", c.getId(), c.getTitle(),
                            TextUtil.excerpt(c.getSummary(), 80)));
                }
            }
            case "VIEWPOINT" -> {
                ScholarViewpoint v = viewpointMapper.selectById(id);
                if (v != null) {
                    self = "【学者观点】" + v.getTitle() + "（" + v.getScholarName() + "）\n"
                            + v.getSummary() + "\n" + (v.getContent() == null ? "" : v.getContent());
                    refs.add(new RelatedItem("VIEWPOINT", v.getId(),
                            v.getTitle() + "（" + v.getScholarName() + "）",
                            TextUtil.excerpt(v.getSummary(), 80)));
                }
            }
            case "QUESTION" -> {
                Question q = questionMapper.selectById(id);
                if (q != null) {
                    self = "【题目】" + q.getStem() + "\n解析：" + q.getAnalysis();
                    refs.add(new RelatedItem("QUESTION", q.getId(),
                            TextUtil.excerpt(q.getStem(), 60), q.getCategory() + "·" + q.getType()));
                }
            }
            default -> {
            }
        }
        sb.append(self);
        RelatedGroups groups = relationService.relatedGroups(type, id);
        List<RelatedItem> all = new ArrayList<>();
        all.addAll(groups.provisions());
        all.addAll(groups.cases());
        all.addAll(groups.viewpoints());
        all.addAll(groups.questions());
        int idx = 1;
        for (RelatedItem item : all) {
            refs.add(item);
            sb.append("\n").append("[引用").append(idx++).append("] ").append(item.title())
                    .append("：").append(item.summary());
        }
        return new ContextBundle(sb.toString(), refs);
    }

    /**
     * 无上下文时的本地检索兜底
     */
    private List<RelatedItem> localSearchRefs(String keyword) {
        List<RelatedItem> refs = new ArrayList<>();
        int limit = 3;
        if (!StringUtils.hasText(keyword)) {
            return refs;
        }
        // 先用整句检索，失败再回退到更短的核心词（中文无空格分词，取前缀做模糊兜底）
        List<String> candidates = new ArrayList<>();
        String cleaned = keyword.replaceAll("[？?。！!，,；;：:\\s]+", "");
        candidates.add(cleaned);
        if (cleaned.length() > 8) {
            candidates.add(cleaned.substring(0, 8));
        }
        if (cleaned.length() > 4) {
            candidates.add(cleaned.substring(0, 4));
        }
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        for (String kw : candidates) {
            if (kw.length() < 2) {
                continue;
            }
            for (Provision p : provisionMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Provision>()
                            .like(Provision::getContent, kw)
                            .or().like(Provision::getArticleNo, kw)
                            .or().like(Provision::getLawName, kw)
                            .last("LIMIT " + limit))) {
                if (seen.add("P" + p.getId())) {
                    refs.add(new RelatedItem("PROVISION", p.getId(),
                            p.getLawName() + " " + p.getArticleNo(), TextUtil.excerpt(p.getContent(), 80)));
                }
            }
            for (CaseInfo c : caseInfoMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CaseInfo>()
                            .like(CaseInfo::getTitle, kw)
                            .or().like(CaseInfo::getSummary, kw)
                            .last("LIMIT " + limit))) {
                if (seen.add("C" + c.getId())) {
                    refs.add(new RelatedItem("CASE", c.getId(), c.getTitle(),
                            TextUtil.excerpt(c.getSummary(), 80)));
                }
            }
            for (ScholarViewpoint v : viewpointMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ScholarViewpoint>()
                            .like(ScholarViewpoint::getTitle, kw)
                            .or().like(ScholarViewpoint::getSummary, kw)
                            .last("LIMIT " + limit))) {
                if (seen.add("V" + v.getId())) {
                    refs.add(new RelatedItem("VIEWPOINT", v.getId(),
                            v.getTitle() + "（" + v.getScholarName() + "）",
                            TextUtil.excerpt(v.getSummary(), 80)));
                }
            }
            for (Question q : questionMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Question>()
                            .like(Question::getStem, kw)
                            .last("LIMIT " + limit))) {
                if (seen.add("Q" + q.getId())) {
                    refs.add(new RelatedItem("QUESTION", q.getId(),
                            TextUtil.excerpt(q.getStem(), 60), q.getCategory() + "·" + q.getType()));
                }
            }
        }
        return refs;
    }

    private String formatRefs(List<RelatedItem> refs) {
        StringBuilder sb = new StringBuilder();
        for (RelatedItem r : refs) {
            sb.append("- [").append(typeLabel(r.type())).append("] ")
                    .append(r.title()).append("：").append(r.summary()).append("\n");
        }
        return sb.toString();
    }

    private String formatRefsForAnswer(List<RelatedItem> refs) {
        if (refs.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\n\n引用来源：\n");
        for (RelatedItem r : refs) {
            sb.append("- ").append(r.title()).append("\n");
        }
        return sb.toString();
    }

    private String typeLabel(String type) {
        return switch (type) {
            case "PROVISION" -> "法条";
            case "CASE" -> "案例";
            case "VIEWPOINT" -> "观点";
            case "QUESTION" -> "题目";
            default -> type;
        };
    }

    /**
     * 从 AI 回答中提取 refs JSON（或使用注入的引用）
     */
    private List<RelatedItem> extractRefs(String answer) {
        if (!StringUtils.hasText(answer)) {
            return List.of();
        }
        Matcher m = REFS_JSON.matcher(answer);
        if (m.find()) {
            try {
                return objectMapper.readValue(m.group(1), new TypeReference<List<RelatedItem>>() {
                });
            } catch (Exception ignored) {
            }
        }
        return List.of();
    }

    private List<RelatedItem> parseRefs(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<RelatedItem>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    private void saveMessage(Long userId, String role, String content, List<RelatedItem> refs) {
        ChatMessage m = new ChatMessage();
        m.setUserId(userId);
        m.setRole(role);
        m.setContent(content);
        m.setRefs(refs == null ? null : toJson(refs));
        chatMessageMapper.insert(m);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
    }

    private record ContextBundle(String text, List<RelatedItem> refs) {
    }
}
