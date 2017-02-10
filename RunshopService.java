package me.ele.bpm.runshop.impl.service;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import me.ele.arch.etrace.agent.aop.Traceable;
import me.ele.bpm.bus.policy.core.service.IPolicyService;
import me.ele.bpm.runshop.core.constants.*;
import me.ele.bpm.runshop.core.dto.*;
import me.ele.bpm.runshop.core.form.*;
import me.ele.bpm.runshop.core.model.*;
import me.ele.bpm.runshop.core.model.constant.RunShopStatus;
import me.ele.bpm.runshop.core.model.constant.RunshopOperateType;
import me.ele.bpm.runshop.core.service.IHeadOfficeService;
import me.ele.bpm.runshop.core.service.IRunshopService;
import me.ele.bpm.runshop.core.service.ISaturnCityService;
import me.ele.bpm.runshop.core.thrift.ers.ERSSystemException;
import me.ele.bpm.runshop.core.thrift.ers.ERSUnknownException;
import me.ele.bpm.runshop.core.thrift.ers.ERSUserException;
import me.ele.bpm.runshop.impl.client.*;
import me.ele.bpm.runshop.impl.dao.elemeReport.BaiduFoodMapper;
import me.ele.bpm.runshop.impl.dao.elemeReport.MeituanFoodMapper;
import me.ele.bpm.runshop.impl.dao.runshop.*;
import me.ele.bpm.runshop.impl.resource.es.EsSearchService;
import me.ele.bpm.runshop.impl.resource.es.entity.RunshopEsEntity;
import me.ele.bpm.runshop.impl.resource.es.entity.RunshopEsQuery;
import me.ele.bpm.runshop.impl.resource.es.entity.RunshopEsResult;
import me.ele.bpm.runshop.impl.service.async.RunshopAsyncService;
import me.ele.bpm.runshop.impl.task.ApplyInfoTask;
import me.ele.bpm.runshop.impl.task.VerifyTask;
import me.ele.bpm.runshop.impl.thirdparty.HermesClient;
import me.ele.bpm.runshop.impl.thirdparty.ers.ElemeRestaurantService;
import me.ele.bpm.runshop.impl.thirdparty.ers.TRestaurant;
import me.ele.bpm.runshop.impl.thirdparty.sauron.Card;
import me.ele.bpm.runshop.impl.thirdparty.sauron.CardQuery;
import me.ele.bpm.runshop.impl.thirdparty.sauron.CardResp;
import me.ele.bpm.runshop.impl.thirdparty.sauron.SauronService;
import me.ele.bpm.runshop.impl.threads.DistributeThread;
import me.ele.bpm.runshop.impl.threads.OpenStoreThread;
import me.ele.bpm.runshop.impl.utils.*;
import me.ele.coffee.hrm.hr.dto.BusinessUnitDto;
import me.ele.coffee.hrm.hr.dto.UserBuRoleDto;
import me.ele.coffee.model.hr.model.User;
import me.ele.config.HuskarHandle;
import me.ele.contract.client.ClientUtil;
import me.ele.contract.exception.ServerException;
import me.ele.contract.exception.ServiceException;
import me.ele.elog.Log;
import me.ele.elog.LogFactory;
import me.ele.eve.soa.core.dto.message.EveMessageForm;
import me.ele.eve.soa.core.service.IMessageService;
import me.ele.jarch.fuss.iface.FussService;
import me.ele.napos.miracle.keeper.api.entities.Keeper;
import me.ele.napos.miracle.restaurant.api.PhotoService;
import me.ele.napos.miracle.restaurant.api.RestaurantService;
import me.ele.napos.miracle.restaurant.api.entities.Restaurant;
import me.ele.napos.miracle.restaurant.api.entities.RestaurantBusyLevel;
import me.ele.napos.miracle.restaurant.api.entities.RestaurantCreation;
import me.ele.napos.operation.service.lucifer.api.LAuditService;
import me.ele.napos.operation.service.lucifer.api.LMerchantService;
import me.ele.napos.operation.service.lucifer.api.LShopCertificationService;
import me.ele.napos.operation.shared.payload.entities.applybase.ApplyShopBase;
import me.ele.napos.operation.shared.payload.entities.audit.*;
import me.ele.napos.operation.shared.payload.entities.certification.LicenseType;
import me.ele.napos.operation.shared.payload.entities.certification.PermitType;
import me.ele.pcd.dom.service.dto.OpenStoreDto;
import me.ele.pcd.dom.service.service.IOpenstoreService;
import me.ele.work.flow.engine.core.constants.RunshopCreatorEnum;
import me.ele.work.flow.engine.core.dto.RunshopAuditSimpleStatusDto;
import me.ele.work.flow.engine.core.form.RunshopWorkflowForm;
import me.ele.work.flow.engine.core.service.IRunshopWorkFlowService;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.elasticsearch.common.Strings;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import static me.ele.bpm.runshop.impl.utils.ProjectConstant.IS_NEW;

/**
 * runshopService.
 */
@Service("runshopService")
public class RunshopService implements IRunshopService {

	/**
	 * elog.
	 */
	private static Log logger = LogFactory.getLog(RunshopService.class);

	/**
	 * 水印图片地址.
	 */
	private static String logoPath;

	/**
	 * cdn的fuss服务名称.
	 */
	private static final String CDN_FUSS_SERVICE_NAME = "arch.fuss";

	/**
	 * walle的fuss服务名称.
	 */
	private static final String WALLE_FUSS_SERVICE_NAME = "arch.fuss.safe";

	/**
	 * 两证是否必填(许可证).
	 */
	private static String CITIES_CATERING_SERVICES_NOT_REQUIRED = "cities_catering_services_not_required";
	/**
	 * 两证是否必填(营业执照).
	 */
	private static String CITIES_BUSINESS_LICENSE_NOT_REQUIRED = "cities_business_license_not_required";

	/**
	 * fuss 服务的集群.
	 */
	@Value("${fuss.group}")
	private String GROUP;

	/**
	 * runshopMapper.
	 */
	@Autowired
	private RunshopMapper runshopMapper;

	@Resource(name ="runshopAsyncService")
	private RunshopAsyncService runshopAsyncService;

	/**
	 * runshopPictureMapper.
	 */
	@Autowired
	private RunshopPictureMapper runshopPictureMapper;

	@Autowired
	private HeadOfficeMapper headOfficeMapper;
	/**
	 * d excludeCitiesMapper.
	 */
	@Autowired
	private ExcludeCitiesMapper excludeCitiesMapper;

	/**
	 * elemeRestaurantService.
	 */
	@Autowired
	private ElemeRestaurantService elemeRestaurantService;

	@Autowired
	private IOpenstoreService iOpenstoreService;

	@Autowired
	private BlackListMapper blackListMapper;

	/**
	 * elemeRestaurantClientService.
	 */
	@Autowired
	private ElemeRestaurantClientService elemeRestaurantClientService;

	/**
	 * elemeUserClientService.
	 */
	@Autowired
	private ElemeUserClientService elemeUserClientService;

	/**
	 * hermesClient.
	 */
	@Autowired
	private HermesClient hermesClient;

	/**
	 * runshopOperateRecordMapper.
	 */
	@Autowired
	private RunshopOperateRecordMapper runshopOperateRecordMapper;

	/**
	 * coffeeHrClientService.
	 */
	@Autowired
	private CoffeeHrClientService coffeeHrClientService;

	/**
	 * photoService.
	 */
	@Autowired
	private PhotoService photoService;

	/**
	 * policyService.
	 */
	@Autowired
	private IPolicyService policyService;

	/**
	 * 走cnd的fuss服务.
	 */
	private FussService fussCdnService;

	/**
	 * 走safe的fuss服务.
	 */
	private FussService fussWalleService;

	@Autowired
	private FussClientService fussService;
	/**
	 * messageService.
	 */
	@Autowired
	private IMessageService messageService;

	/**
	 * naposRestaurantClientService.
	 */
	@Autowired
	private NaposRestaurantClientService naposRestaurantClientService;
	/**
	 * rumbleClientService.
	 */
	@Autowired
	private RumbleClientService rumbleClientService;
	/**
	 * keeperForNaposClientService.
	 */
	@Autowired
	private KeeperForNaposClientService keeperForNaposClientService;
	/**
	 * saturnCityService.
	 */
	@Autowired
	private ISaturnCityService saturnCityService;
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


	@Autowired
	private RunshopCoreService runshopCoreService;

	/**
	 * packClientService.
	 */
	@Autowired
	private PackClientService packClientService;
	@Autowired
	private LShopCertificationService lShopCertificationService;
	@Autowired
	private IRunshopWorkFlowService runshopWorkFlowService;

	@Autowired
	private LMerchantService lMerchantService;

	@Autowired
	private LAuditService lAuditService;

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
	private IHeadOfficeService headOfficeService;

	@Autowired
	private BaiduFoodMapper baiduFoodMapper;

	@Autowired
	private MeituanFoodMapper meituanFoodMapper;

	@Autowired
	private EsSearchService esSearchService;

	/**
	 * init .
	 */
	@PostConstruct
	public void init() {
		fussCdnService = ClientUtil.getContext().getClient(CDN_FUSS_SERVICE_NAME, GROUP, FussService.class);
		fussWalleService = ClientUtil.getContext().getClient(WALLE_FUSS_SERVICE_NAME, GROUP, FussService.class);
		// sauronService =
		// ClientUtil.getContext().getClient(SauronService.class);

		logoPath = System.getProperty("user.dir") + "/config/logo.png";
		logger.info("RunshopService#static logoPaht is:{}", logoPath);
	}

	/**
	 * 查询开店申请列表信息 根据开店申请名称，老板手机号，开店申请状态，开始和截止时间查询.
	 */
	@Override
	public PaginationResponse<RunshopApplicationInfo> listRunshopByStoreNameOrMobile(RunshopListForm runshopListForm)
			throws ServiceException, ServerException {
		if (null == runshopListForm) {
			throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_LIST_FORM_NULL);
		}
		if (null == runshopListForm.getBegin_date()) {
			throw ExceptionUtil.createServiceException(ExceptionCode.BEGIN_DATE_NULL);
		}
		if (null == runshopListForm.getEnd_date()) {
			throw ExceptionUtil.createServiceException(ExceptionCode.END_DATE_NULL);
		}
		int total;
		List<RunshopApplicationInfo> list;
		try {
			total = runshopMapper.countRunshop(runshopListForm);
		} catch (Exception e) {
			logger.error("RunshopService#countRunshop error", e);
			throw ExceptionUtil.createServerException(ExceptionCode.COUNT_RUNSHOP_ERROR);
		}
		try {
			if (runshopListForm.getStatus() == RunshopServiceConstants.WAITING_CHECK) {
				list = runshopMapper.listRunshopByStoreNameOrMobile(runshopListForm);
			} else {
				list = runshopMapper.listRunshopByStoreNameOrMobileDesc(runshopListForm);

			}
		} catch (Exception e) {
			logger.error("RunshopService#listRunshopByStoreNameOrMobile error", e);
			throw ExceptionUtil.createServerException(ExceptionCode.LIST_RUNSHOP_BY_STORENAME_OR_MOBILE_ERROR);
		}
		PaginationResponse<RunshopApplicationInfo> result = new PaginationResponse<>();
		result.setList(list);
		result.setTotal(total);
		return result;
	}

	/**
	 * 根据开店申请id,获取开单申请详情信息.
	 *
	 * @param runshopId
	 * @return
	 * @throws ServiceException
	 * @throws ServerException
	 */
	@Override
	public RunshopApplicationForEveDto getRunshopApplicationDetailForEve(int runshopId) throws ServiceException,
			ServerException {
		logger.info("RunshopService#getRunshopApplicationDetailForEve into, runshopId is:{}", runshopId);
		if (runshopId <= 0) {
			throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_NO_RULE_ID);
		}
		/* 最终要返回的数据 */
		RunshopApplicationForEveDto runshopApplicationForEveDto = new RunshopApplicationForEveDto();
		/* 查询开店申请详情信息 开始 */
		RunshopApplicationInfo runshopApplicationInfo = runshopMapper.getRunshopApplicationInfoById(runshopId);
		if (null == runshopApplicationInfo) {
			throw ExceptionUtil.createServiceException(ExceptionCode.GET_RUNSHOP_BY_ID_NULL);
		}
		/* 查询开店申请详情信息 结束 */
		BeanUtils.copyProperties(runshopApplicationInfo, runshopApplicationForEveDto);
		logger.info(
				"RunshopService#getRunshopApplicationDetailForEve[copyProperties] runshopApplicationForEveDto is:{}",
				runshopApplicationForEveDto);
		runshopApplicationForEveDto.setAuditTimes(runshopApplicationInfo.getSubmit_times());
		runshopApplicationForEveDto.setRst_id(runshopApplicationInfo.getRst_id());
		/* 格式化店铺分类信息 */
		String store_classification = runshopApplicationInfo.getStore_classification();
		String[] store_classification_arr = StringUtils.substringsBetween(store_classification, "\"", "\"");
		StringBuilder sb = new StringBuilder();
		for (String classification : store_classification_arr) {
			sb.append(classification);
			sb.append(ProjectConstant.DOT_SYMBOL);
		}
		sb.setLength(sb.length() - 1);
		runshopApplicationForEveDto.setStore_classification(sb.toString());

		/* 格式化并生成时间信息 */
		try {
			if (ProjectConstant.BUSINESS_LICENSE_NO_EXPIRE_TIME.equals(runshopApplicationInfo
					.getBusiness_license_time())) {
				runshopApplicationForEveDto.setBusiness_license_time(ProjectConstant.BUSINESS_LICENSE_NO_EXPIRE_TIME);
			} else {
				String business_license_time = DateUtil.format(runshopApplicationInfo.getBusiness_license_time(),
						ProjectConstant.DATE_FORMAT, ProjectConstant.DATE_FORMAT_EVE);
				if (StringUtils.isNotBlank(business_license_time))
					runshopApplicationForEveDto.setBusiness_license_time(business_license_time);
			}

		} catch (ParseException e) {
			logger.error("RunshopService#getRunshopApplicationDetailForEve[parse business_license_time] error", e);
		}
		try {
			String catering_services_license_time = DateUtil.format(
					runshopApplicationInfo.getCatering_services_license_time(), ProjectConstant.DATE_FORMAT,
					ProjectConstant.DATE_FORMAT_EVE);
			if (StringUtils.isNotBlank(catering_services_license_time))
				runshopApplicationForEveDto.setCatering_services_license_time(catering_services_license_time);
		} catch (ParseException e) {
			logger.error(
					"RunshopService#getRunshopApplicationDetailForEve[parse catering_services_license_time] error", e);
		}
		/* 生成图片信息 开始 */
		List<RunshopApplicationPicture> runshopApplicationPictureList = runshopPictureMapper
				.listRunshopApplicationPictureByRunshopId(runshopId);
		createRunshopPic(runshopApplicationPictureList, runshopApplicationForEveDto);
		/* 生成图片信息 结束 */

		setStoreClassificationIds(runshopApplicationForEveDto, runshopApplicationInfo);
		setBusinessLicenseInfo(runshopApplicationForEveDto, runshopApplicationInfo);
		setCateringServicesLicenseFlag(runshopApplicationForEveDto, runshopApplicationInfo);
		setBusinessLicenseTimeNoLimit(runshopApplicationForEveDto, runshopApplicationInfo);
		if (runshopApplicationInfo.getDom_id() > 0) {
			runshopApplicationForEveDto.setThirdPlatformMap(generateThirdPlatFormInfo(runshopApplicationInfo.getDom_id()));
		}
		logger.info("RunshopService#getRunshopApplicationDetailForEve done, ret is:{}", runshopApplicationForEveDto);
		return runshopApplicationForEveDto;
	}

	private void setBusinessLicenseTimeNoLimit(RunshopApplicationForEveDto runshopApplicationForEveDto,
			RunshopApplicationInfo runshopApplicationInfo) {
		if (ProjectConstant.BUSINESS_LICENSE_NO_EXPIRE_TIME.equals(runshopApplicationInfo.getBusiness_license_time())) {
			runshopApplicationForEveDto.setBusiness_license_time_no_limit(1);
		} else {
			runshopApplicationForEveDto.setBusiness_license_time_no_limit(0);
		}
	}

	private void setCateringServicesLicenseFlag(RunshopApplicationForEveDto runshopApplicationForEveDto,
			RunshopApplicationInfo runshopApplicationInfo) {
		if (StringUtils.isEmpty(runshopApplicationInfo.getCatering_services_license_address())
				&& StringUtils.isEmpty(runshopApplicationInfo.getCatering_services_license_name())
				&& StringUtils.isEmpty(runshopApplicationInfo.getCatering_services_license_num())
				&& StringUtils.isEmpty(runshopApplicationInfo.getCatering_services_license_time())) {
			runshopApplicationForEveDto.setCatering_services_license_flag(0);
		} else {
			runshopApplicationForEveDto.setCatering_services_license_flag(1);
			// 兼容老的客户端,type为0的情况
			if (runshopApplicationInfo.getCatering_services_license_type() == 0) {
				runshopApplicationForEveDto.setCatering_services_license_type(1);
			}

			for (CateringServicesLicenseTypeEnum type : CateringServicesLicenseTypeEnum.values()) {
				if (runshopApplicationForEveDto.getCatering_services_license_type() == type.getValue()) {
					runshopApplicationForEveDto.setCatering_services_license_type_name(type.getName());
					break;
				}
			}
			if (StringUtils.isBlank(runshopApplicationForEveDto.getCatering_services_license_type_name())) {
				logger.error(
						"RunshopService#setCateringServicesLicenseFlag error, Catering_services_license_type_name is blank, type is {}",
						runshopApplicationForEveDto.getCatering_services_license_type());
			}
		}
	}

	private void setBusinessLicenseInfo(RunshopApplicationForEveDto runshopApplicationForEveDto,
			RunshopApplicationInfo runshopApplicationInfo) {
		if (StringUtils.isEmpty(runshopApplicationInfo.getBusiness_license_address())
				&& StringUtils.isEmpty(runshopApplicationInfo.getBusiness_license_name())
				&& StringUtils.isEmpty(runshopApplicationInfo.getBusiness_license_num())
				&& StringUtils.isEmpty(runshopApplicationInfo.getBusiness_license_time())) {
			runshopApplicationForEveDto.setBusiness_license_flag(0);
		} else {
			runshopApplicationForEveDto.setBusiness_license_flag(1);
			// 兼容老的客户端,type为0的情况
			if (runshopApplicationInfo.getBusiness_license_type() == 0) {
				runshopApplicationForEveDto.setBusiness_license_type(1);
			}
			for (BusinessLicenseTypeEnum type : BusinessLicenseTypeEnum.values()) {
				if (runshopApplicationForEveDto.getBusiness_license_type() == type.getValue()) {
					runshopApplicationForEveDto.setBusiness_license_type_name(type.getName());
					break;
				}
			}
			if (StringUtils.isBlank(runshopApplicationForEveDto.getBusiness_license_type_name())) {
				logger.error(
						"RunshopService#setBusinessLicenseInfo error, Business_license_type_name is blank, type is {}",
						runshopApplicationForEveDto.getBusiness_license_type());
			}
		}
	}

	private void setStoreClassificationIds(RunshopApplicationForEveDto eveDto,
			RunshopApplicationInfo runshopApplicationInfo) {
		List<Integer> list = new ArrayList<>();
		eveDto.setStore_classification_ids(list);

		String sId = runshopApplicationInfo.getStore_classification_id();
		if (StringUtils.isEmpty(sId)) {
			return;
		}

		sId = StringUtils.remove(sId, "[");
		sId = StringUtils.remove(sId, "]");
		String[] ids = StringUtils.split(sId, ",");
		for (String id : ids) {
			list.add(Integer.parseInt(id));
		}
	}

	/**
	 * 生成eve端的图片相关信息
	 *
	 * @param runshopApplicationPictureList
	 * @param runshopApplicationForEveDto
	 */
	private void createRunshopPic(List<RunshopApplicationPicture> runshopApplicationPictureList,
			RunshopApplicationForEveDto runshopApplicationForEveDto) {
		logger.info("RunshopService#createRunshopPic into");
		if (!CollectionUtils.isEmpty(runshopApplicationPictureList)) {
			for (RunshopApplicationPicture runshopApplicationPicture : runshopApplicationPictureList) {
				switch (runshopApplicationPicture.getPic_code()) {
				/* 身份证正面 */
				case ProjectConstant.IDENTITY_POSITIVE_PIC_CODE:
					createPicMap(ProjectConstant.PIC_S, ProjectConstant.IDENTITY_POSITIVE_PIC_S, ProjectConstant.PIC_M,
							ProjectConstant.IDENTITY_POSITIVE_PIC_M, ProjectConstant.PIC_L,
							ProjectConstant.IDENTITY_POSITIVE_PIC_L, ProjectConstant.IDENTITY_POSITIVE_PIC_O,
							runshopApplicationForEveDto.getIdentity_positive_pic(),
							runshopApplicationPicture.getPic_url());
					break;
				/* 身份证反面 */
				case ProjectConstant.IDENTITY_OPPOSITE_PIC_CODE:
					createPicMap(ProjectConstant.PIC_S, ProjectConstant.IDENTITY_OPPOSITE_PIC_S, ProjectConstant.PIC_M,
							ProjectConstant.IDENTITY_OPPOSITE_PIC_M, ProjectConstant.PIC_L,
							ProjectConstant.IDENTITY_OPPOSITE_PIC_L, ProjectConstant.IDENTITY_OPPOSITE_PIC_O,
							runshopApplicationForEveDto.getIdentity_opposite_pic(),
							runshopApplicationPicture.getPic_url());
					break;
				/* 门头照 */
				case ProjectConstant.DOOR_PIC_CODE:
					createPicMap(ProjectConstant.PIC_S, ProjectConstant.DOOR_PIC_URL_S, ProjectConstant.PIC_M,
							ProjectConstant.DOOR_PIC_URL_M, ProjectConstant.PIC_L, ProjectConstant.DOOR_PIC_URL_L,
							ProjectConstant.DOOR_PIC_URL_O, runshopApplicationForEveDto.getDoor_pics(),
							runshopApplicationPicture.getPic_url());
					break;
				/* 店内照 */
				case ProjectConstant.STORE_PIC_CODE:
					createPicMap(ProjectConstant.PIC_S, ProjectConstant.STORE_PIC_URL_S, ProjectConstant.PIC_M,
							ProjectConstant.STORE_PIC_URL_M, ProjectConstant.PIC_L, ProjectConstant.STORE_PIC_URL_L,
							ProjectConstant.STORE_PIC_URL_O, runshopApplicationForEveDto.getStore_pics(),
							runshopApplicationPicture.getPic_url());
					break;
				/* 营业执照 */
				case ProjectConstant.BUSINESS_LICENSE_PIC_CODE:
					createPicMap(ProjectConstant.PIC_S, ProjectConstant.BUSINESS_LICENSE_PIC_S, ProjectConstant.PIC_M,
							ProjectConstant.BUSINESS_LICENSE_PIC_M, ProjectConstant.PIC_L,
							ProjectConstant.BUSINESS_LICENSE_PIC_L, ProjectConstant.BUSINESS_LICENSE_PIC_O,
							runshopApplicationForEveDto.getBusiness_license_pic(),
							runshopApplicationPicture.getPic_url());
					break;
				/* 服务许可证 */
				case ProjectConstant.CATERING_SERVICES_PIC_CODE:
					createPicMap(ProjectConstant.PIC_S, ProjectConstant.CATERING_SERVICES_PIC_S, ProjectConstant.PIC_M,
							ProjectConstant.CATERING_SERVICES_PIC_M, ProjectConstant.PIC_L,
							ProjectConstant.CATERING_SERVICES_PIC_L, ProjectConstant.CATERING_SERVICES_PIC_O,
							runshopApplicationForEveDto.getCatering_services_pic(),
							runshopApplicationPicture.getPic_url());
					break;
				default:
					logger.warn("RunshopService#createRunshopPic error pic type, type is:{}",
							runshopApplicationPicture.getPic_code());
				}
			}
		}
		logger.info("RunshopService#createRunshopPic done");
	}

	/**
	 * 生成图片map
	 *
	 * @param pic_s
	 * @param pic_key_s
	 * @param pic_m
	 * @param pic_key_m
	 * @param pic_l
	 * @param pic_key_l
	 * @param pic_key_o
	 * @param map
	 * @param pic_url
	 */
	private void createPicMap(String pic_s, String pic_key_s, String pic_m, String pic_key_m, String pic_l,
			String pic_key_l, String pic_key_o, Map<String, Object> map, String pic_url) {
		logger.info("RunshopService#createPicMap into");
		/* s size 图片 */
		String pic_url_s = createPicUrl(pic_url, pic_s);
		logger.info("RunshopService#createPicMap pic url s:{}", pic_url_s);
		map.put(pic_key_s, pic_url_s);
		/* m size 图片 */
		String pic_url_m = createPicUrl(pic_url, pic_m);
		logger.info("RunshopService#createPicMap pic url m:{}", pic_url_m);
		map.put(pic_key_m, pic_url_m);
		/* l size 图片 */
		String pic_url_l = createPicUrl(pic_url, pic_l);
		logger.info("RunshopService#createPicMap pic url l:{}", pic_url_l);
		map.put(pic_key_l, pic_url_l);
		/* o size 图片 */
		map.put(pic_key_o, pic_url);
		logger.info("RunshopService#createPicMap done");
	}

	private String createPicUrl(String picUrl, String urlSize) {
		logger.info("RunshopService#createPicUrl into, pirUrl is:{}, urlSize is:{}", picUrl, urlSize);
		String[] picArr = StringUtils.split(picUrl, ProjectConstant.URL_SPLIT_SYMBOL);
		StringBuilder sb = new StringBuilder();
		int picArrLength = picArr.length;
		for (int i = 0; i < picArrLength - 1; i++) {
			sb.append(picArr[i]);
			sb.append(ProjectConstant.URL_SPLIT_SYMBOL);
		}
		sb.setLength(sb.length() - 1);
		sb.append(urlSize);
		sb.append(ProjectConstant.URL_SPLIT_SYMBOL);
		sb.append(picArr[picArrLength - 1]);
		logger.info("RunshopService#createPicUrl done, url is:{}", sb.toString());
		return sb.toString();
	}

	/**
	 * 更新屏蔽城市列表
	 */
	@Override
	public void updateExcludeCity(Map<Integer, String> excludedCityMap) throws ServerException, ServiceException {

		if (null == excludedCityMap) {
			throw ExceptionUtil.createServiceException(ExceptionCode.EXCLUDED_CITY_MAP_NULL);
		}
		try {
			Iterator<Entry<Integer, String>> cityIterator = excludedCityMap.entrySet().iterator();
			Integer count = 0;
			// 如果已经有数据，做insert和update
			if (0 != excludeCitiesMapper.getCityCount()) {
				logger.info("RunshopService#updateExcludeCity: there are data in excluded city db, now start to update and insert.");
				// 先将所有城市置为无效
				excludeCitiesMapper.setIsDeleteForAllCity(1);
				logger.info("RunshopService#updateExcludeCity: set all city to invalid...");

				while (cityIterator.hasNext()) {

					Map.Entry<Integer, String> cityPair = cityIterator.next();
					String city_name = cityPair.getValue();
					int city_id = cityPair.getKey();
					// 如果不存在该城市，就insert
					if (0 == excludeCitiesMapper.isCityExisted(city_id, city_name)) {
						excludeCitiesMapper.insertCityInfo(new ExcludedCityFromRunshop(city_id, city_name));

					} else {
						// 如果存在该城市，就update，把is_delete设置为0，置为有效
						excludeCitiesMapper.updateCityInfo(new ExcludedCityFromRunshop(city_id, city_name));
					}
					count++;
				}

			} else {
				logger.info("RunshopService#updateExcludeCity: there are no data in excluded city db, now start to insert.");
				// 如果没有数据，就全部insert，所有insert都为有效
				while (cityIterator.hasNext()) {
					Map.Entry<Integer, String> cityPair = cityIterator.next();
					String city_name = cityPair.getValue();
					int city_id = cityPair.getKey();
					excludeCitiesMapper.insertCityInfo(new ExcludedCityFromRunshop(city_id, city_name));
					count++;
				}

			}

			logger.info("RunshopService#updateExcludeCity: Handled " + count + " entries!!");

		} catch (Exception e) {
			logger.error("RunshopService#updateExcludeCity error", e);
			throw ExceptionUtil.createServerException(ExceptionCode.UPDATE_EXCLUDED_CITY_ERROR);

		}

	}

	/**
	 * 获取屏蔽城市列表
	 */

	@Override
	public List<ExcludedCityFromRunshop> getExcludedCities() throws ServerException, ServiceException {
		List<ExcludedCityFromRunshop> excludedCityFromRunshopsList;
		try {
			excludedCityFromRunshopsList = excludeCitiesMapper.getExcludedCities();
			if (null == excludedCityFromRunshopsList) {
				return new ArrayList<>();
			} else {
				return excludedCityFromRunshopsList;
			}
		} catch (Exception e) {
			logger.error("RunshopService#getEcludedCities: ", e);
			throw ExceptionUtil.createServerException(ExceptionCode.GET_EXCLUDED_CITY_ERROR);
		}
	}

	/**
	 * 提交开店申请接口
	 *
	 * @return 返回当前创建的开店申请id
	 */
	@Override
	public int insertRunshop(RunshopInsertForm runshopInsertForm) throws ServiceException, ServerException {
		logger.info("RunshopService#insertRunshop,runshopInsertForm:{}", runshopInsertForm);
		/* 数据校验 */
		if (null == runshopInsertForm) {
			throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_INSERT_FORM_NULL);
		}
		if (runshopInsertForm.getStore_name().contains(ProjectConstant.RUNSHOP_NAME_NOT_ALLOW)
				|| runshopInsertForm.getStore_name().contains(ProjectConstant.RUNSHOP_NAME_NOT_ALLOW_1)) {
			throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_NAME_NOT_ALLOW);
		}

		ImageEntity identityPositiveImageEntity = runshopInsertForm.getIdentity_positive_pic();
		if (StringUtils.isBlank(identityPositiveImageEntity.getUrl())
				|| StringUtils.isBlank(identityPositiveImageEntity.getHash())) {
			throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_INSERT_IDENTITY_POSITIVE_PIC_NULL);
		}

		ImageEntity identityOppositeImageEntity = runshopInsertForm.getIdentity_opposite_pic();
		// 对大陆，港澳，台湾是必填
		if ((runshopInsertForm.getCredentials_type() == RunshopServiceConstants.MAINLAND
				|| runshopInsertForm.getCredentials_type() == RunshopServiceConstants.HONGONG_AND_MACAO || runshopInsertForm
				.getCredentials_type() == RunshopServiceConstants.TAIWAN)
				&& (StringUtils.isBlank(identityOppositeImageEntity.getUrl()) || StringUtils
						.isBlank(identityOppositeImageEntity.getHash()))) {
			throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_INSERT_IDENTITY_OPPOSITE_PIC_NULL);
		}

		String business_license_time = runshopInsertForm.getBusiness_license_time();
		if (StringUtils.isNotBlank(business_license_time)) {
			if (!business_license_time.equals(ProjectConstant.BUSINESS_LICENSE_NO_EXPIRE_TIME)) {
				if (!business_license_time.matches(ProjectConstant.DATE_FORMAT_REGULAR)) {
					throw ExceptionUtil.createServiceException(ExceptionCode.INSERT_RUNSHOP_ISSUE_TIME_TYPE_ERROR);
				}
				boolean isBeforeToday;
				try {
					isBeforeToday = DateUtil.isBeforeToday(business_license_time, ProjectConstant.DATE_FORMAT);
				} catch (ParseException e) {
					logger.error("RunshopService#insertRunshop[check is before today] error", e);
					throw ExceptionUtil.createServiceException(ExceptionCode.INSERT_RUNSHOP_ISSUE_TIME_TYPE_ERROR);
				}
				if (isBeforeToday) {
					logger.error("RunshopService#insertRunshop[business license time before today], "
							+ "business license time is:" + "{}", business_license_time);
					throw ExceptionUtil
							.createServiceException(ExceptionCode.INSERT_RUNSHOP_BUSINESS_LICENSE_TIME_BEFORE_NOW_ERROR);
				}
			}
		}
		String catering_services_license_time = runshopInsertForm.getCatering_services_license_time();
		if (StringUtils.isNotBlank(catering_services_license_time)) {
			if (!catering_services_license_time.matches(ProjectConstant.DATE_FORMAT_REGULAR)) {
				throw ExceptionUtil
						.createServiceException(ExceptionCode.INSERT_RUNSHOP_CATERING_SERVICES_LICENSE_TIME_TYPE_ERROR);
			}
			boolean isBeforeToday;
			try {
				isBeforeToday = DateUtil.isBeforeToday(catering_services_license_time, ProjectConstant.DATE_FORMAT);
			} catch (ParseException e) {
				logger.error("RunshopService#insertRunshop[check is before today] error", e);
				throw ExceptionUtil.createServiceException(ExceptionCode.INSERT_RUNSHOP_ISSUE_TIME_TYPE_ERROR);
			}
			if (isBeforeToday) {
				logger.error(
						"RunshopService#insertRunshop[catering services license time], catering services license time is:{}",
						catering_services_license_time);
				throw ExceptionUtil
						.createServiceException(ExceptionCode.INSERT_RUNSHOP_CATERING_SERVICES_LICENSE_TIME_BEFORE_NOW_ERROR);
			}
		}

		/* 保存创建的开店申请数据 */
		RunshopApplicationInfo runshopApplicationInfo = new RunshopApplicationInfo();
		runshopApplicationInfo.setStore_name(runshopInsertForm.getStore_name());
		runshopApplicationInfo.setProvince_id(runshopInsertForm.getProvince_id());
		runshopApplicationInfo.setCity_id(runshopInsertForm.getCity_id());
		runshopApplicationInfo.setDistrict_id(runshopInsertForm.getDistrict_id());
		runshopApplicationInfo.setBusiness_license_type(runshopInsertForm.getBusiness_license_type());
		runshopApplicationInfo.setCatering_services_license_type(runshopInsertForm.getCatering_services_license_type());
		RegionDto city = saturnCityService.getCityById(runshopInsertForm.getCity_id());
		if (city != null) {
			runshopApplicationInfo.setCity_name(city.getName());
		}
		// runshopApplicationInfo.setCity_name(runshopInsertForm.getCity_name());
		runshopApplicationInfo.setAddress(runshopInsertForm.getAddress());
		/* 将门店分类的id转成json string */
		String store_classification_id;
		try {
			store_classification_id = JsonHelper.toJsonString(runshopInsertForm.getStore_classification_id());
		} catch (JsonProcessingException e) {
			logger.error("RunshopService#insertRunshop[toJsonString] error", e);
			throw ExceptionUtil.createServerException(ExceptionCode.RUNSHOP_INSERT_JSON_PARSE_ERROR);
		}
		runshopApplicationInfo.setStore_classification_id(store_classification_id);
		String store_classification;
		try {
			store_classification = JsonHelper.toJsonString(runshopInsertForm.getStore_classification());
		} catch (JsonProcessingException e) {
			logger.error("RunshopService#createRunshop to json string error", e);
			throw ExceptionUtil.createServerException(ExceptionCode.RUNSHOP_INSERT_JSON_PARSE_ERROR);
		}
		runshopApplicationInfo.setStore_classification(store_classification);
		runshopApplicationInfo.setLatitude(runshopInsertForm.getLatitude());
		runshopApplicationInfo.setLongitude(runshopInsertForm.getLongitude());
		runshopApplicationInfo.setMobile(runshopInsertForm.getMobile());
		runshopApplicationInfo.setThird_party_platform_url(runshopInsertForm.getThird_party_platform_url());
		runshopApplicationInfo.setDistribution_way(runshopInsertForm.getDistribution_way());
		runshopApplicationInfo.setBoss(runshopInsertForm.getBoss());
		runshopApplicationInfo.setBusiness_license_name(runshopInsertForm.getBusiness_license_name());
		runshopApplicationInfo.setIdentity_number(runshopInsertForm.getIdentity_number());
		runshopApplicationInfo.setBusiness_license_num(runshopInsertForm.getBusiness_license_num());
		runshopApplicationInfo.setBusiness_license_address(runshopInsertForm.getBusiness_license_address());
		runshopApplicationInfo.setBusiness_license_time(business_license_time);
		runshopApplicationInfo.setCatering_services_license_name(runshopInsertForm.getCatering_services_license_name());
		runshopApplicationInfo.setCatering_services_license_address(runshopInsertForm
				.getCatering_services_license_address());
		runshopApplicationInfo.setCatering_services_license_num(runshopInsertForm.getCatering_services_license_num());
		runshopApplicationInfo.setCatering_services_license_time(catering_services_license_time);
		runshopApplicationInfo.setCredentials_type(runshopInsertForm.getCredentials_type());
		runshopApplicationInfo.setCredentials_time(runshopInsertForm.getCredentials_time());
		try {
			runshopMapper.createRunshop(runshopApplicationInfo);
		} catch (Exception e) {
			logger.error("RunshopService#createRunshop error", e);
			throw ExceptionUtil.createServerException(ExceptionCode.RUNSHOP_INSERT_ERROR);
		}
		/* 保存开店申请图片信息 */
		int runshop_id = runshopApplicationInfo.getId();
		/* 需要创建的图片列表 */
		List<RunshopApplicationPicture> runshopApplicationPictureList = new ArrayList<>();
		/* 证件照正面照 */
		RunshopApplicationPicture identityPositivePic = new RunshopApplicationPicture(runshop_id,
				ProjectConstant.IDENTITY_POSITIVE_PIC_CODE, identityPositiveImageEntity.getUrl(),
				identityPositiveImageEntity.getHash());

		runshopApplicationPictureList.add(identityPositivePic);
		// 证件照反面照 大陆，港澳，台湾填写
		if (runshopInsertForm.getCredentials_type() == RunshopServiceConstants.MAINLAND
				|| runshopInsertForm.getCredentials_type() == RunshopServiceConstants.HONGONG_AND_MACAO
				|| runshopInsertForm.getCredentials_type() == RunshopServiceConstants.TAIWAN) {
			RunshopApplicationPicture identityOppositePic = new RunshopApplicationPicture(runshop_id,
					ProjectConstant.IDENTITY_OPPOSITE_PIC_CODE, identityOppositeImageEntity.getUrl(),
					identityOppositeImageEntity.getHash());
			runshopApplicationPictureList.add(identityOppositePic);
		}

		if (null != runshopInsertForm.getDoor_pics()) {
			/* 门头照 */
			for (int i = 0; i < runshopInsertForm.getDoor_pics().size(); i++) {
				ImageEntity doorPicUrl = runshopInsertForm.getDoor_pics().get(i);
				RunshopApplicationPicture doorPic = new RunshopApplicationPicture(runshop_id,
						ProjectConstant.DOOR_PIC_CODE, doorPicUrl.getUrl(), doorPicUrl.getHash());
				runshopApplicationPictureList.add(doorPic);
			}
		}
		if (null != runshopInsertForm.getStore_pics()) {
			/* 店内照 */
			for (int i = 0; i < runshopInsertForm.getStore_pics().size(); i++) {
				ImageEntity storePicUrl = runshopInsertForm.getStore_pics().get(i);
				RunshopApplicationPicture storePic = new RunshopApplicationPicture(runshop_id,
						ProjectConstant.STORE_PIC_CODE, storePicUrl.getUrl(), storePicUrl.getHash());
				runshopApplicationPictureList.add(storePic);
			}
		}
		// 校验两证

		String cityStrForCateringServices = HuskarHandle.get().getMyConfig()
				.getProperty(CITIES_CATERING_SERVICES_NOT_REQUIRED);
		String cityStrForBusinessLicense = HuskarHandle.get().getMyConfig()
				.getProperty(CITIES_BUSINESS_LICENSE_NOT_REQUIRED);
		cityStrForCateringServices = cityStrForCateringServices == null ? "" : cityStrForCateringServices;
		cityStrForBusinessLicense = cityStrForBusinessLicense == null ? "" : cityStrForBusinessLicense;
		List<String> cityIdListForBusinessLicense = Arrays.asList(cityStrForBusinessLicense.split(","));
		List<String> cityIdListForCateringServices = Arrays.asList(cityStrForCateringServices.split(","));
		logger.info("cityIdListForBusinessLicense:{},cityIdListForCateringServices:{}", cityIdListForBusinessLicense,
				cityIdListForCateringServices);
		if (!cityIdListForBusinessLicense.contains(runshopInsertForm.getCity_id() + "")
				&& !cityIdListForBusinessLicense.contains(runshopInsertForm.getProvince_id() + "")
				&& !cityIdListForBusinessLicense.contains(runshopInsertForm.getDistrict_id() + "")) {
			validateCertificateBusinessLicensePic(runshopInsertForm);
		}
		if (!cityIdListForCateringServices.contains(runshopInsertForm.getCity_id() + "")
				&& !cityIdListForCateringServices.contains(runshopInsertForm.getProvince_id() + "")
				&& !cityIdListForCateringServices.contains(runshopInsertForm.getDistrict_id() + "")) {
			validateCertificateCaterintServiceLicense(runshopInsertForm);
		}

		/* 营业执照正面 */
		ImageEntity businessLicensePicImageEntity = runshopInsertForm.getBusiness_license_pic();
		if (null != businessLicensePicImageEntity && StringUtils.isNotBlank(businessLicensePicImageEntity.getUrl())
				&& StringUtils.isNotBlank(businessLicensePicImageEntity.getHash())) {
			RunshopApplicationPicture businessLicensePic = new RunshopApplicationPicture(runshop_id,
					ProjectConstant.BUSINESS_LICENSE_PIC_CODE, businessLicensePicImageEntity.getUrl(),
					businessLicensePicImageEntity.getHash());
			runshopApplicationPictureList.add(businessLicensePic);
		}
		/* 服务许可证正面 */
		ImageEntity cateringServicePicImageEntity = runshopInsertForm.getCatering_services_pic();
		if (null != cateringServicePicImageEntity && StringUtils.isNotBlank(cateringServicePicImageEntity.getUrl())
				&& StringUtils.isNotBlank(cateringServicePicImageEntity.getHash())) {
			RunshopApplicationPicture cateringServicesPic = new RunshopApplicationPicture(runshop_id,
					ProjectConstant.CATERING_SERVICES_PIC_CODE, cateringServicePicImageEntity.getUrl(),
					cateringServicePicImageEntity.getHash());
			runshopApplicationPictureList.add(cateringServicesPic);
		}
		try {
			runshopPictureMapper.batchCreateRunshopPicture(runshopApplicationPictureList);
		} catch (Exception e) {
			logger.error("RunshopService#insertRunshop[batchCreateRunshopPicture] error", e);
			throw ExceptionUtil.createServerException(ExceptionCode.RUNSHOP_INSERT_RUNSHOP_PIC_ERROR);
		}
		insertRunshopOperateRecord(runshop_id, ProjectConstant.CREATE_RUNSHOP_RECORD_OPERATOR_ID, "",
				ProjectConstant.CREATE_RUNSHOP_RECORD_OPERATOR_NAME, ProjectConstant.CREATE_RUNSHOP_RECORD_MESSAGE,
				RunshopOperateType.CREATE.getValue());
		return runshop_id;
	}

	/**
	 * 新建开店申请 .
	 *
	 * @param runshopInsertForm
	 * @return
	 * @throws ServiceException
	 * @throws ServerException
	 */
	@Override
	public int insertRunshopH5(RunshopInsertForm runshopInsertForm) throws ServiceException, ServerException {
		logger.info("RunshopService#insertRunshopH5,runshopInsertForm:{}", runshopInsertForm);

		if (null == runshopInsertForm) {
			throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_INSERT_FORM_NULL);
		}

		// 城市name字段补充
		RegionDto city = saturnCityService.getCityById(runshopInsertForm.getCity_id());
		if (city != null) {
			runshopInsertForm.setCity_name(city.getName());
		}
		// 手机验证：
		// 1.1、该手机号已经提交过申请，且该申请正在正在审核（待审核），提示“该手机号的开店申请正在审核，我们将尽快处理！”
		// 1.2、该手机号已经提交过申请，且该申请已经通过审核，提示“该手机号码的开店申请已通过审核，请勿重复申请！”
		// 1.3、该手机号已经提交过申请，且该申请被退回（需修改），提示“该手机号的开店申请被退回，请修改后重新提交！”
		List<RunshopApplicationInfo> runshopList = runshopMapper.listRunShopByMobile(runshopInsertForm.getMobile());
		for (RunshopApplicationInfo runShop : runshopList) {
			if (runShop.getIs_delete() == 0) {
				if (runShop.getStatus() == 0) {
					throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_IS_WAITING);
				}
				if (runShop.getStatus() == 1) {
					throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_IS_PASS);
				}
				if (runShop.getStatus() == 3) {
					throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_IS_NO_PASS);
				}
			}
		}
		// 以上确定没有没有绑定的管理员，下面确定没有待申请的开店申请

		ImageEntity identityPositiveImageEntity = runshopInsertForm.getIdentity_positive_pic();
		if (StringUtils.isBlank(identityPositiveImageEntity.getUrl())
				|| StringUtils.isBlank(identityPositiveImageEntity.getHash())) {
			throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_INSERT_IDENTITY_POSITIVE_PIC_NULL);
		}
		ImageEntity identityOppositeImageEntity = runshopInsertForm.getIdentity_opposite_pic();
		// 对大陆，港澳，台湾是必填
		if ((runshopInsertForm.getCredentials_type() == RunshopServiceConstants.MAINLAND
				|| runshopInsertForm.getCredentials_type() == RunshopServiceConstants.HONGONG_AND_MACAO || runshopInsertForm
				.getCredentials_type() == RunshopServiceConstants.TAIWAN)
				&& (StringUtils.isBlank(identityOppositeImageEntity.getUrl()) || StringUtils
						.isBlank(identityOppositeImageEntity.getHash()))) {
			throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_INSERT_IDENTITY_OPPOSITE_PIC_NULL);
		}

		String business_license_time = runshopInsertForm.getBusiness_license_time();
		if (StringUtils.isNotBlank(business_license_time)) {
			if (!business_license_time.equals(ProjectConstant.BUSINESS_LICENSE_NO_EXPIRE_TIME)) {
				if (!business_license_time.matches(ProjectConstant.DATE_FORMAT_REGULAR)) {
					throw ExceptionUtil.createServiceException(ExceptionCode.INSERT_RUNSHOP_ISSUE_TIME_TYPE_ERROR);
				}
				boolean isBeforeToday;
				try {
					isBeforeToday = DateUtil.isBeforeToday(business_license_time, ProjectConstant.DATE_FORMAT);
				} catch (ParseException e) {
					logger.error("RunshopService#insertRunshop[check is before today] error", e);
					throw ExceptionUtil.createServiceException(ExceptionCode.INSERT_RUNSHOP_ISSUE_TIME_TYPE_ERROR);
				}
				if (isBeforeToday) {
					logger.error(
							"RunshopService#insertRunshop[business license time before today], business license time is:{}",
							business_license_time);
					throw ExceptionUtil
							.createServiceException(ExceptionCode.INSERT_RUNSHOP_BUSINESS_LICENSE_TIME_BEFORE_NOW_ERROR);
				}
			}
		}
		String catering_services_license_time = runshopInsertForm.getCatering_services_license_time();
		if (StringUtils.isNotBlank(catering_services_license_time)) {
			if (!catering_services_license_time.matches(ProjectConstant.DATE_FORMAT_REGULAR)) {
				throw ExceptionUtil
						.createServiceException(ExceptionCode.INSERT_RUNSHOP_CATERING_SERVICES_LICENSE_TIME_TYPE_ERROR);
			}
			boolean isBeforeToday;
			try {
				isBeforeToday = DateUtil.isBeforeToday(catering_services_license_time, ProjectConstant.DATE_FORMAT);
			} catch (ParseException e) {
				logger.error("RunshopService#insertRunshop[check is before today] error", e);
				throw ExceptionUtil.createServiceException(ExceptionCode.INSERT_RUNSHOP_ISSUE_TIME_TYPE_ERROR);
			}
			if (isBeforeToday) {
				logger.error(
						"RunshopService#insertRunshop[catering services license time], catering services license time is:{}",
						catering_services_license_time);
				throw ExceptionUtil
						.createServiceException(ExceptionCode.INSERT_RUNSHOP_CATERING_SERVICES_LICENSE_TIME_BEFORE_NOW_ERROR);
			}
		}

		/* 保存创建的开店申请数据 */
		RunshopApplicationInfo runshopApplicationInfo = new RunshopApplicationInfo();
		runshopApplicationInfo.setStore_name(runshopInsertForm.getStore_name());
		if (!Strings.isNullOrEmpty(runshopInsertForm.getExt_phone())) {
			runshopApplicationInfo.setExt_phone(runshopInsertForm.getExt_phone());
		}else {
			runshopApplicationInfo.setExt_phone("");
		}
		runshopApplicationInfo.setProvince_id(runshopInsertForm.getProvince_id());
		runshopApplicationInfo.setCity_id(runshopInsertForm.getCity_id());
		runshopApplicationInfo.setDistrict_id(runshopInsertForm.getDistrict_id());
		runshopApplicationInfo.setCity_name(runshopInsertForm.getCity_name());
		runshopApplicationInfo.setAddress(runshopInsertForm.getAddress());
		runshopApplicationInfo.setSource(ProjectConstant.RUNSHOP_FROM_MKaidian);
		/* 将门店分类的id转成json string */
		String store_classification_id;
		try {
			store_classification_id = JsonHelper.toJsonString(runshopInsertForm.getStore_classification_id());
		} catch (JsonProcessingException e) {
			logger.error("RunshopService#insertRunshop[toJsonString] error", e);
			throw ExceptionUtil.createServerException(ExceptionCode.RUNSHOP_INSERT_JSON_PARSE_ERROR);
		}
		runshopApplicationInfo.setStore_classification_id(store_classification_id);
		String store_classification;
		try {
			store_classification = JsonHelper.toJsonString(runshopInsertForm.getStore_classification());
		} catch (JsonProcessingException e) {
			logger.error("RunshopService#createRunshop to json string error", e);
			throw ExceptionUtil.createServerException(ExceptionCode.RUNSHOP_INSERT_JSON_PARSE_ERROR);
		}
		runshopApplicationInfo.setDom_source(runshopInsertForm.getDom_source());
		runshopApplicationInfo.setTag(runshopInsertForm.getTag());
		runshopApplicationInfo.setStore_classification(store_classification);
		runshopApplicationInfo.setLatitude(runshopInsertForm.getLatitude());
		runshopApplicationInfo.setLongitude(runshopInsertForm.getLongitude());
		runshopApplicationInfo.setMobile(runshopInsertForm.getMobile());
		runshopApplicationInfo.setThird_party_platform_url(runshopInsertForm.getThird_party_platform_url());
		runshopApplicationInfo.setDistribution_way(runshopInsertForm.getDistribution_way());
		runshopApplicationInfo.setBoss(runshopInsertForm.getBoss());
		runshopApplicationInfo.setBusiness_license_name(runshopInsertForm.getBusiness_license_name());
		runshopApplicationInfo.setIdentity_number(runshopInsertForm.getIdentity_number());
		runshopApplicationInfo.setBusiness_license_num(runshopInsertForm.getBusiness_license_num());
		runshopApplicationInfo.setBusiness_license_address(runshopInsertForm.getBusiness_license_address());
		runshopApplicationInfo.setBusiness_license_time(business_license_time);
		runshopApplicationInfo.setCatering_services_license_name(runshopInsertForm.getCatering_services_license_name());
		runshopApplicationInfo.setCatering_services_license_address(runshopInsertForm
				.getCatering_services_license_address());
		runshopApplicationInfo.setCatering_services_license_num(runshopInsertForm.getCatering_services_license_num());
		runshopApplicationInfo.setCatering_services_license_time(catering_services_license_time);
		runshopApplicationInfo.setCredentials_type(runshopInsertForm.getCredentials_type());
		runshopApplicationInfo.setCredentials_time(runshopInsertForm.getCredentials_time());
		runshopApplicationInfo.setBusiness_license_type(runshopInsertForm.getBusiness_license_type());
		runshopApplicationInfo.setCatering_services_license_type(runshopInsertForm.getCatering_services_license_type());

		try {
			runshopMapper.createRunshopForMKaidian(runshopApplicationInfo);
		} catch (Exception e) {
			logger.error("RunshopService#createRunshop error", e);
			throw ExceptionUtil.createServerException(ExceptionCode.RUNSHOP_INSERT_ERROR);
		}
		/* 保存开店申请图片信息 */
		int runshop_id = runshopApplicationInfo.getId();
		/* 需要创建的图片列表 */
		List<RunshopApplicationPicture> runshopApplicationPictureList = new ArrayList<>();
		/* 身份证正面照 */
		RunshopApplicationPicture identityPositivePic = new RunshopApplicationPicture(runshop_id,
				ProjectConstant.IDENTITY_POSITIVE_PIC_CODE, identityPositiveImageEntity.getUrl(),
				identityPositiveImageEntity.getHash());
		runshopApplicationPictureList.add(identityPositivePic);
		/* 身份证反面照 */
		// 证件照反面照 大陆，港澳，台湾填写
		if (runshopInsertForm.getCredentials_type() == RunshopServiceConstants.MAINLAND
				|| runshopInsertForm.getCredentials_type() == RunshopServiceConstants.HONGONG_AND_MACAO
				|| runshopInsertForm.getCredentials_type() == RunshopServiceConstants.TAIWAN) {
			RunshopApplicationPicture identityOppositePic = new RunshopApplicationPicture(runshop_id,
					ProjectConstant.IDENTITY_OPPOSITE_PIC_CODE, identityOppositeImageEntity.getUrl(),
					identityOppositeImageEntity.getHash());
			runshopApplicationPictureList.add(identityOppositePic);
		}

		if (null != runshopInsertForm.getDoor_pics()) {
			/* 门头照 */
			for (int i = 0; i < runshopInsertForm.getDoor_pics().size(); i++) {
				ImageEntity doorPicUrl = runshopInsertForm.getDoor_pics().get(i);
				RunshopApplicationPicture doorPic = new RunshopApplicationPicture(runshop_id,
						ProjectConstant.DOOR_PIC_CODE, doorPicUrl.getUrl(), doorPicUrl.getHash());
				runshopApplicationPictureList.add(doorPic);
			}
		}
		if (null != runshopInsertForm.getStore_pics()) {
			/* 店内照 */
			for (int i = 0; i < runshopInsertForm.getStore_pics().size(); i++) {
				ImageEntity storePicUrl = runshopInsertForm.getStore_pics().get(i);
				RunshopApplicationPicture storePic = new RunshopApplicationPicture(runshop_id,
						ProjectConstant.STORE_PIC_CODE, storePicUrl.getUrl(), storePicUrl.getHash());
				runshopApplicationPictureList.add(storePic);
			}
		}

		// 校验两证

		String cityStrForCateringServices = HuskarHandle.get().getMyConfig()
				.getProperty(CITIES_CATERING_SERVICES_NOT_REQUIRED);
		String cityStrForBusinessLicense = HuskarHandle.get().getMyConfig()
				.getProperty(CITIES_BUSINESS_LICENSE_NOT_REQUIRED);
		cityStrForCateringServices = cityStrForCateringServices == null ? "" : cityStrForCateringServices;
		cityStrForBusinessLicense = cityStrForBusinessLicense == null ? "" : cityStrForBusinessLicense;
		List<String> cityIdListForBusinessLicense = Arrays.asList(cityStrForBusinessLicense.split(","));
		List<String> cityIdListForCateringServices = Arrays.asList(cityStrForCateringServices.split(","));
		logger.info("cityIdListForBusinessLicense:{},cityIdListForCateringServices:{}", cityIdListForBusinessLicense,
				cityIdListForCateringServices);
		if (!cityIdListForBusinessLicense.contains(runshopInsertForm.getCity_id() + "")) {
			validateCertificateBusinessLicensePic(runshopInsertForm);
		}
		if (!cityIdListForCateringServices.contains(runshopInsertForm.getCity_id() + "")) {
			validateCertificateCaterintServiceLicense(runshopInsertForm);
		}

		/* 营业执照正面 */
		ImageEntity businessLicensePicImageEntity = runshopInsertForm.getBusiness_license_pic();
		if (null != businessLicensePicImageEntity && StringUtils.isNotBlank(businessLicensePicImageEntity.getUrl())
				&& StringUtils.isNotBlank(businessLicensePicImageEntity.getHash())) {
			RunshopApplicationPicture businessLicensePic = new RunshopApplicationPicture(runshop_id,
					ProjectConstant.BUSINESS_LICENSE_PIC_CODE, businessLicensePicImageEntity.getUrl(),
					businessLicensePicImageEntity.getHash());
			runshopApplicationPictureList.add(businessLicensePic);
		}
		/* 服务许可证正面 */
		ImageEntity cateringServicePicImageEntity = runshopInsertForm.getCatering_services_pic();
		if (null != cateringServicePicImageEntity && StringUtils.isNotBlank(cateringServicePicImageEntity.getUrl())
				&& StringUtils.isNotBlank(cateringServicePicImageEntity.getHash())) {
			RunshopApplicationPicture cateringServicesPic = new RunshopApplicationPicture(runshop_id,
					ProjectConstant.CATERING_SERVICES_PIC_CODE, cateringServicePicImageEntity.getUrl(),
					cateringServicePicImageEntity.getHash());
			runshopApplicationPictureList.add(cateringServicesPic);
		}
		try {
			runshopPictureMapper.batchCreateRunshopPicture(runshopApplicationPictureList);
		} catch (Exception e) {
			logger.error("RunshopService#insertRunshop[batchCreateRunshopPicture] error", e);
			throw ExceptionUtil.createServerException(ExceptionCode.RUNSHOP_INSERT_RUNSHOP_PIC_ERROR);
		}
		insertRunshopOperateRecord(runshop_id, ProjectConstant.CREATE_RUNSHOP_RECORD_OPERATOR_ID, "",
				ProjectConstant.CREATE_RUNSHOP_RECORD_OPERATOR_NAME, ProjectConstant.CREATE_RUNSHOP_RECORD_MESSAGE,
				RunshopOperateType.CREATE.getValue());
		return runshop_id;
	}

	/**
	 * 检查许可证 insert .
	 *
	 * @param runshopInsertForm
	 * @throws ServiceException
	 */
	private void validateCertificateCaterintServiceLicense(RunshopInsertForm runshopInsertForm) throws ServiceException {

		if (null == runshopInsertForm.getCatering_services_pic()) {
			throw ExceptionUtil.createServiceException(ExceptionCode.CATERING_SERVICES_PIC_NULL);
		}
		if (StringUtils.isBlank(runshopInsertForm.getCatering_services_license_name())) {
			throw ExceptionUtil.createServiceException(ExceptionCode.CATERING_SERVICES_NAME_NULL);
		}
		if (StringUtils.isBlank(runshopInsertForm.getCatering_services_license_address())) {
			throw ExceptionUtil.createServiceException(ExceptionCode.CATERING_SERVICES_ADDRESS_NULL);
		}
		if (StringUtils.isBlank(runshopInsertForm.getCatering_services_license_num())) {
			throw ExceptionUtil.createServiceException(ExceptionCode.CATERING_SERVICES_NUM_NULL);
		}
		if (StringUtils.isBlank(runshopInsertForm.getCatering_services_license_time())) {
			throw ExceptionUtil.createServiceException(ExceptionCode.CATERING_SERVICES_TIME_NULL);
		}

	}

	/**
	 * 检验营业执照信息 insert.
	 *
	 * @param runshopInsertForm
	 * @throws ServiceException
	 */
	private void validateCertificateBusinessLicensePic(RunshopInsertForm runshopInsertForm) throws ServiceException {
		if (null == runshopInsertForm.getBusiness_license_pic()) {
			throw ExceptionUtil.createServiceException(ExceptionCode.BUSSSINESS_LICENSE_PIC_NULL);
		}
		if (StringUtils.isBlank(runshopInsertForm.getBusiness_license_name())) {
			throw ExceptionUtil.createServiceException(ExceptionCode.BUSSSINESS_LICENSE_NAME_NULL);
		}
		if (StringUtils.isBlank(runshopInsertForm.getBusiness_license_address())) {
			throw ExceptionUtil.createServiceException(ExceptionCode.BUSSSINESS_LICENSE_ADDRESS_NULL);
		}
		if (StringUtils.isBlank(runshopInsertForm.getBusiness_license_num())) {
			throw ExceptionUtil.createServiceException(ExceptionCode.BUSSSINESS_LICENSE_NUM_NULL);
		}
		if (StringUtils.isBlank(runshopInsertForm.getBusiness_license_time())) {
			throw ExceptionUtil.createServiceException(ExceptionCode.BUSSSINESS_LICENSE_TIME_NULL);
		}
	}

	/**
	 * 检验营业执照信息 update.
	 *
	 * @param runshopUpdateForm
	 * @throws ServiceException
	 */
	private void validateCertificateBusinessLicensePic(RunshopUpdateForm runshopUpdateForm) throws ServiceException {
		if (null == runshopUpdateForm.getBusiness_license_pic()) {
			throw ExceptionUtil.createServiceException(ExceptionCode.BUSSSINESS_LICENSE_PIC_NULL);
		}
		if (StringUtils.isBlank(runshopUpdateForm.getBusiness_license_name())) {
			throw ExceptionUtil.createServiceException(ExceptionCode.BUSSSINESS_LICENSE_NAME_NULL);
		}
		if (StringUtils.isBlank(runshopUpdateForm.getBusiness_license_address())) {
			throw ExceptionUtil.createServiceException(ExceptionCode.BUSSSINESS_LICENSE_ADDRESS_NULL);
		}
		if (StringUtils.isBlank(runshopUpdateForm.getBusiness_license_num())) {
			throw ExceptionUtil.createServiceException(ExceptionCode.BUSSSINESS_LICENSE_NUM_NULL);
		}
		if (StringUtils.isBlank(runshopUpdateForm.getBusiness_license_time())) {
			throw ExceptionUtil.createServiceException(ExceptionCode.BUSSSINESS_LICENSE_TIME_NULL);
		}
	}

	/**
	 * 检验许可证信息 update.
	 *
	 * @param runshopUpdateForm
	 * @throws ServiceException
	 */
	private void validateCertificateCaterintServiceLicense(RunshopUpdateForm runshopUpdateForm) throws ServiceException {

		if (null == runshopUpdateForm.getCatering_services_pic()) {
			throw ExceptionUtil.createServiceException(ExceptionCode.CATERING_SERVICES_PIC_NULL);
		}
		if (StringUtils.isBlank(runshopUpdateForm.getCatering_services_license_name())) {
			throw ExceptionUtil.createServiceException(ExceptionCode.CATERING_SERVICES_NAME_NULL);
		}
		if (StringUtils.isBlank(runshopUpdateForm.getCatering_services_license_address())) {
			throw ExceptionUtil.createServiceException(ExceptionCode.CATERING_SERVICES_ADDRESS_NULL);
		}
		if (StringUtils.isBlank(runshopUpdateForm.getCatering_services_license_num())) {
			throw ExceptionUtil.createServiceException(ExceptionCode.CATERING_SERVICES_NUM_NULL);
		}
		if (StringUtils.isBlank(runshopUpdateForm.getCatering_services_license_time())) {
			throw ExceptionUtil.createServiceException(ExceptionCode.CATERING_SERVICES_TIME_NULL);
		}

	}

	/**
	 * 获取开店申请详细信息.
	 *
	 * @param id
	 * @return
	 * @throws ServiceException
	 * @throws ServerException
	 */
	@Override
	public RunshopDto getRunshopById(int id) throws ServiceException, ServerException {
		RunshopDto runshopDto = new RunshopDto();
		RunshopApplicationInfo runshop;
		logger.info("RunshopService#getRunshopById,id:{}", id);
		try {
			runshop = runshopMapper.getRunshopApplicationInfoById(id);
			runshop.setSource(runshop.getSource() == RunshopServiceConstants.SOURCE_MKAIDIAN ? RunshopServiceConstants.SOURCE_KAIDIAN
					: runshop.getSource());
		} catch (Exception e) {
			logger.error("RunshopService#getRunshopById[getRunshopApplicationInfoById] error", e);
			throw ExceptionUtil.createServerException(ExceptionCode.GET_RUNSHOP_BY_ID_ERROR);
		}
		if (runshop == null) {
			return null;
		}

		logger.info("RunshopService#getRunshopById,runshop:{}", runshop);
		BeanUtils.copyProperties(runshop, runshopDto);
		// 门店分类id整理
		String scId = runshop.getStore_classification_id();
		List<Integer> scIdList = new ArrayList<>();
		try {
			scIdList = JsonHelper.toJsonObject(scId, ArrayList.class, Integer.class);
		} catch (IOException e) {
			logger.info("RunshopService#getRunshopById,e:{}", e);
		}
		runshopDto.setStore_classification_id(scIdList);
		logger.info("RunshopService#getRunshopById,runshopDto:{}", runshopDto);

		/* 营业执照的发证日期 */
		/* 这里要兼容以前的情况，先取business_license_time，如果为空再去取老的issue_time */
		String business_license_time = runshop.getBusiness_license_time();
		if (StringUtils.isBlank(business_license_time)) {
			Timestamp issue_time = runshop.getIssue_time();
			if (null != issue_time) {
				business_license_time = DateFormatUtils.format(issue_time.getTime(), ProjectConstant.DATE_FORMAT);
				if (business_license_time.equals("1970-01-01")) {
					business_license_time = "";
				}
			}
		}
		runshopDto.setIssue_time(business_license_time);
		String identity_positive_pic = ""; // 证件照正面照片
		String identity_positive_pic_hash = ""; // 证件照正面照片
		String identity_opposite_pic = ""; // 证件照反面照片
		String identity_opposite_pic_hash = ""; // 证件照反面照片
		List<String> door_pics = new ArrayList<>(); // 门头照
		List<String> door_pics_hash = new ArrayList<>(); // 门头照
		List<String> store_pics = new ArrayList<>(); // 店内照
		List<String> store_pics_hash = new ArrayList<>(); // 店内照
		String business_license_pic = ""; // 营业执照照片
		String business_license_pic_hash = ""; // 营业执照照片
		String catering_services_pic = ""; // 服务执照照片
		String catering_services_pic_hash = ""; // 服务执照照片

		List<RunshopApplicationPicture> pics = runshopPictureMapper.listRunshopApplicationPictureByRunshopId(id);
		logger.info("RunshopService#getRunshopById,pics:{}", pics);
		for (RunshopApplicationPicture one : pics) {
			switch (one.getPic_code()) {
			case ProjectConstant.IDENTITY_POSITIVE_PIC_CODE:
				identity_positive_pic = one.getPic_url();
				identity_positive_pic_hash = one.getPic_hash();
				break;
			case ProjectConstant.IDENTITY_OPPOSITE_PIC_CODE:
				identity_opposite_pic = one.getPic_url();
				identity_opposite_pic_hash = one.getPic_hash();
				break;
			case ProjectConstant.DOOR_PIC_CODE:
				door_pics.add(one.getPic_url());
				door_pics_hash.add(one.getPic_hash());
				break;
			case ProjectConstant.STORE_PIC_CODE:
				store_pics.add(one.getPic_url());
				store_pics_hash.add(one.getPic_hash());
				break;
			case ProjectConstant.BUSINESS_LICENSE_PIC_CODE:
				business_license_pic = one.getPic_url();
				business_license_pic_hash = one.getPic_hash();
				break;
			case ProjectConstant.CATERING_SERVICES_PIC_CODE:
				catering_services_pic = one.getPic_url();
				catering_services_pic_hash = one.getPic_hash();
				break;
			default:
				logger.info("RunshopServicegetRunshopById pic code is not allow, runshop id is:{}, pic code is:{}", id,
						one.getPic_code());
				break;
			}
		}

		runshopDto.setBusiness_license_pic(business_license_pic);
		runshopDto.setCatering_services_pic(catering_services_pic);
		runshopDto.setDoor_pics(door_pics);
		runshopDto.setStore_pics(store_pics);
		runshopDto.setIdentity_opposite_pic(identity_opposite_pic);
		runshopDto.setIdentity_positive_pic(identity_positive_pic);
		logger.debug("RunshopController#getRunshop runshopDto is:{}", runshopDto);
		if (runshop.getSource() == RunshopServiceConstants.SOURCE_BD) {
			User user = coffeeHrClientService.getUserById(runshop.getUser_id());
			if (user != null) {
				runshopDto.setReport_name(user.getName() + "(" + user.getEmail() + ")");
				runshopDto.setReport_mobile(user.getMobile() + "");
				String bu_str = getTotalBu(user.getId());
				runshopDto.setReport_bu_total(bu_str);
			}
		} else {
			runshopDto.setReport_name("--");
			runshopDto.setReport_mobile("--");
			runshopDto.setReport_bu_total("--");
		}

		// 组装城市信息
		runshop.getDistrict_id();
		RegionDto province = saturnCityService.getProvinceById(runshop.getProvince_id());
		if (province != null) {
			runshopDto.setProvince_name(province.getName());
		}
		RegionDto city = saturnCityService.getCityById(runshop.getCity_id());
		if (city != null) {
			runshopDto.setCity_name(city.getName());
		}

		RegionDto district = saturnCityService.getCityById(runshop.getDistrict_id());
		if (district != null) {
			runshopDto.setDistrict_name(district.getName());
		}

		// reason
		String reason = runshop.getReason();

		runshopDto.setIdentity_positive_pic_hash(identity_positive_pic_hash);
		runshopDto.setIdentity_opposite_pic_hash(identity_opposite_pic_hash);
		runshopDto.setBusiness_license_pic_hash(business_license_pic_hash);
		runshopDto.setCatering_services_pic_hash(catering_services_pic_hash);
		runshopDto.setDoor_pics_hash(door_pics_hash);
		runshopDto.setStore_pics_hash(store_pics_hash);

		if (runshop.getIs_new() == 1) {
			runshopDto.setReason(runshop.getReason());
		} else {

			// 解析后的reason
			String reasonC = "";
			try {
				NoPassReason reasonObj = JsonHelper.toJsonObject(reason, NoPassReason.class);
				if (reasonObj != null) {
					if (!StringUtils.isEmpty(reasonObj.getBase_info())) {
						reasonC += reasonObj.getBase_info() + ";";
					}
					if (!StringUtils.isEmpty(reasonObj.getBusiness_license_info())) {
						reasonC += reasonObj.getBusiness_license_info() + ";";
					}
					if (!StringUtils.isEmpty(reasonObj.getCatering_services_license_info())) {
						reasonC += reasonObj.getCatering_services_license_info() + ";";
					}
					if (!StringUtils.isEmpty(reasonObj.getIdentity_info())) {
						reasonC += reasonObj.getIdentity_info() + ";";
					}
					if (!StringUtils.isEmpty(reasonObj.getOther_info())) {
						reasonC += reasonObj.getOther_info() + ";";
					}
				}
			} catch (IOException e) {
				reasonC = reason;
				logger.info("RunshopController#getRunshop,reason不是NoPassReason对象，而是一个正常的字符串，e:{}", e);
			}
			runshopDto.setReason(reasonC);

		}
		return runshopDto;
	}

	/**
	 * 获取开店申请详细信息,kaidian.
	 *
	 * @param id
	 * @return
	 * @throws ServiceException
	 * @throws ServerException
	 */
	@Override
	public RunshopDto getRunshopByIdV2(int id) throws ServiceException, ServerException {
		// RunshopDetail target = new RunshopDetail();
		RunshopDto runshopDto = new RunshopDto();
		RunshopApplicationInfo runshop;
		logger.info("RunshopService#getRunshopById,id:{}", id);
		try {
			runshop = runshopMapper.getRunshopApplicationInfoById(id);
		} catch (Exception e) {
			logger.error("RunshopService#getRunshopById[getRunshopApplicationInfoById] error", e);
			throw ExceptionUtil.createServerException(ExceptionCode.GET_RUNSHOP_BY_ID_ERROR);
		}
		if (runshop == null) {
			return null;
		}
		logger.info("RunshopService#getRunshopById,runshop:{}", runshop);
		BeanUtils.copyProperties(runshop, runshopDto);
		runshopDto.setAuditTimes(runshop.getSubmit_times());
		// 门店分类id整理
		String scId = runshop.getStore_classification_id();
		List<Integer> scIdList = new ArrayList<>();
		try {
			scIdList = JsonHelper.toJsonObject(scId, ArrayList.class, Integer.class);
		} catch (IOException e) {
			logger.info("RunshopService#getRunshopById,e:{}", e);
		}
		runshopDto.setStore_classification_id(scIdList);
		logger.info("RunshopService#getRunshopById,runshopDto:{}", runshopDto);

		/* 营业执照的发证日期 */
		/* 这里要兼容以前的情况，先取business_license_time，如果为空再去取老的issue_time */
		String business_license_time = runshop.getBusiness_license_time();
		if (StringUtils.isBlank(business_license_time)) {
			Timestamp issue_time = runshop.getIssue_time();
			if (null != issue_time) {
				business_license_time = DateFormatUtils.format(issue_time.getTime(), ProjectConstant.DATE_FORMAT);
				if (business_license_time.equals("1970-01-01")) {
					business_license_time = "";
				}
			}
		}
		runshopDto.setIssue_time(business_license_time);
		String identity_positive_pic = ""; // 身份证号正面照片
		String identity_positive_pic_hash = ""; // 身份证号正面照片
		String identity_opposite_pic = ""; // 身份证号反面照片
		String identity_opposite_pic_hash = ""; // 身份证号反面照片
		List<String> door_pics = new ArrayList<>(); // 门头照
		List<String> door_pics_hash = new ArrayList<>(); // 门头照
		List<String> store_pics = new ArrayList<>(); // 店内照
		List<String> store_pics_hash = new ArrayList<>(); // 店内照
		String business_license_pic = ""; // 营业执照照片
		String business_license_pic_hash = ""; // 营业执照照片
		String catering_services_pic = ""; // 服务执照照片
		String catering_services_pic_hash = ""; // 服务执照照片

		List<RunshopApplicationPicture> pics = runshopPictureMapper.listRunshopApplicationPictureByRunshopId(id);
		logger.info("RunshopService#getRunshopById,pics:{}", pics);
		for (RunshopApplicationPicture one : pics) {
			switch (one.getPic_code()) {
			case ProjectConstant.IDENTITY_POSITIVE_PIC_CODE:
				identity_positive_pic = RunshopServiceConstants.PRE_RUN_SHOP + one.getPic_url();
				identity_positive_pic_hash = one.getPic_hash();
				break;
			case ProjectConstant.IDENTITY_OPPOSITE_PIC_CODE:
				identity_opposite_pic = RunshopServiceConstants.PRE_RUN_SHOP + one.getPic_url();
				identity_opposite_pic_hash = one.getPic_hash();
				break;
			case ProjectConstant.DOOR_PIC_CODE:
				door_pics.add(RunshopServiceConstants.PRE_RUN_SHOP + one.getPic_url());
				door_pics_hash.add(one.getPic_hash());
				break;
			case ProjectConstant.STORE_PIC_CODE:
				store_pics.add(RunshopServiceConstants.PRE_RUN_SHOP + one.getPic_url());
				store_pics_hash.add(one.getPic_hash());
				break;
			case ProjectConstant.BUSINESS_LICENSE_PIC_CODE:
				business_license_pic = RunshopServiceConstants.PRE_RUN_SHOP + one.getPic_url();
				business_license_pic_hash = one.getPic_hash();
				break;
			case ProjectConstant.CATERING_SERVICES_PIC_CODE:
				catering_services_pic = RunshopServiceConstants.PRE_RUN_SHOP + one.getPic_url();
				catering_services_pic_hash = one.getPic_hash();
				break;
			default:
				logger.error("RunshopServicegetRunshopById pic code is not allow, runshop id is:{}, pic code is:{}",
						id, one.getPic_code());
				break;
			}
		}

		runshopDto.setBusiness_license_pic(business_license_pic);
		runshopDto.setCatering_services_pic(catering_services_pic);
		runshopDto.setDoor_pics(door_pics);
		runshopDto.setStore_pics(store_pics);
		runshopDto.setIdentity_opposite_pic(identity_opposite_pic);
		runshopDto.setIdentity_positive_pic(identity_positive_pic);

		logger.debug("RunshopController#getRunshop runshopDto is:{}", runshopDto);
		runshopDto.setIssue_time(runshop.getBusiness_license_time());
		if (runshop.getSource() == RunshopServiceConstants.SOURCE_BD) {
			User user = coffeeHrClientService.getUserById(runshop.getUser_id());
			if (user != null) {
				runshopDto.setReport_name(user.getName() + "(" + user.getEmail() + ")");
				runshopDto.setReport_mobile(user.getMobile() + "");
				String bu_str = getTotalBu(user.getId());
				runshopDto.setReport_bu_total(bu_str);
			}
		} else {
			runshopDto.setReport_name("--");
			runshopDto.setReport_mobile("--");
			runshopDto.setReport_bu_total("--");
		}

		// 组装城市信息
		RegionDto province = saturnCityService.getProvinceById(runshop.getProvince_id());
		if (province != null) {
			runshopDto.setProvince_name(province.getName());
		}

		RegionDto city = saturnCityService.getCityById(runshop.getCity_id());
		if (city != null) {
			runshopDto.setCity_name(city.getName());
		}
		RegionDto district = saturnCityService.getCityById(runshop.getDistrict_id());
		if (district != null) {
			runshopDto.setDistrict_name(district.getName());
		}

		// reason
		String reason = runshop.getReason();

		runshopDto.setIdentity_positive_pic_hash(identity_positive_pic_hash);
		runshopDto.setIdentity_opposite_pic_hash(identity_opposite_pic_hash);
		runshopDto.setBusiness_license_pic_hash(business_license_pic_hash);
		runshopDto.setCatering_services_pic_hash(catering_services_pic_hash);
		runshopDto.setDoor_pics_hash(door_pics_hash);
		runshopDto.setStore_pics_hash(store_pics_hash);

		// 解析后的reason
		String reasonC = "";
		try {
			NoPassReason reasonObj = JsonHelper.toJsonObject(reason, NoPassReason.class);
			if (reasonObj != null) {
				if (!StringUtils.isEmpty(reasonObj.getBase_info())) {
					reasonC += reasonObj.getBase_info() + ";";
				}
				if (!StringUtils.isEmpty(reasonObj.getBusiness_license_info())) {
					reasonC += reasonObj.getBusiness_license_info() + ";";
				}
				if (!StringUtils.isEmpty(reasonObj.getCatering_services_license_info())) {
					reasonC += reasonObj.getCatering_services_license_info() + ";";
				}
				if (!StringUtils.isEmpty(reasonObj.getIdentity_info())) {
					reasonC += reasonObj.getIdentity_info() + ";";
				}
				if (!StringUtils.isEmpty(reasonObj.getOther_info())) {
					reasonC += reasonObj.getOther_info() + ";";
				}
			}
		} catch (IOException e) {
			reasonC = reason;
			logger.info("RunshopController#getRunshop,reason不是NoPassReason对象，而是一个正常的字符串，e:{}", e);
		}
		runshopDto.setReason(reasonC);

		if (runshop.getDom_id() > 0) {
			runshopDto.setThirdPlatformMap(generateThirdPlatFormInfo(runshop.getDom_id()));
		}

		return runshopDto;
	}

	public Map<ThirdPlatformEnum, String> generateThirdPlatFormInfo(long domId) {
		Map<ThirdPlatformEnum, String> map = new HashMap<>();
		if (domId > 0) {
			try {
				OpenStoreDto openStoreDto = iOpenstoreService.getByDomPoiId(domId);
				if (openStoreDto != null) {
					if (!Strings.isNullOrEmpty(openStoreDto.getBaiduShopId()) && !openStoreDto.getBaiduShopId().equals("0")) {
						map.put(ThirdPlatformEnum.BAIDU, openStoreDto.getBaiduShopId());
					}
					if (!Strings.isNullOrEmpty(openStoreDto.getMeituanShopId()) && !openStoreDto.getMeituanShopId().equals("0")) {
						map.put(ThirdPlatformEnum.MEITUAN, openStoreDto.getMeituanShopId());
					}
					if (!Strings.isNullOrEmpty(openStoreDto.getDianpingShopId()) && !openStoreDto.getDianpingShopId().equals("0")) {
						map.put(ThirdPlatformEnum.DIANPING, openStoreDto.getDianpingShopId());
					}

				}
			}catch (Exception ex) {
				logger.error(String.format("[%s] runShopApplicationDetailForXY get dom store info error",
						domId), ex);
			}
		}
		return map;
	}

	/**
	 * 获取用户所在部门(详细部门路径) .
	 *
	 * @param userId
	 * @return
	 */
	private String getTotalBu(int userId) {
		List<UserBuRoleDto> userBuRoleList = coffeeHrClientService.getUserBuRole(userId);
		String bu_str = "";
		if (userBuRoleList.size() > 0) {
			int bu_id = userBuRoleList.get(0).getBu_id();
			while (bu_id > 0) {
				BusinessUnitDto bu = coffeeHrClientService.getBuById(bu_id);
				if (bu == null || bu.getParent_id() == 0) {
					break;
				}
				if ("".equals(bu_str)) {

					bu_str += bu.getName();
				} else {
					bu_str = bu.getName() + "/" + bu_str;
				}
				bu_id = bu.getParent_id();

			}
		}
		return bu_str;
	}

	/**
	 * 根据餐厅id获取runshop.
	 *
	 * @param rstId
	 * @return
	 * @throws ServiceException
	 * @throws ServerException
	 */
	@Override
	public RunshopApplicationInfo getRunshopByRstId(int rstId) throws ServiceException, ServerException {
		RunshopApplicationInfo runshop = runshopMapper.getRunshopByRstId(rstId);
		return runshop;
	}

	// @Transactional(transactionManager = "transactionManager", rollbackFor =
	// {Exception.class})
	@Override
	// @Async
	@Traceable
	public void setAuditStatus(int id, RunShopStatus status, int userId,
			Map<me.ele.work.flow.engine.core.constants.AuditProcessEnum, String> reason) throws ServiceException,
			ServerException {
		logger.info("RunshopService.setAuditStatus start with:{}, {}, {}, {}", id, status, userId, reason);

		RunshopApplicationInfo runshopApplicationInfo = validParameter(id, status, userId);

		User user = coffeeHrClientService.getUserById(userId);

		/* 更新开店申请状态信息 */
		String auditReason = generateReason(reason);
		try {
			runshopMapper.updateStatus(id, auditReason, status.getValue());
		} catch (Exception e) {
			logger.error("RunshopService#setAuditStatus[updateRunshopApplicationInfoStatusById] error", e);
			throw ExceptionUtil.createServerException(ExceptionCode.UPDATE_RUNSHOP_STATUS_ERROR);
		}
		String operateData = "";

		if (status == RunShopStatus.PASS) {

			try {
				runshopAsyncService.asyncAfterProcess(runshopApplicationInfo, userId, user, id,this);

			}catch (Exception e){
				logger.error("asyncAfterProcessFailedAt {}, applicationInfo {}",e, runshopApplicationInfo);
			}

		} else if (status == RunShopStatus.NEED_FIX) {
			runshopAsyncService.asyncSendMessage(runshopApplicationInfo, auditReason, user, id, userId, operateData);
		}
		logger.info("finish_setAuditStatus,applyId {}", id);

	}

	public void assignBD(RunshopApplicationInfo runshopApplicationInfo, int rstId, int id) {

		if (runshopApplicationInfo.getSource() == RunshopServiceConstants.SOURCE_BD
				&& runshopApplicationInfo.getHead_id() == 0) {

			logger.info("RunshopService.setAuditStatus() BD报备的开店申请，调用分配接口。");

			ThreadPoolHolder.getInstance().execute(
					new DistributeThread(rstId, runshopApplicationInfo.getUser_id(), 1, this, runshopApplicationInfo));
		} else if (runshopApplicationInfo.getHead_id() != 0
				&& runshopApplicationInfo.getSource() == RunshopServiceConstants.SOURCE_BD) {
			ThreadPoolHolder.getInstance().execute(
					new DistributeThread(rstId, runshopApplicationInfo.getUser_id(), 2, this, runshopApplicationInfo));
		} else {
			ThreadPoolHolder.getInstance().execute(
					new DistributeThread(rstId, runshopApplicationInfo.getUser_id(), 0, this, runshopApplicationInfo));
		}

		try {
			// 如果是报备添加eve消息推送给市场经理
			if (runshopApplicationInfo.getSource() == RunshopServiceConstants.SOURCE_BD) {

				EveMessageForm messageForm = new EveMessageForm();
				messageForm.setTitle("您报备的门店已通过审核！");
				String content = String.format(ProjectConstant.EVE_RUNSHOP_QUALIFIED_MESSAGE_TMP,
						runshopApplicationInfo.getStore_name(), runshopApplicationInfo.getAddress(),
						runshopApplicationInfo.getMobile());
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
		} catch (Exception e1) {
			logger.error("RunshopService#setAuditStatus error", e1);
		}
	}

	// 设置开店申请分配状态为分配失败
	private void setDistributeFalse(int runshopId) {
		RunshopOperateRecordForm runshopOperateRecordForm = new RunshopOperateRecordForm();
		runshopOperateRecordForm.setCoffeeUserName("系统");
		runshopOperateRecordForm.setCoffeeUserId(0);
		runshopOperateRecordForm.setCoffeeUserEmail("");
		runshopOperateRecordForm.setOperateData("分配市场经理失败");
		runshopOperateRecordForm.setRunshopId(runshopId);
		runshopOperateRecordForm.setOperateType(4);
		try {
			createRunshopOperateRecord(runshopOperateRecordForm);
		} catch (ServiceException | ServerException e) {
			logger.error("RunshopService#setDistributeFalse#createRunshopOperateRecord error, runshopId : " + runshopId
					+ ", e: ", e);
		}
	}

	/**
	 * 设置门店的分配状态
	 *
	 * @param runshopId
	 * @param is_distribution
	 */
	public void setNotDistribution(int runshopId, int is_distribution) {
		runshopMapper.setIsDistribution(runshopId, is_distribution);
	}

	private RunshopApplicationInfo validParameter(int id, RunShopStatus status, int userId) throws ServiceException,
			ServerException {

		if (0 == id) {
			throw ExceptionUtil.createServiceException(ExceptionCode.GET_RUNSHOP_ID_NULL);
		}
		// if (CollectionUtils.isEmpty(reason)) {
		// throw。
		// ExceptionUtil.createServiceException(ExceptionCode.CHECK_FAIL_RUNSHOP_REASON_IS_NULL);
		// }

		/* 获取开店申请信息 */
		RunshopApplicationInfo runshopApplicationInfo;
		try {
			runshopApplicationInfo = runshopMapper.getRunshopApplicationInfoById(id);
			if (runshopApplicationInfo.getRst_id() != 0) {
				throw ExceptionUtil.createServerException(ExceptionCode.RUNSHOP_ALREADY_AUDITED);
			}
		} catch (Exception e) {
			logger.error("RunshopService#setAuditStatus[getRunshopApplicationInfoById] error", e);
			throw ExceptionUtil.createServerException(ExceptionCode.GET_RUNSHOP_BY_ID_ERROR);
		}
		if (null == runshopApplicationInfo || 0 == runshopApplicationInfo.getId()) {
			logger.warn("RunshopService#setAuditStatus[getRunshopApplicationInfoById] runshop id null");
			throw ExceptionUtil.createServiceException(ExceptionCode.GET_RUNSHOP_BY_ID_NULL);
		}
		if (0 != runshopApplicationInfo.getStatus()) {
			logger.info("RunshopService#setAuditStatus runshop status not allow, status is:{}",
					runshopApplicationInfo.getStatus());
			throw ExceptionUtil.createServiceException(ExceptionCode.UNQUALIFIED_RUNSHOP_STATUS_ERROR);
		}

		return runshopApplicationInfo;
	}

	private String generateReason(Map<me.ele.work.flow.engine.core.constants.AuditProcessEnum, String> reason) {
		Map<Integer, String> map = new HashMap<>();
		reason.entrySet().forEach(e -> map.put(e.getKey().getType(), e.getValue()));
		try {
			return JsonHelper.toJsonString(map);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}
		return "审核原因转换失败";
	}

	/**
	 * 开店申请不合格 .
	 *
	 * @param reason
	 *            不合格原因
	 * @param id
	 *            开店申请主键
	 * @param userId
	 *            操作的用户id
	 * @return
	 */
	@Override
	public void unqualifiedRunshop(int id, String reason, int userId) throws ServiceException, ServerException {
		logger.debug("RunshopService#unqualifiedRunshop, id is:{}, reason is:{}, user id is:{}", id, reason, userId);
		User user = coffeeHrClientService.getUserById(userId);
		if (0 == id) {
			throw ExceptionUtil.createServiceException(ExceptionCode.GET_RUNSHOP_ID_NULL);
		}
		if (StringUtils.isBlank(reason)) {
			throw ExceptionUtil.createServiceException(ExceptionCode.CHECK_FAIL_RUNSHOP_REASON_IS_NULL);
		}
		/* 验证输入参数是否有特殊符号表情 */
		if (EmojiFilterUtils.isExistEmoji(reason)) {
			throw ExceptionUtil.createServiceException(ExceptionCode.EMOJI_SYMBOL_ERROR);
		}
		/* 获取开店申请信息 */
		RunshopApplicationInfo runshopApplicationInfo;
		try {
			runshopApplicationInfo = runshopMapper.getRunshopApplicationInfoById(id);
		} catch (Exception e) {
			logger.error("RunshopService#qualifiedRunshop[getRunshopApplicationInfoById] error", e);
			throw ExceptionUtil.createServerException(ExceptionCode.GET_RUNSHOP_BY_ID_ERROR);
		}
		if (null == runshopApplicationInfo || 0 == runshopApplicationInfo.getId()) {
			logger.warn("RunshopService#qualifiedRunshop[getRunshopApplicationInfoById] runshop id null");
			throw ExceptionUtil.createServiceException(ExceptionCode.GET_RUNSHOP_BY_ID_NULL);
		}
		if (0 != runshopApplicationInfo.getStatus()) {
			logger.info("RunshopService#unqualifiedRunshop runshop status not allow, status is:{}",
					runshopApplicationInfo.getStatus());
			throw ExceptionUtil.createServiceException(ExceptionCode.UNQUALIFIED_RUNSHOP_STATUS_ERROR);
		}
		/* 更新开店申请状态信息 */
		try {
			runshopMapper.checkFailRunshopApplication(id, reason);
		} catch (Exception e) {
			logger.error("RunshopService#qualifiedRunshop[updateRunshopApplicationInfoStatusById] error", e);
			throw ExceptionUtil.createServerException(ExceptionCode.UPDATE_RUNSHOP_STATUS_ERROR);
		}
		String operateData;
		try {
			hermesClient.runshopUnqualifiedMessage(runshopApplicationInfo.getMobile(),
					reason.replaceAll("\\n", "\\\\n"));
			operateData = String.format(ProjectConstant.UNQUALIFIED_RECORD_MESSAGE, reason,
					ProjectConstant.SEND_MESSAGE_SUCCESS);
		} catch (Exception e) {
			logger.error("RunshopService#unqualifiedRunshop error", e);
			operateData = String.format(ProjectConstant.UNQUALIFIED_RECORD_MESSAGE, reason,
					ProjectConstant.SEND_MESSAGE_FAIL);
		}
		insertRunshopOperateRecord(id, userId, user.getEmail(), user.getName(), operateData,
				RunshopOperateType.UNQUALIFIEDRUNSHOP.getValue());
		logger.info("finish_unqualifiedRunshop");

		// 如果是报备添加eve消息推送给市场经理
		try {
			if (runshopApplicationInfo.getSource() == 2) {
				EveMessageForm messageForm = new EveMessageForm();
				messageForm.setTitle("您报备的门店未通过审核！");
				String content = String.format(ProjectConstant.EVE_RUNSHOP_UNQUALIFIED_MESSAGE_TMP,
						runshopApplicationInfo.getStore_name(), runshopApplicationInfo.getAddress(),
						runshopApplicationInfo.getMobile(), reason);
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
			logger.error("RunshopService#unqualifiedRunshop,eve pushMessage error", e);
		}
	}

	/**
	 * 开店申请审核通过.
	 *
	 * @param id
	 * @param userId
	 * @return
	 * @throws ServiceException
	 * @throws ServerException
	 * @throws UnsupportedEncodingException
	 * @throws NoSuchAlgorithmException
	 */
	@Override
	public int qualifiedRunshop(int id, int userId) throws ServiceException, ServerException, NoSuchAlgorithmException,
			UnsupportedEncodingException {

		logger.info("RunshopService.qualifiedRunshop start with is:{}, userId:{}", id, userId);

		if (id <= 0) {
			throw ExceptionUtil.createServiceException(ExceptionCode.GET_RUNSHOP_ID_NULL);
		}
		if (userId <= 0) {
			throw ExceptionUtil.createServiceException(ExceptionCode.QUALIFIED_USER_ID_NULL);
		}
		/* 查询操作人信息 */
		User user = null;
		try {
			user = coffeeHrClientService.getUserById(userId);
			if (null == user || 0 == user.getId()) {
				throw ExceptionUtil.createServiceException(ExceptionCode.QUALIFIED_USER_NULL);
			}
		} catch (Exception e) {
			throw ExceptionUtil.createServiceException(ExceptionCode.QUALIFIED_USER_NULL);
		}

		/* 根据对应id的开店申请信息 */
		RunshopApplicationInfo runshopApplicationInfo = getRunshopApplicationInfoForQualified(id);
		// dentityCardService.auditIdCards(Arrays.asList(runshopApplicationInfo.getIdentity_number()),
		// auditStatus,
		// auditRemarks, operatorId, operatorType);

		logger.info("RunshopService.qualifiedRunshop get runshopApplicationInfo:{}", runshopApplicationInfo);

		/* 更新开店申请状态信息 */
		try {
			runshopMapper.updateRunshopApplicationInfoStatusById(id, ProjectConstant.QUALIFIED);
		} catch (Exception e) {
			logger.error("RunshopService#qualifiedRunshop[updateRunshopApplicationInfoStatusById] error", e);
			throw ExceptionUtil.createServerException(ExceptionCode.UPDATE_RUNSHOP_STATUS_ERROR);
		}

		/* 提交开店申请负责人手机号 */
		String mobile = runshopApplicationInfo.getMobile();

		/* 手机号是否被使用的校验 */
		Keeper keeperUser = null;
		try {
			keeperUser = keeperForNaposClientService.getKeeperUserByMobile(mobile);
		} catch (Exception e) {
			logger.error("Runshop#qualifiedRunshop[getKeeperUserByMobile] keeper", e);
		}

		/*
		 * 标记绑定管理员相关操作是否成功，true成功，false未成功，默认为true--成功
		 * 这里配合商户提交开店申请，商户提交的时候去调用keeper的用户校验
		 * 这里如果发现手机号已经在keeper那边使用过了，那么任然建店，让BD去解决这个问题
		 */
		boolean b = true;
		if (null != keeperUser) {
			logger.warn(
					"Runshop#qualifiedRunshop[getKeeperUserByMobile] user is already exist, runshop name is:{}, runshop mobile is:{}",
					runshopApplicationInfo.getStore_name(), mobile);
			b = false;
		}

		/* 生成短域名 */
		String shortUrl = getShortUrl(runshopApplicationInfo.getStore_name(), runshopApplicationInfo.getAddress(),
				mobile);

		RestaurantCreation restaurantCreation = new RestaurantCreation();
		restaurantCreation.setAddressText(runshopApplicationInfo.getAddress());
		restaurantCreation.setName(runshopApplicationInfo.getStore_name());
		restaurantCreation.setPhone(mobile);
		restaurantCreation.setMobile(mobile);
		restaurantCreation.setLatitude(runshopApplicationInfo.getLatitude().doubleValue());
		restaurantCreation.setLongitude(runshopApplicationInfo.getLongitude().doubleValue());
		restaurantCreation.setNameForUrl(shortUrl);
		restaurantCreation.setCityId((short) runshopApplicationInfo.getCity_id());
		restaurantCreation.setWirelessPrinterEsn("");
		restaurantCreation.setIsValid(ProjectConstant.NEW_RESTAURANT_IS_VALID);
		restaurantCreation.setBusyLevel(ProjectConstant.NEW_RESTAURANT_BUSY_LEVEL);
		restaurantCreation.setOnlinePayment((short) 1);
		restaurantCreation.setOrderMode(ProjectConstant.NEW_RESTAURANT_ORDER_MODE);
		int rst_id = naposRestaurantClientService.createRestaurantWithCreatorId(restaurantCreation, user.getWalle_id(),
				1);

		// 支付方式和结算方式用服务包接口代替
		boolean signFreeContractResult = packClientService.signFreeContract(rst_id);

		if (signFreeContractResult) {
			insertRunshopOperateRecord(id, userId, user.getEmail(), user.getName(),
					ProjectConstant.SERVICE_PACKAGE_SUCESS, RunshopOperateType.SERVICE_PACKAGE_SUCCESS.getValue());
		} else {
			insertRunshopOperateRecord(id, userId, user.getEmail(), user.getName(),
					ProjectConstant.SERVICE_PACKAGE_FAIL, RunshopOperateType.SERVICE_PACKAGE_FAIL.getValue());
		}

		// 开通余额账户
		financeAccountService.create(rst_id, runshopApplicationInfo.getStore_name());
		/* 更新已经建店成功的餐厅状态 */
		try {
			runshopMapper.updateRunshopRstIdById(id, rst_id);
		} catch (Exception e) {

			logger.error("RunshopService#qualifiedRunshop[updateRunshopRstIdById] error", e);
		}
		/* 开店后续操作 */
		ThreadPoolHolder.getInstance().execute(
				new OpenStoreThread(id, rst_id, runshopApplicationInfo.getUser_id(), userId, this,
						elemeRestaurantClientService, naposRestaurantClientService, photoService, hermesClient,
						messageService, fussCdnService, fussWalleService, runshopApplicationInfo, runshopPictureMapper,
						runshopOperateRecordMapper, runshopMapper, mobile, b, user, logoPath,
						keeperForNaposClientService, lShopCertificationService));

		logger.info("end RunshopService.qualifiedRunshop() return rst_id:{}", rst_id);
		return rst_id;
	}

	/**
	 * 获取开店申请详情.
	 *
	 * @param id
	 * @return
	 * @throws ServerException
	 * @throws ServiceException
	 */
	private RunshopApplicationInfo getRunshopApplicationInfoForQualified(int id) throws ServerException,
			ServiceException {
		/* 获取开店申请信息 */
		RunshopApplicationInfo runshopApplicationInfo;
		try {
			runshopApplicationInfo = runshopMapper.getRunshopApplicationInfoById(id);
		} catch (Exception e) {
			logger.error("RunshopService#qualifiedRunshop[getRunshopApplicationInfoById] error", e);
			throw ExceptionUtil.createServerException(ExceptionCode.GET_RUNSHOP_BY_ID_ERROR);
		}
		if (null == runshopApplicationInfo || 0 == runshopApplicationInfo.getId()) {
			logger.warn(
					"RunshopService#qualifiedRunshop[getRunshopApplicationInfoById] runshop id null, runshop id is:{}",
					id);
			throw ExceptionUtil.createServiceException(ExceptionCode.GET_RUNSHOP_BY_ID_NULL);
		}
		if (ProjectConstant.PENDING_AUDIT != runshopApplicationInfo.getStatus()) {
			logger.warn(
					"RunshopService#qualifiedRunshop[getRunshopApplicationInfoById] runshop already audited, runshop id is:{}",
					id);
			throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_ALREADY_AUDITED);
		}
		return runshopApplicationInfo;
	}

	/**
	 * 生成随机短域名
	 *
	 * @param name
	 * @param address
	 * @return
	 * @throws ERSUnknownException
	 * @throws ERSUserException
	 * @throws ERSSystemException
	 */
	private String getShortUrl(String name, String address, String mobile) throws ERSUnknownException,
			ERSUserException, ERSSystemException {
		double random = Math.random();
		String pinyin = PinyinUtil.toPinYinFull(name) + "/" + PinyinUtil.toPinYinFull(address) + mobile + random;
		String[] urlArr = ShortUrl.shortUrl(pinyin);
		String url = null;
		for (int i = 0; i < ProjectConstant.SHORT_URL_RETRY_TIMES; i++) {
			TRestaurant restaurant = null;
			try {
				logger.debug("RunshopService#getShortUrl, current url is:{}", urlArr[i].toLowerCase());
				restaurant = elemeRestaurantClientService.get_by_name_for_url(urlArr[i].toLowerCase());
			} catch (Exception e) {
				logger.error("RunshopService#getShortUrl[get_by_name_for_url] error", e);
			}
			logger.debug("RunshopService#getShortUrl restaurant is:{}", restaurant);
			if (null == restaurant) {
				url = urlArr[i];
				break;
			}

		}
		logger.debug("RunshopService#getShortUrl url is:{}", url);
		return url;
	}

	@Override
	public RunshopApplicationInfo getRunshopByMobile(String mobile) throws ServiceException, ServerException {
		if (StringUtils.isBlank(mobile)) {
			throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_INSERT_MOBILE_NULL);
		}
		if (!mobile.matches(ProjectConstant.MOBILE_REG)) {
			throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_INSERT_MOBILE_TYPE_ERROR);
		}
		RunshopApplicationInfo result;
		try {
			result = runshopMapper.getActRunshopByMobile(mobile);
		} catch (Exception e) {
			logger.error("RunshopService#getRunshopByMobile error", e);
			throw ExceptionUtil.createServerException(ExceptionCode.GET_RUNSHOP_BY_ID_ERROR);
		}
		return result;
	}

	@Override
	public List<RunshopOperateRecord> listOperateRecord(int runshopId) throws ServiceException, ServerException {
		if (0 == runshopId) {
			throw ExceptionUtil.createServiceException(ExceptionCode.GET_RUNSHOP_ID_NULL);
		}
		List<RunshopOperateRecord> result = new ArrayList<>();
		try {
			result = runshopOperateRecordMapper.listRunshopOperateRecordByRunshopId(runshopId);
		} catch (Exception e) {
			logger.error("RunshopService#listOperateRecord error", e);
			throw ExceptionUtil.createServerException(ExceptionCode.LIST_RUNSHOP_OPERATE_RECORD_ERROR);
		}
		return result;
	}

	/**
	 * 创建开店申请操作日志
	 *
	 * @param runshopOperateRecordForm
	 * @return
	 * @throws ServiceException
	 * @throws ServerException
	 */
	@Override
	public int createRunshopOperateRecord(RunshopOperateRecordForm runshopOperateRecordForm) throws ServiceException,
			ServerException {
		if (null == runshopOperateRecordForm) {
			throw ExceptionUtil.createServiceException(ExceptionCode.CREATE_OPERATE_RECORD_NULL);
		}
		if (0 == runshopOperateRecordForm.getRunshopId()) {
			throw ExceptionUtil.createServiceException(ExceptionCode.CREATE_OPERATE_RECORD_RUNSHOP_ID_NULL);
		}
		if (null == runshopOperateRecordForm.getCoffeeUserId()) {
			throw ExceptionUtil.createServiceException(ExceptionCode.CREATE_OPERATE_RECORD_COFFEE_USER_ID_NULL);
		}
		if (0 != runshopOperateRecordForm.getCoffeeUserId()) {
			if (StringUtils.isBlank(runshopOperateRecordForm.getCoffeeUserEmail())) {
				throw ExceptionUtil.createServiceException(ExceptionCode.CREATE_OPERATE_RECORD_COFFEE_USER_EMAIL_NULL);
			}
		}
		if (StringUtils.isBlank(runshopOperateRecordForm.getCoffeeUserName())) {
			throw ExceptionUtil.createServiceException(ExceptionCode.CREATE_OPERATE_RECORD_COFFEE_USER_NAME_NULL);
		}
		if (StringUtils.isBlank(runshopOperateRecordForm.getOperateData())) {
			throw ExceptionUtil.createServiceException(ExceptionCode.CREATE_OPERATE_RECORD_OPERATE_DATA_NULL);
		}
		if (null == runshopOperateRecordForm.getOperateType()) {
			throw ExceptionUtil.createServerException(ExceptionCode.CREATE_OPERATE_RECORD_OPERATE_TYPE_NULL);
		}
		switch (runshopOperateRecordForm.getOperateType()) {
		case 0:
		case 1:
		case 2:
		case 3:
		case 4:
			break;
		default:
			throw ExceptionUtil.createServerException(ExceptionCode.CREATE_OPERATE_RECORD_OPERATE_TYPE_ERROR);
		}
		int result = insertRunshopOperateRecord(runshopOperateRecordForm.getRunshopId(),
				runshopOperateRecordForm.getCoffeeUserId(), runshopOperateRecordForm.getCoffeeUserEmail(),
				runshopOperateRecordForm.getCoffeeUserName(), runshopOperateRecordForm.getOperateData(),
				runshopOperateRecordForm.getOperateType());
		return result;
	}

	/**
	 * 获取开店申请信息列表接口
	 *
	 * @param status
	 * @param page
	 * @return
	 * @throws ServiceException
	 * @throws ServerException
	 */
	@Override
	public PaginationResponse<RunshopApplicationInfo> listRunshopByStatus(RunShopStatus status, int userId, int page)
			throws ServerException, ServiceException {
		if (status == null) {
			throw ExceptionUtil.createServerException(ExceptionCode.LIST_RUNSHOP_BY_STATUS_ERROR);
		}

		int total;
		try {
			total = runshopMapper.countRunshopByStatus(status.getValue(), userId);
		} catch (Exception e) {
			logger.error("RunshopService#countRunshopByStatus error", e);
			throw ExceptionUtil.createServerException(ExceptionCode.COUNT_RUNSHOP_BY_STATUS_ERROR);
		}

		if (total == 0) {
			PaginationResponse<RunshopApplicationInfo> result = new PaginationResponse<>();
			result.setList(new ArrayList<RunshopApplicationInfo>());
			result.setTotal(total);
			return result;
		}

		if (page < 1) {
			page = 1;
		}
		int maxPage = (int) Math.ceil((double) total / ProjectConstant.COUNT_PER_PAGE);
		if (page > maxPage) {
			page = maxPage;
		}

		List<RunshopApplicationInfo> list;
		try {
			int offset = ProjectConstant.COUNT_PER_PAGE * (page - 1);
			list = runshopMapper.listRunshopByStatus(status.getValue(), userId, offset, ProjectConstant.COUNT_PER_PAGE);
		} catch (Exception e) {
			logger.error("RunshopService#listRunshopByStatus error", e);
			throw ExceptionUtil.createServerException(ExceptionCode.LIST_RUNSHOP_BY_STATUS_ERROR);
		}

		PaginationResponse<RunshopApplicationInfo> result = new PaginationResponse<>();
		result.setList(list);
		result.setTotal(total);

		return result;
	}

	@Override
	public PaginationResponse<RunshopApplicationInfo> listRunshopByStatusAndUserId(int status, int userId, int offset,
			int limit) throws ServerException, ServiceException {

		int total;
		try {
			total = runshopMapper.countRunshopByStatusAndIsNew(status, userId, 1);
		} catch (Exception e) {
			logger.error("RunshopService#listRunshopByStatusAndUserId error", e);
			throw ExceptionUtil.createServerException(ExceptionCode.COUNT_RUNSHOP_BY_STATUS_ERROR);
		}

		if (total == 0) {
			PaginationResponse<RunshopApplicationInfo> result = new PaginationResponse<>();
			result.setList(new ArrayList<>());
			result.setTotal(total);
			return result;
		}

		List<RunshopApplicationInfo> list;
		try {
			list = runshopMapper.listRunshopByStatusAndIsNew(status, userId, 1, offset, limit);
		} catch (Exception e) {
			logger.error("RunshopService#listRunshopByStatus error", e);
			throw ExceptionUtil.createServerException(ExceptionCode.LIST_RUNSHOP_BY_STATUS_ERROR);
		}

		PaginationResponse<RunshopApplicationInfo> result = new PaginationResponse<>();
		result.setList(list);
		result.setTotal(total);

		return result;
	}

	/**
	 * 获取开店申请信息列表接口
	 *
	 * @param status
	 * @param
	 * @param page
	 * @return
	 * @throws ServiceException
	 * @throws ServerException
	 */
	@Override
	public PaginationResponse<RunshopApplicationDto> listRunshopByStatusV2(RunShopStatus status, String mobile,
			Integer page, Integer pageSize) throws ServerException, ServiceException {
		logger.info("enter listRunshopByStatusV2");
		Integer statusValue = null;
		if (page == null)
			page = 0;
		if (pageSize == null)
			pageSize = 10000;
		// if (status == null) {
		// throw
		// ExceptionUtil.createServerException(ExceptionCode.LIST_RUNSHOP_BY_STATUS_ERROR);
		// }
		if (status != null)
			statusValue = status.getValue();

		int total;
		try {
			total = runshopMapper.countRunshopByStatusV2(statusValue, mobile);
		} catch (Exception e) {
			logger.error("RunshopService#countRunshopByStatus error", e);
			throw ExceptionUtil.createServerException(ExceptionCode.COUNT_RUNSHOP_BY_STATUS_ERROR);
		}

		if (total == 0) {
			PaginationResponse<RunshopApplicationDto> result = new PaginationResponse<>();
			result.setList(new ArrayList<RunshopApplicationDto>());
			result.setTotal(total);
			return result;
		}

		if (page < 1) {
			page = 1;
		}
		int maxPage = (int) Math.ceil((double) total / pageSize);
		if (page > maxPage) {
			page = maxPage;
		}

		List<RunshopApplicationDto> list;
		try {
			int offset = pageSize * (page - 1);
			list = runshopMapper.listApplicationByStatus(statusValue, mobile, offset, pageSize);

		} catch (Exception e) {
			logger.error("RunshopService#listRunshopByStatus error", e);
			throw ExceptionUtil.createServerException(ExceptionCode.LIST_RUNSHOP_BY_STATUS_ERROR);
		}

		PaginationResponse<RunshopApplicationDto> result = new PaginationResponse<>();
		result.setList(list);
		result.setTotal(total);
		logger.info("exit listRunshopByStatusV2");
		return result;
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

	/**
	 * 设置门店的分配状态
	 *
	 * @param rstId
	 * @param is_distribution
	 */
	@Override
	public void setIsDistribution(int rstId, int is_distribution) throws ServerException, ServiceException {
		logger.info("RunshopService#setIsDistribution,rstId:{},is_distribution:{}", rstId, is_distribution);
		runshopMapper.setIsDistribution(rstId, is_distribution);
	}

	/**
	 * 开店报备提交接口
	 *
	 * @param runshopInsertEveForm
	 * @return
	 * @throws ServiceException
	 * @throws ServerException
	 */
	@Override
	public int insertRunshopForEve(RunshopInsertEveForm runshopInsertEveForm) throws ServiceException, ServerException {
		logger.info("RunshopService#insertRunshopForEve into, form is {}", runshopInsertEveForm);
		if (runshopInsertEveForm.getGaode_address() == null) {
			runshopInsertEveForm.setGaode_address("");
		}
		if (runshopInsertEveForm.getBackup() == null) {
			runshopInsertEveForm.setBackup("");
		}

		if (1 == runshopInsertEveForm.getBusiness_license_time_no_limit()) {
			runshopInsertEveForm.setBusiness_license_time(ProjectConstant.BUSINESS_LICENSE_NO_EXPIRE_TIME);
		}
		checkInsertUpdateParams(runshopInsertEveForm);

		String business_license_time = runshopInsertEveForm.getBusiness_license_time();
		if (StringUtils.isNotBlank(business_license_time)) {
			if (!business_license_time.equals(ProjectConstant.BUSINESS_LICENSE_NO_EXPIRE_TIME)) {
				if (!business_license_time.matches(ProjectConstant.DATE_FORMAT_REGULAR_CH)) {
					throw ExceptionUtil.createServiceException(ExceptionCode.INSERT_RUNSHOP_ISSUE_TIME_TYPE_ERROR);
				}
				boolean isBeforeToday;
				try {
					isBeforeToday = DateUtil.isBeforeToday(business_license_time, ProjectConstant.DATE_FORMAT_EVE);
				} catch (Exception e) {
					logger.error("RunshopService#insertRunshopForEve[check is before today] error", e);
					throw ExceptionUtil.createServiceException(ExceptionCode.INSERT_RUNSHOP_ISSUE_TIME_TYPE_ERROR);
				}
				if (isBeforeToday) {
					logger.error(
							"RunshopService#insertRunshopForEve[business license time before today], business license time is:{}",
							business_license_time);
					throw ExceptionUtil
							.createServiceException(ExceptionCode.INSERT_RUNSHOP_BUSINESS_LICENSE_TIME_BEFORE_NOW_ERROR_FOR_EVE);
				}
				try {
					Date date = DateUtils.parseDate(business_license_time, ProjectConstant.DATE_FORMAT_EVE);
					String dateString = DateFormatUtils.format(date, ProjectConstant.DATE_FORMAT);
					runshopInsertEveForm.setBusiness_license_time(dateString);
					business_license_time = dateString;
				} catch (Exception e) {
					logger.error(
							"RunshopService#insertRunshopForEve[parse business license time to DATE_FORMAT] error", e);
					throw ExceptionUtil.createServiceException(ExceptionCode.INSERT_RUNSHOP_ISSUE_TIME_TYPE_ERROR);
				}
			}
		}
		String catering_services_license_time = runshopInsertEveForm.getCatering_services_license_time();
		if (StringUtils.isNotBlank(catering_services_license_time)) {
			if (!catering_services_license_time.matches(ProjectConstant.DATE_FORMAT_REGULAR_CH)) {
				throw ExceptionUtil
						.createServiceException(ExceptionCode.INSERT_RUNSHOP_CATERING_SERVICES_LICENSE_TIME_TYPE_ERROR);
			}
			boolean isBeforeToday;
			try {
				isBeforeToday = DateUtil.isBeforeToday(catering_services_license_time, ProjectConstant.DATE_FORMAT_EVE);
			} catch (Exception e) {
				logger.error(
						"RunshopService#insertRunshopForEve[catering services license time], catering services license time is:{}",
						catering_services_license_time);
				throw ExceptionUtil
						.createServiceException(ExceptionCode.INSERT_RUNSHOP_CATERING_SERVICES_LICENSE_TIME_BEFORE_NOW_ERROR);
			}
			if (isBeforeToday) {
				logger.error("RunshopService#insertRunshopForEve[catering services license time before today]");
				throw ExceptionUtil
						.createServiceException(ExceptionCode.CREATE_RUNSHOP_CATERING_SERVICES_LICENSE_TIME_BEFORE_NOW_ERROR);
			}
			try {
				Date date = DateUtils.parseDate(catering_services_license_time, ProjectConstant.DATE_FORMAT_EVE);
				String dateString = DateFormatUtils.format(date, ProjectConstant.DATE_FORMAT);
				runshopInsertEveForm.setCatering_services_license_time(dateString);
			} catch (Exception e) {
				logger.error("RunshopService#insertRunshopForEve[parse catering license time to DATE_FORMAT] error", e);
				throw ExceptionUtil.createServiceException(ExceptionCode.INSERT_RUNSHOP_ISSUE_TIME_TYPE_ERROR);
			}
		}
		/* 开店申请runshop重复校验 */
		String mobile = runshopInsertEveForm.getMobile();
		RunshopApplicationInfo existRunshop;
		try {
			existRunshop = runshopMapper.getActRunshopByMobileForEve(mobile);
			logger.info("RunshopService#insertRunshopForEve existRunshop is {}", existRunshop);
		} catch (Exception e) {
			logger.error("RunshopService#insertRunshopForEve[getActRunshopByMobile] error", e);
			throw ExceptionUtil.createServiceException(ExceptionCode.GET_RUNSHOP_BY_ID_ERROR);
		}
		if (null != existRunshop) {
			switch (existRunshop.getStatus()) {
			case ProjectConstant.PENDING_AUDIT:
				throw ExceptionUtil.createServiceException(ExceptionCode.MOBILE_PENDING_AUDIT);
			case ProjectConstant.QUALIFIED:
				throw ExceptionUtil.createServiceException(ExceptionCode.MOBILE_QUALIFIED);
			case ProjectConstant.NEED_FIX:
				throw ExceptionUtil.createServiceException(ExceptionCode.MOBILE_NEED_FIX);

			}
		}
		RunshopApplicationInfo runshopApplicationInfo = new RunshopApplicationInfo();
		runshopApplicationInfo.setStore_name(runshopInsertEveForm.getStore_name());
		runshopApplicationInfo.setCity_id(runshopInsertEveForm.getCity_id());
		runshopApplicationInfo.setCity_name(runshopInsertEveForm.getCity_name());
		runshopApplicationInfo.setAddress(runshopInsertEveForm.getAddress());
		/* 将门店分类的id转成json string */
		String store_classification_id;
		try {
			store_classification_id = JsonHelper.toJsonString(runshopInsertEveForm.getStore_classification_id());
		} catch (JsonProcessingException e) {
			logger.error("RunshopService#createRunshop to json string error", e);
			throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_INSERT_JSON_PARSE_ERROR);
		}
		runshopApplicationInfo.setStore_classification_id(store_classification_id);
		String store_classification;
		try {
			store_classification = JsonHelper.toJsonString(runshopInsertEveForm.getStore_classification());
		} catch (JsonProcessingException e) {
			logger.error("RunshopService#createRunshop to json string error", e);
			throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_INSERT_JSON_PARSE_ERROR);
		}
		runshopApplicationInfo.setStore_classification(store_classification);
		runshopApplicationInfo.setLatitude(runshopInsertEveForm.getLatitude());
		runshopApplicationInfo.setLongitude(runshopInsertEveForm.getLongitude());
		runshopApplicationInfo.setMobile(runshopInsertEveForm.getMobile());
		runshopApplicationInfo.setDistribution_way(ProjectConstant.DISTRIBUTION_WAY_NONE);
		runshopApplicationInfo.setBoss(runshopInsertEveForm.getBoss());
		runshopApplicationInfo.setSource(ProjectConstant.RUNSHOP_FROM_EVE);
		runshopApplicationInfo.setIdentity_number(runshopInsertEveForm.getIdentity_number());
		runshopApplicationInfo.setGaode_address(runshopInsertEveForm.getGaode_address());
		if (null != runshopInsertEveForm.getBusiness_license_pic()) {
			runshopApplicationInfo.setBusiness_license_name(runshopInsertEveForm.getBusiness_license_name());
			runshopApplicationInfo.setBusiness_license_time(business_license_time);
			runshopApplicationInfo.setBusiness_license_address(runshopInsertEveForm.getBusiness_license_address());
			runshopApplicationInfo.setBusiness_license_num(runshopInsertEveForm.getBusiness_license_num());
		}
		if (null != runshopInsertEveForm.getCatering_services_pic()) {
			runshopApplicationInfo.setCatering_services_license_name(runshopInsertEveForm
					.getCatering_services_license_name());
			runshopApplicationInfo.setCatering_services_license_num(runshopInsertEveForm
					.getCatering_services_license_num());
			runshopApplicationInfo.setCatering_services_license_time(runshopInsertEveForm
					.getCatering_services_license_time());
			runshopApplicationInfo.setCatering_services_license_address(runshopInsertEveForm
					.getCatering_services_license_address());
		}
		runshopApplicationInfo.setUser_id(runshopInsertEveForm.getUser_id());
		runshopApplicationInfo.setUser_email(runshopInsertEveForm.getUser_email());
		runshopApplicationInfo.setBackup(runshopInsertEveForm.getBackup());
		runshopApplicationInfo.setBusiness_license_type(runshopInsertEveForm.getBusiness_license_type());
		runshopApplicationInfo.setCatering_services_license_type(runshopInsertEveForm
				.getCatering_services_license_type());
		runshopApplicationInfo.setProvince_id(runshopInsertEveForm.getProvince_id());
		runshopApplicationInfo.setDistrict_id(runshopInsertEveForm.getDistrict_id());
		runshopApplicationInfo.setBusiness_license_type(runshopInsertEveForm.getBusiness_license_type());
		runshopApplicationInfo.setCatering_services_license_type(runshopInsertEveForm
				.getCatering_services_license_type());

		try {
			runshopMapper.createRunshopForEve(runshopApplicationInfo);
		} catch (Exception e) {
			logger.error("RunshopService#createRunshopForEve[createRunshoForEve] error", e);
			throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_INSERT_ERROR);
		}
		/* 保存图片信息 */
		int runshop_id = runshopApplicationInfo.getId();
		List<RunshopApplicationPicture> runshopApplicationPictureList = new ArrayList<>();
		/* 身份证正面 */
		String identity_positive_pic_hash;
		if (!StringUtils.isBlank(runshopInsertEveForm.getIdentity_positive_pic().getHash())) {
			identity_positive_pic_hash = imageUrlToHash(runshopInsertEveForm.getIdentity_positive_pic().getHash());
		} else if (!StringUtils.isBlank(runshopInsertEveForm.getIdentity_positive_pic().getUrl())) {
			identity_positive_pic_hash = imageUrlToHash(runshopInsertEveForm.getIdentity_positive_pic().getUrl());
		} else {
			logger.error("RunshopService#createRunshopForEve identity_positive_pic_hash is null");
			throw ExceptionUtil.createServiceException("ERROR", "身份证图片有误,请重新上传提交");
		}
		RunshopApplicationPicture identityPositivePic = new RunshopApplicationPicture(runshop_id,
				ProjectConstant.IDENTITY_POSITIVE_PIC_CODE, runshopInsertEveForm.getIdentity_positive_pic().getUrl(),
				identity_positive_pic_hash);
		runshopApplicationPictureList.add(identityPositivePic);

		/* 身份证背面 */
		String identity_opposite_pic_hash;
		if (!StringUtils.isBlank(runshopInsertEveForm.getIdentity_opposite_pic().getHash())) {
			identity_opposite_pic_hash = imageUrlToHash(runshopInsertEveForm.getIdentity_opposite_pic().getHash());
		} else if (!StringUtils.isBlank(runshopInsertEveForm.getIdentity_opposite_pic().getUrl())) {
			identity_opposite_pic_hash = imageUrlToHash(runshopInsertEveForm.getIdentity_opposite_pic().getUrl());
		} else {
			logger.error("RunshopService#createRunshopForEve identity_opposite_pic_hash is null");
			throw ExceptionUtil.createServiceException("ERROR", "身份证图片有误,请重新上传提交");
		}
		RunshopApplicationPicture identityOppositePic = new RunshopApplicationPicture(runshop_id,
				ProjectConstant.IDENTITY_OPPOSITE_PIC_CODE, runshopInsertEveForm.getIdentity_opposite_pic().getUrl(),
				identity_opposite_pic_hash);
		runshopApplicationPictureList.add(identityOppositePic);

		/* 门头照 */
		String doorPicHash;
		if (!StringUtils.isBlank(runshopInsertEveForm.getDoor_pics().get(0).getHash())) {
			doorPicHash = imageUrlToHash(runshopInsertEveForm.getDoor_pics().get(0).getHash());
		} else if (!StringUtils.isBlank(runshopInsertEveForm.getDoor_pics().get(0).getUrl())) {
			doorPicHash = imageUrlToHash(runshopInsertEveForm.getDoor_pics().get(0).getUrl());
		} else {
			logger.error("RunshopService#createRunshopForEve doorPicHash is null");
			throw ExceptionUtil.createServiceException("ERROR", "门头照图片有误,请重新上传提交");
		}
		RunshopApplicationPicture doorPic = new RunshopApplicationPicture(runshop_id, ProjectConstant.DOOR_PIC_CODE,
				runshopInsertEveForm.getDoor_pics().get(0).getUrl(), doorPicHash);
		runshopApplicationPictureList.add(doorPic);

		/* 店内照 */
		String storePicHash;
		if (!StringUtils.isBlank(runshopInsertEveForm.getStore_pics().get(0).getHash())) {
			storePicHash = imageUrlToHash(runshopInsertEveForm.getStore_pics().get(0).getHash());
		} else if (!StringUtils.isBlank(runshopInsertEveForm.getStore_pics().get(0).getUrl())) {
			storePicHash = imageUrlToHash(runshopInsertEveForm.getStore_pics().get(0).getUrl());
		} else {
			logger.error("RunshopService#createRunshopForEve storePicHash is null");
			throw ExceptionUtil.createServiceException("ERROR", "店内照照图片有误,请重新上传提交");
		}
		RunshopApplicationPicture storePic = new RunshopApplicationPicture(runshop_id, ProjectConstant.STORE_PIC_CODE,
				runshopInsertEveForm.getStore_pics().get(0).getUrl(), storePicHash);
		runshopApplicationPictureList.add(storePic);

		if (null != runshopInsertEveForm.getBusiness_license_pic()) {
			RunshopApplicationPicture businessLicensePic = new RunshopApplicationPicture(runshop_id,
					ProjectConstant.BUSINESS_LICENSE_PIC_CODE, runshopInsertEveForm.getBusiness_license_pic().getUrl(),
					runshopInsertEveForm.getBusiness_license_pic().getHash());
			runshopApplicationPictureList.add(businessLicensePic);
		}
		if (null != runshopInsertEveForm.getCatering_services_pic()) {
			RunshopApplicationPicture cateringServicesPic = new RunshopApplicationPicture(runshop_id,
					ProjectConstant.CATERING_SERVICES_PIC_CODE, runshopInsertEveForm.getCatering_services_pic()
							.getUrl(), runshopInsertEveForm.getCatering_services_pic().getHash());
			runshopApplicationPictureList.add(cateringServicesPic);
		}
		try {

			runshopPictureMapper.batchCreateRunshopPicture(runshopApplicationPictureList);
		} catch (Exception e) {
			logger.error("RunshopService#createRunshopForEve[batchCreateRunshopPicture] error", e);
			throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_INSERT_RUNSHOP_PIC_ERROR);
		}
		/* 添加操作日志 */
		insertRunshopOperateRecord(runshop_id, runshopInsertEveForm.getUser_id(), runshopInsertEveForm.getUser_email(),
				runshopInsertEveForm.getUser_name(), ProjectConstant.CREATE_RUNSHOP_RECORD_MESSAGE,
				RunshopOperateType.CREATE.getValue());
		return runshop_id;
	}

	/**
	 * 检查 新建开店申请对象 for eve .
	 *
	 * @param runshopInsertEveForm
	 * @throws ServiceException
	 */
	private void checkInsertUpdateParams(RunshopInsertEveForm runshopInsertEveForm) throws ServiceException {
		if (StringUtils.isBlank(runshopInsertEveForm.getStore_name())) {
			throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_INSERT_STORE_NAME_NULL);
		}
		if (runshopInsertEveForm.getStore_name().contains(ProjectConstant.RUNSHOP_NAME_NOT_ALLOW)
				|| runshopInsertEveForm.getStore_name().contains(ProjectConstant.RUNSHOP_NAME_NOT_ALLOW_1)) {
			throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_NAME_NOT_ALLOW);
		}
		if (runshopInsertEveForm.getStore_name().length() > 32) {
			throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_STORE_NAME_MAX_ERROR);
		}
		if (StringUtils.isBlank(runshopInsertEveForm.getMobile())) {
			throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_INSERT_MOBILE_NULL);
		}
		if (!runshopInsertEveForm.getMobile().matches(ProjectConstant.MOBILE_REG)) {
			throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_INSERT_MOBILE_TYPE_ERROR);
		}
		if (CollectionUtils.isEmpty(runshopInsertEveForm.getStore_classification())) {
			throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_INSERT_STORE_CLASSIFICATION_NULL);
		}
		if (CollectionUtils.isEmpty(runshopInsertEveForm.getStore_classification_id())) {
			throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_INSERT_STORE_CLASSIFICATION_ID_NULL);
		}
		if (runshopInsertEveForm.getStore_classification().size() > 2) {
			throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_EVE_CLASSIFICATION_MAX_ERROR);
		}
		if (runshopInsertEveForm.getStore_classification_id().size() > 2) {
			throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_EVE_CLASSIFICATION_MAX_ERROR);
		}
		if (StringUtils.isBlank(runshopInsertEveForm.getCity_name())) {
			throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_INSERT_CITY_NAME_NULL);
		}
		if (0 == runshopInsertEveForm.getCity_id()) {
			throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_INSERT_CITY_ID_NULL);
		}
		if (StringUtils.isBlank(runshopInsertEveForm.getAddress())) {
			throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_INSERT_ADDRESS_NULL);
		}
		if (runshopInsertEveForm.getAddress().length() > 64) {
			throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_ADDRESS_MAX_ERROR);
		}
		if (null == runshopInsertEveForm.getLatitude()) {
			throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_INSERT_LATITUDE_NULL);
		}
		if (null == runshopInsertEveForm.getLongitude()) {
			throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_INSERT_LONGITUDE_NULL);
		}
		if (StringUtils.isBlank(runshopInsertEveForm.getBoss())) {
			throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_INSERT_BOSS_NULL);
		}
		if (runshopInsertEveForm.getBoss().length() > 8) {
			throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_BOSS_MAX_ERROR);
		}
		if (StringUtils.isBlank(runshopInsertEveForm.getIdentity_number())) {
			throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_INSERT_IDENTITY_NUMBER_NULL);
		}
		if (!runshopInsertEveForm.getIdentity_number().matches(ProjectConstant.IDENTITY_NUM_REG)) {
			throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_IDENTITY_NUM_TYPE_ERROR);
		}
		if (null == runshopInsertEveForm.getIdentity_positive_pic()) {
			throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_INSERT_IDENTITY_POSITIVE_PIC_NULL);
		}
		if (null == runshopInsertEveForm.getIdentity_opposite_pic()) {
			throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_INSERT_IDENTITY_OPPOSITE_PIC_NULL);
		}
		if (CollectionUtils.isEmpty(runshopInsertEveForm.getDoor_pics())) {
			throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_INSERT_DOOR_PICS_EVE_NULL);
		}
		if (CollectionUtils.isEmpty(runshopInsertEveForm.getStore_pics())) {
			throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_INSERT_STORE_PICS_NULL);
		}
		if (0 == runshopInsertEveForm.getUser_id()) {
			throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_USER_ID_NULL);
		}
		if (StringUtils.isBlank(runshopInsertEveForm.getUser_email())) {
			throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_USER_EMAIL_NULL);
		}
		if (runshopInsertEveForm.getBackup().length() > 256) {
			throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_BACKUP_MAX_ERROR);
		}

		/* 营业执照校验 */
		checkBusinessLicense(runshopInsertEveForm.getBusiness_license_pic(),
				runshopInsertEveForm.getBusiness_license_name(), runshopInsertEveForm.getBusiness_license_num(),
				runshopInsertEveForm.getBusiness_license_address(), runshopInsertEveForm.getBusiness_license_time(),
				runshopInsertEveForm);

		/* 餐厅服务许可证校验 */
		checkCateringServicesLicense(runshopInsertEveForm.getCatering_services_pic(),
				runshopInsertEveForm.getCatering_services_license_name(),
				runshopInsertEveForm.getCatering_services_license_address(),
				runshopInsertEveForm.getCatering_services_license_num(),
				runshopInsertEveForm.getCatering_services_license_time(), runshopInsertEveForm);
	}

	@Override
	public void updateRunshopForEve(RunshopInsertEveForm runshopInsertEveForm, int runshopId) throws ServiceException,
			ServerException {
		logger.info("RunshopService#updateRunshopForEve into, form is {}, runshopId is {}", runshopInsertEveForm,
				runshopId);
		if (1 == runshopInsertEveForm.getBusiness_license_time_no_limit()) {
			runshopInsertEveForm.setBusiness_license_time(ProjectConstant.BUSINESS_LICENSE_NO_EXPIRE_TIME);
		}
		if (runshopInsertEveForm.getBackup() == null) {
			runshopInsertEveForm.setBackup("");
		}
		checkInsertUpdateParams(runshopInsertEveForm);

		RunshopApplicationInfo originApplication = runshopMapper.getRunshopApplicationInfoById(runshopId);
		if (originApplication == null) {
			logger.error("RunshopService#updateRunshopForEve not existed runshop id {}", runshopId);
			throw new ServiceException("500", "不存在此申请id");
		}

		if (originApplication.getStatus() != RunShopStatus.NEED_FIX.getValue()) {
			logger.error(
					"RunshopService#updateRunshopForEve try to fix runshopApplication id {} whose status is not 2",
					runshopId);
			throw new ServiceException("500", "不能修改该申请");
		}

		if (StringUtils.isEmpty(runshopInsertEveForm.getGaode_address())) {
			logger.error("RunshopService#updateRunshopForEve fail, gaode_address is empty");
			throw new ServiceException("500", "地图信息缺失");
		}

		String business_license_time = runshopInsertEveForm.getBusiness_license_time();
		if (StringUtils.isNotBlank(business_license_time)) {
			if (!business_license_time.equals(ProjectConstant.BUSINESS_LICENSE_NO_EXPIRE_TIME)) {
				if (!business_license_time.matches(ProjectConstant.DATE_FORMAT_REGULAR_CH)) {
					throw ExceptionUtil.createServiceException(ExceptionCode.INSERT_RUNSHOP_ISSUE_TIME_TYPE_ERROR);
				}
				boolean isBeforeToday;
				try {
					isBeforeToday = DateUtil.isBeforeToday(business_license_time, ProjectConstant.DATE_FORMAT_EVE);
				} catch (Exception e) {
					logger.error("RunshopService#updateRunshopForEve[check is before today] error", e);
					throw ExceptionUtil.createServiceException(ExceptionCode.INSERT_RUNSHOP_ISSUE_TIME_TYPE_ERROR);
				}
				if (isBeforeToday) {
					logger.error(
							"RunshopService#updateRunshopForEve[business license time before today], business license time is:{}",
							business_license_time);
					throw ExceptionUtil
							.createServiceException(ExceptionCode.INSERT_RUNSHOP_BUSINESS_LICENSE_TIME_BEFORE_NOW_ERROR_FOR_EVE);
				}
				try {
					Date date = DateUtils.parseDate(business_license_time, ProjectConstant.DATE_FORMAT_EVE);
					String dateString = DateFormatUtils.format(date, ProjectConstant.DATE_FORMAT);
					runshopInsertEveForm.setBusiness_license_time(dateString);
					business_license_time = dateString;
				} catch (Exception e) {
					logger.error(
							"RunshopService#updateRunshopForEve[parse business license time to DATE_FORMAT] error", e);
					throw ExceptionUtil.createServiceException(ExceptionCode.INSERT_RUNSHOP_ISSUE_TIME_TYPE_ERROR);
				}
			}
		}
		String catering_services_license_time = runshopInsertEveForm.getCatering_services_license_time();
		if (StringUtils.isNotBlank(catering_services_license_time)) {
			if (!catering_services_license_time.matches(ProjectConstant.DATE_FORMAT_REGULAR_CH)) {
				throw ExceptionUtil
						.createServiceException(ExceptionCode.INSERT_RUNSHOP_CATERING_SERVICES_LICENSE_TIME_TYPE_ERROR);
			}
			boolean isBeforeToday;
			try {
				isBeforeToday = DateUtil.isBeforeToday(catering_services_license_time, ProjectConstant.DATE_FORMAT_EVE);
			} catch (Exception e) {
				logger.error(
						"RunshopService#updateRunshopForEve[catering services license time], catering services license time is:{}",
						catering_services_license_time);
				throw ExceptionUtil
						.createServiceException(ExceptionCode.INSERT_RUNSHOP_CATERING_SERVICES_LICENSE_TIME_BEFORE_NOW_ERROR);
			}
			if (isBeforeToday) {
				logger.error("RunshopService#updateRunshopForEve[catering services license time before today]");
				throw ExceptionUtil
						.createServiceException(ExceptionCode.CREATE_RUNSHOP_CATERING_SERVICES_LICENSE_TIME_BEFORE_NOW_ERROR);
			}
			try {
				Date date = DateUtils.parseDate(catering_services_license_time, ProjectConstant.DATE_FORMAT_EVE);
				String dateString = DateFormatUtils.format(date, ProjectConstant.DATE_FORMAT);
				runshopInsertEveForm.setCatering_services_license_time(dateString);
			} catch (Exception e) {
				logger.error("RunshopService#updateRunshopForEve[parse catering license time to DATE_FORMAT] error", e);
				throw ExceptionUtil.createServiceException(ExceptionCode.INSERT_RUNSHOP_ISSUE_TIME_TYPE_ERROR);
			}
		}

		/* 开店申请runshop重复校验 */
		String mobile = runshopInsertEveForm.getMobile();
		List<RunshopApplicationInfo> existRunshopList;
		try {
			existRunshopList = runshopMapper.listActRunshopByMobileForEve(mobile);
			logger.info("RunshopService#updateRunshopForEve existRunshopList is {}", existRunshopList);
		} catch (Exception e) {
			logger.error("RunshopService#updateRunshopForEve[getActRunshopByMobile] error", e);
			throw ExceptionUtil.createServiceException(ExceptionCode.GET_RUNSHOP_BY_ID_ERROR);
		}
		if (!CollectionUtils.isEmpty(existRunshopList)) {
			for (RunshopApplicationInfo info : existRunshopList) {
				switch (info.getStatus()) {
				case ProjectConstant.PENDING_AUDIT:
					throw ExceptionUtil.createServiceException(ExceptionCode.MOBILE_PENDING_AUDIT);
				case ProjectConstant.QUALIFIED:
					throw ExceptionUtil.createServiceException(ExceptionCode.MOBILE_QUALIFIED);
				case ProjectConstant.NEED_FIX:
					if (runshopId != info.getId()) {
						throw ExceptionUtil.createServiceException(ExceptionCode.MOBILE_NEED_FIX);
					}
				}
			}
		}

		RunshopApplicationInfo runshopApplicationInfo = new RunshopApplicationInfo();
		runshopApplicationInfo.setId(runshopId);
		runshopApplicationInfo.setStore_name(runshopInsertEveForm.getStore_name());
		runshopApplicationInfo.setProvince_id(runshopInsertEveForm.getProvince_id());
		runshopApplicationInfo.setDistrict_id(runshopInsertEveForm.getDistrict_id());
		runshopApplicationInfo.setCity_id(runshopInsertEveForm.getCity_id());
		runshopApplicationInfo.setCity_name(runshopInsertEveForm.getCity_name());
		runshopApplicationInfo.setAddress(runshopInsertEveForm.getAddress());
		runshopApplicationInfo.setGaode_address(runshopInsertEveForm.getGaode_address());
		/* 将门店分类的id转成json string */
		String store_classification_id;
		try {
			store_classification_id = JsonHelper.toJsonString(runshopInsertEveForm.getStore_classification_id());
		} catch (JsonProcessingException e) {
			logger.error("RunshopService#createRunshop to json string error", e);
			throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_INSERT_JSON_PARSE_ERROR);
		}
		runshopApplicationInfo.setStore_classification_id(store_classification_id);
		String store_classification;
		try {
			store_classification = JsonHelper.toJsonString(runshopInsertEveForm.getStore_classification());
		} catch (JsonProcessingException e) {
			logger.error("RunshopService#createRunshop to json string error", e);
			throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_INSERT_JSON_PARSE_ERROR);
		}
		runshopApplicationInfo.setStore_classification(store_classification);
		runshopApplicationInfo.setLatitude(runshopInsertEveForm.getLatitude());
		runshopApplicationInfo.setLongitude(runshopInsertEveForm.getLongitude());
		runshopApplicationInfo.setMobile(runshopInsertEveForm.getMobile());
		runshopApplicationInfo.setDistribution_way(ProjectConstant.DISTRIBUTION_WAY_NONE);
		runshopApplicationInfo.setBoss(runshopInsertEveForm.getBoss());
		runshopApplicationInfo.setSource(ProjectConstant.RUNSHOP_FROM_EVE);
		runshopApplicationInfo.setStatus(0);
		runshopApplicationInfo.setIdentity_number(runshopInsertEveForm.getIdentity_number());
		if (null != runshopInsertEveForm.getBusiness_license_pic()) {
			runshopApplicationInfo.setBusiness_license_name(runshopInsertEveForm.getBusiness_license_name());
			runshopApplicationInfo.setBusiness_license_time(business_license_time);
			runshopApplicationInfo.setBusiness_license_address(runshopInsertEveForm.getBusiness_license_address());
			runshopApplicationInfo.setBusiness_license_num(runshopInsertEveForm.getBusiness_license_num());
		}
		if (null != runshopInsertEveForm.getCatering_services_pic()) {
			runshopApplicationInfo.setCatering_services_license_name(runshopInsertEveForm
					.getCatering_services_license_name());
			runshopApplicationInfo.setCatering_services_license_num(runshopInsertEveForm
					.getCatering_services_license_num());
			runshopApplicationInfo.setCatering_services_license_time(runshopInsertEveForm
					.getCatering_services_license_time());
			runshopApplicationInfo.setCatering_services_license_address(runshopInsertEveForm
					.getCatering_services_license_address());
		}
		runshopApplicationInfo.setUser_id(runshopInsertEveForm.getUser_id());
		runshopApplicationInfo.setUser_email(runshopInsertEveForm.getUser_email());
		runshopApplicationInfo.setBackup(runshopInsertEveForm.getBackup());
		runshopApplicationInfo.setBusiness_license_type(runshopInsertEveForm.getBusiness_license_type());
		runshopApplicationInfo.setCatering_services_license_type(runshopInsertEveForm
				.getCatering_services_license_type());

		try {
			runshopMapper.updateRunShopForEve(runshopApplicationInfo);
		} catch (Exception e) {
			logger.error("RunshopService#createRunshopForEve[createRunshoForEve] error", e);
			throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_INSERT_ERROR);
		}

		/* 保存图片信息 */
		int runshop_id = runshopApplicationInfo.getId();
		List<RunshopApplicationPicture> runshopApplicationPictureList = new ArrayList<>();
		/* 身份证正面 */
		String identity_positive_pic_hash;
		if (!StringUtils.isBlank(runshopInsertEveForm.getIdentity_positive_pic().getUrl())) {
			identity_positive_pic_hash = imageUrlToHash(runshopInsertEveForm.getIdentity_positive_pic().getUrl());
		} else if (!StringUtils.isBlank(runshopInsertEveForm.getIdentity_positive_pic().getHash())) {
			identity_positive_pic_hash = imageUrlToHash(runshopInsertEveForm.getIdentity_positive_pic().getHash());
		} else {
			logger.error("RunshopService#updateRunshopForEve identity_positive_pic_hash is null");
			throw ExceptionUtil.createServiceException("ERROR", "身份证图片有误,请重新上传提交");
		}
		RunshopApplicationPicture identityPositivePic = new RunshopApplicationPicture(runshop_id,
				ProjectConstant.IDENTITY_POSITIVE_PIC_CODE, runshopInsertEveForm.getIdentity_positive_pic().getUrl(),
				identity_positive_pic_hash);
		runshopApplicationPictureList.add(identityPositivePic);

		/* 身份证背面 */
		String identity_opposite_pic_hash;
		if (!StringUtils.isBlank(runshopInsertEveForm.getIdentity_opposite_pic().getUrl())) {
			identity_opposite_pic_hash = imageUrlToHash(runshopInsertEveForm.getIdentity_opposite_pic().getUrl());
		} else if (!StringUtils.isBlank(runshopInsertEveForm.getIdentity_opposite_pic().getHash())) {
			identity_opposite_pic_hash = imageUrlToHash(runshopInsertEveForm.getIdentity_opposite_pic().getHash());
		} else {
			logger.error("RunshopService#updateRunshopForEve identity_opposite_pic_hash is null");
			throw ExceptionUtil.createServiceException("ERROR", "身份证图片有误,请重新上传提交");
		}
		RunshopApplicationPicture identityOppositePic = new RunshopApplicationPicture(runshop_id,
				ProjectConstant.IDENTITY_OPPOSITE_PIC_CODE, runshopInsertEveForm.getIdentity_opposite_pic().getUrl(),
				identity_opposite_pic_hash);
		runshopApplicationPictureList.add(identityOppositePic);

		/* 门头照 */
		String doorPicHash;
		if (!StringUtils.isBlank(runshopInsertEveForm.getDoor_pics().get(0).getUrl())) {
			doorPicHash = imageUrlToHash(runshopInsertEveForm.getDoor_pics().get(0).getUrl());
		} else if (!StringUtils.isBlank(runshopInsertEveForm.getDoor_pics().get(0).getHash())) {
			doorPicHash = imageUrlToHash(runshopInsertEveForm.getDoor_pics().get(0).getHash());
		} else {
			logger.error("RunshopService#updateRunshopForEve doorPicHash is null");
			throw ExceptionUtil.createServiceException("ERROR", "门头照图片有误,请重新上传提交");
		}
		RunshopApplicationPicture doorPic = new RunshopApplicationPicture(runshop_id, ProjectConstant.DOOR_PIC_CODE,
				runshopInsertEveForm.getDoor_pics().get(0).getUrl(), doorPicHash);
		runshopApplicationPictureList.add(doorPic);

		/* 店内照 */
		String storePicHash;
		if (!StringUtils.isBlank(runshopInsertEveForm.getStore_pics().get(0).getUrl())) {
			storePicHash = imageUrlToHash(runshopInsertEveForm.getStore_pics().get(0).getUrl());
		} else if (!StringUtils.isBlank(runshopInsertEveForm.getStore_pics().get(0).getHash())) {
			storePicHash = imageUrlToHash(runshopInsertEveForm.getStore_pics().get(0).getHash());
		} else {
			logger.error("RunshopService#updateRunshopForEve storePicHash is null");
			throw ExceptionUtil.createServiceException("ERROR", "店内照照图片有误,请重新上传提交");
		}
		RunshopApplicationPicture storePic = new RunshopApplicationPicture(runshop_id, ProjectConstant.STORE_PIC_CODE,
				runshopInsertEveForm.getStore_pics().get(0).getUrl(), storePicHash);
		runshopApplicationPictureList.add(storePic);
		if (null != runshopInsertEveForm.getBusiness_license_pic()) {
			RunshopApplicationPicture businessLicensePic = new RunshopApplicationPicture(runshop_id,
					ProjectConstant.BUSINESS_LICENSE_PIC_CODE, runshopInsertEveForm.getBusiness_license_pic().getUrl(),
					imageUrlToHash(runshopInsertEveForm.getBusiness_license_pic().getUrl()));
			runshopApplicationPictureList.add(businessLicensePic);
		}
		if (null != runshopInsertEveForm.getCatering_services_pic()) {
			RunshopApplicationPicture cateringServicesPic = new RunshopApplicationPicture(runshop_id,
					ProjectConstant.CATERING_SERVICES_PIC_CODE, runshopInsertEveForm.getCatering_services_pic()
							.getUrl(), imageUrlToHash(runshopInsertEveForm.getCatering_services_pic().getUrl()));
			runshopApplicationPictureList.add(cateringServicesPic);
		}

		try {
			// 删除，再新增
			runshopPictureMapper.batchDeleteByRunShopIdForEve(runshopId);
			runshopPictureMapper.batchCreateRunshopPictureForEve(runshopApplicationPictureList);
		} catch (Exception e) {
			logger.error("RunshopService#insertRunshop[batchCreateRunshopPicture] error", e);
			throw ExceptionUtil.createServerException(ExceptionCode.RUNSHOP_INSERT_RUNSHOP_PIC_ERROR);
		}

		String recordMsg = ProjectConstant.UPDATE_RUNSHOP_RECORD_MESSAGE + " last reject resson is : "
				+ originApplication.getReason();
		insertRunshopOperateRecord(runshop_id, runshopApplicationInfo.getUser_id(),
				runshopApplicationInfo.getUser_email(), ProjectConstant.CREATE_RUNSHOP_RECORD_OPERATOR_NAME, recordMsg,
				RunshopOperateType.UPDATE.getValue());
	}

	/**
	 * fuss图片的url转换为hash .
	 *
	 * @param imageUrl
	 * @return
	 */
	private String imageUrlToHash(String imageUrl) {
		String[] urlArray = StringUtils.split(imageUrl, "/");
		/* 获取url数组长度 */
		int urlLength = urlArray.length;
		if (urlLength < 3) {
			return imageUrl;
		}
		String lastUrl = urlArray[urlLength - 1];
		/* 去除最后的"."字符 */
		lastUrl = StringUtils.split(lastUrl, ".")[0];
		/* 组装返回数据 */
		StringBuilder sb = new StringBuilder(urlArray[urlLength - 3]);
		sb.append(urlArray[urlLength - 2]);
		sb.append(lastUrl);
		return sb.toString();
	}

	private void checkBusinessLicense(ImageEntity businessLicensePic, String businessLicenseName,
			String businessLicenseNum, String businessLicenseAddress, String businessLicenseTime,
			RunshopInsertEveForm runshopInsertEveForm) throws ServiceException {
		if (null != businessLicensePic || StringUtils.isNotBlank(businessLicenseName)
				|| StringUtils.isNotBlank(businessLicenseNum) || StringUtils.isNotBlank(businessLicenseAddress)
				|| StringUtils.isNotBlank(businessLicenseTime)) {
			if (null == businessLicensePic)
				throw ExceptionUtil.createServiceException(ExceptionCode.BUSINNESS_LICENSE_PIC_NULL_ERROR);
			if (StringUtils.isBlank(businessLicenseName))
				throw ExceptionUtil.createServiceException(ExceptionCode.BUSINESS_LICENSE_NAME_NULL_ERROR);
			if (businessLicenseName.length() > 64)
				throw ExceptionUtil.createServiceException(ExceptionCode.BUSINESS_LICENSE_NAME_MAX_ERROR);
			if (StringUtils.isBlank(businessLicenseAddress))
				throw ExceptionUtil.createServiceException(ExceptionCode.BUSINESS_LICENSE_ADDRESS_NULL_ERROR);
			if (businessLicenseAddress.length() > 64)
				throw ExceptionUtil.createServiceException(ExceptionCode.BUSINESS_LICENSE_ADDRESS_MAX_ERROR);
			if (StringUtils.isBlank(businessLicenseNum))
				throw ExceptionUtil.createServiceException(ExceptionCode.BUSINESS_LICENSE_NUM_NULL_ERROR);
			if (businessLicenseNum.length() > 64)
				throw ExceptionUtil.createServiceException(ExceptionCode.BUSINESS_LICENSE_NUM_MA_ERROR);
			if (StringUtils.isBlank(businessLicenseTime))
				throw ExceptionUtil.createServiceException(ExceptionCode.BUSINESS_LICENSE_TIME_NULL_ERROR);
			// 兼容老的eve客户端,type为0的情况
			if (runshopInsertEveForm.getBusiness_license_type() == 0) {
				runshopInsertEveForm.setBusiness_license_type(1);
			}
			if (!isBusinessLicenseTypeValid(runshopInsertEveForm.getBusiness_license_type())) {
				throw ExceptionUtil.createServiceException(ExceptionCode.BUSINESS_LICENSE_TYPE_ERROR);
			}
		} else {
			runshopInsertEveForm.setBusiness_license_type(0);
		}

	}

	private boolean isBusinessLicenseTypeValid(int type) {
		for (BusinessLicenseTypeEnum typeEnum : BusinessLicenseTypeEnum.values()) {
			if (typeEnum.getValue() == type) {
				return true;
			}
		}
		return false;
	}

	private boolean isCateringServicesLicenseTypeValid(int type) {
		for (CateringServicesLicenseTypeEnum typeEnum : CateringServicesLicenseTypeEnum.values()) {
			if (typeEnum.getValue() == type) {
				return true;
			}
		}
		return false;
	}

	private void checkCateringServicesLicense(ImageEntity cateringServicesPic, String cateringServicesLicenseName,
			String cateringServicesLicenseAddress, String cateringServicesLicenseNum,
			String cateringServicesLicenseTime, RunshopInsertEveForm insertEveForm) throws ServiceException {
		if (null != cateringServicesPic || StringUtils.isNotBlank(cateringServicesLicenseName)
				|| StringUtils.isNotBlank(cateringServicesLicenseNum)
				|| StringUtils.isNotBlank(cateringServicesLicenseAddress)
				|| StringUtils.isNotBlank(cateringServicesLicenseTime)) {
			if (null == cateringServicesPic)
				throw ExceptionUtil.createServiceException(ExceptionCode.CATERING_SERVICES_PIC_NULL);
			if (StringUtils.isBlank(cateringServicesLicenseName))
				throw ExceptionUtil.createServiceException(ExceptionCode.CATERING_SERVICES_LICENSE_NAME_NULL_ERROR);
			if (cateringServicesLicenseName.length() > 64)
				throw ExceptionUtil.createServiceException(ExceptionCode.CATERING_SERVICES_LICENSE_NAME_MAX_ERROR);
			if (StringUtils.isBlank(cateringServicesLicenseAddress))
				throw ExceptionUtil.createServiceException(ExceptionCode.CATERING_SERVICES_LICENSE_ADDRESS_NULL_ERROR);
			if (cateringServicesLicenseAddress.length() > 64)
				throw ExceptionUtil.createServiceException(ExceptionCode.CATERING_SERVICES_LICENSE_ADDRESS_MAX_ERROR);
			if (StringUtils.isBlank(cateringServicesLicenseNum))
				throw ExceptionUtil.createServiceException(ExceptionCode.CATERING_SERVICES_LICENSE_NUM_NULL_ERROR);
			if (cateringServicesLicenseNum.length() > 64)
				throw ExceptionUtil.createServiceException(ExceptionCode.CATERING_SERVICES_LICENSE_NUM_MAX_ERROR);
			if (StringUtils.isBlank(cateringServicesLicenseTime))
				throw ExceptionUtil.createServiceException(ExceptionCode.CATERING_SERVICES_LICENSE_TIME_NULL_ERROR);
			// 兼容老的客户端,type为0的情况
			if (insertEveForm.getCatering_services_license_type() == 0) {
				insertEveForm.setCatering_services_license_type(1);
			}
			if (!isCateringServicesLicenseTypeValid(insertEveForm.getCatering_services_license_type())) {
				throw ExceptionUtil.createServiceException(ExceptionCode.CATERING_SERVICES_LICENSE_TYPE_ERROR);
			}
		} else {
			insertEveForm.setCatering_services_license_type(0);
		}

	}

	/**
	 * 根据传入的餐厅ID和用户ID，分配BD报备的新店。 本接口仅限于BD报备调用
	 *
	 * @param rstID
	 * @param userID
	 * @throws ServiceException
	 * @throws ServerException
	 */
	@Override
	public void distributeApplicationFromBD(int rstID, int userID, RunshopApplicationInfo info)
			throws ServiceException, ServerException {

		logger.info("RunshopService.distributeApplicationFromBD() start with rstID:{}, userID:{}", rstID, userID);
		try {

			if (rstID < 0 || userID < 0) {
				logger.info("RunshopService.distributeApplicationFromBD() get invalid input, return.");
				return;

			}

			setIsDistribution(rstID, RunshopServiceConstants.DISTRIBUTED);

			try {

				policyService.distributeRstCreatedByBD(rstID, userID, info);
				return;

			} catch (Exception e) {
				logger.error("RunshopService.distributeApplicationFromBD() failed at:{}", e);
				logger.info("RunshopService.distributeApplicationFromBD() retry!!");

			}

			logger.info("RunshopService.distributeApplicationFromBD() distribute failed after {} times, "
					+ "set to not distributed and wait for script handling.", RunshopServiceConstants.RETRY_TIMES);
			setIsDistribution(rstID, RunshopServiceConstants.DISTRIBUTED_FAILED);

		} catch (Exception e) {
			logger.error("RunshopService.distributeApplicationFromBD()failed at:{}", e);
			throw ExceptionUtil.createServerException(ExceptionCode.DISTRIBUTE_STORE_FAILED);

		}

	}

	/**
	 * 根据传入的餐厅ID和用户ID，分配BD报备的新店。 本接口仅限于BD报备调用
	 *
	 * @param rstID
	 * @param userID
	 * @throws ServiceException
	 * @throws ServerException
	 */
	@Override
	public void distributeApplicationForSigGka(int rstID, int userID, RunshopApplicationInfo info)
			throws ServiceException, ServerException {

		logger.info("RunshopService.distributeApplicationForSigGka() start with rstID:{}, userID:{}, {}", rstID,
				userID, info);
		try {

			if (rstID < 0 || userID < 0) {
				logger.info("RunshopService.distributeApplicationForSigGka() get invalid input, return.");
				return;

			}

			setIsDistribution(rstID, RunshopServiceConstants.DISTRIBUTED);

			try {

				policyService.distributeSigGkaShop(rstID, info);
				return;

			} catch (Exception e) {
				logger.error("RunshopService.distributeApplicationForSigGka() failed at:{}", e);
				logger.info("RunshopService.distributeApplicationForSigGka() retry!!");

			}

			logger.info("RunshopService.distributeApplicationFromBD() distribute failed after {} times, "
					+ "set to not distributed and wait for script handling.", RunshopServiceConstants.RETRY_TIMES);
			setIsDistribution(rstID, RunshopServiceConstants.DISTRIBUTED_FAILED);

		} catch (Exception e) {
			logger.error("RunshopService.distributeApplicationFromBD()failed at:{}", e);
			throw ExceptionUtil.createServerException(ExceptionCode.DISTRIBUTE_STORE_FAILED);

		}

	}

	/**
	 * 根据传入的餐厅ID和用户ID，分配新店。
	 *
	 * @param rstID
	 * @throws ServiceException
	 * @throws ServerException
	 */
	@Override
	public void distributeApplication(int rstID) throws ServiceException, ServerException {

		logger.info("RunshopService.distributeApplication() start with rstID:{}", rstID);
		try {

			if (rstID < 0) {
				logger.info("RunshopService.distributeApplication() get invalid input, return.");
				return;
			}

			setIsDistribution(rstID, RunshopServiceConstants.DISTRIBUTED);

			try {
				policyService.distributeByRstID(rstID);
				return;

			} catch (Exception e) {
				logger.error("RunshopService.distributeApplication() failed at:{}", e);
				logger.info("RunshopService.distributeApplication() retry!!");
			}

			logger.info("RunshopService.distributeApplication() distribute failed after {} times, "
					+ "set to not distributed and wait for script handling.", RunshopServiceConstants.RETRY_TIMES);
			setIsDistribution(rstID, RunshopServiceConstants.DISTRIBUTED_FAILED);

		} catch (Exception e) {
			logger.error("RunshopService.distributeApplication()failed at:{}", e);
			throw ExceptionUtil.createServerException(ExceptionCode.DISTRIBUTE_STORE_FAILED);
		}

	}

	/**
	 * 是否首次创建开店申请
	 *
	 * @param mobile
	 * @return
	 * @throws ServiceException
	 * @throws ServiceException
	 */
	@Override
	public boolean isFirstApply(String mobile) throws ServiceException, ServiceException {
		logger.info("RunshopService#isFirstApply,mobile:{}", mobile);
		// List<RunshopApplicationInfo> runShopList =
		// runshopMapper.listRunShopByMobile(mobile);
		List<RunshopApplicationInfo> runShopList = runshopMapper.listRunShopByMobileAndIsNew(mobile);
		logger.info("RunshopService#isFirstApply,runShopList.size:{}", runShopList.size());
		if (runShopList.size() == 0) {
			return true;
		}
		return false;
	}

	@Override
	public List<MyRunShopSimpleDto> listMyRunshop(String mobile, int status, int limit, int offset)
			throws ServiceException, ServiceException {

		logger.info("RunshopService#listMyRunshop,mobile:{},status:{},limit:{},offset:{}", mobile, status, limit,
				offset);
		List<RunShopSimpleDto> runShopList = runshopMapper.listMyRunshopInKaidian(mobile, status, limit, offset);

		List<MyRunShopSimpleDto> myRunShopList = new ArrayList<>();

		if (RunshopServiceConstants.CHECK_PASS == status) {

			List<Integer> rstIdList = runShopList.stream().map(runshop -> runshop.getRst_id())
					.collect(Collectors.toList());

			// 返回值是list，加try catch？
			List<Restaurant> rstList = restaurantService.getRestaurants(rstIdList);
			Map<Integer, Integer> rst2BusyLevelValue = new HashMap<>();
			for (Restaurant rst : rstList) {
				RestaurantBusyLevel busyLevel = rst.getBusyLevel();
				logger.info("RunshopService#listMyRunshop,busyLevel:{}", busyLevel);
				if (busyLevel != null) {
					rst2BusyLevelValue.put(rst.getId(), busyLevel.getValue());
				} else {
					logger.info("RunshopService#listMyRunshop,rstId:{},busyLevel == null", rst.getId());
				}
			}

			String statusStr = "";
			for (RunShopSimpleDto myRunShop : runShopList) {
				statusStr = "";
				if (rst2BusyLevelValue.get(myRunShop.getRst_id()) == RestaurantBusyLevel.OPEN.getValue()) {
					statusStr = "正在营业";
				}
				if (rst2BusyLevelValue.get(myRunShop.getRst_id()) == RestaurantBusyLevel.CLOSED.getValue()) {
					statusStr = "暂时关闭";
				}
				if (rst2BusyLevelValue.get(myRunShop.getRst_id()) == RestaurantBusyLevel.ORDER_BY_PHONE.getValue()) {
					statusStr = "电话订餐";
				}
				if (rst2BusyLevelValue.get(myRunShop.getRst_id()) == RestaurantBusyLevel.HOLIDAY.getValue()) {
					statusStr = "假期休业";
				}
				myRunShopList.add(new MyRunShopSimpleDto(myRunShop.getId(), myRunShop.getStore_name(), myRunShop
						.getBoss(), mobile, myRunShop.getCreated_at(), statusStr, status));
			}

		} else {
			String statusStr = "";
			if (RunshopServiceConstants.CHECK_FAIL == status) {
				statusStr = "未通过";
			} else if (RunshopServiceConstants.NO_PASS == status) {
				statusStr = "需修改";
			} else if (RunshopServiceConstants.WAITING_CHECK == status) {
				statusStr = "审核中";
			}

			for (RunShopSimpleDto myRunShop : runShopList) {
				myRunShopList.add(new MyRunShopSimpleDto(myRunShop.getId(), myRunShop.getStore_name(), myRunShop
						.getBoss(), mobile, myRunShop.getCreated_at(), statusStr, status));
			}
		}

		return myRunShopList;
	}

	@Override
	public List<MyRunShopSimpleDto> listMyRunshopForH5(String mobile) throws ServiceException, ServiceException {
		logger.info("RunshopService#listMyRunshopForH5,mobile:{}", mobile);
		List<RunShopSimpleDto> runShopList = runshopMapper.listMyRunshopInKaidianH5(mobile);

		List<MyRunShopSimpleDto> myRunShopList = new ArrayList<>();

		Map<Integer, List<RunShopSimpleDto>> status2runshopMap = new HashMap<>();
		status2runshopMap.put(RunshopServiceConstants.WAITING_CHECK, new ArrayList<>());
		status2runshopMap.put(RunshopServiceConstants.CHECK_PASS, new ArrayList<>());
		status2runshopMap.put(RunshopServiceConstants.CHECK_FAIL, new ArrayList<>());
		status2runshopMap.put(RunshopServiceConstants.NO_PASS, new ArrayList<>());

		for (RunShopSimpleDto myRunShop : runShopList) {

			if (RunshopServiceConstants.CHECK_FAIL == myRunShop.getStatus()) {
				status2runshopMap.get(RunshopServiceConstants.CHECK_FAIL).add(myRunShop);
			} else if (RunshopServiceConstants.NO_PASS == myRunShop.getStatus()) {
				status2runshopMap.get(RunshopServiceConstants.NO_PASS).add(myRunShop);

			} else if (RunshopServiceConstants.WAITING_CHECK == myRunShop.getStatus()) {
				status2runshopMap.get(RunshopServiceConstants.WAITING_CHECK).add(myRunShop);

			} else if (RunshopServiceConstants.CHECK_PASS == myRunShop.getStatus()) {
				status2runshopMap.get(RunshopServiceConstants.CHECK_PASS).add(myRunShop);
			}
		}
		// 按状态，按时间 排序
		sortForRunShopH5(status2runshopMap);
		for (RunShopSimpleDto runshop : status2runshopMap.get(RunshopServiceConstants.NO_PASS)) {
			MyRunShopSimpleDto oneRunShop = new MyRunShopSimpleDto(runshop.getId(), runshop.getStore_name(),
					runshop.getBoss(), mobile, runshop.getCreated_at(), "需修改", RunshopServiceConstants.NO_PASS);
			myRunShopList.add(oneRunShop);
		}
		for (RunShopSimpleDto runshop : status2runshopMap.get(RunshopServiceConstants.WAITING_CHECK)) {
			MyRunShopSimpleDto oneRunShop = new MyRunShopSimpleDto(runshop.getId(), runshop.getStore_name(),
					runshop.getBoss(), mobile, runshop.getCreated_at(), "审核中", RunshopServiceConstants.WAITING_CHECK);
			myRunShopList.add(oneRunShop);
		}
		for (RunShopSimpleDto runshop : status2runshopMap.get(RunshopServiceConstants.CHECK_PASS)) {
			MyRunShopSimpleDto oneRunShop = new MyRunShopSimpleDto(runshop.getId(), runshop.getStore_name(),
					runshop.getBoss(), mobile, runshop.getCreated_at(), "通过", RunshopServiceConstants.CHECK_PASS);
			myRunShopList.add(oneRunShop);
		}
		for (RunShopSimpleDto runshop : status2runshopMap.get(RunshopServiceConstants.CHECK_FAIL)) {
			MyRunShopSimpleDto oneRunShop = new MyRunShopSimpleDto(runshop.getId(), runshop.getStore_name(),
					runshop.getBoss(), mobile, runshop.getCreated_at(), "未通过", RunshopServiceConstants.CHECK_FAIL);
			myRunShopList.add(oneRunShop);
		}

		return myRunShopList;
	}

	private void sortForRunShopH5(Map<Integer, List<RunShopSimpleDto>> status2runshopMap) {
		Collections.sort(status2runshopMap.get(RunshopServiceConstants.WAITING_CHECK), (arg0, arg1) -> arg1
				.getCreated_at().compareTo(arg0.getCreated_at()));
		Collections.sort(status2runshopMap.get(RunshopServiceConstants.CHECK_PASS), (arg0, arg1) -> arg1
				.getCreated_at().compareTo(arg0.getCreated_at()));
		Collections.sort(status2runshopMap.get(RunshopServiceConstants.CHECK_FAIL), (arg0, arg1) -> arg1
				.getCreated_at().compareTo(arg0.getCreated_at()));
		Collections.sort(status2runshopMap.get(RunshopServiceConstants.NO_PASS), (arg0, arg1) -> arg1.getCreated_at()
				.compareTo(arg0.getCreated_at()));
	}

	@Override
	public void noPass(int id, Map<String, String> result_map, int userId) throws ServerException, ServiceException {
		logger.info("RunshopService#noPass,id:{},result_map:{}", id, result_map);
		String result_map_str = "";
		for (String key : result_map.keySet()) {
			if (!StringUtils.isEmpty(result_map.get(key))) {
				if (StringUtils.isEmpty(result_map_str)) {
					result_map_str = result_map.get(key);
				} else {
					result_map_str = result_map_str + ";" + result_map.get(key);
				}
			}

		}
		logger.info("RunshopService#noPass,result_map_str:{}", result_map_str);
		RunshopApplicationInfo runshop = runshopMapper.getRunshopApplicationInfoById(id);
		logger.info("RunshopService#noPass,runshop:{}", runshop);
		if (runshop == null) {
			logger.info("RunshopService#noPass,runshop ==null");
			throw ExceptionUtil.createServerException(ExceptionCode.GET_RUNSHOP_BY_ID_NULL);
		}
		try {
			runshopMapper.runshopNoPass(id, JsonHelper.toJsonString(result_map));
		} catch (JsonProcessingException e1) {
			logger.error("runshopMapper.runshopNoPass,JsonHelper.toJsonString,e:{}", e1);
		}

		String reason = "";
		if (result_map.keySet().contains("base_info")) {
			reason = reason + "基本信息：" + result_map.get("base_info") + ";";
		}
		if (result_map.keySet().contains("identity_info")) {
			reason = reason + "身份信息：" + result_map.get("identity_info") + ";";
		}
		if (result_map.keySet().contains("business_license_info")) {
			reason = reason + "营业执照：" + result_map.get("business_license_info") + ";";
		}
		if (result_map.keySet().contains("catering_services_license_info")) {
			reason = reason + "许可证：" + result_map.get("catering_services_license_info") + ";";
		}
		if (result_map.keySet().contains("other_info")) {
			reason = reason + "其他：" + result_map.get("other_info");
		}
		// 短信通知8：kaidian.ele.me提交，通知商户
		if (runshop.getSource() == RunshopServiceConstants.SOURCE_KAIDIAN) {
			try {
				hermesClient.runshopNoPassMessage(runshop.getMobile(), reason.replaceAll("\\n", "\\\\n"));
			} catch (Exception e) {
				logger.info("hermesClient.runshopNoPassMessage,失败,e:{}", e);
			}
		}
		// eve通知9：开店报备提交，通知员工

		if (runshop.getSource() == RunshopServiceConstants.SOURCE_BD) {
			try {
				EveMessageForm messageForm = new EveMessageForm();
				messageForm.setTitle("您报备的门店被退回，请修改！");
				String content = String.format(ProjectConstant.EVE_RUNSHOP_UNQUALIFIED_MESSAGE_TMP,
						runshop.getStore_name(), runshop.getAddress(), runshop.getMobile(), reason);
				messageForm.setContent(content);
				List<Integer> userIdList = new ArrayList<>(1);
				userIdList.add(runshop.getUser_id());
				messageForm.setUserIdList(userIdList);
				messageForm.setBuss_type(ProjectConstant.RUNSHOP_EVE_MESSAGE_BUSS_TYPE);
				messageService.pushMessage(messageForm);
			} catch (Exception e) {
				logger.info("messageService.pushMessage,失败,e:{}", e);
			}

		}

		// 日志
		RunshopOperateRecord runshopOperateRecord = new RunshopOperateRecord();
		runshopOperateRecord.setRunshop_id(id);
		runshopOperateRecord.setCoffee_user_id(userId);
		User user = coffeeHrClientService.getUserById(userId);
		runshopOperateRecord.setCoffee_user_email(user.getEmail());
		runshopOperateRecord.setCoffee_user_name(user.getName());
		String operateData = "开店申请被退回，原因:\n";
		if (result_map.keySet().contains("base_info")) {
			operateData = operateData + "基本信息：" + result_map.get("base_info") + "\n";
		}
		if (result_map.keySet().contains("identity_info")) {
			operateData = operateData + "身份信息：" + result_map.get("identity_info") + "\n";
		}
		if (result_map.keySet().contains("business_license_info")) {
			operateData = operateData + "营业执照：" + result_map.get("business_license_info") + "\n";
		}
		if (result_map.keySet().contains("catering_services_license_info")) {
			operateData = operateData + "许可证：" + result_map.get("catering_services_license_info") + "\n";
		}
		if (result_map.keySet().contains("other_info")) {
			operateData = operateData + "其他：" + result_map.get("other_info") + "\n";
		}

		runshopOperateRecord.setOperate_data(operateData);
		runshopOperateRecord.setOperate_type(RunshopOperateType.NO_PASS.getValue());
		try {
			runshopOperateRecordMapper.insertRunshopOperateRecord(runshopOperateRecord);
		} catch (Exception e) {
			logger.error("RunshopService#noPass#insertRunshopOperateRecord error", e);
		}

	}

	@Override
	public boolean isMobileAllow(String mobile) throws ServiceException, ServiceException {

		List<Restaurant> list = new ArrayList<>();
		try {
			list = restaurantService.getRestaurantsByMobile(mobile);
		} catch (Exception e) {
			logger.info("IRunshopService#isMobileAllow,来自napos的异常,e:{}", e);
			return false;
		}
		if (list != null && list.size() > 0) {
			logger.info("IRunshopService#isMobileAllow,list.size>0");
			return false;
		}
		return true;
	}

	@Override
	public boolean isMobileAllow2(String mobile) throws ServiceException, ServiceException {
		// 以上确定没有没有绑定的管理员，下面确定没有待申请的开店申请
		List<RunshopApplicationInfo> runshopList = runshopMapper.listRunShopByMobile(mobile);
		for (RunshopApplicationInfo runShop : runshopList) {
			if ((runShop.getIs_delete() == 0)
					& (runShop.getStatus() == 0 || runShop.getStatus() == 1 || runShop.getStatus() == 3)) {
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean isInWaiting(String mobile) throws ServiceException, ServiceException {
		List<RunshopApplicationInfo> runshopList = runshopMapper.listRunShopByMobile(mobile);
		for (RunshopApplicationInfo runShop : runshopList) {
			if ((runShop.getIs_delete() == 0) & (runShop.getStatus() == 0)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public PaginationResponse<RunshopApplicationInfo> listRunshopByCondition(RunshopCondition runshopCondition)
			throws ServiceException, ServerException {
		if (null == runshopCondition) {
			throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_LIST_FORM_NULL);
		}
		if (null == runshopCondition.getBegin_date()) {
			throw ExceptionUtil.createServiceException(ExceptionCode.BEGIN_DATE_NULL);
		}
		if (null == runshopCondition.getEnd_date()) {
			throw ExceptionUtil.createServiceException(ExceptionCode.END_DATE_NULL);
		}
		int total;
		List<RunshopApplicationInfo> list;
		try {
			total = runshopMapper.countRunshopByCondition(runshopCondition);
		} catch (Exception e) {
			logger.error("RunshopService#countRunshop error", e);
			throw ExceptionUtil.createServerException(ExceptionCode.COUNT_RUNSHOP_ERROR);
		}

		try {
			if (runshopCondition.getStatus() == RunshopServiceConstants.WAITING_CHECK) {
				list = runshopMapper.listRunshopByCondition(runshopCondition);
			} else {
				list = runshopMapper.listRunshopByConditionDesc(runshopCondition);

			}
		} catch (Exception e) {
			logger.error("RunshopService#listRunshopByStoreNameOrMobile error", e);
			throw ExceptionUtil.createServerException(ExceptionCode.LIST_RUNSHOP_BY_STORENAME_OR_MOBILE_ERROR);
		}
		PaginationResponse<RunshopApplicationInfo> result = new PaginationResponse<>();
		result.setList(list);
		result.setTotal(total);
		return result;
	}

	@Override
	public Map<Integer, Integer> myRunshopCount(String mobile) throws ServerException, ServiceException {
		Map<Integer, Integer> myRunshopCount = new HashMap<>();
		myRunshopCount.put(0, 0);
		myRunshopCount.put(1, 0);
		myRunshopCount.put(2, 0);
		myRunshopCount.put(3, 0);
		List<MyRunShopCountDto> resultList = runshopMapper.myRunShopCountInKaidian(mobile);
		logger.info("RunshopService#myRunshopCount,resultList:{}", resultList);
		for (MyRunShopCountDto result : resultList) {
			myRunshopCount.put(result.getStatus(), result.getCount());
		}
		return myRunshopCount;
	}

	@Override
	public int updateRunshop(RunshopUpdateForm runshopUpdateForm) throws ServiceException, ServerException {
		if (runshopUpdateForm.getStore_name().contains(ProjectConstant.RUNSHOP_NAME_NOT_ALLOW)
				|| runshopUpdateForm.getStore_name().contains(ProjectConstant.RUNSHOP_NAME_NOT_ALLOW_1))
			throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_NAME_NOT_ALLOW);
		/* 数据校验 */
		if (null == runshopUpdateForm) {
			throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_INSERT_FORM_NULL);
		}
		// 城市name字段补充
		RegionDto city = saturnCityService.getCityById(runshopUpdateForm.getCity_id());
		if (city != null) {
			runshopUpdateForm.setCity_name(city.getName());
		}

		ImageEntity identityPositiveImageEntity = runshopUpdateForm.getIdentity_positive_pic();
		if (StringUtils.isBlank(identityPositiveImageEntity.getUrl())
				|| StringUtils.isBlank(identityPositiveImageEntity.getHash())) {
			throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_INSERT_IDENTITY_POSITIVE_PIC_NULL);
		}

		ImageEntity identityOppositeImageEntity = runshopUpdateForm.getIdentity_opposite_pic();
		// 对大陆，港澳，台湾是必填
		if ((runshopUpdateForm.getCredentials_type() == RunshopServiceConstants.MAINLAND
				|| runshopUpdateForm.getCredentials_type() == RunshopServiceConstants.HONGONG_AND_MACAO || runshopUpdateForm
				.getCredentials_type() == RunshopServiceConstants.TAIWAN)
				&& (StringUtils.isBlank(identityOppositeImageEntity.getUrl()) || StringUtils
						.isBlank(identityOppositeImageEntity.getHash()))) {
			throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_INSERT_IDENTITY_OPPOSITE_PIC_NULL);
		}
		String business_license_time = runshopUpdateForm.getBusiness_license_time();
		if (StringUtils.isNotBlank(business_license_time)) {
			if (!business_license_time.equals(ProjectConstant.BUSINESS_LICENSE_NO_EXPIRE_TIME)) {
				if (!business_license_time.matches(ProjectConstant.DATE_FORMAT_REGULAR)) {
					throw ExceptionUtil.createServiceException(ExceptionCode.INSERT_RUNSHOP_ISSUE_TIME_TYPE_ERROR);
				}
				boolean isBeforeToday;
				try {
					isBeforeToday = DateUtil.isBeforeToday(business_license_time, ProjectConstant.DATE_FORMAT);
				} catch (ParseException e) {
					logger.error("RunshopService#insertRunshop[check is before today] error", e);
					throw ExceptionUtil.createServiceException(ExceptionCode.INSERT_RUNSHOP_ISSUE_TIME_TYPE_ERROR);
				}
				if (isBeforeToday) {
					logger.error(
							"RunshopService#insertRunshop[business license time before today], business license time is:{}",
							business_license_time);
					throw ExceptionUtil
							.createServiceException(ExceptionCode.INSERT_RUNSHOP_BUSINESS_LICENSE_TIME_BEFORE_NOW_ERROR);
				}
			}
		}
		String catering_services_license_time = runshopUpdateForm.getCatering_services_license_time();
		if (StringUtils.isNotBlank(catering_services_license_time)) {
			if (!catering_services_license_time.matches(ProjectConstant.DATE_FORMAT_REGULAR)) {
				throw ExceptionUtil
						.createServiceException(ExceptionCode.INSERT_RUNSHOP_CATERING_SERVICES_LICENSE_TIME_TYPE_ERROR);
			}
			boolean isBeforeToday;
			try {
				isBeforeToday = DateUtil.isBeforeToday(catering_services_license_time, ProjectConstant.DATE_FORMAT);
			} catch (ParseException e) {
				logger.error("RunshopService#insertRunshop[check is before today] error", e);
				throw ExceptionUtil.createServiceException(ExceptionCode.INSERT_RUNSHOP_ISSUE_TIME_TYPE_ERROR);
			}
			if (isBeforeToday) {
				logger.error(
						"RunshopService#insertRunshop[catering services license time], catering services license time is:{}",
						catering_services_license_time);
				throw ExceptionUtil
						.createServiceException(ExceptionCode.INSERT_RUNSHOP_CATERING_SERVICES_LICENSE_TIME_BEFORE_NOW_ERROR);
			}
		}
		/* 保存创建的开店申请数据 */
		RunshopApplicationInfo runshopApplicationInfo = new RunshopApplicationInfo();
		runshopApplicationInfo.setId(runshopUpdateForm.getId());
		if (!Strings.isNullOrEmpty(runshopUpdateForm.getExt_phone())) {
			runshopApplicationInfo.setExt_phone(runshopUpdateForm.getExt_phone());
		}else {
			runshopApplicationInfo.setExt_phone("");
		}
		runshopApplicationInfo.setStore_name(runshopUpdateForm.getStore_name());
		runshopApplicationInfo.setProvince_id(runshopUpdateForm.getProvince_id());
		runshopApplicationInfo.setCity_id(runshopUpdateForm.getCity_id());
		runshopApplicationInfo.setDistrict_id(runshopUpdateForm.getDistrict_id());
		runshopApplicationInfo.setCity_name(runshopUpdateForm.getCity_name());
		runshopApplicationInfo.setAddress(runshopUpdateForm.getAddress());
		runshopApplicationInfo.setBusiness_license_type(runshopUpdateForm.getBusiness_license_type());
		runshopApplicationInfo.setCatering_services_license_type(runshopUpdateForm.getCatering_services_license_type());
		/* 将门店分类的id转成json string */
		String store_classification_id;
		try {
			store_classification_id = JsonHelper.toJsonString(runshopUpdateForm.getStore_classification_id());
		} catch (JsonProcessingException e) {
			logger.error("RunshopService#insertRunshop[toJsonString] error", e);
			throw ExceptionUtil.createServerException(ExceptionCode.RUNSHOP_INSERT_JSON_PARSE_ERROR);
		}
		runshopApplicationInfo.setStore_classification_id(store_classification_id);
		String store_classification;
		try {
			store_classification = JsonHelper.toJsonString(runshopUpdateForm.getStore_classification());
		} catch (JsonProcessingException e) {
			logger.error("RunshopService#createRunshop to json string error", e);
			throw ExceptionUtil.createServerException(ExceptionCode.RUNSHOP_INSERT_JSON_PARSE_ERROR);
		}
		runshopApplicationInfo.setStore_classification(store_classification);
		runshopApplicationInfo.setLatitude(runshopUpdateForm.getLatitude());
		runshopApplicationInfo.setLongitude(runshopUpdateForm.getLongitude());
		runshopApplicationInfo.setMobile(runshopUpdateForm.getMobile());
		runshopApplicationInfo.setThird_party_platform_url(runshopUpdateForm.getThird_party_platform_url());
		runshopApplicationInfo.setDistribution_way(runshopUpdateForm.getDistribution_way());
		runshopApplicationInfo.setBoss(runshopUpdateForm.getBoss());
		runshopApplicationInfo.setBusiness_license_name(runshopUpdateForm.getBusiness_license_name());
		runshopApplicationInfo.setIdentity_number(runshopUpdateForm.getIdentity_number());
		runshopApplicationInfo.setBusiness_license_num(runshopUpdateForm.getBusiness_license_num());
		runshopApplicationInfo.setBusiness_license_address(runshopUpdateForm.getBusiness_license_address());
		runshopApplicationInfo.setBusiness_license_time(business_license_time);
		runshopApplicationInfo.setCatering_services_license_name(runshopUpdateForm.getCatering_services_license_name());
		runshopApplicationInfo.setCatering_services_license_address(runshopUpdateForm
				.getCatering_services_license_address());
		runshopApplicationInfo.setCatering_services_license_num(runshopUpdateForm.getCatering_services_license_num());
		runshopApplicationInfo.setCatering_services_license_time(catering_services_license_time);
		runshopApplicationInfo.setCredentials_type(runshopUpdateForm.getCredentials_type());
		runshopApplicationInfo.setCredentials_time(runshopUpdateForm.getCredentials_time());
		try {
			runshopMapper.updateRunShop(runshopApplicationInfo);
		} catch (Exception e) {
			logger.error("RunshopService#createRunshop error", e);
			throw ExceptionUtil.createServerException(ExceptionCode.RUNSHOP_INSERT_ERROR);
		}
		/* 保存开店申请图片信息 */
		int runshop_id = runshopApplicationInfo.getId();
		/* 需要创建的图片列表 */
		List<RunshopApplicationPicture> runshopApplicationPictureList = new ArrayList<>();
		/* 身份证正面照 */
		RunshopApplicationPicture identityPositivePic = new RunshopApplicationPicture(runshop_id,
				ProjectConstant.IDENTITY_POSITIVE_PIC_CODE, identityPositiveImageEntity.getUrl(),
				identityPositiveImageEntity.getHash());
		runshopApplicationPictureList.add(identityPositivePic);
		// 身份证反面照 对大陆，港澳，台湾填充
		if ((runshopUpdateForm.getCredentials_type() == RunshopServiceConstants.MAINLAND
				|| runshopUpdateForm.getCredentials_type() == RunshopServiceConstants.HONGONG_AND_MACAO || runshopUpdateForm
				.getCredentials_type() == RunshopServiceConstants.TAIWAN)
				&& (StringUtils.isNotBlank(identityOppositeImageEntity.getUrl()) || StringUtils
						.isNotBlank(identityOppositeImageEntity.getHash()))) {
			RunshopApplicationPicture identityOppositePic = new RunshopApplicationPicture(runshop_id,
					ProjectConstant.IDENTITY_OPPOSITE_PIC_CODE, identityOppositeImageEntity.getUrl(),
					identityOppositeImageEntity.getHash());
			runshopApplicationPictureList.add(identityOppositePic);

		}
		if (null != runshopUpdateForm.getDoor_pics()) {
			/* 门头照 */
			for (int i = 0; i < runshopUpdateForm.getDoor_pics().size(); i++) {
				ImageEntity doorPicUrl = runshopUpdateForm.getDoor_pics().get(i);
				RunshopApplicationPicture doorPic = new RunshopApplicationPicture(runshop_id,
						ProjectConstant.DOOR_PIC_CODE, doorPicUrl.getUrl(), doorPicUrl.getHash());
				runshopApplicationPictureList.add(doorPic);
			}
		}
		if (null != runshopUpdateForm.getStore_pics()) {
			/* 店内照 */
			for (int i = 0; i < runshopUpdateForm.getStore_pics().size(); i++) {
				ImageEntity storePicUrl = runshopUpdateForm.getStore_pics().get(i);
				RunshopApplicationPicture storePic = new RunshopApplicationPicture(runshop_id,
						ProjectConstant.STORE_PIC_CODE, storePicUrl.getUrl(), storePicUrl.getHash());
				runshopApplicationPictureList.add(storePic);
			}
		}

		// 校验两证

		String cityStrForCateringServices = HuskarHandle.get().getMyConfig()
				.getProperty(CITIES_CATERING_SERVICES_NOT_REQUIRED);
		String cityStrForBusinessLicense = HuskarHandle.get().getMyConfig()
				.getProperty(CITIES_BUSINESS_LICENSE_NOT_REQUIRED);
		cityStrForCateringServices = cityStrForCateringServices == null ? "" : cityStrForCateringServices;
		cityStrForBusinessLicense = cityStrForBusinessLicense == null ? "" : cityStrForBusinessLicense;
		List<String> cityIdListForBusinessLicense = Arrays.asList(cityStrForBusinessLicense.split(","));
		List<String> cityIdListForCateringServices = Arrays.asList(cityStrForCateringServices.split(","));
		if (!cityIdListForBusinessLicense.contains(runshopUpdateForm.getCity_id() + "")) {
			validateCertificateBusinessLicensePic(runshopUpdateForm);
		}
		if (!cityIdListForCateringServices.contains(runshopUpdateForm.getCity_id() + "")) {
			validateCertificateCaterintServiceLicense(runshopUpdateForm);
		}

		/* 营业执照正面 */
		ImageEntity businessLicensePicImageEntity = runshopUpdateForm.getBusiness_license_pic();
		if (null != businessLicensePicImageEntity && StringUtils.isNotBlank(businessLicensePicImageEntity.getUrl())
				&& StringUtils.isNotBlank(businessLicensePicImageEntity.getHash())) {
			RunshopApplicationPicture businessLicensePic = new RunshopApplicationPicture(runshop_id,
					ProjectConstant.BUSINESS_LICENSE_PIC_CODE, businessLicensePicImageEntity.getUrl(),
					businessLicensePicImageEntity.getHash());
			runshopApplicationPictureList.add(businessLicensePic);
		}
		/* 服务许可证正面 */
		ImageEntity cateringServicePicImageEntity = runshopUpdateForm.getCatering_services_pic();
		if (null != cateringServicePicImageEntity && StringUtils.isNotBlank(cateringServicePicImageEntity.getUrl())
				&& StringUtils.isNotBlank(cateringServicePicImageEntity.getHash())) {
			RunshopApplicationPicture cateringServicesPic = new RunshopApplicationPicture(runshop_id,
					ProjectConstant.CATERING_SERVICES_PIC_CODE, cateringServicePicImageEntity.getUrl(),
					cateringServicePicImageEntity.getHash());
			runshopApplicationPictureList.add(cateringServicesPic);
		}
		try {
			// 删除，再新增
			runshopPictureMapper.batchDeleteByRunShopId(runshopUpdateForm.getId());
			runshopPictureMapper.batchCreateRunshopPicture(runshopApplicationPictureList);
		} catch (Exception e) {
			logger.error("RunshopService#insertRunshop[batchCreateRunshopPicture] error", e);
			throw ExceptionUtil.createServerException(ExceptionCode.RUNSHOP_INSERT_RUNSHOP_PIC_ERROR);
		}
		insertRunshopOperateRecord(runshop_id, ProjectConstant.CREATE_RUNSHOP_RECORD_OPERATOR_ID, "",
				ProjectConstant.CREATE_RUNSHOP_RECORD_OPERATOR_NAME, ProjectConstant.CREATE_RUNSHOP_RECORD_MESSAGE,
				RunshopOperateType.UPDATE.getValue());
		return runshop_id;
	}

	/**
	 * 根据特定URL和手机号获取图片流
	 *
	 * @param picUrl
	 * @param mobile
	 * @return
	 * @throws Exception
	 */
	@Override
	public String getSafePic(String picUrl, String mobile) throws Exception {
		logger.info("RunshopService.getSafePic start with: picUrl: {}, mobile: {}", picUrl, mobile);

		try {
			if (StringUtils.isBlank(picUrl) || StringUtils.isBlank(mobile)) {
				logger.info("RunshopService.getSafePic invalid input!!");
				throw new Exception();
			}

			if (isPicOwner(mobile, picUrl)) {
				// TODO: get pic from fuss and return with base 64
				String imageHash = imageUrlToHash(picUrl);
				ByteBuffer base64 = fussWalleService.fileDownload(imageHash);
				String result = Base64Util.encode(base64.array());
				// 最后一个.的位置
				int lastIndex = picUrl.lastIndexOf(".");
				if (lastIndex == 0) {
					return "";
				}
				String imageType = picUrl.substring(lastIndex + 1);
				if (StringUtils.isEmpty(imageType)) {
					return "";
				}
				String base64Str = "data:image/" + imageType + ";base64," + result;
				return base64Str;
			} else {
				logger.info("isPicOwner,false");
				throw ExceptionUtil.createServiceException(ExceptionCode.NO_AUTH_GET_PIC);
			}

		} catch (Exception e) {
			logger.error("RunshopService.getSafePic failed at:{}", e);
			throw e;

		}

	}

	@Override
	public boolean isPicOwner(String mobile, String picUrl) {
		logger.info("RunshopService#isPicOwner,mobile:{},picUrl:{}", mobile, picUrl);
		List<RunshopApplicationPicture> rPicList = runshopPictureMapper.getByPicUrl(picUrl);
		logger.info("RunshopService#isPicOwner,rPic:{}", rPicList);
		if (CollectionUtils.isEmpty(rPicList)) {
			return false;
		}
		for (RunshopApplicationPicture rpic : rPicList) {
			int rId = rpic.getRunshop_id();
			RunshopApplicationInfo runshop = runshopMapper.getRunshopApplicationInfoById(rId);
			if (runshop != null && runshop.getMobile().equals(mobile)) {
				logger.info("RunshopService#isPicOwner,runshop:{}", runshop);
				return true;
			}
		}
		return false;

	}

	/**
	 * 根据手机号查询该手机号的用户是否提交过开店申请
	 *
	 * @param mobile
	 * @return 0:未提交开店申请,1:已提交开店申请
	 * @throws ServiceException
	 * @throws ServerException
	 */
	@Override
	public int isRunshopUser(String mobile) {
		logger.info("RunshopService#isRunshopUser into, mobile is:{}", mobile);
		if (null == mobile)
			mobile = "";
		Integer runshopId = 0;
		try {
			runshopId = runshopMapper.getRunshopIdByMobile(mobile);
			if (runshopId == null) {
				runshopId = 0;
			}
		} catch (Exception e) {
			logger.error("RunshopService#isRunshopUser error", e);
		}
		return 0 == runshopId ? 0 : 1;
	}

	@Override
	public List<RunshopApplicationInfo> listApplicationByMobile(String mobile) throws ServiceException,
			ServiceException {
		logger.info("RunshopService#listApplicationByMobile,mobile:{}", mobile);
		List<RunshopApplicationInfo> result = runshopMapper.listRunShopByMobile(mobile);
		if (result == null) {
			return new ArrayList<>();
		}
		return result;
	}

	@Override
	public List<RunShopInfoKFDto> listRunshopForKF(String mobile, String rstName) throws ServiceException,
			ServerException {
		logger.info("RunshopService#listRunshopForKF,mobile:{},rstName:{}", mobile, rstName);
		if (StringUtils.isBlank(mobile) && StringUtils.isBlank(rstName)) {
			throw ExceptionUtil.createServiceException(ExceptionCode.EMPTY_MBILE_AND_RST_NAME);
		}
		mobile = mobile == null ? "" : mobile;
		rstName = rstName == null ? "" : rstName;

		List<RunShopInfoKFDto> resultList = runshopMapper.listRunshopForKF(mobile, rstName);
		logger.debug("RunshopService#listRunshopForKF,resultList.size:{}", resultList.size());
		return resultList;
	}

	@Override
	public String getSafePicthumb(String picUrl, String mobile) throws ServiceException, ServerException, Exception {
		logger.info("RunshopService.getSafePicthumb start with: picUrl: {}, mobile: {}", picUrl, mobile);

		try {
			if (StringUtils.isBlank(picUrl) || StringUtils.isBlank(mobile)) {
				logger.info("RunshopService.getSafePicthumb invalid input!!");
				throw new Exception();
			}

			if (isPicOwner(mobile, picUrl)) {
				// TODO: get pic from fuss and return with base 64
				String imageHash = imageUrlToHash(picUrl);
				ByteBuffer base64 = fussWalleService.fileDownloadSized(imageHash,
						RunshopServiceConstants.suffixThumb300);
				// ByteBuffer base64 = fussWalleService.fileDownload(imageHash);

				String result = Base64Util.encode(base64.array());
				// 最后一个.的位置
				int lastIndex = picUrl.lastIndexOf(".");
				if (lastIndex == 0) {
					return "";
				}
				String imageType = picUrl.substring(lastIndex + 1);
				if (StringUtils.isEmpty(imageType)) {
					return "";
				}
				String base64Str = "data:image/" + imageType + ";base64," + result;
				return base64Str;
			} else {
				logger.info("isPicOwner,false");
				throw ExceptionUtil.createServiceException(ExceptionCode.NO_AUTH_GET_PIC);
			}

		} catch (Exception e) {
			logger.error("RunshopService.getSafePicthumb failed at:{}", e);
			throw e;

		}
	}

	@Override
	public RunShopInfoKFDetailDto getRunshopDetailForKF(int id) throws ServiceException, ServerException {
		logger.info("RunshopService#getRunshopDetailForKF,id:{}", id);
		RunShopInfoKFDetailDto result = new RunShopInfoKFDetailDto();
		RunShopInfoKFDetailSourceDto runshopSourceDetail = runshopMapper.getRunshopDetailForKF(id);
		if (runshopSourceDetail != null) {
			BeanUtils.copyProperties(runshopSourceDetail, result);
			// bd信息
			int userId = runshopSourceDetail.getBdId();
			User user = coffeeHrClientService.getUserById(userId);
			if (user != null) {
				result.setBdName(user.getName());
				result.setBdMobile(user.getMobile() + "");
				List<UserBuRoleDto> userBuRoleList = coffeeHrClientService.getUserBuRole(userId);
				if (!CollectionUtils.isEmpty(userBuRoleList)) {
					UserBuRoleDto userBuRole = userBuRoleList.get(0);
					result.setBdBu(userBuRole.getBu_name());
				}
			}
			List<RunshopOperateRecord> records = listOperateRecord(id);
			result.setRecourds(records);
		}
		return result;
	}

	@Override
	public Map<Integer, String> businessLicenseTypeDicForEve() {
		Map<Integer, String> dic = new HashMap<>();
		dic.put(BusinessLicenseTypeEnum.NORMAL.getValue(), BusinessLicenseTypeEnum.NORMAL.getName());
		dic.put(BusinessLicenseTypeEnum.FLEXIBLE_EMPLOYMENT.getValue(),
				BusinessLicenseTypeEnum.FLEXIBLE_EMPLOYMENT.getName());
		return dic;
	}

	@Override
	public Map<Integer, String> cateringServicesLicenseTypeDicForEve() {
		Map<Integer, String> dic = new HashMap<>();
		dic.put(CateringServicesLicenseTypeEnum.NORMAL.getValue(), CateringServicesLicenseTypeEnum.NORMAL.getName());
		dic.put(CateringServicesLicenseTypeEnum.FoodBusinessLicense.getValue(),
				CateringServicesLicenseTypeEnum.FoodBusinessLicense.getName());
		dic.put(CateringServicesLicenseTypeEnum.FoodDistributionLicense.getValue(),
				CateringServicesLicenseTypeEnum.FoodDistributionLicense.getName());
		dic.put(CateringServicesLicenseTypeEnum.TemporaryFoodBusinessLicese.getValue(),
				CateringServicesLicenseTypeEnum.TemporaryFoodBusinessLicese.getName());
		dic.put(CateringServicesLicenseTypeEnum.NationalIndustrialProductionPermit.getValue(),
				CateringServicesLicenseTypeEnum.NationalIndustrialProductionPermit.getName());
		dic.put(CateringServicesLicenseTypeEnum.SmallShopGradeCer.getValue(),
				CateringServicesLicenseTypeEnum.SmallShopGradeCer.getName());
		dic.put(CateringServicesLicenseTypeEnum.FoodProductionAndProcessingWorkshopPermit.getValue(),
				CateringServicesLicenseTypeEnum.FoodProductionAndProcessingWorkshopPermit.getName());
		dic.put(CateringServicesLicenseTypeEnum.FoodSmallWorkshopProductionLicense.getValue(),
				CateringServicesLicenseTypeEnum.FoodSmallWorkshopProductionLicense.getName());
		dic.put(CateringServicesLicenseTypeEnum.Food_WorkshopsRegistrationCertificate1.getValue(),
				CateringServicesLicenseTypeEnum.Food_WorkshopsRegistrationCertificate1.getName());
		dic.put(CateringServicesLicenseTypeEnum.FoodWorkshopsRegistrationCertificate2.getValue(),
				CateringServicesLicenseTypeEnum.FoodWorkshopsRegistrationCertificate2.getName());
		dic.put(CateringServicesLicenseTypeEnum.FoodWorkshopsProductionApprovalCertificate.getValue(),
				CateringServicesLicenseTypeEnum.FoodWorkshopsProductionApprovalCertificate.getName());
		dic.put(CateringServicesLicenseTypeEnum.SmallShopRegistrationCertificate.getValue(),
				CateringServicesLicenseTypeEnum.SmallShopRegistrationCertificate.getName());
		dic.put(CateringServicesLicenseTypeEnum.FoodVendorsRecordEvidence.getValue(),
				CateringServicesLicenseTypeEnum.FoodVendorsRecordEvidence.getName());
		dic.put(CateringServicesLicenseTypeEnum.SmallShopHealthPermits.getValue(),
				CateringServicesLicenseTypeEnum.SmallShopHealthPermits.getName());
		dic.put(CateringServicesLicenseTypeEnum.FoodVendorsRegistrationCard.getValue(),
				CateringServicesLicenseTypeEnum.FoodVendorsRegistrationCard.getName());
		dic.put(CateringServicesLicenseTypeEnum.FoodBusinessTrueName.getValue(),
				CateringServicesLicenseTypeEnum.FoodBusinessTrueName.getName());
		dic.put(CateringServicesLicenseTypeEnum.SmallShopBusinessLicense.getValue(),
				CateringServicesLicenseTypeEnum.SmallShopBusinessLicense.getName());

		return dic;
	}

	/**
	 * 新建 门店信息对象
	 *
	 * @param storeInfo
	 * @return
	 * @throws ServerException
	 * @throws ServiceException
	 */
	@Override
	public int createStoreInfo(StoreInfo storeInfo) throws ServerException, ServiceException {
		logger.info("RunshopService#createStoreInfo,storeInfo:{}", storeInfo);

		// logo为空时处理
		if (storeInfo.getLogo() == null || StringUtils.isBlank(storeInfo.getLogo().getHash())
				|| StringUtils.isBlank(storeInfo.getLogo().getUrl())) {
			throw ExceptionUtil.createServiceException(ExceptionCode.RST_LOGO_IS_EMPTY);
		}
		if (storeInfo.getLogo_old() == null || StringUtils.isBlank(storeInfo.getLogo_old().getHash())
				|| StringUtils.isBlank(storeInfo.getLogo_old().getUrl())) {
			throw ExceptionUtil.createServiceException(ExceptionCode.RST_LOGO_OLD_IS_EMPTY);

		}

		RunshopApplicationInfo runshop = covertStoreInfoToRunshop(storeInfo);
		// VerifyTask applyInfoTask = VerifyTask.buildApplyBaseTask();
		// applyInfoTask.generateCompareHash(storeInfo);
		// VerifyTask logoTask = VerifyTask.buildLogoTask();
		// logoTask.generateCompareHash(storeInfo);
		// saveTaskCompareHash(Arrays.asList(new
		// VerifyTask[]{applyInfoTask,logoTask}), runshop);

		int result = runshopMapper.createStoreInfo(runshop);
		logger.info("RunshopService#createStoreInfo,runshop:{}", runshop);

		Map<Integer, String> toSaveMap = new HashMap<>();

		// 图片
		/* 需要创建的图片列表 */
		List<RunshopApplicationPicture> runshopApplicationPictureList = new ArrayList<>();
		if (null != storeInfo.getDoor_pic()) {
			/* 门头照 */

			RunshopApplicationPicture doorPic = new RunshopApplicationPicture(runshop.getId(),
					ProjectConstant.DOOR_PIC_CODE, storeInfo.getDoor_pic().getUrl(), storeInfo.getDoor_pic().getHash());
			runshopApplicationPictureList.add(doorPic);

			toSaveMap.put(ProjectConstant.DOOR_VM_PIC_CODE, storeInfo.getDoor_pic().getUrl());
			// saveVMPic(storeInfo.getDoor_pic().getUrl(),ProjectConstant.DOOR_VM_PIC_CODE,runshop.getId());

		}
		if (null != storeInfo.getStore_pic()) {
			/* 店内照 */
			ImageEntity storePicUrl = storeInfo.getStore_pic();
			RunshopApplicationPicture storePic = new RunshopApplicationPicture(runshop.getId(),
					ProjectConstant.STORE_PIC_CODE, storePicUrl.getUrl(), storePicUrl.getHash());
			runshopApplicationPictureList.add(storePic);

			toSaveMap.put(ProjectConstant.STORE_VM_PIC_CODE, storePicUrl.getUrl());
			// saveVMPic(storePicUrl.getUrl(),ProjectConstant.STORE_VM_PIC_CODE,runshop.getId());
		}

		if (null != storeInfo.getOther_pic()) {
			/* 其他照 */
			ImageEntity storeOtherPicUrl = storeInfo.getOther_pic();
			RunshopApplicationPicture storeOtherPic = new RunshopApplicationPicture(runshop.getId(),
					ProjectConstant.STORE_OTHER_PIC_CODE, storeOtherPicUrl.getUrl(), storeOtherPicUrl.getHash());
			runshopApplicationPictureList.add(storeOtherPic);

			toSaveMap.put(ProjectConstant.STORE_OTHER_VM_PIC_CODE, storeOtherPicUrl.getUrl());
			// saveVMPic(storeOtherPicUrl.getUrl(),ProjectConstant.STORE_OTHER_VM_PIC_CODE,runshop.getId());
		}
		if (null != storeInfo.getLogo()) {
			/* logo照 */
			ImageEntity logo = storeInfo.getLogo();
			RunshopApplicationPicture logoPic = new RunshopApplicationPicture(runshop.getId(),
					ProjectConstant.STORE_LOGO, logo.getUrl(), logo.getHash());
			runshopApplicationPictureList.add(logoPic);
		}
		if (null != storeInfo.getLogo_old()) {
			ImageEntity logoOld = storeInfo.getLogo_old();
			RunshopApplicationPicture logoPic = new RunshopApplicationPicture(runshop.getId(),
					ProjectConstant.STORE_LOGO_OLD, logoOld.getUrl(), logoOld.getHash());
			runshopApplicationPictureList.add(logoPic);
		}

		try {
			runshopPictureMapper.batchCreateRunshopPicture(runshopApplicationPictureList);
		} catch (Exception e) {
			logger.error("RunshopService#insertRunshop[batchCreateRunshopPicture] error", e);
			throw ExceptionUtil.createServerException(ExceptionCode.RUNSHOP_INSERT_RUNSHOP_PIC_ERROR);
		}

		runshopAsyncService.saveMarkedPics(logoPath, runshop.getId(), toSaveMap);

		logger.info("RunshopService.createStoreInfo return:{}", runshop.getId());
		return runshop.getId();
	}

	/**
	 * 更新 门店信息对象
	 *
	 * @param storeInfo
	 * @return
	 * @throws ServerException
	 * @throws ServiceException
	 */
	@Override
	public int updateStoreInfo(StoreInfo storeInfo) throws ServerException, ServiceException {
		logger.info("RunshopService#updateStoreInfo,storeInfo:{}", storeInfo);
		RunshopApplicationInfo runshop = covertStoreInfoToRunshop(storeInfo);

		long startTime = System.currentTimeMillis();
		// VerifyTask applyInfoTask = VerifyTask.buildApplyBaseTask();
		// applyInfoTask.generateCompareHash(storeInfo);
		// VerifyTask logoTask = VerifyTask.buildLogoTask();
		// logoTask.generateCompareHash(storeInfo);
		// saveTaskCompareHash(Arrays.asList(new
		// VerifyTask[]{applyInfoTask,logoTask}), runshop);

		// 检查输入的mobile是否是第一次
		// List<RunshopApplicationInfo> runShopList =
		// runshopMapper.listRunShopByMobile(storeInfo.getMobile());
		// logger.info("RunshopService#updateStoreInfo,listRunShopByMobile.size:{}",
		// runShopList.size());
		//

		int result = runshopMapper.updateStoreInfo(runshop);
		long endTime = System.currentTimeMillis();
		logger.info("RunshopService#updateStoreInfo step1 const {}", endTime - startTime);

		// 更新操作是，针对总店变化时，对法人身份证信息，营业执照信息，结算方式信息，进行处理
		syncHeadOffice2Runshop(storeInfo.getRunshop_id(), storeInfo.getHead_id());

		Map<Integer, String> toSaveMap = new HashMap<>();

		// 图片
		/* 需要创建的图片列表 */
		List<RunshopApplicationPicture> runshopApplicationPictureList = new ArrayList<>();
		if (null != storeInfo.getDoor_pic()) {
			/* 门头照 */

			RunshopApplicationPicture doorPic = new RunshopApplicationPicture(runshop.getId(),
					ProjectConstant.DOOR_PIC_CODE, storeInfo.getDoor_pic().getUrl(), storeInfo.getDoor_pic().getHash());
			runshopApplicationPictureList.add(doorPic);

			toSaveMap.put(ProjectConstant.DOOR_VM_PIC_CODE, storeInfo.getDoor_pic().getUrl());
			// saveVMPic(storeInfo.getDoor_pic().getUrl(),ProjectConstant.DOOR_VM_PIC_CODE,runshop.getId());
		}
		if (null != storeInfo.getStore_pic()) {
			/* 店内照 */
			ImageEntity storePicUrl = storeInfo.getStore_pic();
			RunshopApplicationPicture storePic = new RunshopApplicationPicture(runshop.getId(),
					ProjectConstant.STORE_PIC_CODE, storePicUrl.getUrl(), storePicUrl.getHash());
			runshopApplicationPictureList.add(storePic);

			toSaveMap.put(ProjectConstant.STORE_VM_PIC_CODE, storePicUrl.getUrl());
			// saveVMPic(storePicUrl.getUrl(),ProjectConstant.STORE_VM_PIC_CODE,runshop.getId());

		}

		if (null != storeInfo.getOther_pic()) {
			/* 其他照 */
			ImageEntity storeOtherPicUrl = storeInfo.getOther_pic();
			RunshopApplicationPicture storeOtherPic = new RunshopApplicationPicture(runshop.getId(),
					ProjectConstant.STORE_OTHER_PIC_CODE, storeOtherPicUrl.getUrl(), storeOtherPicUrl.getHash());
			runshopApplicationPictureList.add(storeOtherPic);

			toSaveMap.put(ProjectConstant.STORE_OTHER_VM_PIC_CODE, storeOtherPicUrl.getUrl());
			// saveVMPic(storeOtherPicUrl.getUrl(),ProjectConstant.STORE_OTHER_VM_PIC_CODE,runshop.getId());

		}
		if (null != storeInfo.getLogo()) {
			/* logo照 */
			ImageEntity logo = storeInfo.getLogo();
			RunshopApplicationPicture logoPic = new RunshopApplicationPicture(runshop.getId(),
					ProjectConstant.STORE_LOGO, logo.getUrl(), logo.getHash());
			runshopApplicationPictureList.add(logoPic);
		}
		if (null != storeInfo.getLogo_old()) {
			/* logo旧照 */
			ImageEntity logo = storeInfo.getLogo_old();
			RunshopApplicationPicture logoPic = new RunshopApplicationPicture(runshop.getId(),
					ProjectConstant.STORE_LOGO_OLD, logo.getUrl(), logo.getHash());
			runshopApplicationPictureList.add(logoPic);
		}

		try {
			// 删除，再新增
			runshopPictureMapper.batchDeleteByRunShopIdAndPicCode(runshop.getId(), Arrays.asList(
					ProjectConstant.STORE_LOGO, ProjectConstant.DOOR_PIC_CODE, ProjectConstant.STORE_OTHER_PIC_CODE,
					ProjectConstant.STORE_PIC_CODE, ProjectConstant.DOOR_VM_PIC_CODE,
					ProjectConstant.STORE_VM_PIC_CODE, ProjectConstant.STORE_OTHER_VM_PIC_CODE,
					ProjectConstant.STORE_LOGO_OLD));

			runshopPictureMapper.batchCreateRunshopPicture(runshopApplicationPictureList);
		} catch (Exception e) {
			logger.error("RunshopService#insertRunshop[batchCreateRunshopPicture] error", e);
			throw ExceptionUtil.createServerException(ExceptionCode.RUNSHOP_INSERT_RUNSHOP_PIC_ERROR);
		}

		runshopAsyncService.saveMarkedPics(logoPath, runshop.getId(), toSaveMap);

		logger.info("RunshopService.updateStoreInfo return:{}", result);
		return result;
	}

	@Override
	public RunshopV2Dto fillStoreInfo(int runshop_id, String mobile) {
		RunshopV2Dto result = new RunshopV2Dto();

		RunshopApplicationInfo runshopApplicationInfo = runshopMapper.getRunshopApplicationInfoByIdV2(runshop_id,
				mobile);

		if (runshopApplicationInfo.getStep() < RunshopServiceConstants.STEP_STORE_INFO
				&& runshopApplicationInfo.getStep() != RunshopServiceConstants.STEP_DEFAULT_OLD) {
			result.setStore_info(null);
		} else {
			StoreInfo storeInfo = generateStoreInfoFromRunshop(runshopApplicationInfo);
			result.setStore_info(storeInfo);
		}

		result.setStep(runshopApplicationInfo.getStep());
		result.setStatus_code(runshopApplicationInfo.getStatus());
		result.setReason(runshopApplicationInfo.getReason());
		return result;
	}

	/**
	 * 更新 资质信息对象
	 *
	 * @param qualificationInfo
	 * @return
	 * @throws ServerException
	 * @throws ServiceException
	 */
	@Override
	public int updateQualificationInfo(QualificationInfo qualificationInfo) throws ServerException, ServiceException {
		logger.info("RunshopService#updateQualificationInfo,qualificationInfo:{}", qualificationInfo);
		RunshopApplicationInfo runshop = convertQualificationInfoToRunshop(qualificationInfo);
		// VerifyTask legalPersonTask = ApplyInfoTask.buildLegalPersonTask();
		// legalPersonTask.generateCompareHash(qualificationInfo.getIdentity_info());
		// VerifyTask businessLisenceTask =
		// ApplyInfoTask.buildBusinessLisenceTask();
		// businessLisenceTask.generateCompareHash(qualificationInfo.getBusiness_license_info());
		// VerifyTask categeryLisenceTask =
		// ApplyInfoTask.buildCateringServiceLicenseTask();
		// categeryLisenceTask.generateCompareHash(qualificationInfo.getCatering_services_license_info());
		//
		// saveTaskCompareHash(Arrays.asList(new VerifyTask[]{legalPersonTask,
		// businessLisenceTask,categeryLisenceTask}), runshop);
		if (qualificationInfo.getIdentity_info() != null) {
			IdentityInfo identityInfo = qualificationInfo.getIdentity_info();
			BlackListDto blackListDto = blackListMapper.getBlackList(
					BlackListTypeEnum.IDENTITY_CARD.getIndex(), identityInfo.getIdentity_number(), identityInfo.getCredentials_type());
			if (blackListDto != null && blackListDto.getValid()) {
				throw new ServiceException("法定代表人疑似黑名单");
			}
		}
		if (qualificationInfo.getBusiness_license_info() != null) {
			BusinessLicenseInfo businessLicenseInfo = qualificationInfo.getBusiness_license_info();
			BlackListDto blackListDto = blackListMapper.getBlackList(
					BlackListTypeEnum.LICENSE.getIndex(), businessLicenseInfo.getBusiness_license_num(), businessLicenseInfo.getBusiness_license_type());
			if (blackListDto != null && blackListDto.getValid()) {
				throw new ServiceException("主体资质疑似黑名单");
			}
		}
		if (qualificationInfo.getCatering_services_license_info() != null) {
			CateringServiceLicenseInfo cateringServiceLicenseInfo = qualificationInfo.getCatering_services_license_info();
			BlackListDto blackListDto = blackListMapper.getBlackList(
					BlackListTypeEnum.PERMIT.getIndex(), cateringServiceLicenseInfo.getCatering_services_license_num(), cateringServiceLicenseInfo.getCatering_services_license_type());
			if (blackListDto != null && blackListDto.getValid()) {
				throw new ServiceException("行业资质疑似黑名单");
			}
		}
		int result = runshopMapper.updateQualificationInfo(runshop);
		logger.info("exit RunshopService#updateQualificationInfo,qualificationInfo:{}",
				qualificationInfo.getRunshop_id());
		return result;
	}

	@Async
	@Override
	public void updateQualificationInfoPic(QualificationInfo qualificationInfo) throws ServiceException,
			ServerException {

		logger.info("enter#updateQualificationInfoPic,{}", qualificationInfo);

		RunshopApplicationInfo runshop = convertQualificationInfoToRunshop(qualificationInfo);

		Map<Integer, String> toSaveMarkedPicCodeUrlMap = new HashMap<>();

		// 图片
		IdentityInfo identity = qualificationInfo.getIdentity_info();

		BusinessLicenseInfo businessLicenseInfo = qualificationInfo.getBusiness_license_info();

		CateringServiceLicenseInfo cateringServiceLicenseInfo = qualificationInfo.getCatering_services_license_info();

		// 营业执照 和 许可证 必填其一

		if (businessLicenseInfo == null && cateringServiceLicenseInfo == null) {
			logger.error("RunshopService#updateQualificationInfoPic,businessLicenseInfo ==null,cateringServiceLicenseInfo =null");
			throw ExceptionUtil
					.createServerException(ExceptionCode.RUNSHOP_BUSINESS_LICENSE_AND_CATERING_SERVICE_LICENSE_NOT_NULL);
		}

		// ########身份证 检查start ###############
		if (identity == null) {
			throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_INSERT_IDENTITY_POSITIVE_PIC_NULL);
		}
		ImageEntity positive = identity.getIdentity_positive_pic();
		ImageEntity opposit = identity.getIdentity_opposite_pic();
		ImageEntity holdOn = identity.getIdentity_hold_on_pic();

		if (positive == null) {
			throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_INSERT_IDENTITY_POSITIVE_PIC_NULL);
		}
		// 对大陆，是必填 港澳，非必填
		if ((identity.getCredentials_type() == RunshopServiceConstants.MAINLAND)
				&& (opposit == null || StringUtils.isBlank(opposit.getUrl()) || StringUtils.isBlank(opposit.getHash()))) {
			throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_INSERT_IDENTITY_OPPOSITE_PIC_NULL);
		}

		if (holdOn == null) {
			throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_INSERT_IDENTITY_HOLD_ON_PIC_NULL);
		}
		// ########身份证 检查end ###############

		/* 需要创建的图片列表 */
		List<RunshopApplicationPicture> runshopApplicationPictureList = new ArrayList<>();
		/* 身份证正面照 */
		RunshopApplicationPicture identityPositivePic = new RunshopApplicationPicture(runshop.getId(),
				ProjectConstant.IDENTITY_POSITIVE_PIC_CODE, positive.getUrl(), positive.getHash());
		runshopApplicationPictureList.add(identityPositivePic);

		toSaveMarkedPicCodeUrlMap.put(ProjectConstant.IDENTITY_POSITIVE_VM_PIC_CODE, positive.getUrl());
		// saveVMPic(positive.getUrl(),ProjectConstant.IDENTITY_POSITIVE_VM_PIC_CODE,runshop.getId());

		// 身份证反面照 对大陆，港澳，台湾不填充
		boolean identityOppDel = false;
		if ((identity.getCredentials_type() == RunshopServiceConstants.MAINLAND) && opposit != null
				&& (StringUtils.isNotBlank(opposit.getUrl()) || StringUtils.isNotBlank(opposit.getHash()))) {
			RunshopApplicationPicture identityOppositePic = new RunshopApplicationPicture(runshop.getId(),
					ProjectConstant.IDENTITY_OPPOSITE_PIC_CODE, opposit.getUrl(), opposit.getHash());
			runshopApplicationPictureList.add(identityOppositePic);
			identityOppDel = true;

			toSaveMarkedPicCodeUrlMap.put(ProjectConstant.IDENTITY_OPPOSITE_VM_PIC_CODE, opposit.getUrl());
			// saveVMPic(opposit.getUrl(),ProjectConstant.IDENTITY_OPPOSITE_VM_PIC_CODE,runshop.getId());

		}

		// 手持
		RunshopApplicationPicture identityHoldOnPic = new RunshopApplicationPicture(runshop.getId(),
				ProjectConstant.DENTITY_HOLD_ON, holdOn.getUrl(), holdOn.getHash());
		runshopApplicationPictureList.add(identityHoldOnPic);
		toSaveMarkedPicCodeUrlMap.put(ProjectConstant.DENTITY_VM_HOLD_ON, holdOn.getUrl());
		// saveVMPic(holdOn.getUrl(),ProjectConstant.DENTITY_VM_HOLD_ON,runshop.getId());
		if (businessLicenseInfo != null) {
			// 营业执照
			ImageEntity businessLicensePicImageEntity = businessLicenseInfo.getBusiness_license_pic();
			if (null != businessLicensePicImageEntity && StringUtils.isNotBlank(businessLicensePicImageEntity.getUrl())
					&& StringUtils.isNotBlank(businessLicensePicImageEntity.getHash())) {
				RunshopApplicationPicture businessLicensePic = new RunshopApplicationPicture(runshop.getId(),
						ProjectConstant.BUSINESS_LICENSE_PIC_CODE, businessLicensePicImageEntity.getUrl(),
						businessLicensePicImageEntity.getHash());

				validateCertificateBusinessLicensePic(businessLicenseInfo);
				runshopApplicationPictureList.add(businessLicensePic);
				toSaveMarkedPicCodeUrlMap.put(ProjectConstant.BUSINESS_LICENSE_VM_PIC_CODE,
						businessLicensePicImageEntity.getUrl());
				// saveVMPic(businessLicensePicImageEntity.getUrl(),ProjectConstant.BUSINESS_LICENSE_VM_PIC_CODE,runshop.getId());

			}
		}

		if (cateringServiceLicenseInfo != null) {
			// 许可证
			ImageEntity cateringServicePicImageEntity = cateringServiceLicenseInfo.getCatering_services_pic();
			if (null != cateringServicePicImageEntity && StringUtils.isNotBlank(cateringServicePicImageEntity.getUrl())
					&& StringUtils.isNotBlank(cateringServicePicImageEntity.getHash())) {
				RunshopApplicationPicture cateringServicesPic = new RunshopApplicationPicture(runshop.getId(),
						ProjectConstant.CATERING_SERVICES_PIC_CODE, cateringServicePicImageEntity.getUrl(),
						cateringServicePicImageEntity.getHash());
				runshopApplicationPictureList.add(cateringServicesPic);
				validateCertificateCaterintServiceLicense(cateringServiceLicenseInfo);

				toSaveMarkedPicCodeUrlMap.put(ProjectConstant.CATERING_SERVICES_VM_PIC_CODE,
						cateringServicePicImageEntity.getUrl());
				// saveVMPic(cateringServicePicImageEntity.getUrl(),ProjectConstant.CATERING_SERVICES_VM_PIC_CODE,runshop.getId());

			}
		}
		try {
			List<Integer> delPicCodes = new LinkedList<>();
			delPicCodes.addAll(Arrays.asList(ProjectConstant.IDENTITY_POSITIVE_PIC_CODE,
					ProjectConstant.DENTITY_HOLD_ON, ProjectConstant.BUSINESS_LICENSE_PIC_CODE,
					ProjectConstant.CATERING_SERVICES_PIC_CODE, ProjectConstant.IDENTITY_POSITIVE_VM_PIC_CODE,
					ProjectConstant.DENTITY_VM_HOLD_ON, ProjectConstant.BUSINESS_LICENSE_VM_PIC_CODE,
					ProjectConstant.CATERING_SERVICES_VM_PIC_CODE));

			if (identityOppDel) {
				delPicCodes.add(ProjectConstant.IDENTITY_OPPOSITE_PIC_CODE);
				delPicCodes.add(ProjectConstant.IDENTITY_OPPOSITE_VM_PIC_CODE);
			}

			logger.info("RunshopService#updateQualificationInfoPic#beforeDeletePic,{}", runshop);
			// 删除，再新增
			runshopCoreService.batchDeleteByRunShopIdAndPicCode(runshop.getId(), delPicCodes);
			runshopCoreService.batchCreateRunshopPicture(runshopApplicationPictureList);
			logger.info("RunshopService#updateQualificationInfoPic#afterDeletePic,{}", runshop);
			logger.info("RunshopService#updateQualificationInfoPic#beforeSaveMarkedPics,{}", runshop);
			runshopAsyncService.saveMarkedPics(logoPath, runshop.getId(), toSaveMarkedPicCodeUrlMap);
			logger.info("RunshopService#updateQualificationInfoPic#afterSaveMarkedPics,{}", runshop);
			// runshopPictureMapper.batchDeleteByRunShopIdAndPicCode(runshop.getId(),
			// delPicCodes);
			// runshopPictureMapper.batchCreateRunshopPicture(runshopApplicationPictureList);

		} catch (Exception e) {
			logger.error("RunshopService#insertRunshop[batchCreateRunshopPicture] error", e);
			throw ExceptionUtil.createServerException(ExceptionCode.RUNSHOP_INSERT_RUNSHOP_PIC_ERROR);
		}
		logger.info("exit RunshopService#updateQualificationInfoPic,qualificationInfo:{}",
				qualificationInfo.getRunshop_id());
	}

	/**
	 * 更新 合作方案信息对象
	 *
	 * @param cooperationInfo
	 * @return
	 */
	@Override
	public int updateCooperationInfo(CooperationInfo cooperationInfo) throws ServiceException, ServiceException {
		RunshopApplicationInfo runshop = convertCooperationToRunshop(cooperationInfo);

		int result = runshopMapper.updateCooperationInfo(runshop);
		return result;
	}

	/**
	 * 更新DeliveryInfo对象
	 *
	 * @param deliveryInfo
	 * @return
	 */
	@Override
	public int saveDeliveryInfo(DeliveryInfo deliveryInfo) throws ServiceException, ServiceException {

		logger.info("RunshopService#updateDeliveryInfo,deliveryInfo:{}", deliveryInfo);
		RunshopV2StautsAndStep statusAndStep = runshopMapper.getRunshopV2StautsAndStep(deliveryInfo.getRunshop_id());
		if (statusAndStep == null) {
			throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_NULL_BY_ID);
		}

		boolean isSkip = isSkip(statusAndStep.getStep(), RunshopServiceConstants.STEP_DELIVERY_INFO);
		if (isSkip) {
			throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_IS_SKIP);
		}
		if (deliveryInfo.getRunshop_id() != 0) {
			runshopMapper.deleteDeliveryListByRunshopId(deliveryInfo.getRunshop_id());
		}
		List<Delivery> list = deliveryInfo.getDeliveryList();
		if (!CollectionUtils.isEmpty(list)) {
			for (Delivery delivery : list) {
				delivery.setRunshop_id(deliveryInfo.getRunshop_id());
				runshopMapper.createDelivery(delivery);
			}
		}

		if (RunshopServiceConstants.STEP_DELIVERY_INFO > statusAndStep.getStep()) {
			runshopMapper.updateStepOnly(deliveryInfo.getRunshop_id(), RunshopServiceConstants.STEP_DELIVERY_INFO);
		}
		return 0;
	}

	/**
	 * 更新settlementInfo对象
	 *
	 * @param settlementInfo
	 * @return
	 */
	@Override
	public int updateSettlementInfo(SettlementInfo settlementInfo) throws ServiceException, ServiceException {

		SettlementInfoDto settlementInfoDto = convertSettlementInfoToSettlementInfoDto(settlementInfo);

		int result = runshopMapper.updateSettlementInfo(settlementInfoDto);

		return result;
	}

	public void saveTaskCompareHash(List<VerifyTask> taskList, RunshopApplicationInfo info) {

		String compareHash = info.getCompare_hash();
		Map<Integer, Integer> map = Maps.newHashMap();
		if (!StringUtils.isEmpty(compareHash)) {
			try {
				map = JsonHelper.toJsonObject(compareHash, Map.class, Integer.class, Integer.class);
			} catch (JsonParseException e) {
				e.printStackTrace();
			} catch (JsonMappingException e) {
				e.printStackTrace();
				logger.error("comparehash deserialize failed info {}", info);
			} catch (Exception e) {
				logger.error("comparehash deserialize failed info {}", info);
			}
		}
		for (VerifyTask task : taskList) {
			map.put(task.getAuditProcessEnum().getType(), task.hashCode());
		}
		try {
			info.setCompare_hash(JsonHelper.toJsonString(map));
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}
	}

	/**
	 * 是否跳步:当前步骤比数据库步骤领先2时，认为是跳步
	 *
	 * @param step
	 *            数据库中step
	 * @param stepNow
	 *            当前step
	 * @return
	 */
	private boolean isSkip(int step, int stepNow) {
		if (stepNow - step > 1) {
			return true;
		} else {
			return false;
		}

	}

	private SettlementInfoDto convertSettlementInfoToSettlementInfoDto(SettlementInfo settlementInfo)
			throws ServiceException {

		logger.info("convertSettlementInfoToSettlementInfoDto#settlementInfo:{}", settlementInfo);
		RunshopV2StautsAndStep statusAndStep = runshopMapper.getRunshopV2StautsAndStep(settlementInfo.getRunshop_id());

		if (statusAndStep == null) {
			throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_NULL_BY_ID);

		}
		boolean isSkip = isSkip(statusAndStep.getStep(), RunshopServiceConstants.STEP_SETTLEMENT_INFO);

		if (isSkip) {
			throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_IS_SKIP);
		} else {
			SettlementInfoDto settlementInfoDto = new SettlementInfoDto();

			if (settlementInfo.getInherit() == RunshopServiceConstants.INHERIT_SETTLEMENT) {
				settlementInfo.setSettle_way(RunshopServiceConstants.HEAD_OFFICE_SETTLEMENT);
			} else if (settlementInfo.getInherit() == RunshopServiceConstants.NOT_INHERIT_SETTLEMENT) {
				settlementInfo.setSettle_way(RunshopServiceConstants.BRANCH_SETTLEMENT);
			}

			// 如果bankcard_owner为空,则自动填充boss字段
			if (StringUtils.isBlank(settlementInfo.getBankacard_owner())) {
				logger.info("RunshopService#convertSettlementInfoToSettlementInfoDto,bankcard_owner 为空,填充旧数据");
				RunshopApplicationInfo oldRunshop = runshopMapper.getRunshopApplicationInfoById(settlementInfo
						.getRunshop_id());
				if (oldRunshop != null && oldRunshop.getBoss() != null) {
					settlementInfo.setBankacard_owner(oldRunshop.getBoss());
				}
			}

			BeanUtils.copyProperties(settlementInfo, settlementInfoDto);
			settlementInfoDto.setStep(Math.max(RunshopServiceConstants.STEP_SETTLEMENT_INFO, statusAndStep.getStep()));
			return settlementInfoDto;
		}
	}

	/**
	 * 新建goodInfo对象
	 *
	 * @param
	 * @param
	 * @return
	 */
	@Override
	@Async
	public void saveGoodListInfo(GoodListInfo goodListInfo, String mobile) throws ServerException, ServiceException {
		logger.info("RunshopService.saveGoodListInfo start with:{}, {}", goodListInfo.toString(), mobile);

		RunshopV2StautsAndStep statusAndStep = runshopMapper.getRunshopV2StautsAndStep(goodListInfo.getRunshop_id());
		if (statusAndStep == null) {
			throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_NULL_BY_ID);

		}
		boolean isSkip = isSkip(statusAndStep.getStep(), RunshopServiceConstants.STEP_SETTLEMENT_INFO);

		if (isSkip) {
			throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_IS_SKIP);
		}

		if (goodListInfo != null) {
			List<GoodInfo> list = goodListInfo.getGood_info();
			if (CollectionUtils.isEmpty(list)) {
				return;
			}

			// 全部删除
			List<Integer> delPicCodes = new ArrayList<>();
			delPicCodes.add(ProjectConstant.GOOD_PIC_CODE);

			// 删除，再新增
			runshopPictureMapper.batchDeleteByRunShopIdAndPicCode(goodListInfo.getRunshop_id(), delPicCodes);

			// 查询是否有默认的商品分类
			boolean hasDefaultCatory = false;
			Category defaultCatory = runshopMapper.getRunShopGoodClassificationByRunshopIdAndName(
					goodListInfo.getRunshop_id(), ProjectConstant.DEFAULT_CLASSIFICATION_NAME);
			if (defaultCatory != null) {
				hasDefaultCatory = true;
			}

			List<GoodInfo> oldGoodList = runshopMapper.selectGoodInfo(goodListInfo.getRunshop_id());
			List<Integer> oldGoodIdList = oldGoodList.stream().map(good -> good.getId()).collect(Collectors.toList());
			// goodIdNeedUpdateList 为需要更新的goodid
			List<Integer> goodIdNeedUpdateList = new ArrayList<>();
			for (GoodInfo good : list) {
				good.setRunshop_id(goodListInfo.getRunshop_id());
				if (good.getId() == 0) {

					// 设置默认分类 美食
					// 如果有默认的商品分类，则绑定默认商品分类；如果没有默认商品分类，则新建默认商品分类，然后，绑定默认商品分类

					if (!hasDefaultCatory) {
						List<Category> runShopGoodClassificationList = new ArrayList<>();
						Category defaultCatoryForAdd = new Category(ProjectConstant.DEFAULT_CLASSIFICATION_NAME,
								good.getRunshop_id());
						runShopGoodClassificationList.add(defaultCatoryForAdd);
						runshopMapper.createRunShopGoodClassification(runShopGoodClassificationList);
						defaultCatory = runshopMapper.getRunShopGoodClassificationByRunshopIdAndName(
								goodListInfo.getRunshop_id(), ProjectConstant.DEFAULT_CLASSIFICATION_NAME);
						hasDefaultCatory = true;
					}
					int defaultCatoryId = defaultCatory.getId();
					// 设置默认商品分类id
					good.getGood_classification().setClassification_id(defaultCatoryId);
					// good 图片可以为空
					if (StringUtils.isBlank(good.getGood_pic())) {
						good.setGood_pic("");
					} else {
						good.setGood_pic(good.getGood_pic());
					}

					// 添加商品
					runshopMapper.createGoodInfo(good);

				} else {
					runshopMapper.updateGoodInfo(good);
					goodIdNeedUpdateList.add(good.getId());
				}
				// 重新添加
				RunshopApplicationPicture rap = new RunshopApplicationPicture();
				rap.setIs_delete(0);
				if (StringUtils.isBlank(good.getGood_pic())) {
					rap.setPic_url("");
				} else {
					rap.setPic_url(good.getGood_pic());
				}
				rap.setRunshop_id(goodListInfo.getRunshop_id());
				rap.setPic_code(ProjectConstant.GOOD_PIC_CODE);
				rap.setPic_hash("pichash");
				runshopPictureMapper.batchCreateRunshopPicture(Arrays.asList(rap));
			}

			// oldGoodIdList -goodIdNeedUpdateList 就是需要delete的good
			oldGoodIdList.removeAll(goodIdNeedUpdateList);

			if (!CollectionUtils.isEmpty(oldGoodIdList)) {
				runshopMapper.deleteGoodInfoList(oldGoodIdList);
			}

			if (RunshopServiceConstants.STEP_GOODLIST_INFO > statusAndStep.getStep()) {
				// 创建开店,从待完善状态提交
				RunshopApplicationInfo runshopApplicationInfo = runshopMapper.getRunshopApplicationInfoByIdV2(
						goodListInfo.getRunshop_id(), mobile);

				try {
					ApplyShopBase applyShopBase = createApplyStoreInfo(runshopApplicationInfo, mobile);
					LegalPersonApply legalPersonApply = createLegalApply(runshopApplicationInfo, mobile);
					ShopLicensePermitApply shopLicensePermitApply = createLicensePermitApply(runshopApplicationInfo,
							mobile);

					lMerchantService.createApplyShopBase(applyShopBase,
							runshopApplicationInfo.getSource() == 2 ? runshopApplicationInfo.getUser_id() : 0);
					lAuditService.createLegalApply(legalPersonApply,
							runshopApplicationInfo.getSource() == 2 ? runshopApplicationInfo.getUser_id() : 0);
					lAuditService.createLicensePermitApply(shopLicensePermitApply,
							runshopApplicationInfo.getSource() == 2 ? runshopApplicationInfo.getUser_id() : 0);

					RunshopWorkflowForm form = new RunshopWorkflowForm();

					form.setMobile(String.valueOf(mobile));
					form.setRunshopId(goodListInfo.getRunshop_id());
					form.setAuditProcessEnumSet(Sets.newHashSet(
							me.ele.work.flow.engine.core.constants.AuditProcessEnum.STORE_LOGO_AUDIT,
							me.ele.work.flow.engine.core.constants.AuditProcessEnum.STORE_INFO_AUDIT,
							me.ele.work.flow.engine.core.constants.AuditProcessEnum.MAIN_QUALIFICATIONS_AUDIT,
							me.ele.work.flow.engine.core.constants.AuditProcessEnum.INDUSTRY_QUALIFICATION_AUDIT,
							me.ele.work.flow.engine.core.constants.AuditProcessEnum.LEGAL_REPRESENTATIVE_AUDIT));
					form.setRunshopCreatorEnum(runshopApplicationInfo.getSource() == RunshopServiceConstants.SOURCE_BD ? RunshopCreatorEnum.BD
							: RunshopCreatorEnum.MERCHANT);
					logger.info("start_process_form is {} ", form);

					runshopWorkFlowService.startProcess(form);

					// 修改step为6
					runshopMapper.updateStep(goodListInfo.getRunshop_id(), RunshopServiceConstants.STEP_GOODLIST_INFO);
					// patch, 保证修改状态
					runshopMapper.updateRunshopApplicationInfoStatusById(goodListInfo.getRunshop_id(), 0);
				} catch (Exception e) {
					logger.error("RunshopService#saveGoodListInfo error, ", e);
				}
			} else if (RunshopServiceConstants.STEP_GOODLIST_INFO == statusAndStep.getStep()) {
				// 修改开店,从需修改状态提交
				try {

					RunshopApplicationInfo runshopApplicationInfo = runshopMapper.getRunshopApplicationInfoByIdV2(
							goodListInfo.getRunshop_id(), mobile);

					RunshopWorkflowForm form = new RunshopWorkflowForm();

					form.setMobile(String.valueOf(mobile));
					form.setRunshopId(goodListInfo.getRunshop_id());

					// runshopApplicationInfo.getCompare_hash();

					Set<me.ele.work.flow.engine.core.constants.AuditProcessEnum> reauditEnum = getChangedProcessEnum(
							goodListInfo.getRunshop_id(), mobile);

					logger.info("open_store_apply_shopId {} reaudit process {}", goodListInfo.getRunshop_id(),
							reauditEnum);

					if (reauditEnum.contains(me.ele.work.flow.engine.core.constants.AuditProcessEnum.STORE_LOGO_AUDIT)
							|| reauditEnum
									.contains(me.ele.work.flow.engine.core.constants.AuditProcessEnum.STORE_INFO_AUDIT)) {
						ApplyShopBase applyShopBase = createApplyStoreInfo(runshopApplicationInfo, mobile);

						if (!reauditEnum
								.contains(me.ele.work.flow.engine.core.constants.AuditProcessEnum.STORE_LOGO_AUDIT)) {
							applyShopBase.setShopSqureLogoHash(null);
							applyShopBase.setShopRectangleLogoHash(null);
						}
						if (!reauditEnum
								.contains(me.ele.work.flow.engine.core.constants.AuditProcessEnum.STORE_INFO_AUDIT)) {
							ApplyShopBase applyShopBase1 = new ApplyShopBase();
							applyShopBase1.setApplyId(applyShopBase.getApplyId());
							applyShopBase1.setOperatorId(0);
							applyShopBase1.setShopRectangleLogoHash(applyShopBase.getShopRectangleLogoHash());
							applyShopBase1.setShopSqureLogoHash(applyShopBase.getShopSqureLogoHash());
							applyShopBase = applyShopBase1;
						}
						lMerchantService.createApplyShopBase(applyShopBase,
								runshopApplicationInfo.getSource() == 2 ? runshopApplicationInfo.getUser_id() : 0);

					}

					if (reauditEnum
							.contains(me.ele.work.flow.engine.core.constants.AuditProcessEnum.LEGAL_REPRESENTATIVE_AUDIT)) {
						LegalPersonApply legalPersonApply = createLegalApply(runshopApplicationInfo, mobile);

						lAuditService.createLegalApply(legalPersonApply,
								runshopApplicationInfo.getSource() == 2 ? runshopApplicationInfo.getUser_id() : 0);
					}

					if (reauditEnum
							.contains(me.ele.work.flow.engine.core.constants.AuditProcessEnum.INDUSTRY_QUALIFICATION_AUDIT)
							|| reauditEnum
									.contains(me.ele.work.flow.engine.core.constants.AuditProcessEnum.MAIN_QUALIFICATIONS_AUDIT)) {
						ShopLicensePermitApply shopLicensePermitApply = createLicensePermitApply(
								runshopApplicationInfo, mobile);

						if (!reauditEnum
								.contains(me.ele.work.flow.engine.core.constants.AuditProcessEnum.INDUSTRY_QUALIFICATION_AUDIT)) {
							shopLicensePermitApply.setShopPermit(null);
						}
						if (!reauditEnum
								.contains(me.ele.work.flow.engine.core.constants.AuditProcessEnum.MAIN_QUALIFICATIONS_AUDIT)) {
							shopLicensePermitApply.setShopLicense(null);
						}
						lAuditService.createLicensePermitApply(shopLicensePermitApply,
								runshopApplicationInfo.getSource() == 2 ? runshopApplicationInfo.getUser_id() : 0);

					}

					// if(reauditEnum.contains())
					// form.getAuditProcessEnumSet(getAuditProcessByRunshopId());
					form.setAuditProcessEnumSet(reauditEnum);
					form.setRunshopCreatorEnum(runshopApplicationInfo.getSource() == RunshopServiceConstants.SOURCE_BD ? RunshopCreatorEnum.BD
							: RunshopCreatorEnum.MERCHANT);
					logger.info("start_process_form is {} ", form);
					try {
						runshopWorkFlowService.startProcess(form);
					} catch (Exception e) {
						logger.error("workflow start process error {}", form);
					}

					// 修改step为6
					runshopMapper.updateStep(goodListInfo.getRunshop_id(), RunshopServiceConstants.STEP_GOODLIST_INFO);
					// patch, 保证修改状态
					runshopMapper.updateRunshopApplicationInfoStatusById(goodListInfo.getRunshop_id(), 0);
				} catch (Exception e) {
					logger.error("RunshopService#saveGoodListInfo has error, {}", e);
				}
			}
			updateTaskHash(goodListInfo.getRunshop_id(), mobile);
		}
		return;
	}

	public void syncHeadOffice2Runshop(int runshopId, int headId) throws ServerException, ServiceException {
		logger.info("RunshopService#syncHeadOffice2Runshop into, runshopId is {}", runshopId);
		RunshopApplicationInfo runshop = runshopMapper.getRunshopApplicationInfoById(runshopId);
		if (runshop == null) {
			logger.error("RunshopService#syncHeadOffice2Runshop runshp no found");
			return;
		}

		logger.info("RunshopService#syncHeadOffice2Runshop runshop is {}", runshop);

		if (runshop.getHead_id() == 0) {
			logger.info("RunshopService#syncHeadOffice2Runshop head_id is {}", runshop.getHead_id());
			return;
		}

		HeadOfficeDetailedInfo headOffice = headOfficeService.getHeadOfficeById(headId);
		List<RunshopApplicationPicture> list = new ArrayList<>();

		runshop.setHead_id(headId);

		// 同步总店法人信息
		if (runshop.getIdentity_inherit() == 1) {
			runshop.setCredentials_type(headOffice.getCredentials_type());
			runshop.setBoss(headOffice.getIdentity_name());
			runshop.setIdentity_number(headOffice.getIdentity_num());
			runshop.setIdentity_inherit(1);

			// 设置照片信息
			runshopPictureMapper.deletePicByRunshopIdAndPicCode(runshop.getId(),
					ProjectConstant.IDENTITY_POSITIVE_PIC_CODE);
			runshopPictureMapper.deletePicByRunshopIdAndPicCode(runshop.getId(),
					ProjectConstant.IDENTITY_OPPOSITE_PIC_CODE);
			runshopPictureMapper.deletePicByRunshopIdAndPicCode(runshop.getId(), ProjectConstant.DENTITY_HOLD_ON);

			list.add(new RunshopApplicationPicture(runshop.getId(), ProjectConstant.IDENTITY_POSITIVE_PIC_CODE,
					headOffice.getIdentity_face_pic().getPic_url(), headOffice.getIdentity_face_pic().getPic_hash()));

			if (headOffice.getIdentity_back_pic() != null
					&& StringUtils.isNotBlank(headOffice.getIdentity_back_pic().getPic_url())
					&& StringUtils.isNotBlank(headOffice.getIdentity_back_pic().getPic_hash())) {
				list.add(new RunshopApplicationPicture(runshop.getId(), ProjectConstant.IDENTITY_OPPOSITE_PIC_CODE,
						headOffice.getIdentity_back_pic().getPic_url(), headOffice.getIdentity_back_pic().getPic_hash()));
			}

			list.add(new RunshopApplicationPicture(runshop.getId(), ProjectConstant.DENTITY_HOLD_ON, headOffice
					.getWith_person_pic().getPic_url(), headOffice.getWith_person_pic().getPic_hash()));
		}

		// 同步证件信息
		if (runshop.getBusiness_license_inherit() == 1) {
			runshop.setBusiness_license_type(headOffice.getBusiness_license_type().getValue());
			runshop.setBusiness_license_name(headOffice.getBusiness_license_name());
			runshop.setBusiness_license_num(headOffice.getBusiness_license_num());
			runshop.setBusiness_license_address(headOffice.getBusiness_license_address());
			runshop.setBusiness_license_legal_person(headOffice.getBusiness_license_legal_person());
			if (headOffice.getAlways_valid() == 1) {
				runshop.setBusiness_license_time("长期有效");
			} else {
				runshop.setBusiness_license_time(headOffice.getBusiness_license_time().toLocalDateTime().toLocalDate()
						.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
			}
			runshop.setBusiness_license_inherit(1);

			// 设置照片信息
			headOffice.getBusiness_license_pic();
			runshopPictureMapper.deletePicByRunshopIdAndPicCode(runshop.getId(),
					ProjectConstant.BUSINESS_LICENSE_PIC_CODE);
			list.add(new RunshopApplicationPicture(runshop.getId(), ProjectConstant.BUSINESS_LICENSE_PIC_CODE,
					headOffice.getBusiness_license_pic().getPic_url(), headOffice.getBusiness_license_pic()
							.getPic_hash()));

		}

		// 如果是总店结算,同步总店结算信息
		if (headOffice.getSettlement_way() == SettlementWayEnum.HEAD_OFFICE) {
			logger.info("RunshopService#syncHeadOffice2Runshop in head settlment");
			CardSimpleDto cardInfo = null;
			try {
				cardInfo = bankCardByRstId(headOffice.getId());
			} catch (Exception e) {
				logger.error("H5HeadOfficeService#syncHeadOffice2Runshop bankCardByRstId(" + headOffice.getId()
						+ ") err, ", e);
			}
			if (cardInfo != null) {
				runshop.setSettlement_type(cardInfo.getSettlement_type());
				if (cardInfo.getSettlement_type() == 1) { // 对公
					runshop.setBankcard(cardInfo.getAcct_no());
					runshop.setBankacard_owner(cardInfo.getAcct_name());
				} else if (cardInfo.getSettlement_type() == 2) { // 对私
					runshop.setBankcard(cardInfo.getCard_no());
					runshop.setBankacard_owner(cardInfo.getReal_name());

				}
				runshop.setBank(cardInfo.getBank_name());
				runshop.setBankcard_province_id(cardInfo.getProvince_id());
				runshop.setBankcard_province_name(cardInfo.getProvince_name());
				runshop.setBankcard_city_id(cardInfo.getCity_id());
				runshop.setBankcard_city_name(cardInfo.getCity_name());
				runshop.setBankcard_cycle("");
				runshop.setBranch_banck(cardInfo.getSub_bank_name());
				runshop.setSettlement_inherit(1);
				runshop.setSettle_way(1);
			} else { // 采用本地总店数据
				runshop.setSettlement_type(headOffice.getSettlement_type().getVal());
				runshop.setBankcard(headOffice.getBankcard());
				switch (headOffice.getSettlement_type()) {
				case BUSINESS:
					runshop.setBankacard_owner("");
					break;
				case PRIVATE:
					runshop.setBankacard_owner(headOffice.getIdentity_name());
					break;
				}
				runshop.setBank(headOffice.getBank());
				runshop.setBankcard_province_name(headOffice.getBankcard_province_name());
				runshop.setBankcard_province_id(headOffice.getBankcard_province_id());
				runshop.setBankcard_city_id(headOffice.getBankcard_city_id());
				runshop.setBankcard_city_name(headOffice.getBankcard_city_name());
				runshop.setBankcard_cycle("");
				runshop.setBranch_banck(headOffice.getBranch_bank());
				runshop.setSettlement_inherit(1);
				runshop.setSettle_way(1);
			}
		} else if (headOffice.getSettlement_way() == SettlementWayEnum.BRANCH && runshop.getSettle_way() == 1) { // 如果目前是分店结算,但是上一次是总店结算,将清理字段
			logger.info("RunshopService#syncHeadOffice2Runshop in branch settlement");
			runshop.setSettlement_type(0);
			runshop.setBankcard("");
			runshop.setBankacard_owner("");
			runshop.setBank("");
			runshop.setBankcard_province_name("");
			runshop.setBankcard_province_id(0);
			runshop.setBankcard_city_id(0);
			runshop.setBankcard_city_name("");
			runshop.setBankcard_cycle("");
			runshop.setBranch_banck("");
			runshop.setSettlement_inherit(0);
			runshop.setSettle_way(2);
		}

		logger.info("RunshopService#syncHeadOffice2Runshop updateRunshpInfo runshop is {}", runshop);
		runshopMapper.updateRunshopInfo(runshop);

		if (!CollectionUtils.isEmpty(list)) {
			runshopPictureMapper.batchCreateRunshopPicture(list);
		}

		logger.info("RunshopService#syncHeadOffice2Runshop done, synced runshop id {}, added pic is {}", runshop, list);
	}

	private void updateTaskHash(int runshop_id, String mobile) {

		RunshopApplicationInfo runshopApplicationInfo = runshopMapper.getRunshopApplicationInfoByIdV2(runshop_id,
				mobile);

		Map<Integer, Integer> map = new HashMap<>();
		StoreInfo storeInfo = generateStoreInfoFromRunshop(runshopApplicationInfo);
		QualificationInfo qualificationInfo = generateQualifictionInfoFromRunshop(runshopApplicationInfo);

		VerifyTask applyInfoTask = VerifyTask.buildApplyBaseTask();
		applyInfoTask.generateCompareHash(storeInfo);
		VerifyTask logoTask = VerifyTask.buildLogoTask();
		logoTask.generateCompareHash(storeInfo);
		VerifyTask legalPersonTask = ApplyInfoTask.buildLegalPersonTask();
		legalPersonTask.generateCompareHash(qualificationInfo.getIdentity_info());
		VerifyTask businessLisenceTask = ApplyInfoTask.buildBusinessLisenceTask();
		businessLisenceTask.generateCompareHash(qualificationInfo.getBusiness_license_info());
		VerifyTask categeryLisenceTask = ApplyInfoTask.buildCateringServiceLicenseTask();
		categeryLisenceTask.generateCompareHash(qualificationInfo.getCatering_services_license_info());

		List<VerifyTask> taskList = Lists.newArrayList(applyInfoTask, logoTask, legalPersonTask, businessLisenceTask,
				categeryLisenceTask);
		for (VerifyTask task : taskList) {
			map.put(task.getAuditProcessEnum().getType(), task.hashCode());
		}
		try {
			runshopApplicationInfo.setCompare_hash(JsonHelper.toJsonString(map));
			logger.info("update_compareHash_for_runshopApplication : {}", runshopApplicationInfo);
			runshopMapper.updateCompareHash(runshopApplicationInfo);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}
	}

	private Set<me.ele.work.flow.engine.core.constants.AuditProcessEnum> getChangedProcessEnum(int runshop_id,
			String mobile) {

		Set<me.ele.work.flow.engine.core.constants.AuditProcessEnum> result = Sets.newHashSet();

		RunshopApplicationInfo runshopApplicationInfo = runshopMapper.getRunshopApplicationInfoByIdV2(runshop_id,
				mobile);

		StoreInfo storeInfo = generateStoreInfoFromRunshop(runshopApplicationInfo);
		QualificationInfo qualificationInfo = generateQualifictionInfoFromRunshop(runshopApplicationInfo);

		VerifyTask applyInfoTask = VerifyTask.buildApplyBaseTask();
		applyInfoTask.generateCompareHash(storeInfo);
		VerifyTask logoTask = VerifyTask.buildLogoTask();
		logoTask.generateCompareHash(storeInfo);
		VerifyTask legalPersonTask = ApplyInfoTask.buildLegalPersonTask();
		legalPersonTask.generateCompareHash(qualificationInfo.getIdentity_info());
		VerifyTask businessLisenceTask = ApplyInfoTask.buildBusinessLisenceTask();
		businessLisenceTask.generateCompareHash(qualificationInfo.getBusiness_license_info());
		VerifyTask categeryLisenceTask = ApplyInfoTask.buildCateringServiceLicenseTask();
		categeryLisenceTask.generateCompareHash(qualificationInfo.getCatering_services_license_info());

		String compareHash = runshopApplicationInfo.getCompare_hash();
		Map<Integer, Integer> hashMap = new HashMap();
		if (compareHash != null) {
			try {
				hashMap.putAll(JsonHelper.toJsonObject(compareHash, Map.class, Integer.class, Integer.class));
			} catch (JsonParseException e) {
				e.printStackTrace();
			} catch (Exception e) {
				e.printStackTrace();
				logger.error("");
			}
		}
		if (hashMap.get(me.ele.work.flow.engine.core.constants.AuditProcessEnum.STORE_INFO_AUDIT.getType()) == null
				|| (hashMap.get(me.ele.work.flow.engine.core.constants.AuditProcessEnum.STORE_INFO_AUDIT.getType()) != null && applyInfoTask
						.hashCode() != hashMap
						.get(me.ele.work.flow.engine.core.constants.AuditProcessEnum.STORE_INFO_AUDIT.getType()))) {
			result.add(me.ele.work.flow.engine.core.constants.AuditProcessEnum.STORE_INFO_AUDIT);
		}
		if (hashMap.get(me.ele.work.flow.engine.core.constants.AuditProcessEnum.LEGAL_REPRESENTATIVE_AUDIT.getType()) == null
				|| (hashMap.get(me.ele.work.flow.engine.core.constants.AuditProcessEnum.LEGAL_REPRESENTATIVE_AUDIT
						.getType()) != null && legalPersonTask.hashCode() != hashMap
						.get(me.ele.work.flow.engine.core.constants.AuditProcessEnum.LEGAL_REPRESENTATIVE_AUDIT
								.getType()))) {
			result.add(me.ele.work.flow.engine.core.constants.AuditProcessEnum.LEGAL_REPRESENTATIVE_AUDIT);
		}
		if (hashMap.get(me.ele.work.flow.engine.core.constants.AuditProcessEnum.STORE_LOGO_AUDIT.getType()) == null
				|| (hashMap.get(me.ele.work.flow.engine.core.constants.AuditProcessEnum.STORE_LOGO_AUDIT.getType()) != null && logoTask
						.hashCode() != hashMap
						.get(me.ele.work.flow.engine.core.constants.AuditProcessEnum.STORE_LOGO_AUDIT.getType()))) {
			result.add(me.ele.work.flow.engine.core.constants.AuditProcessEnum.STORE_LOGO_AUDIT);
		}
		if (hashMap.get(me.ele.work.flow.engine.core.constants.AuditProcessEnum.MAIN_QUALIFICATIONS_AUDIT.getType()) == null
				|| (hashMap.get(me.ele.work.flow.engine.core.constants.AuditProcessEnum.MAIN_QUALIFICATIONS_AUDIT
						.getType()) != null && businessLisenceTask.hashCode() != hashMap
						.get(me.ele.work.flow.engine.core.constants.AuditProcessEnum.MAIN_QUALIFICATIONS_AUDIT
								.getType()))) {
			result.add(me.ele.work.flow.engine.core.constants.AuditProcessEnum.MAIN_QUALIFICATIONS_AUDIT);
		}
		if (hashMap.get(me.ele.work.flow.engine.core.constants.AuditProcessEnum.INDUSTRY_QUALIFICATION_AUDIT.getType()) == null
				|| (hashMap.get(me.ele.work.flow.engine.core.constants.AuditProcessEnum.INDUSTRY_QUALIFICATION_AUDIT
						.getType()) != null && categeryLisenceTask.hashCode() != hashMap
						.get(me.ele.work.flow.engine.core.constants.AuditProcessEnum.INDUSTRY_QUALIFICATION_AUDIT
								.getType()))) {
			result.add(me.ele.work.flow.engine.core.constants.AuditProcessEnum.INDUSTRY_QUALIFICATION_AUDIT);
		}

		// runshopApplicationInfo.getCompare_hash();

		try {
			Map<Integer, String> list = JsonHelper.toJsonObject(runshopApplicationInfo.getReason(), Map.class,
					Integer.class, String.class);
			for (Integer process : list.keySet()) {
				result.add(me.ele.work.flow.engine.core.constants.AuditProcessEnum.valueOf(process));
			}
		} catch (JsonParseException e) {
			e.printStackTrace();
		} catch (JsonMappingException e) {
			e.printStackTrace();
		} catch (Exception e) {
			logger.error("");
		}

		return result;
	}

	private ShopLicensePermitApply createLicensePermitApply(RunshopApplicationInfo runshopApplicationInfo, String mobile)
			throws me.ele.napos.vine.api.ServiceException {
		ShopLicensePermitApply apply = new ShopLicensePermitApply();

		ShopLicenseApply shopLicenseApply = new ShopLicenseApply();
		ShopPermitApply shopPermit = new ShopPermitApply();

		shopLicenseApply.setApplyId(runshopApplicationInfo.getId());
		shopLicenseApply.setAddress(runshopApplicationInfo.getBusiness_license_address());
		shopLicenseApply.setCorpName(runshopApplicationInfo.getBusiness_license_name());
		shopLicenseApply.setLegalPerson(runshopApplicationInfo.getBusiness_license_legal_person());
		try {
			shopLicenseApply.setExpireDate(DateUtils.parseDate(runshopApplicationInfo.getBusiness_license_time(),
					"yyyy-MM-dd"));
		} catch (Exception e) {
			try {
				shopLicenseApply.setExpireDate(DateUtils.parseDate("9999-09-09", "yyyy-MM-dd"));
			} catch (ParseException e1) {
				e1.printStackTrace();
			}
		}
		List<RunshopApplicationPicture> picList = runshopPictureMapper
				.listRunshopApplicationPictureByRunshopId(runshopApplicationInfo.getId());
		if (!CollectionUtils.isEmpty(picList)) {
			for (RunshopApplicationPicture pic : picList) {
				switch (pic.getPic_code()) {
				case ProjectConstant.BUSINESS_LICENSE_PIC_CODE:
					shopLicenseApply.setImageHash(pic.getPic_hash());
					break;
				case ProjectConstant.BUSINESS_LICENSE_VM_PIC_CODE:
					shopLicenseApply.setWmImageHash(pic.getPic_hash());
					break;
				case ProjectConstant.CATERING_SERVICES_PIC_CODE:
					shopPermit.setImageHash(pic.getPic_hash());
					break;
				case ProjectConstant.CATERING_SERVICES_VM_PIC_CODE:
					shopPermit.setWmImageHash(pic.getPic_hash());
					break;
				default:
					break;
				}
			}
		}
		// shopLicenseApply.setLegalPerson(runshopApplicationInfo.getBoss());
		shopLicenseApply.setLicenseNo(runshopApplicationInfo.getBusiness_license_num());
		shopLicenseApply
				.setLicenseType(LicenseType.getLicenseType(runshopApplicationInfo.getBusiness_license_type() - 1));
		// shopLicenseApply.setOperatorId(user.getId());

		// shopPermit.setOperatorId(user.getId());
		shopPermit
				.setPermitType(PermitType.getPermitType(runshopApplicationInfo.getCatering_services_license_type() - 1));
		// shopPermit.setLegalPerson(runshopApplicationInfo.getBoss());
		shopPermit.setAddress(runshopApplicationInfo.getCatering_services_license_address());
		shopPermit.setApplyId(runshopApplicationInfo.getId());
		shopPermit.setPermitNo(runshopApplicationInfo.getCatering_services_license_num());
		shopPermit.setCorpName(runshopApplicationInfo.getCatering_services_license_name());
		shopPermit.setLegalPerson(runshopApplicationInfo.getCatering_services_legal_person());
		try {
			shopPermit.setExpireDate(DateUtils.parseDate(runshopApplicationInfo.getCatering_services_license_time(),
					"yyyy-MM-dd"));
		} catch (Exception e) {
			try {
				shopPermit.setExpireDate(DateUtils.parseDate("9999-09-09", "yyyy-MM-dd"));
			} catch (ParseException e1) {
				e1.printStackTrace();
			}
		}

		apply.setShopLicense(shopLicenseApply);
		apply.setShopPermit(shopPermit);
		return apply;
		// lAuditService.createLicensePermitApply(apply, 0);
	}

	private LegalPersonApply createLegalApply(RunshopApplicationInfo runshopApplicationInfo, String mobile)
			throws me.ele.napos.vine.api.ServiceException {

		LegalPersonApply apply = new LegalPersonApply();
		apply.setApplyId(runshopApplicationInfo.getId());
		apply.setPersonName(runshopApplicationInfo.getBoss());
		apply.setIdentityType(IdentityType.fromIndex(runshopApplicationInfo.getCredentials_type()));
		apply.setLegalPersonNo(runshopApplicationInfo.getIdentity_number());

		List<RunshopApplicationPicture> picList = runshopPictureMapper
				.listRunshopApplicationPictureByRunshopId(runshopApplicationInfo.getId());
		if (!CollectionUtils.isEmpty(picList)) {
			for (RunshopApplicationPicture pic : picList) {
				switch (pic.getPic_code()) {
				case ProjectConstant.IDENTITY_POSITIVE_PIC_CODE:
					apply.setFrontImageHash(pic.getPic_hash());
					break;
				case ProjectConstant.IDENTITY_OPPOSITE_PIC_CODE:
					apply.setBackImageHash(pic.getPic_hash());
					break;
				case ProjectConstant.DENTITY_HOLD_ON:
					apply.setHandheldImageHash(pic.getPic_hash());
					break;
				case ProjectConstant.IDENTITY_POSITIVE_VM_PIC_CODE:
					apply.setWmFrontImageHash(pic.getPic_hash());
					break;
				case ProjectConstant.IDENTITY_OPPOSITE_VM_PIC_CODE:
					apply.setWmbackImageHash(pic.getPic_hash());
					break;
				case ProjectConstant.DENTITY_VM_HOLD_ON:
					apply.setWmhandheldImageHash(pic.getPic_hash());
					break;
				default:
					break;
				}
			}

		}
		return apply;
		// lAuditService.createLegalApply(apply, 0);
	}

	private ApplyShopBase createApplyStoreInfo(RunshopApplicationInfo runshopApplicationInfo, String mobile)
			throws me.ele.napos.vine.api.ServiceException {

		ApplyShopBase applyShopBase = new ApplyShopBase();

		applyShopBase.setApplyId(runshopApplicationInfo.getId());
		applyShopBase.setCityId(runshopApplicationInfo.getCity_id());
		applyShopBase.setContactPerson(runshopApplicationInfo.getContact_name());
		applyShopBase.setDistrictId(runshopApplicationInfo.getDistrict_id());
		applyShopBase.setProvinceId(runshopApplicationInfo.getProvince_id());
		applyShopBase.setLatitude(runshopApplicationInfo.getLatitude().doubleValue());
		applyShopBase.setLongitude(runshopApplicationInfo.getLongitude().doubleValue());
		applyShopBase.setFlavors(runshopApplicationInfo.getStore_classification_id());
		applyShopBase.setShopName(runshopApplicationInfo.getStore_name());
		applyShopBase.setPhone(runshopApplicationInfo.getMobile());
		applyShopBase.setDetailAddress(runshopApplicationInfo.getAddress());
		applyShopBase.setExtPhone(runshopApplicationInfo.getExt_phone());
		List<RunshopApplicationPicture> picList = runshopPictureMapper
				.listRunshopApplicationPictureByRunshopId(runshopApplicationInfo.getId());
		if (!CollectionUtils.isEmpty(picList)) {
			String cdnHash = "";
			for (RunshopApplicationPicture pic : picList) {
				switch (pic.getPic_code()) {
				case ProjectConstant.STORE_LOGO:
					;
					cdnHash = fussService.uploadImageToCdn(pic.getPic_url(), pic.getPic_hash());
					logger.info("STORE_LOGO cdnHash is {}", cdnHash);
					applyShopBase.setShopSqureLogoHash(cdnHash);
					applyShopBase.setShopRectangleLogoHash(cdnHash);
					break;
				case ProjectConstant.DOOR_PIC_CODE:
					cdnHash = fussService.uploadImageToCdn(pic.getPic_url(), pic.getPic_url());
					logger.info("cdnHash is {}", cdnHash);
					applyShopBase.setShopFacdeImageHash(cdnHash);
					break;
				case ProjectConstant.DOOR_VM_PIC_CODE:
					applyShopBase.setWmShopFacdeImageHash(pic.getPic_hash());
					logger.info("cdnHash is {}", cdnHash);
					break;
				case ProjectConstant.STORE_PIC_CODE:
					cdnHash = fussService.uploadImageToCdn(pic.getPic_url(), pic.getPic_url());
					logger.info("cdnHash is {}", cdnHash);
					applyShopBase.setShopInternalImageHash(cdnHash);
					break;
				case ProjectConstant.STORE_VM_PIC_CODE:
					applyShopBase.setWmShopInternalImageHash(pic.getPic_hash());
					break;
				case ProjectConstant.STORE_OTHER_PIC_CODE:
					cdnHash = fussService.uploadImageToCdn(pic.getPic_url(), pic.getPic_url());
					applyShopBase.setOtherImageHash(cdnHash);
					break;
				case ProjectConstant.STORE_OTHER_VM_PIC_CODE:
					applyShopBase.setWmOtherImageHash(pic.getPic_hash());
					break;
				default:
					break;
				}
			}

		}
		try {
			applyShopBase.setProvinceName(saturnCityService.getProvinceById(runshopApplicationInfo.getProvince_id())
					.getName());
			applyShopBase.setCityName(saturnCityService.getCityById(runshopApplicationInfo.getCity_id()).getName());
			applyShopBase.setDistrictName(saturnCityService.getCityById(runshopApplicationInfo.getDistrict_id())
					.getName());
		} catch (Exception e) {
			logger.error("get province name failed, ", e);
		}

		applyShopBase.setOtherPlatformLink(runshopApplicationInfo.getThird_party_platform_url());
		// applyShopBase.setOpeningDate(runshopApplicationInfo.getOpen_day());
		try {
			List<OpenTime> origin = JsonHelper.toJsonObject(runshopApplicationInfo.getOpen_time(), ArrayList.class,
					OpenTime.class);

			List<me.ele.napos.operation.shared.payload.entities.applybase.OpenTime> list = new LinkedList<>();
			for (OpenTime o : origin) {
				me.ele.napos.operation.shared.payload.entities.applybase.OpenTime o1 = new me.ele.napos.operation.shared.payload.entities.applybase.OpenTime();
				o1.setBeginTime(o.getOpen());
				o1.setEndTime(o.getClose());
				list.add(o1);
			}
			applyShopBase.setOpeningTime(list);
		} catch (Exception e) {
			logger.info("parse openTime error, ", e);
		}

		return applyShopBase;
		//
	}

	@Override
	public List<GoodInfo> listGoodInfoByRunshopId(int runshopId) throws ServiceException, ServiceException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<Category> saveRunshopGoodClassifications(List<Category> categoryList, int runShopId) {
		List<Category> categoryListInDB = runshopMapper.listRunShopGoodClassificationByRunshopId(runShopId);

		List<Category> categoryNeedInsert = new ArrayList<>();
		List<Category> categoryNeedUpdate = new ArrayList<>();
		List<Integer> categoryIdNeedDelete = new ArrayList<>();
		// 过滤要添加和更新的
		for (Category c : categoryList) {
			// 填充runShopId
			c.setRunshop_id(runShopId);
			if (c.getId() == 0) {
				categoryNeedInsert.add(c);
			} else {
				categoryNeedUpdate.add(c);
			}
		}

		// 过滤要删除的
		for (Category cDB : categoryListInDB) {
			for (Category c : categoryList) {
				if (cDB.getId() == c.getId()) {
					break;
				}
			}
			categoryIdNeedDelete.add(cDB.getId());
		}

		if (!CollectionUtils.isEmpty(categoryNeedInsert)) {
			runshopMapper.createRunShopGoodClassification(categoryNeedInsert);
		}
		if (!CollectionUtils.isEmpty(categoryNeedUpdate)) {
			for (Category c : categoryNeedUpdate) {
				runshopMapper.updateRunShopGoodClassification(c);
			}
		}
		if (!CollectionUtils.isEmpty(categoryIdNeedDelete)) {
			runshopMapper.deleteRunShopGoodClassificationByIds(categoryIdNeedDelete);
		}

		// 再次查询
		List<Category> categoryListInDBNew = runshopMapper.listRunShopGoodClassificationByRunshopId(runShopId);

		return categoryListInDBNew;
	}

	@Override
	public List<Category> listCategory(int runshopId) {
		List<Category> categoryListInDBNew = runshopMapper.listRunShopGoodClassificationByRunshopId(runshopId);
		return categoryListInDBNew;
	}

	// 针对写操作之前
	private StoreInfo generateStoreInfoFromRunshop(RunshopApplicationInfo runshopApplicationInfo) {

		if (runshopApplicationInfo != null) {
			StoreInfo storeInfo = new StoreInfo();
			storeInfo.setAuditTimes(runshopApplicationInfo.getSubmit_times());
			storeInfo.setRunshop_id(runshopApplicationInfo.getId());
			if (!Strings.isNullOrEmpty(runshopApplicationInfo.getExt_phone())) {
				storeInfo.setExt_phone(runshopApplicationInfo.getExt_phone());
			}else {
				storeInfo.setExt_phone("");
			}
			storeInfo.setStore_name(runshopApplicationInfo.getStore_name());
			storeInfo.setContact_name(runshopApplicationInfo.getContact_name());
			storeInfo.setMobile(runshopApplicationInfo.getMobile());
			storeInfo.setHead_id(runshopApplicationInfo.getHead_id());
			storeInfo.setRst_id(runshopApplicationInfo.getRst_id());
			storeInfo.setBrand_id(runshopApplicationInfo.getBrand_id());
			storeInfo.setBrand_name(runshopApplicationInfo.getBrand_name());
			storeInfo.setDom_id(runshopApplicationInfo.getDom_id());
			if (runshopApplicationInfo.getDom_id() > 0) {
				storeInfo.setThirdPlatformMap(generateThirdPlatFormInfo(runshopApplicationInfo.getDom_id()));
			}
			storeInfo.setContact_mobile(runshopApplicationInfo.getContact_mobile());
			// 门店分类id整理
			String scId = runshopApplicationInfo.getStore_classification_id();
			String classification = runshopApplicationInfo.getStore_classification();
			List<Integer> scIdList = new ArrayList<>();
			List<String> classificationList = new ArrayList<>();
			try {
				scIdList = JsonHelper.toJsonObject(scId, ArrayList.class, Integer.class);
				classificationList = JsonHelper.toJsonObject(classification, ArrayList.class, String.class);
			} catch (IOException e) {
				logger.info("RunshopService#getRunshopById,e:{}", e);
			}
			if (!CollectionUtils.isEmpty(scIdList)) {
				List<StoreClassification> sclist = new LinkedList<>();
				for (int i = 0; i < scIdList.size(); i++) {
					StoreClassification storeClassification = new StoreClassification();
					storeClassification.setId(scIdList.get(i));
					storeClassification.setName(classificationList.get(i));
					sclist.add(storeClassification);
				}
				storeInfo.setStore_classification_list(sclist);
			}

			try {
				List<OpenTime> openTime = JsonHelper.toJsonObject(runshopApplicationInfo.getOpen_time(),
						ArrayList.class, OpenTime.class);
				List<Integer> openDay = JsonHelper.toJsonObject(runshopApplicationInfo.getOpen_day(), ArrayList.class,
						Integer.class);
				OpenPeriod openPeriod = new OpenPeriod();
				openPeriod.setOpen_day(openDay);
				openPeriod.setOpen_time(openTime);
				storeInfo.setOpen_period(openPeriod);
			} catch (IOException e) {
				logger.info("RunshopService#getRunshopById,e:{}", e);
			}

			PcdRegion pcdRegion = new PcdRegion();
			/*
			 * pcdRegion.setCity(new City(runshopApplicationInfo.getCity_id(),
			 * runshopApplicationInfo.getCity_name()));
			 * 
			 * try { RegionDto province_city =
			 * saturnCityService.getCityById(runshopApplicationInfo
			 * .getProvince_id()); RegionDto district_city =
			 * saturnCityService.getCityById
			 * (runshopApplicationInfo.getDistrict_id()); if (province_city !=
			 * null) { pcdRegion.setProvince(new Province(province_city.getId(),
			 * province_city.getName())); } if (district_city != null) {
			 * pcdRegion.setDistrict(new District(district_city.getId(),
			 * district_city.getName())); } } catch (Exception e) {
			 * logger.error(
			 * "CovertStoreInfoToRunshop#saturnCityService.getCityById,e:{}",
			 * e); }
			 */
			pcdRegion.setCity_id(runshopApplicationInfo.getCity_id());
			pcdRegion.setCity_name(runshopApplicationInfo.getCity_name());
			pcdRegion.setProvince_id(runshopApplicationInfo.getProvince_id());
			// pcdRegion.setProvince_name("");
			if (runshopApplicationInfo.getProvince_id() != 0) {
				try {
					pcdRegion.setProvince_name(saturnCityService.getProvinceById(
							runshopApplicationInfo.getProvince_id()).getName());
				} catch (Exception e) {
					logger.error("RunshopService#generateStoreInfoFromRunshop get province name error, ", e);
				}
			}
			pcdRegion.setDistrict_id(runshopApplicationInfo.getDistrict_id());
			// pcdRegion.setDistrict_name("");
			if (runshopApplicationInfo.getDistrict_id() != 0) {
				try {
					pcdRegion.setDistrict_name(saturnCityService.getCityById(runshopApplicationInfo.getDistrict_id())
							.getName());
				} catch (Exception e) {
					logger.error("RunshopService#generateStoreInfoFromRunshop get district name error, ", e);
				}
			}
			storeInfo.setPcd_region(pcdRegion);

			AddressInfo addressInfo = new AddressInfo();
			addressInfo.setAddress(runshopApplicationInfo.getAddress());
			addressInfo.setLatitude(runshopApplicationInfo.getLatitude());
			addressInfo.setLongitude(runshopApplicationInfo.getLongitude());

			storeInfo.setAddress_info(addressInfo);

			storeInfo.setThird_party_platform_url(runshopApplicationInfo.getThird_party_platform_url());

			List<RunshopApplicationPicture> pics = runshopPictureMapper
					.listRunshopApplicationPictureByRunshopId(runshopApplicationInfo.getId());
			logger.info("RunshopService#getRunshopById,pics:{}", pics);
			for (RunshopApplicationPicture one : pics) {
				ImageEntity entity = new ImageEntity();
				switch (one.getPic_code()) {
				case ProjectConstant.STORE_LOGO:
					entity.setHash(one.getPic_hash());
					entity.setUrl(one.getPic_url());
					storeInfo.setLogo(entity);
					break;
				case ProjectConstant.DOOR_PIC_CODE:
					entity.setHash(one.getPic_hash());
					entity.setUrl(one.getPic_url());
					storeInfo.setDoor_pic(entity);
					break;
				case ProjectConstant.STORE_PIC_CODE:
					entity.setHash(one.getPic_hash());
					entity.setUrl(one.getPic_url());
					storeInfo.setStore_pic(entity);
					break;
				case ProjectConstant.STORE_OTHER_PIC_CODE:
					entity.setHash(one.getPic_hash());
					entity.setUrl(one.getPic_url());
					storeInfo.setOther_pic(entity);
					break;
				case ProjectConstant.STORE_LOGO_OLD:
					entity.setHash(one.getPic_hash());
					entity.setUrl(one.getPic_url());
					storeInfo.setLogo_old(entity);
				default:
					break;
				}
			}

			return storeInfo;
		} else
			return null;
	}

	// 针对写操作之前
	private RunshopApplicationInfo covertStoreInfoToRunshop(StoreInfo storeInfo) {

		RunshopApplicationInfo runshop = new RunshopApplicationInfo();
		runshop.setId(storeInfo.getRunshop_id());
		if(!Strings.isNullOrEmpty(storeInfo.getExt_phone())) {
			runshop.setExt_phone(storeInfo.getExt_phone());
		}else {
			runshop.setExt_phone("");
		}
		runshop.setDom_source(storeInfo.getDom_source());
		runshop.setTag(storeInfo.getTag());
		runshop.setStore_name(storeInfo.getStore_name());
		runshop.setContact_name(storeInfo.getContact_name());
		runshop.setMobile(storeInfo.getMobile());
		runshop.setHead_id(storeInfo.getHead_id());
		runshop.setBrand_id(storeInfo.getBrand_id());
		runshop.setBrand_name(storeInfo.getBrand_name());
		runshop.setContact_mobile(storeInfo.getContact_mobile());
		List<StoreClassification> clist = storeInfo.getStore_classification_list();
		List<Integer> classificationIdList = new ArrayList<>();
		List<String> classificationNameList = new ArrayList<>();

		for (StoreClassification classification : clist) {
			if (classification == null) {
				continue;
			}
			int cId = classification.getId();
			String cName = classification.getName();
			classificationIdList.add(cId);
			classificationNameList.add(cName);
		}

		if (classificationIdList.size() > 0) {
			try {
				runshop.setStore_classification_id(JsonHelper.toJsonString(classificationIdList));
				runshop.setStore_classification(JsonHelper.toJsonString(classificationNameList));
			} catch (JsonProcessingException e) {
				logger.error("CovertStoreInfoToRunshop#Json parse error,{}", e);
			}
		}

		OpenPeriod openPeriod = storeInfo.getOpen_period();
		if (openPeriod != null) {

			try {
				List<Integer> openDay = openPeriod.getOpen_day();
				List<OpenTime> openTime = openPeriod.getOpen_time();
				if (!CollectionUtils.isEmpty(openDay)) {
					runshop.setOpen_day("noopenday");
				}
				if (!CollectionUtils.isEmpty(openTime)) {
					for (OpenTime ot : openTime) {
						if (StringUtils.isEmpty(ot.getOpen()))
							ot.setOpen("00:00");
						if (StringUtils.isEmpty(ot.getClose()))
							ot.setClose("23:59");
					}
					runshop.setOpen_time(JsonHelper.toJsonString(openTime));
				} else {
					openTime = new ArrayList<>();
					OpenTime openTimeDefault = new OpenTime();
					openTimeDefault.setOpen("00:00");
					openTimeDefault.setClose("22:00");
					openTime.add(openTimeDefault);
					runshop.setOpen_time(JsonHelper.toJsonString(openTime));
				}
			} catch (JsonProcessingException e) {
				logger.error("CovertStoreInfoToRunshop#Json parse error,{}", e);
			}

		}

		PcdRegion pcd = storeInfo.getPcd_region();
		if (pcd != null) {
			/*
			 * runshop.setProvince_id(pcd.getProvince() != null ?
			 * pcd.getProvince().getProvince_id() : 0);
			 * runshop.setCity_id(pcd.getCity() != null ?
			 * pcd.getCity().getCity_id() : 0);
			 * runshop.setCity_name(pcd.getCity() != null ?
			 * pcd.getCity().getCity_name() : "");
			 * runshop.setDistrict_id(pcd.getDistrict() != null ?
			 * pcd.getDistrict().getDistrict_id() : 0);
			 */
			runshop.setProvince_id(pcd.getProvince_id());
			runshop.setCity_id(pcd.getCity_id());
			runshop.setDistrict_id(pcd.getDistrict_id());
			try {
				RegionDto city = saturnCityService.getCityById(runshop.getCity_id());
				if (city != null) {
					runshop.setCity_name(city.getName());
				} else {
					logger.warn("not find city info for {}", runshop.getCity_id());
				}
			} catch (Exception e) {
				logger.error("CovertStoreInfoToRunshop#saturnCityService.getCityById,e:{}", e);
			}
		}

		AddressInfo addressInfo = storeInfo.getAddress_info();
		String address = addressInfo.getAddress();
		BigDecimal latitude = addressInfo.getLatitude();
		BigDecimal longitude = addressInfo.getLongitude();

		runshop.setAddress(address);
		runshop.setLongitude(longitude);
		runshop.setLatitude(latitude);
		runshop.setThird_party_platform_url(storeInfo.getThird_party_platform_url());

		if (storeInfo.getRunshop_id() == 0) {
			runshop.setStep(RunshopServiceConstants.STEP_STORE_INFO);
			runshop.setStatus(RunshopServiceConstants.NEED_COMPLETE);
		} else {
			RunshopV2StautsAndStep statusAndStep = runshopMapper.getRunshopV2StautsAndStep(storeInfo.getRunshop_id());
			// 新建的情况
			if (statusAndStep == null) {
				runshop.setStep(RunshopServiceConstants.STEP_STORE_INFO);
				runshop.setStatus(RunshopServiceConstants.NEED_COMPLETE);
			} else {
				// 更新的情况
				runshop.setStep(Math.max(RunshopServiceConstants.STEP_STORE_INFO, statusAndStep.getStep()));
				runshop.setStatus(RunshopServiceConstants.NEED_COMPLETE);
			}
		}

		if (storeInfo.getDom_id() > 0) {
			runshop.setDom_id(storeInfo.getDom_id());
		}

		return runshop;
	}

	;

	private RunshopApplicationInfo convertQualificationInfoToRunshop(QualificationInfo qualificationInfo)
			throws ServiceException {
		logger.info("ConvertQualificationInfoToRunshop,qualificationInfo:{}", qualificationInfo);
		RunshopApplicationInfo runshop = new RunshopApplicationInfo();

		RunshopV2StautsAndStep statusAndStep = runshopMapper.getRunshopV2StautsAndStep(qualificationInfo
				.getRunshop_id());

		if (statusAndStep == null) {
			throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_NULL_BY_ID);

		} else {

			boolean isSkip = isSkip(statusAndStep.getStep(), RunshopServiceConstants.STEP_QUALIFICATION_INFO);
			if (isSkip) {
				throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_IS_SKIP);
			}
			// 更新的情况
			runshop.setStep(Math.max(RunshopServiceConstants.STEP_QUALIFICATION_INFO, statusAndStep.getStep()));
		}

		runshop.setId(qualificationInfo.getRunshop_id());

		BusinessLicenseInfo bli = qualificationInfo.getBusiness_license_info();
		if (bli != null) {
			String bAddress = bli.getBusiness_license_address();
			String bName = bli.getBusiness_license_name();
			String bNum = bli.getBusiness_license_num();
			String bTime = bli.getBusiness_license_time();
			int bType = bli.getBusiness_license_type();
			boolean bIsForever = bli.isIs_forever();
			int bInherit = bli.getInherit();
			String bLegalPerson = bli.getLegal_person();

			if (StringUtils.isBlank(bLegalPerson)) {
				bLegalPerson = "";
			}

			runshop.setBusiness_license_address(bAddress);
			runshop.setBusiness_license_name(bName);
			runshop.setBusiness_license_num(bNum);
			runshop.setBusiness_license_time(bTime);
			runshop.setBusiness_license_inherit(bInherit);
			runshop.setBusiness_license_legal_person(bLegalPerson);

			if (bIsForever) {
				runshop.setBusiness_license_time(ProjectConstant.BUSINESS_LICENSE_NO_EXPIRE_TIME);
			}
			runshop.setBusiness_license_type(bType);
		}

		CateringServiceLicenseInfo csli = qualificationInfo.getCatering_services_license_info();

		if (csli != null) {
			String cAdress = csli.getCatering_services_license_address();
			String cName = csli.getCatering_services_license_name();
			String cNum = csli.getCatering_services_license_num();

			String cTime = csli.isIs_forever() ? ProjectConstant.BUSINESS_LICENSE_NO_EXPIRE_TIME : csli
					.getCatering_services_license_time();
			int cType = csli.getCatering_services_license_type();

			String csliLegalPerson = csli.getLegal_person();

			if (StringUtils.isBlank(csliLegalPerson)) {
				csliLegalPerson = "";
			}
			runshop.setCatering_services_license_address(cAdress);
			runshop.setCatering_services_license_name(cName);
			runshop.setCatering_services_license_num(cNum);
			runshop.setCatering_services_license_time(cTime);
			runshop.setCatering_services_license_type(cType);
			runshop.setCatering_services_legal_person(csliLegalPerson);
		}

		IdentityInfo identity = qualificationInfo.getIdentity_info();
		String boss = identity.getBoss();
		int idType = identity.getCredentials_type();
		String idNumber = identity.getIdentity_number();
		int iInherit = identity.getInherit();

		runshop.setBoss(boss);
		runshop.setCredentials_type(idType);
		runshop.setIdentity_number(idNumber);
		runshop.setIdentity_inherit(iInherit);

		return runshop;
	}

	private RunshopApplicationInfo convertCooperationToRunshop(CooperationInfo cooperationInfo) throws ServiceException {
		logger.info("convertCooperationToRunshop#cooperationInfo:{}", cooperationInfo);
		RunshopApplicationInfo runshop = new RunshopApplicationInfo();

		RunshopV2StautsAndStep statusAndStep = runshopMapper.getRunshopV2StautsAndStep(cooperationInfo.getRunshop_id());

		runshop.setId(cooperationInfo.getRunshop_id());
		runshop.setCooperation_type(cooperationInfo.getCooperation_type());
		if (statusAndStep == null) {
			throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_NULL_BY_ID);

		} else {
			boolean isSkip = isSkip(statusAndStep.getStep(), RunshopServiceConstants.STEP_COOPERATION_INFO);
			if (isSkip) {
				throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_IS_SKIP);
			}
			// 更新的情况
			runshop.setStep(Math.max(RunshopServiceConstants.STEP_COOPERATION_INFO, statusAndStep.getStep()));
		}

		return runshop;
	}

	private void validateCertificateBusinessLicensePic(BusinessLicenseInfo businessLicenseInfo) throws ServiceException {

		if (StringUtils.isBlank(businessLicenseInfo.getBusiness_license_name())) {
			throw ExceptionUtil.createServiceException(ExceptionCode.BUSSSINESS_LICENSE_NAME_NULL);
		}
		if (StringUtils.isBlank(businessLicenseInfo.getBusiness_license_address())) {
			throw ExceptionUtil.createServiceException(ExceptionCode.BUSSSINESS_LICENSE_ADDRESS_NULL);
		}
		if (StringUtils.isBlank(businessLicenseInfo.getBusiness_license_num())) {
			throw ExceptionUtil.createServiceException(ExceptionCode.BUSSSINESS_LICENSE_NUM_NULL);
		}
		if (StringUtils.isBlank(businessLicenseInfo.getBusiness_license_time())) {
			throw ExceptionUtil.createServiceException(ExceptionCode.BUSSSINESS_LICENSE_TIME_NULL);
		}
	}

	/**
	 * 检验许可证信息 update.
	 *
	 * @param cateringServiceLicenseInfo
	 * @throws ServiceException
	 */
	private void validateCertificateCaterintServiceLicense(CateringServiceLicenseInfo cateringServiceLicenseInfo)
			throws ServiceException {

		logger.info("RunshopService.validateCertificateCaterintServiceLicense start with:{}",
				cateringServiceLicenseInfo);

		if (null == cateringServiceLicenseInfo.getCatering_services_pic()) {
			throw ExceptionUtil.createServiceException(ExceptionCode.CATERING_SERVICES_PIC_NULL);
		}
		if (StringUtils.isBlank(cateringServiceLicenseInfo.getCatering_services_license_name())) {
			throw ExceptionUtil.createServiceException(ExceptionCode.CATERING_SERVICES_NAME_NULL);
		}
		if (StringUtils.isBlank(cateringServiceLicenseInfo.getCatering_services_license_address())) {
			throw ExceptionUtil.createServiceException(ExceptionCode.CATERING_SERVICES_ADDRESS_NULL);
		}
		if (StringUtils.isBlank(cateringServiceLicenseInfo.getCatering_services_license_num())) {
			throw ExceptionUtil.createServiceException(ExceptionCode.CATERING_SERVICES_NUM_NULL);
		}
		if (StringUtils.isBlank(cateringServiceLicenseInfo.getCatering_services_license_time())) {
			throw ExceptionUtil.createServiceException(ExceptionCode.CATERING_SERVICES_TIME_NULL);
		}

	}

	@Override
	public RunshopV2Dto fillQualificationInfo(int runshop_id, String mobile) {
		logger.info("RunshopService.fillQualificationInfo start with:{}", runshop_id, mobile);
		RunshopV2Dto result = new RunshopV2Dto();
		RunshopApplicationInfo runshopApplicationInfo = runshopMapper.getRunshopApplicationInfoByIdV2(runshop_id,
				mobile);
		if (runshopApplicationInfo.getStep() < RunshopServiceConstants.STEP_QUALIFICATION_INFO) {
			result.setQualification_info(null);
		} else {
			QualificationInfo qualificationInfo = generateQualifictionInfoFromRunshop(runshopApplicationInfo);
			result.setQualification_info(qualificationInfo);
		}
		result.setStep(runshopApplicationInfo.getStep());
		result.setStatus_code(runshopApplicationInfo.getStatus());
		result.setReason(runshopApplicationInfo.getReason());
		return result;
	}

	private QualificationInfo generateQualifictionInfoFromRunshop(RunshopApplicationInfo runshopApplicationInfo) {
		logger.info("RunshopService.generateQualifictionInfoFromRunshop start with:{}", runshopApplicationInfo);
		if (runshopApplicationInfo != null) {
			QualificationInfo qualificationInfo = new QualificationInfo();
			qualificationInfo.setRunshop_id(runshopApplicationInfo.getId());

			BusinessLicenseInfo businessLicenseInfo = new BusinessLicenseInfo();
			businessLicenseInfo.setBusiness_license_address(runshopApplicationInfo.getBusiness_license_address());
			businessLicenseInfo.setBusiness_license_name(runshopApplicationInfo.getBusiness_license_name());
			businessLicenseInfo.setBusiness_license_num(runshopApplicationInfo.getBusiness_license_num());
			businessLicenseInfo.setBusiness_license_time(runshopApplicationInfo.getBusiness_license_time());
			if (businessLicenseInfo.getBusiness_license_time().equals(ProjectConstant.BUSINESS_LICENSE_NO_EXPIRE_TIME)) {
				businessLicenseInfo.setIs_forever(true);
			}
			businessLicenseInfo.setBusiness_license_type(runshopApplicationInfo.getBusiness_license_type());
			businessLicenseInfo.setInherit(runshopApplicationInfo.getBusiness_license_inherit());
			businessLicenseInfo.setLegal_person(runshopApplicationInfo.getBusiness_license_legal_person());
			qualificationInfo.setBusiness_license_info(businessLicenseInfo);

			CateringServiceLicenseInfo cateringServiceLicenseInfo = new CateringServiceLicenseInfo();
			cateringServiceLicenseInfo.setCatering_services_license_address(runshopApplicationInfo
					.getCatering_services_license_address());
			cateringServiceLicenseInfo.setCatering_services_license_name(runshopApplicationInfo
					.getCatering_services_license_name());
			cateringServiceLicenseInfo.setCatering_services_license_num(runshopApplicationInfo
					.getCatering_services_license_num());
			cateringServiceLicenseInfo.setCatering_services_license_time(runshopApplicationInfo
					.getCatering_services_license_time());
			cateringServiceLicenseInfo.setCatering_services_license_type(runshopApplicationInfo
					.getCatering_services_license_type());
			if (cateringServiceLicenseInfo.getCatering_services_license_time().equals(
					ProjectConstant.BUSINESS_LICENSE_NO_EXPIRE_TIME)) {
				cateringServiceLicenseInfo.setIs_forever(true);
			}

			cateringServiceLicenseInfo.setLegal_person(runshopApplicationInfo.getCatering_services_legal_person());

			qualificationInfo.setCatering_services_license_info(cateringServiceLicenseInfo);

			IdentityInfo identityInfo = new IdentityInfo();
			identityInfo.setBoss(runshopApplicationInfo.getBoss());
			identityInfo.setCredentials_type(runshopApplicationInfo.getCredentials_type());
			identityInfo.setIdentity_number(runshopApplicationInfo.getIdentity_number());
			identityInfo.setInherit(runshopApplicationInfo.getIdentity_inherit());
			List<RunshopApplicationPicture> pics = runshopPictureMapper
					.listRunshopApplicationPictureByRunshopId(runshopApplicationInfo.getId());
			logger.info("RunshopService#getRunshopById,pics:{}", pics);
			for (RunshopApplicationPicture one : pics) {
				ImageEntity entity = new ImageEntity();
				switch (one.getPic_code()) {
				case ProjectConstant.BUSINESS_LICENSE_PIC_CODE:
					entity.setHash(one.getPic_hash());
					entity.setUrl(one.getPic_url());
					businessLicenseInfo.setBusiness_license_pic(entity);
					break;
				case ProjectConstant.CATERING_SERVICES_PIC_CODE:
					entity.setHash(one.getPic_hash());
					entity.setUrl(one.getPic_url());
					cateringServiceLicenseInfo.setCatering_services_pic(entity);
					break;
				case ProjectConstant.DENTITY_HOLD_ON:
					entity.setHash(one.getPic_hash());
					entity.setUrl(one.getPic_url());
					identityInfo.setIdentity_hold_on_pic(entity);
					break;
				case ProjectConstant.IDENTITY_OPPOSITE_PIC_CODE:
					entity.setHash(one.getPic_hash());
					entity.setUrl(one.getPic_url());
					identityInfo.setIdentity_opposite_pic(entity);
					break;
				case ProjectConstant.IDENTITY_POSITIVE_PIC_CODE:
					entity.setHash(one.getPic_hash());
					entity.setUrl(one.getPic_url());
					identityInfo.setIdentity_positive_pic(entity);
					break;
				default:
					break;
				}
			}

			qualificationInfo.setIdentity_info(identityInfo);

			return qualificationInfo;
		} else
			return null;
	}

	@Override
	public RunshopV2Dto fillCooperationInfo(int runshop_id, String mobile) {
		logger.info("RunshopService.fillCooperationInfo start with:{}", runshop_id, mobile);
		RunshopV2Dto result = new RunshopV2Dto();
		RunshopApplicationInfo runshopApplicationInfo = runshopMapper.getRunshopApplicationInfoByIdV2(runshop_id,
				mobile);
		if (runshopApplicationInfo.getStep() < RunshopServiceConstants.STEP_COOPERATION_INFO) {
			result.setCooperation_info(null);
		} else {
			CooperationInfo cooperationInfo = generateCooperationInfoFromRunshop(runshopApplicationInfo);
			result.setCooperation_info(cooperationInfo);
		}
		result.setStep(runshopApplicationInfo.getStep());
		result.setStatus_code(runshopApplicationInfo.getStatus());
		result.setReason(runshopApplicationInfo.getReason());
		return result;
	}

	private CooperationInfo generateCooperationInfoFromRunshop(RunshopApplicationInfo runshopApplicationInfo) {
		logger.info("RunshopService.generateCooperationInfoFromRunshop start with:{}", runshopApplicationInfo);
		if (runshopApplicationInfo != null) {
			CooperationInfo cooperationInfo = new CooperationInfo();
			cooperationInfo.setRunshop_id(runshopApplicationInfo.getId());
			cooperationInfo.setCooperation_type(runshopApplicationInfo.getCooperation_type());
			return cooperationInfo;
		} else
			return null;
	}

	@Override
	public RunshopV2Dto fillDeliveryInfo(int runshop_id, String mobile) {
		RunshopV2Dto result = new RunshopV2Dto();
		RunshopApplicationInfo runshopApplicationInfo = runshopMapper.getRunshopApplicationInfoByIdV2(runshop_id,
				mobile);

		if (runshopApplicationInfo.getStep() < RunshopServiceConstants.STEP_DELIVERY_INFO) {
			result.setDelivery_info(null);
		} else {
			DeliveryInfo info = generateDeliveryInfoFromRunshop(runshopApplicationInfo);
			result.setDelivery_info(info);
		}
		result.setStep(runshopApplicationInfo.getStep());
		result.setStatus_code(runshopApplicationInfo.getStatus());
		result.setReason(runshopApplicationInfo.getReason());
		return result;
	}

	private DeliveryInfo generateDeliveryInfoFromRunshop(RunshopApplicationInfo runshopApplicationInfo) {
		if (runshopApplicationInfo != null) {
			DeliveryInfo deliveryInfo = new DeliveryInfo();
			deliveryInfo.setRunshop_id(runshopApplicationInfo.getId());
			deliveryInfo.setDeliveryList(runshopMapper.selectDeliveryByRunshopId(runshopApplicationInfo.getId()));
			return deliveryInfo;
		} else
			return null;
	}

	@Override
	public RunshopV2Dto fillSettlementInfo(int runshop_id, String mobile) {
		RunshopV2Dto result = new RunshopV2Dto();
		RunshopApplicationInfo runshopApplicationInfo = runshopMapper.getRunshopApplicationInfoByIdV2(runshop_id,
				mobile);
		if (runshopApplicationInfo.getStep() < RunshopServiceConstants.STEP_SETTLEMENT_INFO) {
			result.setSettlement_info(null);
		} else {
			SettlementInfo settlementInfo = generateSettlementInfoFromRunshop(runshopApplicationInfo);
			result.setSettlement_info(settlementInfo);
		}
		result.setStep(runshopApplicationInfo.getStep());
		result.setStatus_code(runshopApplicationInfo.getStatus());
		result.setReason(runshopApplicationInfo.getReason());
		return result;
	}

	private SettlementInfo generateSettlementInfoFromRunshop(RunshopApplicationInfo runshopApplicationInfo) {
		if (runshopApplicationInfo != null) {
			SettlementInfo settleinfo = new SettlementInfo();
			settleinfo.setRunshop_id(runshopApplicationInfo.getId());
			BeanUtils.copyProperties(runshopApplicationInfo, settleinfo);
			settleinfo.setBranch_bank(runshopApplicationInfo.getBranch_banck());
			return settleinfo;
		} else
			return null;
	}

	@Override
	public RunshopV2Dto fillGoodListInfo(int runshop_id, String mobile) {
		RunshopV2Dto result = new RunshopV2Dto();
		RunshopApplicationInfo runshopApplicationInfo = runshopMapper.getRunshopApplicationInfoByIdV2(runshop_id,
				mobile);
		if (runshopApplicationInfo.getStep() < RunshopServiceConstants.STEP_GOODLIST_INFO) {
			result.setGood_list_info(null);
		} else {
			GoodListInfo goodListInfo = generateGoodListInfoFromRunshop(runshopApplicationInfo);
			result.setGood_list_info(goodListInfo);
		}
		result.setStep(runshopApplicationInfo.getStep());
		result.setStatus_code(runshopApplicationInfo.getStatus());
		result.setReason(runshopApplicationInfo.getReason());
		return result;
	}

	@Override
	public void copyGoodInfo(int runshopTargetId, int runshopSourceId) throws ServiceException {

		// 获取source开店申请的商品
		List<GoodInfoOriginDto> sourceGoodList = runshopMapper.selectGoodInfoOrigin(runshopSourceId);
		List<Integer> sourceGoodCategoryIds = new ArrayList<>();
		Map<Integer, String> sourceGoodCategoryMap = new HashMap<>();
		for (GoodInfoOriginDto good : sourceGoodList) {
			if (good.getRunshop_good_classification_id() != 0) {
				sourceGoodCategoryIds.add(good.getRunshop_good_classification_id());
			} else {
				// 如果商品没有分类，则默认分类
				sourceGoodCategoryMap.put(good.getRunshop_good_classification_id(),
						ProjectConstant.DEFAULT_CLASSIFICATION_NAME);
			}
		}

		if (!CollectionUtils.isEmpty(sourceGoodCategoryIds)) {
			// 获取source开店申请的商品的分类
			List<Category> sourceGoodCategoryList = runshopMapper.categoryListByIds(sourceGoodCategoryIds);
			for (Category sourceGood : sourceGoodCategoryList) {
				sourceGoodCategoryMap.put(sourceGood.getId(), sourceGood.getName());
			}
		}

		// 在target中新建商品分类
		List<Category> targetCategoryNeedAdd = new ArrayList<>();
		for (String sourceCategoryName : sourceGoodCategoryMap.values()) {
			Category categoryInTarget = runshopMapper.getRunShopGoodClassificationByRunshopIdAndName(runshopTargetId,
					sourceCategoryName);
			// 如果指定分类名称未出现在target中，则新建分类名称
			if (categoryInTarget == null) {
				Category catoryForAdd = new Category(sourceCategoryName, runshopTargetId);
				targetCategoryNeedAdd.add(catoryForAdd);
			}
		}
		if (!CollectionUtils.isEmpty(targetCategoryNeedAdd)) {
			runshopMapper.createRunShopGoodClassification(targetCategoryNeedAdd);
		}

		// 在target中添加商品
		for (GoodInfoOriginDto good : sourceGoodList) {
			String categoryName = sourceGoodCategoryMap.get(good.getRunshop_good_classification_id());
			if (StringUtils.isBlank(categoryName)) {
				categoryName = ProjectConstant.DEFAULT_CLASSIFICATION_NAME;
			}
			Category targetCategory = runshopMapper.getRunShopGoodClassificationByRunshopIdAndName(runshopTargetId,
					categoryName);

			GoodInfo targetGood = new GoodInfo();

			BeanUtils.copyProperties(good, targetGood);
			targetGood.getGood_classification().setClassification_id(targetCategory.getId());

			// 添加商品
			runshopMapper.createGoodInfo(targetGood);
		}
	}

	private GoodListInfo generateGoodListInfoFromRunshop(RunshopApplicationInfo runshopApplicationInfo) {
		if (runshopApplicationInfo != null) {
			GoodListInfo goodListInfo = new GoodListInfo();
			goodListInfo.setRunshop_id(runshopApplicationInfo.getId());
			List<GoodInfo> goodInfoList = runshopMapper.selectGoodInfo(goodListInfo.getRunshop_id());
			goodListInfo.setGood_info(goodInfoList);
			return goodListInfo;
		} else
			return null;
	}

	@Override
	public RunshopV2Dto fillAllInfo(int runshop_id, String mobile) {
		RunshopV2Dto result = new RunshopV2Dto();
		RunshopApplicationInfo runshopApplicationInfo;
		if (mobile == null) {
			runshopApplicationInfo = runshopMapper.getRunshopApplicationInfoById(runshop_id);
		} else {
			runshopApplicationInfo = runshopMapper.getRunshopApplicationInfoByIdV2(runshop_id, mobile);
		}
		if (runshopApplicationInfo != null) {
			StoreInfo storeInfo = generateStoreInfoFromRunshop(runshopApplicationInfo);
			CooperationInfo cooperationInfo = generateCooperationInfoFromRunshop(runshopApplicationInfo);
			DeliveryInfo deliveryInfo = generateDeliveryInfoFromRunshop(runshopApplicationInfo);
			QualificationInfo qualificationInfo = generateQualifictionInfoFromRunshop(runshopApplicationInfo);
			SettlementInfo settlementInfo = generateSettlementInfoFromRunshop(runshopApplicationInfo);
			GoodListInfo goodListInfo = generateGoodListInfoFromRunshop(runshopApplicationInfo);

			result.setCooperation_info(cooperationInfo);
			result.setGood_list_info(goodListInfo);
			result.setSettlement_info(settlementInfo);
			result.setQualification_info(qualificationInfo);
			result.setDelivery_info(deliveryInfo);
			result.setStore_info(storeInfo);

			result.setStep(runshopApplicationInfo.getStep());

			result.setStatus_code(runshopApplicationInfo.getStatus());
			result.setReason(runshopApplicationInfo.getReason());
		}
		return result;
	}

	public RunshopV2Dto fillAllInfoV2(RunshopApplicationInfo runshopApplicationInfo) {
		RunshopV2Dto result = new RunshopV2Dto();
		if (runshopApplicationInfo != null) {
			StoreInfo storeInfo = generateStoreInfoFromRunshop(runshopApplicationInfo);
			CooperationInfo cooperationInfo = generateCooperationInfoFromRunshop(runshopApplicationInfo);
			DeliveryInfo deliveryInfo = generateDeliveryInfoFromRunshop(runshopApplicationInfo);
			QualificationInfo qualificationInfo = generateQualifictionInfoFromRunshop(runshopApplicationInfo);
			SettlementInfo settlementInfo = generateSettlementInfoFromRunshop(runshopApplicationInfo);
			GoodListInfo goodListInfo = generateGoodListInfoFromRunshop(runshopApplicationInfo);

			result.setCooperation_info(cooperationInfo);
			result.setGood_list_info(goodListInfo);
			result.setSettlement_info(settlementInfo);
			result.setQualification_info(qualificationInfo);
			result.setDelivery_info(deliveryInfo);
			result.setStore_info(storeInfo);

			result.setStep(runshopApplicationInfo.getStep());

			result.setStatus_code(runshopApplicationInfo.getStatus());
			result.setReason(runshopApplicationInfo.getReason());

		}
		return result;
	}

	@Override
	public SettlePPInfo getSettlePPInfo(int runshopId) {
		logger.info("runshopService#getSettlePPInfo,runshopId:{}", runshopId);
		SettlePPInfo result = runshopMapper.getSettlePPInfo(runshopId);
		logger.info("runshopService#getSettlePPInfo,result:{}", result);
		return result;
	}

	public RunshopEsQuery runshopSearchFormToEsQuery(RunshopSearchFormV2 searchForm) {
		RunshopEsQuery runshopEsQuery = new RunshopEsQuery();
		runshopEsQuery.setAuditStatus(searchForm.getAuditStatus());
		runshopEsQuery.setAuditTimes(searchForm.getAuditTimesEnum());
		if (searchForm.getDistrictIds() != null && searchForm.getDistrictIds().size() > 0) {
			runshopEsQuery.setDistrictIds(searchForm.getDistrictIds());
		}
		if (searchForm.getCityIds() != null && searchForm.getCityIds().size() > 0) {
			runshopEsQuery.setCityIds(searchForm.getCityIds());
		}
		if (searchForm.getProvinceIds() != null && searchForm.getProvinceIds().size() > 0) {
			runshopEsQuery.setProvinceIds(searchForm.getProvinceIds());
		}
		runshopEsQuery.setLimit(searchForm.getLimit());
		runshopEsQuery.setOffset(searchForm.getOffset());
		runshopEsQuery.setRunshopId(searchForm.getRunshopId());
		if (searchForm.getSponsorType() != null) {
			runshopEsQuery.setSponsorType(searchForm.getSponsorType());
		}

		if (searchForm.getUpdateStartDate() != null) {
			runshopEsQuery.setUpdatedAtStart(Timestamp.valueOf(searchForm.getUpdateStartDate()).getTime());
		}

		if (searchForm.getUpdateEndDate() != null) {
			runshopEsQuery.setUpdatedAtEnd(Timestamp.valueOf(searchForm.getUpdateEndDate()).getTime());
		}
		if (!Strings.isNullOrEmpty(searchForm.getMobile())) {
			runshopEsQuery.setMobile(searchForm.getMobile());
		}

		return runshopEsQuery;
	}

	@Override
	public PaginationResponse<RunShopSearchItemForXY> searchRunshopApplicationForXY(RunshopSearchForm searchForm)
			throws ServiceException {

		/* 审核状态 */
		Integer status = transformToRunshopStatus(searchForm.getAuditStatus());
		/* 来源 */
		Integer source = transformToRunshopSource(searchForm.getSponsorType());
		/* 是否一审 */
		Integer auditTimes = searchForm.getAuditTimesEnum().getIndex();

		LocalDate updateStartDate = searchForm.getUpdateStartDate();
		LocalDate updateEndDate = searchForm.getUpdateEndDate();
		Timestamp updateBeginTime = null;
		Timestamp updateEndTime = null;
		if (null != updateStartDate && null != updateEndDate) {
			updateBeginTime = Timestamp.valueOf(updateStartDate.atStartOfDay());
			updateEndTime = Timestamp.valueOf(updateEndDate.plusDays(1L).atStartOfDay());
		}
		int count = runshopMapper.countRunshopInfoForXY(searchForm.getRunshopId(), searchForm.getUserId(), status,
				source, searchForm.getMobile(), updateBeginTime, updateEndTime);
		if (0 == count) {
			return new PaginationResponse<>();
		}
		List<RunshopApplicationInfo> runshopApplicationInfoList = runshopMapper.searchRunshopInfoForXY(
				searchForm.getRunshopId(), searchForm.getUserId(), status, source, searchForm.getMobile(),
				updateBeginTime, updateEndTime, searchForm.getLimit(), searchForm.getOffset());
//		List<Integer> runshopIdList = runshopApplicationInfoList.stream().map(RunshopApplicationInfo::getId)
//				.collect(Collectors.toList());
//		Map<Integer, RunshopAuditSimpleStatusDto> runshopAuditSimpleStatusDtos = runshopWorkFlowService
//				.searchAuditRunshopStatus(runshopIdList, toWorkFlowAuditStatusEnum(searchForm.getAuditStatus()));

		List<RunShopSearchItemForXY> list = new ArrayList<>();
		for (RunshopApplicationInfo runshopApplicationInfo : runshopApplicationInfoList) {
//			RunshopAuditSimpleStatusDto runshopAuditSimpleStatusDto = runshopAuditSimpleStatusDtos
//					.get(runshopApplicationInfo.getId());
			RunShopSearchItemForXY item = new RunShopSearchItemForXY();
//			if (null == runshopAuditSimpleStatusDto) {
				item.setAuditStatus(transformToXYStatus(RunShopStatus.fromValue(runshopApplicationInfo.getStatus())));
				item.setSponsorType(SponsorTypeEnum.valueOf(runshopApplicationInfo.getSource()));
				item.setUpdater("");
				item.setTaskId("");
//			} else {
//				item.setAuditStatus(fromWorkFlowAuditStatusEnum(runshopAuditSimpleStatusDto.getProcessStatus()));
//				SponsorTypeEnum sponsorTypeEnum = SponsorTypeEnum.valueOf(runshopApplicationInfo.getSource());
//				item.setSponsorType(sponsorTypeEnum);
//				User user = coffeeHrClientService.getUserById(runshopAuditSimpleStatusDto.getAssignmentUserId());
//				if (null == user) {
//					item.setUpdater("");
//				} else {
//					item.setUpdater(user.getName());
//				}
//				item.setTaskId(runshopAuditSimpleStatusDto.getProcessInstanceId());
//			}
			item.setRunshopId(runshopApplicationInfo.getId());
			item.setStoreAddress(runshopApplicationInfo.getAddress());
			item.setStoreName(runshopApplicationInfo.getStore_name());
			item.setStoreMobile(runshopApplicationInfo.getMobile());
			item.setCreatedAt(runshopApplicationInfo.getCreated_at());
			item.setUpdatedAt(runshopApplicationInfo.getUpdated_at());
			list.add(item);
		}
		PaginationResponse<RunShopSearchItemForXY> ret = new PaginationResponse<>(count, list);
		logger.info("RunshopService#searchRunshopApplicationForXY done, ret is {}", ret);
		return ret;
	}

	@Override
	public PaginationResponse<RunShopSearchItemForXY> searchRunshopApplicationForXYV2(RunshopSearchFormV2 searchForm)
			throws ServiceException {
		logger.info("RunshopServiceConstants#searchRunshopApplicationForXY into, form is {}", searchForm);
		if (searchForm == null) {
			return null;
		}
		PaginationResponse<RunShopSearchItemForXY> ret = null;
		RunshopEsQuery runshopEsQuery = runshopSearchFormToEsQuery(searchForm);

		RunshopEsResult runshopEsResult = esSearchService.queryRunshopInfosFromEs(runshopEsQuery);

		if (runshopEsResult != null) {
			if (runshopEsResult.getRunshopEsEntities().size() > 0) {
				List<Integer> runshopIdList = runshopEsResult.getRunshopEsEntities().stream().map(RunshopEsEntity::getId)
						.collect(Collectors.toList());
				Map<Integer, RunshopAuditSimpleStatusDto> runshopAuditSimpleStatusDtos = runshopWorkFlowService
						.searchAuditRunshopStatus(runshopIdList, toWorkFlowAuditStatusEnum(searchForm.getAuditStatus()));
				List<RunShopSearchItemForXY> list = new ArrayList<>();
				for (RunshopEsEntity runshopEsEntity : runshopEsResult.getRunshopEsEntities()) {
					RunshopAuditSimpleStatusDto runshopAuditSimpleStatusDto = runshopAuditSimpleStatusDtos
							.get(runshopEsEntity.getId());
					RunShopSearchItemForXY item = new RunShopSearchItemForXY();
					if (null == runshopAuditSimpleStatusDto) {
						if (runshopEsEntity.getStatus() == 3) { //回退
							item.setAuditStatus(AuditStatusEnum.NEED_MODIFY);
						}else {
							item.setAuditStatus(AuditStatusEnum.valueOf(runshopEsEntity.getStatus()));
						}
						item.setSponsorType(SponsorTypeEnum.valueOf(runshopEsEntity.getSource()));
						item.setUpdater("");
						item.setTaskId("");
					} else {
						item.setAuditStatus(fromWorkFlowAuditStatusEnum(runshopAuditSimpleStatusDto.getProcessStatus()));
						SponsorTypeEnum sponsorTypeEnum = SponsorTypeEnum.valueOf(runshopEsEntity.getSource());
						item.setSponsorType(sponsorTypeEnum);
						User user = null;
						if (runshopAuditSimpleStatusDto.getAssignmentUserId() > 0) {
							user = coffeeHrClientService.getUserById(runshopAuditSimpleStatusDto.getAssignmentUserId());
						}
						if (null == user) {
							item.setUpdater("");
						} else {
							item.setUpdater(user.getName());
						}
						item.setTaskId(runshopAuditSimpleStatusDto.getProcessInstanceId());
					}
					item.setRunshopId(runshopEsEntity.getId());
					item.setStoreAddress(runshopEsEntity.getAddress());
					item.setStoreName(runshopEsEntity.getStoreName());
					item.setStoreMobile(runshopEsEntity.getMobile());
					item.setCreatedAt(new Timestamp(runshopEsEntity.getCreatedAt()));
					item.setUpdatedAt(new Timestamp(runshopEsEntity.getUpdatedAt()));
					if (runshopEsEntity.getSubmitTimes() == 1) {
						item.setAuditTimesEnum(AuditTimesEnum.ONE);
					} else if (runshopEsEntity.getSubmitTimes() > 1) {
						item.setAuditTimesEnum(AuditTimesEnum.OTHER);
					}

					list.add(item);
				}
				ret = new PaginationResponse<>(runshopEsResult.getTotal(), list);
			}
		}

		return ret;
	}

	/**
	 * 将轩辕系统传输的开店审核状态改为开店申请的状态
	 *
	 * @param auditStatusEnum
	 * @return
	 */
	private Integer transformToRunshopStatus(AuditStatusEnum auditStatusEnum) {
		switch (auditStatusEnum) {
		case UNQUALIFIED:
			return ProjectConstant.UNQUALIFIED;
		case AUDITING:
			return ProjectConstant.PENDING_AUDIT;
		case PASS:
			return ProjectConstant.QUALIFIED;
		case NEED_MODIFY:
			return ProjectConstant.NEED_FIX;
		default:
			// couldn't be here
			return null;
		}
	}

	/**
	 * 将轩辕系统传输的开店审核状态改为开店申请的状态
	 *
	 * @param runShopStatus
	 * @return
	 */
	private AuditStatusEnum transformToXYStatus(RunShopStatus runShopStatus) {
		switch (runShopStatus) {
			case FAIL:
				return AuditStatusEnum.UNQUALIFIED;
			case PASS:
				return AuditStatusEnum.PASS;
			case WAIT:
				return AuditStatusEnum.AUDITING;
			case NEED_FIX:
				return AuditStatusEnum.NEED_MODIFY;
			default:
				// couldn't be here
				return null;
		}
	}
	/**
	 * 将轩辕系统的开店来源转换为runshop的开店来源
	 *
	 * @param sponsorTypeEnum
	 * @return
	 */
	private Integer transformToRunshopSource(SponsorTypeEnum sponsorTypeEnum) {
		switch (sponsorTypeEnum) {
		case ALL:
			return null;
		case STORE:
			return ProjectConstant.RUNSHOP_FROM_KAIDIAN_ELE_ME;
		case BD:
			return ProjectConstant.RUNSHOP_FROM_EVE;
		default:
			// couldn't be here
			return null;
		}
	}

	@Override
	public RunShopForXYDto runShopApplicationDetailForXY(int runShopId, String taskId) throws ServiceException {
		logger.info("RunshopService#runShopApplicationDetailForXY into, runshopId is {}, taskId is {}", runShopId,
				taskId);
		RunshopApplicationInfo runshopApplicationInfoById = getRunshopApplicationInfoById(runShopId);
		/* 只返回新的开店申请, 过滤掉老的开店申请 */
		if (null == runshopApplicationInfoById || IS_NEW != runshopApplicationInfoById.getIs_new())
			return null;
		RunshopV2Dto runshopInfo = fillAllInfoV2(runshopApplicationInfoById);
		RunShopForXYDto ret = new RunShopForXYDto(runshopInfo);
		if (runshopApplicationInfoById.getDom_id() > 0) {
			Map<ThirdPlatformEnum, String> thirdPlatformMap = generateThirdPlatFormInfo(runshopApplicationInfoById.getDom_id());
			StoreInfo storeInfo = runshopInfo.getStore_info();
			storeInfo.setThirdPlatformMap(thirdPlatformMap);
			runshopInfo.setStore_info(storeInfo);
		}
		logger.info("RunshopService#runShopApplicationDetailForXY done, ret is {}", ret);
		return ret;
	}

	@Override
	public PaginationResponse<RunShopForNaposSimpleDto> listMyRunshopApplicationForNapos(int userId, int limit,
			int offest) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public BirdCooperationValidDto isBirdCooperationValid(int runshopId) {
		return new BirdCooperationValidDto(true);
	}

	@Override
	public List<BankProvinceDto> listBankProvince() {
		List<BankProvince> list = BankProvinces.getInstance().getList();
		List<BankProvinceDto> ret = new ArrayList<>();
		for (BankProvince bankProvince : list) {
			ret.add(new BankProvinceDto(bankProvince.getId(), bankProvince.getName()));
		}
		return ret;
	}

	@Override
	public List<BankProvince> listBankProvinceWithCities() {
		return BankProvinces.getInstance().getList();
	}

	@Override
	public List<BankCityDto> listBankCity(int provinceId) {
		List<BankCityDto> ret = BankProvinces.getInstance().getMap().get(provinceId).getList();
		return ret;
	}

	@Override
	public List<BankDto> listBank() {
		List<BankDto> ret = Banks.getInstance().getList();
		return ret;
	}

	@Override
	public void auditRunshopProcess(RunshopAuditProcessForm form) throws ServiceException, ServerException {
		logger.info("RunshopService.auditRunshopProcess start with:{}", form);
		me.ele.work.flow.engine.core.form.RunshopAuditProcessForm auditProcessForm = new me.ele.work.flow.engine.core.form.RunshopAuditProcessForm(
				toWorkFlowAuditProcessEnum(form.getAuditProcessEnum()),
				toWorkFlowAuditStatusEnum(form.getFromStatus()), form.getReason(), form.getRunshopId(),
				toWorkFlowAuditStatusEnum(form.getToStatus()), form.getUserId());
		runshopWorkFlowService.auditRunshopProcess(auditProcessForm);
		logger.info("RunshopService.auditRunshopProcess done with:{}", form);
	}

	private me.ele.work.flow.engine.core.constants.AuditProcessEnum toWorkFlowAuditProcessEnum(
			me.ele.bpm.runshop.core.constants.AuditProcessEnum process) {
		if (process == null) {
			return null;
		}
		switch (process) {
		case STORE_INFO_AUDIT:
			return me.ele.work.flow.engine.core.constants.AuditProcessEnum.STORE_INFO_AUDIT;
		case STORE_LOGO_AUDIT:
			return me.ele.work.flow.engine.core.constants.AuditProcessEnum.STORE_LOGO_AUDIT;
		case LEGAL_REPRESENTATIVE_AUDIT:
			return me.ele.work.flow.engine.core.constants.AuditProcessEnum.LEGAL_REPRESENTATIVE_AUDIT;
		case MAIN_QUALIFICATIONS_AUDIT:
			return me.ele.work.flow.engine.core.constants.AuditProcessEnum.MAIN_QUALIFICATIONS_AUDIT;
		case INDUSTRY_QUALIFICATION_AUDIT:
			return me.ele.work.flow.engine.core.constants.AuditProcessEnum.INDUSTRY_QUALIFICATION_AUDIT;
		}
		return null;
	}

	private me.ele.work.flow.engine.core.constants.AuditStatusEnum toWorkFlowAuditStatusEnum(AuditStatusEnum status) {
		if (status == null) {
			return null;
		}
		switch (status) {
		case AUDITING:
			return me.ele.work.flow.engine.core.constants.AuditStatusEnum.AUDITING;
		case PASS:
			return me.ele.work.flow.engine.core.constants.AuditStatusEnum.PASS;
		case NEED_MODIFY:
			return me.ele.work.flow.engine.core.constants.AuditStatusEnum.NEED_MODIFY;
		case UNQUALIFIED:
			return me.ele.work.flow.engine.core.constants.AuditStatusEnum.UNQUALIFIED;
		}
		return null;
	}

	private RunshopCreatorEnum toWorkFlowRunshopCreator(SponsorTypeEnum sponsorType) {
		if (sponsorType == null) {
			return null;
		}
		switch (sponsorType) {
		case ALL:
			return RunshopCreatorEnum.ALL;
		case STORE:
			return RunshopCreatorEnum.MERCHANT;
		case BD:
			return RunshopCreatorEnum.BD;
		}
		return null;
	}

	private AuditStatusEnum fromWorkFlowAuditStatusEnum(
			me.ele.work.flow.engine.core.constants.AuditStatusEnum auditStatus) {
		if (auditStatus == null) {
			return null;
		}
		switch (auditStatus) {
		case AUDITING:
			return AuditStatusEnum.AUDITING;
		case PASS:
			return AuditStatusEnum.PASS;
		case NEED_MODIFY:
			return AuditStatusEnum.NEED_MODIFY;
		case UNQUALIFIED:
			return AuditStatusEnum.UNQUALIFIED;
		}
		return null;
	}

	private me.ele.bpm.runshop.core.constants.AuditProcessEnum fromWorkFlowAuditProcessEnum(
			me.ele.work.flow.engine.core.constants.AuditProcessEnum auditProcess) {
		if (auditProcess == null) {
			return null;
		}
		switch (auditProcess) {
		case STORE_INFO_AUDIT:
			return me.ele.bpm.runshop.core.constants.AuditProcessEnum.STORE_INFO_AUDIT;
		case STORE_LOGO_AUDIT:
			return me.ele.bpm.runshop.core.constants.AuditProcessEnum.STORE_LOGO_AUDIT;
		case LEGAL_REPRESENTATIVE_AUDIT:
			return me.ele.bpm.runshop.core.constants.AuditProcessEnum.STORE_LOGO_AUDIT;
		case MAIN_QUALIFICATIONS_AUDIT:
			return me.ele.bpm.runshop.core.constants.AuditProcessEnum.MAIN_QUALIFICATIONS_AUDIT;
		case INDUSTRY_QUALIFICATION_AUDIT:
			return me.ele.bpm.runshop.core.constants.AuditProcessEnum.INDUSTRY_QUALIFICATION_AUDIT;
		case CONTRACT_PLAN_AUDIT:
			break;
		case BILLING_INFO_AUDIT:
			break;
		case PRODUCT_CONTENT_AUDIT:
			break;
		case DELIVERY_INFO_AUDIT:
			break;
		}

		return null;
	}

	@Override
	public Map<AuditProcessEnum, AuditStatusEnum> getAuditStatus(int runshopId) throws ServiceException,
			ServerException {
		logger.info("RunshopService#getAuditStatus into, runshopId is {}", runshopId);

		HashMap<AuditProcessEnum, AuditStatusEnum> ret = new HashMap<>();
		Map<me.ele.work.flow.engine.core.constants.AuditProcessEnum, me.ele.work.flow.engine.core.constants.AuditStatusEnum> auditStatus;
		try {
			auditStatus = runshopWorkFlowService.getAuditStatus(runshopId);
		} catch (Exception e) {
			logger.error("RunshopService#getAuditStatus getAuditStatus(" + runshopId + ") error, ", e);
			return null;
		}
		if (CollectionUtils.isEmpty(auditStatus)) {
			logger.info("RunshopService#getAuditStatus done, ret null");
			return null;
		}

		for (AuditProcessEnum process : AuditProcessEnum.values()) {
			me.ele.work.flow.engine.core.constants.AuditStatusEnum status = auditStatus
					.get(toWorkFlowAuditProcessEnum(process));
			ret.put(process, fromWorkFlowAuditStatusEnum(status));
		}

		logger.info("RunshopService#getAuditStatus done, ret is {}", ret);
		return ret;
	}

	/**
	 * 更新指定开店申请的更新时间字段
	 *
	 * @param runshopId
	 *            开店申请id
	 */
	@Override
	public void updateRunshopUpdatedAt(int runshopId) {
		logger.info("RunshopService#updateRunshopUpdatedAt into, runshopId is:{}", runshopId);
		if (runshopId < 1) {
			logger.error("RunshopService#updateRunshopUpdatedAt runshopId error.");
			return;
		}
		runshopMapper.updateRunshopUpdatedAt(runshopId);
		logger.info("RunshopService#updateRunshopUpdatedAt done.");
	}

	@Override
	public void setBDApplicant(int runshopId, int bdUserId, String email) {
		logger.info("RunshopService#setBDApplicant into, runshopId is {}, bdUserId is {}, eamil is {}", runshopId,
				bdUserId, email);
		runshopMapper.updateSourceAndUserIdAndEmail(2, bdUserId, email, runshopId);
	}

	@Override
	public ImageEntity getLogo(int runshopId) {
		logger.info("RunshopService#getLogo into, runshopId is {}", runshopId);

		List<RunshopApplicationPicture> pics = runshopPictureMapper.listPicByRunshopIdAndPicCode(runshopId,
				ProjectConstant.STORE_LOGO);
		if (CollectionUtils.isEmpty(pics)) {
			logger.info("RunshopService#getLogo done, pics is empty");
			return null;
		}
		ImageEntity ret = new ImageEntity(pics.get(0).getPic_url(), pics.get(0).getPic_hash());
		logger.info("RunshopService#getLogo done, ret is {}", ret);
		return ret;
	}

	@Override
	public int countBDRunshopByStatus(int userId, int status) {
		logger.info("RunshopService#countBDRunshopByStatus into, userId is {}, status is {}", userId, status);
		int ret = runshopMapper.countRunshopByStatusAndIsNew(status, userId, 1);
		logger.info("RunshopService#countRunshopByStatus done, ret is {}", ret);
		return ret;
	}

	@Override
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

	@Override
	public Integer getStepById(int id) {
		logger.info("RunshopService#getStepById into, id is {}", id);
		RunshopApplicationInfo runshop = runshopMapper.getRunshopApplicationInfoById(id);
		if (runshop == null) {
			return null;
		}
		int ret = runshop.getStep();
		logger.info("RunshopService#getStepById done, ret is {}", ret);
		return ret;
	}

	@Override
	public RunshopApplicationInfo getRunshopApplicationInfoById(int id) {
		logger.info("RunshopService#getRunshopApplicationInfoById into, id is {}", id);
		RunshopApplicationInfo ret = runshopMapper.getRunshopApplicationInfoById(id);
		logger.info("RunshopService#getRunshopApplicationInfoById done, ret is {}", ret);
		return ret;
	}

	@Override
	public List<RunshopApplicationInfo> listRunshopApplicationInfoByHeadIdAndStatus(int id, int status) {
		logger.info("RunshopService#listRunshopApplicationInfoByHeadId into, id is {}, status is {}", id, status);
		List<RunshopApplicationInfo> ret = runshopMapper.listRunshopApplicationInfoByHeadIdAndStatus(id, 1);
		logger.info("RunshopService#listRunshopApplicationInfoByHeadId done, ret is {}", ret);
		return ret;
	}

	@Override
	public boolean isOnlyApplyForBdByMobile(int runshopId, String mobile) throws ServiceException {

		logger.info("RunshopService#isOnlyApplyForBdByMobile,runshopId:{},mobile:{}", runshopId, mobile);

		// 检查手机号mobile 是否为空
		if (StringUtils.isBlank(mobile)) {
			throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_MOBILE_IS_EMPTY);
		}

		// 检查输入的mobile是否是第一次
		List<RunshopApplicationInfo> runShopList = runshopMapper.listRunShopByMobile(mobile);
		logger.info("RunshopService#isOnlyApplyForBdByMobile,listRunShopByMobile.size:{}", runShopList.size());

		for (RunshopApplicationInfo existRunshop : runShopList) {
			if (mobile.equals(existRunshop.getMobile()) && runshopId != existRunshop.getId()
					&& existRunshop.getIs_new() == IS_NEW && existRunshop.getStatus() != ProjectConstant.UNQUALIFIED) {
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean isFirstApplyForBd(String mobile) throws ServiceException, ServiceException {
		logger.info("RunshopService#isFirstApplyForBd,mobile:{}", mobile);
		// 检查手机号mobile 是否为空
		if (StringUtils.isBlank(mobile)) {
			throw ExceptionUtil.createServiceException(ExceptionCode.RUNSHOP_MOBILE_IS_EMPTY);
		}
		List<RunshopApplicationInfo> runShopList = runshopMapper.listRunShopByMobile(mobile);
		logger.info("RunshopService#isFirstApplyForBd,runShopList.size:{}", runShopList.size());
		for (RunshopApplicationInfo existRunshop : runShopList) {
			if (mobile.equals(existRunshop.getMobile()) && existRunshop.getIs_new() == IS_NEW
					&& existRunshop.getStatus() != ProjectConstant.UNQUALIFIED) {
				return false;
			}
		}
		return true;
	}

	@Override
	public List<DomGoodsInfo> getDomGoodsInfos(Map<Integer, String> sourceMap) throws ServiceException {
		List<DomGoodsInfo> domGoodsInfos = new ArrayList<>();
		if (sourceMap == null) {
			return domGoodsInfos;
		}
		// ERS((short) 0), MEI_TUAN((short) 1), DIAN_PING((short) 2),
		// BAI_DU((short) 3), KOU_BEI((short) 4)
		if (sourceMap.get(1) != null) {
			List<DomGoodsInfo> meituanFoods = meituanFoodMapper.getMeituanFoodByRestaurantId(sourceMap.get(1));
			if (meituanFoods != null && meituanFoods.size() > 0) {
				domGoodsInfos.addAll(meituanFoods);
			}
		}

		if (sourceMap.get(3) != null) {
			List<DomGoodsInfo> baiduFoods = baiduFoodMapper.getBaiduFoodByRestaurantId(sourceMap.get(3));
			if (baiduFoods != null && baiduFoods.size() > 0) {
				domGoodsInfos.addAll(baiduFoods);
			}
		}
		return domGoodsInfos;
	}

}
