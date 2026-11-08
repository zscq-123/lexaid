package com.lexaid.content;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lexaid.content.dto.ProvisionCategoryVO;
import org.apache.ibatis.annotations.Select;

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
}
