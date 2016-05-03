package com.travelzen.tops.finance.account.service.impl.electronic;

import java.io.Serializable;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.annotation.Resource;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;
import org.perf4j.StopWatch;
import org.perf4j.slf4j.Slf4JStopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;

import com.google.common.eventbus.EventBus;
import com.ibatis.common.jdbc.exception.NestedSQLException;
import com.travelzen.framework.core.exception.BizException;
import com.travelzen.framework.core.util.RandomUtil;
import com.travelzen.framework.core.wrapper.Pagination;
import com.travelzen.framework.redis.client.SpringRedisClient;
import com.travelzen.framework.util.TZBeanUtils;
import com.travelzen.tops.common.dict.core.ReturnCode;
import com.travelzen.tops.common.dict.finance.enums.OrderStatus;
import com.travelzen.tops.common.dict.finance.enums.OrderType;
import com.travelzen.tops.common.dict.finance.enums.PayType;
import com.travelzen.tops.common.dict.finance.enums.PaymentOrg;
import com.travelzen.tops.common.dict.finance.enums.PaymentType;
import com.travelzen.tops.common.dict.finance.enums.ProductType;
import com.travelzen.tops.finance.account.billing.vo.BillingAccountBeanFactory;
import com.travelzen.tops.finance.account.bo.ElectronicAccountBO;
import com.travelzen.tops.finance.account.bo.FreezeBO;
import com.travelzen.tops.finance.account.bo.RewardPointAccountBO;
import com.travelzen.tops.finance.account.dao.ITblFreezeDAO;
import com.travelzen.tops.finance.account.dao.gen.ElectronicOrderDAO;
import com.travelzen.tops.finance.account.dao.gen.ElectronicProductDAO;
import com.travelzen.tops.finance.account.dao.gen.TblAccountDAOImpl;
import com.travelzen.tops.finance.account.dao.gen.TblCouponAccountDetailDAOImpl;
import com.travelzen.tops.finance.account.dao.gen.TblCouponAccountInoutDetailDAOImpl;
import com.travelzen.tops.finance.account.dao.gen.TblCouponDAOImpl;
import com.travelzen.tops.finance.account.dao.gen.TblElectronicAccountHistoryDAOImpl;
import com.travelzen.tops.finance.account.dao.gen.TblElectronicRechargeDAOImpl;
import com.travelzen.tops.finance.account.dao.gen.TblElectronicRefundDAOImpl;
import com.travelzen.tops.finance.account.dao.gen.TblRefundDAO;
import com.travelzen.tops.finance.account.dto.CouponAccountBean;
import com.travelzen.tops.finance.account.dto.CouponFreezeBean;
import com.travelzen.tops.finance.account.dto.ElectronicBean;
import com.travelzen.tops.finance.account.dto.OrderRefundBean;
import com.travelzen.tops.finance.account.dto.RewardBackBean;
import com.travelzen.tops.finance.account.dto.RewardPointDistributionBean;
import com.travelzen.tops.finance.account.dto.TraceTradeBean;
import com.travelzen.tops.finance.account.enums.BalanceType;
import com.travelzen.tops.finance.account.enums.CheckoutStatus;
import com.travelzen.tops.finance.account.enums.EcardProductState;
import com.travelzen.tops.finance.account.enums.EcardRefundStatus;
import com.travelzen.tops.finance.account.enums.ElectronicInoutType;
import com.travelzen.tops.finance.account.enums.ElectronicInterType;
import com.travelzen.tops.finance.account.enums.FreezeStatus;
import com.travelzen.tops.finance.account.enums.FreezeType;
import com.travelzen.tops.finance.account.enums.InoutType;
import com.travelzen.tops.finance.account.enums.ManageType;
import com.travelzen.tops.finance.account.enums.NotifyStatus;
import com.travelzen.tops.finance.account.enums.OperateType;
import com.travelzen.tops.finance.account.enums.OrderPayStatus;
import com.travelzen.tops.finance.account.enums.RefundStatus;
import com.travelzen.tops.finance.account.enums.RefundType;
import com.travelzen.tops.finance.account.enums.TbShopName;
import com.travelzen.tops.finance.account.enums.TradeStatus;
import com.travelzen.tops.finance.account.enums.WorkflowType;
import com.travelzen.tops.finance.account.event.RewardBackEvent;
import com.travelzen.tops.finance.account.event.TPRefundEvent;
import com.travelzen.tops.finance.account.factory.TblAccountFactory;
import com.travelzen.tops.finance.account.po.gen.ElectronicOrder;
import com.travelzen.tops.finance.account.po.gen.ElectronicProduct;
import com.travelzen.tops.finance.account.po.gen.ElectronicProductExample;
import com.travelzen.tops.finance.account.po.gen.TblAccount;
import com.travelzen.tops.finance.account.po.gen.TblAccountExample;
import com.travelzen.tops.finance.account.po.gen.TblAccountHistory;
import com.travelzen.tops.finance.account.po.gen.TblCheckout;
import com.travelzen.tops.finance.account.po.gen.TblCheckoutExample;
import com.travelzen.tops.finance.account.po.gen.TblCouponAccountDetail;
import com.travelzen.tops.finance.account.po.gen.TblCouponAccountDetailExample;
import com.travelzen.tops.finance.account.po.gen.TblCouponAccountInoutDetail;
import com.travelzen.tops.finance.account.po.gen.TblCouponAccountInoutDetailExample;
import com.travelzen.tops.finance.account.po.gen.TblElectronicAccountHistory;
import com.travelzen.tops.finance.account.po.gen.TblElectronicAccountHistoryExample;
import com.travelzen.tops.finance.account.po.gen.TblElectronicAccountHistoryExample.Criteria;
import com.travelzen.tops.finance.account.po.gen.TblElectronicRecharge;
import com.travelzen.tops.finance.account.po.gen.TblElectronicRechargeExample;
import com.travelzen.tops.finance.account.po.gen.TblElectronicRefund;
import com.travelzen.tops.finance.account.po.gen.TblElectronicRefundExample;
import com.travelzen.tops.finance.account.po.gen.TblElectronicSaleRules;
import com.travelzen.tops.finance.account.po.gen.TblElectronicSaleRulesExample;
import com.travelzen.tops.finance.account.po.gen.TblFreeze;
import com.travelzen.tops.finance.account.po.gen.TblFreezeExample;
import com.travelzen.tops.finance.account.po.gen.TblOrderPay;
import com.travelzen.tops.finance.account.po.gen.TblRewardPointAccountDetail;
import com.travelzen.tops.finance.account.po.gen.TblWorkFlow;
import com.travelzen.tops.finance.account.processor.refund.DistributorRefundProcessor;
import com.travelzen.tops.finance.account.service.AbstractService;
import com.travelzen.tops.finance.account.service.IAsyncCheckoutService;
import com.travelzen.tops.finance.account.service.ICouponService;
import com.travelzen.tops.finance.account.service.IElectronicOrderService;
import com.travelzen.tops.finance.account.service.IRewardPointService;
import com.travelzen.tops.finance.account.service.impl.discount.CouponService;
import com.travelzen.tops.finance.account.service.impl.discount.ElectronicService;
import com.travelzen.tops.finance.account.service.impl.freeze.FinanceAccountFreezeService;
import com.travelzen.tops.finance.account.util.AccountMessage;
import com.travelzen.tops.finance.account.util.TransactionUtil;
import com.travelzen.tops.member.common.vo.Customer;
@Service("electronicOrderService")
public class ElectronicOrderServiceImpl extends AbstractService implements IElectronicOrderService{

	@Resource
	private ElectronicProductDAO electronicProductDAO;
	@Resource
	private ElectronicOrderDAO electronicOrderDAO;
	
	@Resource
	private ElectronicService electronicService;
	
	@Resource
	private TblElectronicAccountHistoryDAOImpl electronicAccountHistoryDAO;
	
	@Resource
	private TblAccountDAOImpl tblAccountDAO;
	
	@Resource
	private TblCouponAccountDetailDAOImpl couponAccountDao;

	@Resource
	private TblCouponDAOImpl tblCouponDao;
	
	@Resource
	private TblCouponAccountInoutDetailDAOImpl couponInoutDetailDao;
	@Resource
	private TblElectronicRechargeDAOImpl rechargeDAO;
	
	@Resource
	private FinanceAccountFreezeService financeAccountFreezeService;
	
	@Resource 
	private ElectronicAccountBO electronicAccountBo;
	
	@Resource
	private TblElectronicRefundDAOImpl tblElectronicRefundDAO;
	
	@Resource FreezeBO freezeBo;
	
	@Resource CouponService couponService;
	
	@Autowired
	 protected TblAccountFactory accountFactory;
	
    @Resource(name = "billing_account_bean_factory")
    private BillingAccountBeanFactory billing_account_bean_factory;
    @Resource
    private TblRefundDAO finance_refund_dao;
	   
    @Resource(name="financeEventBus")
	private EventBus financeEventBus;
	@Resource(name="couponBackEventBus")
	private EventBus couponBackEventBus;
	@Resource
	private DistributorRefundProcessor distributorRefundProcessor;
	@Resource(name="rewardPointService")
	private IRewardPointService reward_point_service;
	@Resource(name="reward_point_account_bo")
	private RewardPointAccountBO reward_point_account_bo;
	@Resource(name="couponService")
	private ICouponService coupon_service;
	
	@Resource
	private ITblFreezeDAO<TblFreeze> finance_tbl_freeze_dao;
	
	@Autowired
	private IAsyncCheckoutService finance_async_checkout_service;
    
	private Logger logger = LoggerFactory.getLogger(ElectronicOrderServiceImpl.class);
	
  /*  public String IP = TopsConfReader.getConfContent("properties/redis.properties", "redis.ip", ConfScope.R);
	public int PORT = Integer.valueOf(TopsConfReader.getConfContent("properties/redis.properties", "redis.port", ConfScope.R));
	public int expire = Integer.valueOf(TopsConfReader.getConfContent("properties/redis.properties", "redis.timeoutForSetnx", ConfScope.R));
	*/  
	private final RedisTemplate<Serializable, Serializable> redisTemplate = SpringRedisClient.getInstance().getSpringRedisDao().getRedisTemplate();
 
	@Override
	public void placeElectronicOrder(String id, long num,ElectronicOrder order) throws Exception {
		// TODO Auto-generated method stub
		ElectronicProduct product = electronicProductDAO.selectByPrimaryKey(id);
		
		if(product.getStatus().equals(EcardProductState.OFFLINE.name())){
				throw new BizException("ecard product has offlined productId :", id);
		}
	 /*   Jedis jedis = new Jedis(IP,PORT);
	    SpringRedisClient redisClient = SpringRedisClient.getInstance();
	*/   // 删除这个key当且仅当这个key存在而且值是我期望的那个值。
	   // 避免误删其他线程得到的锁，
	    String randomKey = RandomUtil.getRandomStr(10);
	    String productId = product.getProductId();
	    
	    //使用redis实现分布式锁的效果，从而不用使用数据库锁
	    boolean result  = false;
	    for(int i =0; i < 10; i ++){
	    	 result = redisTemplate.opsForValue().setIfAbsent(productId, randomKey);
	    	//加锁成功
	    	//if(reqCount.intValue()== 1) break;
	    	 if(result) break;
	    	 else Thread.sleep(100);
	    }
	    logger.info("set result: {}", result);
	    if(!result){
	    	logger.info("can not find productId in redis : productId {}", product.getProductId());
	    	throw new BizException("concurrency too high,please retry later");
	    }
	    
	    redisTemplate.expire(productId, 300, TimeUnit.SECONDS);
	//	jedis.expire(product.getProductId(), expire);
		try{
			if(product.getRepository() < num){
				logger.info("can not find productId in redis : productId {}", product.getProductId());
		    	throw new BizException("repository is not enough");
			}else{
				product.setRepository(product.getRepository() - num);
				product.setSold(product.getSold() + num);
			}
			
			setOrder(product, order,num);
			commitOrder(product, order);
		}finally{
			 // 删除这个key当且仅当这个key存在而且值是期望的那个值。
			   // 避免误删其他线程得到的锁，
			if(redisTemplate.opsForValue().get(productId).equals(randomKey)){
				redisTemplate.delete(productId);
			}
		}
	/*	if(jedis.get(productId).equals(randomKey)){
			jedis.del(productId);
		}*/
	}
	public void commitOrder(ElectronicProduct product, ElectronicOrder order) throws SQLException{
		electronicProductDAO.updateByPrimaryKey(product);
		try {			
			electronicOrderDAO.insert(order);
		} catch (NestedSQLException e) {
			order.setOrderId(createOrderId());
			electronicOrderDAO.insert(order);
		}
	}
	
	private void setOrder(ElectronicProduct product, ElectronicOrder order, long num){

		Date date = new Date();
		order.setOrderId(createOrderId());
		order.setSerialNumber(createSerialNumber());
		order.setRelatedProductId(product.getProductId());					
	
		order.setNum(num);
		order.setTotalPrice(num*product.getActualPaidMoney());
		order.setOrderStatus(OrderPayStatus.AWAIT.name());
		order.setCreateDate(date);
	
		order.setUpdateDate(date);
	
		order.setIsSync(false);
	}
	
	private String createOrderId() {
		return "EC"+ new SimpleDateFormat("yyMMddHHmmss").format(new Date()) + getSalt(2);
	}
	
	private String createSerialNumber() {
		return "ECOD"+ getSalt(4) + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
	}
	
	
	private String getSalt(int num) {
		StringBuilder result = new StringBuilder();
		for (int i=0;i<num;i++) {
			result.append((int)(Math.random()*10));
		}
		return result.toString();
	}
	@Override
	public void applyRefund(String orderId) throws Exception{
		// TODO Auto-generated method stub
		ElectronicOrder electronicOrder = electronicOrderDAO.selectByPrimaryKey(orderId);
		
		String newAccountCustomerId = getNewAccountIdByOrderId(orderId);
		
		//锁定该电子卡生成的新账户
		TblAccount account = finance_account_bo.getAccountByCustomerIdInAllStateAndLock(newAccountCustomerId);
		String accountId = account.getAccountId();
		/**
		 * 1.	判断能否退款
		 * 2.	老账户{
		 * 		根据主账户生成退款单
		 * 
		 *		 }
		 * 3.	新账户{
		 * 		a 主账户生成根据原单号生成冻结记录
		 * `		b 电子卡账户生成冻结记录
		 * 		c 代金券账户冻结对应的代金券
		 * 	}
		 */	
		boolean canRefund = canRefund(accountId, orderId);
	  
		if(!electronicOrder.getOrderStatus().equals(OrderPayStatus.PAY.name())){
			canRefund =  false;
		}
		logger.info("electornic order: {}  can refund check result: {}", orderId, canRefund);
		if(canRefund){
	//		 financeAccountFreezeService.freezeFromAvailableAmount(accountId, FreezeType.AMOUNT_FREEZE, freezeAmount, orderId, summary, user);
			String refundId = generateRefundOrder(orderId,electronicOrder, newAccountCustomerId);
			generateFreezeOrder(refundId,orderId, account);
			
			electronicOrder.setOrderStatus(RefundType.REFUNDING.name());
			electronicOrderDAO.updateByPrimaryKey(electronicOrder);
			
		//	frozenCoupon();
			freezeCoupon(orderId,electronicOrder.getTotalPrice());
			
		}else{
			throw new BizException("can't refund order , orderId : {"+ orderId +"}" );
		}
	}
	
	private void freezeCoupon(String orderId, long totalPrice) throws Exception {
		// TODO Auto-generated method stub
		
		TblCouponAccountDetailExample example = new TblCouponAccountDetailExample();
		example.createCriteria().andAwardRelatedOrderIdEqualTo(orderId);
		
		List<TblCouponAccountDetail> details =couponAccountDao.selectByExample(example);
		
		logger.info("electornic order: {}  freeze coupon t: {}", orderId, TZBeanUtils.describe(details));
		
		for(TblCouponAccountDetail detail : details){
			
			CouponAccountBean bean = new CouponAccountBean();
			bean.setCouponCode(detail.getCouponCode());
			bean.setAccountId(detail.getAccountId());
			bean.setCustomerId(detail.getCustomerId());
			bean.setOperatorId(getUser());
			bean.setOrderAmount(totalPrice);
			bean.setProductType(ProductType.ELECTRONIC_CARD);
			bean.setRelatedOrderId(orderId);
			
			couponService.freezeCouponForEcardRefund(bean);
		}
	}
	private String generateRefundOrder(String orderId, ElectronicOrder electronicOrder, String newAccountCustomerId) throws Exception {
		// TODO Auto-generated method stub
		ElectronicProduct product = electronicProductDAO.selectByPrimaryKey(electronicOrder.getRelatedProductId());
		
		TblElectronicRefund record = finance_tbl_account_factory.createTblElectronicRefund();
		record.setCustomerId(newAccountCustomerId);
		record.setAmount(electronicOrder.getNum());
		record.setApplyDate(new Date());
		record.setOriginOrderId(orderId);
		record.setProductId(product.getProductId());
		record.setStatus(EcardRefundStatus.APPLIED.name());
		record.setProductName(product.getProductName());
		record.setTotalAmount(electronicOrder.getTotalPrice());
		record.setUnitPrice(product.getActualPaidMoney());
		record.setCreater(electronicOrder.getPurchaserId());
		tblElectronicRefundDAO.insert(record);
		logger.info("electornic order: {}  refund order : {}", orderId, TZBeanUtils.describe(record));
		return record.getRefundId();
	}
	public void requestRefund(String refundId) throws Exception{
		// TODO Auto-generated method stub
	/*	ElectronicOrder electronicOrder = electronicOrderDAO.selectByPrimaryKey(orderId);
	
		String customerId = electronicOrder.getPurchaserId();
		//锁定该电子卡生成的新账户
		TblAccount account = finance_account_bo.getAccountByCustomerIdInAllStateAndLock(customerId);
		String accountId = account.getAccountId();*/
		/**
		 * 1.	判断能否退款
		 * 2.	老账户{
		 * 		根据主账户生成退款单发起退款请求
		 * 	//	根据退单号生成冻结记录
		 *		 }
		 * 3.	新账户{
		 * 		a 根据主账户生成冻结记录进行扣款操作
		 * `		b 根据电子卡账户生成冻结记录进行扣款操作
		 * 		c 扣除对应的代金券
		 * 	}
		 */
		logger.info("electornic order: {} request refund", refundId);
		notifyRefund(refundId);
		TblElectronicRefund record = tblElectronicRefundDAO.selectByPrimaryKey(refundId);

		record.getProductName();
		changeOriginOrderStatus(record.getOriginOrderId());
		recoverProductRepository(record.getAmount(),record.getProductId());
		changeActivateHistory(record.getOriginOrderId());
		//新账户
		TblFreeze accountFreeze = financeAccountFreezeService.getFreezeByOrderIdAndFreezeStatus(record.getOriginOrderId());
		freezeBo.deductFrozenAmountForEcardOrder(accountFreeze.getFreezeId(), "电子卡退单支出新主账户冻结金额",getUser());
		//b
		ElectronicBean electronicBean = new ElectronicBean();
		electronicBean.setAmount(accountFreeze.getFreezeAmount());
		electronicBean.setCustomerId(record.getCustomerId());
		electronicBean.setInterType(InoutType.DEDUCT.name());
		electronicBean.setOperateDate(new Date());
		electronicBean.setOperator(getUser());
		electronicBean.setOriginOrderId(record.getOriginOrderId());
		electronicBean.setProductType(ProductType.ELECTRONIC_CARD);
		electronicBean.setRelatedOrderId(record.getRefundId());
		electronicService.deductFromFrozenAmount(electronicBean);
		//c
		TblCouponAccountDetailExample example = new TblCouponAccountDetailExample();
		example.createCriteria().andAwardRelatedOrderIdEqualTo(record.getOriginOrderId());
		
		List<TblCouponAccountDetail> details =couponAccountDao.selectByExample(example);
		
		for(TblCouponAccountDetail detail : details){
			
			CouponFreezeBean bean = new CouponFreezeBean();
			bean.setCustomerId(detail.getCustomerId());
			bean.setOperatorId(getUser());
			bean.setProductType(ProductType.ELECTRONIC_CARD);
			bean.setRelatedOrderId(record.getOriginOrderId());
			
			bean.setCouponAmount(tblCouponDao.selectByPrimaryKey(detail.getCouponId()).getAmount());
			bean.setOperateDate(new Date());
			
			couponService.decuctFrozenCouponForEcard(bean);
		}

	//	checkoutElectronicAccount();
	}
	private void changeActivateHistory(String originOrderId) {
		
		TblElectronicRechargeExample example = new TblElectronicRechargeExample();
		example.createCriteria().andTbOrderIdEqualTo(originOrderId);
		// TODO Auto-generated method stub
		try {
			List<TblElectronicRecharge> list = rechargeDAO.selectByExample(example);
			TblElectronicRecharge record = list.get(0);
			record.setRefundedNum(record.getActivedNum());
			record.setActivedNum(0L);
			rechargeDAO.updateByPrimaryKey(record);
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	private void recoverProductRepository(Long amount, String productId) {
		// TODO Auto-generated method stub
		ElectronicProduct product;
		try {
			product = electronicProductDAO.selectByPrimaryKey(productId);
			product.setRepository(product.getRepository() + amount);
			product.setSold(product.getSold() - amount);
			electronicProductDAO.updateByPrimaryKey(product);
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	private void changeOriginOrderStatus(String originOrderId) {
		// TODO Auto-generated method stub
		logger.info("ecard order {} has refunded", originOrderId);
		// TODO Auto-generated method stub
		ElectronicOrder order = new ElectronicOrder();
		order.setOrderId(originOrderId);
		order.setOrderStatus(RefundType.REFUNDED.name());
		order.setUpdateDate(new Date());
		order.setUpdateUser(getUser());
		try {
			electronicOrderDAO.updateByPrimaryKeySelective(order);
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	@Override
	public void rejectRefund(String refundId, String remark) throws Exception{
		// TODO Auto-generated method stub
		/**
		 * 1.	判断能否退款
		 * 2.	老账户{
		 * 		将主账户的退款记录置为rejected
		 *		 }
		 * 3.	新账户{
		 * 		解冻主账户的金额
		 * 		解冻电子卡账户金额
		 * 		解冻代金券账户对应的代金券
		 * 	}
		 */
		TblElectronicRefund refund = getApplyingRefundRecordById(refundId);
		
		rejecteRefund(refund,remark);

		String orderId= refund.getOriginOrderId();
		
		withdrawOriginOrderStatus(orderId);
		
		TblFreeze freeze = financeAccountFreezeService.getFreezeByOrderIdAndFreezeStatus(orderId);

		freezeBO.unfreezeAmount(FreezeType.AMOUNT_FREEZE, freeze.getFreezeId(), null, "电子卡", getUser());
		
		unFreezeElectronicAccount(refund);
		unFreezeCoupon(orderId);
		logger.info("ecard order {} refund request rejected,", orderId);
	}
	private void withdrawOriginOrderStatus(String orderId) {
		logger.info("ecard order {} pay status back to pay", orderId);
		// TODO Auto-generated method stub
		ElectronicOrder order = new ElectronicOrder();
		order.setOrderId(orderId);
		order.setOrderStatus(OrderPayStatus.PAY.name());
		order.setUpdateDate(new Date());
		order.setUpdateUser(getUser());
		try {
			electronicOrderDAO.updateByPrimaryKeySelective(order);
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	private void unFreezeCoupon(String orderId) throws Exception {
		// TODO Auto-generated method stub
		TblCouponAccountDetailExample example = new TblCouponAccountDetailExample();
		example.createCriteria().andAwardRelatedOrderIdEqualTo(orderId);
		
		List<TblCouponAccountDetail> details =couponAccountDao.selectByExample(example);
		
		for(TblCouponAccountDetail detail : details){
			
			CouponFreezeBean bean = new CouponFreezeBean();
			bean.setCustomerId(detail.getCustomerId());
			bean.setOperatorId(getUser());
			bean.setProductType(ProductType.ELECTRONIC_CARD);
			bean.setRelatedOrderId(orderId);
			bean.setCouponAmount(tblCouponDao.selectByPrimaryKey(detail.getCouponId()).getAmount());
			bean.setOperateDate(new Date());
			couponService.unfreezeCouponForEcardRefund(bean);
		}
	}
	private void unFreezeElectronicAccount(TblElectronicRefund refund) throws Exception {
		// TODO Auto-generated method stub
		ElectronicBean bean = new ElectronicBean();
		bean.setAmount(refund.getTotalAmount());
		bean.setCustomerId(refund.getCustomerId());
		bean.setOperateDate(new Date());
		bean.setOperator(getUser());
		bean.setOriginOrderId(refund.getOriginOrderId());
		bean.setProductType(ProductType.ELECTRONIC_CARD);
		bean.setRelatedOrderId(refund.getRefundId());
		bean.setInterType(ElectronicInterType.UNFREEZE.name());
		
		electronicService.unFreezeAmount(bean);
	}
	@Override
	public void approveRefund(String refundId, String verifyRemark) throws Exception {
		// TODO Auto-generated method stub
		TblElectronicRefund refund = getApplyingRefundRecordById(refundId);
		refund.setVerifyRemark(verifyRemark);
		refund.setStatus(EcardRefundStatus.VERIFIED.name());
		refund.setVerifyDate(new Date());
		refund.setVerifier(getUser());
		tblElectronicRefundDAO.updateByPrimaryKeySelective(refund);
	}
	public void rejecteRefund(TblElectronicRefund refund, String verifyRemark) throws Exception {
		// TODO Auto-generated method stub
		refund.setVerifyRemark(verifyRemark);
		refund.setStatus(EcardRefundStatus.REJECTED.name());
		refund.setVerifyDate(new Date());
		refund.setVerifier(getUser());
		tblElectronicRefundDAO.updateByPrimaryKeySelective(refund);
	}
	
	
	private TblElectronicRefund getApplyingRefundRecordById(String refundId) throws SQLException {
		// TODO Auto-generated method stub
		TblElectronicRefundExample example = new TblElectronicRefundExample();
		
		example.createCriteria().andStatusEqualTo(EcardRefundStatus.APPLIED.name()).andRefundIdEqualTo(refundId);
		List<TblElectronicRefund> refundList = tblElectronicRefundDAO.selectByExample(example);
		
		if(refundList.size() ==1) {
			return refundList.get(0);
		}else{
			throw new BizException("can't find correct refund record");
		}
	}
	private void notifyRefund(String refundId) throws Exception {
		// TODO Auto-generated method stub
		TblElectronicRefundExample example = new TblElectronicRefundExample();
		
		example.createCriteria().andStatusEqualTo(EcardRefundStatus.VERIFIED.name()).andRefundIdEqualTo(refundId);
		List<TblElectronicRefund> refundList = tblElectronicRefundDAO.selectByExample(example);
		logger.info(" notify refund electornic refundId: {}  refund result: {}", refundId, TZBeanUtils.describe(refundList));
		if(refundList.size() ==1){
			TblElectronicRefund refund = refundList.get(0);
			OrderRefundBean bean = new OrderRefundBean();
			bean.setCustomerId(refund.getCreater());
			bean.setOrderId(refund.getRefundId());
			bean.setOrigOrderId(refund.getOriginOrderId());
			bean.setRefundAmount(-refund.getTotalAmount());
			bean.setReturnedFee(0);
			bean.setProductType(ProductType.ELECTRONIC_CARD);
			bean.setOrderType(OrderType.ELECTRONIC_REFUND.name());
			bean.setCreateOrderDate(new Date());
			bean.setOrderVerifyDate(refund.getVerifyDate());
			bean.setSummary(refund.getVerifyRemark());
			bean.setCreateOrderOperator(refund.getCreater());
			bean.setOperator(getUser());
			
			notifyFinanceRefund(bean);
			refund.setOperator(getUser());
			refund.setRefundDate(new Date());
			refund.setStatus(EcardRefundStatus.REFUNDED.name());
			tblElectronicRefundDAO.updateByPrimaryKey(refund);
		}else{
			throw new BizException("can't found verified refund order,ordersId = " +refundId);
		}
	}
	private void notifyFinanceRefund(OrderRefundBean refundReq) throws Exception {
		StopWatch stopWatch = new Slf4JStopWatch(perfLogger);
		stopWatch.start();
		logger.info("Enter function: [finance][electronicOrderService][notifyFinanceRefund].");
		logger.info(refundReq.toString());

		final Date date = new Date();
		final String customerId = refundReq.getCustomerId();
		final String orderId = refundReq.getOrderId();
		final String origOrderId = refundReq.getOrigOrderId();
		final List<String> ticketNo = refundReq.getTicketNo() == null ? new ArrayList<String>() : refundReq.getTicketNo();
		final String ticketName = refundReq.getTicketName();
		final long refundAmount = refundReq.getRefundAmount();
		final long returnedFee = refundReq.getReturnedFee();
		final ProductType productType = refundReq.getProductType();
		final OrderType orderType;
		final Date createOrderDate = refundReq.getCreateOrderDate();
		final Date orderVerifyDate = refundReq.getOrderVerifyDate();
		final String summary = refundReq.getSummary();
		final String hotelOrderSettlePeriod = refundReq.getHotelOrderSettlePeriod();
		final boolean hotelSettleState = refundReq.isHotelSettle();
		final String createOrderOperator = refundReq.getCreateOrderOperator();
		final String operator = StringUtils.isBlank(refundReq.getOperator()) ? getAutoUser() : refundReq.getOperator();
		final List<String> pnr = refundReq.getPnr() == null ? new ArrayList<String>() : refundReq.getPnr();
		final String orderGroupId = refundReq.getOrderGroupId();
		
		Customer customer = pCustomerService.getCustomerByKey(customerId);

		if ("FP".equals(refundReq.getOrderType()))
			orderType = OrderType.UNUSED_TICKET;
		else if ("TP".equals(refundReq.getOrderType()))
			orderType = OrderType.RETURN_TICKET;
		else if("GENERAL_REFUND".equals(refundReq.getOrderType()))
			orderType = OrderType.REFUND_GENERAL_ORDER;
		else if (OrderType.REFUND_HOTEL.name().equals(refundReq.getOrderType())
				|| OrderType.OFFLINE_REFUND_HOTEL.name().equals(refundReq.getOrderType())
				|| OrderType.PURCHASER_REFUND_HOTEL.name().equals(refundReq.getOrderType())
				|| OrderType.REFUND_INSURANCE.name().equals(refundReq.getOrderType())
				|| OrderType.VISA_SIGN_BACK.name().equals(refundReq.getOrderType())
				|| OrderType.JN_REFUND.name().equals(refundReq.getOrderType())
				|| OrderType.JN_SIGN_BACK.name().equals(refundReq.getOrderType())
				|| OrderType.GTA_REFUND_HOTEL.name().equals(refundReq.getOrderType())
				|| OrderType.REFUND_FREE_TRAVEL.name().equals(refundReq.getOrderType())
				|| OrderType.REFUND_LOCAL_TRAVEL.name().equals(refundReq.getOrderType())
				|| OrderType.REFUND_CRUISE.name().equals(refundReq.getOrderType())
				|| OrderType.NEW_VISA_SIGN_BACK.name().equals(refundReq.getOrderType())
				|| StringUtils.isNotBlank(refundReq.getOrderType()))
			orderType = OrderType.valueOf(refundReq.getOrderType());
		else
			orderType = null;

		/* 待扣款账户的信息 */
		TblAccount account = getAccountByCustomerIdInAllStateAndLock(customerId);

		// 逻辑判断：判断参数是否合法
		{
			if (createOrderDate == null || productType == null || orderType == null || origOrderId == null || origOrderId.length() == 0)
				throw new BizException(ReturnCode.FINANCE_WRONG_PARAMS_ERROR, AccountMessage.getMessage("WRONG_PARAMS_ERROR_MSG"));
			if (refundAmount > 0)
				throw new BizException(ReturnCode.FINANCE_WRONG_PARAMS_AMOUNT_ERROR,
						AccountMessage.getMessage("WRONG_PARAMS_AMOUNT_ERROR_MSG"));
			/*TblOrderPayExample example = new TblOrderPayExample();
			example.createCriteria().andRelatedOrderIdEqualTo(orderId);
			List<TblOrderPay> orderPayList = finance_tbl_order_pay_dao.selectByExample(example);
			if (orderPayList != null && orderPayList.size() > 0)
				throw new BizException(ReturnCode.FINANCE_NOTIFY_REFUND_ERROR, AccountMessage.getMessage("NOTIFY_REFUND_ERROR_MSG"));*/
			if (!OrderType.getRefundTypes().contains(orderType))
				throw new BizException(ReturnCode.FINANCE_WRONG_PARAMS_STATUS_ERROR,
						AccountMessage.getMessage("WRONG_PARAMS_STATUS_ERROR_MSG"));
		}

        
		/* 保存合并支付前的账户状态 */
		TblAccount oldAccount = (TblAccount) BeanUtils.cloneBean(account);

		// 原始收银记录
		TblCheckout origCheckout = checkoutBO.getCheckoutByOrderId(origOrderId);
		logger.info("退单原单收银记录.origCheckout:{}", TZBeanUtils.describe(origCheckout));
	 
		/**
         * 老数据兼容怎么做
         * 
         * 对原单添加freezeDetail
         */ 
		List<TblOrderPay> relatedOrderPayList = checkoutBO.getRelatedOderPayByOrderId(origOrderId);
		//计算退单明细 && 增加冻结金额
//		TblFreeze freezeManage = freezeBO.createRefundFreeze(orderId, origOrderId, refundAmount, operator);
		TblFreeze freezeManage = finance_tbl_account_factory.createFreeze(operator);
		final long criteriaAmount = freezeBO.createRefundFreeze(freezeManage, orderId, origOrderId, refundAmount, operator);
		logger.info("criteriaAmount : "+ criteriaAmount);
		freezeManage.setFreezeStatus(FreezeStatus.FREEZE.name());
		freezeManage.setAccountId(account.getAccountId());
		freezeManage.setCustomerId(customerId);
		freezeManage.setFreezeType(FreezeType.RETURN_ORDER.name());
		finance_tbl_freeze_dao.updateByPrimaryKeySelective(freezeManage);
		logger.info("freeze : {}", TZBeanUtils.describe(freezeManage));
		
		//计算收回的奖励积分和抵扣现金
		long compensatoryCashAmount = 0l; //需要抵扣的现金金额
        long backRewardPoint = 0l;//需要收回的奖励积分
        final TblOrderPay rootOrderPay = relatedOrderPayList.get(0); //最原始的订单
		logger.info("rootOrderPay : {}", TZBeanUtils.describe(rootOrderPay));
        TblRewardPointAccountDetail rewardDetail = reward_point_account_bo.getRewardPointAccountDetailByOrderId(rootOrderPay.getRelatedOrderId());
        if(rewardDetail != null && rewardDetail.getAmount() > 0 && rewardDetail.getIsActive()){
            backRewardPoint = rewardDetail.getAmount();
            rewardDetail.setIsActive(false);
            reward_point_account_bo.getRewardPointAccountDetailDao().updateByPrimaryKeySelective(rewardDetail);
        }
        if(backRewardPoint > 0){
            long refundRewardPoint = -freezeManage.getFreezeRewardPointAmount();
            long balanceRewardPoint = reward_point_account_bo.getAvailableRewardPointAmount(account.getAccountId());
            long availableRewardPoint = refundRewardPoint + balanceRewardPoint;
            compensatoryCashAmount = backRewardPoint - availableRewardPoint;
        }
        if(compensatoryCashAmount > 0){
            long cashAmount = -freezeManage.getFreezeCashAmount();
            if(cashAmount < compensatoryCashAmount){
//                throw new BizException("收回积分的抵扣金额大于退款现金金额");
            	compensatoryCashAmount = cashAmount;
            }
            freezeManage.setCompensationAmount(-compensatoryCashAmount);
            freezeManage.setFreezeCashAmount(compensatoryCashAmount - cashAmount);
            finance_tbl_freeze_dao.updateByPrimaryKeySelective(freezeManage);
            backRewardPoint -=  compensatoryCashAmount;
        }
		final long backRewardPointAmount = backRewardPoint;
		/* 更新账户的金额信息 */
        long oldAccountAmount = oldAccount.getAccountAmount();
        long oldFrozenAmount = oldAccount.getFrozenAmount();
        long newAccountAmount = oldAccountAmount - freezeManage.getFreezeCashAmount() - freezeManage.getFreezeOnlineRemitAmount();
        long newFrozenAmount = oldFrozenAmount - freezeManage.getFreezeCashAmount() - freezeManage.getFreezeOnlineRemitAmount();
        account.setAccountAmount(newAccountAmount);
        account.setFrozenAmount(newFrozenAmount);
        account.setUpdateBy(operator);
        account.setUpdateDate(date);
        int count = finance_tbl_account_dao.updateByPrimaryKey(account);
        if (count != 1)
            throw new BizException(ReturnCode.FINANCE_UPDATE_DB_ERROR, AccountMessage.getMessage("UPDATE_DB_ERROR_MSG"));
        
		TblWorkFlow workflow = finance_tbl_account_factory.createTblWorkFlow(operator);
		workflow.setWorkFlowType(WorkflowType.FREEZE.name());
		workflow.setRelatedId(freezeManage.getFreezeId());
		workflow.setStatus(FreezeStatus.FREEZE.name());
		workflow.setOperator(operator);
		workflow.setOperateDate(date);
		workflow.setOperateSummary(null);
		finance_tbl_workflow_dao.insert(workflow);
		
		/* 订单支付数据 */
		TblOrderPay orderPayManage = finance_tbl_account_factory.createOrderPay(operator);
		orderPayManage.setTicketNo(Arrays.toString(ticketNo.toArray()));
		orderPayManage.setTicketName(ticketName);
		orderPayManage.setTicketNum((long) ticketNo.size());
		orderPayManage.setCustomerId(customerId);
		orderPayManage.setAccountId(account.getAccountId());
		orderPayManage.setRelatedOrderId(orderId);
		orderPayManage.setRelatedOriginOrderId(origOrderId);
		orderPayManage.setSerialNumber(null);
		orderPayManage.setOrderPayStatus(OrderPayStatus.FREEZE.name());
		orderPayManage.setProductType(productType.name());
		orderPayManage.setOrderType(orderType.name());
		orderPayManage.setCreateOrderDate(createOrderDate);
		orderPayManage.setOrderVerifyDate(orderVerifyDate);
		orderPayManage.setReceiveTicketDate(date);
		orderPayManage.setSummary(summary);
		orderPayManage.setRelatedFreezeId(freezeManage.getFreezeId());
		orderPayManage.setCreateOrderOperator(createOrderOperator);
		orderPayManage.setPnr(Arrays.toString(pnr.toArray()));
		orderPayManage.setOrderGroupId(orderGroupId);
		finance_tbl_order_pay_dao.insert(orderPayManage);

		
		boolean returnMoney2NetpayFlag = checkoutBO.needReturnMoney2Netpay(orderPayManage.getRelatedOriginOrderId());
		logger.info("是否需退款到网支.relatedOriginOrderId:" + orderPayManage.getRelatedOriginOrderId() + ", returnMoney2NetpayFlag:" + returnMoney2NetpayFlag);
		PayType payType = PayType.valueOf(origCheckout.getPayType());
		PaymentType paymentType = StringUtils.isNotBlank(origCheckout.getPaymentType()) ?
				PaymentType.valueOf(origCheckout.getPaymentType()) : payType.getDefaultPaymentType();
		PaymentOrg paymentOrg = StringUtils.isNotBlank(origCheckout.getPaymentOrg()) ?
				PaymentOrg.valueOf(origCheckout.getPaymentOrg()) : payType.getDefaultPaymentOrg();
		if(!returnMoney2NetpayFlag && payType.isNetpayPayType()){
			paymentType = PaymentType.PRESTORE;
			paymentOrg = PaymentOrg.PLATFORM;
		}
		payType = PayType.getPayType(paymentType, paymentOrg);
		
		/* 收银数据 */
		TblCheckout checkoutManage = finance_tbl_account_factory.createCheckout(operator);
		checkoutManage.setRelatedOrderPayId(orderPayManage.getOrderPayId());
		checkoutManage.setRelatedOrderId(orderPayManage.getRelatedOrderId());
		checkoutManage.setPayType(payType.name());// 退款时的支付方式与原始单子保持一致
		checkoutManage.setReturnAmount(Math.abs(freezeManage.getFreezeCashAmount()));
		checkoutManage.setCheckoutStatus(CheckoutStatus.UNCHECKOUT.name());
		checkoutManage.setOperateType(OperateType.RETURN_MONEY.name());
		checkoutManage.setPaymentOrg(paymentOrg.name());
		checkoutManage.setPaymentType(paymentType.name());
		finance_tbl_checkout_dao.insert(checkoutManage);

		/* 记录收支信息 */
		TblAccountHistory accountManage = finance_tbl_account_factory.createAccountHistory(oldAccount, account, operator);
		accountManage.setAccountId(account.getAccountId());
		accountManage.setManageType(ManageType.ORDER_REFUND.name());
		accountManage.setFreezeId(freezeManage.getFreezeId());
		accountManage.setAmount(Math.abs(freezeManage.getFreezeCashAmount()));
		accountManage.setOperator(operator);
		accountManage.setOperateDate(date);
		accountManage.setOrderPayId(orderPayManage.getOrderPayId());
		accountManage.setSummary(summary);
		accountManage.setPostFrozenAmount(newFrozenAmount);
		accountManage.setPostAccountAmount(newAccountAmount);
		accountManage.setOrderPayId(orderPayManage.getOrderPayId());
		/* 插入账户充值的记录 */
		finance_tbl_account_history_dao.insertSelective(accountManage);

		/* 退款时,对国内国际机票的信用和预存支付方式执行自动收银 */
		checkoutBO.checkRefund(orderPayManage);
		boolean autoCheckoutTicket = checkoutBO.shouldRefundAutoCheckout(checkoutManage, orderPayManage);
		boolean autoCheckoutHotel = productType == ProductType.HOTEL && !("NOW_SETTLE".equals(hotelOrderSettlePeriod) && hotelSettleState);
		autoCheckoutHotel  = autoCheckoutHotel && !(refundReq.isReservationFee() && hotelSettleState); // 有留帐 & 酒店的原订单出帐状态true & RETURN_MONEY => 不自动收银
		boolean autoCheckout = (ProductType.HOTEL !=productType && autoCheckoutTicket) || autoCheckoutHotel;
		if (autoCheckout) { 

//		    List<TblRefund> refunds = finance_refund_bo.getRefundsByOrderId(orderId);
		    long realMoneyRefundAmount = freezeManage.getFreezeCashAmount();
		    long rewardPointRefundAmount = freezeManage.getFreezeRewardPointAmount();
//            for(TblRefund refund : refunds){
//                TblFreezeDetail detail = freezeDetailDAO.selectByPrimaryKey(refund.getSourceFreezeDetailId());
//                detail.setRefundAmount(detail.getRefundAmount() + refund.getRefundAmount());
//                freezeDetailDAO.updateByPrimaryKeySelective(detail);
//                finance_refund_bo.completeRefund(refund);
//            }
            if(rewardPointRefundAmount < 0){
                RewardPointDistributionBean reward = new RewardPointDistributionBean();
                reward.setCustomerId(customerId);
                reward.setRelatedOrderId(rootOrderPay.getRelatedOrderId());
                reward.setProductType(productType);
                reward.setOperateDate(date);
                reward.setOperatorId(operator);
                reward.setProductType(productType);
                reward.setRewardPointNum(-rewardPointRefundAmount);
                reward.setOrderAmount(refundAmount);
                reward_point_service.refundRewardPoint(reward);
            }
            if(backRewardPoint > 0){
                RewardPointDistributionBean reward = new RewardPointDistributionBean();
                reward.setCustomerId(customerId);
                reward.setRelatedOrderId(rootOrderPay.getRelatedOrderId());
                reward.setProductType(productType);
                reward.setOperateDate(date);
                reward.setOperatorId(operator);
                reward.setProductType(productType);
                reward.setRewardPointNum(backRewardPoint);
                reward_point_service.deductRewardPoint(reward);
            }
            
            if (!returnMoney2NetpayFlag) {
                stopWatch.lap("finance.notifyFinanceRefund.autoCheckout.return.start");
                TraceTradeBean tradeBean = new TraceTradeBean(customerId,
                        productType, orderType, orderId, null, realMoneyRefundAmount,
                        payType, BalanceType.INCOME, TradeStatus.UNDERWAY,
                        NotifyStatus.UNDERWAY);
                tradeBean.setPaymentType(paymentType);
                tradeBean.setPaymentOrg(paymentOrg);
                finance_tracing_service.traceTradeRequest(tradeBean, operator);

                checkoutBO.doCheckout(checkoutManage, checkoutBO.getAutoCashierByProductAndPayType(productType,PayType.valueOf(checkoutManage.getPayType())),
                        null, null);
                /* 退单解冻 */
                unfreezeAmount(FreezeType.RETURN_ORDER, freezeManage.getFreezeId(), freezeManage.getRelatedOrderId(), null);

                TraceTradeBean tradeBean1 = new TraceTradeBean(customerId,
                        orderId, null, payType, BalanceType.INCOME,
                        TradeStatus.SUCCESS, null, null, NotifyStatus.SUCCESS);
                tradeBean1.setPaymentType(paymentType);
                tradeBean1.setPaymentOrg(paymentOrg);
                finance_tracing_service.traceTradeResult(tradeBean1, operator);
                stopWatch.lap("finance.notifyFinanceRefund.autoCheckout.return.end");
            } else {
                stopWatch.lap("finance.notifyFinanceRefund.autoCheckout.return2netpay.start");
                if (relatedOrderPayList != null && relatedOrderPayList.size() > 0) {
                    TblCheckoutExample example = new TblCheckoutExample();
                    example.createCriteria().andRelatedOrderPayIdEqualTo(relatedOrderPayList.get(0).getOrderPayId());
                    List<TblCheckout> checkoutList = finance_tbl_checkout_dao.selectByExample(example);
                    if (checkoutList.size() > 0) {
                        checkoutManage.setPayType(checkoutList.get(0).getPayType());
                        finance_tbl_checkout_dao.updateByPrimaryKeySelective(checkoutManage);
                    }
                }
                checkoutBO.doCheckout(checkoutManage, checkoutBO.getAutoCashierByProductAndPayType(productType,PayType.valueOf(checkoutManage.getPayType())),null, null);
                if (freezeManage.getFreezeCashAmount() == 0) {
                	unfreezeAmount(FreezeType.RETURN_ORDER, freezeManage.getFreezeId(), freezeManage.getRelatedOrderId(), null);
                }
                TransactionUtil.afterCommit(new TransactionSynchronizationAdapter() {
                            @Override
                            public void afterCommit() {
                                financeEventBus.post(new TPRefundEvent(orderId));
                            }
                        });
                stopWatch.lap("finance.notifyFinanceRefund.autoCheckout.return2netpay.end");
            }
//			if (!returnMoney2NetpayFlag) {
//				stopWatch.lap("finance.notifyFinanceRefund.autoCheckout.return.start");
//				TraceTradeBean tradeBean = new TraceTradeBean(customerId, productType, orderType, orderId, null,
//						refundAmount, payType, BalanceType.INCOME, TradeStatus.UNDERWAY, NotifyStatus.UNDERWAY);
//				tradeBean.setPaymentType(paymentType);
//				tradeBean.setPaymentOrg(paymentOrg);
//				finance_tracing_service.traceTradeRequest(tradeBean, operator);
//
//				checkoutBO.doCheckout(checkoutManage, checkoutBO.getAutoCashierByProductAndPayType(productType, PayType.valueOf(checkoutManage.getPayType())), null, null);
//				/* 退单解冻 */
//				unfreezeAmount(FreezeType.RETURN_ORDER, freezeManage.getFreezeId(), freezeManage.getRelatedOrderId(), null);
//
//				TraceTradeBean tradeBean1 = new TraceTradeBean(customerId, orderId, null, payType, BalanceType.INCOME,
//						TradeStatus.SUCCESS, null, null, NotifyStatus.SUCCESS);
//				tradeBean1.setPaymentType(paymentType);
//				tradeBean1.setPaymentOrg(paymentOrg);
//				finance_tracing_service.traceTradeResult(tradeBean1, operator);
//				stopWatch.lap("finance.notifyFinanceRefund.autoCheckout.return.end");
//			} else {
//				stopWatch.lap("finance.notifyFinanceRefund.autoCheckout.return2netpay.start");
//				List<TblOrderPay> opList = checkoutBO.getRelatedOderPayByOrderId(orderPayManage.getRelatedOriginOrderId());
//				if (opList != null && opList.size() > 0) {
//
//					TblCheckoutExample example = new TblCheckoutExample();
//					example.createCriteria().andRelatedOrderPayIdEqualTo(opList.get(0).getOrderPayId());
//					List<TblCheckout> checkoutList = finance_tbl_checkout_dao.selectByExample(example);
//					if (checkoutList.size() > 0) {
//						checkoutManage.setPayType(checkoutList.get(0).getPayType());
//						finance_tbl_checkout_dao.updateByPrimaryKeySelective(checkoutManage);
//					}		
//				}
//				checkoutBO.doCheckout(checkoutManage, checkoutBO.getAutoCashierByProductAndPayType(productType, PayType.valueOf(checkoutManage.getPayType())), null, null);
//				final String refundOrderId = orderPayManage.getRelatedOrderId();
//				TransactionUtil.afterCommit(new TransactionSynchronizationAdapter() {
//					@Override
//					public void afterCommit() {
//						financeEventBus.post(new TPRefundEvent(refundDetail));
//					}
//				});
//				stopWatch.lap("finance.notifyFinanceRefund.autoCheckout.return2netpay.end");
//			}
		
		}
		// 非网支且非自动支付时，只生成请求记录
		if (!returnMoney2NetpayFlag && !autoCheckout) {
			TraceTradeBean tradeBean = new TraceTradeBean(customerId, productType, orderType, orderId, null, refundAmount,
					payType, BalanceType.INCOME, TradeStatus.UNDERWAY, NotifyStatus.UNDERWAY);
			tradeBean.setPaymentType(paymentType);
			tradeBean.setPaymentOrg(paymentOrg);
			finance_tracing_service.traceTradeRequest(tradeBean, operator);
		}
//		}
		finance_async_checkout_service.autoCheckoutPaidAndUnCheckedOrder(customerId);
		stopWatch.lap("finance.notifyFinanceRefund.autoCheckout.success");

		/* 检查账户金额的计算结果 */
		stopWatch.lap("finance.notifyFinanceRefund.checkamount.start");
		//update account
		account = finance_tbl_account_dao.selectByPrimaryKey(account.getAccountId());
		if (!checkAccountAmount(account))
			throw new BizException(ReturnCode.FINANCE_ACCOUNT_AMOUNT_CACULATE_ERROR,
					AccountMessage.getMessage("ACCOUNT_AMOUNT_CACULATE_ERROR_MSG"));
		stopWatch.lap("finance.notifyFinanceRefund.checkamount.end");
		
		TransactionUtil.afterCommit(new TransactionSynchronizationAdapter() {
            @Override
            public void afterCommit() {
                RewardBackBean back = new RewardBackBean(orderId, 
                        rootOrderPay.getRelatedOrderId(), criteriaAmount, operator, backRewardPointAmount);
                couponBackEventBus.post(new RewardBackEvent(back));
            }
        });
		
		
		logger.info("Exit function: [finance][electronicOrderService][notifyFinanceRefund].");
	}
	
	private long generateFreezeOrder(String refundId,String orderId, TblAccount account) throws Exception {
		// TODO Auto-generated method stub
		TblElectronicRechargeExample  example = new TblElectronicRechargeExample();
		example.createCriteria().andTbOrderIdEqualTo(orderId);
		List<TblElectronicRecharge>rechargeList = rechargeDAO.selectByExample(example);
		if(rechargeList.size() == 1){
			TblElectronicRecharge rechargeRecord = rechargeList.get(0);
			Long freezeAmount  = rechargeRecord.getTotalAmount() + rechargeRecord.getGiveAmount();
		//	FreezeResult freezeResult
			//冻结主账户,
			long freezeId =   financeAccountFreezeService.freezeFromAvailableAmountForEcard(account, 
					FreezeType.AMOUNT_FREEZE, freezeAmount, orderId, "ecard refund freeze", getUser());
	//		electronicAccountBo.freezeAmountByCustomerId(account.getCustomerId(), freezeAmount);
		    
			ElectronicBean bean = new ElectronicBean();
			bean.setAmount(freezeAmount);
			bean.setCustomerId(account.getCustomerId());
			bean.setInterType(ElectronicInoutType.FREEZE.name());
			bean.setOperateDate(new Date());
			bean.setOperator(getUser());
			bean.setRelatedOrderId(refundId);
			bean.setOriginOrderId(orderId);
			bean.setProductType(ProductType.ELECTRONIC_CARD);
	
			electronicService.freezeAmount(bean);
			
			return freezeId;
		}else{
			throw new BizException("recharge order error");
		}
	}
	
	private boolean canRefund(String accountId,String orderId) throws SQLException {
		// TODO Auto-generated method stub
		if(existsElectronicOrder(accountId,orderId) || existsCouponOrder(accountId, orderId) 
				||existsFreezeAmountRecord(accountId)||newAccountHasFreezed(accountId))
			return false;
		return true;
	}
	
	private boolean newAccountHasFreezed(String accountId) {
		TblAccountExample example = new TblAccountExample();
		example.createCriteria().andAccountIdEqualTo(accountId).andFreezeStatusEqualTo(FreezeStatus.FREEZE.name());
		try {
			 boolean result =  tblAccountDAO.selectByExample(example).size() == 0 ? false : true;
				logger.info("ecard account {} freeze result {} ", accountId, result);
				return result;
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return false;
	}
	private boolean existsFreezeAmountRecord(String accountId) {
		// TODO Auto-generated method stub
		TblFreezeExample example = new TblFreezeExample();
		example.createCriteria().andAccountIdEqualTo(accountId).andFreezeTypeEqualTo(FreezeType.AMOUNT_FREEZE.name()).andFreezeStatusNotEqualTo(FreezeStatus.UNFREEZE.name());
		List<TblFreeze> list = new ArrayList<TblFreeze>();
		try {
			 list = finance_tbl_freeze_dao.selectByExample(example);
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if (!list.isEmpty()){
			logger.info("ecard account {} exists freeze order,can not refund ", accountId);
			return true;
		}
		logger.info("ecard account {} does not exists freeze order,can refund ", accountId);
		return false;
	}
	@Override
	public boolean orderCanRefund(String orderId) throws Exception{
		logger.info("check whether order {} can refund ", orderId);
		ElectronicOrder electronicOrder = electronicOrderDAO.selectByPrimaryKey(orderId);
		try{
			String customerId = getNewAccountIdByOrderId(orderId);
		
			TblAccount account = finance_account_bo.getAccountByCustomerIdInAllState(customerId);
			String accountId = account.getAccountId();
			
			if(!electronicOrder.getOrderStatus().equals(OrderPayStatus.PAY.name()))
				return false;
			if(existsElectronicOrder(accountId,orderId) || existsCouponOrder(accountId,orderId)|| existsFreezeAmountRecord(accountId)
					||newAccountHasFreezed(accountId))
				return false;
		}catch(Exception e){
			return false;
		}
		return true;
		
	}
	
	private String getNewAccountIdByOrderId(String orderId) throws Exception{
		TblElectronicRechargeExample  example  = new TblElectronicRechargeExample();
		
		example.createCriteria().andTbOrderIdEqualTo(orderId);
		
		List<TblElectronicRecharge> list = rechargeDAO.selectByExample(example);
		logger.error("electronic card recharge record result :" + TZBeanUtils.describe(list) );
		
		String newAccountCustomerId = null;
		if(list.size() != 1){
			throw new BizException("electronic card recharge record is not right ");
		}else{
			newAccountCustomerId = list.get(0).getCustomerId();
		}
		return newAccountCustomerId;
	}
	
	private boolean existsCouponOrder(String account_id,String originOrderId) throws SQLException { // TODO Auto-generated method stub
		
		TblCouponAccountInoutDetailExample example = new TblCouponAccountInoutDetailExample();
		example.createCriteria().andAccountIdEqualTo(account_id).andOrderPayIdNotEqualTo(originOrderId);
		List<TblCouponAccountInoutDetail> list = couponInoutDetailDao.selectByExample(example);
		for(TblCouponAccountInoutDetail detail : list){
			if(!detail.getInoutType().equals(InoutType.DISTRABUTE.name())){
				logger.info("ecard account {} exists coupon order,can not refund ", account_id);
				logger.info("coupon order detail {}  ", TZBeanUtils.describe(detail));
				return true;
			}
		}
		logger.info("ecard account {} does not exists Coupon Order ", account_id);
		return false;
	}
	private boolean existsElectronicOrder(String accountId,String originOrderId) throws SQLException {
		   TblElectronicAccountHistoryExample example = new TblElectronicAccountHistoryExample();

		   List<String> inoutTypeList = new ArrayList<String>();
		    inoutTypeList.add(ElectronicInoutType.GIVEN.name());
		    inoutTypeList.add(ElectronicInoutType.RECHARGE.name());
		    
		    Criteria createria = example.createCriteria();
			createria.andAccountIdEqualTo(accountId);
			createria.andInoutTypeNotIn(inoutTypeList);
			createria.andOriginOrderIdNotEqualTo(originOrderId);
			
			List<TblElectronicAccountHistory> list = electronicAccountHistoryDAO.selectByExample(example);
			logger.info("ecard order generated account electronic order result {} ,{}", accountId, TZBeanUtils.describe(list));
			
			return !list.isEmpty();
	}
	@Override
	public int verifyRefund(String refundId) throws SQLException {
		// TODO Auto-generated method stub
		TblElectronicRefundExample example = new TblElectronicRefundExample();
		
		TblElectronicRefund record = new TblElectronicRefund();
		record.setRefundId(refundId);
		record.setStatus(EcardRefundStatus.VERIFIED.name());
		example.createCriteria().andStatusEqualTo(EcardRefundStatus.APPLIED.name()).andRefundIdEqualTo(refundId);
		int result  = tblElectronicRefundDAO.updateByExampleSelective(record, example);
		
		if(result != 1) throw new BizException(" refund record num not right :count :" + result);
		
		return result;
	}
	@Override
	public List<ElectronicProduct> getPagedElectronicProduct(Pagination<ElectronicProduct> pagination) throws Exception {
		
		ElectronicProductExample example = new ElectronicProductExample();
		example.setPgLength(pagination.getPageSize());
		example.setPgOffset((pagination.getPageNo() -1)* pagination.getPageSize());
		
		com.travelzen.tops.finance.account.po.gen.ElectronicProductExample.Criteria criteria= example.createCriteria();
		criteria.andStatusEqualTo(EcardProductState.ONSALE.name());
		
		example.setOrderByClause("create_date asc");
		pagination.setTotalItemCount((long) electronicProductDAO.countByExample(example));
		
		 List<ElectronicProduct> refundList = electronicProductDAO.selectByExample(example);
		pagination.setData(refundList);
	 
		return refundList;
	}
	public boolean validateProductName( String tb_product_name,String productId)  throws Exception {
		// TODO Auto-generated method stub
		
		ElectronicProductExample example = new ElectronicProductExample();
		
		List<ElectronicProduct> products = electronicProductDAO.selectByExample(example);

		logger.info("全部电子卡产品 :{}", TZBeanUtils.describe(products));
		
		String productName = tb_product_name.replace("\t", "").replace(" ", "");
		ElectronicProduct result = null;
		
		int count = 0;
		for(ElectronicProduct product : products){
			if(product.getProductName().replace("\t", "").replace(" ", "").equals(productName) 
					&& !product.getProductId().equals(productId))
			{
				result = product;
				count++;
			}
		}
		
		logger.info("匹配的产品:{}",TZBeanUtils.describe(result));
		
		if (count == 0)
			return true;
		else return false;
	}
}
