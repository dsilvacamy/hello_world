package com.ge.treasury.myfunding.mapper.myfunding;

import java.util.List;

import com.ge.treasury.myfunding.dto.DealActivityDTO;

public interface DealActivityMapper {
	
	int insert(DealActivityDTO activity);
	
	int insertSelective(DealActivityDTO activity);
	
	List<DealActivityDTO> selectByDealId(Integer dealId);
	
	DealActivityDTO selectByActivityId(Integer activity);
	
	int updateByPrimaryKey(DealActivityDTO activity);
	
	int updateByPrimaryKeySelective(DealActivityDTO activity);

}
