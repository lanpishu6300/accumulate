package me.ele.bpm.runshop.impl.service.async;

import com.fasterxml.jackson.core.JsonParseException;
import com.google.common.collect.Lists;
import me.ele.bpm.runshop.core.constants.RunshopServiceConstants;
import me.ele.bpm.runshop.core.dto.CardSimpleDto;
import me.ele.bpm.runshop.core.dto.Delivery;
import me.ele.bpm.runshop.core.dto.GoodInfo;
import me.ele.bpm.runshop.core.model.HeadOffice;
import me.ele.bpm.runshop.core.model.RunshopApplicationInfo;
import me.ele.bpm.runshop.core.model.RunshopApplicationPicture;
import me.ele.bpm.runshop.core.model.RunshopOperateRecord;
import me.ele.bpm.runshop.core.model.constant.RunshopOperateType;
import me.ele.bpm.runshop.impl.client.*;
import me.ele.bpm.runshop.impl.dao.runshop.HeadOfficeMapper;
import me.ele.bpm.runshop.impl.dao.runshop.RunshopMapper;
import me.ele.bpm.runshop.impl.dao.runshop.RunshopOperateRecordMapper;
import me.ele.bpm.runshop.impl.service.RequiredNewTransaction.RequiredNewTransaction;
import me.ele.bpm.runshop.impl.service.RunshopCoreService;
import me.ele.bpm.runshop.impl.service.RunshopService;
import me.ele.bpm.runshop.impl.thirdparty.HermesClient;
import me.ele.bpm.runshop.impl.thirdparty.ers.ElemeRestaurantService;
import me.ele.bpm.runshop.impl.thirdparty.ers.TFood;
import me.ele.bpm.runshop.impl.thirdparty.ers.TFoodCategory;
import me.ele.bpm.runshop.impl.thirdparty.hydros.HydrosService;
import me.ele.bpm.runshop.impl.thirdparty.sauron.*;
import me.ele.bpm.runshop.impl.utils.*;
import me.ele.coffee.model.hr.model.User;
import me.ele.contract.client.ClientUtil;
import me.ele.contract.exception.ServerException;
import me.ele.contract.exception.ServiceException;
import me.ele.elog.Log;
import me.ele.elog.LogFactory;
import me.ele.eve.soa.core.dto.message.EveMessageForm;
import me.ele.eve.soa.core.service.IMessageService;
import me.ele.jarch.fuss.entity.FussFile;
import me.ele.jarch.fuss.iface.FussService;
import me.ele.napos.base.api.entities.GeoLocation;
import me.ele.napos.miracle.keeper.api.entities.Keeper;
import me.ele.napos.miracle.restaurant.api.RelationShopService;
import me.ele.napos.miracle.restaurant.api.RestaurantService;
import me.ele.napos.miracle.restaurant.api.ShopBrandService;
import me.ele.napos.miracle.restaurant.api.entities.RestaurantDeliveryArea;
import me.ele.napos.miracle.restaurant.api.entities.RestaurantDeliveryUpdate;
import me.ele.napos.miracle.restaurant.api.entities.brand.SetShopBrandRelationResult;
import me.ele.napos.miracle.restaurant.api.entities.relationShop.Head;
import me.ele.napos.miracle.restaurant.api.entities.relationShop.LeafShopCreate;
import me.ele.napos.miracle.restaurant.api.entities.relationShop.SystemType;
import me.ele.napos.operation.service.lucifer.api.LMerchantService;
import me.ele.napos.operation.shared.payload.entities.applybase.OperatorType;
import me.ele.napos.operation.shared.payload.entities.merchant.CreateRestaurantResult;
import me.ele.pcd.dom.service.service.IOpenstoreService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.CollectionUtils;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Created by stephen on 16/10/5.
 */
@Service("runshopAsyncService")
@Transactional
@Lazy
public class RunshopAsyncService {

	private static Log logger = LogFactory.getLog(RunshopAsyncService.class);

	/**
	 * 走safe的fuss服务.
	 */
	private FussService fussWalleService;

	/**
	 * 走cnd的fuss服务.
	 */
	private FussService fussCdnService;

	@Autowired
	private RunshopCoreService runshopCoreService;

	/**
	 * cdn的fuss服务名称.
	 */
	private static final String CDN_FUSS_SERVICE_NAME = "arch.fuss";

	/**
	 * walle的fuss服务名称.
	 */
	private static final String WALLE_FUSS_SERVICE_NAME = "arch.fuss.safe";

	 @Autowired
	private RelationShopService relationShopService;

	/**
	 * hermesClient.
	 */
	@Autowired
	private HermesClient hermesClient;

	/**
	 * runshopMapper.
	 */
	@Autowired
	private RunshopMapper runshopMapper;

	/**
	 * messageService.
	 */
	@Autowired
	private IMessageService messageService;

	@Autowired
	private HeadOfficeMapper headOfficeMapper;


	/**
	 * elemeRestaurantService.
	 */
	@Autowired
	private ElemeRestaurantService elemeRestaurantService;

	/**
	 * elemeRestaurantClientService.
	 */
	@Autowired
	private ElemeRestaurantClientService elemeRestaurantClientService;

	 @Autowired
	private ShopBrandService shopBrandService;

	/**
	 * runshopOperateRecordMapper.
	 */
	@Autowired
	private RunshopOperateRecordMapper runshopOperateRecordMapper;


	@Autowired
	private FussClientService fussService;

	/**
	 * keeperForNaposClientService.
	 */
	@Autowired
	private KeeperForNaposClientService keeperForNaposClientService;

	/**
	 * taurusClientService.
	 */
	@Autowired
	private TaurusClientService taurusClientService;

	/**
	 * financeAccountService
	 */
	@Autowired
	private FinanceAccountService financeAccountService;

	/**
	 * restaurantService.
	 */
	@Autowired
	private RestaurantService restaurantService;
	/**
	 * packClientService.
	 */
	@Autowired
	private PackClientService packClientService;

	@Autowired
	private BindCardService bindCardService;

	@Value("${humble.consumer.key}")
	private String consumer_key;
	/**
	 * consumer_secret.
	 */
	@Value("${humble.consumer.secret}")
	private String consumer_secret;

	@Value("${sauron.appid}")
	private String sauron_appid;
	@Value("${sauron.client.id}")
	private String sauron_client_id;

	@Value("${sauron.key}")
	private String souron_key;

	@Autowired
	private SauronService sauronService;

	@Autowired
	private HydrosService hydrosService;


	@Autowired
	private LMerchantService lMerchantService;
	@Autowired
	private IOpenstoreService iOpenstoreService;

	@Autowired
	private RequiredNewTransaction requiredNewTransaction;

	/**
	 * fuss 服务的集群.
	 */
	@Value("${fuss.group}")
	private String GROUP;

	@PostConstruct
	public void init() {
		fussCdnService = ClientUtil.getContext().getClient(CDN_FUSS_SERVICE_NAME, GROUP, FussService.class);
		fussWalleService = ClientUtil.getContext().getClient(WALLE_FUSS_SERVICE_NAME, GROUP, FussService.class);
		shopBrandService = ClientUtil.getContext().getClient(ShopBrandService.class);
		relationShopService = ClientUtil.getContext().getClient(RelationShopService.class);
	}

	@Async
	public void saveMarkedPics(String logoPath, int runshopId, Map<Integer, String> toSaveMarkedPicCodeUrlMap) {

		logger.info("RunshopAsyncService.saveMarkedPics start with:{}, {}, {}", logoPath, runshopId,
				toSaveMarkedPicCodeUrlMap.size());

		for (Map.Entry<Integer, String> entry : toSaveMarkedPicCodeUrlMap.entrySet()) {

			String originUrl = entry.getValue();
			int vmPicCode = entry.getKey();

			logger.info("RunshopAsyncService.saveMarkedPics handle:{}, {}", originUrl, vmPicCode);

			String imageHash = CommonUtils.convertUrlToHash(originUrl);
			logger.info("RunshopAsyncService.saveMarkedPics,imageHash{}", imageHash);
			if (StringUtils.isBlank(imageHash))
				continue;

			/* 获取无水印图片的字节流信息 */
			ByteBuffer imageByteBuffer = null;
			try {
				imageByteBuffer = fussWalleService.fileDownload(imageHash);
				logger.debug("RunshopAsyncService.saveMarkedPics,fussWalleService.fileDownload,imageByteBuffer:{}",
						imageByteBuffer);
			} catch (ServiceException e) {
				e.printStackTrace();
			} catch (ServerException e) {
				e.printStackTrace();
				logger.error("download by imageHash failed");
			}
			if (null == imageByteBuffer)
				continue;
			/* 将无水印图片转换为有水印图片 */
			try {
				imageByteBuffer = ImageUtils.markImageByIcon(logoPath, imageByteBuffer,
						RunshopServiceConstants.WATER_DEGREE);
				logger.debug("RunshopAsyncService.saveMarkedPics,ImageUtils.markImageByIcon,imageByteBuffer:{}",
						imageByteBuffer);
			} catch (ServiceException e) {
				e.printStackTrace();
			}
			FussFile fussFile = new FussFile();
			fussFile.setContent(imageByteBuffer);
			/* 获取文件类型 */
			String imageHashMD5 = StringUtils.substring(imageHash, 0, 32);
			String imageType = StringUtils.remove(imageHash, imageHashMD5);
			fussFile.setExtension(imageType);
			/* 上传图片 */
			String markedImageHash;
			try {
				markedImageHash = fussCdnService.fileUpload(fussFile);
				logger.debug("RunshopAsyncService.saveMarkedPics,fussCdnService.fileUpload,markedImageHash:{}",
						markedImageHash);
				RunshopApplicationPicture rap = new RunshopApplicationPicture();
				rap.setPic_hash(markedImageHash);
				rap.setPic_code(vmPicCode);
				rap.setPic_url(originUrl);
				rap.setRunshop_id(runshopId);
				runshopCoreService.createApplicationPicture(rap);
				logger.debug("RunshopAsyncService.saveMarkedPics,runshopCoreService.createApplicationPicture,finish");

			} catch (ServiceException e) {
				e.printStackTrace();
			} catch (ServerException e) {
				e.printStackTrace();
				logger.error("upload fussImage failed");
			}

		}
		logger.info("RunshopAsyncService.saveMarkedPics runshopId:{} done at:{}", runshopId, LocalDateTime.now()
				.toString());
	}


	@Async
	public void asyncSendMessage(RunshopApplicationInfo runshopApplicationInfo,String auditReason,
								 User user,int id, int userId,String operateData){
		try {
			try {
				hermesClient.runshopUnqualifiedMessage(runshopApplicationInfo.getMobile(),
						auditReason.replaceAll("\\n", "\\\\n"));
				operateData = String.format(ProjectConstant.UNQUALIFIED_RECORD_MESSAGE, auditReason,
						ProjectConstant.SEND_MESSAGE_SUCCESS);
			} catch (Exception e) {
				logger.error("RunshopService#setAuditStatus error", e);
				operateData = String.format(ProjectConstant.UNQUALIFIED_RECORD_MESSAGE, auditReason,
						ProjectConstant.SEND_MESSAGE_FAIL);
			}
			insertRunshopOperateRecord(id, userId, user.getEmail(), user.getName(), operateData,
					RunshopOperateType.UNQUALIFIEDRUNSHOP.getValue());

			try {
				if (runshopApplicationInfo.getSource() == RunshopServiceConstants.SOURCE_BD) {
					EveMessageForm messageForm = new EveMessageForm();
					messageForm.setTitle("您报备的门店未通过审核！");

					Map<String, String> solReasonMap = JsonHelper.toJsonObject(auditReason, Map.class);
					String solReasonStr = "";
					if(!CollectionUtils.isEmpty(solReasonMap)) {
						for (String val : solReasonMap.values()) {
							if (StringUtils.isBlank(solReasonStr)) {
								solReasonStr = val;
							} else {
								solReasonStr = solReasonStr + ";" + val;
							}
						}
					}

					String content = String.format(ProjectConstant.EVE_RUNSHOP_UNQUALIFIED_MESSAGE_TMP,
							runshopApplicationInfo.getStore_name(), runshopApplicationInfo.getAddress(),
							runshopApplicationInfo.getMobile(), solReasonStr);
					messageForm.setUrl(ProjectConstant.EVE_RUNSHOP_URL_CODE); // 开店管理详情URL
					Map<String, Object> param = new HashMap<>();
					param.put("id", id);
					messageForm.setUrlParam(param);
					messageForm.setContent(content);
					List<Integer> userIdList = new ArrayList<>(1);
					userIdList.add(runshopApplicationInfo.getUser_id());
					messageForm.setUserIdList(userIdList);
					messageForm.setBuss_type(ProjectConstant.RUNSHOP_EVE_MESSAGE_BUSS_TYPE);
					messageService.pushMessage(messageForm);
				}
			} catch (Exception e) {
				logger.error("RunshopService#setAuditStatus,eve pushMessage error", e);
			}

		}catch (Exception e){
			logger.error("RunshopAsyncService.asyncSendMessage failed at:{}",e);
		}
	}

	//@Transactional(transactionManager = "transactionManager", rollbackFor = {Exception.class})
	//@Async
	//@Transactional(transactionManager = "transactionManager", rollbackFor = {Exception.class},propagation = Propagation.REQUIRES_NEW)
	//由于@Async注解生效之后  requires_new不能生效 所以改方法改为同步修改,requires_new update rstId，afterCommit 处理后续流程
	// （afterCommit 之后的流程也需要 commit，因此同样需要requires new）
	public void asyncAfterProcess(RunshopApplicationInfo runshopApplicationInfo,int userId,User user,int id,RunshopService runshopService) throws Exception {

		logger.info("enter_method_asyncAfterProcess");
		logger.info("runshopApplicationInfo = [" + runshopApplicationInfo + "], userId = [" + userId + "], user = [" + user + "], id = [" + id + "], runshopService = [" + runshopService + "]");
		try {

			CreateRestaurantResult result = lMerchantService.createRealRestaurantV2(id, 0, OperatorType.EUS);

			int rstId = result.getRestaurantId();
			runshopApplicationInfo.setRst_id(result.getRestaurantId());

			try {
				requiredNewTransaction.updateRstId(id,rstId);

//				runshopMapper.updateRunshopRstIdById(id, rstId);
				logger.info("update runshop status success");
			} catch (Exception e) {

				logger.error("FixAppyStatusServcie#setAuditStatus[updateRunshopRstIdById] error", e);
			}

			if(result.isHasCreated() == true) return;

			final RunshopAsyncService runshopAsyncService = this;

			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
				@Override
				public void afterCommit() {
					//do what you want to do after commit
					try {
						requiredNewTransaction.afterSetRstId(runshopApplicationInfo, userId, user, id, runshopService, runshopAsyncService ,rstId);

					}catch (Exception e){

					}

				}
			});


		}catch (Exception e){
			logger.error("asyncAfterProcessFailedAt {} ,runshopapplicationInfo {} ", e, runshopApplicationInfo);
		}
		logger.info("exit_method_asyncAfterProcess");

	}


	public void afterSetRstId(RunshopApplicationInfo runshopApplicationInfo,int userId,User user,int id,RunshopService runshopService,int rstId) throws Exception {

		logger.info("enter#method#afterSetRstId");
		logger.info("runshopApplicationInfo = [" + runshopApplicationInfo + "], userId = [" + userId + "], user = [" + user + "], id = [" + id + "], runshopService = [" + runshopService + "], rstId = [" + rstId + "]");
		// 如果dom_id > 0,回写建店成功的id到dom
			if (runshopApplicationInfo.getDom_id() > 0) {
				try {
					boolean isBanding = iOpenstoreService.relateShop(runshopApplicationInfo.getDom_id(), rstId);
					if (!isBanding) {
						logger.info(String.format("[domId: %s, rstId: %s]rstId write back failed.",
								runshopApplicationInfo.getDom_id(), rstId));
					} else {
						logger.info(String.format("[domId: %s, rstId: %s]rstId write back success.",
								runshopApplicationInfo.getDom_id(), rstId));
					}
				} catch (Exception e) {
					logger.error(
							String.format("[domId: %s, rstId: %s]rstId write back error.",
									runshopApplicationInfo.getDom_id(), rstId), e);
				}
			}

			// 连锁店分店,绑定总店及品牌信息
			if (runshopApplicationInfo.getHead_id() != 0) {
				bindHeadOffice(rstId, runshopApplicationInfo.getHead_id());
				bindBrand(rstId, runshopApplicationInfo.getBrand_id());
			}
			// createSettleAccount();
			// 支付方式和结算方式用服务包接口代替
			boolean signFreeContractResult = packClientService.signFreeContract(rstId);
			logger.info("after free contract success");
			try {
				if (signFreeContractResult) {
					insertRunshopOperateRecord(id, userId, user.getEmail(), user.getName(),
							ProjectConstant.SERVICE_PACKAGE_SUCESS, RunshopOperateType.SERVICE_PACKAGE_SUCCESS.getValue());
				} else {
					insertRunshopOperateRecord(id, userId, user.getEmail(), user.getName(),
							ProjectConstant.SERVICE_PACKAGE_FAIL, RunshopOperateType.SERVICE_PACKAGE_FAIL.getValue());
				}
			} catch (Exception e) {
				logger.error("insert_operateRecord_error : applyId {}", id);
			}
			createSettleAccount(rstId, runshopApplicationInfo);

			logger.info("after_create_settle_account, rstId {},applyId {}", rstId, id);

			// 连锁店总店结算的时候，分店首先获取总店的rstId，然后绑定总店的银行卡
			// 将runshopApplicationInfo中的相关信息暂时替换成总店信息

			if (runshopApplicationInfo.getHead_id() != 0 && runshopApplicationInfo.getSettle_way() == 1) {
				replaceBankcardWithHeadOffice(runshopApplicationInfo);
			}


			passFlavor(runshopApplicationInfo, rstId);
			logger.info("after_pass_flavor, rstId {}", rstId);
			passFoodInfo(runshopApplicationInfo, rstId, userId);
			logger.info("after_save_food_info,rstId {}", rstId);

			passDeliveryInfo(runshopApplicationInfo, rstId, userId);
			logger.info("after_pass_delivery_info, rstId {}", rstId);

			// updateRestaurantDelivery(rstId, userId,update);

			try {
				if (runshopApplicationInfo.getSettlement_type() == 2) {
					bindCardService.bindCard4Private(userId, runshopApplicationInfo);
				} else if (runshopApplicationInfo.getSettlement_type() == 1) {
					bindCardService.bindCard4Business(userId, runshopApplicationInfo);
				}
				logger.info("after#bind#card#with#rstId:{}", rstId);
			} catch (Exception e) {
				logger.error("bind#card#error, {}", e);
			}

			bindStoreAdmin(runshopApplicationInfo, rstId);
			logger.info("after_bind_store_info,rstId {}", rstId);

			runshopService.assignBD(runshopApplicationInfo, rstId, id);

			logger.info("after_assign_bd rstId {}", rstId);

	}

	//
	@Async
	public void asyncAfterProcess(RunshopApplicationInfo runshopApplicationInfo,int userId,User user,int id,RunshopService runshopService, int rstId) {

		try {
			// 如果dom_id > 0,回写建店成功的id到dom
			if (runshopApplicationInfo.getDom_id() > 0) {
				try {
					boolean isBanding = iOpenstoreService.relateShop(runshopApplicationInfo.getDom_id(), rstId);
					if (!isBanding) {
						logger.info(String.format("[domId: %s, rstId: %s]rstId write back failed.",
								runshopApplicationInfo.getDom_id(), rstId));
					} else {
						logger.info(String.format("[domId: %s, rstId: %s]rstId write back success.",
								runshopApplicationInfo.getDom_id(), rstId));
					}
				} catch (Exception e) {
					logger.error(
							String.format("[domId: %s, rstId: %s]rstId write back error.",
									runshopApplicationInfo.getDom_id(), rstId), e);
				}
			}

			// 连锁店分店,绑定总店及品牌信息
			if (runshopApplicationInfo.getHead_id() != 0) {
				bindHeadOffice(rstId, runshopApplicationInfo.getHead_id());
				bindBrand(rstId, runshopApplicationInfo.getBrand_id());
			}
			// createSettleAccount();
			// 支付方式和结算方式用服务包接口代替
			boolean signFreeContractResult = packClientService.signFreeContract(rstId);
			logger.info("after free contract success");
			try {
				if (signFreeContractResult) {
					insertRunshopOperateRecord(id, userId, user.getEmail(), user.getName(),
							ProjectConstant.SERVICE_PACKAGE_SUCESS, RunshopOperateType.SERVICE_PACKAGE_SUCCESS.getValue());
				} else {
					insertRunshopOperateRecord(id, userId, user.getEmail(), user.getName(),
							ProjectConstant.SERVICE_PACKAGE_FAIL, RunshopOperateType.SERVICE_PACKAGE_FAIL.getValue());
				}
			} catch (Exception e) {
				logger.error("insert_operateRecord_error : applyId {}", id);
			}
			createSettleAccount(rstId, runshopApplicationInfo);

			logger.info("after_create_settle_account, rstId {},applyId {}", rstId, id);

			// 连锁店总店结算的时候，分店首先获取总店的rstId，然后绑定总店的银行卡
			// 将runshopApplicationInfo中的相关信息暂时替换成总店信息

			if (runshopApplicationInfo.getHead_id() != 0 && runshopApplicationInfo.getSettle_way() == 1) {
				replaceBankcardWithHeadOffice(runshopApplicationInfo);
			}


			passFlavor(runshopApplicationInfo, rstId);
			logger.info("after_pass_flavor, rstId {}", rstId);
			passFoodInfo(runshopApplicationInfo, rstId, userId);
			logger.info("after_save_food_info,rstId {}", rstId);

			passDeliveryInfo(runshopApplicationInfo, rstId, userId);
			logger.info("after_pass_delivery_info, rstId {}", rstId);

			// updateRestaurantDelivery(rstId, userId,update);

			try {
				if (runshopApplicationInfo.getSettlement_type() == 2) {
					bindCardService.bindCard4Private(userId, runshopApplicationInfo);
				} else if (runshopApplicationInfo.getSettlement_type() == 1) {
					bindCardService.bindCard4Business(userId, runshopApplicationInfo);
				}
				logger.info("after#bind#card#with#rstId:{}", rstId);
			} catch (Exception e) {
				logger.error("bind#card#error, {}", e);
			}

			bindStoreAdmin(runshopApplicationInfo, rstId);
			logger.info("after_bind_store_info,rstId {}", rstId);

			runshopService.assignBD(runshopApplicationInfo, rstId, id);

			logger.info("after_assign_bd rstId {}", rstId);
		}catch (Exception e){
			logger.error("asyncAfterProcessFailedAt {} ,runshopapplicationInfo {} ", e, runshopApplicationInfo);
		}
	}



	private void bindHeadOffice(int rstId, int headId) {
		logger.info("RunshopService#bindHeadOffice into, rstId is {}, headId is {}", rstId, headId);
		HeadOffice headOffice = headOfficeMapper.getHeadOfficeById(headId);
		int chainId = headOffice.getHead_office_id();
		logger.info("RunshopService#bindHeadOffice chainId is {}", chainId);
		if (chainId == 0) {
			logger.error("RunshopService#bindHeadOffice done, chainId is 0");
			return;
		}

		List<Integer> chainIds = new ArrayList<>();
		chainIds.add(chainId);
		List<LeafShopCreate> creates = new ArrayList<>();
		LeafShopCreate leafShopCreate = new LeafShopCreate();
		leafShopCreate.setShopId(rstId);
		creates.add(leafShopCreate);
		try {
			Map<Integer, Head> headMap = relationShopService.getShopHeads(chainIds, SystemType.CHAIN);
			if (headMap != null && headMap.get(chainId) != null) {
				relationShopService.createLeafShops(creates, chainId, SystemType.CHAIN, headMap.get(chainId)
						.getHeadId(), 0);
			} else {
				logger.error(String.format("RunshopService#bindHeadOffice 获取连锁店头信息失败, chainId: %s", chainId));
			}
		} catch (Exception ex) {
			logger.error(String.format("RunshopService#bindHeadOffice 创建连锁店子节点失败, restaurantId: %d, chainId: %s",
					rstId, chainId), ex);
		}
		logger.info("RunshopService#bindHeadOffice success");
	}

	private void replaceBankcardWithHeadOffice(RunshopApplicationInfo runshopApplicationInfo) {

		try {
			CardSimpleDto cardSimpleDto = bankCardByRstId(runshopApplicationInfo.getHead_id());

			if (cardSimpleDto.getSettlement_type() == RunshopServiceConstants.SAURON_BANK_PUBLIC) {
				runshopApplicationInfo.setBankacard_owner(cardSimpleDto.getReal_name());
				runshopApplicationInfo.setBankcard(cardSimpleDto.getCard_no());

			} else if (cardSimpleDto.getSettlement_type() == RunshopServiceConstants.SAURON_BANK_PRIVATE) {
				runshopApplicationInfo.setBusiness_license_name(cardSimpleDto.getBusi_name());
				runshopApplicationInfo.setBusiness_license_num(cardSimpleDto.getAcct_no());
				runshopApplicationInfo.setBankcard(cardSimpleDto.getAcct_no());
				runshopApplicationInfo.setBoss(cardSimpleDto.getAcct_name());
			}

			runshopApplicationInfo.setSettlement_type(cardSimpleDto.getSettlement_type());
			runshopApplicationInfo.setIdentity_number(cardSimpleDto.getCert_no());

			runshopApplicationInfo.setBusiness_license_num(cardSimpleDto.getBusi_no());
			runshopApplicationInfo.setBankcard(cardSimpleDto.getAcct_no());
			runshopApplicationInfo.setBusiness_license_name(cardSimpleDto.getBusi_name());

			runshopApplicationInfo.setBank(cardSimpleDto.getBank_name());
			runshopApplicationInfo.setBranch_banck(cardSimpleDto.getSub_bank_name());
			runshopApplicationInfo.setBankcard_province_id(cardSimpleDto.getProvince_id());
			runshopApplicationInfo.setBankcard_province_name(cardSimpleDto.getProvince_name());
			runshopApplicationInfo.setCity_id(cardSimpleDto.getCity_id());
			runshopApplicationInfo.setCity_name(cardSimpleDto.getCity_name());

			logger.info("replaceBankcardWithHeadOffice success ,headId,{},rstId {}",
					runshopApplicationInfo.getHead_id(), runshopApplicationInfo.getRst_id());

		} catch (Exception e) {
			logger.error("replaceBankcardWithHeadOffice error ,e {},headId,{},rstId {}", e,
					runshopApplicationInfo.getHead_id(), runshopApplicationInfo.getRst_id());
		}
	}



	private void passFlavor(RunshopApplicationInfo runshopApplicationInfo, int rstId) {

		/* 转换门店分类id信息 */
		List<Integer> storeClassificationIdList = null;
		try {
			storeClassificationIdList = JsonHelper.toJsonObject(
					runshopApplicationInfo.getStore_classification_id(), ArrayList.class, Integer.class);
		} catch (Exception e) {
			logger.error("RunshopService#setAuditStatus[json parse store classification id] error", e);
			//	throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_DISTRIBUTION_ERROR, e);
		}
		logger.debug("RunshopService#setAuditStatus storeClassificationIdList is:{}", storeClassificationIdList);
			/* 保存提交失败的信息 */
		StringBuilder submitFail = new StringBuilder();

		/**
		 * . 设置餐厅门店分类
		 */
			/* 为餐厅设置分类信息 */
		if (null == storeClassificationIdList) {
			submitFail.append(ProjectConstant.STORE_CLASSIFICATION);
			logger.error(
					"RunshopService#setAuditStatus storeClassificationIdList empty, runshop id is:{}, rstID is:{}",
					runshopApplicationInfo.getId(), rstId);
		} else {
			try {
				for (int storeClassificationId : storeClassificationIdList) {
					elemeRestaurantClientService.set_restaurant_newflavor_relation(rstId, storeClassificationId);
				}
				logger.info("set restaurant flavor success");
			} catch (Exception e) {
				submitFail.append(ProjectConstant.STORE_CLASSIFICATION);
				logger.warn("RunshopService#setAuditStatus[set_restaurant_newflavor_relation] error", e);
			}
		}

	}

	private void createSettleAccount(int rstId, RunshopApplicationInfo runshopApplicationInfo) {

		// 开通余额账户
		financeAccountService.create(rstId, runshopApplicationInfo.getStore_name());

		// 如果是连锁店的分店 而且 结算方式 为分店结算的话
		if (runshopApplicationInfo.getHead_id() != 0 && runshopApplicationInfo.getSettle_way() == RunshopServiceConstants.HEAD_OFFICE_SETTLEMENT) {
			try {
				HeadOffice headOffice = headOfficeMapper.getHeadOfficeById(runshopApplicationInfo.getHead_id());
				hydrosService.settlementConfig(rstId, headOffice.getHead_office_id());
			} catch (Exception e) {
				logger.error("selltmentConfig_error rstId {}, e {}", rstId, e);
			}
		}
	}

	private void bindStoreAdmin(RunshopApplicationInfo runshopApplicationInfo, int rstId) {

		/* 手机号是否被使用的校验 */
		Keeper keeperUser = null;
		String mobile = runshopApplicationInfo.getMobile();
		try {
			keeperUser = keeperForNaposClientService.getKeeperUserByMobile(mobile);
		} catch (Exception e) {
			logger.error("Runshop#setAuditStatus[getKeeperUserByMobile] keeper {}", e);
		}

			/*
			 * 标记绑定管理员相关操作是否成功，true成功，false未成功，默认为true--成功
			 * 这里配合商户提交开店申请，商户提交的时候去调用keeper的用户校验
			 * 这里如果发现手机号已经在keeper那边使用过了，那么任然建店，让BD去解决这个问题
			 */
		boolean b = true;
		if (null != keeperUser) {
			logger.warn(
					"Runshop#setAuditStatus[getKeeperUserByMobile] user is already exist, runshop name is:{}, runshop mobile is:{}",
					runshopApplicationInfo.getStore_name(), mobile);
			b = false;
		}

			/* 生成eus注册管理员相关信息 */
		String username = CommonUtils.initAdminUsername(mobile);
		String email = CommonUtils.initAdminEmail(mobile);
		String password = CommonUtils.initRandomPassword();
		logger.info("RunshopService#setAuditStatus, username:{}, email:{}, password:{}", username, email, password);
		int rstAdminId = 0;

			/* 保存管理员是否创建成功 */
		String createAdmin;
			/* 保存管理员是否绑定成功 */
		String bindAdmin;
		/**
		 * 如果手机号没有在eus中注册过，则执行绑定管理员操作
		 */
			/* 这里如果手机号没有在eus那边使用过我才去用户，否则不去建用户 */
		if (b) {
			try {
					/* 创建eus信息，返回创建的eus中餐厅老板id */
				// NONO 不调用eus接口了，调用napos.keeper的接口
				// rstAdminId = elemeUserService.signup(username, email,
				// password);
				logger.info("RunshopService#setAuditStatus,keeperForNaposService.createRestaurantAdmin,start");
				rstAdminId = keeperForNaposClientService.createRestaurantAdmin(username, password, rstId, 1, 1,
						mobile);
				logger.info(
						"RunshopService#setAuditStatus,keeperForNaposService.createRestaurantAdmin,rstAdminId:{}",
						rstAdminId);
			} catch (Exception e) {
				b = false;
				logger.error("RunshopService#setAuditStatus[signup] error", e);
			}
			logger.debug("RunshopService#setAuditStatus[signup] rstAdminId is:{}", rstAdminId);
			if (0 == rstAdminId) {
				b = false;
				createAdmin = ProjectConstant.CREATE_RESTAURANT_ADMIN_FAIL;
				bindAdmin = ProjectConstant.BIND_RESTAURANT_ADMIN_FAIL;
				logger.error(
						"RunshopService#setAuditStatus[signup] rstAdminId is 0, username:{}, email:{}, password:{}",
						username, email, password);
			} else {
				createAdmin = ProjectConstant.CREATE_RESTAURANT_ADMIN_SUCCESS;
				bindAdmin = ProjectConstant.BIND_RESTAURANT_ADMIN_SUCCESS;
			}
		} else {
			createAdmin = ProjectConstant.CREATE_RESTAURANT_ADMIN_FAIL;
			bindAdmin = ProjectConstant.BIND_RESTAURANT_ADMIN_FAIL;
		}


		// String operateData =
		// String.format(ProjectConstant.QUALIFIED_RECORD_MESSAGE, rstID,
		// sendMessage,
		// createAdmin, bindAdmin, 0 == submitFail.length() ?
		// ProjectConstant.RECORD_NO_ERROR : submitFail
		// .replace(0, 1, "").toString(), processCertificationRecord);
		// /* 记录日志信息 */
		// insertRunshopOperateRecord(id, oper_userId, user.getEmail(), use
	}

	private void passDeliveryInfo(RunshopApplicationInfo runshopApplicationInfo,int rstId,int userId) {

		RestaurantDeliveryUpdate restaurantDeliveryUpdate = new RestaurantDeliveryUpdate();
		List<Delivery> list = runshopMapper.selectDeliveryByRunshopId(runshopApplicationInfo.getId());
		RestaurantDeliveryUpdate.GeoLocation location = new RestaurantDeliveryUpdate.GeoLocation();

		location.setLatitude(runshopApplicationInfo.getLatitude() == null ? 0 : runshopApplicationInfo
				.getLatitude().doubleValue());
		location.setLongitude(runshopApplicationInfo.getLatitude() == null ? 0 : runshopApplicationInfo
				.getLongitude().doubleValue());

		restaurantDeliveryUpdate.setLocation(location);
		List<RestaurantDeliveryArea> areas = Lists.newLinkedList();

		for (Delivery delivery : list) {

			RestaurantDeliveryArea restaurantDeliveryArea = new RestaurantDeliveryArea();
			restaurantDeliveryArea.setAreaAgentFee(delivery.getDelivery_price());
			restaurantDeliveryArea.setPrice(delivery.getBase_price());
			String range = delivery.getRange();

			try {
				List<Coordinate> coordinateList = JsonHelper.toJsonObject(range, List.class, Coordinate.class);
				List<GeoLocation> vertexes = Lists.newLinkedList();
				for (Coordinate coordinate : coordinateList) {
					GeoLocation geoLocation = new GeoLocation();
					geoLocation.setLatitude(coordinate.getLat());
					geoLocation.setLongitude(coordinate.getLng());
					vertexes.add(geoLocation);
				}
				restaurantDeliveryArea.setVertexes(vertexes);
			} catch (JsonParseException e) {
				logger.error("parse coordinate error, {}", e);
			} catch (Exception e) {
				logger.error("parse coordinate error,{}", e);
			}

			areas.add(restaurantDeliveryArea);
		}
		try {
			restaurantDeliveryUpdate.setAreas(areas);
			restaurantService.updateRestaurantDelivery(rstId, userId, restaurantDeliveryUpdate);
			logger.info("set restaurant areas success,rstId {}", rstId);
		} catch (Exception e) {
			logger.error("update delivery error,{}", e);
		}



	}

	private void passFoodInfo(RunshopApplicationInfo runshopApplicationInfo,int rstId,int userId) {


		TFoodCategory foodCategory = new TFoodCategory();
		foodCategory.setRestaurant_id(rstId);
		foodCategory.setIs_valid((short) 1);
		foodCategory.setName("美食");

		// TFood food = new TFood();
		// food.setRestaurant_id();

		try {
			int t_category = elemeRestaurantService.save_food_category_with_operator_id(0, foodCategory, userId);
			logger.info("after_save_food_category {}", t_category);
			List<GoodInfo> foodList = runshopMapper.getGoodInfoByRunshopId(runshopApplicationInfo.getId());

			if(!org.springframework.util.CollectionUtils.isEmpty(foodList)){
				for (GoodInfo goodInfo : foodList) {
					TFood food = new TFood();
					food.setRestaurant_id(rstId);
					food.setName(goodInfo.getGood_name());
					food.setIs_valid((short) 1);
					food.setCategory_id(t_category);

					food.setDescription(goodInfo.getGood_description());
					food.setPrice(goodInfo.getUnit_price());
					food.setPacking_fee(goodInfo.getMeal_price());
					food.setStock(goodInfo.getStock_current());
					food.setMax_stock(goodInfo.getStock_max());
					food.setImage_url(goodInfo.getGood_pic());

					// 存储hash
					if (StringUtils.isNotBlank(goodInfo.getGood_pic())) {

						String imageHash = fussService.uploadImageToCdn(goodInfo.getGood_pic(), "");
						// CommonUtils.convertUrlToHash(goodInfo.getGood_pic());
						food.setImage_hash(imageHash);
					}

					food.setAttribute("{}");
					logger.debug(
							"RunshopService#setAuditStatus,elemeRestaurantService.save_food_new_with_operator_id,food:{}",
							food);
					try {
						elemeRestaurantService.save_food_new_with_operator_id(0, food, userId);
						logger.info("after_save_food,userId {}, food {}", userId, food);
					} catch (Exception e) {
						logger.error("set food error");
					}
				}
			}
		}catch (Exception e){
			logger.error("save_food_error_for rst {}",rstId);
		}
	}

	private void bindBrand(int rstId, int brandId) {
		logger.info("RunshopService#bindBrand into, rstId is {}, brandId is {}", rstId, brandId);
		if (brandId == 0) {
			return;
		}

		try {
			Map<Integer, SetShopBrandRelationResult> ret = shopBrandService.setShopBrandRelations(brandId,
					Arrays.asList(new Integer[]{rstId}));
			logger.info("RunshopService#bindBrand bind success, ret is {}", ret);
		} catch (Exception e) {
			logger.info("RunshopService#bindBrand bind failed, ", e);
		}

	}

	private int insertRunshopOperateRecord(int runshopId, int coffeeUserId, String coffeeUserEmail,
										   String coffeeUserName, String operateData, int operateType) throws ServiceException, ServerException {
		RunshopOperateRecord runshopOperateRecord = new RunshopOperateRecord();
		runshopOperateRecord.setRunshop_id(runshopId);
		runshopOperateRecord.setCoffee_user_id(coffeeUserId);
		runshopOperateRecord.setCoffee_user_email(coffeeUserEmail);
		runshopOperateRecord.setCoffee_user_name(coffeeUserName);
		runshopOperateRecord.setOperate_data(operateData);
		runshopOperateRecord.setOperate_type(operateType);
		int runshopOperateRecordId;
		try {
			runshopOperateRecordId = runshopOperateRecordMapper.insertRunshopOperateRecord(runshopOperateRecord);
		} catch (Exception e) {
			logger.error("RunshopService#insertRunshopOperateRecord error", e);
			throw ExceptionUtil.createServerException(ExceptionCode.INSERT_RUNSHOP_OPERATE_RECORD_ERROR);
		}
		return runshopOperateRecordId;
	}

	public CardSimpleDto bankCardByRstId(int rstId) throws IOException {
		// 银行卡

		CardQuery card_bind_q_query = new CardQuery();
		card_bind_q_query.setApp_id(sauron_appid);
		card_bind_q_query.setClient_id(sauron_client_id);
		card_bind_q_query.setMerchant_id(rstId + "");
		card_bind_q_query.setType(0);

		Map<String, String> map = ElemeHelper.convertBeanToMap(card_bind_q_query);
		map.remove("sign");
		logger.info("RunshopService#bankCardByRstId,sign,map:{}", map);
		String sign = ElemeHelper.sign(map, souron_key);
		card_bind_q_query.setSign(sign);
		CardResp cards = null;
		try {
			logger.info("RunshopService#bankCardByRstId#card_bind_q_query:{}", card_bind_q_query);
			// 重试3次，服务依然不可用时抛异常
			for (int i = 0; i < RunshopServiceConstants.RETRY_TIMES; i++) {
				try {
					cards = sauronService.card_bind_query(card_bind_q_query);
					break;
				} catch (Exception e) {
					if (i == RunshopServiceConstants.RETRY_TIMES - 1) {
						throw e;
					}
				}
			}
			logger.info("RunshopService#bankCardByRstId,sauronService,cards:{}", cards);
			String carNo = "";
			// String acctNo = "";
			for (Card card : cards.getData_list()) {
				CardSimpleDto cardSimpleDto = new CardSimpleDto();
				if (card.getType() == RunshopServiceConstants.SAURON_BANK_PUBLIC) {
					cardSimpleDto.setSettlement_type(RunshopServiceConstants.BANK_PUBLIC);
					cardSimpleDto.setAcct_name(card.getAcct_name());
					cardSimpleDto.setAcct_no(card.getAcct_no());
					cardSimpleDto.setBusi_name(card.getBusi_name());
					cardSimpleDto.setBusi_no(card.getBusi_no());
				} else if (card.getType() == RunshopServiceConstants.SAURON_BANK_PRIVATE) {
					cardSimpleDto.setSettlement_type(RunshopServiceConstants.BANK_PRIVATE);
					cardSimpleDto.setCert_no(card.getCert_no());
					cardSimpleDto.setCard_no(card.getCard_no());
					cardSimpleDto.setReal_name(card.getReal_name());
					cardSimpleDto.setPhone(card.getPhone());
				} else {
					continue;
				}
				cardSimpleDto.setBank_id(card.getBank_id());
				cardSimpleDto.setBank_name(card.getBank_name());
				cardSimpleDto.setSub_bank_name(card.getSub_bank_name());
				cardSimpleDto.setProvince_id(card.getProvince_id());
				cardSimpleDto.setProvince_name(card.getProvince_name());
				cardSimpleDto.setCity_id(card.getCity_id());
				cardSimpleDto.setCity_name(card.getCity_name());
				return cardSimpleDto;

			}
		} catch (Exception e) {
			logger.error("BusinessPackageService#shopDetailDto,sauronService.card_bind_query,e:{}", e);
		}
		return null;
	}

}
