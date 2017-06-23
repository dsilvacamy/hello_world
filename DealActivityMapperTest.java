package com.ge.treasury.myfunding.mapper.myfunding;

import static org.junit.Assert.assertNotNull;

import java.text.ParseException;
import java.util.Date;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.boot.test.WebIntegrationTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

import com.ge.treasury.myfunding.MyFundingApplication;
import com.ge.treasury.myfunding.application.tests.MyFundingTest;
import com.ge.treasury.myfunding.dto.DealActivityDTO;
import com.ge.treasury.myfunding.utils.MyFundingLogger;


@Transactional
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = MyFundingApplication.class)
@WebIntegrationTest(randomPort = true)
@ConfigurationProperties(prefix = "application.yml")
public class DealActivityMapperTest extends MyFundingTest{

	@Autowired
	public DealActivityMapper activityMapper;
	
	@Test
	@Rollback(false)
	public void getDealActivityTest() {

		int dealid = 123;
		List<DealActivityDTO> activity = activityMapper.selectByDealId(dealid);

		MyFundingLogger.logInfo(this, "Deal Activity: " + activity.toString());
		assertNotNull(activity);
	}
	
	@Test
	@Rollback(false)
	public void insertDealActivityTest() throws ParseException {

		
		DealActivityDTO activity = new DealActivityDTO();
		activity.setDealId(4158);
		activity.setSso("999999009");
		activity.setUserName("ALex");
		activity.setActComment("test1");
		activity.setAction("Testing Deal Activity");
		activity.setUserRole("Tester");
		activity.setCreateUser("Camy");
		activity.setLastUpdateUser("Camy");
		activity.setCreateTimeStamp(new Date());
		activity.setLastUpdateTimestamp(new Date());
		
		int result = activityMapper.insert(activity);

		MyFundingLogger.logInfo(this, "Deal Activity row inserted : " + result);
		assertNotNull(result);
	}
	
	@Test
	    @Rollback(false)
	    public void updateDealTest() {
		 DealActivityDTO activity = activityMapper.selectByActivityId(21);
		 activity.setActComment("Update Check");
		 
		 int updateRecord= activityMapper.updateByPrimaryKey(activity);
		 MyFundingLogger.logInfo(this, "records Updated: " +updateRecord);
		 assertNotNull(activity);
	 }
	       

}
