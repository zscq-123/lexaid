package com.lexaid.content;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lexaid.content.dto.ProvisionCategoryVO;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/**
 * 法条 Mapper
 */
public interface ProvisionMapper extends BaseMapper<Provision> {

    @Select("""
            SELECT DISTINCT jurisdiction, category
            FROM legal_provision
            WHERE category REGEXP '[^[:space:]]'
            ORDER BY category, jurisdiction
            """)
    List<ProvisionCategoryVO> selectCategories();

    @Select("""
            SELECT DISTINCT jurisdiction,law_name FROM legal_provision
            WHERE chapter IS NOT NULL AND TRIM(chapter) != ''
            ORDER BY jurisdiction,law_name
            """)
    List<ProvisionOutline.Code> outlineCodes();

    @Select("""
            SELECT chapter FROM legal_provision
            WHERE jurisdiction=#{jurisdiction} AND law_name=#{code} AND source_dataset IS NOT NULL
              AND chapter IS NOT NULL AND TRIM(chapter) != ''
            GROUP BY chapter ORDER BY MIN(id)
            """)
    List<String> outlinePaths(@Param("jurisdiction") String jurisdiction, @Param("code") String code);

    @Select("""
            <script>
            SELECT p.id,COALESCE((SELECT IF(COUNT(DISTINCT c.chapter)=1,MIN(c.chapter),NULL)
                FROM legal_provision c WHERE c.source_dataset='Chinese-Law-Corpus'
                  AND c.jurisdiction=p.jurisdiction AND c.law_name=p.law_name
                  AND c.article_no=p.article_no AND c.chapter IS NOT NULL AND TRIM(c.chapter) != ''),p.chapter) AS path
            FROM legal_provision p WHERE p.source_dataset IS NULL
            <if test="code != null">AND p.law_name=#{code} AND p.jurisdiction=#{jurisdiction}</if>
            <if test="ids != null">AND p.id IN
              <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </if>
            ORDER BY p.id
            </script>
            """)
    List<ProvisionOutline.SeedPath> seedPaths(@Param("jurisdiction") String jurisdiction, @Param("code") String code,
                                               @Param("ids") Collection<Long> ids);
}
