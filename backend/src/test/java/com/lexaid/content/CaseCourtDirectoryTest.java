package com.lexaid.content;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.lexaid.content.dto.CourtOption;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CaseCourtDirectoryTest {
    @BeforeAll
    static void configureMapper() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "court-directory-test");
        assistant.setCurrentNamespace(CaseInfoMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, CaseInfo.class);
    }

    @Test
    void sameLeafIncludesStoredCourtAndOnlyTheMatchingGuidingCase() {
        String court = "浙江省杭州市萧山区人民法院";
        CaseInfo stored = storedCase(3L, "（2016）浙0109刑初23号", court);
        CaseInfo guiding = guidingCase(15L, "（2016）浙0109刑初1736号", court);
        CaseInfo other = guidingCase(96L, "（2011）杭滨民初字第885号", "浙江省杭州市滨江区人民法院");
        CaseInfoMapper mapper = mock(CaseInfoMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of(), List.of(stored), List.of(guiding, other),
                List.of(stored), List.of(guiding, other), List.of(stored), List.of(guiding, other));
        var directory = new CaseCourtDirectory(mapper);
        List<CourtOption> leaves = directory.options().stream().flatMap(CaseCourtDirectoryTest::leaves).toList();
        assertThat(leaves).hasSize(2);
        String key = leaves.stream().filter(n -> n.label().equals(court)).findFirst().orElseThrow().value();

        var query = new LambdaQueryWrapper<CaseInfo>();
        assertThat(directory.applyFilter(query, key)).isTrue();
        assertThat(query.getSqlSegment()).contains("jurisdiction =", "id IN").doesNotContain("court IN");
        assertThat(query.getParamNameValuePairs().values()).contains("CN", 3L, 15L)
                .doesNotContain(96L, other.getCourt());
        assertThat(directory.applyFilter(new LambdaQueryWrapper<>(), key + "-missing")).isFalse();
    }

    @Test
    void identicalStoredCourtNamesInDifferentHistoricalProvincesKeepSeparateMemberships() {
        String court = "云阳县人民法院";
        var rows = List.of(storedCase(1L, "（1990）民初1号", court), storedCase(2L, "（2015）民初2号", court));
        CaseInfoMapper mapper = mock(CaseInfoMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of(), rows, List.of(), rows, List.of(), rows, List.of());
        var directory = new CaseCourtDirectory(mapper);
        var leaves = directory.options().stream().flatMap(CaseCourtDirectoryTest::leaves).toList();
        assertThat(leaves).hasSize(2);
        for (var leaf : leaves) {
            boolean older = leaf.value().contains("四川省");
            assertThat(leaf.value()).contains(older ? "四川省" : "重庆市");
            var query = new LambdaQueryWrapper<CaseInfo>();
            assertThat(directory.applyFilter(query, leaf.value())).isTrue();
            assertThat(query.getSqlSegment()).contains("id IN");
            assertThat(query.getParamNameValuePairs().values()).contains(older ? 1L : 2L).doesNotContain(older ? 2L : 1L);
        }
    }

    private static Stream<CourtOption> leaves(CourtOption node) {
        return node.children().isEmpty() ? Stream.of(node) : node.children().stream().flatMap(CaseCourtDirectoryTest::leaves);
    }

    private static CaseInfo storedCase(long id, String caseNo, String court) {
        CaseInfo row = new CaseInfo();
        row.setId(id);
        row.setJurisdiction("CN");
        row.setCourt(court);
        row.setCaseNo(caseNo);
        return row;
    }

    private static CaseInfo guidingCase(long id, String caseNo, String court) {
        CaseInfo row = storedCase(id, caseNo, "最高人民法院（指导性案例）");
        row.setSourceDataset(CourtRegionClassifier.GUIDING_DATASET);
        row.setHolding(court + "于2016年11月9日作出" + caseNo + "刑事判决。");
        return row;
    }
}
