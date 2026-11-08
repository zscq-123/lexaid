package com.lexaid.content;

import org.junit.jupiter.api.Test;

import static com.lexaid.content.CourtRegionClassifier.GUIDING_DATASET;
import static com.lexaid.content.CourtRegionClassifier.classify;
import static org.assertj.core.api.Assertions.assertThat;

class CourtRegionClassifierTest {
    private CourtRegionClassifier.Court guiding(String docket, String holding) {
        return classify("CN", "最高人民法院（指导性案例）", docket, holding, GUIDING_DATASET);
    }

    @Test
    void followsTheExactDocketAcrossJudicialInstances() {
        String holding = "上海市虹口区人民法院于2009年6月23日作出（2009）虹民三（民）初字第912号民事判决。"
                + "上海市第二中级人民法院于2009年9月4日作出（2009）沪二中民二（民）终字第1508号民事判决。";
        assertThat(guiding("(2009)虹民三(民)初字第912 号", holding))
                .isEqualTo(new CourtRegionClassifier.Court("上海市", null, "虹口区", "上海市虹口区人民法院"));
        assertThat(guiding("(2009)沪二中民二(民)终字第1508号", holding).name())
                .isEqualTo("上海市第二中级人民法院");
        assertThat(guiding("(2009)虹民三(民)初字第9号", holding).name()).isNull();
    }

    @Test
    void unrelatedIssuersAndConflictingReferencesAreUnclassified() {
        String docket = "(2016)案字第1号";
        assertThat(guiding(docket, "上海市高级人民法院撤销公安局作出的" + docket + "决定。").name()).isNull();
        assertThat(guiding(docket, "上海市虹口区人民法院作出" + docket + "判决；北京市东城区人民法院作出"
                + docket + "判决。").name()).isNull();
        assertThat(guiding("\u00a0", "裁判结果").name()).isNull();
        assertThat(guiding(null, "最高人民法院发布指导性案例").name()).isNull();
    }

    @Test
    void identifiesHierarchiesForTheCaseYearIncludingMunicipalAndSpecialCourts() {
        assertThat(guiding("(2017)鲁06民初8号", "山东省烟台市中级人民法院于2018年4月1日作出（2017）鲁06民初8号判决。"))
                .isEqualTo(new CourtRegionClassifier.Court("山东省", "烟台市", null, "山东省烟台市中级人民法院"));
        assertThat(guiding("(2018)苏0724民初2732号", "江苏省灌南县人民法院于2018年11月15日作出(2018)苏0724民初2732号判决。"))
                .isEqualTo(new CourtRegionClassifier.Court("江苏省", "连云港市", "灌南县", "江苏省灌南县人民法院"));
        assertThat(guiding("(2016)京73行初3047号", "北京知识产权法院于2016年9月29日作出(2016)京73行初3047号判决。"))
                .isEqualTo(new CourtRegionClassifier.Court("北京市", null, null, "北京知识产权法院"));
        assertThat(classify("CN", "杭州市中级人民法院", null, null, null))
                .isEqualTo(classify("CN", "浙江省杭州市中级人民法院", null, null, null));
    }

    @Test
    void doesNotGuessWhereAnUnqualifiedCourtOrAnInternationalCourtIsLocated() {
        assertThat(classify("CN", "朝阳区人民法院", "(2012)案字第1号", null, null).province()).isNull();
        assertThat(classify("CN", "最高人民法院赔偿委员会", null, null, null))
                .isEqualTo(new CourtRegionClassifier.Court("全国", null, null, "最高人民法院赔偿委员会"));
        assertThat(classify("US", "美国联邦最高法院", null, null, null))
                .isEqualTo(new CourtRegionClassifier.Court(null, null, null, "美国联邦最高法院"));
    }
}
